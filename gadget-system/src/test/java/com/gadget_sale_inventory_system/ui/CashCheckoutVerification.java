package com.gadget_sale_inventory_system.ui;

import com.gadget_sale_inventory_system.dao.*;
import com.gadget_sale_inventory_system.model.*;
import com.gadget_sale_inventory_system.service.*;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.sql.*;
import java.util.List;

/** Cash payment regression checks. Run only against a disposable database. */
public final class CashCheckoutVerification {
    private static int passed;

    private static void check(boolean condition, String label) {
        if (!condition) throw new AssertionError(label);
        System.out.println("PASS " + label);
        passed++;
    }

    private static int count(String table) throws SQLException {
        try (Connection c = DatabaseConnection.getConnection(); Statement st = c.createStatement();
                ResultSet rs = st.executeQuery("SELECT COUNT(*) FROM " + table)) {
            return rs.getInt(1);
        }
    }

    private static String receipt(Sale sale) {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        PrintStream original = System.out;
        try (PrintStream capture = new PrintStream(bytes, true, StandardCharsets.UTF_8)) {
            System.setOut(capture);
            ViewHelper.printReceipt(sale);
        } finally {
            System.setOut(original);
        }
        return bytes.toString(StandardCharsets.UTF_8);
    }

    public static void main(String[] args) throws Exception {
        DatabaseInitializer.initialize();
        ActivityLogService logs = new ActivityLogService(new ActivityLogDao());
        AuthService auth = new AuthService(new UserDAOImpl(), logs);
        ProductService products = new ProductService(new ProductDAOImpl(), new CategoryDAOImpl(), logs);
        SaleService sales = new SaleService(new SaleDAOImpl(), new ProductDAOImpl(), logs);
        User owner = auth.register("Cash Owner", "cash_owner", "TestPass123");
        User cashier = auth.createStaff(owner, "Cash Tester", "cash_tester", "TestPass123", User.ROLE_CASHIER);
        Category category = products.addCategory(owner, "Cash Test Category", "Test");
        Product product = products.addProduct(owner, "Test Gadget", category.getCategoryId(), 99.99, 50);
        List<SaleService.CartItem> cart = List.of(new SaleService.CartItem(product.getGadgetId(), 1));
        int beforeSales = count("sales"), beforeLogs = count("activity_logs");
        for (Double tender : new Double[]{null, -1.0, 0.0, Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY, 99.98, 100_000_000.0}) {
            boolean rejected = false;
            try { sales.checkout(cashier, cart, "Cash", tender); }
            catch (ServiceException expected) { rejected = true; }
            check(rejected && count("sales") == beforeSales && count("activity_logs") == beforeLogs
                    && products.getProduct(product.getGadgetId()).getStockQuantity() == 50,
                    "Invalid/insufficient cash rejected without sale, stock or log changes: " + tender);
        }
        Sale changed = sales.checkout(cashier, cart, "Cash", 100.0);
        check(changed.getCashReceived() == 100 && changed.getChangeAmount() == 0.01,
                "Centavo change: 100.00 minus 99.99 equals 0.01");
        Sale loaded = sales.getSaleDetails(cashier, changed.getSaleId());
        check(loaded.getCashReceived().equals(changed.getCashReceived())
                && loaded.getChangeAmount().equals(changed.getChangeAmount()), "Cash and change survive DAO reload");
        String output = receipt(changed);
        check(output.contains("CASH RECEIVED") && output.contains("CHANGE") && output.contains("₱0.01"),
                "Cash receipt displays received amount and change");
        check(output.lines().filter(line -> line.contains("CASH RECEIVED") || line.contains("CHANGE"))
                .allMatch(line -> line.length() == 52), "Payment receipt rows align with 52-character border");
        Sale exact = sales.checkout(cashier, cart, "Cash", 99.99);
        check(exact.getChangeAmount() == 0 && receipt(exact).contains("₱0.00"), "Exact payment displays zero change");
        for (String method : List.of("Card", "E-Wallet")) {
            Sale nonCash = sales.checkout(cashier, cart, method);
            Sale saved = sales.getSaleDetails(cashier, nonCash.getSaleId());
            check(saved.getCashReceived() == null && saved.getChangeAmount() == null
                    && !receipt(nonCash).contains("CASH RECEIVED"), method + " leaves cash fields empty and hides cash receipt rows");
        }
        boolean rejected = false;
        try { sales.checkout(cashier, cart, "Card", 100.0); }
        catch (ServiceException expected) { rejected = true; }
        check(rejected, "Non-cash payment rejects a cash tender");
        // Tender/change represent the original checkout, even after lifecycle changes.
        sales.returnItem(owner, changed.getSaleId(), product.getGadgetId(), 1, "Test return");
        loaded = sales.getSaleDetails(owner, changed.getSaleId());
        check(loaded.getCashReceived() == 100 && loaded.getChangeAmount() == 0.01,
                "Return preserves original checkout payment amounts");
        try (Connection c = DatabaseConnection.getConnection(); Statement st = c.createStatement()) {
            st.execute("INSERT INTO sales(user_id,sale_date,total_amount,payment_method,sale_status) VALUES ("
                    + cashier.getUserId() + ",'2025-01-01 10:00:00',99.99,'Cash','Completed')");
            try (ResultSet rs = st.executeQuery("SELECT MAX(sale_id) FROM sales")) {
                Sale historical = sales.getSaleDetails(owner, rs.getInt(1));
                check(historical.getCashReceived() == null && historical.getChangeAmount() == null,
                        "Historical cash sale keeps unknown received/change as NULL");
            }
        }
        beforeSales = count("sales"); beforeLogs = count("activity_logs");
        int beforeStock = products.getProduct(product.getGadgetId()).getStockQuantity();
        try (Connection c = DatabaseConnection.getConnection(); Statement st = c.createStatement()) {
            st.execute("CREATE TRIGGER fail_cash_log BEFORE INSERT ON activity_logs BEGIN SELECT RAISE(ABORT,'test failure'); END");
        }
        rejected = false;
        try { sales.checkout(cashier, cart, "Cash", 100.0); }
        catch (RuntimeException expected) { rejected = true; }
        check(rejected && count("sales") == beforeSales && count("activity_logs") == beforeLogs
                && products.getProduct(product.getGadgetId()).getStockQuantity() == beforeStock,
                "Audit failure rolls back cash sale and stock together");
        try (Connection c = DatabaseConnection.getConnection(); Statement st = c.createStatement()) {
            st.execute("DROP TRIGGER fail_cash_log");
        }
        DatabaseInitializer.initialize();
        check(sales.getSaleDetails(owner, exact.getSaleId()).getChangeAmount() == 0,
                "Repeat startup preserves recorded payment details");
        System.out.println("TOTAL " + passed + " cash checks passed");
    }
}
