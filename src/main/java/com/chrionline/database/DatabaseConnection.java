package com.chrionline.database;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;


public class DatabaseConnection {

    private static final String URL      = "jdbc:mysql://localhost:3306/chrionline"
                                         + "?useSSL=false"
                                         + "&serverTimezone=UTC"
                                         + "&allowPublicKeyRetrieval=true"
                                         + "&characterEncoding=utf8";
    private static final String USERNAME = "root";
    private static final String PASSWORD = "";

    private static volatile DatabaseConnection instance;

    private Connection connection;

    private DatabaseConnection() {
        openConnection();
    }

  
    public static DatabaseConnection getInstance() {
        if (instance == null) {                         // first check (no lock)
            synchronized (DatabaseConnection.class) {
                if (instance == null) {                 // second check (inside lock)
                    instance = new DatabaseConnection();
                }
            }
        }
        return instance;
    }


    public Connection getConnection() {
        try {
            if (connection == null || connection.isClosed()) {
                System.out.println("[DB] Connection is not open — reconnecting …");
                openConnection();
            }
        } catch (SQLException e) {
            throw new RuntimeException("[DB] Failed to verify connection state: " + e.getMessage(), e);
        }
        return connection;
    }

    public void closeConnection() {
        if (connection != null) {
            try {
                if (!connection.isClosed()) {
                    connection.close();
                    System.out.println("[DB] Connection closed successfully.");
                }
            } catch (SQLException e) {
                System.err.println("[DB] Error while closing connection: " + e.getMessage());
            } finally {
                connection = null;
            }
        }
    }


    private void openConnection() {
        try {
            Class.forName("com.mysql.cj.jdbc.Driver");

            connection = DriverManager.getConnection(URL, USERNAME, PASSWORD);
            System.out.println("[DB] Connection established successfully  →  " + URL);

        } catch (ClassNotFoundException e) {
            throw new RuntimeException(
                "[DB] MySQL JDBC driver not found on the classpath. "
                + "Add mysql-connector-j to pom.xml: " + e.getMessage(), e);

        } catch (SQLException e) {
            throw new RuntimeException(
                "[DB] Unable to connect to the database at " + URL
                + " — check credentials and that MySQL is running: " + e.getMessage(), e);
        }
    }
}
