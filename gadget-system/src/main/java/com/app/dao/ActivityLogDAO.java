package com.app.dao;

import com.app.model.ActivityLog;
import java.sql.Connection;
import java.util.List;

public interface ActivityLogDAO {
    int insert(Connection c, ActivityLog log);
    List<ActivityLog> findRecent(int limit);
    /** Removes every trail row owned by the user (used when an account is permanently deleted). */
    void deleteByUser(Connection c, int userId);
}
