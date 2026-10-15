package com.app.dao;

import com.app.model.ActivityLog;
import java.sql.Connection;
import java.util.List;

public class ActivityLogDAOImpl implements ActivityLogDAO {

    @Override public int insert(Connection c, ActivityLog l) {
        return JdbcHelper.insert(c, "INSERT INTO activity_logs (user_id, action, description, date_time) VALUES (?, ?, ?, ?)", ps -> {
            ps.setInt(1, l.getUserId());
            ps.setString(2, l.getAction());
            ps.setString(3, l.getDescription());
            ps.setString(4, l.getDateTime());
        });
    }

    @Override public List<ActivityLog> findRecent(int limit) {
        return JdbcHelper.query(
                "SELECT l.log_id, l.user_id, u.name AS user_name, l.action, l.description, l.date_time " +
                "FROM activity_logs l LEFT JOIN users u ON u.user_id = l.user_id ORDER BY l.log_id DESC LIMIT ?",
                ps -> ps.setInt(1, limit),
                rs -> {
                    ActivityLog a = new ActivityLog();
                    a.setLogId(rs.getInt("log_id"));
                    a.setUserId(rs.getInt("user_id"));
                    a.setUserName(rs.getString("user_name"));
                    a.setAction(rs.getString("action"));
                    a.setDescription(rs.getString("description"));
                    a.setDateTime(rs.getString("date_time"));
                    return a;
                });
    }
}
