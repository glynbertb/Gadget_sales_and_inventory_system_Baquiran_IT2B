package com.app.ui;

import com.app.model.ActivityLog;
import com.app.model.Category;
import com.app.model.DailySales;
import com.app.model.Product;
import com.app.model.Sale;
import com.app.model.SaleItem;
import com.app.model.SummaryReport;
import com.app.model.TopSeller;
import com.app.model.User;
import com.app.service.ActivityLogService;
import com.app.service.ProductService;
import com.app.service.ReportService;
import com.app.service.SaleService;
import com.app.service.ServiceException;
import com.app.util.ConsoleStyle;
import com.app.util.DataAccessException;
import com.app.util.FormatUtil;
import com.app.util.InputUtil;
import java.nio.file.Path;
import java.util.List;

/** Shared console rendering + reusable flows used by several role menus. */
final class ViewHelper {

    // Box drawing characters below are the outer document chrome (menu headers, report cards).
    // Data tables are plain ASCII grids (see tables NOTE).
    private static final String DL = "╔", DR = "╗", DLB = "╚", DRB = "╝";
    private static final String DH = "═";
    /** Shared outer-box border used to wrap every table line (same package: CashierMenu cart). */
    static final String DV = "║";
    private static final String D_T_RIGHT = "╠", D_T_LEFT = "╣";

    private ViewHelper() { }

    // ------------------------------------------------------------ chrome / errors

    static void header(String title) {
        int w = 64;
        String clean = ConsoleStyle.strip(title).trim();
        // Top border double
        System.out.println();
        System.out.println(ConsoleStyle.color(DL + DH.repeat(w) + DR, ConsoleStyle.CYAN));
        System.out.println(ConsoleStyle.color(DV, ConsoleStyle.CYAN) + ConsoleStyle.boldColor(center(clean, w), ConsoleStyle.BRIGHT_CYAN) + ConsoleStyle.color(DV, ConsoleStyle.CYAN));
        System.out.println(ConsoleStyle.color(DLB + DH.repeat(w) + DRB, ConsoleStyle.CYAN));
    }

    static void section(String title) {
        System.out.println();
        System.out.println(ConsoleStyle.boldColor("  " + title, ConsoleStyle.CYAN));
        System.out.println(ConsoleStyle.color("  " + "─".repeat(Math.min(title.length()+2, 50)), ConsoleStyle.MUTED));
    }

    static void success(String msg) {
        System.out.println(ConsoleStyle.color("\n  ✓  " + msg, ConsoleStyle.GREEN));
    }

    static void warn(String msg) {
        System.out.println(ConsoleStyle.color("\n  ⚠  " + msg, ConsoleStyle.YELLOW));
    }

    static void error(String msg) {
        System.out.println(ConsoleStyle.color("\n  ✗  " + msg, ConsoleStyle.RED));
    }

    static void info(String msg) {
        System.out.println(ConsoleStyle.color("  " + msg, ConsoleStyle.MUTED));
    }

    /** Runs a UI action; business and database errors are shown instead of crashing the menu loop. */
    static void safely(Runnable action) {
        try {
            action.run();
        } catch (ServiceException e) {
            error(e.getMessage());
        } catch (DataAccessException e) {
            error("Database error: " + e.getMessage());
        }
    }

    static String chooseRole() {
        System.out.println(ConsoleStyle.color("  ┌─ Select Role ─────────────────────", ConsoleStyle.MUTED));
        for (int i = 0; i < User.ROLES.length; i++) {
            String num = ConsoleStyle.color(String.format("%d", i+1), ConsoleStyle.CYAN);
            System.out.println(ConsoleStyle.color("  │ ", ConsoleStyle.MUTED) + " [" + num + ConsoleStyle.color("] ", ConsoleStyle.MUTED) + User.ROLES[i]);
        }
        System.out.println(ConsoleStyle.color("  └─────────────────────────────────", ConsoleStyle.MUTED));
        return User.ROLES[InputUtil.readInt("  Select role: ", 1, User.ROLES.length) - 1];
    }

    static int chooseCategoryId(List<Category> categories) {
        if (categories.isEmpty()) throw new ServiceException("No categories exist yet. Ask the Owner to add one.");
        printCategories(categories);
        while (true) {
            int id = InputUtil.readInt("  Category ID: ", 1, Integer.MAX_VALUE);
            if (categories.stream().anyMatch(c -> c.getCategoryId() == id)) return id;
            System.out.println(ConsoleStyle.color("  Unknown category ID.", ConsoleStyle.YELLOW));
        }
    }

    private static String padRight(String s, int w) {
        if (s == null) s = "";
        String stripped = ConsoleStyle.strip(s);
        int len = stripped.length();
        if (len >= w) return s;
        return s + " ".repeat(w - len);
    }

    private static String padLeft(String s, int w) {
        if (s == null) s = "";
        String stripped = ConsoleStyle.strip(s);
        int len = stripped.length();
        if (len >= w) return s;
        return " ".repeat(w - len) + s;
    }

    private static String center(String s, int w) {
        if (s == null) s = "";
        int len = ConsoleStyle.strip(s).length();
        if (len >= w) return s;
        int pad = (w - len) / 2;
        int padR = w - len - pad;
        return " ".repeat(pad) + s + " ".repeat(padR);
    }

    static void printUsers(List<User> users) {
        // Plain grid: to change a column width, edit its %-Ns below AND the matching dashes in sepLine.
        String sepLine = "+------+------------------------+------------------+------------------+------------+";
        System.out.println();
        System.out.println(sepLine);
        System.out.println(String.format("| %-4s | %-22s | %-16s | %-16s | %-10s |", "ID", "Name", "Username", "Role", "Status"));
        System.out.println(sepLine);
        if (users.isEmpty()) {
            System.out.println(String.format("| %-80s |", "(no users found)"));
        } else {
            for (User u : users) {
                System.out.println(String.format("| %-4s | %-22s | %-16s | %-16s | %-10s |",
                        u.getUserId(),
                        FormatUtil.trunc(u.getName(), 22),
                        FormatUtil.trunc(u.getUsername(), 16),
                        u.getRole() == null ? "-" : FormatUtil.trunc(u.getRole(), 16),
                        u.getStatus() == null ? "-" : u.getStatus()));
            }
        }
        System.out.println(sepLine);
        System.out.println("  " + users.size() + " user(s)");
    }

    /**
     * Structured user table: aligned headers, borders and one row per account.
     * Columns are ID / Username / Role / Status.
     */
    static void printUserTable(List<User> users) {
        String sepLine = "+-------+-----------------+--------------+------------+";
        System.out.println();
        System.out.println(sepLine);
        System.out.println(String.format("| %-5s | %-15s | %-12s | %-10s |", "ID", "Username", "Role", "Status"));
        System.out.println(sepLine);
        if (users.isEmpty()) {
            System.out.println(String.format("| %-45s |", "(no users found)"));
        } else {
            for (User u : users) {
                System.out.println(String.format("| %-5s | %-15s | %-12s | %-10s |",
                        u.getUserId(),
                        FormatUtil.trunc(u.getUsername(), 15),
                        u.getRole() == null ? "-" : FormatUtil.trunc(u.getRole(), 12),
                        u.getStatus() == null ? "-" : u.getStatus()));
            }
        }
        System.out.println(sepLine);
        System.out.println("  " + users.size() + " user(s)");
    }

    static void printCategories(List<Category> list) {
        // Plain grid: to change a column width, edit its %-Ns below AND the matching dashes in sepLine.
        String sepLine = "+------+------------------------+----------------------------------------+";
        System.out.println();
        System.out.println(sepLine);
        System.out.println(String.format("| %-4s | %-22s | %-38s |", "ID", "Category", "Description"));
        System.out.println(sepLine);
        if (list.isEmpty()) {
            System.out.println(String.format("| %-70s |", "(no categories)"));
        } else {
            for (Category c : list) {
                System.out.println(String.format("| %-4s | %-22s | %-38s |",
                        c.getCategoryId(),
                        FormatUtil.trunc(c.getCategoryName(), 22),
                        FormatUtil.trunc(c.getDescription() == null ? "" : c.getDescription(), 38)));
            }
        }
        System.out.println(sepLine);
        System.out.println("  " + list.size() + " categor" + (list.size()==1?"y":"ies"));
    }

    static void printProducts(List<Product> list) {
        // Plain grid: to change a column width, edit its %-Ns below AND the matching dashes in sepLine.
        String sepLine = "+------+----------------------------+----------------+-------------+--------+--------------+";
        System.out.println();
        System.out.println(sepLine);
        System.out.println(String.format("| %-4s | %-26s | %-14s | %11s | %6s | %-12s |", "ID", "Gadget", "Category", "Price", "Stock", "Status"));
        System.out.println(sepLine);
        if (list.isEmpty()) {
            System.out.println(String.format("| %-88s |", "(no records found)"));
        } else {
            for (Product p : list) {
                System.out.println(String.format("| %-4s | %-26s | %-14s | %11s | %6s | %-12s |",
                        p.getGadgetId(),
                        FormatUtil.trunc(p.getGadgetName(), 26),
                        FormatUtil.trunc(p.getCategoryName() == null ? "-" : p.getCategoryName(), 14),
                        FormatUtil.money(p.getPrice()),
                        p.getStockQuantity(),
                        p.getStatus() == null ? "-" : p.getStatus()));
            }
        }
        System.out.println(sepLine);
        System.out.println("  " + list.size() + " gadget(s)");
    }

    static void printSales(List<Sale> list) {
        // Plain grid: to change a column width, edit its %-Ns below AND the matching dashes in sepLine.
        String sepLine = "+---------+---------------------+------------------+------------+--------------+------------+";
        System.out.println();
        System.out.println(sepLine);
        System.out.println(String.format("| %-7s | %-19s | %-16s | %-10s | %12s | %-10s |", "Sale ID", "Date", "Cashier", "Payment", "Total", "Status"));
        System.out.println(sepLine);
        if (list.isEmpty()) {
            System.out.println(String.format("| %-89s |", "(no transactions found)"));
        } else {
            for (Sale s : list) {
                System.out.println(String.format("| %-7s | %-19s | %-16s | %-10s | %12s | %-10s |",
                        "#" + String.format("%06d", s.getSaleId()),
                        FormatUtil.trunc(s.getSaleDate(), 19),
                        FormatUtil.trunc(s.getCashierName() == null ? "-" : s.getCashierName(), 16),
                        FormatUtil.trunc(s.getPaymentMethod() == null ? "-" : s.getPaymentMethod(), 10),
                        FormatUtil.money(s.getTotalAmount()),
                        s.getSaleStatus() == null ? "-" : s.getSaleStatus()));
            }
        }
        System.out.println(sepLine);
        System.out.println("  " + list.size() + " transaction(s)");
    }

    static void printSaleDetail(Sale s) {
        String sepLine = "+------------------------+-------+------------+--------------+";

        System.out.println("+------------------------------------------------------------+");
        // Integrated Sale ID Header
        System.out.println(String.format("| %-58s |", "SALE #" + String.format("%06d", s.getSaleId())));
        System.out.println("+------------------------------------------------------------+");

        // Integrated Metadata Rows (Split into balanced grid cells)
        System.out.println(String.format("| %-30s | %-25s |",
                "Date    : " + s.getSaleDate(),
                "Cashier : " + (s.getCashierName() == null ? "-" : s.getCashierName())));
        System.out.println(String.format("| %-30s | %-25s |",
                "Payment : " + s.getPaymentMethod(),
                "Status  : " + (s.getSaleStatus() == null ? "-" : s.getSaleStatus())));
        System.out.println("+------------------------------------------------------------+");

        // Column Headers
        System.out.println(String.format("| %-22s | %5s | %10s | %12s |", "Gadget", "Qty", "Unit Price", "Subtotal"));
        System.out.println(sepLine);

        // Line Items
        for (SaleItem it : s.getItems()) {
            System.out.println(String.format("| %-22s | %5s | %10s | %12s |",
                    FormatUtil.trunc(it.getGadgetName(), 22),
                    it.getQuantity(),
                    FormatUtil.money(it.getUnitPrice()),
                    FormatUtil.money(it.getSubtotal())));
        }
        System.out.println(sepLine);

        // Total Row
        System.out.println(String.format("| %-23s| %5s | %10s | %12s |", "TOTAL", "", "", FormatUtil.money(s.getTotalAmount())));
        System.out.println(sepLine);
    }
    static void printLogs(List<ActivityLog> logs) {
        // Plain grid: to change a column width, edit its %-Ns below AND the matching dashes in sepLine.
        String sepLine = "+-------+---------------------+----------------+------------------+----------------------------------------+";
        System.out.println();
        System.out.println(sepLine);
        System.out.println(String.format("| %-5s | %-19s | %-14s | %-16s | %-38s |", "ID", "Date/Time", "User", "Action", "Description"));
        System.out.println(sepLine);
        if (logs.isEmpty()) {
            System.out.println(String.format("| %-104s |", "(no activity recorded)"));
        } else {
            for (ActivityLog l : logs) {
                String user = l.getUserName() == null ? "#" + l.getUserId() : l.getUserName();
                System.out.println(String.format("| %-5s | %-19s | %-14s | %-16s | %-38s |",
                        l.getLogId(),
                        FormatUtil.trunc(l.getDateTime(), 19),
                        FormatUtil.trunc(user, 14),
                        FormatUtil.trunc(l.getAction() == null ? "-" : l.getAction(), 16),
                        FormatUtil.trunc(l.getDescription() == null ? "" : l.getDescription(), 38)));
            }
        }
        System.out.println(sepLine);
        System.out.println("  " + logs.size() + " log(s)");
    }

    static void printSummary(SummaryReport r) {
        int w = 64;
        System.out.println();
        System.out.println(ConsoleStyle.color(DL + DH.repeat(w) + DR, ConsoleStyle.CYAN));
        System.out.println(ConsoleStyle.color(DV, ConsoleStyle.CYAN) + ConsoleStyle.boldColor(center("SALES & INVENTORY SUMMARY REPORT", w), ConsoleStyle.BRIGHT_CYAN) + ConsoleStyle.color(DV, ConsoleStyle.CYAN));
        System.out.println(ConsoleStyle.color(DV, ConsoleStyle.CYAN) + ConsoleStyle.dim(center("Generated: " + r.generatedAt(), w)) + ConsoleStyle.color(DV, ConsoleStyle.CYAN));
        System.out.println(ConsoleStyle.color(D_T_RIGHT + DH.repeat(w) + D_T_LEFT, ConsoleStyle.CYAN));

        // KPI lines, plain text inside the title box.
        System.out.println(ConsoleStyle.color(DV, ConsoleStyle.CYAN) + padRight("  Total Sales   :  " + FormatUtil.money(r.totalSalesAmount()), w) + ConsoleStyle.color(DV, ConsoleStyle.CYAN));
        System.out.println(ConsoleStyle.color(DV, ConsoleStyle.CYAN) + padRight("  Transactions  :  " + r.totalTransactions() + " completed  |  " + r.voidedTransactions() + " voided", w) + ConsoleStyle.color(DV, ConsoleStyle.CYAN));
        System.out.println(ConsoleStyle.color(DLB + DH.repeat(w) + DRB, ConsoleStyle.CYAN));

        // Top sellers: plain standalone grid. Widths live in the %-Ns and sepLine below.
        String topSep = "+------+------------------------------+------------+----------------+";
        System.out.println("\nTop-Selling Gadgets:");
        System.out.println(topSep);
        System.out.println(String.format("| %-4s | %-28s | %-10s | %-14s |", "Rank", "Gadget", "Units", "Revenue"));
        System.out.println(topSep);
        if (r.topSellers().isEmpty()) {
            System.out.println(String.format("| %-65s |", "(no sales yet)"));
        } else {
            int rank = 1;
            for (TopSeller t : r.topSellers()) {
                System.out.println(String.format("| %-4s | %-28s | %-10s | %-14s |",
                        rank++,
                        FormatUtil.trunc(t.name(), 28),
                        FormatUtil.trunc(t.quantity() + " unit(s)", 10),
                        FormatUtil.money(t.revenue())));
            }
        }
        System.out.println(topSep);

        // Sales by date: plain standalone grid.
        String dateSep = "+--------------+----------------------+----------------------+";
        System.out.println("\nSales by Date:");
        System.out.println(dateSep);
        System.out.println(String.format("| %-12s | %-20s | %-20s |", "Date", "Transactions", "Total Sales"));
        System.out.println(dateSep);
        if (r.salesByDate().isEmpty()) {
            System.out.println(String.format("| %-58s |", "(no sales yet)"));
        } else {
            for (DailySales d : r.salesByDate()) {
                System.out.println(String.format("| %-12s | %-20s | %-20s |",
                        d.date(),
                        FormatUtil.trunc(d.transactions() + " transaction(s)", 20),
                        FormatUtil.money(d.total())));
            }
        }
        System.out.println(dateSep);

        System.out.println("\nCurrent Stock Levels:");
        printProducts(r.stockLevels());
        System.out.println("\nLow-Stock Gadgets (at most " + r.lowStockThreshold() + " left):");
        if (r.lowStock().isEmpty()) {
            System.out.println("  All stock levels are healthy.");
        } else {
            printProducts(r.lowStock());
        }
    }

    // ------------------------------------------------------------------ receipt

    static void printReceipt(Sale s) {
        // Plain receipt: one 52-wide box. Title and meta lines are padded to 48 chars inside "| ... |".
        String edge = "+--------------------------------------------------+";
        String gridSep = "+------------------+-----+------------+------------+";
        System.out.println();
        System.out.println(edge);
        System.out.println("| " + center("BERTZKIE GADGET STORE", 48) + " |");
        System.out.println("| " + center("Gadget Sales & Inventory System", 48) + " |");
        System.out.println("| " + center("OFFICIAL RECEIPT", 48) + " |");
        System.out.println(edge);
        System.out.println("| " + String.format("%-48s", "Receipt No : #" + String.format("%06d", s.getSaleId())) + " |");
        System.out.println("| " + String.format("%-48s", "Date       : " + s.getSaleDate()) + " |");
        System.out.println("| " + String.format("%-48s", "Cashier    : " + (s.getCashierName() == null ? "-" : s.getCashierName())) + " |");
        System.out.println("| " + String.format("%-48s", "Payment    : " + s.getPaymentMethod()) + " |");
        System.out.println(edge);
        System.out.println(String.format("| %-16s | %-3s | %-10s | %-10s |", "Item", "Qty", "Price", "Subtotal"));
        System.out.println(gridSep);
        for (SaleItem i : s.getItems()) {
            System.out.println(String.format("| %-16s | %-3s | %-10s | %-10s |",
                    FormatUtil.trunc(i.getGadgetName(), 16),
                    i.getQuantity(),
                    FormatUtil.money(i.getUnitPrice()),
                    FormatUtil.money(i.getSubtotal())));
        }
        System.out.println(gridSep);
        // TOTAL keeps the same columns: label under Price, amount under Subtotal.
        System.out.println(String.format("| %-16s | %-3s | %-10s | %-10s |", "", "", "TOTAL", FormatUtil.money(s.getTotalAmount())));
        System.out.println(gridSep);
        System.out.println("| " + center("Thank you for shopping with us!", 48) + " |");
        System.out.println("| " + center("Please come again!", 48) + " |");
        System.out.println(edge);
        System.out.println();
    }

    // -------------------------------------------------------------- shared flows

    static void viewSalesFlow(SaleService sales, User user) {
        List<Sale> list = sales.listSales(user);
        printSales(list);
        if (list.isEmpty()) return;
        int id = InputUtil.readInt("\n  Enter Sale ID for details (0 to go back): ", 0, Integer.MAX_VALUE);
        if (id != 0) printSaleDetail(sales.getSaleDetails(user, id));
    }

    static void voidSaleFlow(SaleService sales, User user) {
        List<Sale> list = sales.listSales(user);
        printSales(list);
        if (list.isEmpty()) {
            System.out.println(ConsoleStyle.muted("  No sales to void."));
            return;
        }
        int id = InputUtil.readInt("\n  Sale ID to void (0 to cancel): ", 0, Integer.MAX_VALUE);
        if (id == 0) return;
        Sale s = sales.getSaleDetails(user, id);
        printSaleDetail(s);
        if (!Sale.STATUS_COMPLETED.equals(s.getSaleStatus())) {
            warn("Only Completed sales can be voided (this one is " + s.getSaleStatus() + ").");
            return;
        }
        String reason = InputUtil.readRequired("  Void reason (required): ");
        if (!InputUtil.confirm("  Void sale #" + String.format("%06d", id) + " and return its items to stock?")) {
            System.out.println(ConsoleStyle.muted("  Cancelled."));
            return;
        }
        sales.voidSale(user, id, reason);
        success("Sale #" + String.format("%06d", id) + " has been voided and stock was restored.");
    }

    static void returnFlow(SaleService sales, User user) {
        List<Sale> list = sales.listSales(user);
        printSales(list);
        if (list.isEmpty()) {
            System.out.println(ConsoleStyle.muted("  No sales to return from."));
            return;
        }
        int id = InputUtil.readInt("\n  Sale ID to return from (0 to cancel): ", 0, Integer.MAX_VALUE);
        if (id == 0) return;
        Sale s = sales.getSaleDetails(user, id);
        printSaleDetail(s);
        if (!Sale.STATUS_COMPLETED.equals(s.getSaleStatus())) {
            warn("Only Completed sales accept returns (this one is " + s.getSaleStatus() + ").");
            return;
        }
        int gadgetId = InputUtil.readInt("  Gadget ID to return (0 to cancel): ", 0, Integer.MAX_VALUE);
        if (gadgetId == 0) return;
        int qty = InputUtil.readInt("  Quantity to return: ", 1, Integer.MAX_VALUE);
        String reason = InputUtil.readRequired("  Return reason (required): ");
        if (!InputUtil.confirm("  Return " + qty + " unit(s) to stock?")) {
            System.out.println(ConsoleStyle.muted("  Cancelled."));
            return;
        }
        sales.returnItem(user, id, gadgetId, qty, reason);
        success("Return recorded and stock was restored.");
    }

    static void exchangeFlow(SaleService sales, ProductService products, User user) {
        List<Sale> list = sales.listSales(user);
        printSales(list);
        if (list.isEmpty()) {
            System.out.println(ConsoleStyle.muted("  No sales to exchange from."));
            return;
        }
        int id = InputUtil.readInt("\n  Sale ID to exchange from (0 to cancel): ", 0, Integer.MAX_VALUE);
        if (id == 0) return;
        Sale s = sales.getSaleDetails(user, id);
        printSaleDetail(s);
        if (!Sale.STATUS_COMPLETED.equals(s.getSaleStatus())) {
            warn("Only Completed sales accept exchanges (this one is " + s.getSaleStatus() + ").");
            return;
        }
        int oldId = InputUtil.readInt("  Gadget ID to take back (0 to cancel): ", 0, Integer.MAX_VALUE);
        if (oldId == 0) return;
        int oldQty = InputUtil.readInt("  Quantity to take back: ", 1, Integer.MAX_VALUE);
        printProducts(products.listAvailable());
        int newId = InputUtil.readInt("  Replacement Gadget ID (0 to cancel): ", 0, Integer.MAX_VALUE);
        if (newId == 0) return;
        int newQty = InputUtil.readInt("  Replacement quantity: ", 1, Integer.MAX_VALUE);
        String reason = InputUtil.readRequired("  Exchange reason (required): ");
        if (!InputUtil.confirm("  Confirm exchange (price difference settles at the register)?")) {
            System.out.println(ConsoleStyle.muted("  Cancelled."));
            return;
        }
        sales.exchangeItem(user, id, oldId, oldQty, newId, newQty, reason);
        success("Exchange recorded: old units restocked, replacements added to the sale.");
    }

    static void registerAuditFlow(ReportService reports, User user) {
        double counted = readNonNegativeCash("  Counted cash in drawer (₱): ");
        ReportService.RegisterAudit r = reports.registerAudit(user, counted);
        section("Register audit  •  " + r.date());
        System.out.println("  Expected (today's Cash sales): " + ConsoleStyle.bold(FormatUtil.money(r.expectedCash())));
        System.out.println("  Counted in drawer:              " + ConsoleStyle.bold(FormatUtil.money(r.countedCash())));
        if (r.variance() == 0) success("Register balances. No discrepancy.");
        else if (r.variance() > 0) warn("Over by " + FormatUtil.money(r.variance()) + " (more cash than sales). Recorded in the log.");
        else warn("Short by " + FormatUtil.money(-r.variance()) + " (less cash than sales). Recorded in the log.");
    }

    /** Cash count of zero is valid (e.g. no Cash sales yet), so this allows 0 unlike readPositiveDouble. */
    private static double readNonNegativeCash(String prompt) {
        while (true) {
            String s = InputUtil.readOptional(prompt).replace(",", "").replace("₱", "").trim();
            try {
                double v = FormatUtil.round2(Double.parseDouble(s));
                if (v >= 0 && v < 100_000_000 && !Double.isNaN(v)) return v;
                System.out.println(ConsoleStyle.color("    ✗ Amount cannot be negative.", ConsoleStyle.YELLOW));
            } catch (NumberFormatException e) {
                System.out.println(ConsoleStyle.color("    ✗ Invalid amount.", ConsoleStyle.YELLOW));
            }
        }
    }

    /** One-line low-stock notice shown once right after login (silent details — the full list lives in the menus/reports). */
    static void lowStockAlert(ProductService products) {
        try {
            List<Product> low = products.lowStock();
            if (low.isEmpty()) {
                System.out.println(ConsoleStyle.color("  ✓  All stock levels are healthy.", ConsoleStyle.GREEN));
            } else {
                System.out.println(ConsoleStyle.color("  ⚠  Low-stock alert: " + low.size()
                        + " gadget(s) at or below threshold (" + ProductService.LOW_STOCK_THRESHOLD
                        + "). See the stock list for details.", ConsoleStyle.YELLOW));
            }
        } catch (ServiceException | DataAccessException e) {
            System.out.println(ConsoleStyle.color("  (Could not check stock levels: " + e.getMessage() + ")", ConsoleStyle.MUTED));
        }
    }

    static void logsFlow(ActivityLogService logs, User user) {
        int n = InputUtil.readInt("  How many recent entries? (1-500): ", 1, 500);
        System.out.println(ConsoleStyle.dim("  Fetching last " + n + " entries…"));
        printLogs(logs.recent(user, n));
    }

    static void summaryFlow(ReportService reports, User user, boolean allowExport) {
        printSummary(reports.buildSummary(user));
        if (allowExport && InputUtil.confirm("\n  Export this report to CSV?")) {
            java.util.List<Path> csvFiles = listExistingCsvFiles();
            Path chosen;
            if (csvFiles.isEmpty()) {
                System.out.println(ConsoleStyle.dim("  No existing CSV found — creating a new one."));
                String name = InputUtil.readOptional(ConsoleStyle.color("  File name [sales_inventory_report.csv]: ", ConsoleStyle.CYAN));
                if (name.isEmpty()) name = "sales_inventory_report.csv";
                if (!name.toLowerCase().endsWith(".csv")) name += ".csv";
                chosen = Path.of(name);
            } else {
                System.out.println();
                System.out.println(ConsoleStyle.boldColor("  Existing CSV reports (append will NOT overwrite):", ConsoleStyle.BRIGHT_WHITE));
                for (int i = 0; i < csvFiles.size(); i++) {
                    Path p = csvFiles.get(i);
                    String when = formatFileTime(p);
                    String size = formatFileSize(p);
                    System.out.printf("   %s %s  %s  %s%n",
                            ConsoleStyle.color("[" + (i + 1) + "]", ConsoleStyle.CYAN),
                            ConsoleStyle.bold(p.getFileName().toString()),
                            ConsoleStyle.dim("— Last exported: " + when + "  (" + size + ")"),
                            "");
                }
                int createOpt = csvFiles.size() + 1;
                System.out.printf("   %s %s%n",
                        ConsoleStyle.color("[" + createOpt + "]", ConsoleStyle.CYAN),
                        ConsoleStyle.bold("+ Create new file"));
                int sel = InputUtil.readInt(
                        "  Select report to append to [1-" + createOpt + "] (0 to cancel): ", 0, createOpt);
                if (sel == 0) {
                    System.out.println(ConsoleStyle.dim("  Cancelled."));
                    return;
                }
                if (sel == createOpt) {
                    String name = InputUtil.readOptional(ConsoleStyle.color("  New file name [sales_inventory_report.csv]: ", ConsoleStyle.CYAN));
                    if (name.isEmpty()) name = "sales_inventory_report.csv";
                    if (!name.toLowerCase().endsWith(".csv")) name += ".csv";
                    chosen = Path.of(name);
                    if (java.nio.file.Files.exists(chosen)) {
                        System.out.println(ConsoleStyle.color("  Note: file already exists — new data will be APPENDED below existing rows.", ConsoleStyle.YELLOW));
                    }
                } else {
                    chosen = csvFiles.get(sel - 1);
                    System.out.println(ConsoleStyle.color("  Appending below existing data in: " + chosen.getFileName(), ConsoleStyle.YELLOW));
                }
            }
            Path out = reports.exportCsv(user, chosen.toString());
            boolean appended = csvFiles.contains(out.toAbsolutePath().normalize()) || java.nio.file.Files.exists(out);
            // Determine if this was an append by checking if file had data before (CSVExporter handles it)
            java.io.File f = out.toFile();
            String whenNow = formatFileTime(out);
            if (f.length() > 0) {
                success("Report " + (csvFiles.stream().anyMatch(p -> p.toAbsolutePath().normalize().equals(out.toAbsolutePath().normalize())) ? "appended" : "exported") + " to: " + out.toAbsolutePath() + "  (updated: " + whenNow + ")");
            } else {
                success("Report exported to: " + out.toAbsolutePath() + "  (created: " + whenNow + ")");
            }
            System.out.println(ConsoleStyle.dim("  Old data preserved — new report added below."));
        }
    }

    private static java.util.List<Path> listExistingCsvFiles() {
        try (java.util.stream.Stream<Path> s = java.nio.file.Files.list(Path.of("."))) {
            return s.filter(p -> java.nio.file.Files.isRegularFile(p))
                    .filter(p -> p.getFileName().toString().toLowerCase().endsWith(".csv"))
                    .sorted((a, b) -> {
                        try {
                            return java.nio.file.Files.getLastModifiedTime(b).compareTo(java.nio.file.Files.getLastModifiedTime(a));
                        } catch (java.io.IOException e) { return a.compareTo(b); }
                    })
                    .toList();
        } catch (java.io.IOException e) {
            return java.util.List.of();
        }
    }

    private static String formatFileTime(Path p) {
        try {
            java.nio.file.attribute.FileTime ft = java.nio.file.Files.getLastModifiedTime(p);
            return java.time.LocalDateTime.ofInstant(ft.toInstant(), java.time.ZoneId.systemDefault())
                    .format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
        } catch (java.io.IOException e) { return "unknown"; }
    }

    private static String formatFileSize(Path p) {
        try {
            long sz = java.nio.file.Files.size(p);
            if (sz < 1024) return sz + " B";
            if (sz < 1024 * 1024) return String.format(java.util.Locale.US, "%.1f KB", sz / 1024.0);
            return String.format(java.util.Locale.US, "%.1f MB", sz / 1024.0 / 1024.0);
        } catch (java.io.IOException e) { return "?"; }
    }

    // Helper for menu rendering
    static void printMenuBox(String[] options, int cols) {
        // not used directly; menus build their own boxes
    }
}
