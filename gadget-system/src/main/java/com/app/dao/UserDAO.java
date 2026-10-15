package com.app.dao;

import com.app.model.User;
import java.sql.Connection;
import java.util.List;
import java.util.Optional;

public interface UserDAO {
    Optional<User> findById(int userId);
    Optional<User> findByUsername(String username);
    List<User> findAll();
    int insert(Connection c, User user);
    void update(Connection c, User user);                 // name + role
    void updateStatus(Connection c, int userId, String status);
    void updatePassword(Connection c, int userId, String passwordHash);
}
