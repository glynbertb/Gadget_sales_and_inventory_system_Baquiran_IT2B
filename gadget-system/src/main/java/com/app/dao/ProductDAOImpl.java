package com.app.dao;

import com.app.model.Product;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;

public class ProductDAOImpl implements ProductDAO {

    private static final String SELECT =
            "SELECT p.gadget_id, p.gadget_name, p.category_id, c.category_name, p.price, p.stock_quantity, p.status " +
            "FROM products p LEFT JOIN categories c ON c.category_id = p.category_id";

    private static Product map(ResultSet rs) throws SQLException {
        Product p = new Product();
        p.setGadgetId(rs.getInt("gadget_id"));
        p.setGadgetName(rs.getString("gadget_name"));
        p.setCategoryId(rs.getInt("category_id"));
        p.setCategoryName(rs.getString("category_name"));
        p.setPrice(rs.getDouble("price"));
        p.setStockQuantity(rs.getInt("stock_quantity"));
        p.setStatus(rs.getString("status"));
        return p;
    }

    @Override public List<Product> findAll() {
        return JdbcHelper.query(SELECT + " ORDER BY p.gadget_id", null, ProductDAOImpl::map);
    }

    @Override public List<Product> findByStatus(String status) {
        return JdbcHelper.query(SELECT + " WHERE p.status = ? ORDER BY p.gadget_name",
                ps -> ps.setString(1, status), ProductDAOImpl::map);
    }

    @Override public List<Product> search(String keyword) {
        String like = "%" + keyword.toLowerCase() + "%";
        return JdbcHelper.query(SELECT + " WHERE LOWER(p.gadget_name) LIKE ? OR LOWER(c.category_name) LIKE ? ORDER BY p.gadget_name",
                ps -> { ps.setString(1, like); ps.setString(2, like); }, ProductDAOImpl::map);
    }

    @Override public List<Product> findLowStock(int threshold) {
        return JdbcHelper.query(SELECT + " WHERE p.stock_quantity <= ? AND p.status <> 'Discontinued' ORDER BY p.stock_quantity, p.gadget_name",
                ps -> ps.setInt(1, threshold), ProductDAOImpl::map);
    }

    @Override public Optional<Product> findById(int id) {
        return JdbcHelper.queryOne(SELECT + " WHERE p.gadget_id = ?", ps -> ps.setInt(1, id), ProductDAOImpl::map);
    }

    @Override public Optional<Product> findById(Connection c, int id) {
        return JdbcHelper.queryOne(c, SELECT + " WHERE p.gadget_id = ?", ps -> ps.setInt(1, id), ProductDAOImpl::map);
    }

    @Override public int insert(Connection c, Product p) {
        return JdbcHelper.insert(c,
                "INSERT INTO products (gadget_name, category_id, price, stock_quantity, status) VALUES (?, ?, ?, ?, ?)", ps -> {
                    ps.setString(1, p.getGadgetName());
                    ps.setInt(2, p.getCategoryId());
                    ps.setDouble(3, p.getPrice());
                    ps.setInt(4, p.getStockQuantity());
                    ps.setString(5, p.getStatus());
                });
    }

    @Override public void updateInfo(Connection c, Product p) {
        JdbcHelper.update(c, "UPDATE products SET gadget_name = ?, category_id = ?, price = ? WHERE gadget_id = ?", ps -> {
            ps.setString(1, p.getGadgetName());
            ps.setInt(2, p.getCategoryId());
            ps.setDouble(3, p.getPrice());
            ps.setInt(4, p.getGadgetId());
        });
    }

    @Override public void updateStock(Connection c, int id, int quantity, String status) {
        JdbcHelper.update(c, "UPDATE products SET stock_quantity = ?, status = ? WHERE gadget_id = ?", ps -> {
            ps.setInt(1, quantity);
            ps.setString(2, status);
            ps.setInt(3, id);
        });
    }

    @Override public void updateStatus(Connection c, int id, String status) {
        JdbcHelper.update(c, "UPDATE products SET status = ? WHERE gadget_id = ?", ps -> { ps.setString(1, status); ps.setInt(2, id); });
    }

    @Override public boolean deductStock(Connection c, int id, int quantity) {
        // In SQLite UPDATE, right-hand expressions see the OLD row, so (stock - qty = 0) means "will hit zero".
        // Status: >0 => Available, 0 => Out of Stock (or keep Discontinued if already Discontinued at 0).
        int rows = JdbcHelper.update(c,
                "UPDATE products SET stock_quantity = stock_quantity - ?, " +
                "status = CASE WHEN status = 'Discontinued' AND stock_quantity - ? = 0 THEN 'Discontinued' " +
                "WHEN stock_quantity - ? = 0 THEN 'Out of Stock' ELSE 'Available' END " +
                "WHERE gadget_id = ? AND stock_quantity >= ?", ps -> {
                    ps.setInt(1, quantity);
                    ps.setInt(2, quantity);
                    ps.setInt(3, quantity);
                    ps.setInt(4, id);
                    ps.setInt(5, quantity);
                });
        return rows == 1;
    }

    @Override public void increaseStock(Connection c, int id, int quantity) {
        JdbcHelper.update(c,
                "UPDATE products SET stock_quantity = stock_quantity + ?, " +
                "status = 'Available' WHERE gadget_id = ?", ps -> { ps.setInt(1, quantity); ps.setInt(2, id); });
    }
}
