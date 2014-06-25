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

import com.codename1.db.Cursor;
import com.codename1.db.Database;
import com.codename1.db.Row;
import com.codename1.io.Log;
import com.codename1.ui.Display;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Observable;
import java.util.Observer;
import java.util.Set;

/**
 * A Data access object for a single table.  You can implement a separate DAO for
 * each table of the database.  Currently tables must have an auto increment column named "id"
 * in order to be compatible with this class.  
 * @author shannah
 * @param <T> The Class type of the POJO model object.
 */
public abstract class DAO<T> {
    
    
    /**
     * Enum type for a column type in the DAO.
     */
    public enum ColType {
        INTEGER,
        FLOAT,
        DOUBLE,
        BLOB,
        LONG,
        SHORT,
        STRING,
        VARCHAR
    }
    
    /**
     * Cache mapping long IDs to Wrappers for the model POJO.
     */
    private final Map<Long,Wrapper> cache = new HashMap<Long,Wrapper>();
    //private final Map<Long,T> index = new HashMap<Long,T>();
    
    /**
     * The Database connection for this DAO.
     */
    private final Database db;
    
    /**
     * The tableName that this DAO works on.  Each table will have its own DAO object.
     */
    private final String tableName;
    
    /**
     * Map of column types of fields in this table.  This maps field names to ColTypes.
     */
    protected final Map<String, ColType> colTypes = new HashMap<String,ColType>();
    
    
    /**
     * SQL for generic update statement.
     */
    private String updateStatement = null;
    
    /**
     * SQL for generic select by ID statement.
     */
    private String selectByIdStatement = null;
    
    /**
     * Reference to the DAO provider for this table.  This will allow
     * subclasses to load the DAO for other tables if necessary.
     */
    private final DAOProvider provider;
    
    /**
     * Creates a new DAO object.
     * @param tableName The tablename that this DAO works on.
     * @param provider The DAO provider for this tablename.
     * @throws IOException 
     */
    public DAO(String tableName, DAOProvider provider) throws IOException{
        this.db = provider.db;
        this.tableName = tableName;
        this.provider = provider;
        provider.loadSchema(tableName, this);
        
    }
    
    /**
     * Returns the DAOProvider that this DAO is a member of.
     * @return 
     */
    public DAOProvider getProvider(){
        return provider;
    }
    
    
    /**
     * Gets the generic update SQL statement.
     * @return 
     */
    private String updateStatement(){
        if ( updateStatement == null ){
            StringBuilder sb = new StringBuilder();
            sb.append("UPDATE \"").append(tableName).append("\" set ");
            for ( Map.Entry<String,ColType> e : colTypes.entrySet() ){
                sb.append("\"").append(e.getKey()).append("\" = ?,");
                
            }
            sb.deleteCharAt(sb.length()-1);
            sb.append(" WHERE id=?");
            updateStatement = sb.toString();
        }
        return updateStatement;
    }
    
    /**
     * Gets the update args for the given map.
     * @param id
     * @param m
     * @return 
     */
    private Object[] updateArgs(long id, Map m){
        List largs = new ArrayList();
        for ( Map.Entry<String,ColType> e : colTypes.entrySet() ){
            largs.add(""+m.get(e.getKey()));
        }
        largs.add(id);
        return largs.toArray(new Object[0]);
    }
    
    /**
     * Gets the update statement for the given wrapper.
     * @param w
     * @return 
     */
    private String updateStatement(Wrapper w){
        //Log.p("Updating wrapper "+w);
        //Log.p("Object is "+w.getObject());
        if ( w.getObject() instanceof Observable ){
            if ( w.dirty.isEmpty() ){
                throw new RuntimeException("Cannot create update statement when record is not dirty at all."+w.getObject().getClass());
            }
            StringBuilder sb = new StringBuilder();
            sb.append("UPDATE \"").append(tableName).append("\" set ");
            
            List<String> d = new ArrayList<String>();
            d.addAll(w.dirty);
            Collections.sort(d, new Comparator<String>(){

                public int compare(String o1, String o2) {
                    return o1.compareTo(o2);
                }
                
            });
            
            for ( String colName : d ){
                sb.append("\"").append(colName).append("\"=?, ");
            }
            
            sb.delete(sb.length()-2, sb.length());
            
            sb.append(") VALUES (");
            for ( String colName : d ){
                sb.append("?, ");
            }
            sb.delete(sb.length()-2, sb.length());
            sb.append(") WHERE id=?");
            
            return sb.toString();
        } else {
            return updateStatement();
        }
    }
    
    /**
     * Returns update arguments for given wrapper.
     * @param w The wrapper that is being updated.
     * @param m Map of values from the wrapper's objects.
     * @return 
     */
    private Object[] updateArgs(Wrapper w, Map m){
        if ( w.getObject() instanceof Observable ){
            if ( w.dirty.isEmpty() ){
                throw new RuntimeException("Cannot create update statement when record is not dirty at all.");
            }
            
            List<String> d = new ArrayList<String>();
            d.addAll(w.dirty);
            Collections.sort(d, new Comparator<String>(){

                public int compare(String o1, String o2) {
                    return o1.compareTo(o2);
                }
                
            });
            
            List out = new ArrayList();
            for ( String colName : d ){
                out.add(""+m.get(colName));
            }
            out.add(getId(w.getObject()));
            return out.toArray();
        } else {
            return updateArgs(getId(w.getObject()), m);
        }
    }
    
    /**
     * Gets generic insert statement for the given map of row data.
     * @param row
     * @return 
     */
    private String insertStatement(Map row){
        StringBuilder sb = new StringBuilder();
        sb.append("INSERT INTO \"").append(tableName).append("\" (");
        for ( Map.Entry<String,ColType> e : colTypes.entrySet() ){
            if ( "id".equals(e.getKey()) && row.get("id") == null ){
                continue;
            }
            if ( "id".equals(e.getKey()) && NumberUtil.longValue(row.get("id")) <= 0 ){
                continue;
            }
            
            sb.append("\"").append(e.getKey()).append("\",");
        }
        sb.deleteCharAt(sb.length()-1);
        sb.append(") VALUES (");
        for ( Map.Entry<String,ColType> e : colTypes.entrySet() ){
            if ( "id".equals(e.getKey()) && row.get("id") == null ){
                continue;
            }
            if ( "id".equals(e.getKey()) && NumberUtil.longValue(row.get("id")) <= 0 ){
                continue;
            }
            sb.append("?,");
        }
        sb.deleteCharAt(sb.length()-1);
        sb.append(")");
        return sb.toString();
        
    }
    
    /**
     * Gets generic insert args for insert statement for the given map of row data.
     * @param m Map containing row data to insert. Maps column names to column values.
     * @return Object[] array that can be used in Database.execute()
     */
    private Object[] insertArgs(Map m){
        List largs = new ArrayList();
        for ( Map.Entry<String,ColType> e : colTypes.entrySet() ){
            if ( "id".equals(e.getKey()) && m.get("id") == null ){
                continue;
            }
            if ( "id".equals(e.getKey()) && NumberUtil.longValue(m.get("id")) <= 0 ){
                continue;
            }
            largs.add(""+m.get(e.getKey()));
        }
        
        return largs.toArray();
    }
    
    
    
    
    /**
     * Gets the generic selectById statement.
     * @return 
     */
    private String selectByIdStatement(){
        if ( selectByIdStatement == null ){
            StringBuilder sb = new StringBuilder();
            sb.append("SELECT * FROM \"").append(tableName).append("\" where id=?");
            selectByIdStatement = sb.toString();
        }
        return selectByIdStatement;
    }
    
    /**
     * Gets the generic selectById args to be used in the Database.executeQuery() method.
     * @param id
     * @return 
     */
    private String[] selectByIdArgs(long id){
        return new String[]{""+id};
    }
    
    /**
     * A wrapper class for the POJO model object.  This stores a weak reference
     * to the POJO object so that the GC can handle unloading the cache.
     */
    protected class Wrapper implements Observer {
        
        //private long id=-1l;
        /**
         * Weak reference to the POJO model object.
         */
        private Object obj;
        
        /**
         * Set of flags to indicate which columns are dirty and require update.
         * These are only used if the POJO model class extends Observable
         */
        Set<String> dirty = new HashSet<String>();
        
        /**
         * Dirty flag to indicate if the object is dirty and requires update.
         */
        private boolean dirtyFlag = false;
        
        /**
         * Listens for updates to the POJO and marks dirty flags.
         * @param o
         * @param arg 
         */
        public void update(Observable o, Object arg) {
            if ( arg instanceof String ){
                dirty.add((String)arg);
            }
        }
        
        /**
         * Gets the POJO model object.  If it has been GC'd, then this will return null.
         * @return 
         */
        T getObject(){
            Object o = obj;
            if ( o != null && (o = Display.getInstance().extractHardRef(o)) != null){
                return (T)o;
            }
            return null;
        }
        
        /**
         * Sets the POJO model object for this wrapper.  This will only store a weak 
         * reference so it is possible for the GC to clear the object behind the scenes.
         * @param object The POJO model object
         */
        void setObject(T object){
            obj = Display.getInstance().createSoftWeakRef(object);
        }
    }
    
    /**
     * Gets an entity object by its ID.  This version only checks the cache.
     * If you want to check the database, you should use {@link #getById(long,boolean)}.
     * @param id The ID of the object.
     * @return The entity object with the given ID or null if it isn't already loaded and cached.
     * @throws IOException 
     */
    public final T getById(long id) throws IOException {
        return getById(id, false);
    }
    
    /**
     * Gets all of the entities in this DAO that are currently cached.  Please note, that the
     * cache only stores weak references to the entities, so this is not a reliable way to store
     * a set of objects for caching and retrieval.  
     * <p>Use {@link #fetchAll} to fetch all of the items from the data source.</p>
     * @return A List of all of the entities that are currently cached.
     * @throws IOException 
     * @see #fetchAll 
     */
    public List<T> getAll() throws IOException {
        List<T> out = new ArrayList<T>();
        List<Long> removes = new ArrayList<Long>();
        for ( Map.Entry<Long,Wrapper> e : cache.entrySet()){
            T o = e.getValue().getObject();
            if ( o != null ){
                out.add(o);
            } else {
                removes.add(e.getKey());
            }
            
        }
        if ( !removes.isEmpty()){
            for ( Long l : removes ){
                cache.remove(l);
            }
        }
        
        return out;
    }
    
    /**
     * Fetches all of the entities in the underlying table as entity objects.
     * @return List of entities.
     * @throws IOException 
     */
    public final List<T> fetchAll() throws IOException {
        return fetchAll("select * from \""+tableName+"\"", new String[0]);
    }
    
    /**
     * Fetches a set of entities from the underlying table that match a given
     * query.
     * @param query A field=>value mapping the constitutes a query.
     * @return A set of entities from the underlying table.
     * @throws IOException 
     */
    public final List<T> fetch(Map<String,String> query) throws IOException {
        StringBuilder sb = new StringBuilder();
        sb.append("select * from \"").append(tableName).append("\" where ");
        int len = query.size();
        int i=0;
        String[] vals = new String[len];
        for ( Object key : query.keySet()){
            String strKey = (String)key;
            vals[i] = (""+query.get(strKey));
            sb.append("\"").append(strKey).append("\"=? ");
            if ( i++ < len-1 ){
                sb.append("AND ");
            }
        }
        return fetchAll(sb.toString(), vals);
        
    }
    
    /**
     * Fetches a set of entities from the underlying table that match a given query.
     * This is a wrapper for {@link #fetch(java.util.Map)} that juse uses a String[] array
     * to pass the query instead of a Map.
     * @param query Query parameters.  Odd indices contain column names, Even indices contain column values.
     * @return List of entities in the underlying table that match the query.
     * @throws IOException 
     */
    public final List<T> fetch(String[] query) throws IOException {
        Map<String,String> q = new HashMap<String,String>();
        for ( int i=0; i<query.length; i+=2){
            q.put(query[i], query[i+1]);
        }
        return fetch(q);
    }
    
    
    /**
     * Fetches a single entity from the underlying table that matches a given query.
     * @param query
     * @return An entity object, or null if none was found.
     * @throws IOException 
     */
    public final T fetchOne(Map<String,String> query) throws IOException {
        List<T> res = fetch(query);
        if ( res.isEmpty() ){
            return null;
        } else {
            return res.get(0);
        }
    }
    
    /**
     * Fetchs a single entity from the underlying table that matches a given query.
     * @param query
     * @return An entity object or null if none was found.
     * @throws IOException 
     */
    public final T fetchOne(String[] query) throws IOException {
        Map<String,String> q = new HashMap<String,String>();
        for ( int i=0; i<query.length; i+=2){
            q.put(query[i], query[i+1]);
        }
        return fetchOne(q);
    }
    
    /**
     * Fills a map with the data of the current row of database Cursor
     * @param c The database cursor from a query.
     * @param m The map to fill.
     * @throws IOException 
     */
    protected void fillMap(Cursor c,  Map m) throws IOException{
        Row row = c.getRow();
        int len = c.getColumnCount();
        for ( int i=0; i<len; i++){
            
                String colName = c.getColumnName(i);
                ColType colType = colTypes.get(colName);
                
                if ( colType == null ){
                    continue;
                }
                switch ( colType ){
                    case FLOAT:
                       m.put(colName, row.getFloat(i));
                        break;
                    case DOUBLE:
                        m.put(colName, row.getDouble(i));
                        break;
                    case BLOB:
                        m.put(colName, row.getBlob(i));
                        break;
                    case STRING:
                    case VARCHAR:
                        m.put(colName, row.getString(i));
                        break;
                    case INTEGER:
                        if ( "id".equals(colName) ){
                            m.put(colName, row.getLong(i));
                        } else {
                             m.put(colName, row.getInteger(i));
                        }
                        break;
                    case LONG:
                        m.put(colName, row.getLong(i));
                        break;
                    case SHORT:
                        m.put(colName, row.getShort(i));
                        break;
                    default:
                }

            }
    }
    
    /**
     * Gets an entity by ID. If the refresh parameter is false, then this will
     * only check the cache.  If it is true, then this will reload the entity
     * from the database, and update it's data.
     * @param id The ID of the entity to load.
     * @param refresh True to refresh from the database.  False to just load from cache.
     * @return
     * @throws IOException 
     */
    public T getById(long id, boolean refresh) throws IOException {
        if ( !refresh ){
            //T obj = index.get(id);
            Wrapper w = cache.get(id);
            if ( w == null ){
                return null;
            }
            T obj = w.getObject();
            if ( obj != null ){
                return obj;
            } else {
                cache.remove(id);
                
            }
        }
        Cursor c = db.executeQuery(selectByIdStatement(), selectByIdArgs(id));
        
        Map m = new HashMap();
        if ( c.next() ){
            fillMap(c, m);
            c.close();
            T object = newObject();
            Wrapper w = initObject(id, object,  m);
            return object;
            
        }
        
        return null;
        
    
    }
    
    /**
     * Fetches records from the database using a specified SQL query.  This is protected
     * as an encouragement for implementors to create a finite set of fetchXXX methods in 
     * the subclass for performing specific searches, rather than exposing SQL to the
     * caller.
     * @param sqlQuery The SQL query
     * @param params The SQL query params
     * @return List of entity objects.
     * @throws IOException 
     */
    protected List<T> fetchAll(String sqlQuery, String[] params) throws IOException{
        Cursor c = db.executeQuery(sqlQuery, params);
        List<T> out = new ArrayList<T>();
        while ( c.next() ){
            Map m = new HashMap();
            fillMap(c, m);
            Wrapper w = null;
            if ( m.containsKey("id") ){
                long id = (Long)m.get("id");
                w = cache.get(id);
                if ( w != null ){
                    T existing = w.getObject();
                    if ( existing != null ){
                        //w = cache.get(existing);
                        unmap(existing, m);
                    } else {
                        cache.remove(id);
                    }
                }
                
            }
            T object; // so that the w.getObject() weakref doesn't get gc'd
            if ( w == null ){
                object = newObject();
                w = initObject((Long)m.get("id"), object,  m);
            } 
            out.add(w.getObject());
        }
        c.close();
        return out;
    }
    
    /**
     * Inserts an entity into the database.  This will fail if the entity is already
     * inserted.
     * @param object The entity object to insert.
     * @throws IOException 
     * @see #save
     * @see #update
     */
    public void insert(T object) throws IOException {
        long id = getId(object);
        Wrapper w = cache.get(id);
        if ( w != null ){
            throw new IOException("Cannot insert this object because it is already inserted");
        }
        w = new Wrapper();
        w.setObject(object);
        Map m = new HashMap();
        map(object, m);
        //Log.p("Just set object "+object+" so we have "+w.getObject());

        db.execute(insertStatement(m), insertArgs(m));
        Cursor c = db.executeQuery("select last_insert_rowid()");
        if ( c.next() ){
            id = c.getRow().getLong(0);
            m.put("id", id);
            unmap(object, m);
            //index.put(w.id, object);
            cache.put(id, w);
            w.dirty.clear();
            w.dirtyFlag = false;
            

        } else {
            throw new IOException("Failed to get the insert ID");
        }
            
    }
    
    /**
     * Updates an existing entity in the database.
     * @param object The entity to update.
     * @throws IOException 
     */
    public void update(T object) throws IOException {
        long id = getId(object);
        
        Wrapper w = cache.get(id);
        if ( w == null ){
            throw new IOException("Cannot update this record because it hasn't been loaded yet.");
        } 
        Map m = new HashMap();
        map(object, m);
        db.execute(updateStatement(w), updateArgs(w, m));
        w.dirty.clear();
        w.dirtyFlag = false;
    }
    
    /**
     * Saves an entity to the database.  This will check to see if the entity exists already,
     * and will call update() if not.  It will call insert() if it doesn't exist yet.
     * @param object The entity object to save.
     * @throws IOException 
     */
    public void save(T object) throws IOException{
        
        //Map m = new HashMap();
        //map(object, m);
        long id = getId(object);
        Wrapper w = cache.get(id);
        if ( w == null ){
            getById(id, true);
            w = cache.get(id);
            if ( w == null ){
                insert(object);
            } else {
                update(object);
            }
        } else {
            update(object);
            
        }
    }
    
    /*
    public void removeFromCache(T object){
        Wrapper w = cache.get(object);
        index.remove(w.id);
        cache.remove(object);
        if ( object instanceof Observable  ){
            ((Observable)object).deleteObserver(w);
        }
    }
    */
    
    /*
    public void clearCache(){
        List<T> o = new ArrayList<T>();
        o.addAll(cache.keySet());
        for ( T t : o ){
            removeFromCache(t);
        }
    }*/
    
    /**
     * Checks to see if the entity object is dirty and should be saved.
     * @param object
     * @return 
     */
    public boolean isDirty(T object){
        long id = getId(object);
        Wrapper w = cache.get(id);
        if ( w == null  ){
            return true;
        }
        if ( object instanceof Observable ){
            return w.dirtyFlag || !w.dirty.isEmpty();
        } else {
            return w.dirtyFlag;
        }
    }
    
    /**
     * Creates a new empty entity object for this DAO.  This should be implemented
     * by a subclass.
     * @return 
     */
    public abstract T newObject();
    
    /**
     * Copies values from the provided map into the provided entity object.
     * @param obj The entity object to which the values should be copied.
     * @param values The map from which the values are to be copied
     */
    public abstract void unmap(T obj, Map values);
    
    /**
     * Copies values from the provided entity object to the provided map.
     * @param obj The entity object from which the values should be copied.
     * @param values The map to which the values should be copied.
     */
    public abstract void map(T obj, Map values);
    
    /**
     * Initializes a wrapper object with the given id, entity object, and values.
     * @param id The ID of the object.
     * @param object The entity object.
     * @param m Values to add to the object.
     * @return The resulting wrapper.
     */
    protected Wrapper initObject(long id, T object,  Map m){
        Wrapper w = new Wrapper();
        w.setObject(object);
        //w.id = id;
        if ( object instanceof Observable ){
            ((Observable)object).addObserver(w);
        }
        unmap(object, m);
        w.dirty.clear();
        w.dirtyFlag = false;
        //index.put(id, object);
        cache.put(id, w);
        return w;
    }
    
    /**
     * Updates the dirty flag of the given entity object.
     * @param object The entity object to apply the flag to.
     * @param dirty True to make the object dirty.  False to make it clean.
     */
    public void setDirty(T object, boolean dirty){
        long id = getId(object);
        if ( id <= 0 ){
            return;
        }
        Wrapper w = cache.get(id);
        if ( w != null ){
            w.dirtyFlag = dirty;
            if ( !dirty && !w.dirty.isEmpty()){
                w.dirty.clear();
            }
        }
        
    }
    
    /**
     * Gets the ID of a provided entity object.  Should be implemented by a subclass.
     * @param object The entity object whose ID we wish to obtain.
     * @return The ID of the provided entity object.
     */
    public abstract long getId(T object);
    
    /**
     * Reference to the Database object for this DAO.
     * @return 
     */
    protected Database db(){
        return db;
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
    public void importSet(Map set, String selector, Map columnMap, String[] keyCols) throws IOException{
        Importer<T> i = new Importer(this, keyCols, columnMap);
        i.importSet(set, selector);
        
    }
    
    public final void importSet(Map set, String selector, Map columnMap) throws IOException{
        Importer<T> i = new Importer<T>(this, null, columnMap);
        i.importSet(set, selector);
    }
    
    public final void importSet(Map set, String selector) throws IOException{
        Importer<T> i = new Importer<T>(this);
        i.importSet(set, selector);
    }
    
    public void importSet(List rows, Map columnMap, String[] keyCols) throws IOException{
        
         Importer<T> i = new Importer<T>(this, keyCols, columnMap);
         i.importSet(rows);
    }
    
    public final void importSet(List rows, Map columnMap) throws IOException {
        Importer<T> i = new Importer<T>(this, null, columnMap);
        i.importSet(rows);
    }
    
    public final void importSet(List rows) throws IOException{
        importSet(rows, null);
    }
    
    public final void importJSON(InputStream is, String selector, Map columnMap, String[] keyCols) throws IOException{
        Importer<T> i = new Importer<T>(this, keyCols, columnMap );
        i.importJSON(is, selector);
        
    }
    
    public final void importJSON(InputStream is, String selector, Map columnMap) throws IOException {
        Importer<T> i = new Importer<T>(this, null, columnMap );
        i.importJSON(is, selector);
    }
    
    public final void importJSON(InputStream is, String selector) throws IOException{
        importJSON(is, selector, null);
    }
    
    
}
