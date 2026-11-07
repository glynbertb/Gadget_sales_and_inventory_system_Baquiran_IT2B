package com.gadget_sale_inventory_system.ui;

import com.gadget_sale_inventory_system.model.ActivityLog;
import com.gadget_sale_inventory_system.model.User;
import com.gadget_sale_inventory_system.service.AccessControl;
import com.gadget_sale_inventory_system.service.ActivityLogService;
import com.gadget_sale_inventory_system.util.InputUtil;
import java.util.List;

/** Audit tools for the Owner (administrator) and read-only Supervisor access. */
public final class AdminMenu {
    private static final int PAGE_SIZE = 10;
    private final ActivityLogService logs;

    public AdminMenu(ActivityLogService logs) { this.logs = logs; }

    public void run(User actor) {
        AccessControl.require(actor, User.ROLE_OWNER, User.ROLE_SUPERVISOR);
        boolean owner = User.ROLE_OWNER.equals(actor.getRole());
        while (true) {
            ViewHelper.header("SYSTEM AUDIT LOGS");
            System.out.println("  [1] View All System Logs");
            System.out.println("  [2] Filter Logs by User ID");
            if (owner) System.out.println("  [3] Clear Historical Logs");
            System.out.println("  [0] Back\n");
            int choice = InputUtil.readInt("Select option: ", 0, owner ? 3 : 2);
            if (choice == 0) return;
            ViewHelper.safely(() -> {
                switch (choice) {
                    case 1 -> browse(logs.getAllLogs(actor), "All system logs");
                    case 2 -> {
                        int userId = InputUtil.readInt("User ID (0 = cancel): ", 0, Integer.MAX_VALUE);
                        if (userId != 0) browse(logs.getLogsByUserId(actor, userId), "Logs for User ID " + userId);
                    }
                    case 3 -> clear(actor);
                    default -> { }
                }
            });
        }
    }

    private void browse(List<ActivityLog> entries, String title) {
        int page = 0;
        int pages = Math.max(1, (entries.size() + PAGE_SIZE - 1) / PAGE_SIZE);
        while (true) {
            System.out.println("\n  " + title + " | Newest first");
            int start = page * PAGE_SIZE;
            ViewHelper.printLogs(entries.subList(start, Math.min(start + PAGE_SIZE, entries.size())));
            System.out.printf("  Page %d of %d | %d log(s)%n", page + 1, pages, entries.size());
            System.out.println("  [N] Next   [P] Previous   [0] Back");
            String choice = InputUtil.readOptional("Select: ");
            if (choice.equals("0")) return;
            if (choice.equalsIgnoreCase("n") && page + 1 < pages) page++;
            else if (choice.equalsIgnoreCase("p") && page > 0) page--;
            else System.out.println("  Choose an available page or 0 to return.");
        }
    }

    private void clear(User actor) {
        System.out.println("\n  This permanently removes historical audit logs.");
        System.out.println("  A new LOGS_CLEARED entry will record this operation.");
        String confirmation = InputUtil.readOptional("Type CLEAR to continue (ENTER = cancel): ");
        if (!"CLEAR".equals(confirmation)) {
            System.out.println("  Cancelled. No logs were cleared.");
            return;
        }
        if (!InputUtil.confirm("Clear historical logs now?")) {
            System.out.println("  Cancelled. No logs were cleared.");
            return;
        }
        int removed = logs.clearLogs(actor, confirmation);
        ViewHelper.success("Cleared " + removed + " historical log(s). Audit receipt saved.");
    }
}
