package com.app.dao;

import com.app.model.Product;
import java.sql.Connection;
import java.util.List;
import java.util.Optional;

public interface ProductDAO {
    List<Product> findAll();
    List<Product> findByStatus(String status);
    List<Product> search(String keyword);
    List<Product> findLowStock(int threshold);
    Optional<Product> findById(int id);
    Optional<Product> findById(Connection c, int id);

    int insert(Connection c, Product p);
    void updateInfo(Connection c, Product p);                       // name, category, price
    void updateStock(Connection c, int id, int quantity, String status);
    void updateStatus(Connection c, int id, String status);

    /** Atomically deducts stock only if enough exists; flips status to 'Out of Stock' at 0. */
    boolean deductStock(Connection c, int id, int quantity);

    /** Atomically adds stock; flips 'Out of Stock' back to 'Available' (Discontinued stays Discontinued). */
    void increaseStock(Connection c, int id, int quantity);
}
