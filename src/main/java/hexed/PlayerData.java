package hexed;

import java.sql.Array;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.ResultSet;
import java.sql.PreparedStatement;
import java.util.ArrayList;
import java.util.List;

public class PlayerData {

    private Connection conn = null;

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

    public boolean hasRow(String uuid){
        String sql;
        sql = "SELECT uuid FROM players WHERE uuid = '" + uuid + "'";
        try {
            Statement stmt  = conn.createStatement();
            ResultSet rs    = stmt.executeQuery(sql);

            // loop through the result set
            return rs.getString("uuid").length() != 0;
        } catch (SQLException ignored) {
        }
        return false;
    }

    public int[] getWins(String uuid){
        String sql;
        sql = "SELECT monthWins,allWins FROM players WHERE uuid = '" + uuid + "'";

        try {
            Statement stmt  = conn.createStatement();
            ResultSet rs    = stmt.executeQuery(sql);

            int wins[] = new int[2];

            wins[0] = rs.getInt("monthWins");
            wins[1] = rs.getInt("allWins");

            return wins;
        } catch (SQLException ignored) {
        }
        return null;
    }

    public void addWin(String uuid){
        boolean check = hasRow(uuid);
        String sql;
        if (!check){
            sql = "INSERT INTO players(uuid,monthWins,allWins) VALUES('" + uuid + "',1,1)";
        }else{
            int wins[] = getWins(uuid);
            sql = "UPDATE players SET monthWins = " + (wins[0] + 1) + ", allWins = "
                    + (wins[1] + 1) + " WHERE uuid = '" + uuid + "'";
        }
        try {
            Statement stmt = conn.createStatement();
            stmt.executeUpdate(sql);
        } catch (SQLException e) {
            e.printStackTrace();
        }

    }

    public int getGames(String uuid){
        String sql;
        sql = "SELECT gamesPlayed FROM players WHERE uuid = '" + uuid + "'";

        try {
            Statement stmt  = conn.createStatement();
            ResultSet rs    = stmt.executeQuery(sql);

            return rs.getInt("gamesPlayed");
        } catch (SQLException ignored) {
        }
        return 0;
    }

    public void addGame(String uuid){
        String sql;
        sql = "UPDATE players SET gamesPlayed =" + (getGames(uuid) + 1) +  " WHERE uuid = '" + uuid + "'";

        try {
            Statement stmt = conn.createStatement();
            stmt.executeUpdate(sql);
        } catch (SQLException e) {
            e.printStackTrace();
        }

    }

    public int getHexesCaptured(String uuid){
        String sql;
        sql = "SELECT hexesCaptured FROM players WHERE uuid = '" + uuid + "'";

        try {
            Statement stmt  = conn.createStatement();
            ResultSet rs    = stmt.executeQuery(sql);

            return rs.getInt("hexesCaptured");
        } catch (SQLException ignored) {
        }
        return 0;
    }

    public void addHexCaptures(String uuid, int hexes){
        String sql;
        sql = "UPDATE players SET hexesCaptured =" + (getHexesCaptured(uuid) + hexes) +  " WHERE uuid = '" + uuid + "'";

        try {
            Statement stmt = conn.createStatement();
            stmt.executeUpdate(sql);
        } catch (SQLException e) {
            e.printStackTrace();
        }

    }

    public ArrayList<ArrayList<String>> getTopWins(String filter, int count){
        String sql;
        sql = "select * from players order by " + filter + " desc";

        try {
            PreparedStatement stmt  = conn.prepareStatement(sql);
            ResultSet rs    = stmt.executeQuery();

            ArrayList<ArrayList<String>> top = new ArrayList<ArrayList<String>>();//create a List

            for (int i = 0; i < 4; i ++) {
                top.add(new ArrayList<String>());
            }
            int c = 0;
            while (rs.next()){//loop throw the result set
                top.get(0).add(rs.getString("uuid"));
                top.get(1).add(rs.getString("latestName"));
                top.get(2).add(rs.getString("monthWins"));
                top.get(3).add(rs.getString("allWins"));
                c ++;
                if(c >= count){
                    break;
                }
            }
            return top;
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return null;
    }


    public void _setWins(String uuid, int monthWins, int allWins){
        boolean check = hasRow(uuid);
        String sql;

        if (monthWins < 0){monthWins = getWins(uuid)[0];}
        if (allWins < 0){allWins = getWins(uuid)[1];}

        if (!check){
            sql = "INSERT INTO players(uuid,monthWins,allWins) VALUES('" + uuid + "'," + monthWins + "," + allWins + ")";
        }else{
            sql = "UPDATE players SET monthWins = " + monthWins + ", allWins = "
                    + allWins + " WHERE uuid = '" + uuid + "'";
        }
        try {
            Statement stmt = conn.createStatement();
            stmt.executeUpdate(sql);
        } catch (SQLException e) {
            e.printStackTrace();
        }

    }

    public void setWins(String uuid, int monthWins, int allWins){
        _setWins(uuid,monthWins,allWins);

    }

    public void setName(String uuid, String name){
        boolean check = hasRow(uuid);
        if (!check){
            setWins(uuid, 0, 0);
        }
        String sql;
        sql = "UPDATE players SET latestName= '" + name + "' WHERE uuid = '" + uuid + "'";

        try {
            Statement stmt = conn.createStatement();
            stmt.executeUpdate(sql);
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    public String getCol(String uuid){
        String sql;
        sql = "SELECT color FROM players WHERE uuid = '" + uuid + "'";

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
        sql = "UPDATE players SET color= '" + color + "' WHERE uuid = '" + uuid + "'";

        try {
            Statement stmt = conn.createStatement();
            stmt.executeUpdate(sql);
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    public int[] getPoints(String uuid){
        String sql;
        sql = "SELECT monthPoints,allPoints FROM players WHERE uuid = '" + uuid + "'";

        try {
            Statement stmt  = conn.createStatement();
            ResultSet rs    = stmt.executeQuery(sql);

            int points[] = new int[2];

            points[0] = rs.getInt("monthPoints");
            points[1] = rs.getInt("allPoints");

            return points;
        } catch (SQLException ignored) {
        }
        return null;
    }

    public void addPoints(String uuid, int pointVal){
        boolean check = hasRow(uuid);
        String sql;
        if (!check){
            sql = "INSERT INTO players(uuid,monthWins,allWins) VALUES('" + uuid + "'," + pointVal + "," + pointVal + ")";
        }else{
            int points[] = getPoints(uuid);
            sql = "UPDATE players SET monthPoints = " + (points[0] + pointVal) + ", allPoints = "
                    + (points[1] + pointVal) + " WHERE uuid = '" + uuid + "'";
        }
        try {
            Statement stmt = conn.createStatement();
            stmt.executeUpdate(sql);
        } catch (SQLException e) {
            e.printStackTrace();
        }

    }

    public ArrayList<ArrayList<String>> getTopPoints(String filter, int count){
        String sql;
        sql = "select * from players order by " + filter + " desc";

        try {
            PreparedStatement stmt  = conn.prepareStatement(sql);
            ResultSet rs    = stmt.executeQuery();

            ArrayList<ArrayList<String>> top = new ArrayList<ArrayList<String>>();//create a List

            for (int i = 0; i < 4; i ++) {
                top.add(new ArrayList<String>());
            }
            int c = 0;
            while (rs.next()){//loop throw the result set
                top.get(0).add(rs.getString("uuid"));
                top.get(1).add(rs.getString("latestName"));
                top.get(2).add(rs.getString("monthPoints"));
                top.get(3).add(rs.getString("allPoints"));
                c ++;
                if(c >= count){
                    break;
                }
            }
            return top;
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return null;
    }

    public int getDonatorLevel(String uuid){
        String sql;
        sql = "SELECT donatorLevel FROM players WHERE uuid = '" + uuid + "'";

        try {
            Statement stmt  = conn.createStatement();
            ResultSet rs    = stmt.executeQuery(sql);

            int level;

            level = rs.getInt("donatorLevel");

            return level;
        } catch (SQLException ignored) {
        }
        return 0;
    }

    public void setDonatorLevel(String uuid, int level){
        String sql;
        sql = "UPDATE players SET donatorLevel = " + level + " WHERE uuid = '" + uuid + "'";
        try {
            Statement stmt = conn.createStatement();
            stmt.executeUpdate(sql);
        } catch (SQLException e) {
            e.printStackTrace();
        }

    }

}
