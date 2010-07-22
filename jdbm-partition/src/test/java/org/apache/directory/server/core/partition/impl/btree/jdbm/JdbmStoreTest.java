/*
 *  Licensed to the Apache Software Foundation (ASF) under one
 *  or more contributor license agreements.  See the NOTICE file
 *  distributed with this work for additional information
 *  regarding copyright ownership.  The ASF licenses this file
 *  to you under the Apache License, Version 2.0 (the
 *  "License"); you may not use this file except in compliance
 *  with the License.  You may obtain a copy of the License at
 *  
 *    http://www.apache.org/licenses/LICENSE-2.0
 *  
 *  Unless required by applicable law or agreed to in writing,
 *  software distributed under the License is distributed on an
 *  "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 *  KIND, either express or implied.  See the License for the
 *  specific language governing permissions and limitations
 *  under the License. 
 *  
 */
package org.apache.directory.server.core.partition.impl.btree.jdbm;


import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.File;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.UUID;

import javax.naming.directory.Attributes;

import org.apache.commons.io.FileUtils;
import org.apache.directory.server.constants.ApacheSchemaConstants;
import org.apache.directory.server.xdbm.GenericIndex;
import org.apache.directory.server.xdbm.Index;
import org.apache.directory.server.xdbm.IndexEntry;
import org.apache.directory.server.xdbm.IndexNotFoundException;
import org.apache.directory.server.xdbm.StoreUtils;
import org.apache.directory.shared.ldap.constants.SchemaConstants;
import org.apache.directory.shared.ldap.csn.CsnFactory;
import org.apache.directory.shared.ldap.cursor.Cursor;
import org.apache.directory.shared.ldap.entry.DefaultEntry;
import org.apache.directory.shared.ldap.entry.DefaultEntryAttribute;
import org.apache.directory.shared.ldap.entry.DefaultModification;
import org.apache.directory.shared.ldap.entry.Entry;
import org.apache.directory.shared.ldap.entry.EntryAttribute;
import org.apache.directory.shared.ldap.entry.Modification;
import org.apache.directory.shared.ldap.entry.ModificationOperation;
import org.apache.directory.shared.ldap.exception.LdapNoSuchObjectException;
import org.apache.directory.shared.ldap.exception.LdapSchemaViolationException;
import org.apache.directory.shared.ldap.name.DN;
import org.apache.directory.shared.ldap.name.RDN;
import org.apache.directory.shared.ldap.schema.AttributeType;
import org.apache.directory.shared.ldap.schema.SchemaManager;
import org.apache.directory.shared.ldap.schema.ldif.extractor.SchemaLdifExtractor;
import org.apache.directory.shared.ldap.schema.ldif.extractor.impl.DefaultSchemaLdifExtractor;
import org.apache.directory.shared.ldap.schema.loader.ldif.LdifSchemaLoader;
import org.apache.directory.shared.ldap.schema.manager.impl.DefaultSchemaManager;
import org.apache.directory.shared.ldap.util.LdapExceptionUtils;
import org.junit.After;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


/**
 * Unit test cases for JdbmStore
 *
 * @author <a href="mailto:dev@directory.apache.org">Apache Directory Project</a>
 */
@SuppressWarnings("unchecked")
public class JdbmStoreTest
{
    private static final Logger LOG = LoggerFactory.getLogger( JdbmStoreTest.class.getSimpleName() );

    File wkdir;
    JdbmStore<Entry> store;
    private static SchemaManager schemaManager = null;
    private static LdifSchemaLoader loader;
    private static DN EXAMPLE_COM;
    
    /** The OU AttributeType instance */
    private static AttributeType OU_AT;

    /** The ApacheAlias AttributeType instance */
    private static AttributeType APACHE_ALIAS_AT;

    /** The DC AttributeType instance */
    private static AttributeType DC_AT;

    /** The SN AttributeType instance */
    private static AttributeType SN_AT;


    @BeforeClass
    public static void setup() throws Exception
    {
        String workingDirectory = System.getProperty( "workingDirectory" );

        if ( workingDirectory == null )
        {
            String path = JdbmStoreTest.class.getResource( "" ).getPath();
            int targetPos = path.indexOf( "target" );
            workingDirectory = path.substring( 0, targetPos + 6 );
        }

        File schemaRepository = new File( workingDirectory, "schema" );
        SchemaLdifExtractor extractor = new DefaultSchemaLdifExtractor( new File( workingDirectory ) );
        extractor.extractOrCopy( true );
        loader = new LdifSchemaLoader( schemaRepository );
        schemaManager = new DefaultSchemaManager( loader );

        boolean loaded = schemaManager.loadAllEnabled();

        if ( !loaded )
        {
            fail( "Schema load failed : " + LdapExceptionUtils.printErrors( schemaManager.getErrors() ) );
        }

        EXAMPLE_COM = new DN( "dc=example,dc=com", schemaManager );
        
        OU_AT = schemaManager.getAttributeType( SchemaConstants.OU_AT );
        DC_AT = schemaManager.getAttributeType( SchemaConstants.DC_AT );
        SN_AT = schemaManager.getAttributeType( SchemaConstants.SN_AT );
        APACHE_ALIAS_AT = schemaManager.getAttributeType( ApacheSchemaConstants.APACHE_ALIAS_AT );
    }


    @Before
    public void createStore() throws Exception
    {
        destroyStore();

        // setup the working directory for the store
        wkdir = File.createTempFile( getClass().getSimpleName(), "db" );
        wkdir.delete();
        wkdir = new File( wkdir.getParentFile(), getClass().getSimpleName() );
        wkdir.mkdirs();

        // initialize the store
        store = new JdbmStore<Entry>();
        store.setId( "example" );
        store.setCacheSize( 10 );
        store.setPartitionDir( wkdir );
        store.setSyncOnWrite( false );
        store.addIndex( new JdbmIndex( SchemaConstants.OU_AT_OID ) );
        store.addIndex( new JdbmIndex( SchemaConstants.UID_AT_OID ) );

        StoreUtils.loadExampleData( store, schemaManager );
        LOG.debug( "Created new store" );
    }


    @After
    public void destroyStore() throws Exception
    {
        if ( store != null )
        {
            store.destroy();
        }

        store = null;

        if ( wkdir != null )
        {
            FileUtils.deleteDirectory( wkdir );
        }

        wkdir = null;
    }


    /**
     * Tests a suffix with two name components: dc=example,dc=com.
     * When reading this entry back from the store the DN must
     * consist of two RDNs.
     */
    @Test
    public void testTwoComponentSuffix() throws Exception
    {
        // setup the working directory for the 2nd store
        File wkdir2 = File.createTempFile( getClass().getSimpleName(), "db2" );
        wkdir2.delete();
        wkdir2 = new File( wkdir2.getParentFile(), getClass().getSimpleName() );
        wkdir2.mkdirs();

        // initialize the 2nd store
        JdbmStore<Entry> store2 = new JdbmStore<Entry>();
        store2.setId( "example2" );
        store2.setCacheSize( 10 );
        store2.setPartitionDir( wkdir2 );
        store2.setSyncOnWrite( false );
        store2.addIndex( new JdbmIndex( SchemaConstants.OU_AT_OID ) );
        store2.addIndex( new JdbmIndex( SchemaConstants.UID_AT_OID ) );
        store2.setSuffixDn( EXAMPLE_COM );
        store2.init( schemaManager );

        // inject context entry
        DN suffixDn = new DN( "dc=example,dc=com", schemaManager );
        Entry entry = new DefaultEntry( schemaManager, suffixDn );
        entry.add( "objectClass", "top", "domain" );
        entry.add( "dc", "example" );
        entry.add( SchemaConstants.ENTRY_CSN_AT, new CsnFactory( 0 ).newInstance().toString() );
        entry.add( SchemaConstants.ENTRY_UUID_AT, UUID.randomUUID().toString() );
        store2.add( entry );

        // lookup the context entry
        Long id = store2.getEntryId( suffixDn );
        Entry lookup = store2.lookup( id );
        assertEquals( 2, lookup.getDn().getRdns().size() );
    }


    @Test
    public void testSimplePropertiesUnlocked() throws Exception
    {
        JdbmStore<Attributes> store = new JdbmStore<Attributes>();
        store.setSyncOnWrite( true ); // for code coverage

        assertNull( store.getAliasIndex() );
        store.addIndex( new JdbmIndex<String, Attributes>( ApacheSchemaConstants.APACHE_ALIAS_AT_OID ) );
        assertNotNull( store.getAliasIndex() );

        assertEquals( JdbmStore.DEFAULT_CACHE_SIZE, store.getCacheSize() );
        store.setCacheSize( 24 );
        assertEquals( 24, store.getCacheSize() );

        assertNull( store.getPresenceIndex() );
        store.addIndex( new JdbmIndex<String, Attributes>( ApacheSchemaConstants.APACHE_EXISTENCE_AT_OID ) );
        assertNotNull( store.getPresenceIndex() );

        assertNull( store.getOneLevelIndex() );
        store.addIndex( new JdbmIndex<Long, Attributes>( ApacheSchemaConstants.APACHE_ONE_LEVEL_AT_OID ) );
        assertNotNull( store.getOneLevelIndex() );

        assertNull( store.getSubLevelIndex() );
        store.addIndex( new JdbmIndex<Long, Attributes>( ApacheSchemaConstants.APACHE_SUB_LEVEL_AT_OID ) );
        assertNotNull( store.getSubLevelIndex() );

        assertNull( store.getId() );
        store.setId( "foo" );
        assertEquals( "foo", store.getId() );

        assertNull( store.getRdnIndex() );
        store.addIndex( new JdbmRdnIndex( ApacheSchemaConstants.APACHE_RDN_AT_OID ) );
        assertNotNull( store.getRdnIndex() );

        assertNull( store.getOneAliasIndex() );
        store.addIndex( new JdbmIndex<Long, Attributes>( ApacheSchemaConstants.APACHE_ONE_ALIAS_AT_OID ) );
        assertNotNull( store.getOneAliasIndex() );

        assertNull( store.getSubAliasIndex() );
        store.addIndex( new JdbmIndex<Long, Attributes>( ApacheSchemaConstants.APACHE_SUB_ALIAS_AT_OID ) );
        assertNotNull( store.getSubAliasIndex() );

        assertNull( store.getSuffixDn() );
        store.setSuffixDn( EXAMPLE_COM );
        assertEquals( "dc=example,dc=com", store.getSuffixDn().getName() );

        assertNotNull( store.getSuffixDn() );

        assertEquals( 0, store.getUserIndices().size() );
        store.addIndex( new JdbmIndex<Object, Attributes>( "1.2.3.4" ) );
        assertEquals( 1, store.getUserIndices().size() );

        assertNull( store.getPartitionDir() );
        store.setPartitionDir( new File( "." ) );
        assertEquals( new File( "." ), store.getPartitionDir() );

        assertFalse( store.isInitialized() );
        assertTrue( store.isSyncOnWrite() );
        store.setSyncOnWrite( false );
        assertFalse( store.isSyncOnWrite() );

        store.sync();
        store.destroy();
    }


    @Test
    public void testSimplePropertiesLocked() throws Exception
    {
        assertNotNull( store.getAliasIndex() );
        try
        {
            store.addIndex( new JdbmIndex<String, Entry>( ApacheSchemaConstants.APACHE_ALIAS_AT_OID ) );
            fail();
        }
        catch ( IllegalStateException e )
        {
        }

        assertEquals( 10, store.getCacheSize() );
        try
        {
            store.setCacheSize( 24 );
        }
        catch ( IllegalStateException e )
        {
        }

        assertNotNull( store.getPresenceIndex() );
        try
        {
            store.addIndex( new JdbmIndex<String, Entry>( ApacheSchemaConstants.APACHE_EXISTENCE_AT_OID ) );
            fail();
        }
        catch ( IllegalStateException e )
        {
        }

        assertNotNull( store.getOneLevelIndex() );
        try
        {
            store.addIndex( new JdbmIndex<Long, Entry>( ApacheSchemaConstants.APACHE_ONE_LEVEL_AT_OID ) );
            fail();
        }
        catch ( IllegalStateException e )
        {
        }

        assertNotNull( store.getSubLevelIndex() );
        try
        {
            store.addIndex( new JdbmIndex<Long, Entry>( ApacheSchemaConstants.APACHE_SUB_LEVEL_AT_OID ) );
            fail();
        }
        catch ( IllegalStateException e )
        {
        }

        assertNotNull( store.getId() );
        try
        {
            store.setId( "foo" );
            fail();
        }
        catch ( IllegalStateException e )
        {
        }

        assertNotNull( store.getNdnIndex() );

        assertNotNull( store.getRdnIndex() );
        try
        {
            store.addIndex( new JdbmRdnIndex( ApacheSchemaConstants.APACHE_RDN_AT_OID ) );
            fail();
        }
        catch ( IllegalStateException e )
        {
        }

        assertNotNull( store.getOneAliasIndex() );
        try
        {
            store.addIndex( new JdbmIndex<Long, Entry>( ApacheSchemaConstants.APACHE_ONE_ALIAS_AT_OID ) );
            fail();
        }
        catch ( IllegalStateException e )
        {
        }

        assertNotNull( store.getSubAliasIndex() );
        try
        {
            store.addIndex( new JdbmIndex<Long, Entry>( ApacheSchemaConstants.APACHE_SUB_ALIAS_AT_OID ) );
            fail();
        }
        catch ( IllegalStateException e )
        {
        }

        assertNotNull( store.getSuffixDn() );
        try
        {
            store.setSuffixDn( EXAMPLE_COM );
            fail();
        }
        catch ( IllegalStateException e )
        {
        }

        Iterator<String> systemIndices = store.systemIndices();

        for ( int ii = 0; ii < 10; ii++ )
        {
            assertTrue( systemIndices.hasNext() );
            assertNotNull( systemIndices.next() );
        }

        assertFalse( systemIndices.hasNext() );
        assertNotNull( store.getSystemIndex( APACHE_ALIAS_AT ) );
        
        try
        {
            store.getSystemIndex( "bogus" );
            fail();
        }
        catch ( IndexNotFoundException e )
        {
        }
        try
        {
            store.getSystemIndex( DC_AT );
            fail();
        }
        catch ( IndexNotFoundException e )
        {
        }

        assertNotNull( store.getSuffixDn() );

        assertEquals( 2, store.getUserIndices().size() );
        assertFalse( store.hasUserIndexOn( "dc" ) );
        assertTrue( store.hasUserIndexOn( OU_AT ) );
        assertTrue( store.hasSystemIndexOn( APACHE_ALIAS_AT ) );
        Iterator<String> userIndices = store.userIndices();
        assertTrue( userIndices.hasNext() );
        assertNotNull( userIndices.next() );
        assertTrue( userIndices.hasNext() );
        assertNotNull( userIndices.next() );
        assertFalse( userIndices.hasNext() );
        assertNotNull( store.getUserIndex( OU_AT ) );
        try
        {
            store.getUserIndex( "bogus" );
            fail();
        }
        catch ( IndexNotFoundException e )
        {
        }
        try
        {
            store.getUserIndex( "dc" );
            fail();
        }
        catch ( IndexNotFoundException e )
        {
        }

        assertNotNull( store.getPartitionDir() );
        try
        {
            store.setPartitionDir( new File( "." ) );
            fail();
        }
        catch ( IllegalStateException e )
        {
        }

        assertTrue( store.isInitialized() );
        assertFalse( store.isSyncOnWrite() );

        store.sync();
    }


    @Test
    public void testPersistentProperties() throws Exception
    {
        assertNull( store.getProperty( "foo" ) );
        store.setProperty( "foo", "bar" );
        assertEquals( "bar", store.getProperty( "foo" ) );
    }


    @Test
    public void testFreshStore() throws Exception
    {
        DN dn = new DN( "o=Good Times Co.", schemaManager );
        assertEquals( 1L, ( long ) store.getEntryId( dn ) );
        assertEquals( 11, store.count() );
        assertEquals( "o=Good Times Co.", store.getEntryDn( 1L ).getName() );
        assertEquals( dn.getNormName(), store.getEntryDn( 1L ).getNormName() );
        assertEquals( dn.getName(), store.getEntryDn( 1L ).getName() );

        // note that the suffix entry returns 0 for it's parent which does not exist
        assertEquals( 0L, ( long ) store.getParentId( store.getEntryId( dn ) ) );
        assertNull( store.getParentId( 0L ) );

        // should NOW be allowed
        store.delete( 1L );
    }


    @Test
    public void testEntryOperations() throws Exception
    {
        assertEquals( 3, store.getChildCount( 1L ) );

        Cursor<IndexEntry<Long, Entry, Long>> cursor = store.list( 1L );
        assertNotNull( cursor );
        cursor.beforeFirst();
        assertTrue( cursor.next() );
        assertEquals( 2L, ( long ) cursor.get().getId() );
        assertTrue( cursor.next() );
        assertEquals( 3, store.getChildCount( 1L ) );

        store.delete( 2L );
        assertEquals( 2, store.getChildCount( 1L ) );
        assertEquals( 10, store.count() );

        // add an alias and delete to test dropAliasIndices method
        DN dn = new DN( "commonName=Jack Daniels,ou=Apache,ou=Board of Directors,o=Good Times Co.", schemaManager );
        Entry entry = new DefaultEntry( schemaManager, dn );
        entry.add( "objectClass", "top", "alias", "extensibleObject" );
        entry.add( "ou", "Apache" );
        entry.add( "commonName", "Jack Daniels" );
        entry.add( "aliasedObjectName", "cn=Jack Daniels,ou=Engineering,o=Good Times Co." );
        entry.add( "entryCSN", new CsnFactory( 1 ).newInstance().toString() );
        entry.add( "entryUUID", UUID.randomUUID().toString() );
        store.add( entry );

        store.delete( 12L ); // drops the alias indices

    }


    @Test
    public void testSubLevelIndex() throws Exception
    {
        Index idx = store.getSubLevelIndex();

        assertEquals( 19, idx.count() );

        Cursor<IndexEntry<Long, Attributes, Long>> cursor = idx.forwardCursor( 2L );

        assertTrue( cursor.next() );
        assertEquals( 2, ( long ) cursor.get().getId() );

        assertTrue( cursor.next() );
        assertEquals( 5, ( long ) cursor.get().getId() );

        assertTrue( cursor.next() );
        assertEquals( 6, ( long ) cursor.get().getId() );

        assertFalse( cursor.next() );

        idx.drop( 5L );

        cursor = idx.forwardCursor( 2L );

        assertTrue( cursor.next() );
        assertEquals( 2, ( long ) cursor.get().getId() );

        assertTrue( cursor.next() );
        assertEquals( 6, ( long ) cursor.get().getId() );

        assertFalse( cursor.next() );

        // dn id 12
        DN martinDn = new DN( "cn=Marting King,ou=Sales,o=Good Times Co.", schemaManager );
        Entry entry = new DefaultEntry( schemaManager, martinDn );
        entry.add( "objectClass", "top", "person", "organizationalPerson" );
        entry.add( "ou", "Sales" );
        entry.add( "cn", "Martin King" );
        entry.add( "entryCSN", new CsnFactory( 1 ).newInstance().toString() );
        entry.add( "entryUUID", UUID.randomUUID().toString() );
        store.add( entry );

        cursor = idx.forwardCursor( 2L );
        cursor.afterLast();
        assertTrue( cursor.previous() );
        assertEquals( 12, ( long ) cursor.get().getId() );

        DN newParentDn = new DN( "ou=Board of Directors,o=Good Times Co.", schemaManager );
        
        DN newDn = ((DN)newParentDn.clone()).add( martinDn.getRdn() );

        store.move( martinDn, newParentDn, newDn, entry );
        cursor = idx.forwardCursor( 3L );
        cursor.afterLast();
        assertTrue( cursor.previous() );
        assertEquals( 12, ( long ) cursor.get().getId() );

        // dn id 13
        DN marketingDn = new DN( "ou=Marketing,ou=Sales,o=Good Times Co.", schemaManager );
        entry = new DefaultEntry( schemaManager, marketingDn );
        entry.add( "objectClass", "top", "organizationalUnit" );
        entry.add( "ou", "Marketing" );
        entry.add( "entryCSN", new CsnFactory( 1 ).newInstance().toString() );
        entry.add( "entryUUID", UUID.randomUUID().toString() );
        store.add( entry );

        // dn id 14
        DN jimmyDn = new DN( "cn=Jimmy Wales,ou=Marketing, ou=Sales,o=Good Times Co.", schemaManager );
        entry = new DefaultEntry( schemaManager, jimmyDn );
        entry.add( "objectClass", "top", "person", "organizationalPerson" );
        entry.add( "ou", "Marketing" );
        entry.add( "cn", "Jimmy Wales" );
        entry.add( "entryCSN", new CsnFactory( 1 ).newInstance().toString() );
        entry.add( "entryUUID", UUID.randomUUID().toString() );
        store.add( entry );

        newDn = ((DN)newParentDn.clone()).add( marketingDn.getRdn() );

        store.move( marketingDn, newParentDn, newDn, entry );

        cursor = idx.forwardCursor( 3L );
        cursor.afterLast();

        assertTrue( cursor.previous() );
        assertEquals( 14, ( long ) cursor.get().getId() );

        assertTrue( cursor.previous() );
        assertEquals( 13, ( long ) cursor.get().getId() );

        assertTrue( cursor.previous() );
        assertEquals( 12, ( long ) cursor.get().getId() );

        assertTrue( cursor.previous() );
        assertEquals( 10, ( long ) cursor.get().getId() );

        assertTrue( cursor.previous() );
        assertEquals( 9, ( long ) cursor.get().getId() );

        assertTrue( cursor.previous() );
        assertEquals( 7, ( long ) cursor.get().getId() );

        assertTrue( cursor.previous() );
        assertEquals( 3, ( long ) cursor.get().getId() );

        assertFalse( cursor.previous() );
    }


    @Test
    public void testConvertIndex() throws Exception
    {
        // just create the new directory under working directory
        // so this gets cleaned up automatically
        File testSpecificDir = new File( wkdir, "testConvertIndex" );
        testSpecificDir.mkdirs();
        
        Index nonJdbmIndex = new GenericIndex( "ou", 10,  testSpecificDir );

        Method convertIndex = store.getClass().getDeclaredMethod( "convertAndInit", Index.class );
        convertIndex.setAccessible( true );
        Object obj = convertIndex.invoke( store, nonJdbmIndex );

        assertNotNull( obj );
        assertEquals( JdbmIndex.class, obj.getClass() );
    }


    @Test(expected = LdapNoSuchObjectException.class)
    public void testAddWithoutParentId() throws Exception
    {
        DN dn = new DN( "cn=Marting King,ou=Not Present,o=Good Times Co.", schemaManager );
        Entry entry = new DefaultEntry( schemaManager, dn );
        entry.add( "objectClass", "top", "person", "organizationalPerson" );
        entry.add( "ou", "Not Present" );
        entry.add( "cn", "Martin King" );
        store.add( entry );
    }


    @Test(expected = LdapSchemaViolationException.class)
    public void testAddWithoutObjectClass() throws Exception
    {
        DN dn = new DN( "cn=Martin King,ou=Sales,o=Good Times Co.", schemaManager );
        Entry entry = new DefaultEntry( schemaManager, dn );
        entry.add( "ou", "Sales" );
        entry.add( "cn", "Martin King" );
        store.add( entry );
    }


    @Test
    public void testModifyAddOUAttrib() throws Exception
    {
        DN dn = new DN( "cn=JOhnny WAlkeR,ou=Sales,o=Good Times Co.", schemaManager );

        List<Modification> mods = new ArrayList<Modification>();
        EntryAttribute attrib = new DefaultEntryAttribute( SchemaConstants.OU_AT, OU_AT );
        attrib.add( "Engineering" );

        Modification add = new DefaultModification( ModificationOperation.ADD_ATTRIBUTE, attrib );

        mods.add( add );

        store.modify( dn, mods );
    }


    @Test
    public void testRename() throws Exception
    {
        DN dn = new DN( "cn=Pivate Ryan,ou=Engineering,o=Good Times Co.", schemaManager );
        Entry entry = new DefaultEntry( schemaManager, dn );
        entry.add( "objectClass", "top", "person", "organizationalPerson" );
        entry.add( "ou", "Engineering" );
        entry.add( "cn", "Private Ryan" );
        entry.add( "entryCSN", new CsnFactory( 1 ).newInstance().toString() );
        entry.add( "entryUUID", UUID.randomUUID().toString() );

        store.add( entry );

        RDN rdn = new RDN( "sn=James" );

        store.rename( dn, rdn, true );
    }


    @Test
    public void testRenameEscaped() throws Exception
    {
        DN dn = new DN( "cn=Pivate Ryan,ou=Engineering,o=Good Times Co.", schemaManager );
        Entry entry = new DefaultEntry( schemaManager, dn );
        entry.add( "objectClass", "top", "person", "organizationalPerson" );
        entry.add( "ou", "Engineering" );
        entry.add( "cn", "Private Ryan" );
        entry.add( "entryCSN", new CsnFactory( 1 ).newInstance().toString() );
        entry.add( "entryUUID", UUID.randomUUID().toString() );

        store.add( entry );

        RDN rdn = new RDN( "sn=Ja\\+es" );

        store.rename( dn, rdn, true );

        DN dn2 = new DN( "sn=Ja\\+es,ou=Engineering,o=Good Times Co.", schemaManager );
        Long id = store.getEntryId( dn2 );
        assertNotNull( id );
        Entry entry2 = store.lookup( id );
        assertEquals( "Ja+es", entry2.get( "sn" ).getString() );
    }


    @Test
    public void testMove() throws Exception
    {
        DN childDn = new DN( "cn=Pivate Ryan,ou=Engineering,o=Good Times Co.", schemaManager );
        Entry childEntry = new DefaultEntry( schemaManager, childDn );
        childEntry.add( "objectClass", "top", "person", "organizationalPerson" );
        childEntry.add( "ou", "Engineering" );
        childEntry.add( "cn", "Private Ryan" );
        childEntry.add( "entryCSN", new CsnFactory( 1 ).newInstance().toString() );
        childEntry.add( "entryUUID", UUID.randomUUID().toString() );

        store.add( childEntry );

        DN parentDn = new DN( "ou=Sales,o=Good Times Co.", schemaManager );

        RDN rdn = new RDN( "cn=Ryan" );

        store.moveAndRename( childDn, parentDn, rdn, childEntry, true );

        // to drop the alias indices   
        childDn = new DN( "commonName=Jim Bean,ou=Apache,ou=Board of Directors,o=Good Times Co.", schemaManager );

        parentDn = new DN( "ou=Engineering,o=Good Times Co.", schemaManager );

        assertEquals( 3, store.getSubAliasIndex().count() );

        DN newDn = ((DN)parentDn.clone()).add( childDn.getRdn() );
        
        store.move( childDn, parentDn, newDn, childEntry );

        assertEquals( 4, store.getSubAliasIndex().count() );
    }


    @Test
    public void testModifyAdd() throws Exception
    {
        DN dn = new DN( "cn=JOhnny WAlkeR,ou=Sales,o=Good Times Co.", schemaManager );

        List<Modification> mods = new ArrayList<Modification>();
        EntryAttribute attrib = new DefaultEntryAttribute( SchemaConstants.SURNAME_AT, SN_AT );

        String attribVal = "Walker";
        attrib.add( attribVal );

        Modification add = new DefaultModification( ModificationOperation.ADD_ATTRIBUTE, attrib );
        mods.add( add );

        Entry lookedup = store.lookup( store.getEntryId( dn ) );

        store.modify( dn, mods );
        assertTrue( lookedup.get( "sn" ).contains( attribVal ) );

        // testing the store.modify( dn, mod, entry ) API
        Entry entry = new DefaultEntry( schemaManager, dn );
        attribVal = "+1974045779";
        entry.add( "telephoneNumber", attribVal );

        store.modify( dn, ModificationOperation.ADD_ATTRIBUTE, entry );
        lookedup = store.lookup( store.getEntryId( dn ) );
        assertTrue( lookedup.get( "telephoneNumber" ).contains( attribVal ) );
    }


    @Test
    public void testModifyReplace() throws Exception
    {
        DN dn = new DN( "cn=JOhnny WAlkeR,ou=Sales,o=Good Times Co.", schemaManager );

        List<Modification> mods = new ArrayList<Modification>();
        EntryAttribute attrib = new DefaultEntryAttribute( SchemaConstants.SN_AT, SN_AT );

        String attribVal = "Johnny";
        attrib.add( attribVal );

        Modification add = new DefaultModification( ModificationOperation.REPLACE_ATTRIBUTE, attrib );
        mods.add( add );

        Entry lookedup = store.lookup( store.getEntryId( dn ) );

        assertEquals( "WAlkeR", lookedup.get( "sn" ).get().getString() ); // before replacing

        store.modify( dn, mods );
        assertEquals( attribVal, lookedup.get( "sn" ).get().getString() );

        // testing the store.modify( dn, mod, entry ) API
        Entry entry = new DefaultEntry( schemaManager, dn );
        attribVal = "JWalker";
        entry.add( "sn", attribVal );

        store.modify( dn, ModificationOperation.REPLACE_ATTRIBUTE, entry );
        assertEquals( attribVal, lookedup.get( "sn" ).get().getString() );
    }


    @Test
    public void testModifyRemove() throws Exception
    {
        DN dn = new DN( "cn=JOhnny WAlkeR,ou=Sales,o=Good Times Co.", schemaManager );

        List<Modification> mods = new ArrayList<Modification>();
        EntryAttribute attrib = new DefaultEntryAttribute( SchemaConstants.SN_AT, SN_AT );

        Modification add = new DefaultModification( ModificationOperation.REMOVE_ATTRIBUTE, attrib );
        mods.add( add );

        Entry lookedup = store.lookup( store.getEntryId( dn ) );

        assertNotNull( lookedup.get( "sn" ).get() );

        store.modify( dn, mods );
        assertNull( lookedup.get( "sn" ) );

        // testing the store.modify( dn, mod, entry ) API
        Entry entry = new DefaultEntry( schemaManager, dn );

        // add an entry for the sake of testing the remove operation
        entry.add( "sn", "JWalker" );
        store.modify( dn, ModificationOperation.ADD_ATTRIBUTE, entry );
        assertNotNull( lookedup.get( "sn" ) );

        store.modify( dn, ModificationOperation.REMOVE_ATTRIBUTE, entry );
        assertNull( lookedup.get( "sn" ) );
    }


    @Test
    public void testModifyReplaceNonExistingIndexAttribute() throws Exception
    {
        DN dn = new DN( "cn=Tim B,ou=Sales,o=Good Times Co.", schemaManager );
        Entry entry = new DefaultEntry( schemaManager, dn );
        entry.add( "objectClass", "top", "person", "organizationalPerson" );
        entry.add( "cn", "Tim B" );
        entry.add( "entryCSN", new CsnFactory( 1 ).newInstance().toString() );
        entry.add( "entryUUID", UUID.randomUUID().toString() );

        store.add( entry );

        List<Modification> mods = new ArrayList<Modification>();
        EntryAttribute attrib = new DefaultEntryAttribute( SchemaConstants.OU_AT, OU_AT );

        String attribVal = "Marketing";
        attrib.add( attribVal );

        Modification add = new DefaultModification( ModificationOperation.REPLACE_ATTRIBUTE, attrib );
        mods.add( add );

        Entry lookedup = store.lookup( store.getEntryId( dn ) );

        assertNull( lookedup.get( "ou" ) ); // before replacing

        store.modify( dn, mods );
        assertEquals( attribVal, lookedup.get( "ou" ).get().getString() );
    }
    
    
    @Test
    public void testDeleteUnusedIndexFiles() throws Exception
    {
        File ouIndexDbFile = new File( wkdir, SchemaConstants.OU_AT_OID + ".db" );
        File ouIndexTxtFile = new File( wkdir, SchemaConstants.OU_AT_OID + "-ou.txt" );
        File uuidIndexDbFile = new File( wkdir, SchemaConstants.ENTRY_UUID_AT_OID + ".db" );
        File uuidIndexTxtFile = new File( wkdir, SchemaConstants.ENTRY_UUID_AT_OID + "-entryUUID.txt" );
        
        assertTrue( ouIndexDbFile.exists() );
        assertTrue( ouIndexTxtFile.exists() );
        assertTrue( uuidIndexDbFile.exists() );
        assertTrue( uuidIndexTxtFile.exists() );
        
        // destroy the store to manually start the init phase
        // by keeping the same work dir
        store.destroy();
        
        // just assert again that ou and entryUUID files exist even after destroying the store
        assertTrue( ouIndexDbFile.exists() );
        assertTrue( ouIndexTxtFile.exists() );
        assertTrue( uuidIndexDbFile.exists() );
        assertTrue( uuidIndexTxtFile.exists() );
        
        store = new JdbmStore<Entry>();
        store.setId( "example" );
        store.setCacheSize( 10 );
        store.setPartitionDir( wkdir );
        store.setSyncOnWrite( false );
        // do not add ou index this time
        store.addIndex( new JdbmIndex( SchemaConstants.UID_AT_OID ) );

        
        DN suffixDn = new DN( "o=Good Times Co.", schemaManager );
        store.setSuffixDn( suffixDn );
        // init the store to call deleteUnusedIndexFiles() method
        store.init( schemaManager );

        assertFalse( ouIndexDbFile.exists() );
        assertFalse( ouIndexTxtFile.exists() );

        assertTrue( uuidIndexDbFile.exists() );
        assertTrue( uuidIndexTxtFile.exists() );
    }
    
}
