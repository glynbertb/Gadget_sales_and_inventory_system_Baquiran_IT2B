package com.gadget_sale_inventory_system.dao;

import com.gadget_sale_inventory_system.util.DataAccessException;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.ResultSet;

/**
 * Database initializer guard: creates every required table (only when missing)
 * so the CLI menu always starts against a complete schema, even on a brand-new
 * database file. Called once from {@code Main} before any menu is shown.
 */
public final class DatabaseInitializer {

    private static final String[] DDL = {
        "CREATE TABLE IF NOT EXISTS users (" +
            "id INTEGER PRIMARY KEY AUTOINCREMENT, name TEXT NOT NULL, username TEXT NOT NULL UNIQUE, " +
            "password TEXT NOT NULL, role TEXT NOT NULL, status TEXT NOT NULL DEFAULT 'Active')",
        "CREATE TABLE IF NOT EXISTS categories (" +
            "category_id INTEGER PRIMARY KEY AUTOINCREMENT, category_name TEXT NOT NULL UNIQUE, description TEXT)",
        "CREATE TABLE IF NOT EXISTS products (" +
            "gadget_id INTEGER PRIMARY KEY AUTOINCREMENT, gadget_name TEXT NOT NULL, " +
            "category_id INTEGER NOT NULL REFERENCES categories(category_id), " +
            "price REAL NOT NULL CHECK (price >= 0), stock_quantity INTEGER NOT NULL DEFAULT 0 CHECK (stock_quantity >= 0), " +
            "status TEXT NOT NULL DEFAULT 'Available')",
        "CREATE TABLE IF NOT EXISTS sales (" +
            "sale_id INTEGER PRIMARY KEY AUTOINCREMENT, user_id INTEGER NOT NULL REFERENCES users(id), " +
            "sale_date TEXT NOT NULL, total_amount REAL NOT NULL, payment_method TEXT NOT NULL, " +
            "sale_status TEXT NOT NULL DEFAULT 'Completed', " +
            "cash_received REAL CHECK (cash_received IS NULL OR cash_received >= 0), " +
            "change_amount REAL CHECK (change_amount IS NULL OR change_amount >= 0))",
        "CREATE TABLE IF NOT EXISTS sale_items (" +
            "sale_item_id INTEGER PRIMARY KEY AUTOINCREMENT, sale_id INTEGER NOT NULL REFERENCES sales(sale_id), " +
            "gadget_id INTEGER NOT NULL REFERENCES products(gadget_id), quantity INTEGER NOT NULL CHECK (quantity > 0), " +
            "unit_price REAL NOT NULL, subtotal REAL NOT NULL)",
        activityLogDDL("activity_logs")
    };

    private DatabaseInitializer() { }

    /** Creates required tables and upgrades legacy audit schemas without losing history. */
    public static void initialize() {
        try (Connection c = DatabaseConnection.getConnection(); Statement st = c.createStatement()) {
            c.setAutoCommit(false);
            try {
                st.execute(DDL[0]);
                if (hasColumn(c, "users", "user_id")) {
                    // SQLite also updates existing sales/audit foreign-key references.
                    st.execute("ALTER TABLE users RENAME COLUMN user_id TO id");
                }
                for (int i = 1; i < DDL.length; i++) st.execute(DDL[i]);
                st.executeUpdate("UPDATE products SET stock_quantity = 0 WHERE status IN ('Out of Stock','Discontinued') AND stock_quantity <> 0");
                st.executeUpdate("UPDATE products SET status = 'Available' WHERE stock_quantity > 0 AND status <> 'Available'");
                st.executeUpdate("UPDATE products SET status = 'Out of Stock' WHERE stock_quantity = 0 AND status = 'Available'");
                // Old receipts have unknown tender/change; preserve those values as NULL.
                if (!hasColumn(c, "sales", "cash_received")) {
                    st.execute("ALTER TABLE sales ADD COLUMN cash_received REAL CHECK (cash_received IS NULL OR cash_received >= 0)");
                }
                if (!hasColumn(c, "sales", "change_amount")) {
                    st.execute("ALTER TABLE sales ADD COLUMN change_amount REAL CHECK (change_amount IS NULL OR change_amount >= 0)");
                }
                migrateAuditLogs(c, st);
                st.execute("CREATE INDEX IF NOT EXISTS idx_activity_logs_timestamp ON activity_logs(timestamp DESC, id DESC)");
                st.execute("CREATE INDEX IF NOT EXISTS idx_activity_logs_user_timestamp ON activity_logs(user_id, timestamp DESC, id DESC)");
                c.commit();
            } catch (SQLException e) {
                c.rollback();
                throw e;
            }
        } catch (SQLException e) {
            throw new DataAccessException("Could not initialise database: " + e.getMessage(), e);
        }
    }

    private static String activityLogDDL(String table) {
        return "CREATE TABLE IF NOT EXISTS " + table + " ("
                + "id INTEGER PRIMARY KEY AUTOINCREMENT, "
                + "user_id INTEGER NOT NULL REFERENCES users(id) ON DELETE CASCADE, "
                + "action_type TEXT NOT NULL, description TEXT, "
                + "timestamp TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP)";
    }

    private static boolean hasColumn(Connection c, String table, String column) throws SQLException {
        try (Statement inspect = c.createStatement(); ResultSet rs = inspect.executeQuery("PRAGMA table_info(" + table + ")")) {
            while (rs.next()) if (column.equals(rs.getString("name"))) return true;
        }
        return false;
    }

    /** Upgrade both original and earlier Deliverable 8 schemas in one transaction. */
    private static void migrateAuditLogs(Connection c, Statement st) throws SQLException {
        boolean cascade = false;
        try (Statement inspect = c.createStatement(); ResultSet rs = inspect.executeQuery("PRAGMA foreign_key_list(activity_logs)")) {
            while (rs.next()) {
                if ("users".equals(rs.getString("table")) && "user_id".equals(rs.getString("from"))
                        && "id".equals(rs.getString("to")) && "CASCADE".equalsIgnoreCase(rs.getString("on_delete"))) {
                    cascade = true;
                }
            }
        }
        if (hasColumn(c, "activity_logs", "id") && hasColumn(c, "activity_logs", "action_type")
                && hasColumn(c, "activity_logs", "timestamp") && cascade) return;

        // These column choices are fixed schema identifiers, never user input.
        String id = hasColumn(c, "activity_logs", "id") ? "id" : "log_id";
        String action = hasColumn(c, "activity_logs", "action_type") ? "action_type" : "action";
        String time = hasColumn(c, "activity_logs", "timestamp") ? "timestamp" : "date_time";
        try (Statement inspect = c.createStatement(); ResultSet rs = inspect.executeQuery(
                "SELECT COUNT(*) FROM activity_logs l LEFT JOIN users u ON u.id = l.user_id WHERE u.id IS NULL")) {
            if (rs.next() && rs.getInt(1) != 0) {
                throw new SQLException("Audit migration requires valid user IDs. Existing logs reference deleted users; "
                        + "restore those user records before retrying. No migration changes were saved.");
            }
        }
        st.execute(activityLogDDL("activity_logs_upgrade"));
        st.executeUpdate("INSERT INTO activity_logs_upgrade (id, user_id, action_type, description, timestamp) "
                + "SELECT " + id + ", user_id, CASE " + action + " WHEN 'USER_APPROVE' THEN 'USER_APPROVED' "
                + "WHEN 'USER_UPDATE' THEN 'USER_UPDATED' WHEN 'USER_ACTIVATE' THEN 'USER_UPDATED' "
                + "WHEN 'USER_DEACTIVATE' THEN 'USER_UPDATED' ELSE " + action + " END, description, " + time + " FROM activity_logs");
        st.executeUpdate("UPDATE sqlite_sequence SET seq = MAX(seq, "
                + "COALESCE((SELECT seq FROM sqlite_sequence WHERE name = 'activity_logs'), 0)) "
                + "WHERE name = 'activity_logs_upgrade'");
        st.execute("DROP TABLE activity_logs");
        st.execute("ALTER TABLE activity_logs_upgrade RENAME TO activity_logs");
    }
}
