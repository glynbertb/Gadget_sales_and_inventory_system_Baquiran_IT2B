package com.app.dao;

import com.app.model.Category;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;

public class CategoryDAOImpl implements CategoryDAO {

    private static final String SELECT = "SELECT category_id, category_name, description FROM categories";

    private static Category map(ResultSet rs) throws SQLException {
        return new Category(rs.getInt("category_id"), rs.getString("category_name"), rs.getString("description"));
    }

    @Override public List<Category> findAll() {
        return JdbcHelper.query(SELECT + " ORDER BY category_name", null, CategoryDAOImpl::map);
    }

    @Override public Optional<Category> findById(int id) {
        return JdbcHelper.queryOne(SELECT + " WHERE category_id = ?", ps -> ps.setInt(1, id), CategoryDAOImpl::map);
    }

    @Override public boolean existsByName(String name) {
        return JdbcHelper.queryOne("SELECT 1 FROM categories WHERE LOWER(category_name) = LOWER(?)",
                ps -> ps.setString(1, name), rs -> rs.getInt(1)).isPresent();
    }

    @Override public boolean isInUse(int id) {
        return JdbcHelper.queryOne("SELECT COUNT(*) FROM products WHERE category_id = ?",
                ps -> ps.setInt(1, id), rs -> rs.getInt(1)).orElse(0) > 0;
    }

    @Override public int insert(Connection c, Category cat) {
        return JdbcHelper.insert(c, "INSERT INTO categories (category_name, description) VALUES (?, ?)", ps -> {
            ps.setString(1, cat.getCategoryName());
            ps.setString(2, cat.getDescription());
        });
    }

    @Override public void update(Connection c, Category cat) {
        JdbcHelper.update(c, "UPDATE categories SET category_name = ?, description = ? WHERE category_id = ?", ps -> {
            ps.setString(1, cat.getCategoryName());
            ps.setString(2, cat.getDescription());
            ps.setInt(3, cat.getCategoryId());
        });
    }

    @Override public void delete(Connection c, int id) {
        JdbcHelper.update(c, "DELETE FROM categories WHERE category_id = ?", ps -> ps.setInt(1, id));
    }
}
