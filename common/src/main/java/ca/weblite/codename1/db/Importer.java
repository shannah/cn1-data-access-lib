/*
 * Copyright 2014 Steve Hannah.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package ca.weblite.codename1.db;

import com.codename1.io.JSONParser;
import com.codename1.util.StringUtil;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * An importer for importing sets of data into a table.  This can be used to import
 * JSON data, or lists of Maps.
 * @author shannah
 * @param <T> The type of the entity object.
 */
public class Importer<T> {
    
    /**
     * The DAO into which this import data.
     */
    final DAO<T> dao;
    
    /**
     * A map to specify how column names in the import rows should be mapped
     * to the DAO columns.
     */
    final Map<String,String> columnMap=new HashMap<String,String>();
    
    /**
     * A map to specify the columns of DAO that serve as primary
     * keys, for looking up existing records in the DAO.  Note that these are 
     * column names of the DAO and not the import set.
     */
    final List<String> keyCols = new ArrayList<String>();
    
    /**
     * Creates an importer for the given DAO.
     * @param dao The DAO into which the importer will import records.
     */
    public Importer(DAO<T> dao){
        this(dao, (String[])null, (String[])null);
    }
    
    /**
     * Creates an importer for the given DAO with the specified key columns and column map.
     * @param dao The DAO into which the importer will import records.
     * @param keyColumns List of columns in the import set that are key columns.
     * @param columnMap A map of columns in the import set to the corresponding column in the DAO. 
     * Odd entries are column names in the import set, and the next even entry is the corresponding 
     * column name in the DAO.
     */
    public Importer(DAO<T> dao, String[] keyColumns, String[] columnMap){
        this.dao = dao;
        if ( keyColumns != null ){
            keyCols.addAll(Arrays.asList(keyColumns));
        } else {
            keyCols.add("id");
        }
        if ( columnMap != null ){
            for ( int i=0; i<columnMap.length; i+=2 ){
                this.columnMap.put(columnMap[i], columnMap[i+1]);
            }
        }
    }
    
    /**
     * Creates an importer for the given DAO with specified key columns and column map.
     * @param dao The DAO into which the importer will import records.
     * @param keyColumns List of columns in the import set that are key columns.
     * @param columnMap A map of columns in the import set to the corresponding column in the DAO.
     */
    public Importer(DAO<T> dao, String[] keyColumns, Map columnMap){
        this.dao = dao;
        if ( keyColumns != null ){
            keyCols.addAll(Arrays.asList(keyColumns));
        } else {
            keyCols.add("id");
        }
        if ( columnMap != null ){
            this.columnMap.putAll(columnMap);
        }
    }
    
    
    
    
    
    /**
     * Gets a reference to the column map.
     * @return The column map for this importer.  This maps column names of the import set to
     * column names in the DAO.
     */
    public Map<String,String> getColumnMap(){
        return Collections.unmodifiableMap(columnMap);
    }
    
    /**
     * Gets the key columns for this set.  These are the columns that will be used
     * to look up existing records in the DAO.  These should be column names of the DAO,
     * and not the import set.
     * @return 
     */
    public List<String> getKeyCols(){
        return Collections.unmodifiableList(keyCols);
    }
    
    
    
    /**
     * Imports a set of rows into the table.
     * @param set A Map with a nested data structure.  Typically this will have been parsed from JSON.
     * @param selector A selector path to indicate where, within the dataset, the rows to be imported
     *  are located.  E.g. "tables/data" indicates that the Map set has a key "tables" with another Map, 
     * which contains a key "data", which is a List of Maps - each of which represents a row to be imported.
     * @param columnMap Maps columns in the imported set, into the corresponding column names in the table.
     * @throws IOException 
     */
    public void importSet(Map set, String selector) throws IOException{
        List<String> path = StringUtil.tokenize(selector, "/");
        int len = path.size();
        int i =0;
        List rows = null;
        for ( String part : path ){
            if ( set.containsKey(part)){
                Object o = set.get(part);
                if ( !(o instanceof Map) && i != len-1){
                    throw new IOException("Invalid selector.  "+part+" is not a map.");
                }
                if ( i == len-1 && !(o instanceof List)){
                    throw new IOException("Expected import set to be a list.");
                }
                if ( i == len-1 ){
                    rows = (List)o;
                } else {
                    set = (Map)o;
                }
            } else {
                throw new IOException("Invalid selector.  Cannot find "+part);
            }
            i++;
        }
        
        if ( rows == null ){
            throw new IOException("Failed to find rows to import.");
        }
        
       importSet(rows);
        
    }
    
    /**
     * Imports a set of rows into the DAO.
     * @param rows List of Maps to be imported.
     * @throws IOException 
     */
    public void importSet(List rows) throws IOException{
         for ( Object o : rows ){
            Map row = (Map)o;
            Map convertedRow = new HashMap(row.size());
            for ( Object key : row.keySet()){
                if ( columnMap != null && columnMap.containsKey(key)){
                    convertedRow.put(columnMap.get(key), row.get(key));
                } else {
                    convertedRow.put(key, row.get(key));
                }
                
            }
            
            Map<String,String> query = new HashMap<String,String>(keyCols.size());
            for ( String col : keyCols ){
                query.put(col, ""+convertedRow.get(col));
            }
            
            T existing = dao.fetchOne(query);
            
            if ( existing == null ){
                existing = dao.newObject();
            }
            //Map mExisting = new HashMap();
            //this.map(existing, mExisting);
            if ( dao.getId(existing) > 0){
                convertedRow.put("id", dao.getId(existing));
            }
            //Log.p("converted row "+convertedRow);
            this.prepareRowForImport(convertedRow);
            dao.unmap(existing, convertedRow);
            this.beforeImport(existing, convertedRow);
            dao.save(existing);
            this.afterImport(existing, convertedRow);
            
                
        }
    }
    
    /**
     * Trigger called just before a row is imported.  This gives subclasses
     * an opportunity to modify data in the row before it is applied to the 
     * Entity object.
     * @param row The row that is being imported.  It has already had the
     * importer's column map applied to it.
     */
    protected void prepareRowForImport(Map row){
        
    }
    
    /**
     * Trigger called just before an entity object is saved.
     * @param object The entity object that is being imported.  It may be an 
     * existing object or a new object.
     * @param row A map of values that were applied to the entity object.
     */
    protected void beforeImport(T object, Map row){
        
    }
    
    /**
     * Trigger called just after the entity object is saved.
     * @param object The entity object that was saved.
     * @param row The row of Map data that was applied to the entity object to be saved.
     */
    protected void afterImport(T object, Map row){
        
    }
    
    /**
     * Imports JSON data from an input stream into the database.
     * @param is InputStream containing JSON data.
     * @param selector The selector to specify the path to the list of data to be imported.
     * @throws IOException 
     */
    public final void importJSON(InputStream is, String selector) throws IOException{
        JSONParser parser = new JSONParser();
        Map data = parser.parseJSON(new InputStreamReader(is, "UTF-8"));
        importSet(data, selector);
        
    }
    
   
    
}
