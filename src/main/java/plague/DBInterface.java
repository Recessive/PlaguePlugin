package plague;

import java.sql.*;
import java.util.HashMap;
import java.util.List;

public class DBInterface {
    public String table;
    public String key;
    public Connection conn = null;
    public HashMap<String, HashMap<String, Object>> entries = new HashMap<String, HashMap<String, Object>>();

    public DBInterface(String database){
        this.table = database;
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
        // Initialise primaryKey
        String sql = "pragma table_info('" + table + "');";
        try {
            Statement stmt  = conn.createStatement();
            ResultSet rs    = stmt.executeQuery(sql);
            key = rs.getString(2);
        } catch (SQLException ignored) {
        }
    }

    public void connect(Connection c){
        conn = c;
    }

    public boolean hasRow(String key){
        String sql;
        sql = "SELECT " + this.key + " FROM " + table + " WHERE " + this.key + " = '" + key + "'";
        try {
            Statement stmt  = conn.createStatement();
            ResultSet rs    = stmt.executeQuery(sql);

            // loop through the result set
            return rs.getString(this.key).length() != 0;
        } catch (SQLException ignored) {
        }
        return false;
    }

    public void addRow(String key){
        String sql = "INSERT INTO " + this.table + "(" + this.key + ") VALUES('" + key + "')";

        try {
            Statement stmt = conn.createStatement();
            stmt.executeUpdate(sql);
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    public void loadRow(String key){
        HashMap<String, Object> vals = new HashMap<String, Object>();

        if(!hasRow(key)) addRow(key);

        String sql = "SELECT * FROM " + this.table + " where " + this.key + " = '" + key + "'";

        Statement stmt;
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
        entries.put(key, vals);

    }

    public void saveRow(String key){
        try {
            HashMap<String, Object> vals = entries.get(key);

            try {
                String sql = "UPDATE " + this.table + " SET ";
                int c = 0;
                for (Object _key : vals.keySet()) {
                    if (vals.get(_key) == null) {
                        continue;
                    }
                    if (c > 0) {
                        sql += ",";
                    }
                    c++;
                    if (vals.get(_key) instanceof String) {
                        sql += _key + " = '" + vals.get(_key) + "'";
                    } else if (vals.get(_key) instanceof Boolean) {
                        sql += _key + " = " + ((boolean) vals.get(_key) ? 1 : 0);
                    } else {
                        sql += _key + " = " + vals.get(_key);
                    }

                }
                sql += " WHERE " + this.key + " = '" + key + "'";

                Statement stmt = conn.createStatement();
                stmt.executeUpdate(sql);
                stmt.close();
            } catch (SQLException e) {
                e.printStackTrace();
            }

            entries.remove(key);
        }catch(NullPointerException e){
            e.printStackTrace();
        }

    }
}
