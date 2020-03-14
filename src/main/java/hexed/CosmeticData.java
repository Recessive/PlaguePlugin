package hexed;

import arc.util.Log;
import mindustry.content.*;
import mindustry.entities.Effects;

import java.lang.reflect.Field;
import java.sql.*;
import java.util.*;
// \uF87F
public class CosmeticData {

    public Connection conn = null;
    public List<Effects.Effect> trailList = new ArrayList<Effects.Effect>();
    public HashMap<String, HashMap<String, Object>> entries = new HashMap<String, HashMap<String, Object>>();

    public void initMappings(){
        Fx a = new Fx();
        for (Field f : a.getClass().getDeclaredFields()) {
            try {
                Object value = f.get(a);
                trailList.add((Effects.Effect) value);
            } catch (IllegalAccessException e) {
                e.printStackTrace();
            }
        }

    }


    public void connect(String db){
        // SQLite connection string
        String url = "jdbc:sqlite:" + db;
        try {
            Class.forName("org.sqlite.JDBC");
            conn = DriverManager.getConnection(url);
        } catch (SQLException | ClassNotFoundException e) {
            e.printStackTrace();
        }
        System.out.println("Connected to database successfully");
    }

    public void connect(Connection c){
        conn = c;
    }

    public boolean hasRow(String uuid){
        String sql;
        sql = "SELECT uuid FROM cosmetics WHERE uuid = '" + uuid + "'";
        try {
            Statement stmt  = conn.createStatement();
            ResultSet rs    = stmt.executeQuery(sql);

            // loop through the result set
            return rs.getString("uuid").length() != 0;
        } catch (SQLException ignored) {
        }
        return false;
    }

    public void addPlayer(String uuid){
        String sql = "INSERT INTO cosmetics(uuid) VALUES('" + uuid + "')";

        try {
            Statement stmt = conn.createStatement();
            stmt.executeUpdate(sql);
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    public void loadPlayer(String uuid){
        HashMap<String, Object> vals = new HashMap<String, Object>();

        if(!hasRow(uuid)){addPlayer(uuid);}

        String sql = "SELECT * FROM cosmetics where uuid = '" + uuid + "'";

        Statement stmt  = null;
        try {
            stmt = conn.createStatement();
            ResultSet rs = stmt.executeQuery(sql);
            ResultSetMetaData rsmd = rs.getMetaData();
            for(int i = 1; i <= rsmd.getColumnCount(); i++){ // ONE INDEXED? REALLY?
                vals.put(rsmd.getColumnName(i),rs.getObject(rsmd.getColumnName(i)));
            }
            rs.close();
        } catch (SQLException e) {
            e.printStackTrace();
        }

        String tstr = (String) vals.get("trails");
        if(tstr == null) {
            vals.put("trails", new ArrayList<>());
        }else{
            vals.put("trails", new ArrayList<>(Arrays.asList(tstr.split(","))));
        }
        entries.put(uuid, vals);

    }

    public void savePlayer(String uuid){
        HashMap<String, Object> vals = entries.get(uuid);

        String trailStr = String.join(",", (List<String>) vals.get("trails"));
        vals.put("trails", trailStr);

        try {
            String sql = "UPDATE cosmetics SET ";
            int c = 0;
            Log.info(vals);
            for(Object key: vals.keySet()){
                if(vals.get(key) == null){
                    continue;
                }
                if(c > 0){
                   sql += ",";
                }
                c ++;
                if(vals.get(key) instanceof String) {
                    sql += key + " = '" + vals.get(key) + "'";
                }else if(vals.get(key) instanceof Boolean){
                    sql += key + " = " + ((boolean) vals.get(key) ? 1: 0);
                }else{
                    sql += key + " = " + vals.get(key);
                }

            }
            sql += " WHERE uuid = '" + uuid + "'";
            Log.info(sql);

            Statement stmt = conn.createStatement();
            stmt.executeUpdate(sql);
            stmt.close();
        } catch (SQLException e) {
            e.printStackTrace();
        }

        entries.remove(uuid);

    }

    public List<String> getTrails(String uuid){
        return (List<String>) entries.get(uuid).get("trails");
    }

    public void addTrail(String uuid, String trail){
        List<String> trails = (List<String>) entries.get(uuid).get("trails");
        trails.add(trail);
    }

    public void setTrail(String uuid, Integer trail){
        entries.get(uuid).put("equippedTrail", trail);
    }

    public int getTrail(String uuid){
        return (int) entries.get(uuid).get("equippedTrail");
    }

    public void setCol(String uuid, String color){
        entries.get(uuid).put("color", color);
    }

    public void toggleTrail(String uuid){
        boolean toggle = getTrailToggle(uuid);
        entries.get(uuid).put("doTrail",!toggle);
    }

    public boolean getTrailToggle(String uuid){
        Object t = entries.get(uuid).get("doTrail");
        boolean toggle;
        if(t == null){
            toggle = false;
        }else if(!(t instanceof Boolean)) {
            toggle = (int) t == 1;
        }else{
            toggle = (boolean) t;
        }
        return toggle;
    }
}
