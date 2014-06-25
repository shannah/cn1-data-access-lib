/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */

package ca.weblite.codename1.db;

/**
 *
 * @author shannah
 */
class NumberUtil {
    public static int intValue(Object o){
        if ( o == null ){
            return 0;
        }
        if ( o instanceof Integer ){
            return ((Integer)o).intValue();
        } else if ( o instanceof Double ){
            return ((Double)o).intValue();
        } else if ( o instanceof Short ){
            return ((Short)o).shortValue();
        } else if ( o instanceof Float ){
            return ((Float)o).intValue();
        } else if ( o instanceof Long ){
            return (int)((Long)o).longValue();
        } else {
            return Integer.parseInt(""+o);
        }
    }
    
    public static long longValue(Object o){
        if ( o == null ){
            return 0l;
        }
        if ( o instanceof Integer ){
            return ((Integer)o).longValue();
        } else if ( o instanceof Double ){
            return ((Double)o).longValue();
        } else if ( o instanceof Short ){
            return ((Short)o).shortValue();
        } else if ( o instanceof Float ){
            return ((Float)o).longValue();
        } else if ( o instanceof Long ){
            return ((Long)o).longValue();
        } else {
            return Long.parseLong(""+o);
        }
    }
    
    public static double doubleValue(Object o){
        if ( o == null ){
            return 0;
        }
        if ( o instanceof Integer ){
            return ((Integer)o).doubleValue();
        } else if ( o instanceof Double ){
            return ((Double)o).doubleValue();
        } else if ( o instanceof Short ){
            return ((Short)o).shortValue();
        } else if ( o instanceof Float ){
            return ((Float)o).doubleValue();
        } else if ( o instanceof Long ){
            return ((Long)o).doubleValue();
        } else {
            return Double.parseDouble(""+o);
        }
    }
    
}
