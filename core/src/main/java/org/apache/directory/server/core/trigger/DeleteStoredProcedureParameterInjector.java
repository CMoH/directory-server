/*
 *   Copyright 2006 The Apache Software Foundation
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

package org.apache.directory.server.core.trigger;

import java.util.Map;

import javax.naming.Name;
import javax.naming.NamingException;
import javax.naming.directory.Attributes;

import org.apache.directory.server.core.partition.DirectoryPartitionNexusProxy;
import org.apache.directory.shared.ldap.trigger.StoredProcedureParameter.DeleteStoredProcedureParameter;

public class DeleteStoredProcedureParameterInjector extends AbstractStoredProcedureParameterInjector
{
    private Name deletedEntryName;
    
    private Map injectors;
    
    public DeleteStoredProcedureParameterInjector()
    {        
        injectors = super.getInjectors();
        injectors.put( DeleteStoredProcedureParameter.NAME, $nameInjector );
        injectors.put( DeleteStoredProcedureParameter.DELETED_ENTRY, $deletedEntryInjector );
    }
    
    public void setDeletedEntryName( Name deletedEntryName )
    {
        this.deletedEntryName = deletedEntryName;
    }
    
    MicroInjector $nameInjector = new MicroInjector()
    {
        public Object inject() throws NamingException
        {
            return deletedEntryName;
        };
    };
    
    MicroInjector $deletedEntryInjector = new MicroInjector()
    {
        public Object inject() throws NamingException
        {
            DirectoryPartitionNexusProxy proxy = getInvocation().getProxy();
            Attributes deletedEntry = proxy.lookup( deletedEntryName, DirectoryPartitionNexusProxy.LOOKUP_BYPASS );
            return deletedEntry;
        };
    };

}
