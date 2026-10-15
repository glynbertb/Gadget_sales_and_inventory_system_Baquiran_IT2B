package com.app.dao;

import com.app.model.ActivityLog;
import java.sql.Connection;
import java.util.List;

public interface ActivityLogDAO {
    int insert(Connection c, ActivityLog log);
    List<ActivityLog> findRecent(int limit);
}
