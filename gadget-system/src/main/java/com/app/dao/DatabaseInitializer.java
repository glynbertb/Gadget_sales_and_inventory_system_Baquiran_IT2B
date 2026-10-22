package com.app.dao;

import com.app.util.DataAccessException;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;

/**
 * Database initializer guard: creates every required table (only when missing)
 * so the CLI menu always starts against a complete schema, even on a brand-new
 * database file. Called once from {@code Main} before any menu is shown.
 */
public final class DatabaseInitializer {

    private static final String[] DDL = {
        "CREATE TABLE IF NOT EXISTS users (" +
            "user_id INTEGER PRIMARY KEY AUTOINCREMENT, name TEXT NOT NULL, username TEXT NOT NULL UNIQUE, " +
            "password TEXT NOT NULL, role TEXT NOT NULL, status TEXT NOT NULL DEFAULT 'Active')",
        "CREATE TABLE IF NOT EXISTS categories (" +
            "category_id INTEGER PRIMARY KEY AUTOINCREMENT, category_name TEXT NOT NULL UNIQUE, description TEXT)",
        "CREATE TABLE IF NOT EXISTS products (" +
            "gadget_id INTEGER PRIMARY KEY AUTOINCREMENT, gadget_name TEXT NOT NULL, " +
            "category_id INTEGER NOT NULL REFERENCES categories(category_id), " +
            "price REAL NOT NULL CHECK (price >= 0), stock_quantity INTEGER NOT NULL DEFAULT 0 CHECK (stock_quantity >= 0), " +
            "status TEXT NOT NULL DEFAULT 'Available')",
        "CREATE TABLE IF NOT EXISTS sales (" +
            "sale_id INTEGER PRIMARY KEY AUTOINCREMENT, user_id INTEGER NOT NULL REFERENCES users(user_id), " +
            "sale_date TEXT NOT NULL, total_amount REAL NOT NULL, payment_method TEXT NOT NULL, " +
            "sale_status TEXT NOT NULL DEFAULT 'Completed')",
        "CREATE TABLE IF NOT EXISTS sale_items (" +
            "sale_item_id INTEGER PRIMARY KEY AUTOINCREMENT, sale_id INTEGER NOT NULL REFERENCES sales(sale_id), " +
            "gadget_id INTEGER NOT NULL REFERENCES products(gadget_id), quantity INTEGER NOT NULL CHECK (quantity > 0), " +
            "unit_price REAL NOT NULL, subtotal REAL NOT NULL)",
        "CREATE TABLE IF NOT EXISTS activity_logs (" +
            "log_id INTEGER PRIMARY KEY AUTOINCREMENT, user_id INTEGER NOT NULL REFERENCES users(user_id), " +
            "action TEXT NOT NULL, description TEXT, date_time TEXT NOT NULL)"
    };

    private DatabaseInitializer() { }

    /** Creates all required tables when they are missing; existing data is untouched. */
    public static void initialize() {
        try (Connection c = DatabaseConnection.getConnection(); Statement st = c.createStatement()) {
            for (String sql : DDL) st.execute(sql);
            st.executeUpdate("UPDATE products SET stock_quantity = 0 WHERE status IN ('Out of Stock','Discontinued') AND stock_quantity <> 0");
            st.executeUpdate("UPDATE products SET status = 'Available' WHERE stock_quantity > 0 AND status <> 'Available'");
            st.executeUpdate("UPDATE products SET status = 'Out of Stock' WHERE stock_quantity = 0 AND status = 'Available'");
        } catch (SQLException e) {
            throw new DataAccessException("Could not initialise database: " + e.getMessage(), e);
        }
    }
}
