package com.gadget_sale_inventory_system.dao;

import com.gadget_sale_inventory_system.model.ActivityLog;
import com.gadget_sale_inventory_system.util.FormatUtil;
import com.gadget_sale_inventory_system.util.Transaction;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;

public class ActivityLogDao {
    private static final String SELECT = "SELECT l.id, l.user_id, u.name AS user_name, "
            + "l.action_type, l.description, l.timestamp FROM activity_logs l "
            + "LEFT JOIN users u ON u.id = l.user_id ";
    private static final String ORDER = " ORDER BY l.timestamp DESC, l.id DESC";

    public void logAction(int userId, String actionType, String description) {
        ActivityLog log = new ActivityLog();
        log.setUserId(userId);
        log.setActionType(actionType);
        log.setDescription(description);
        log.setTimestamp(FormatUtil.now());
        Transaction.run(c -> insert(c, log));
    }

    public int insert(Connection c, ActivityLog log) {
        return JdbcHelper.insert(c,
                "INSERT INTO activity_logs (user_id, action_type, description, timestamp) VALUES (?, ?, ?, ?)", ps -> {
                    ps.setInt(1, log.getUserId());
                    ps.setString(2, log.getActionType());
                    ps.setString(3, log.getDescription());
                    ps.setString(4, log.getTimestamp());
                });
    }

    public List<ActivityLog> getAllLogs() {
        return JdbcHelper.query(SELECT + ORDER, null, ActivityLogDao::map);
    }

    public List<ActivityLog> getLogsByUserId(int userId) {
        return JdbcHelper.query(SELECT + "WHERE l.user_id = ?" + ORDER,
                ps -> ps.setInt(1, userId), ActivityLogDao::map);
    }

    public List<ActivityLog> findRecent(int limit) {
        return JdbcHelper.query(SELECT + ORDER + " LIMIT ?",
                ps -> ps.setInt(1, limit), ActivityLogDao::map);
    }

    public int clearLogs(Connection c) {
        return JdbcHelper.update(c, "DELETE FROM activity_logs", null);
    }

    private static ActivityLog map(ResultSet rs) throws SQLException {
        ActivityLog log = new ActivityLog();
        log.setId(rs.getInt("id"));
        log.setUserId(rs.getInt("user_id"));
        log.setUserName(rs.getString("user_name"));
        log.setActionType(rs.getString("action_type"));
        log.setDescription(rs.getString("description"));
        log.setTimestamp(rs.getString("timestamp"));
        return log;
    }
}
