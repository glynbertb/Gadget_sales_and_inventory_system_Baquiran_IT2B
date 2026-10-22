package com.app.ui;

import com.app.model.Product;
import com.app.model.User;
import com.app.service.AuthService;
import com.app.service.ProductService;
import com.app.util.Ansi;
import com.app.util.InputUtil;

/** Inventory Staff console. */
public class InventoryMenu {

    private final AuthService auth;
    private final ProductService products;

    public InventoryMenu(AuthService auth, ProductService products) {
        this.auth = auth; this.products = products;
    }

    public void run(User user) {
        ViewHelper.safely(() -> ViewHelper.lowStockAlert(products));
        while (true) {
            printMenu(user);
            switch (InputUtil.readInt("Select option: ", 0, 6)) {
                case 1 -> ViewHelper.safely(() -> ViewHelper.printProducts(products.listProducts()));
                case 2 -> ViewHelper.safely(() -> {
                    var low = products.lowStock();
                    if (low.isEmpty()) System.out.println(Ansi.color("\n  ✓ No low-stock gadgets. All good!", Ansi.GREEN));
                    else {
                        System.out.println(Ansi.color("\n  ⚠ LOW STOCK ALERT  —  the following gadgets need attention:", Ansi.YELLOW));
                        ViewHelper.printProducts(low);
                    }
                });
                case 3 -> ViewHelper.safely(() -> {
                    String name = InputUtil.readRequired("Gadget name: ");
                    int cat = ViewHelper.chooseCategoryId(products.listCategories());
                    double price = InputUtil.readPositiveDouble("Price (₱): ");
                    int qty = InputUtil.readInt("Initial stock quantity: ", 0, 1_000_000);
                    Product p = products.addProduct(user, name, cat, price, qty);
                    ViewHelper.success("Gadget #" + p.getGadgetId() + " added (" + p.getStatus() + ").");
                });
                case 4 -> ViewHelper.safely(() -> {
                    ViewHelper.printProducts(products.listProducts());
                    int id = InputUtil.readInt("Gadget ID: ", 1, Integer.MAX_VALUE);
                    products.receiveStock(user, id, InputUtil.readInt("Units received: ", 1, 1_000_000));
                    ViewHelper.success("Stock updated.");
                });
                case 5 -> ViewHelper.safely(() -> {
                    ViewHelper.printProducts(products.listProducts());
                    int id = InputUtil.readInt("Gadget ID: ", 1, Integer.MAX_VALUE);
                    products.setStock(user, id, InputUtil.readInt("New stock quantity: ", 0, 1_000_000));
                    ViewHelper.success("Stock updated (correction).");
                });
                case 6 -> ViewHelper.safely(() -> updateStatusFlow(products, user));
                case 0 -> { auth.logout(user); System.out.println(Ansi.color("\n  Logged out. Stock kept safe — see you, " + user.getName() + "!", Ansi.MUTED)); return; }
                default -> { }
            }
        }
    }

    private void printMenu(User user) {
        ViewHelper.header("INVENTORY  •  " + user.getName());
        int w = 64;
        System.out.println(Ansi.color("┌" + "─".repeat(w) + "┐", Ansi.MUTED));
        printOpt(1, "View inventory records", w);
        printOpt(2, "Low-stock alerts  (≤ " + ProductService.LOW_STOCK_THRESHOLD + ")", w);
        printOpt(3, "Add new gadget", w);
        printOpt(4, "Receive stock  (add units)", w);
        printOpt(5, "Set stock quantity  (correction)", w);
        printOpt(6, "Update gadget availability status", w);
        System.out.println(Ansi.color("├" + "─".repeat(w) + "┤", Ansi.MUTED));
        printOpt(0, "Logout", w);
        System.out.println(Ansi.color("└" + "─".repeat(w) + "┘", Ansi.MUTED));
    }

    private void printOpt(int n, String label, int w) {
        String line = String.format("  [%s] %s", Ansi.boldColor(String.valueOf(n), Ansi.CYAN), label);
        String stripped = Ansi.strip(line);
        System.out.println(Ansi.color("│", Ansi.MUTED) + line + " ".repeat(Math.max(0, w - stripped.length())) + Ansi.color("│", Ansi.MUTED));
    }

    /** Shared with OwnerMenu. */
    static void updateStatusFlow(ProductService products, User user) {
        ViewHelper.printProducts(products.listProducts());
        int id = InputUtil.readInt("Gadget ID: ", 1, Integer.MAX_VALUE);
        System.out.println(Ansi.color("\n  ── Select New Status ────────────────", Ansi.MUTED));
        for (int i = 0; i < Product.STATUSES.length; i++) {
            String s = Product.STATUSES[i];
            String colored = switch (s) {
                case Product.STATUS_AVAILABLE -> Ansi.color(s, Ansi.GREEN);
                case Product.STATUS_OUT_OF_STOCK -> Ansi.color(s, Ansi.YELLOW);
                default -> Ansi.color(s, Ansi.RED);
            };
            System.out.println("    [" + Ansi.color(String.valueOf(i+1), Ansi.CYAN) + "] " + colored);
        }
        String status = Product.STATUSES[InputUtil.readInt("  Select: ", 1, Product.STATUSES.length) - 1];
        products.updateStatus(user, id, status);
        ViewHelper.success("Status updated to " + status + ".");
    }
}
