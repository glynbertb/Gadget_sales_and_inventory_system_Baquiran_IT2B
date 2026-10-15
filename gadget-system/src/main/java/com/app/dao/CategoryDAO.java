package com.app.dao;

import com.app.model.Category;
import java.sql.Connection;
import java.util.List;
import java.util.Optional;

public interface CategoryDAO {
    List<Category> findAll();
    Optional<Category> findById(int id);
    boolean existsByName(String name);
    boolean isInUse(int id);
    int insert(Connection c, Category category);
    void update(Connection c, Category category);
    void delete(Connection c, int id);
}
