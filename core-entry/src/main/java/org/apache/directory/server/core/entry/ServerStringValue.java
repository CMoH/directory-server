/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.apache.directory.server.core.entry;


import org.apache.directory.shared.ldap.entry.StringValue;
import org.apache.directory.shared.ldap.entry.Value;
import org.apache.directory.shared.ldap.schema.AttributeType;
import org.apache.directory.shared.ldap.schema.MatchingRule;
import org.apache.directory.shared.ldap.schema.Normalizer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.naming.NamingException;
import java.util.Comparator;


/**
 * A server side value which is also a StringValue.
 *
 * @author <a href="mailto:dev@directory.apache.org">Apache Directory Project</a>
 * @version $Rev$, $Date$
 */
public class ServerStringValue extends StringValue implements ServerValue<String>
{
    private static final Logger LOG = LoggerFactory.getLogger( ServerStringValue.class );

    private String normalizedValue;

    // use this to lookup the attributeType when deserializing
    @SuppressWarnings ( { "UnusedDeclaration" } )
    private final String oid;

    // do not serialize the schema entity graph associated with the type
    private transient AttributeType attributeType;


    public ServerStringValue( AttributeType attributeType )
    {
        if ( attributeType == null )
        {
            throw new NullPointerException( "attributeType cannot be null" );
        }
        this.attributeType = attributeType;
        this.oid = attributeType.getOid();
    }


    public ServerStringValue( AttributeType attributeType, String wrapped )
    {
        if ( attributeType == null )
        {
            throw new NullPointerException( "attributeType cannot be null" );
        }
        this.attributeType = attributeType;
        this.oid = attributeType.getOid();
        super.set( wrapped );
    }


    public String getNormalizedValue() throws NamingException
    {
        if ( get() == null )
        {
            return null;
        }

        if ( normalizedValue == null )
        {
            Normalizer normalizer = getNormalizer();

            if ( normalizer == null )
            {
                normalizedValue = get();
            }
            else
            {
                normalizedValue = ( String ) normalizer.normalize( get() );
            }
        }

        return normalizedValue;
    }


    public final void set( String wrapped )
    {
        normalizedValue = null;
        super.set( wrapped );
    }


    public final boolean isValid() throws NamingException
    {
        return attributeType.getSyntax().getSyntaxChecker().isValidSyntax( get() );
    }


    public int compareTo( ServerValue<String> value )
    {
        try
        {
            //noinspection unchecked
            return getComparator().compare( getNormalizedValue(), value.getNormalizedValue() );
        }
        catch ( Exception e )
        {
            throw new IllegalStateException( "Failed to normalize values.", e );
        }
    }


    private MatchingRule getMatchingRule() throws NamingException
    {
        MatchingRule mr = attributeType.getEquality();

        if ( mr == null )
        {
            mr = attributeType.getOrdering();
        }

        if ( mr == null )
        {
            mr = attributeType.getSubstr();
        }

        return mr;
    }


    private Normalizer getNormalizer() throws NamingException
    {
        MatchingRule mr = getMatchingRule();

        if ( mr == null )
        {
            return null;
        }

        return mr.getNormalizer();
    }


    private Comparator getComparator() throws NamingException
    {
        MatchingRule mr = getMatchingRule();

        if ( mr == null )
        {
            return null;
        }

        return mr.getComparator();
    }


    /**
     * @see Object#hashCode()
     */
    public int hashCode()
    {
        // return zero if the value is null so only one null value can be
        // stored in an attribute - the binary version does the same 
        if ( get() == null )
        {
            return 0;
        }

        try
        {
            return getNormalizedValue().hashCode();
        }
        catch ( NamingException e )
        {
            LOG.warn( "Failed to get normalized value while trying to get hashCode: {}", toString() , e );

            // recover by using non-normalized values
            return get().hashCode();
        }
    }


    public int compareTo( Value<String> value )
    {
        if ( value == null && get() == null )
        {
            return 0;
        }

        if ( value != null && get() == null )
        {
            if ( value.get() == null )
            {
                return 0;
            }
            return -1;
        }

        if ( value == null )
        {
            return 1;
        }


        try
        {
            if ( value instanceof ServerStringValue )
            {
                //noinspection unchecked
                return getComparator().compare( getNormalizedValue(),
                        ( ( ServerStringValue ) value ).getNormalizedValue() );
            }

            //noinspection unchecked
            return getComparator().compare( getNormalizedValue(), value.get() );
        }
        catch ( NamingException e )
        {
            throw new IllegalStateException( "Normalization failed when it should have succeeded", e );
        }
    }


    /**
     * @see Object#equals(Object)
     */
    public boolean equals( Object obj )
    {
        if ( this == obj )
        {
            return true;
        }

        if ( obj == null )
        {
            return false;
        }

        if ( ! ( obj instanceof ServerStringValue ) )
        {
            return false;
        }

        ServerStringValue other = ( ServerStringValue ) obj;
        if ( get() == null && other.get() == null )
        {
            return true;
        }

        //noinspection SimplifiableIfStatement
        if ( get() == null && other.get() != null ||
             get() != null && other.get() == null )
        {
            return false;
        }

        // now unlike regular values we have to compare the normalized values
        try
        {
            return getNormalizedValue().equals( other.getNormalizedValue() );
        }
        catch ( NamingException e )
        {
            // 1st this is a warning because we're recovering from it and secondly
            // we build big string since waste is not an issue when exception handling
            LOG.warn( "Failed to get normalized value while trying to compare StringValues: "
                    + toString() + " and " + other.toString() , e );

            // recover by comparing non-normalized values
            return get().equals( other.get() );
        }
    }
}
