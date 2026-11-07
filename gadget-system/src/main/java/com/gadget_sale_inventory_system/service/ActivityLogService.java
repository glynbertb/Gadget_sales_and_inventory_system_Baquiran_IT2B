package com.gadget_sale_inventory_system.service;

import com.gadget_sale_inventory_system.dao.ActivityLogDao;
import com.gadget_sale_inventory_system.model.ActivityLog;
import com.gadget_sale_inventory_system.model.User;
import com.gadget_sale_inventory_system.util.FormatUtil;
import com.gadget_sale_inventory_system.util.Transaction;
import java.sql.Connection;
import java.util.List;

/** Centralised audit trail. Timestamps are generated here so every caller gets them for free. */
public class ActivityLogService {

    private final ActivityLogDao dao;

    public ActivityLogService(ActivityLogDao dao) { this.dao = dao; }

    /** Standalone writes fail visibly if the audit entry cannot be saved. */
    public void logAction(int userId, String action, String description) {
        dao.logAction(userId, action, description);
    }

    /** Log entry inside the caller's transaction, so business change + log commit or roll back together. */
    public void logAction(Connection c, int userId, String action, String description) {
        ActivityLog l = new ActivityLog();
        l.setUserId(userId);
        l.setActionType(action);
        l.setDescription(description);
        l.setTimestamp(FormatUtil.now());
        dao.insert(c, l);
    }

    public List<ActivityLog> getAllLogs(User actor) {
        AccessControl.require(actor, User.ROLE_OWNER, User.ROLE_SUPERVISOR);
        return dao.getAllLogs();
    }

    public List<ActivityLog> getLogsByUserId(User actor, int userId) {
        AccessControl.require(actor, User.ROLE_OWNER, User.ROLE_SUPERVISOR);
        if (userId <= 0) throw new ServiceException("User ID must be positive.");
        return dao.getLogsByUserId(userId);
    }

    /** Clearing and its audit receipt are atomic. IDs are never reset. */
    public int clearLogs(User actor, String confirmation) {
        AccessControl.require(actor, User.ROLE_OWNER);
        if (!"CLEAR".equals(confirmation)) throw new ServiceException("Confirmation must be CLEAR. No logs were cleared.");
        return Transaction.execute(c -> {
            int removed = dao.clearLogs(c);
            logAction(c, actor.getUserId(), "LOGS_CLEARED",
                    "Admin " + actor.getUserId() + " cleared " + removed + " historical audit log(s).");
            return removed;
        });
    }

    public List<ActivityLog> recent(User actor, int limit) {
        AccessControl.require(actor, User.ROLE_OWNER, User.ROLE_SUPERVISOR);
        if (limit < 1 || limit > 500) throw new ServiceException("Limit must be between 1 and 500.");
        return dao.findRecent(limit);
    }
}
