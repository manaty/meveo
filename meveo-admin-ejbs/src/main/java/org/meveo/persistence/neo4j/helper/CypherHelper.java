/*
 * (C) Copyright 2018-2019 Webdrone SAS (https://www.webdrone.fr/) and contributors.
 *
 * This program is free software: you can redistribute it and/or modify it under the terms of the
 * GNU Affero General Public License as published by the Free Software Foundation, either version 3
 * of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without
 * even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. This program is
 * not suitable for any direct or indirect application in MILITARY industry See the GNU Affero
 * General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License along with this program.
 * If not, see <http://www.gnu.org/licenses/>.
 */

package org.meveo.persistence.neo4j.helper;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import javax.inject.Inject;

import org.apache.commons.beanutils.BeanUtils;
import org.meveo.model.CustomEntity;
import org.meveo.model.persistence.DBStorageType;
import org.meveo.model.persistence.JacksonUtil;
import org.meveo.persistence.CrossStorageTransaction;
import org.meveo.service.storage.RepositoryService;
import org.neo4j.driver.Record;
import org.neo4j.driver.Result;
import org.neo4j.driver.Transaction;
import org.neo4j.driver.Value;
import org.neo4j.driver.internal.InternalNode;
import org.neo4j.driver.internal.types.TypeConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class CypherHelper {

    @Inject
    private CrossStorageTransaction crossStorageTransaction;
    
    @Inject
    private RepositoryService repositoryService;
    
    private static Logger log = LoggerFactory.getLogger(CypherHelper.class);

    @SuppressWarnings("javadoc")
	public <T> T execute(
            String neo4jConfiguration,
            String request,
            Map<String, Object> parameters,
            CypherResultTransformer<T> resultAction,
            CypherExceptionHandler cypherExceptionHandler
    ){
    	
    	Transaction transaction = crossStorageTransaction.beginTransaction(repositoryService.findByCode(neo4jConfiguration), DBStorageType.NEO4J);
    			
        try {

            final Result result = transaction.run(request, parameters);

            if(resultAction != null){
                return resultAction.execute(transaction, result);
                
            } else {
            	result.consume();
            }

        } catch (Exception e) {
            
            if(cypherExceptionHandler != null){
                cypherExceptionHandler.handle(e);
            } else {
            	log.error("Error executing query \n{}\nwith parameters {}", request, parameters, e);
            	crossStorageTransaction.rollbackTransaction(e, List.of(DBStorageType.NEO4J));
            }
            
        }

        return null;
    }

    public <T> T execute(String neo4jConfiguration,  String request, Map<String, Object> parameters, CypherResultTransformer<T> resultAction){
        return execute(neo4jConfiguration, request, parameters, resultAction, null);
    }

    public void execute(String neo4jConfiguration,  String request, Map<String, Object> parameters, CypherExceptionHandler cypherExceptionHandler){
        execute(neo4jConfiguration, request, parameters, null, cypherExceptionHandler);
    }

    public void execute(String neo4jConfiguration,  String request, Map<String, Object> parameters){
        execute(neo4jConfiguration, request, parameters, null, null);
    }
    
    public <T> List<T> execute(String neo4jConfiguration,  String request, Map<String, Object> parameters, Class<T> entityClass){
    	
    	CypherResultTransformer<List<T> > cypherResultTransformer = new CypherResultTransformer<List<T> >() {
			
			@Override
			public List<T> execute(Transaction transaction, Result result) {
				List<T> output = new ArrayList<T>();
				while(result.hasNext()) {
						Record record = result.next();
						List<Value> elements = record.values();
						for(Value elem : elements) {
							Map<String, Object> props = elem.asMap();
							try {
								T entity = entityClass.getConstructor().newInstance();
								BeanUtils.populate(entity, props);
								output.add(entity);
							} catch (InstantiationException | IllegalAccessException | InvocationTargetException | IllegalArgumentException | NoSuchMethodException | SecurityException e) {
								log.error("Couldn't populate entity " + entityClass.toString(), e);
								continue;
							}
						}
					}
				
				return output;
			}
		}; 
        return execute(neo4jConfiguration, request, parameters, cypherResultTransformer, null);
    }

    @SuppressWarnings("javadoc")
	public void update(
            String neo4jConfiguration,
            String request,
            Map<String, Object> parameters,
            CypherExceptionHandler cypherExceptionHandler, 
            Transaction transaction
    ){
    	
    	if(transaction == null) {
        	transaction = crossStorageTransaction.beginTransaction(repositoryService.findByCode(neo4jConfiguration), DBStorageType.NEO4J);
    	}

        try {
            Result run = transaction.run(request, parameters);
            run.consume();
            
        } catch (Exception e) {
            if(cypherExceptionHandler != null){
                cypherExceptionHandler.handle(e);
            } else {
	            log.error("Can't run update query", e);
            	crossStorageTransaction.rollbackTransaction(e, List.of(DBStorageType.NEO4J));
            }
        }
    }

    public void update(String neo4jConfiguration,  String request){
        update(neo4jConfiguration, request, null, null, null);
    }
    
    public void update(String neo4jConfiguration,  String request, Map<String, Object> parameters){
        update(neo4jConfiguration, request, parameters, null, null);
    }
    
    public void update(String neo4jConfiguration,  String request, CypherExceptionHandler cypherExceptionHandler){
        update(neo4jConfiguration, request, null, cypherExceptionHandler, null);
    }
}
