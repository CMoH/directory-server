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
package org.apache.directory.server.core.admin;


import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import javax.naming.directory.SearchControls;

import org.apache.directory.server.constants.ServerDNConstants;
import org.apache.directory.server.core.CoreSession;
import org.apache.directory.server.core.DefaultCoreSession;
import org.apache.directory.server.core.DirectoryService;
import org.apache.directory.server.core.LdapPrincipal;
import org.apache.directory.server.core.administrative.AccessControlAAP;
import org.apache.directory.server.core.administrative.AccessControlAdministrativePoint;
import org.apache.directory.server.core.administrative.AccessControlIAP;
import org.apache.directory.server.core.administrative.AccessControlSAP;
import org.apache.directory.server.core.administrative.AdministrativePoint;
import org.apache.directory.server.core.administrative.CollectiveAttributeAAP;
import org.apache.directory.server.core.administrative.CollectiveAttributeAdministrativePoint;
import org.apache.directory.server.core.administrative.CollectiveAttributeIAP;
import org.apache.directory.server.core.administrative.CollectiveAttributeSAP;
import org.apache.directory.server.core.administrative.SubschemaAAP;
import org.apache.directory.server.core.administrative.SubschemaAdministrativePoint;
import org.apache.directory.server.core.administrative.SubschemaSAP;
import org.apache.directory.server.core.administrative.TriggerExecutionAAP;
import org.apache.directory.server.core.administrative.TriggerExecutionAdministrativePoint;
import org.apache.directory.server.core.administrative.TriggerExecutionIAP;
import org.apache.directory.server.core.administrative.TriggerExecutionSAP;
import org.apache.directory.server.core.authn.Authenticator;
import org.apache.directory.server.core.filtering.EntryFilteringCursor;
import org.apache.directory.server.core.interceptor.BaseInterceptor;
import org.apache.directory.server.core.interceptor.Interceptor;
import org.apache.directory.server.core.interceptor.NextInterceptor;
import org.apache.directory.server.core.interceptor.context.AddOperationContext;
import org.apache.directory.server.core.interceptor.context.DeleteOperationContext;
import org.apache.directory.server.core.interceptor.context.ModifyOperationContext;
import org.apache.directory.server.core.interceptor.context.MoveAndRenameOperationContext;
import org.apache.directory.server.core.interceptor.context.MoveOperationContext;
import org.apache.directory.server.core.interceptor.context.RenameOperationContext;
import org.apache.directory.server.core.interceptor.context.SearchOperationContext;
import org.apache.directory.server.core.partition.PartitionNexus;
import org.apache.directory.shared.ldap.constants.AuthenticationLevel;
import org.apache.directory.shared.ldap.constants.SchemaConstants;
import org.apache.directory.shared.ldap.entry.DefaultEntryAttribute;
import org.apache.directory.shared.ldap.entry.Entry;
import org.apache.directory.shared.ldap.entry.EntryAttribute;
import org.apache.directory.shared.ldap.entry.Modification;
import org.apache.directory.shared.ldap.entry.Value;
import org.apache.directory.shared.ldap.exception.LdapException;
import org.apache.directory.shared.ldap.exception.LdapInvalidAttributeValueException;
import org.apache.directory.shared.ldap.exception.LdapNoSuchAttributeException;
import org.apache.directory.shared.ldap.exception.LdapOperationException;
import org.apache.directory.shared.ldap.exception.LdapUnwillingToPerformException;
import org.apache.directory.shared.ldap.filter.ExprNode;
import org.apache.directory.shared.ldap.filter.PresenceNode;
import org.apache.directory.shared.ldap.message.AliasDerefMode;
import org.apache.directory.shared.ldap.message.ResultCodeEnum;
import org.apache.directory.shared.ldap.name.DN;
import org.apache.directory.shared.ldap.schema.AttributeType;
import org.apache.directory.shared.ldap.schema.SchemaManager;
import org.apache.directory.shared.ldap.subtree.AdministrativeRole;
import org.apache.directory.shared.ldap.util.StringTools;
import org.apache.directory.shared.ldap.util.tree.DnNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


/**
 * An interceptor to manage the Administrative model
 *
 * @author <a href="mailto:dev@directory.apache.org">Apache Directory Project</a>
 */
public class AdministrativePointInterceptor extends BaseInterceptor
{
    /** A {@link Logger} for this class */
    private static final Logger LOG = LoggerFactory.getLogger( AdministrativePointInterceptor.class );

    /**
     * Speedup for logs
     */
    private static final boolean IS_DEBUG = LOG.isDebugEnabled();

    /** A reference to the DirectoryService instance */
    private DirectoryService directoryService;

    /** A reference to the SchemaManager instance */
    private SchemaManager schemaManager;

    /** A reference to the nexus for direct backend operations */
    private PartitionNexus nexus;

    /** A reference to the AdministrativeRole AT */
    private static AttributeType ADMINISTRATIVE_ROLE_AT;

    /** A reference to the EntryUUID AT */
    private static AttributeType ENTRY_UUID_AT;

    /** The possible roles */
    private static final Set<String> ROLES = new HashSet<String>();

    // Initialize the ROLES field
    static
    {
        ROLES.add( SchemaConstants.AUTONOMOUS_AREA.toLowerCase() );
        ROLES.add( SchemaConstants.AUTONOMOUS_AREA_OID );
        ROLES.add( SchemaConstants.ACCESS_CONTROL_SPECIFIC_AREA.toLowerCase() );
        ROLES.add( SchemaConstants.ACCESS_CONTROL_SPECIFIC_AREA_OID );
        ROLES.add( SchemaConstants.ACCESS_CONTROL_INNER_AREA.toLowerCase() );
        ROLES.add( SchemaConstants.ACCESS_CONTROL_INNER_AREA_OID );
        ROLES.add( SchemaConstants.COLLECTIVE_ATTRIBUTE_SPECIFIC_AREA.toLowerCase() );
        ROLES.add( SchemaConstants.COLLECTIVE_ATTRIBUTE_SPECIFIC_AREA_OID );
        ROLES.add( SchemaConstants.COLLECTIVE_ATTRIBUTE_INNER_AREA.toLowerCase() );
        ROLES.add( SchemaConstants.COLLECTIVE_ATTRIBUTE_INNER_AREA_OID );
        ROLES.add( SchemaConstants.SUB_SCHEMA_ADMIN_SPECIFIC_AREA.toLowerCase() );
        ROLES.add( SchemaConstants.SUB_SCHEMA_ADMIN_SPECIFIC_AREA_OID );
        ROLES.add( SchemaConstants.TRIGGER_EXECUTION_SPECIFIC_AREA.toLowerCase() );
        ROLES.add( SchemaConstants.TRIGGER_EXECUTION_SPECIFIC_AREA_OID );
        ROLES.add( SchemaConstants.TRIGGER_EXECUTION_INNER_AREA.toLowerCase() );
        ROLES.add( SchemaConstants.TRIGGER_EXECUTION_INNER_AREA_OID );
    }

    /** A Map to associate a role with it's OID */
    private static final Map<String, String> ROLES_OID = new HashMap<String, String>();

    // Initialize the roles/oid map
    static
    {
        ROLES_OID.put( SchemaConstants.AUTONOMOUS_AREA.toLowerCase(), SchemaConstants.AUTONOMOUS_AREA_OID );
        ROLES_OID.put( SchemaConstants.ACCESS_CONTROL_SPECIFIC_AREA.toLowerCase(),
            SchemaConstants.ACCESS_CONTROL_SPECIFIC_AREA_OID );
        ROLES_OID.put( SchemaConstants.ACCESS_CONTROL_INNER_AREA.toLowerCase(),
            SchemaConstants.ACCESS_CONTROL_INNER_AREA_OID );
        ROLES_OID.put( SchemaConstants.COLLECTIVE_ATTRIBUTE_SPECIFIC_AREA.toLowerCase(),
            SchemaConstants.COLLECTIVE_ATTRIBUTE_SPECIFIC_AREA_OID );
        ROLES_OID.put( SchemaConstants.COLLECTIVE_ATTRIBUTE_INNER_AREA.toLowerCase(),
            SchemaConstants.COLLECTIVE_ATTRIBUTE_INNER_AREA_OID );
        ROLES_OID.put( SchemaConstants.SUB_SCHEMA_ADMIN_SPECIFIC_AREA.toLowerCase(),
            SchemaConstants.SUB_SCHEMA_ADMIN_SPECIFIC_AREA_OID );
        ROLES_OID.put( SchemaConstants.TRIGGER_EXECUTION_SPECIFIC_AREA.toLowerCase(),
            SchemaConstants.TRIGGER_EXECUTION_SPECIFIC_AREA_OID );
        ROLES_OID.put( SchemaConstants.TRIGGER_EXECUTION_INNER_AREA.toLowerCase(),
            SchemaConstants.TRIGGER_EXECUTION_INNER_AREA_OID );
    }

    /** The possible inner area roles */
    private static final Set<String> INNER_AREA_ROLES = new HashSet<String>();

    static
    {
        INNER_AREA_ROLES.add( SchemaConstants.ACCESS_CONTROL_INNER_AREA.toLowerCase() );
        INNER_AREA_ROLES.add( SchemaConstants.ACCESS_CONTROL_INNER_AREA_OID );
        INNER_AREA_ROLES.add( SchemaConstants.COLLECTIVE_ATTRIBUTE_INNER_AREA.toLowerCase() );
        INNER_AREA_ROLES.add( SchemaConstants.COLLECTIVE_ATTRIBUTE_INNER_AREA_OID );
        INNER_AREA_ROLES.add( SchemaConstants.TRIGGER_EXECUTION_INNER_AREA.toLowerCase() );
        INNER_AREA_ROLES.add( SchemaConstants.TRIGGER_EXECUTION_INNER_AREA_OID );
    }

    /** The possible specific area roles */
    private static final Set<String> SPECIFIC_AREA_ROLES = new HashSet<String>();

    static
    {
        SPECIFIC_AREA_ROLES.add( SchemaConstants.ACCESS_CONTROL_SPECIFIC_AREA.toLowerCase() );
        SPECIFIC_AREA_ROLES.add( SchemaConstants.ACCESS_CONTROL_SPECIFIC_AREA_OID );
        SPECIFIC_AREA_ROLES.add( SchemaConstants.COLLECTIVE_ATTRIBUTE_SPECIFIC_AREA.toLowerCase() );
        SPECIFIC_AREA_ROLES.add( SchemaConstants.COLLECTIVE_ATTRIBUTE_SPECIFIC_AREA_OID );
        SPECIFIC_AREA_ROLES.add( SchemaConstants.SUB_SCHEMA_ADMIN_SPECIFIC_AREA.toLowerCase() );
        SPECIFIC_AREA_ROLES.add( SchemaConstants.SUB_SCHEMA_ADMIN_SPECIFIC_AREA_OID );
        SPECIFIC_AREA_ROLES.add( SchemaConstants.TRIGGER_EXECUTION_SPECIFIC_AREA.toLowerCase() );
        SPECIFIC_AREA_ROLES.add( SchemaConstants.TRIGGER_EXECUTION_SPECIFIC_AREA_OID );
    }

    /** A lock to guarantee the AP cache consistency */
    private ReentrantReadWriteLock mutex = new ReentrantReadWriteLock();

    /**
     * Get a read-lock on the AP cache.
     * No read operation can be done on the AP cache if this
     * method is not called before.
     */
    public void lockRead()
    {
        mutex.readLock().lock();
    }


    /**
     * Get a write-lock on the AP cache.
     * No write operation can be done on the apCache if this
     * method is not called before.
     */
    public void lockWrite()
    {
        mutex.writeLock().lock();
    }


    /**
     * Release the read-write lock on the AP cache.
     * This method must be called after having read or modified the
     * AP cache
     */
    public void unlock()
    {
        if ( mutex.isWriteLockedByCurrentThread() )
        {
            mutex.writeLock().unlock();
        }
        else
        {
            mutex.readLock().unlock();
        }
    }


    /**
     * Create the list of AP for a given entry
     */
    private void createAdministrativePoints( EntryAttribute adminPoint, DN dn, String uuid ) throws LdapException
    {
        if ( isAAP( adminPoint ) )
        {
            // The AC AAP
            AccessControlAdministrativePoint acAap = new AccessControlAAP( dn, uuid );
            directoryService.getAccessControlAPCache().add( dn, acAap );

            // The CA AAP
            CollectiveAttributeAdministrativePoint caAap = new CollectiveAttributeAAP( dn, uuid );
            directoryService.getCollectiveAttributeAPCache().add( dn, caAap );

            // The TE AAP
            TriggerExecutionAdministrativePoint teAap = new TriggerExecutionAAP( dn, uuid );
            directoryService.getTriggerExecutionAPCache().add( dn, teAap );

            // The SS AAP
            SubschemaAdministrativePoint ssAap = new SubschemaAAP( dn, uuid );
            directoryService.getSubschemaAPCache().add( dn, ssAap );

            // TODO : Here, we have to update the children, removing any 
            // reference to any other underlying AP

            // If it's an AAP, we can get out immediately
            return;
        }

        for ( Value<?> value : adminPoint )
        {
            String role = value.getString();

            // Deal with AccessControl AP
            if ( isAccessControlSpecificRole( role ) )
            {
                AccessControlAdministrativePoint sap = new AccessControlSAP( dn, uuid );
                directoryService.getAccessControlAPCache().add( dn, sap );

                // TODO : Here, we have to update the children, removing any 
                // reference to any other underlying AccessControl IAP or SAP 

                continue;
            }

            if ( isAccessControlInnerRole( role ) )
            {
                AccessControlAdministrativePoint iap = new AccessControlIAP( dn, uuid );
                directoryService.getAccessControlAPCache().add( dn, iap );

                continue;
            }

            // Deal with CollectiveAttribute AP
            if ( isCollectiveAttributeSpecificRole( role ) )
            {
                CollectiveAttributeAdministrativePoint sap = new CollectiveAttributeSAP( dn, uuid );
                directoryService.getCollectiveAttributeAPCache().add( dn, sap );

                // TODO : Here, we have to update the children, removing any 
                // reference to any other underlying CollectiveAttribute IAP or SAP 
                
                continue;
            }

            if ( isCollectiveAttributeInnerRole( role ) )
            {
                CollectiveAttributeAdministrativePoint iap = new CollectiveAttributeIAP( dn, uuid );
                directoryService.getCollectiveAttributeAPCache().add( dn, iap );

                continue;
            }

            // Deal with SubSchema AP
            if ( isSubschemaSpecficRole( role ) )
            {
                SubschemaAdministrativePoint sap = new SubschemaSAP( dn, uuid );
                directoryService.getSubschemaAPCache().add( dn, sap );

                // TODO : Here, we have to update the children, removing any 
                // reference to any other underlying Subschema IAP or SAP 

                continue;
            }

            // Deal with TriggerExecution AP
            if ( isTriggerExecutionSpecificRole( role ) )
            {
                TriggerExecutionAdministrativePoint sap = new TriggerExecutionSAP( dn, uuid );
                directoryService.getTriggerExecutionAPCache().add( dn, sap );

                // TODO : Here, we have to update the children, removing any 
                // reference to any other underlying TriggerExecution IAP or SAP
                
                continue;
            }

            if ( isTriggerExecutionInnerRole( role ) )
            {
                TriggerExecutionAdministrativePoint iap = new TriggerExecutionIAP( dn, uuid );
                directoryService.getTriggerExecutionAPCache().add( dn, iap );

                continue;
            }
        }

        return;
    }


    /**
     * Update the cache clones with the added roles
     */
    private void addRole( String role, DN dn, String uuid, DnNode<AccessControlAdministrativePoint> acapCache,
        DnNode<CollectiveAttributeAdministrativePoint> caapCache, DnNode<TriggerExecutionAdministrativePoint> teapCache,
        DnNode<SubschemaAdministrativePoint> ssapCache ) throws LdapException
    {
        // Deal with Autonomous AP : create the 4 associated SAP/AAP
        if ( isAutonomousAreaRole( role ) )
        {
            // The AC AAP
            AccessControlAdministrativePoint acAap = new AccessControlAAP( dn, uuid );
            acapCache.add( dn, acAap );

            // The CA AAP
            CollectiveAttributeAdministrativePoint caAap = new CollectiveAttributeAAP( dn, uuid );
            caapCache.add( dn, caAap );

            // The TE AAP
            TriggerExecutionAdministrativePoint teAap = new TriggerExecutionAAP( dn, uuid );
            teapCache.add( dn, teAap );

            // The SS AAP
            SubschemaAdministrativePoint ssAap = new SubschemaAAP( dn, uuid );
            ssapCache.add( dn, ssAap );
            
            // If it's an AAP, we can get out immediately
            return;
        }

        // Deal with AccessControl AP
        if ( isAccessControlSpecificRole( role ) )
        {
            AccessControlAdministrativePoint sap = new AccessControlSAP( dn, uuid );
            acapCache.add( dn, sap );

            return;
        }

        if ( isAccessControlInnerRole( role ) )
        {
            AccessControlAdministrativePoint iap = new AccessControlIAP( dn, uuid );
            acapCache.add( dn, iap );

            return;
        }

        // Deal with CollectiveAttribute AP
        if ( isCollectiveAttributeSpecificRole( role ) )
        {
            CollectiveAttributeAdministrativePoint sap = new CollectiveAttributeSAP( dn, uuid );
            caapCache.add( dn, sap );

            return;
        }

        if ( isCollectiveAttributeInnerRole( role ) )
        {
            CollectiveAttributeAdministrativePoint iap = new CollectiveAttributeIAP( dn, uuid );
            caapCache.add( dn, iap );

            return;
        }

        // Deal with SubSchema AP
        if ( isSubschemaSpecficRole( role ) )
        {
            SubschemaAdministrativePoint sap = new SubschemaSAP( dn, uuid );
            ssapCache.add( dn, sap );

            return;
        }

        // Deal with TriggerExecution AP
        if ( isTriggerExecutionSpecificRole( role ) )
        {
            TriggerExecutionAdministrativePoint sap = new TriggerExecutionSAP( dn, uuid );
            teapCache.add( dn, sap );

            return;
        }

        if ( isTriggerExecutionInnerRole( role ) )
        {
            TriggerExecutionAdministrativePoint iap = new TriggerExecutionIAP( dn, uuid );
            teapCache.add( dn, iap );

            return;
        }
    }




    /**
     * Update the cache clones with the added roles
     */
    private void delRole( String role, DN dn, String uuid, DnNode<AccessControlAdministrativePoint> acapCache,
        DnNode<CollectiveAttributeAdministrativePoint> caapCache, DnNode<TriggerExecutionAdministrativePoint> teapCache,
        DnNode<SubschemaAdministrativePoint> ssapCache ) throws LdapException
    {
        // Deal with Autonomous AP : remove the 4 associated SAP/AAP
        if ( isAutonomousAreaRole( role ) )
        {
            // The AC AAP
            acapCache.remove( dn );

            // The CA AAP
            caapCache.remove( dn );

            // The TE AAP
            teapCache.remove( dn );

            // The SS AAP
            ssapCache.remove( dn );
            
            return;
        }

        // Deal with AccessControl AP
        if ( isAccessControlSpecificRole( role ) || isAccessControlInnerRole( role ) )
        {
            acapCache.remove( dn );

            return;
        }

        // Deal with CollectiveAttribute AP
        if ( isCollectiveAttributeSpecificRole( role ) || isCollectiveAttributeInnerRole( role ) )
        {
            caapCache.remove( dn );

            return;
        }

        // Deal with SubSchema AP
        if ( isSubschemaSpecficRole( role ) )
        {
            ssapCache.remove( dn );

            return;
        }

        // Deal with TriggerExecution AP
        if ( isTriggerExecutionSpecificRole( role ) || isTriggerExecutionInnerRole( role ) )
        {
            teapCache.remove( dn );

            return;
        }
    }

    
    private AdministrativePoint getParent( AdministrativePoint ap, List<AdministrativePoint> aps,
        AdministrativeRole role, DnNode<List<AdministrativePoint>> currentNode )
    {
        AdministrativePoint parent = null;

        for ( AdministrativePoint adminPoint : aps )
        {
            if ( adminPoint.isAutonomous() || ( adminPoint.getRole() == ap.getRole() ) )
            {
                // Same role or AP : this is the parent
                return adminPoint;
            }
            else if ( adminPoint.getRole() == role )
            {
                parent = adminPoint;
            }
        }

        if ( parent != null )
        {
            return parent;
        }

        // We have to go down one level
        if ( currentNode.hasParent() )
        {
            return findParent( ap, currentNode );
        }
        else
        {
            return null;
        }
    }


    /**
     * Find the parent for the given administrative point. If the AP is an AAP, the parent will be the closest
     * AAP or the closest SAP. If we have a SAP between the added AAP and a AAP, then 
     */
    private AdministrativePoint findParent( AdministrativePoint ap, DnNode<List<AdministrativePoint>> currentNode )
    {
        List<AdministrativePoint> aps = currentNode.getElement();

        if ( aps != null )
        {
            // Check if the current element is a valid parent
            switch ( ap.getRole() )
            {
                case AutonomousArea:
                    AdministrativePoint currentAp = aps.get( 0 );

                    if ( currentAp.isAutonomous() )
                    {
                        return currentAp;
                    }
                    else
                    {
                        // We have to go down one level, as an AAP
                        // must have another AAP as a parent
                        if ( currentNode.hasParent() )
                        {
                            return findParent( ap, currentNode );
                        }
                        else
                        {
                            return null;
                        }
                    }

                case AccessControlInnerArea:
                    return getParent( ap, aps, AdministrativeRole.AccessControlSpecificArea, currentNode );

                case CollectiveAttributeInnerArea:
                    return getParent( ap, aps, AdministrativeRole.CollectiveAttributeSpecificArea, currentNode );

                case TriggerExecutionInnerArea:
                    return getParent( ap, aps, AdministrativeRole.TriggerExecutionSpecificArea, currentNode );

                case AccessControlSpecificArea:
                    return getParent( ap, aps, AdministrativeRole.AccessControlSpecificArea, currentNode );

                case CollectiveAttributeSpecificArea:
                    return getParent( ap, aps, AdministrativeRole.CollectiveAttributeSpecificArea, currentNode );

                case SubSchemaSpecificArea:
                    return getParent( ap, aps, AdministrativeRole.SubSchemaSpecificArea, currentNode );

                case TriggerExecutionSpecificArea:
                    return getParent( ap, aps, AdministrativeRole.TriggerExecutionSpecificArea, currentNode );

                default:
                    return null;
            }
        }
        else
        {
            if ( currentNode.hasParent() )
            {
                return findParent( ap, currentNode.getParent() );
            }
            else
            {
                return null;
            }
        }
    }


    /**
     * Check if we can safely add a role. If it's an AAP, we have to be sure that
     * it's the only role present in the AT.
     */
    private void checkAddRole( Value<?> role, EntryAttribute adminPoint, DN dn ) throws LdapException
    {
        String roleStr = StringTools.toLowerCase( StringTools.trim( role.getString() ) );

        // Check that the added AdministrativeRole is valid
        if ( !ROLES.contains( roleStr ) )
        {
            String message = "Cannot add the given role, it's not a valid one :" + role;
            LOG.error( message );
            throw new LdapUnwillingToPerformException( message );
        }

        // If we are trying to add an AAP, we have to check that 
        // it's the only role in the AdminPoint AT
        if ( isAutonomousAreaRole( roleStr ) )
        {
            if ( adminPoint.size() > 1 )
            {
                String message = "Cannot add an Autonomous Administratve Point when some other roles are added : "
                    + adminPoint;
                LOG.error( message );
                throw new LdapUnwillingToPerformException( message );
            }
            else
            {
                // Fine : we only have one AAP
                return;
            }
        }
        
        // Check that we don't have already an AAP in the AdminPoint AT when we try to
        // add a role
        if ( adminPoint.contains( SchemaConstants.AUTONOMOUS_AREA ) )
        {
            String message = "Cannot add a role when an Autonomous Administratve Point is already present : "
                + adminPoint;
            LOG.error( message );
            throw new LdapUnwillingToPerformException( message );
        }

        // check that we can't mix Inner and Specific areas
        checkInnerSpecificMix( roleStr, adminPoint );

        // Check that we don't add an IAP with no parent. The IAP must be added under
        // either a AAP, or a SAP/IAP within the same family
        if ( isIAP( roleStr ) )
        {
            checkIAPHasParent( roleStr, adminPoint, dn );
        }
    }


    /**
     * Check if we can safely delete a role
     */
    private void checkDelRole( Value<?> role, EntryAttribute adminPoint, DN dn ) throws LdapException
    {
        String roleStr = StringTools.toLowerCase( StringTools.trim( role.getString() ) );

        // Check that the removed AdministrativeRole is valid
        if ( !ROLES.contains( roleStr ) )
        {
            String message = "Cannot delete the given role, it's not a valid one :" + role;
            LOG.error( message );
            throw new LdapUnwillingToPerformException( message );
        }

        // Now we are trying to delete an Administrative point. We have to check that 
        // we only have one role if the deleted role is an AAP
        if ( isAutonomousAreaRole( roleStr ) )
        {
            // We know have to check that removing the AAP, we will not 
            // left any pending IAP. We should check for the 3 potential IAPs :
            // AccessControl, CollectiveAttribute and TriggerExecution.
            // If the removed AP has a parent, no need to go any further :
            // the children IAPs will depend on this parent.
            
            // Process the ACs
            DnNode<AccessControlAdministrativePoint> acAps = directoryService.getAccessControlAPCache();
            
            if ( !acAps.hasParent( dn ) )
            {
                // No parent, check for any IAP
                List<AccessControlAdministrativePoint> children = acAps.getDescendantElements( dn );
                
                for ( AccessControlAdministrativePoint child : children )
                {
                    if ( child.isInner() )
                    {
                        // Ok, we are dead : the IAP will remain with no parent.
                        String message = "Cannot delete the given role, the " + child.getDn() + " AccessControl IAP will remain orphan";
                        LOG.error( message );
                        throw new LdapUnwillingToPerformException( message );
                    }
                }
            }
            
            // Process the CAs
            DnNode<CollectiveAttributeAdministrativePoint> caAps = directoryService.getCollectiveAttributeAPCache();
            
            if ( !acAps.hasParent( dn ) )
            {
                // No parent, check for any IAP
                List<CollectiveAttributeAdministrativePoint> children = caAps.getDescendantElements( dn );
                
                for ( CollectiveAttributeAdministrativePoint child : children )
                {
                    if ( child.isInner() )
                    {
                        // Ok, we are dead : the IAP will remain with no parent.
                        String message = "Cannot delete the given role, the " + child.getDn() + " CollectiveAttribute IAP will remain orphan";
                        LOG.error( message );
                        throw new LdapUnwillingToPerformException( message );
                    }
                }
            }
            
            // Process the TEs
            DnNode<TriggerExecutionAdministrativePoint> teAps = directoryService.getTriggerExecutionAPCache();
            
            if ( !acAps.hasParent( dn ) )
            {
                // No parent, check for any IAP
                List<TriggerExecutionAdministrativePoint> children = teAps.getDescendantElements( dn );
                
                for ( TriggerExecutionAdministrativePoint child : children )
                {
                    if ( child.isInner() )
                    {
                        // Ok, we are dead : the IAP will remain with no parent.
                        String message = "Cannot delete the given role, the " + child.getDn() + " TriggerExecution IAP will remain orphan";
                        LOG.error( message );
                        throw new LdapUnwillingToPerformException( message );
                    }
                }
            }
        }
    }


    /**
     * Creates an Administrative service interceptor.
     */
    public AdministrativePointInterceptor()
    {
    }


    //-------------------------------------------------------------------------------------------
    // Helper methods
    //-------------------------------------------------------------------------------------------
    private List<Entry> getAdministrativePoints() throws LdapException
    {
        List<Entry> entries = new ArrayList<Entry>();

        DN adminDn = new DN( ServerDNConstants.ADMIN_SYSTEM_DN, schemaManager );

        SearchControls controls = new SearchControls();
        controls.setSearchScope( SearchControls.SUBTREE_SCOPE );
        controls.setReturningAttributes( new String[]
            { SchemaConstants.ADMINISTRATIVE_ROLE_AT, SchemaConstants.ENTRY_UUID_AT } );

        // Search for all the adminstrativePoints in the base
        ExprNode filter = new PresenceNode( ADMINISTRATIVE_ROLE_AT );

        CoreSession adminSession = new DefaultCoreSession( new LdapPrincipal( adminDn, AuthenticationLevel.STRONG ),
            directoryService );

        SearchOperationContext searchOperationContext = new SearchOperationContext( adminSession, DN.EMPTY_DN, filter,
            controls );

        searchOperationContext.setAliasDerefMode( AliasDerefMode.NEVER_DEREF_ALIASES );

        EntryFilteringCursor results = nexus.search( searchOperationContext );

        try
        {
            while ( results.next() )
            {
                Entry entry = results.get();

                entries.add( entry );
            }
            
            results.close();
        }
        catch ( Exception e )
        {
            throw new LdapOperationException( e.getMessage() );
        }

        return entries;
    }


    /**
     * Tells if a given role is a valid administrative role. We check the lower cased
     * and trimmed value, and also the OID value.
     */
    private boolean isValidRole( String role )
    {
        return ROLES.contains( StringTools.toLowerCase( StringTools.trim( role ) ) );
    }


    /**
     * Update The Administrative Points cache, adding the given AdminPoints
     */
    private void addAdminPointCache( List<Entry> adminPointEntries ) throws LdapException
    {
        for ( Entry adminPointEntry : adminPointEntries )
        {
            // update the cache
            DN dn = adminPointEntry.getDn();

            String uuid = adminPointEntry.get( ENTRY_UUID_AT ).getString();
            EntryAttribute adminPoint = adminPointEntry.get( ADMINISTRATIVE_ROLE_AT );

            createAdministrativePoints( adminPoint, dn, uuid );
        }
    }


    /**
     * Update The Administrative Points cache, removing the given AdminPoint
     */
    private void deleteAdminPointCache( EntryAttribute adminPoint, DeleteOperationContext deleteContext )
        throws LdapException
    {
        DN dn = deleteContext.getDn();
        
        // Remove the APs in the AP cache
        for ( Value<?> value : adminPoint )
        {
            String role = value.getString();

            // Deal with Autonomous AP : delete the 4 associated SAP/AAP
            if ( isAutonomousAreaRole( role ) )
            {
                // The AC AAP
                directoryService.getAccessControlAPCache().remove( dn );

                // The CA AAP
                directoryService.getCollectiveAttributeAPCache().remove( dn );

                // The TE AAP
                directoryService.getTriggerExecutionAPCache().remove( dn );

                // The SS AAP
                directoryService.getSubschemaAPCache().remove( dn );

                // If it's an AAP, we can get out immediately
                return;
            }

            // Deal with AccessControl AP
            if ( isAccessControlSpecificRole( role ) || isAccessControlInnerRole( role ) )
            {
                directoryService.getAccessControlAPCache().remove( dn );

                continue;
            }

            // Deal with CollectveAttribute AP
            if ( isCollectiveAttributeSpecificRole( role ) || isCollectiveAttributeInnerRole( role ) )
            {
                directoryService.getCollectiveAttributeAPCache().remove( dn );

                continue;
            }


            // Deal with SubSchema AP
            if ( isSubschemaSpecficRole( role ) )
            {
                directoryService.getSubschemaAPCache().remove( dn );

                continue;
            }

            // Deal with TriggerExecution AP
            if ( isTriggerExecutionSpecificRole( role ) || isTriggerExecutionInnerRole( role ))
            {
                directoryService.getTriggerExecutionAPCache().remove( dn );

                continue;
            }
        }
    }


    /**
     * Tells if the role is an AC IAP
     */
    private boolean isAccessControlInnerRole( String role )
    {
        return role.equalsIgnoreCase( SchemaConstants.ACCESS_CONTROL_INNER_AREA ) ||
               role.equals( SchemaConstants.ACCESS_CONTROL_INNER_AREA_OID );
    }


    /**
     * Tells if the role is an AC SAP
     */
    private boolean isAccessControlSpecificRole( String role )
    {
        return role.equalsIgnoreCase( SchemaConstants.ACCESS_CONTROL_SPECIFIC_AREA ) ||
               role.equals( SchemaConstants.ACCESS_CONTROL_SPECIFIC_AREA_OID );
    }


    /**
     * Tells if the role is a CA IAP
     */
    private boolean isCollectiveAttributeInnerRole( String role )
    {
        return role.equalsIgnoreCase( SchemaConstants.COLLECTIVE_ATTRIBUTE_INNER_AREA ) ||
               role.equals( SchemaConstants.COLLECTIVE_ATTRIBUTE_INNER_AREA_OID );
    }


    /**
     * Tells if the role is a CA SAP
     */
    private boolean isCollectiveAttributeSpecificRole( String role )
    {
        return role.equalsIgnoreCase( SchemaConstants.COLLECTIVE_ATTRIBUTE_SPECIFIC_AREA ) ||
               role.equals( SchemaConstants.COLLECTIVE_ATTRIBUTE_SPECIFIC_AREA_OID );
    }


    /**
     * Tells if the role is a TE IAP
     */
    private boolean isTriggerExecutionInnerRole( String role )
    {
        return role.equalsIgnoreCase( SchemaConstants.TRIGGER_EXECUTION_INNER_AREA ) ||
               role.equals( SchemaConstants.TRIGGER_EXECUTION_INNER_AREA_OID );
    }


    /**
     * Tells if the role is a TE SAP
     */
    private boolean isTriggerExecutionSpecificRole( String role )
    {
        return role.equalsIgnoreCase( SchemaConstants.TRIGGER_EXECUTION_SPECIFIC_AREA ) ||
               role.equals( SchemaConstants.TRIGGER_EXECUTION_SPECIFIC_AREA_OID );
    }


    /**
     * Tells if the role is a SS SAP
     */
    private boolean isSubschemaSpecficRole( String role )
    {
        return role.equalsIgnoreCase( SchemaConstants.SUB_SCHEMA_ADMIN_SPECIFIC_AREA ) ||
               role.equals( SchemaConstants.SUB_SCHEMA_ADMIN_SPECIFIC_AREA_OID );
    }


    /**
     * Tells if the role is an AAP
     */
    private boolean isAutonomousAreaRole( String role )
    {
        return role.equalsIgnoreCase( SchemaConstants.AUTONOMOUS_AREA ) ||
               role.equals( SchemaConstants.AUTONOMOUS_AREA_OID );
    }


    /**
     * Tells if the Administrative Point role is an AAP
     */
    private boolean isAAP( EntryAttribute adminPoint )
    {
        return ( adminPoint.contains( SchemaConstants.AUTONOMOUS_AREA ) || adminPoint
            .contains( SchemaConstants.AUTONOMOUS_AREA_OID ) );
    }


    private boolean hasAccessControlSpecificRole( EntryAttribute adminPoint )
    {
        return adminPoint.contains( SchemaConstants.ACCESS_CONTROL_SPECIFIC_AREA ) ||
               adminPoint.contains( SchemaConstants.ACCESS_CONTROL_SPECIFIC_AREA_OID );
    }


    private boolean isIAP( String role )
    {
        return INNER_AREA_ROLES.contains( role );
    }


    private boolean hasCollectiveAttributeSpecificRole( EntryAttribute adminPoint )
    {
        return adminPoint.contains( SchemaConstants.COLLECTIVE_ATTRIBUTE_SPECIFIC_AREA ) ||
               adminPoint.contains( SchemaConstants.COLLECTIVE_ATTRIBUTE_SPECIFIC_AREA_OID );
    }


    private boolean hasTriggerExecutionSpecificRole( EntryAttribute adminPoint )
    {
        return adminPoint.contains( SchemaConstants.TRIGGER_EXECUTION_SPECIFIC_AREA ) ||
               adminPoint.contains( SchemaConstants.TRIGGER_EXECUTION_SPECIFIC_AREA_OID );
    }


    /**
     * Check that we don't have an IAP and a SAP with the same family
     */
    private void checkInnerSpecificMix( String role, EntryAttribute adminPoint ) throws LdapUnwillingToPerformException
    {
        if ( isAccessControlInnerRole( role ) )
        {
            if ( hasAccessControlSpecificRole( adminPoint ) )
            {
                // This is inconsistent
                String message = "Cannot add a specific Administrative Point and the same"
                    + " inner Administrative point at the same time : " + adminPoint;
                LOG.error( message );
                throw new LdapUnwillingToPerformException( message );
            }
            else
            {
                return;
            }
        }

        if ( isCollectiveAttributeInnerRole( role ) )
        {
            if ( hasCollectiveAttributeSpecificRole( adminPoint ) )
            {
                // This is inconsistent
                String message = "Cannot add a specific Administrative Point and the same"
                    + " inner Administrative point at the same time : " + adminPoint;
                LOG.error( message );
                throw new LdapUnwillingToPerformException( message );
            }
            else
            {
                return;
            }
        }

        if ( isTriggerExecutionInnerRole( role ) )
        {
            if ( hasTriggerExecutionSpecificRole( adminPoint ) )
            {
                // This is inconsistent
                String message = "Cannot add a specific Administrative Point and the same"
                    + " inner Administrative point at the same time : " + adminPoint;
                LOG.error( message );
                throw new LdapUnwillingToPerformException( message );
            }
            else
            {
                return;
            }
        }
    }


    /**
     * Check that the IAPs (if any) have a parent. We will check for each kind or role :
     * AC, CA and TE.
     */
    private void checkIAPHasParent( String role, EntryAttribute adminPoint, DN dn )
        throws LdapUnwillingToPerformException
    {
        // Check for the AC role
        if ( isAccessControlInnerRole( role ) )
        {
            DnNode<AccessControlAdministrativePoint> acCache = directoryService.getAccessControlAPCache();
            
            DnNode<AccessControlAdministrativePoint> parent =  acCache.getNode( dn );
            
            if ( parent == null )
            {
                // We don't have any AC administrativePoint in the tree, this is an error
                String message = "Cannot add an IAP with no parent : " + adminPoint;
                LOG.error( message );
                throw new LdapUnwillingToPerformException( message );
            }
        }
        else if ( isCollectiveAttributeInnerRole( role ) )
        {
            DnNode<CollectiveAttributeAdministrativePoint> caCache = directoryService.getCollectiveAttributeAPCache();
            
            boolean hasAP = caCache.hasParentElement( dn );
            
            if ( !hasAP )
            {
                // We don't have any AC administrativePoint in the tree, this is an error
                String message = "Cannot add an IAP with no parent : " + adminPoint;
                LOG.error( message );
                throw new LdapUnwillingToPerformException( message );
            }
        }
        else if ( isTriggerExecutionInnerRole( role ) )
        {
            DnNode<TriggerExecutionAdministrativePoint> caCache = directoryService.getTriggerExecutionAPCache();
            
            DnNode<TriggerExecutionAdministrativePoint> parent =  caCache.getNode( dn );
            
            if ( parent == null )
            {
                // We don't have any AC administrativePoint in the tree, this is an error
                String message = "Cannot add an IAP with no parent : " + adminPoint;
                LOG.error( message );
                throw new LdapUnwillingToPerformException( message );
            }
        }
        else
        {
            // Wtf ? We *must* have an IAP here...
            String message = "This is not an IAP : " + role;
            LOG.error( message );
            throw new LdapUnwillingToPerformException( message );
        }
    }


    //-------------------------------------------------------------------------------------------
    // Interceptor initialization
    //-------------------------------------------------------------------------------------------
    /**
     * Registers and initializes all {@link Authenticator}s to this service.
     */
    public void init( DirectoryService directoryService ) throws LdapException
    {
        LOG.debug( "Initializing the AdministrativeInterceptor" );

        this.directoryService = directoryService;

        schemaManager = directoryService.getSchemaManager();
        nexus = directoryService.getPartitionNexus();

        // Init the At we use locally
        ADMINISTRATIVE_ROLE_AT = schemaManager.getAttributeType( SchemaConstants.ADMINISTRATIVE_ROLE_AT );
        ENTRY_UUID_AT = schemaManager.getAttributeType( SchemaConstants.ENTRY_UUID_AT );

        // Load all the AdministratvePoint :
        // Autonomous Administrative Point first, then Specific
        // administrative point, finally the Inner administrative Point
        DN adminDn = new DN( ServerDNConstants.ADMIN_SYSTEM_DN, schemaManager );

        SearchControls controls = new SearchControls();
        controls.setSearchScope( SearchControls.SUBTREE_SCOPE );
        controls.setReturningAttributes( new String[]
            { SchemaConstants.ADMINISTRATIVE_ROLE_AT } );

        // get the list of all the AAPs
        List<Entry> administrativePoints = getAdministrativePoints();
        
        lockWrite();
        addAdminPointCache( administrativePoints );
        unlock();
    }


    /**
     * {@inheritDoc}
     */
    public void destroy()
    {
    }


    private void updateAPEntries( EntryAttribute adminPoint, String apUuid )
    {
        if ( isAAP( adminPoint ) )
        {

        }

        return;
    }


    /**
     * Add an administrative point into the DIT.
     * 
     * We have to deal with some specific cases :
     * <ul>
     * <li>If it's an AA, then the added role should be the only one</li>
     * <li>It's not possible to add IA and SA at the same time</li>
     * 
     * @param next The next {@link Interceptor} in the chain
     * @param addContext The {@link AddOperationContext} instance
     * @throws LdapException If we had some error while processing the Add operation
     */
    public void add( NextInterceptor next, AddOperationContext addContext ) throws LdapException
    {
        LOG.debug( "Entering into the Administrative Interceptor, addRequest" );
        Entry entry = addContext.getEntry();
        DN dn = entry.getDn();

        // Check if we are adding an Administrative Point
        EntryAttribute adminPoint = entry.get( ADMINISTRATIVE_ROLE_AT );

        if ( adminPoint == null )
        {
            // Nope, go on.
            next.add( addContext );

            LOG.debug( "Exit from Administrative Interceptor, no AP in the added entry" );

            return;
        }

        LOG.debug( "Addition of an administrative point at {} for the role {}", dn, adminPoint );

        // Protect the AP caches against concurrent access
        lockWrite();
        
        // Loop on all the added roles to check if they are valid
        for ( Value<?> role : adminPoint )
        {
            checkAddRole( role, adminPoint, dn );
        }

        // Ok, we are golden.
        next.add( addContext );

        String apUuid = entry.get( ENTRY_UUID_AT ).getString();

        // Now, update the AdminPoint cache
        createAdministrativePoints( adminPoint, dn, apUuid );

        // Release the APCaches lock
        unlock();
        
        LOG.debug( "Added an Administrative Point at {}", dn );

        return;
    }


    /**
     * We have to check that we can remove the associated AdministrativePoint : <br/> 
     * <ul>
     * <li> if we remove an AAP, no descendant IAP should remain orphan</li>
     * <li> If we remove a SAP, no descendant IAP should remain orphan</li>
     * </ul> 
     * {@inheritDoc}
     */
    public void delete( NextInterceptor next, DeleteOperationContext deleteContext ) throws LdapException
    {
        Entry entry = deleteContext.getEntry();
        DN dn = entry.getDn();

        // Check if we are deleting an Administrative Point
        EntryAttribute adminPoint = entry.get( ADMINISTRATIVE_ROLE_AT );

        if ( adminPoint == null )
        {
            // Nope, go on.
            next.delete( deleteContext );

            LOG.debug( "Exit from Administrative Interceptor" );

            return;
        }

        LOG.debug( "Deletion of an administrative point at {} for the role {}", dn, adminPoint );
        
        // Protect the AP caches against concurrent access
        lockWrite();
        
        // Check that the removed AdministrativeRoles are valid. We don't have to do
        // any other check, as the deleted entry has no children.
        for ( Value<?> role : adminPoint )
        {
            if ( !isValidRole( role.getString() ) )
            {
                String message = "Cannot remove the given role, it's not a valid one :" + role;
                LOG.error( message );
                throw new LdapUnwillingToPerformException( message );
            }
        }

        // Ok, we can remove the AP
        next.delete( deleteContext );

        // Now, update the AdminPoint cache
        deleteAdminPointCache( adminPoint, deleteContext );

        // Release the APCaches lock
        unlock();
        
        LOG.debug( "Deleted an Administrative Point at {}", dn );

        return;
    }


    /**
     * Only the add and remove modifications are fully supported. We have to check that the
     * underlying APs are still consistent.
     * We first have to compute the final AdministrativeRole, then do a diff with the
     * initial attribute, to determinate which roles have been added and which ones have
     * been deleted.
     * Once this is done, we have to check that when deleting or adding each of those roles
     * the admin model remains consistent.
     * 
     * {@inheritDoc}
     */
    public void modify( NextInterceptor next, ModifyOperationContext modifyContext ) throws LdapException
    {
        // We have to check that the modification is acceptable
        List<Modification> modifications = modifyContext.getModItems();
        DN dn = modifyContext.getDn();
        String uuid = modifyContext.getEntry().get( ENTRY_UUID_AT ).getString();

        // Create a clone of the current AdminRole AT
        EntryAttribute modifiedAdminRole = ( modifyContext.getEntry() ).getOriginalEntry().get( ADMINISTRATIVE_ROLE_AT );

        if ( modifiedAdminRole == null )
        {
            // Create the attribute
            modifiedAdminRole = new DefaultEntryAttribute( ADMINISTRATIVE_ROLE_AT );
        }
        else
        {
            modifiedAdminRole = modifiedAdminRole.clone();
        }

        // Clone the AP caches before applying modifications to them modify it
        DnNode<AccessControlAdministrativePoint> acapCacheCopy = directoryService.getAccessControlAPCache().clone();
        DnNode<CollectiveAttributeAdministrativePoint> caapCacheCopy = directoryService.getCollectiveAttributeAPCache().clone();
        DnNode<TriggerExecutionAdministrativePoint> teapCacheCopy = directoryService.getTriggerExecutionAPCache().clone();
        DnNode<SubschemaAdministrativePoint> ssapCacheCopy = directoryService.getSubschemaAPCache().clone();
        
        // Loop on the modification to select the AdministrativeRole and process it :
        // we will create a new AT containing all the roles after having applied the modifications
        // on it
        for ( Modification modification : modifications )
        {
            EntryAttribute attribute = modification.getAttribute();

            // Skip all the attributes but AdministrativeRole
            if ( attribute.getAttributeType() == ADMINISTRATIVE_ROLE_AT )
            {
                // Ok, we have a modification impacting the administrative role
                // Apply it to a virtual AdministrativeRole attribute
                switch ( modification.getOperation() )
                {
                    case ADD_ATTRIBUTE:
                        if ( modifiedAdminRole == null )
                        {
                            // Create the attribute
                            modifiedAdminRole = new DefaultEntryAttribute( ADMINISTRATIVE_ROLE_AT, attribute.get() );
                        }

                        for ( Value<?> role : attribute )
                        {
                            addRole( role.getString(), dn, uuid, acapCacheCopy, caapCacheCopy, teapCacheCopy, ssapCacheCopy );

                            // Add the role to the modified attribute
                            modifiedAdminRole.add( role );
                        }

                        break;

                    case REMOVE_ATTRIBUTE:
                        if ( modifiedAdminRole == null )
                        {
                            // We can't remove a value when the attribute does not exist.
                            String msg = "Cannot remove the administrative role, it does not exist";
                            LOG.error( msg );
                            throw new LdapNoSuchAttributeException( msg );
                        }

                        // It may be a complete removal
                        if ( attribute.size() == 0 )
                        {
                            // Complete removal. Loop on all the existing roles and remove them
                            for ( Value<?> role : modifiedAdminRole )
                            {
                                //checkDelRole( role, modifiedAdminRole, dn, directoryService.getAdministrativePoints() );
                                delRole( role.getString(), dn, uuid, acapCacheCopy, caapCacheCopy, teapCacheCopy, ssapCacheCopy );
                            }

                            modifiedAdminRole.clear();
                            break;
                        }

                        // Now deal with the values to remove
                        for ( Value<?> value : attribute )
                        {
                            if ( !isValidRole( value.getString() ) )
                            {
                                // Not a valid role : we will throw an exception
                                String msg = "Invalid role : " + value.getString();
                                LOG.error( msg );
                                throw new LdapInvalidAttributeValueException( ResultCodeEnum.INVALID_ATTRIBUTE_SYNTAX,
                                    msg );
                            }

                            if ( !modifiedAdminRole.contains( value ) )
                            {
                                // We can't remove a value if it does not exist !
                                String msg = "Cannot remove the administrative role value" + value
                                    + ", it does not exist";
                                LOG.error( msg );
                                throw new LdapNoSuchAttributeException( msg );
                            }

                            modifiedAdminRole.remove( value );
                        }

                        break;

                    case REPLACE_ATTRIBUTE:
                        // Not supported
                        String msg = "Cannot replace an administrative role, the opertion is not supported";
                        LOG.error( msg );
                        throw new LdapUnwillingToPerformException( msg );
                }
            }
        }

        // At this point, we have a new AdministrativeRole AT, and we need to get the lists of
        // added roles and removed roles, in order to process them

        next.modify( modifyContext );
    }


    /**
     * {@inheritDoc}
     */
    public void move( NextInterceptor next, MoveOperationContext moveContext ) throws LdapException
    {
        Entry entry = moveContext.getOriginalEntry();

        // Check if we are moving an Administrative Point
        EntryAttribute adminPoint = entry.get( ADMINISTRATIVE_ROLE_AT );

        if ( adminPoint == null )
        {
            // Nope, go on.
            next.move( moveContext );

            LOG.debug( "Exit from Administrative Interceptor" );

            return;
        }

        // Else throw an UnwillingToPerform exception ATM
        String message = "Cannot move an Administrative Point in the current version";
        LOG.error( message );
        throw new LdapUnwillingToPerformException( message );
    }


    /**
     * {@inheritDoc}
     */
    public void moveAndRename( NextInterceptor next, MoveAndRenameOperationContext moveAndRenameContext )
        throws LdapException
    {
        Entry entry = moveAndRenameContext.getOriginalEntry();

        // Check if we are moving and renaming an Administrative Point
        EntryAttribute adminPoint = entry.get( ADMINISTRATIVE_ROLE_AT );

        if ( adminPoint == null )
        {
            // Nope, go on.
            next.moveAndRename( moveAndRenameContext );

            LOG.debug( "Exit from Administrative Interceptor" );

            return;
        }

        // Else throw an UnwillingToPerform exception ATM
        String message = "Cannot move and rename an Administrative Point in the current version";
        LOG.error( message );
        throw new LdapUnwillingToPerformException( message );
    }


    /**
     * {@inheritDoc}
     */
    public void rename( NextInterceptor next, RenameOperationContext renameContext ) throws LdapException
    {
        Entry entry = renameContext.getEntry();

        // Check if we are renaming an Administrative Point
        EntryAttribute adminPoint = entry.get( ADMINISTRATIVE_ROLE_AT );

        if ( adminPoint == null )
        {
            // Nope, go on.
            next.rename( renameContext );

            LOG.debug( "Exit from Administrative Interceptor" );

            return;
        }

        // Else throw an UnwillingToPerform exception ATM
        String message = "Cannot rename an Administrative Point in the current version";
        LOG.error( message );
        throw new LdapUnwillingToPerformException( message );
    }
}