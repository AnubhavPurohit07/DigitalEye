package com.digitaleye.database;

import com.digitaleye.database.dao.UserDao;
import com.digitaleye.database.model.User;

/**
 * Quick manual smoke test - NOT part of the application itself.
 * Run this after creating the schema and filling in db.properties to
 * confirm the JDBC connection and DAOs actually work before anyone
 * builds on top of this module.
 */
public class DbConnectionTest {

    public static void main(String[] args) {
        try {
            UserDao userDao = new UserDao();

            User testAdmin = new User(
                    "admin",
                    "TEMP_HASH_REPLACE_LATER",
                    "Administrator",
                    "admin@digitaleye.local",
                    "ADMIN",
                    null
            );

            Long newId = userDao.insert(testAdmin);
            System.out.println("Inserted test admin user with id: " + newId);

            User fetched = userDao.findByUsername("admin");
            System.out.println("Fetched back: " + fetched.getFullName() + " (" + fetched.getRole() + ")");

            System.out.println("Database connection and DAO layer are working.");
        } catch (Exception e) {
            System.err.println("Something went wrong - check db.properties and that schema.sql has been run.");
            e.printStackTrace();
        }
    }
}
