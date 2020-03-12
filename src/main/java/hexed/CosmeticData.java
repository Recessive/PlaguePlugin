package hexed;

import arc.util.Log;
import com.fasterxml.jackson.databind.ObjectMapper;
import mindustry.content.*;
import mindustry.entities.Effects;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;

import java.io.IOException;
import java.lang.reflect.Field;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.ResultSet;
import java.sql.PreparedStatement;
import java.util.*;

public class CosmeticData {

    public Connection conn = null;
    public List<Effects.Effect> trailList = new ArrayList<Effects.Effect>();

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

    public String getCol(String uuid){
        String sql;
        sql = "SELECT color FROM cosmetics WHERE uuid = '" + uuid + "'";

        try {
            Statement stmt  = conn.createStatement();
            ResultSet rs    = stmt.executeQuery(sql);

            return rs.getString("color");
        } catch (SQLException ignored) {
        }
        return "";
    }

    public void setCol(String uuid, String color){
        String sql;
        sql = "UPDATE cosmetics SET color= '" + color + "' WHERE uuid = '" + uuid + "'";

        try {
            Statement stmt = conn.createStatement();
            stmt.executeUpdate(sql);
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    public int getTrail(String uuid){
        String sql;
        sql = "SELECT trail FROM cosmetics WHERE uuid = '" + uuid + "'";

        try {
            Statement stmt  = conn.createStatement();
            ResultSet rs    = stmt.executeQuery(sql);

            return rs.getInt("trail");
        } catch (SQLException ignored) {
        }
        return 0;
    }

    public void setTrail(String uuid, int trail){
        String sql;
        sql = "UPDATE cosmetics SET trail= " + trail + " WHERE uuid = '" + uuid + "'";

        try {
            Statement stmt = conn.createStatement();
            stmt.executeUpdate(sql);
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    public boolean getTrailToggle(String uuid){
        String sql;
        sql = "SELECT doTrail FROM cosmetics WHERE uuid = '" + uuid + "'";

        try {
            Statement stmt  = conn.createStatement();
            ResultSet rs    = stmt.executeQuery(sql);

            return rs.getBoolean("doTrail");
        } catch (SQLException ignored) {
        }
        return false;
    }

    public void toggleTrail(String uuid){
        int trail = !getTrailToggle(uuid) ? 1 : 0; // convert bool to int
        String sql;
        sql = "UPDATE cosmetics SET doTrail= " + trail + " WHERE uuid = '" + uuid + "'";

        try {
            Statement stmt = conn.createStatement();
            stmt.executeUpdate(sql);

        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    public List<String> getTrails(String uuid){
        String sql;
        sql = "SELECT trails FROM cosmetics WHERE uuid = '" + uuid + "'";

        try {
            Statement stmt  = conn.createStatement();
            ResultSet rs    = stmt.executeQuery(sql);

            return new ArrayList<String>(Arrays.asList(rs.getString("trails").split(",")));
        } catch (SQLException | NullPointerException ignored) {
        }
        return new ArrayList<>();
    }

    public boolean addTrail(String uuid, String trail){
        boolean returnVal = false;
        List<String> trails = getTrails(uuid);
        if(!trails.contains(trail)){
            trails.add(trail);
            returnVal = true;
        }
        String trailStr = String.join(",", trails);
        String sql;
        sql = "UPDATE cosmetics SET trails= '" + trailStr + "' WHERE uuid = '" + uuid + "'";
        try {
            Statement stmt = conn.createStatement();
            stmt.executeUpdate(sql);
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return returnVal;
    }

    public boolean removeTrail(String uuid, String trail){
        boolean returnVal = false;
        List<String> trails = getTrails(uuid);
        if(trails.contains(trail)){
            trails.remove(trail);
            returnVal = true;
        }
        String trailStr = String.join(",", trails);
        String sql;
        sql = "UPDATE cosmetics SET trails= " + trailStr + " WHERE uuid = '" + uuid + "'";

        try {
            Statement stmt = conn.createStatement();
            stmt.executeUpdate(sql);
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return returnVal;
    }
}
