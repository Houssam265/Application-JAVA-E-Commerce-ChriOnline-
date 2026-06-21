package com.chrionline.util;

import com.chrionline.database.DatabaseConnection;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;

public class CheckDB {
    public static void main(String[] args) {
        try {
            Connection conn = DatabaseConnection.getInstance().getConnection();
            Statement stmt = conn.createStatement();
            ResultSet rs = stmt.executeQuery("DESCRIBE sessions;");
            System.out.println("--- COLUMNS IN SESSIONS TABLE ---");
            while (rs.next()) {
                System.out.println(rs.getString("Field") + " - " + rs.getString("Type"));
            }
            System.out.println("---------------------------------");
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
