package com.digitaleye.database;

import java.io.IOException;
import java.io.InputStream;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.Properties;

/**
 * Central place that opens a JDBC connection to MySQL using the settings
 * in db.properties. Every DAO calls DBConnection.get() rather than
 * hardcoding connection details itself - if the DB moves or credentials
 * change, only db.properties needs to change.
 */
public class DBConnection {

    private static final Properties props = new Properties();

    static {
        try (InputStream in = DBConnection.class.getClassLoader().getResourceAsStream("db.properties")) {
            if (in == null) {
                throw new RuntimeException("db.properties not found on the classpath");
            }
            props.load(in);
        } catch (IOException e) {
            throw new RuntimeException("Failed to load db.properties", e);
        }
    }

    public static Connection get() throws SQLException {
        return DriverManager.getConnection(
                props.getProperty("db.url"),
                props.getProperty("db.username"),
                props.getProperty("db.password")
        );
    }
}
