package com.app.util;

import java.sql.Connection;
import java.sql.SQLException;

/**
 * Runs a unit of work inside one atomic database transaction
 * (setAutoCommit(false) -> commit, or rollback on any failure).
 */
public final class Tx {

    @FunctionalInterface
    public interface Work<T> { T run(Connection c) throws SQLException; }

    @FunctionalInterface
    public interface VoidWork { void run(Connection c) throws SQLException; }

    private Tx() { }

    public static <T> T execute(Work<T> work) {
        Connection c = null;
        try {
            c = DatabaseConfig.getConnection();
            c.setAutoCommit(false);
            T result = work.run(c);
            c.commit();
            return result;
        } catch (RuntimeException e) {
            rollbackQuietly(c);
            throw e;
        } catch (SQLException e) {
            rollbackQuietly(c);
            throw new DataAccessException("Transaction failed: " + e.getMessage(), e);
        } finally {
            closeQuietly(c);
        }
    }

    public static void run(VoidWork work) {
        execute(c -> { work.run(c); return null; });
    }

    private static void rollbackQuietly(Connection c) {
        if (c == null) return;
        try { c.rollback(); } catch (SQLException ignored) { /* nothing more to do */ }
    }

    private static void closeQuietly(Connection c) {
        if (c == null) return;
        try { c.setAutoCommit(true); c.close(); } catch (SQLException ignored) { /* nothing more to do */ }
    }
}
