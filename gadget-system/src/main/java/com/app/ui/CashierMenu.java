package com.app.ui;

import com.app.model.Product;
import com.app.model.Sale;
import com.app.model.User;
import com.app.service.AuthService;
import com.app.service.ProductService;
import com.app.service.SaleService;
import com.app.service.SaleService.CartItem;
import com.app.util.ConsoleStyle;
import com.app.util.FormatUtil;
import com.app.util.InputUtil;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Cashier console: gadget lookup, sales terminal, transaction history. */
public class CashierMenu {

    private final AuthService auth;
    private final ProductService products;
    private final SaleService sales;

    public CashierMenu(AuthService auth, ProductService products, SaleService sales) {
        this.auth = auth; this.products = products; this.sales = sales;
    }

    public void run(User user) {
        while (true) {
            printMenu(user);
            switch (InputUtil.readInt("Select option: ", 0, 4)) {
                case 1 -> ViewHelper.safely(() -> ViewHelper.printProducts(products.listAvailable()));
                case 2 -> ViewHelper.safely(() -> ViewHelper.printProducts(products.search(InputUtil.readRequired("Keyword: "))));
                case 3 -> ViewHelper.safely(() -> newSale(user));
                case 4 -> ViewHelper.safely(() -> ViewHelper.viewSalesFlow(sales, user));
                case 0 -> { auth.logout(user); System.out.println(ConsoleStyle.color("\n  Logged out. Great work today, " + user.getName() + "!", ConsoleStyle.MUTED)); return; }
                default -> { }
            }
        }
    }

    private void printMenu(User user) {
        ViewHelper.header("CASHIER  •  " + user.getName());
        int w = 64;
        System.out.println(ConsoleStyle.color("┌" + "─".repeat(w) + "┐", ConsoleStyle.MUTED));
        printOpt(1, "View available gadgets & stock", w);
        printOpt(2, "Search gadgets", w);
        printOpt(3, "New sale  (checkout)", w);
        printOpt(4, "My transaction history", w);
        System.out.println(ConsoleStyle.color("├" + "─".repeat(w) + "┤", ConsoleStyle.MUTED));
        printOpt(0, "Logout", w);
        System.out.println(ConsoleStyle.color("└" + "─".repeat(w) + "┘", ConsoleStyle.MUTED));
    }

    private void printOpt(int n, String label, int w) {
        String line = String.format("  [%s] %s", ConsoleStyle.boldColor(String.valueOf(n), ConsoleStyle.CYAN), ConsoleStyle.bold(label));
        String stripped = ConsoleStyle.strip(line);
        System.out.println(ConsoleStyle.color("│", ConsoleStyle.MUTED) + line + " ".repeat(Math.max(0, w - stripped.length())) + ConsoleStyle.color("│", ConsoleStyle.MUTED));
    }

    private void newSale(User user) {
        Map<Integer, Integer> cart = new LinkedHashMap<>();   // gadgetId -> quantity
        Map<Integer, Product> known = new LinkedHashMap<>();  // for display only; checkout re-validates in the DB

        while (true) {
            ViewHelper.header("SALES TERMINAL");
            printCart(cart, known);
            int w = 64;
            System.out.println(ConsoleStyle.color("┌" + "─".repeat(w) + "┐", ConsoleStyle.MUTED));
            printCartOpt(1, "Add gadget", 2, "Remove gadget", w);
            printCartOpt(3, "Checkout", 0, "Cancel sale", w);
            System.out.println(ConsoleStyle.color("└" + "─".repeat(w) + "┘", ConsoleStyle.MUTED));
            switch (InputUtil.readInt("Select option: ", 0, 3)) {
                case 1 -> ViewHelper.safely(() -> addToCart(cart, known));
                case 2 -> {
                    if (cart.isEmpty()) { System.out.println(ConsoleStyle.color("  Cart is empty.", ConsoleStyle.YELLOW)); break; }
                    int id = InputUtil.readInt("Gadget ID to remove: ", 1, Integer.MAX_VALUE);
                    System.out.println(cart.remove(id) != null ? ConsoleStyle.color("  ✓ Removed.", ConsoleStyle.GREEN) : ConsoleStyle.color("  That gadget is not in the cart.", ConsoleStyle.YELLOW));
                }
                case 3 -> {
                    if (cart.isEmpty()) { System.out.println(ConsoleStyle.color("  Cart is empty.", ConsoleStyle.YELLOW)); break; }
                    if (checkout(user, cart)) return;
                }
                case 0 -> {
                    if (cart.isEmpty() || InputUtil.confirm("Discard this sale?")) return;
                }
                default -> { }
            }
        }
    }

    private void printCartOpt(int n1, String l1, int n2, String l2, int w) {
        String a = String.format("  [%s] %-18s", ConsoleStyle.color(String.valueOf(n1), ConsoleStyle.CYAN), l1);
        String b = String.format("  [%s] %-18s", ConsoleStyle.color(String.valueOf(n2), ConsoleStyle.CYAN), l2);
        String line = a + b;
        String stripped = ConsoleStyle.strip(line);
        System.out.println(ConsoleStyle.color("│", ConsoleStyle.MUTED) + line + " ".repeat(Math.max(0, w - stripped.length())) + ConsoleStyle.color("│", ConsoleStyle.MUTED));
    }

    private void addToCart(Map<Integer, Integer> cart, Map<Integer, Product> known) {
        ViewHelper.printProducts(products.listAvailable());
        int id = InputUtil.readInt("Gadget ID (0 = back): ", 0, Integer.MAX_VALUE);
        if (id == 0) return;
        Product p = products.getProduct(id);
        if (!Product.STATUS_AVAILABLE.equals(p.getStatus())) {
            System.out.println(ConsoleStyle.color("  ✗ '" + p.getGadgetName() + "' is " + p.getStatus() + ".", ConsoleStyle.RED));
            return;
        }
        int inCart = cart.getOrDefault(id, 0);
        int room = p.getStockQuantity() - inCart;
        if (room <= 0) {
            System.out.println(ConsoleStyle.color("  ✗ All available stock of this gadget is already in the cart.", ConsoleStyle.YELLOW));
            return;
        }
        int qty = InputUtil.readInt("Quantity (1-" + room + "): ", 1, room);
        cart.merge(id, qty, Integer::sum);
        known.put(id, p);
        System.out.println(ConsoleStyle.color("  ✓ Added " + qty + " × " + p.getGadgetName() + ".", ConsoleStyle.GREEN));
    }

    private void printCart(Map<Integer, Integer> cart, Map<Integer, Product> known) {
        if (cart.isEmpty()) {
            System.out.println(ConsoleStyle.color("  ┌─────────────────────────────────────────────┐", ConsoleStyle.MUTED));
            System.out.println(ConsoleStyle.color("  │", ConsoleStyle.MUTED) + ConsoleStyle.dim(center("Cart is empty  —  add gadgets to begin", 43)) + ConsoleStyle.color("│", ConsoleStyle.MUTED));
            System.out.println(ConsoleStyle.color("  └─────────────────────────────────────────────┘", ConsoleStyle.MUTED));
            System.out.println();
            return;
        }
        // Plain grid: to change a column width, edit its %-Ns below AND the matching dashes in sepLine.
        String sepLine = "+------+----------------------------+------+-------------+--------------+";
        System.out.println();
        System.out.println(sepLine);
        System.out.println(String.format("| %-4s | %-26s | %-4s | %-11s | %-12s |", "ID", "Gadget", "Qty", "Price", "Subtotal"));
        System.out.println(sepLine);
        double total = 0;
        for (Map.Entry<Integer, Integer> e : cart.entrySet()) {
            Product p = known.get(e.getKey());
            double sub = FormatUtil.round2(p.getPrice() * e.getValue());
            total += sub;
            System.out.println(String.format("| %-4s | %-26s | %-4s | %-11s | %-12s |",
                    p.getGadgetId(),
                    FormatUtil.trunc(p.getGadgetName(), 26),
                    e.getValue(),
                    FormatUtil.money(p.getPrice()),
                    FormatUtil.money(sub)));
        }
        System.out.println(sepLine);
        // TOTAL keeps the same columns: label under Price, amount under Subtotal.
        System.out.println(String.format("| %-4s | %-26s | %-4s | %-11s | %-12s |", "", "", "", "TOTAL", FormatUtil.money(FormatUtil.round2(total))));
        System.out.println(sepLine);
        System.out.println();
    }

    private static String center(String s, int w) {
        int len = ConsoleStyle.strip(s).length();
        if (len >= w) return s;
        int pad = (w - len)/2;
        return " ".repeat(pad) + s + " ".repeat(w - len - pad);
    }

    /** @return true when the sale was completed (terminal should close), false to keep editing the cart. */
    private boolean checkout(User user, Map<Integer, Integer> cart) {
        System.out.println(ConsoleStyle.color("\n  ── Payment Method ──────────────────────", ConsoleStyle.MUTED));
        for (int i = 0; i < Sale.PAYMENT_METHODS.length; i++)
            System.out.println("    [" + ConsoleStyle.color(String.valueOf(i+1), ConsoleStyle.CYAN) + "] " + Sale.PAYMENT_METHODS[i]);
        String method = Sale.PAYMENT_METHODS[InputUtil.readInt("  Select: ", 1, Sale.PAYMENT_METHODS.length) - 1];
        if (!InputUtil.confirm("Confirm and complete this sale?")) return false;

        List<CartItem> lines = new ArrayList<>();
        cart.forEach((id, qty) -> lines.add(new CartItem(id, qty)));
        try {
            Sale sale = sales.checkout(user, lines, method);
            ViewHelper.printReceipt(sale);
            InputUtil.pause();
            return true;
        } catch (com.app.service.ServiceException | com.app.util.DataAccessException e) {
            System.out.println(ConsoleStyle.color("\n  ✗ Checkout failed and was rolled back: " + e.getMessage(), ConsoleStyle.RED));
            return false; // cart kept so the cashier can adjust quantities
        }
    }
}
