# Deliverable 8 — Activity Logging and Audit Trail

The Owner is the system administrator. From the Owner menu, choose **6 — View System Audit Logs**.

| Option | Behavior |
| --- | --- |
| View All System Logs | All records, newest timestamp first; ten records per page. |
| Filter Logs by User ID | Matches the actor's numeric ID. Enter 0 to cancel. |
| Clear Historical Logs | Owner only. Type `CLEAR`, then confirm `y`. Cancellation preserves history. |
| Back | Returns to the role menu. |

The Supervisor can view and filter logs, but cannot clear them. Cashiers and Inventory Staff cannot access audit tools.

Tables use the required widths: `%-5s | %-8s | %-18s | %-35s | %-20s` for ID, User ID, Action, Description, and Timestamp. Descriptions wrap without losing evidence; N/P navigate pages and 0 returns to the submenu.

## Implementation

- `dao/DatabaseInitializer.java` creates `activity_logs` with `id`, `user_id`, `action_type`, `description`, and `timestamp`. Startup upgrades previous schemas atomically, retaining IDs and valid audit records, and aligns the parent key with `users(id)`.
- `dao/ActivityLogDao.java` contains parameterized audit persistence and exposes `logAction(int, String, String)`, `getAllLogs()`, and `getLogsByUserId(int)`.
- `service/ActivityLogService.java` enforces access and keeps business updates and audit writes in the same transaction.
- `ui/AdminMenu.java` provides audit navigation; `ViewHelper` renders aligned tables.
- Account approvals use `USER_APPROVED`; role/status changes use `USER_UPDATED`. Sales, voids, returns, exchanges, inventory changes, account deletion, password resets, and exports also log their actions.
- The audit foreign key is `user_id REFERENCES users(id) ON DELETE CASCADE`, as required by the checklist. Deleting an eligible account removes its audit entries; the deletion event is recorded under the Owner's ID. Accounts with recorded sales cannot be deleted.
- Clearing leaves one `LOGS_CLEARED` receipt with actor and removed-record count. If the receipt cannot be written, deletion rolls back. IDs are never reset.
- Timestamp and user/timestamp indexes support sorting and filtering. Timestamp ties use descending audit ID for consistent results.

## Cash checkout

Cash checkout prompts for cash received, rejects insufficient amounts, and displays the change before confirmation and on the receipt. Received cash and change are saved in `sales.cash_received` and `sales.change_amount`. Card and E-Wallet payments skip the cash prompt. Existing sales retain NULL payment details rather than guessed values. Sale details label these amounts as the original checkout amounts after returns or exchanges.

## Verification

With JDK 17+ and the bundled runnable JAR available, run:

```bash
bash verify-audit.sh
```

If Java is outside PATH, set `JAVA_HOME` to its JDK directory. The script compiles current source and runs checks against separate fresh, legacy, previous-schema, orphaned-history, and cash-test databases in a new temporary directory. It never opens the application's database.

Checks cover registration, approval, updates, checkout and stock, authorization, quoted input, failed-log rollback, cascade deletion, ordering, filtering, more than 500 logs, confirmation, clear rollback, legacy migration, cash receipt persistence, centavo rounding, exact payment, insufficient cash, and non-cash payments.

## Classroom submission

The completed verification form uses four mandatory categories: authentication, account management, gadget sale, and sale void. Include the terminal log screenshot and matching SQLite table screenshot, and set the form to **Anyone with the link — Viewer**. Submit the public repository URL, completed form URL, and screenshots through Google Classroom.
