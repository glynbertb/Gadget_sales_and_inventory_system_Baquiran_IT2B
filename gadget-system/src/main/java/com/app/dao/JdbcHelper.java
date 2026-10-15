package com.app.dao;

import com.app.util.DataAccessException;
import com.app.util.DatabaseConfig;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/** Small internal helper that removes try/catch boilerplate from the DAO implementations. */
final class JdbcHelper {

    @FunctionalInterface interface Binder { void bind(PreparedStatement ps) throws SQLException; }
    @FunctionalInterface interface RowMapper<T> { T map(ResultSet rs) throws SQLException; }

    private JdbcHelper() { }

    static <T> List<T> query(String sql, Binder binder, RowMapper<T> mapper) {
        try (Connection c = DatabaseConfig.getConnection()) {
            return query(c, sql, binder, mapper);
        } catch (SQLException e) {
            throw new DataAccessException("Query failed: " + e.getMessage(), e);
        }
    }

    static <T> List<T> query(Connection c, String sql, Binder binder, RowMapper<T> mapper) {
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            if (binder != null) binder.bind(ps);
            try (ResultSet rs = ps.executeQuery()) {
                List<T> out = new ArrayList<>();
                while (rs.next()) out.add(mapper.map(rs));
                return out;
            }
        } catch (SQLException e) {
            throw new DataAccessException("Query failed: " + e.getMessage(), e);
        }
    }

    static <T> Optional<T> queryOne(String sql, Binder binder, RowMapper<T> mapper) {
        List<T> l = query(sql, binder, mapper);
        return l.isEmpty() ? Optional.empty() : Optional.of(l.get(0));
    }

    static <T> Optional<T> queryOne(Connection c, String sql, Binder binder, RowMapper<T> mapper) {
        List<T> l = query(c, sql, binder, mapper);
        return l.isEmpty() ? Optional.empty() : Optional.of(l.get(0));
    }

    static int update(Connection c, String sql, Binder binder) {
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            if (binder != null) binder.bind(ps);
            return ps.executeUpdate();
        } catch (SQLException e) {
            throw new DataAccessException("Update failed: " + e.getMessage(), e);
        }
    }

    /** Executes an INSERT and returns the generated primary key (same connection => same transaction). */
    static int insert(Connection c, String sql, Binder binder) {
        update(c, sql, binder);
        return query(c, "SELECT last_insert_rowid()", null, rs -> rs.getInt(1)).get(0);
    }
}
