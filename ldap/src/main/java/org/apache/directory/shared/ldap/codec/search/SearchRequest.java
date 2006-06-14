/*
 *   Copyright 2005 The Apache Software Foundation
 *
 *   Licensed under the Apache License, Version 2.0 (the "License");
 *   you may not use this file except in compliance with the License.
 *   You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 *   Unless required by applicable law or agreed to in writing, software
 *   distributed under the License is distributed on an "AS IS" BASIS,
 *   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *   See the License for the specific language governing permissions and
 *   limitations under the License.
 *
 */
package org.apache.directory.shared.ldap.codec.search;


import org.apache.directory.shared.asn1.ber.tlv.Length;
import org.apache.directory.shared.asn1.ber.tlv.UniversalTag;
import org.apache.directory.shared.asn1.ber.tlv.Value;
import org.apache.directory.shared.asn1.codec.EncoderException;
import org.apache.directory.shared.ldap.codec.LdapConstants;
import org.apache.directory.shared.ldap.codec.LdapMessage;
import org.apache.directory.shared.ldap.codec.util.LdapString;
import org.apache.directory.shared.ldap.name.LdapDN;
import org.apache.directory.shared.ldap.util.StringTools;

import java.io.UnsupportedEncodingException;
import java.nio.BufferOverflowException;
import java.nio.ByteBuffer;

import javax.naming.NamingEnumeration;
import javax.naming.directory.Attribute;
import javax.naming.directory.Attributes;
import javax.naming.directory.BasicAttribute;
import javax.naming.directory.BasicAttributes;


/**
 * A SearchRequest ldapObject. It's a sub-class of Asn1Object, and it implements
 * the ldapObject class to be seen as a member of the LdapMessage CHOICE.
 * 
 * @author <a href="mailto:dev@directory.apache.org">Apache Directory Project</a>
 */
public class SearchRequest extends LdapMessage
{
    // ~ Instance fields
    // ----------------------------------------------------------------------------

    /** The base DN */
    private LdapDN baseObject;

    /** The scope. It could be baseObject, singleLevel or wholeSubtree. */
    private int scope;

    /**
     * The deref alias could be neverDerefAliases, derefInSearching,
     * derefFindingBaseObj or derefAlways.
     */
    private int derefAliases;

    /** The size limit (number of objects returned) */
    private int sizeLimit;

    /**
     * The time limit (max time to process the response before returning the
     * result)
     */
    private int timeLimit;

    /**
     * An indicator as to whether search results will contain both attribute
     * types and values, or just attribute types. Setting this field to TRUE
     * causes only attribute types (no values) to be returned. Setting this
     * field to FALSE causes both attribute types and values to be returned.
     */
    private boolean typesOnly;

    /** The filter tree */
    private Filter filter;

    /** The list of attributes to get */
    private Attributes attributes;

    /** The current filter. This is used while decoding a PDU */
    private transient Filter currentFilter;

    /** The searchRequest length */
    private transient int searchRequestLength;

    /** The attributeDescriptionList length */
    private transient int attributeDescriptionListLength;


    // ~ Constructors
    // -------------------------------------------------------------------------------

    /**
     * Creates a new SearchRequest object.
     */
    public SearchRequest()
    {
        super();

        currentFilter = null;
        attributes = new BasicAttributes( true );
    }


    // ~ Methods
    // ------------------------------------------------------------------------------------

    /**
     * Get the message type
     * 
     * @return Returns the type.
     */
    public int getMessageType()
    {
        return LdapConstants.SEARCH_REQUEST;
    }


    /**
     * Get the list of attributes
     * 
     * @return Returns the attributes.
     */
    public Attributes getAttributes()
    {
        return attributes;
    }


    /**
     * Add an attribute to the attributes list.
     * 
     * @param attribute
     *            The attribute to add to the list
     */
    public void addAttribute( LdapString attribute )
    {
        attributes.put( new BasicAttribute( StringTools.lowerCase( attribute.getString() ) ) );
    }


    /**
     * Get the base object
     * 
     * @return Returns the baseObject.
     */
    public LdapDN getBaseObject()
    {
        return baseObject;
    }


    /**
     * Set the base object
     * 
     * @param baseObject
     *            The baseObject to set.
     */
    public void setBaseObject( LdapDN baseObject )
    {
        this.baseObject = baseObject;
    }


    /**
     * Get the derefAliases flag
     * 
     * @return Returns the derefAliases.
     */
    public int getDerefAliases()
    {
        return derefAliases;
    }


    /**
     * Set the derefAliases flag
     * 
     * @param derefAliases
     *            The derefAliases to set.
     */
    public void setDerefAliases( int derefAliases )
    {
        this.derefAliases = derefAliases;
    }


    /**
     * Get the filter
     * 
     * @return Returns the filter.
     */
    public Filter getFilter()
    {
        return filter;
    }


    /**
     * Set the filter
     * 
     * @param filter
     *            The filter to set.
     */
    public void setFilter( Filter filter )
    {
        this.filter = filter;
    }


    /**
     * Get the search scope
     * 
     * @return Returns the scope.
     */
    public int getScope()
    {
        return scope;
    }


    /**
     * Set the search scope
     * 
     * @param scope
     *            The scope to set.
     */
    public void setScope( int scope )
    {
        this.scope = scope;
    }


    /**
     * Get the size limit
     * 
     * @return Returns the sizeLimit.
     */
    public int getSizeLimit()
    {
        return sizeLimit;
    }


    /**
     * Set the size limit
     * 
     * @param sizeLimit
     *            The sizeLimit to set.
     */
    public void setSizeLimit( int sizeLimit )
    {
        this.sizeLimit = sizeLimit;
    }


    /**
     * Get the time limit
     * 
     * @return Returns the timeLimit.
     */
    public int getTimeLimit()
    {
        return timeLimit;
    }


    /**
     * Set the time limit
     * 
     * @param timeLimit
     *            The timeLimit to set.
     */
    public void setTimeLimit( int timeLimit )
    {
        this.timeLimit = timeLimit;
    }


    /**
     * Get the typesOnly flag
     * 
     * @return Returns the typesOnly.
     */
    public boolean isTypesOnly()
    {
        return typesOnly;
    }


    /**
     * Set the typesOnly flag
     * 
     * @param typesOnly
     *            The typesOnly to set.
     */
    public void setTypesOnly( boolean typesOnly )
    {
        this.typesOnly = typesOnly;
    }


    /**
     * Get the current dilter
     * 
     * @return Returns the currentFilter.
     */
    public Filter getCurrentFilter()
    {
        return currentFilter;
    }


    /**
     * Set the current dilter
     * 
     * @param currentFilter
     *            The currentFilter to set.
     */
    public void setCurrentFilter( Filter currentFilter )
    {
        this.currentFilter = currentFilter;
    }


    /**
     * Compute the SearchRequest length SearchRequest : 0x63 L1 | +--> 0x04 L2
     * baseObject +--> 0x0A 0x01 scope +--> 0x0A 0x01 derefAliases +--> 0x02
     * 0x0(1..4) sizeLimit +--> 0x02 0x0(1..4) timeLimit +--> 0x01 0x01
     * typesOnly +--> filter.computeLength() +--> 0x30 L3 (Attribute description
     * list) | +--> 0x04 L4-1 Attribute description +--> 0x04 L4-2 Attribute
     * description +--> ... +--> 0x04 L4-i Attribute description +--> ... +-->
     * 0x04 L4-n Attribute description
     */
    public int computeLength()
    {
        searchRequestLength = 0;

        // The baseObject
        searchRequestLength += 1 + Length.getNbBytes( LdapDN.getNbBytes( baseObject ) )
            + LdapDN.getNbBytes( baseObject );

        // The scope
        searchRequestLength += 1 + 1 + 1;

        // The derefAliases
        searchRequestLength += 1 + 1 + 1;

        // The sizeLimit
        searchRequestLength += 1 + 1 + Value.getNbBytes( sizeLimit );

        // The timeLimit
        searchRequestLength += 1 + 1 + Value.getNbBytes( timeLimit );

        // The typesOnly
        searchRequestLength += 1 + 1 + 1;

        // The filter
        searchRequestLength += filter.computeLength();

        // The attributes description list
        attributeDescriptionListLength = 0;

        if ( ( attributes != null ) && ( attributes.size() != 0 ) )
        {
            NamingEnumeration attributeIterator = attributes.getAll();

            // Compute the attributes length
            while ( attributeIterator.hasMoreElements() )
            {
                Attribute attribute = ( BasicAttribute ) attributeIterator.nextElement();

                // add the attribute length to the attributes length
                try
                {
                    int idLength = attribute.getID().getBytes( "UTF-8" ).length;
                    attributeDescriptionListLength += 1 + Length.getNbBytes( idLength ) + idLength;
                }
                catch ( UnsupportedEncodingException uee )
                {
                    // Should not be possible. The encoding of the Attribute ID
                    // will check that this ID is valid, and if not, it will
                    // throw
                    // an exception.
                    // The allocated length will be set to a null length value
                    // in order to avoid an exception thrown while encoding the
                    // Attribute ID.
                    attributeDescriptionListLength += 1 + 1;
                }
            }
        }

        searchRequestLength += 1 + Length.getNbBytes( attributeDescriptionListLength ) + attributeDescriptionListLength;

        // Return the result.
        return 1 + Length.getNbBytes( searchRequestLength ) + searchRequestLength;
    }


    /**
     * Encode the SearchRequest message to a PDU. SearchRequest : 0x63 LL 0x04
     * LL baseObject 0x0A 01 scope 0x0A 01 derefAliases 0x02 0N sizeLimit 0x02
     * 0N timeLimit 0x01 0x01 typesOnly filter.encode() 0x30 LL
     * attributeDescriptionList 0x04 LL attributeDescription ... 0x04 LL
     * attributeDescription
     * 
     * @param buffer
     *            The buffer where to put the PDU
     * @return The PDU.
     */
    public ByteBuffer encode( ByteBuffer buffer ) throws EncoderException
    {
        if ( buffer == null )
        {
            throw new EncoderException( "Cannot put a PDU in a null buffer !" );
        }

        try
        {
            // The SearchRequest Tag
            buffer.put( LdapConstants.SEARCH_REQUEST_TAG );
            buffer.put( Length.getBytes( searchRequestLength ) );

            // The baseObject
            Value.encode( buffer, LdapDN.getBytes( baseObject ) );

            // The scope
            Value.encodeEnumerated( buffer, scope );

            // The derefAliases
            Value.encodeEnumerated( buffer, derefAliases );

            // The sizeLimit
            Value.encode( buffer, sizeLimit );

            // The timeLimit
            Value.encode( buffer, timeLimit );

            // The typesOnly
            Value.encode( buffer, typesOnly );

            // The filter
            filter.encode( buffer );

            // The attributeDescriptionList
            buffer.put( UniversalTag.SEQUENCE_TAG );
            buffer.put( Length.getBytes( attributeDescriptionListLength ) );

            if ( ( attributes != null ) && ( attributes.size() != 0 ) )
            {
                NamingEnumeration attributeIterator = attributes.getAll();

                // encode each attribute
                while ( attributeIterator.hasMoreElements() )
                {
                    Attribute attribute = ( BasicAttribute ) attributeIterator.nextElement();

                    Value.encode( buffer, attribute.getID() );
                }
            }
        }
        catch ( BufferOverflowException boe )
        {
            throw new EncoderException( "The PDU buffer size is too small !" );
        }

        return buffer;
    }


    /**
     * @return A string that represent the Filter
     */
    private String buildFilter()
    {
        if ( filter == null )
        {
            return "";
        }

        StringBuffer sb = new StringBuffer();

        sb.append( "(" );

        sb.append( filter.toString() );

        sb.append( ")" );

        return sb.toString();
    }


    /**
     * @return A string that represent the atributes list
     */
    private String buildAttributes()
    {
        StringBuffer sb = new StringBuffer();

        NamingEnumeration attrs = attributes.getAll();
        boolean isFirst = true;

        while ( attrs.hasMoreElements() )
        {
            Attribute attr = ( BasicAttribute ) attrs.nextElement();

            if ( isFirst )
            {
                isFirst = false;
            }
            else
            {
                sb.append( ", " );
            }

            sb.append( attr.getID() );
        }

        return sb.toString();
    }


    /**
     * Return a string the represent a SearchRequest
     */
    public String toString()
    {
        StringBuffer sb = new StringBuffer();

        sb.append( "    Search Request\n" );
        sb.append( "        Base Object : '" ).append( baseObject ).append( "'\n" );
        sb.append( "        Scope : " );

        switch ( scope )
        {
            case LdapConstants.SCOPE_BASE_OBJECT:
                sb.append( "base object" );
                break;

            case LdapConstants.SCOPE_SINGLE_LEVEL:
                sb.append( "single level" );
                break;

            case LdapConstants.SCOPE_WHOLE_SUBTREE:
                sb.append( "whole subtree" );
                break;
        }

        sb.append( "\n" );

        sb.append( "        Deref Aliases : " );

        switch ( derefAliases )
        {
            case LdapConstants.NEVER_DEREF_ALIASES:
                sb.append( "never Deref Aliases" );
                break;

            case LdapConstants.DEREF_IN_SEARCHING:
                sb.append( "deref In Searching" );
                break;

            case LdapConstants.DEREF_FINDING_BASE_OBJ:
                sb.append( "deref Finding Base Obj" );
                break;

            case LdapConstants.DEREF_ALWAYS:
                sb.append( "deref Always" );
                break;
        }

        sb.append( "\n" );

        sb.append( "        Size Limit : " );

        if ( sizeLimit == 0 )
        {
            sb.append( "no limit" );
        }
        else
        {
            sb.append( sizeLimit );
        }

        sb.append( "\n" );

        sb.append( "        Time Limit : " );

        if ( timeLimit == 0 )
        {
            sb.append( "no limit" );
        }
        else
        {
            sb.append( timeLimit );
        }

        sb.append( "\n" );

        sb.append( "        Types Only : " ).append( typesOnly ).append( "\n" );
        sb.append( "        Filter : '" ).append( buildFilter() ).append( "'\n" );

        if ( ( attributes != null ) && ( attributes.size() != 0 ) )
        {
            sb.append( "        Attributes : " ).append( buildAttributes() ).append( "\n" );
        }
        return sb.toString();
    }
}
