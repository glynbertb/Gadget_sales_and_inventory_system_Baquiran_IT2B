package com.app.dao;

import com.app.model.User;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;

public class UserDAOImpl implements UserDAO {

    private static final String SELECT = "SELECT user_id, name, username, password, role, status FROM users";

    private static User map(ResultSet rs) throws SQLException {
        User u = new User();
        u.setUserId(rs.getInt("user_id"));
        u.setName(rs.getString("name"));
        u.setUsername(rs.getString("username"));
        u.setPassword(rs.getString("password"));
        u.setRole(rs.getString("role"));
        u.setStatus(rs.getString("status"));
        return u;
    }

    @Override public Optional<User> findById(int userId) {
        return JdbcHelper.queryOne(SELECT + " WHERE user_id = ?", ps -> ps.setInt(1, userId), UserDAOImpl::map);
    }

    @Override public Optional<User> findByUsername(String username) {
        return JdbcHelper.queryOne(SELECT + " WHERE username = ?", ps -> ps.setString(1, username), UserDAOImpl::map);
    }

    @Override public List<User> findAll() {
        return JdbcHelper.query(SELECT + " ORDER BY user_id", null, UserDAOImpl::map);
    }

    @Override public int insert(Connection c, User u) {
        return JdbcHelper.insert(c, "INSERT INTO users (name, username, password, role, status) VALUES (?, ?, ?, ?, ?)", ps -> {
            ps.setString(1, u.getName());
            ps.setString(2, u.getUsername());
            ps.setString(3, u.getPassword());
            ps.setString(4, u.getRole());
            ps.setString(5, u.getStatus() == null ? User.STATUS_ACTIVE : u.getStatus());
        });
    }

    @Override public void update(Connection c, User u) {
        JdbcHelper.update(c, "UPDATE users SET name = ?, role = ? WHERE user_id = ?", ps -> {
            ps.setString(1, u.getName());
            ps.setString(2, u.getRole());
            ps.setInt(3, u.getUserId());
        });
    }

    @Override public void updateStatus(Connection c, int userId, String status) {
        JdbcHelper.update(c, "UPDATE users SET status = ? WHERE user_id = ?", ps -> { ps.setString(1, status); ps.setInt(2, userId); });
    }

    @Override public void updatePassword(Connection c, int userId, String hash) {
        JdbcHelper.update(c, "UPDATE users SET password = ? WHERE user_id = ?", ps -> { ps.setString(1, hash); ps.setInt(2, userId); });
    }
}
