package com.app.dao;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;

/**
 * Database connection guardrail: every connection opened through this class has
 * SQLite foreign-key enforcement switched on via {@code PRAGMA foreign_keys = ON}.
 * Callers must close the returned connection (try-with-resources).
 *
 * <p>The file location can be overridden without recompiling:
 * {@code java -Dgadget.db=C:/data/Gadget.db -jar app.jar} or the
 * {@code GADGET_DB} environment variable. Default: {@code Gadget.db} in the
 * working directory.
 */
public final class DatabaseConnection {

    private static final String DEFAULT_FILE = "Gadget.db";
    private static final Path DB_PATH = resolvePath();
    private static final String URL = "jdbc:sqlite:" + DB_PATH;

    static {
        try {
            Class.forName("org.sqlite.JDBC");
        } catch (ClassNotFoundException e) {
            throw new ExceptionInInitializerError("SQLite JDBC driver (org.sqlite.JDBC) not found on classpath.");
        }
    }

    private DatabaseConnection() { }

    private static Path resolvePath() {
        String p = System.getProperty("gadget.db");
        if (p == null || p.isBlank()) p = System.getenv("GADGET_DB");
        if (p == null || p.isBlank()) p = DEFAULT_FILE;
        return Path.of(p).toAbsolutePath().normalize();
    }

    public static Path getDatabasePath() { return DB_PATH; }

    public static boolean databaseFileExists() { return Files.isRegularFile(DB_PATH); }

    /** Opens a new connection with foreign-key enforcement enabled. Callers must close it. */
    public static Connection getConnection() throws SQLException {
        Connection connection = DriverManager.getConnection(URL);
        try (Statement st = connection.createStatement()) {
            st.execute("PRAGMA foreign_keys = ON;");
        } catch (SQLException e) {
            connection.close();
            throw e;
        }
        return connection;
    }
}
