package com.app.ui;

import com.app.model.Category;
import com.app.model.Product;
import com.app.model.User;
import com.app.service.ActivityLogService;
import com.app.service.AuthService;
import com.app.service.ProductService;
import com.app.service.ReportService;
import com.app.service.SaleService;
import com.app.util.ConsoleStyle;
import com.app.util.InputUtil;
import java.util.List;

/** Owner / Admin console. */
public class OwnerMenu {

    private final AuthService auth;
    private final ProductService products;
    private final SaleService sales;
    private final ReportService reports;
    private final ActivityLogService logs;

    public OwnerMenu(AuthService auth, ProductService products, SaleService sales,
                     ReportService reports, ActivityLogService logs) {
        this.auth = auth; this.products = products; this.sales = sales; this.reports = reports; this.logs = logs;
    }

    public void run(User user) {
        ViewHelper.safely(() -> ViewHelper.lowStockAlert(products));
        while (true) {
            printMenu(user);
            switch (InputUtil.readInt("Select option: ", 0, 6)) {
                case 1 -> userMenu(user);
                case 2 -> categoryMenu(user);
                case 3 -> gadgetMenu(user);
                case 4 -> ViewHelper.safely(() -> ViewHelper.summaryFlow(reports, user, true));
                case 5 -> salesMenu(user);
                case 6 -> ViewHelper.safely(() -> ViewHelper.logsFlow(logs, user));
                case 0 -> { auth.logout(user); System.out.println(ConsoleStyle.color("\n  Logged out. See you next time, " + user.getName() + "!", ConsoleStyle.MUTED)); return; }
                default -> { }
            }
        }
    }

    private void printMenu(User user) {
        ViewHelper.header("ADMIN  •  " + user.getName() + "  (" + user.getRole() + ")");
        int w = 64;
        System.out.println(ConsoleStyle.color("┌" + "─".repeat(w) + "┐", ConsoleStyle.MUTED));
        printOpt(1, "User Account Management", "", w);
        printOpt(2, "Category Management", "", w);
        printOpt(3, "Gadget Management", "", w);
        printOpt(4, "Summary Report",  "", w);
        printOpt(5, "Sales History / Void Sale", "", w);
        printOpt(6, "System Activity Logs", "", w);
        System.out.println(ConsoleStyle.color("├" + "─".repeat(w) + "┤", ConsoleStyle.MUTED));
        printOpt(0, "Logout", "", w);
        System.out.println(ConsoleStyle.color("└" + "─".repeat(w) + "┘", ConsoleStyle.MUTED));
    }

    private void printOpt(int n, String label, String desc, int w) {
        String left = String.format("  [%s] %-26s", ConsoleStyle.boldColor(String.valueOf(n), ConsoleStyle.CYAN), ConsoleStyle.bold(label));
        String line = left + ConsoleStyle.dim(desc);
        String stripped = ConsoleStyle.strip(line);
        int pad = w - stripped.length();
        System.out.println(ConsoleStyle.color("│", ConsoleStyle.MUTED) + line + " ".repeat(Math.max(0, pad)) + ConsoleStyle.color("│", ConsoleStyle.MUTED));
    }

    // ------------------------------------------------------------------ users

    private void userMenu(User owner) {
        while (true) {
            ViewHelper.header("USER ACCOUNT MANAGEMENT");
            int w = 64;
            System.out.println(ConsoleStyle.color("┌" + "─".repeat(w) + "┐", ConsoleStyle.MUTED));
            printMini(1, "List users", 2, "Create account", w);
            printMini(3, "Edit name/role", 4, "Reset password", w);
            printMini(5, "Deactivate", 6, "Reactivate", w);
            printMini(7, "Delete account", 8, "Pending approvals", w);
            System.out.println(ConsoleStyle.color("├" + "─".repeat(w) + "┤", ConsoleStyle.MUTED));
            printSingle(0, "Back", w);
            System.out.println(ConsoleStyle.color("└" + "─".repeat(w) + "┘", ConsoleStyle.MUTED));
            switch (InputUtil.readInt("Select option: ", 0, 8)) {
                case 1 -> ViewHelper.safely(() -> ViewHelper.printUsers(auth.listUsers(owner)));
                case 2 -> ViewHelper.safely(() -> {
                    String name = InputUtil.readRequired("Full name: ");
                    String username = InputUtil.readRequired("Username: ");
                    String pw = InputUtil.readPassword("Temporary password (min 6 chars): ");
                    String role = ViewHelper.chooseRole();
                    User u = auth.createStaff(owner, name, username, pw, role);
                    ViewHelper.success("Account #" + u.getUserId() + " created (" + u.getRole() + ").");
                });
                case 3 -> ViewHelper.safely(() -> {
                    ViewHelper.printUsers(auth.listUsers(owner));
                    int id = InputUtil.readInt("User ID to edit: ", 1, Integer.MAX_VALUE);
                    String name = InputUtil.readRequired("New full name: ");
                    String role = ViewHelper.chooseRole();
                    auth.updateUser(owner, id, name, role);
                    ViewHelper.success("Account updated.");
                });
                case 4 -> ViewHelper.safely(() -> {
                    ViewHelper.printUsers(auth.listUsers(owner));
                    int id = InputUtil.readInt("User ID: ", 1, Integer.MAX_VALUE);
                    auth.resetPassword(owner, id, InputUtil.readPassword("New password (min 6 chars): "));
                    ViewHelper.success("Password reset.");
                });
                case 5 -> ViewHelper.safely(() -> {
                    ViewHelper.printUsers(auth.listUsers(owner));
                    int id = InputUtil.readInt("User ID to deactivate: ", 1, Integer.MAX_VALUE);
                    if (InputUtil.confirm("Deactivate account #" + id + "?")) {
                        auth.setUserStatus(owner, id, false);
                        ViewHelper.success("Account deactivated.");
                    } else System.out.println(ConsoleStyle.muted("  Cancelled."));
                });
                case 6 -> ViewHelper.safely(() -> {
                    ViewHelper.printUsers(auth.listUsers(owner));
                    int id = InputUtil.readInt("User ID to reactivate: ", 1, Integer.MAX_VALUE);
                    auth.setUserStatus(owner, id, true);
                    ViewHelper.success("Account reactivated.");
                });
                case 7 -> ViewHelper.safely(() -> {
                    ViewHelper.printUsers(auth.listUsers(owner));
                    int id = InputUtil.readInt("User ID to delete: ", 1, Integer.MAX_VALUE);
                    if (InputUtil.confirm("Permanently delete account #" + id + "? This cannot be undone.")) {
                        auth.deleteUser(owner, id);
                        ViewHelper.success("Account deleted.");
                    } else System.out.println(ConsoleStyle.muted("  Cancelled."));
                });
                case 8 -> ViewHelper.safely(() -> {
                    List<User> pending = auth.pendingUsers(owner);
                    ViewHelper.printUserTable(pending);
                    if (pending.isEmpty()) return;
                    int id = InputUtil.readInt("Target User ID (0 to cancel): ", 0, Integer.MAX_VALUE);
                    if (id == 0) {
                        System.out.println(ConsoleStyle.muted("  Cancelled."));
                        return;
                    }
                    User approved = auth.approveUser(owner, id);
                    ViewHelper.success("Account #" + approved.getUserId() + " (" + approved.getUsername()
                            + ") approved. Status is now " + approved.getStatus() + ".");
                });
                case 0 -> { return; }
                default -> { }
            }
        }
    }

    private void printMini(int n1, String l1, int n2, String l2, int w) {
        String a = String.format("  [%s] %-18s", ConsoleStyle.color(String.valueOf(n1), ConsoleStyle.CYAN), l1);
        String b = String.format("  [%s] %-18s", ConsoleStyle.color(String.valueOf(n2), ConsoleStyle.CYAN), l2);
        String line = a + b;
        String stripped = ConsoleStyle.strip(line);
        int pad = w - stripped.length();
        System.out.println(ConsoleStyle.color("│", ConsoleStyle.MUTED) + line + " ".repeat(Math.max(0, pad)) + ConsoleStyle.color("│", ConsoleStyle.MUTED));
    }
    private void printSingle(int n, String l, int w) {
        String line = String.format("  [%s] %s", ConsoleStyle.color(String.valueOf(n), ConsoleStyle.CYAN), l);
        String stripped = ConsoleStyle.strip(line);
        System.out.println(ConsoleStyle.color("│", ConsoleStyle.MUTED) + line + " ".repeat(Math.max(0, w - stripped.length())) + ConsoleStyle.color("│", ConsoleStyle.MUTED));
    }

    // ------------------------------------------------------------- categories

    private void categoryMenu(User owner) {
        while (true) {
            ViewHelper.header("CATEGORY MANAGEMENT");
            int w = 64;
            System.out.println(ConsoleStyle.color("┌" + "─".repeat(w) + "┐", ConsoleStyle.MUTED));
            printMini(1, "List", 2, "Add", w);
            printMini(3, "Edit", 4, "Remove", w);
            System.out.println(ConsoleStyle.color("├" + "─".repeat(w) + "┤", ConsoleStyle.MUTED));
            printSingle(0, "Back", w);
            System.out.println(ConsoleStyle.color("└" + "─".repeat(w) + "┘", ConsoleStyle.MUTED));
            switch (InputUtil.readInt("Select option: ", 0, 4)) {
                case 1 -> ViewHelper.safely(() -> ViewHelper.printCategories(products.listCategories()));
                case 2 -> ViewHelper.safely(() -> {
                    String name = InputUtil.readRequired("Category name: ");
                    String desc = InputUtil.readOptional("Description (optional): ");
                    Category c = products.addCategory(owner, name, desc);
                    ViewHelper.success("Category #" + c.getCategoryId() + " added.");
                });
                case 3 -> ViewHelper.safely(() -> {
                    ViewHelper.printCategories(products.listCategories());
                    int id = InputUtil.readInt("Category ID: ", 1, Integer.MAX_VALUE);
                    String name = InputUtil.readRequired("New name: ");
                    String desc = InputUtil.readOptional("New description (optional): ");
                    products.updateCategory(owner, id, name, desc);
                    ViewHelper.success("Category updated.");
                });
                case 4 -> ViewHelper.safely(() -> {
                    ViewHelper.printCategories(products.listCategories());
                    int id = InputUtil.readInt("Category ID to remove: ", 1, Integer.MAX_VALUE);
                    if (InputUtil.confirm("Remove category #" + id + "?")) {
                        products.deleteCategory(owner, id);
                        ViewHelper.success("Category removed.");
                    } else System.out.println(ConsoleStyle.muted("  Cancelled."));
                });
                case 0 -> { return; }
                default -> { }
            }
        }
    }

    // ----------------------------------------------------------------- gadgets

    private void gadgetMenu(User owner) {
        while (true) {
            ViewHelper.header("GADGET MANAGEMENT");
            int w = 64;
            System.out.println(ConsoleStyle.color("┌" + "─".repeat(w) + "┐", ConsoleStyle.MUTED));
            printMini(1, "List all", 2, "Search", w);
            printMini(3, "Add gadget", 4, "Edit info/price", w);
            printMini(5, "Receive stock", 6, "Set stock", w);
            printOpt(7, "Update status", "Available / Out of Stock / Discontinued", w);
            System.out.println(ConsoleStyle.color("├" + "─".repeat(w) + "┤", ConsoleStyle.MUTED));
            printSingle(0, "Back", w);
            System.out.println(ConsoleStyle.color("└" + "─".repeat(w) + "┘", ConsoleStyle.MUTED));
            switch (InputUtil.readInt("Select option: ", 0, 7)) {
                case 1 -> ViewHelper.safely(() -> ViewHelper.printProducts(products.listProducts()));
                case 2 -> ViewHelper.safely(() -> ViewHelper.printProducts(products.search(InputUtil.readRequired("Keyword: "))));
                case 3 -> ViewHelper.safely(() -> {
                    String name = InputUtil.readRequired("Gadget name: ");
                    int cat = ViewHelper.chooseCategoryId(products.listCategories());
                    double price = InputUtil.readPositiveDouble("Price (₱): ");
                    int qty = InputUtil.readInt("Initial stock quantity: ", 0, 1_000_000);
                    Product p = products.addProduct(owner, name, cat, price, qty);
                    ViewHelper.success("Gadget #" + p.getGadgetId() + " added (" + p.getStatus() + ").");
                });
                case 4 -> ViewHelper.safely(() -> {
                    ViewHelper.printProducts(products.listProducts());
                    int id = InputUtil.readInt("Gadget ID to edit: ", 1, Integer.MAX_VALUE);
                    String name = InputUtil.readRequired("Gadget name: ");
                    int cat = ViewHelper.chooseCategoryId(products.listCategories());
                    double price = InputUtil.readPositiveDouble("Price (₱): ");
                    products.updateProductInfo(owner, id, name, cat, price);
                    ViewHelper.success("Gadget updated.");
                });
                case 5 -> ViewHelper.safely(() -> {
                    ViewHelper.printProducts(products.listProducts());
                    int id = InputUtil.readInt("Gadget ID: ", 1, Integer.MAX_VALUE);
                    products.receiveStock(owner, id, InputUtil.readInt("Units received: ", 1, 1_000_000));
                    ViewHelper.success("Stock updated.");
                });
                case 6 -> ViewHelper.safely(() -> {
                    ViewHelper.printProducts(products.listProducts());
                    int id = InputUtil.readInt("Gadget ID: ", 1, Integer.MAX_VALUE);
                    products.setStock(owner, id, InputUtil.readInt("New stock quantity: ", 0, 1_000_000));
                    ViewHelper.success("Stock updated.");
                });
                case 7 -> ViewHelper.safely(() -> InventoryMenu.updateStatusFlow(products, owner));
                case 0 -> { return; }
                default -> { }
            }
        }
    }

    // ------------------------------------------------------------------- sales

    private void salesMenu(User owner) {
        while (true) {
            ViewHelper.header("SALES HISTORY");
            int w = 64;
            System.out.println(ConsoleStyle.color("┌" + "─".repeat(w) + "┐", ConsoleStyle.MUTED));
            printMini(1, "View transactions", 2, "Void a sale", w);
            System.out.println(ConsoleStyle.color("├" + "─".repeat(w) + "┤", ConsoleStyle.MUTED));
            printSingle(0, "Back", w);
            System.out.println(ConsoleStyle.color("└" + "─".repeat(w) + "┘", ConsoleStyle.MUTED));
            switch (InputUtil.readInt("Select option: ", 0, 2)) {
                case 1 -> ViewHelper.safely(() -> ViewHelper.viewSalesFlow(sales, owner));
                case 2 -> ViewHelper.safely(() -> ViewHelper.voidSaleFlow(sales, owner));
                case 0 -> { return; }
                default -> { }
            }
        }
    }
}
