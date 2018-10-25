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

import ca.weblite.codename1.db.DAO.ColType;
import com.codename1.db.Cursor;
import com.codename1.db.Database;
import com.codename1.db.Row;
import com.codename1.io.Log;
import com.codename1.io.Util;
import com.codename1.ui.Display;
import com.codename1.util.StringUtil;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * A provider for DAO objects in a database.  You would generally instantiate one
 * DAOProvider object per database, and you would register one DAO per table in the 
 * database that you need to use.  A default, generic DAO implementation is provided
 * for tables that you don't explicitly register with custom DAO classes.
 * @author shannah
 */
public class DAOProvider {
    /**
     * Maps table names to the corresponding DAO object.
     */
    private final Map<String,DAO> daos = new HashMap<String,DAO>();
    
    /**
     * The database that this Provider operates on.
     */
    final Database db;
    
    /**
     * The database schema.  Stores column information for all tables.
     */
    private Map databaseSchema = null;
    
    /**
     * The schema version of this provider.  Allows for versioning the database
     * schema.
     */
    private int schemaVersion = 0;
    
    /**
     * The path to the config file (which is just a special SQL file with the
     * create, and alter table statements to set up the database.
     */
    private final String configFile;
    
    /**
     * Sets the schema version of this DAOProvider.
     * @param version 
     */
    public void setSchemaVersion(int version){
        schemaVersion = version;
    }
    
    /**
     * Gets the schema version of this DAOProvider.
     * @return 
     */
    public  int getSchemaVersion(){
        return schemaVersion;
    }
    
    /**
     * Loads the database SQL from a specified file.
     * @param file The path (from src root) to the SQL file.
     * @return A 2 dimensional map.  Maps IDs to arrays of Strings that correspond
     * to that version ID.  Each string is an SQL query that would be executed
     * in sequence to create or update the database to that version.
     */
    static Map<Integer,List<String>> loadDatabaseSQL(String file){
        InputStream is = null;
        Map<Integer, List<String>> out = new HashMap<Integer,List<String>>();
        try {
            is = Display.getInstance().getResourceAsStream(null, file);
            String contents = Util.readToString(is);
            List<String> lines = StringUtil.tokenize(contents, "\n");
            int currVersion = 0;
            List<String> versionCommands = new ArrayList<String>();
            StringBuilder cmdBuf = new StringBuilder();
            for ( String line : lines ){
                if ( "--".equals(line.trim()) && cmdBuf.length() > 0 ){
                    if ( !"".equals(cmdBuf.toString().trim())){
                        versionCommands.add(cmdBuf.toString());
                    }
                    cmdBuf.delete(0, cmdBuf.length());
                } else if ( line.toLowerCase().startsWith("--version:")){
                    if ( cmdBuf.length() > 0   ){
                        if ( !"".equals(cmdBuf.toString().trim())){
                            versionCommands.add(cmdBuf.toString());
                        }
                        cmdBuf.delete(0, cmdBuf.length());
                    }
                    int num = Integer.parseInt(line.substring(line.indexOf(":")+1, line.length()).trim());
                    
                    if ( num != currVersion ){
                        if ( !versionCommands.isEmpty() ){
                            out.put(currVersion, versionCommands);
                        }
                        currVersion = num;
                        versionCommands = new ArrayList<String>();
                    }
                } else {
                    cmdBuf.append(line).append("\n");
                }
            }
            
            if ( cmdBuf.length() > 0 && !"".equals(cmdBuf.toString().trim())){
                versionCommands.add(cmdBuf.toString());
            }
            if ( !versionCommands.isEmpty() ){
                out.put(currVersion, versionCommands);
            }
            
            //JSONParser parser = new JSONParser();
            //databaseSchema = parser.parseJSON(new InputStreamReader(is));
        } catch ( IOException ex){
           
        } finally {
            try {
                is.close();
            } catch ( Throwable t){}
        }
        return out;
    }
    
    
    /**
     * Gets the database schema for a given database.
     * @param db The database for which to get the schema.
     * @param configPath The path to the config file (SQL statements)
     * @return A map with the schema information.
     * @throws IOException 
     */
    
    Map getDatabaseSchema(Database db, String configPath) throws IOException{
        
        if ( databaseSchema == null ){
            databaseSchema = new HashMap();
            
            
            //int schemaVersion = (Integer)databaseSchema.get("version");
            int dbVersion = getDatabaseVersion(db);
            
            if ( schemaVersion > dbVersion ){
                
                Map<Integer,List<String>> updates = loadDatabaseSQL(configPath);
                List<Map.Entry<Integer,List<String>>> entries = new ArrayList<Map.Entry<Integer,List<String>>>();
                entries.addAll(updates.entrySet());
                Collections.sort(entries, new Comparator<Map.Entry<Integer,List<String>>>(){

                    public int compare(Map.Entry<Integer, List<String>> o1, Map.Entry<Integer, List<String>> o2) {
                        if ( o1.getKey() < o2.getKey() ){
                            return -1;
                        } else if ( o1.getKey() > o2.getKey() ){
                            return 1;
                        } else {
                            return 0;
                        }
                    }
                
                });
                for (Map.Entry<Integer,List<String>> v : entries ){
                    if ( v.getKey() > dbVersion ){
                        //db.beginTransaction();
                        List<String> commands = v.getValue();
                        for ( String cmd : commands ){
                            db.execute(cmd);
                        }
                        setDatabaseVersion(db, v.getKey());
                        dbVersion = v.getKey();
                        //db.commitTransaction();
                    }
                
                }
                
                setDatabaseVersion(db, schemaVersion);
                
                
                
            }
            
            
            
            
        }
        return databaseSchema;
    }
    
    /**
     * Maps Column type strings to their corresponding ColType enum type.
     * @param s
     * @return 
     */
    ColType colType(String s){
        if ( "INTEGER".equals(s)){
            return ColType.INTEGER;
        } else if ( "STRING".equals(s) ){
            return ColType.STRING;
        } else if ( "VARCHAR".equals(s)){
            return ColType.VARCHAR;
        } else if ( "FLOAT".equals(s)){
            return ColType.FLOAT;
        } else if ( "DOUBLE".equals(s)){
            return ColType.DOUBLE;
        } else if ( "LONG".equals(s)){
            return ColType.LONG;
        } else if ( "SHORT".equals(s)){
            return ColType.SHORT;
        } else if ("BLOB".equals(s)){
            return ColType.BLOB;
        }
        return ColType.STRING;
         
    }
    
    /**
     * Loads a schema into a given DAO.
     * @param tableName The table name to associate with the DAO.
     * @param dao The DAO to be loaded.
     * @throws IOException 
     */
    void loadSchema(String tableName, DAO dao) throws IOException{
        Map dbSchema = getDatabaseSchema(db, configFile);
        Map tables = (Map)dbSchema.get("tables");
        if ( tables == null ){
            tables = new HashMap();
            ((Map)dbSchema).put("tables", tables);
        }
        
        Map me = (Map)tables.get(tableName);
        if (me == null ){
            me = new HashMap();
            ((Map)tables).put(tableName, me);
            //Log.p("Executing PRAGMA query");
            try {
                Cursor c = db.executeQuery("PRAGMA table_info("+tableName+")", null);
                //Cursor c = db.executeQuery("SELECT * from \""+tableName+"\"");
                Map myFields = new HashMap();
                me.put("fields", myFields);
                //Log.p("About to loop through results");
                while ( c.next() ){
                    //Log.p("1");
                    Row row = c.getRow();
                    //Log.p("2");
                    String name = row.getString(1);
                    String type = row.getString(2);
                    //Log.p("Res name: "+name);
                    //Log.p("Res type: "+type);

                    dao.colTypes.put(name, colType(type));
                    //Log.p("3");
                    Map thisField = new HashMap();
                    thisField.put("name", name);
                    thisField.put("type", type);
                    //Log.p("4");
                    myFields.put(name, thisField);
                    //Log.p("5");
                }
                //Log.p("Finished loop");
                c.close();
            } catch (IOException ex) {
                // On WebSQL Pragma is banned
                // so we need to get creative.
                Cursor c = db.executeQuery("select sql from sqlite_master where type='table' and name=?", new String[]{tableName});
                Map myFields = new HashMap();
                me.put("fields", myFields);
                
                if (c.next()) {
                    Row row = c.getRow();
                    String val = row.getString(0);
                    val = StringUtil.replaceAll(val, "\n", " ");
                    val = StringUtil.replaceAll(val, "\t", " ");
                    while (val.indexOf("  ") >= 0) {
                        val = StringUtil.replaceAll(val, "  ", " ");
                    }
                    
                    int pos = val.indexOf("(");
                    val = val.substring(pos+1);
                    List<String> parts = StringUtil.tokenize(val, ',');
                    for (String segment : parts) {
                        List<String> words = StringUtil.tokenize(segment, ' ');
                        String firstWordUC = words.get(0).trim().toUpperCase();
                        if ("PRIMARY".equals(firstWordUC) || "KEY".equals(firstWordUC) || "INDEX".equals(firstWordUC) || "CONSTRAINT".equals(firstWordUC) || "FOREIGN".equals(firstWordUC)) {
                            continue;
                        }
                        String name = StringUtil.replaceAll(words.get(0), "\"", "");
                        if (words.size() < 2) {
                            continue;
                        }
                        String type = words.get(1).trim().toUpperCase();
                        if ("VARYING".equals(type) || "UNSIGNED".equals(type) || "BIG".equals(type) || "NATIVE".equals(type)) {
                            if (words.size() < 3) {
                                continue;
                            }
                            type = words.get(2).trim().toUpperCase();
                        }
                        int len = type.length();
                        StringBuilder sb = new StringBuilder();
                        for ( int i=0; i<len; i++) {
                           char ch = type.charAt(i);
                           if (ch >= 'A' && ch <= 'Z') {
                               sb.append(ch);
                           } 
                        }
                        
                        type = sb.toString();
                        type = normalizeType(type);
                        Map thisField = new HashMap();
                        thisField.put("name", name);
                        thisField.put("type", type);
                        myFields.put(name, thisField);
                        
                        dao.colTypes.put(name, colType(type));
                        
                        
                    }
                    
                }
                c.close();
            }
        }
    }
    
    private static final String[] intTypes = new String[] {
        "INT", "INTEGER", "TINYINT", "SMALLINT", "MEDIUMINT", "BIGINT", "INT2", "INT8"
    };
    
    private static final String[] textTypes = new String[] {
        "CHARACTER", "VARCHAR", "NCHAR", "CHARACTER", "NVARCHAR", "TEXT", "CLOB"
    };
    
    private static final String[] blobTypes = new String[] {
        "BLOB"
    };
    
    
    private static final String[] realTypes = new String[] {
        "REAL", "DOUBLE", "FLOAT"
    };
    
    private static final String[] numericTypes = new String[] {
        "NUMERIC", "DECIMAL", "BOOLEAN", "DATE", "DATETIME"
    };
    
    private int indexOf(String needle ,String[] haystack) {
        int len = haystack.length;
        for (int i=0; i<len; i++) {
            if (needle.equals(haystack[i])) {
                return i;
            }
        }
        return -1;
    }
    
    private String normalizeType(String type) {
        type = type.toUpperCase();
        if (indexOf(type, textTypes) >=0) {
            return "TEXT";
        } else if (indexOf(type, intTypes) >=0 ) {
            return "INTEGER";
        } else if (indexOf(type, blobTypes) >= 0) {
            return "BLOB";
        } else if (indexOf(type, realTypes) >= 0 ) {
            return "REAL";
        } else if (indexOf(type, numericTypes) >= 0) {
            return "NUMERIC";
        }
        return type;
    }
    
    /**
     * Sets the database version to the given schema version.
     * @param db The database to update
     * @param version The schema version to set.
     * @throws IOException 
     */
    private static void setDatabaseVersion(Database db, int version) throws IOException{
        
        db.execute("update database_version set version_number="+version);
    }
    
    /**
     * Gets the schema version that is installed in the current database.
     * @param db
     * @return
     * @throws IOException 
     */
    static int getDatabaseVersion(Database db) throws IOException{
        db.execute("CREATE TABLE IF NOT EXISTS database_version (version_number INTEGER PRIMARY KEY)");
        Cursor c = db.executeQuery("select version_number from database_version");
        if ( c.next() ){
            
            int v = c.getRow().getInteger(0);
            //Log.p(v+"");
            c.close();
            return v;
        } else {
            db.execute("INSERT INTO database_version (version_number) VALUES (0)");
            return 0;
        }
    }
    
    /**
     * Constructor to create a provider for the given database and with the given schema version.  This version 
     * of the constructor uses a default config file path of /config.sql.  So you should place your config
     * file in a file named config.sql in the src root.
     * @param db The database to operate on.
     * @param schemaVersion The schema version.
     */
    public DAOProvider(Database db, int schemaVersion){
        this(db, "/config.sql", schemaVersion);
    }
    
    /**
     * Constructor to create a provider for the given database, using a specified config file, and at a 
     * specified version.
     * @param db
     * @param configFile
     * @param schemaVersion 
     */
    public DAOProvider(Database db, String configFile, int schemaVersion){
        this.db = db;
        this.configFile = configFile;
        this.schemaVersion = schemaVersion;
    }
    
    /**
     * Gets the DAO object for the specified table name.  If none is registered,
     * it will instantiate a new GenericDAO object for the given table and register
     * it.
     * @param tableName The table name.
     * @return The DAO corresponding to the table name.
     * @throws IOException 
     */
    public DAO get(String tableName) throws IOException{
        DAO dao = daos.get(tableName);
        if ( dao == null ){
            dao = new GenericDAO(tableName, this);
            daos.put(tableName, dao);
        }
        return dao;
    }
    
    /**
     * Registers a DAO for the given table name.
     * @param tableName The table name.
     * @param dao The DAO object to use for interacting with the table.
     */
    public void set(String tableName, DAO dao){
        daos.put(tableName, dao);
    }
    
    
    /**
     * A Generic DAO object that uses a Map as the entity object.
     */
    private static class GenericDAO extends DAO<Map> {
        
        
        public GenericDAO(String tableName, DAOProvider provider) throws IOException {
            super(tableName, provider);
        }

        @Override
        public Map newObject() {
            return new KeyableHashMap();
        }

        @Override
        public void unmap(Map obj, Map values) {
            obj.putAll(values);
        }

        @Override
        public void map(Map obj, Map values) {
            values.putAll(obj);
        }

        @Override
        public long getId(Map object) {
            return NumberUtil.longValue(object.get("id"));
        }

        
        
        
        
        
        
    }
    
    /**
     * A HashMap with equals() and hashCode() overridden so that equality will not
     * be based on the values in the map.
     */
    private static class KeyableHashMap extends HashMap{
        private final Object hashObject = new Object();
        @Override
        public int hashCode() {
            return hashObject.hashCode();
        }

        @Override
        public boolean equals(Object obj) {
            if ( obj instanceof KeyableHashMap ){
                return hashObject.equals(((KeyableHashMap)obj).hashObject);
            }
            return false;
        }
    }
    
    public Database getDatabase(){
        return db;
    }
}
