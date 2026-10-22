package com.app.service;

import com.app.dao.ActivityLogDAO;
import com.app.model.ActivityLog;
import com.app.model.User;
import com.app.util.FormatUtil;
import com.app.util.Tx;
import java.sql.Connection;
import java.util.List;

/** Centralised audit trail. Timestamps are generated here so every caller gets them for free. */
public class ActivityLogService {

    private final ActivityLogDAO dao;

    public ActivityLogService(ActivityLogDAO dao) { this.dao = dao; }

    /** Standalone log entry (own transaction). A logging failure never breaks the user's workflow. */
    public void log(int userId, String action, String description) {
        try {
            Tx.run(c -> log(c, userId, action, description));
        } catch (RuntimeException e) {
            System.err.println("[WARN] Could not write activity log (" + action + "): " + e.getMessage());
        }
    }

    /** Log entry inside the caller's transaction, so business change + log commit or roll back together. */
    public void log(Connection c, int userId, String action, String description) {
        ActivityLog l = new ActivityLog();
        l.setUserId(userId);
        l.setAction(action);
        l.setDescription(description);
        l.setDateTime(FormatUtil.now());
        dao.insert(c, l);
    }

    /**
     * Removes the user's own trail rows inside the caller's transaction.
     * Called only after the caller enforced its authorization rules.
     */
    public void purgeUser(Connection c, int userId) {
        dao.deleteByUser(c, userId);
    }

    public List<ActivityLog> recent(User actor, int limit) {
        AccessControl.require(actor, User.ROLE_OWNER, User.ROLE_SUPERVISOR);
        return dao.findRecent(limit);
    }
}
