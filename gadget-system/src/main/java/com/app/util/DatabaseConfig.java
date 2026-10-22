package com.app.util;

import com.app.dao.DatabaseConnection;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.SQLException;

/**
 * Central JDBC connection factory for the SQLite database file (Gadget.db).
 * Override the location without recompiling:  java -Dgadget.db=C:/data/Gadget.db -jar app.jar
 * or set the GADGET_DB environment variable. Default: Gadget.db in the working directory.
 */
public final class DatabaseConfig {

    private DatabaseConfig() { }

    public static Path getDatabasePath() { return DatabaseConnection.getDatabasePath(); }

    public static boolean databaseFileExists() { return DatabaseConnection.databaseFileExists(); }

    /** Opens a new connection with foreign-key enforcement enabled. Callers must close it. */
    public static Connection getConnection() throws SQLException {
        return DatabaseConnection.getConnection();
    }
}
