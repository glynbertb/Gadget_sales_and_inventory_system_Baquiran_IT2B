package com.app.view;

import com.app.model.Product;
import com.app.model.User;
import com.app.service.ActivityLogService;
import com.app.service.AuthService;
import com.app.service.ProductService;
import com.app.service.ReportService;
import com.app.service.SaleService;
import com.app.util.Ansi;
import com.app.util.InputUtil;

/** Store Supervisor console: voids, returns, exchanges, stock/register audits, operational reports. */
public class SupervisorMenu {

    private final AuthService auth;
    private final ProductService products;
    private final SaleService sales;
    private final ReportService reports;
    private final ActivityLogService logs;

    public SupervisorMenu(AuthService auth, ProductService products, SaleService sales,
                          ReportService reports, ActivityLogService logs) {
        this.auth = auth; this.products = products; this.sales = sales; this.reports = reports; this.logs = logs;
    }

    public void run(User user) {
        while (true) {
            printMenu(user);
            switch (InputUtil.readInt("Select option: ", 0, 8)) {
                case 1 -> ViewHelper.safely(() -> ViewHelper.viewSalesFlow(sales, user));
                case 2 -> ViewHelper.safely(() -> ViewHelper.voidSaleFlow(sales, user));
                case 3 -> ViewHelper.safely(() -> ViewHelper.returnFlow(sales, user));
                case 4 -> ViewHelper.safely(() -> ViewHelper.exchangeFlow(sales, products, user));
                case 5 -> ViewHelper.safely(() -> stockAudit(user));
                case 6 -> ViewHelper.safely(() -> ViewHelper.registerAuditFlow(reports, user));
                case 7 -> ViewHelper.safely(() -> ViewHelper.summaryFlow(reports, user, false));
                case 8 -> ViewHelper.safely(() -> ViewHelper.logsFlow(logs, user));
                case 0 -> { auth.logout(user); System.out.println(Ansi.color("\n  Logged out. Supervisor duties done — goodbye, " + user.getName() + "!", Ansi.MUTED)); return; }
                default -> { }
            }
        }
    }

    private void printMenu(User user) {
        ViewHelper.header("STORE SUPERVISOR  •  " + user.getName());
        int w = 64;
        System.out.println(Ansi.color("┌" + "─".repeat(w) + "┐", Ansi.MUTED));
        printOpt(1, "View transaction log", w);
        printOpt(2, "Void / cancel a sale", w);
        printOpt(3, "Return item(s) to stock", w);
        printOpt(4, "Exchange item(s)", w);
        printOpt(5, "Perform stock audit", w);
        printOpt(6, "Perform register audit", w);
        printOpt(7, "Operational summary report", w);
        printOpt(8, "System activity logs", w);
        System.out.println(Ansi.color("├" + "─".repeat(w) + "┤", Ansi.MUTED));
        printOpt(0, "Logout", w);
        System.out.println(Ansi.color("└" + "─".repeat(w) + "┘", Ansi.MUTED));
    }

    private void printOpt(int n, String label, int w) {
        String line = String.format("  [%s] %s", Ansi.boldColor(String.valueOf(n), Ansi.CYAN), label);
        String stripped = Ansi.strip(line);
        System.out.println(Ansi.color("│", Ansi.MUTED) + line + " ".repeat(Math.max(0, w - stripped.length())) + Ansi.color("│", Ansi.MUTED));
    }

    private void stockAudit(User user) {
        while (true) {
            ViewHelper.printProducts(products.listProducts());
            int id = InputUtil.readInt("\n  Gadget ID to audit (0 = finish): ", 0, Integer.MAX_VALUE);
            if (id == 0) return;
            Product p = products.getProduct(id);
            System.out.println(Ansi.color("\n  ── Auditing: ", Ansi.MUTED) + Ansi.bold(p.getGadgetName()) + Ansi.muted("  (System: " + p.getStockQuantity() + ")"));
            int counted = InputUtil.readInt("  Physical count: ", 0, 1_000_000);
            int variance = counted - p.getStockQuantity();
            boolean adjust = false;
            if (variance == 0) {
                System.out.println(Ansi.color("  ✓ No discrepancy. Stock matches.", Ansi.GREEN));
            } else {
                String delta = (variance > 0 ? "+" : "") + variance + " unit(s)";
                System.out.println(Ansi.color("  ⚠ Discrepancy detected: ", Ansi.YELLOW) + Ansi.bold(delta));
                adjust = InputUtil.confirm("  Adjust system stock to the counted quantity?");
            }
            products.auditStock(user, id, counted, adjust);
            if (adjust) ViewHelper.success("Audit recorded and stock adjusted to " + counted + ".");
            else ViewHelper.success("Audit recorded (no adjustment).");
            if (!InputUtil.confirm("  Audit another gadget?")) return;
        }
    }
}
