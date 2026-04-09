package com.chrionline.database;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

public class DatabaseConnection {

    private static final Logger LOG = LogManager.getLogger(DatabaseConnection.class);

    private static final String URL = "jdbc:mysql://localhost:3306/chrionline"
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
        if (instance == null) {
            synchronized (DatabaseConnection.class) {
                if (instance == null) {
                    instance = new DatabaseConnection();
                }
            }
        }
        return instance;
    }

    public Connection getConnection() {
        try {
            if (connection == null || connection.isClosed()) {
                LOG.info("[DB] Connection is not open - reconnecting");
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
                    LOG.info("[DB] Connection closed successfully.");
                }
            } catch (SQLException e) {
                LOG.warn("[DB] Error while closing connection: {}", e.getMessage(), e);
            } finally {
                connection = null;
            }
        }
    }

    private void openConnection() {
        try {
            Class.forName("com.mysql.cj.jdbc.Driver");
            connection = DriverManager.getConnection(URL, USERNAME, PASSWORD);
            LOG.info("[DB] Connection established successfully -> {}", URL);
        } catch (ClassNotFoundException e) {
            throw new RuntimeException(
                "[DB] MySQL JDBC driver not found on the classpath. "
                    + "Add mysql-connector-j to pom.xml: " + e.getMessage(),
                e
            );
        } catch (SQLException e) {
            throw new RuntimeException(
                "[DB] Unable to connect to the database at " + URL
                    + " - check credentials and that MySQL is running: " + e.getMessage(),
                e
            );
        }
    }
}
