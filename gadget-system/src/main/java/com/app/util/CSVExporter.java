package com.app.util;

import com.app.model.DailySales;
import com.app.model.Product;
import com.app.model.SummaryReport;
import com.app.model.TopSeller;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;

/**
 * Writes the Sales and Inventory Summary Report as an Excel-friendly CSV
 * (UTF-8 with BOM, CRLF line endings, all text quoted, formula-injection safe).
 * Presentable layout: store header, KPI cards, section dividers, 6-column grid.
 */
public final class CSVExporter {

    private static final String NL = "\r\n";
    // 6-column grid — keeps every section aligned in Excel
    private static final int COLS = 6;

    private CSVExporter() { }

    public static Path exportSummary(SummaryReport r, Path target) throws IOException {
        boolean exists = Files.exists(target) && Files.size(target) > 0;
        if (exists) {
            try (BufferedWriter w = Files.newBufferedWriter(target, StandardCharsets.UTF_8,
                    java.nio.file.StandardOpenOption.CREATE, java.nio.file.StandardOpenOption.APPEND)) {
                row(w);
                row(w);
                // Full-width separator — easy to spot when scrolling appended exports
                row(w, "━━━━━━━━━━━━━━━━━━━━  APPENDED EXPORT  •  " + r.generatedAt() + "  ━━━━━━━━━━━━━━━━━━━━", "", "", "", "", "");
                row(w);
                writeReportBody(w, r);
            }
        } else {
            try (BufferedWriter w = Files.newBufferedWriter(target, StandardCharsets.UTF_8)) {
                w.write('\uFEFF');
                writeReportBodyWithHeader(w, r);
            }
        }
        return target;
    }

    private static void writeReportBodyWithHeader(Writer w, SummaryReport r) throws IOException {
        // Match final.csv exactly — first 4 rows centered in column B (leading empty), no quotes for plain headers
        row(w, "", "◆  BERTZKIE GADGET STORE  ◆", "", "", "", "");
        row(w, "", "SALES & INVENTORY SUMMARY REPORT", "", "", "", "");
        row(w, "", "Generated At: " + r.generatedAt(), "", "", "", "");
        row(w, "", "Report ID: RPT-" + r.generatedAt().replaceAll("[^0-9]", "").substring(0, 8) + "-" + String.format("%04d", (int)(Math.random()*9999)), "", "", "", "");
        row(w, "", "", "", "", "", "");
        writeReportBody(w, r);
    }

    private static void writeReportBody(Writer w, SummaryReport r) throws IOException {
        // ── KPI CARDS ── (plain headers, no quotes — matches final.csv)
        row(w, "━━━━━━━━━━━━━━━━━━━━━━  SALES SUMMARY  ━━━━━━━━━━━━━━━━━━━━━━", "", "", "", "", "");
        row(w, "Metric", "Value", "", "", "", "");
        row(w, "Total Sales Amount", csv(money(r.totalSalesAmount())), "", "", "", "");
        row(w, "Total Transactions (Completed)", String.valueOf(r.totalTransactions()), "", "", "", "");
        row(w, "Voided Transactions", String.valueOf(r.voidedTransactions()), "", "", "", "");
        String health = r.lowStock().isEmpty() ? "✓  All stock levels healthy" : "⚠  " + r.lowStock().size() + " low-stock item(s) — see below";
        row(w, "Stock Health", csv(health), "", "", "", "");
        row(w, "", "", "", "", "", "");

        // ── TOP SELLERS ──
        int topCount = r.topSellers().size();
        row(w, "━━━━━━━━━━━━━━━━  TOP-SELLING GADGETS  (Top " + TOP_N + ")  —  " + topCount + " item(s)  ━━━━━━━━━━━━━━━━", "", "", "", "", "");
        if (topCount == 0) {
            row(w, "(no sales yet)", "", "", "", "", "");
        } else {
            row(w, "Rank", "Gadget", "Units Sold", "Revenue", "", "");
            int rank = 1;
            for (TopSeller t : r.topSellers()) {
                row(w, String.valueOf(rank++), csv(t.name()), String.valueOf(t.quantity()), csv(money(t.revenue())), "", "");
            }
        }
        row(w, "", "", "", "", "", "");

        // ── SALES BY DATE ──
        int dateCount = r.salesByDate().size();
        row(w, "━━━━━━━━━━━━━━━━  SALES BY DATE  —  " + dateCount + " day(s)  ━━━━━━━━━━━━━━━━", "", "", "", "", "");
        if (dateCount == 0) {
            row(w, "(no sales yet)", "", "", "", "", "");
        } else {
            row(w, "Date", "Transactions", "Total Sales", "", "", "");
            for (DailySales d : r.salesByDate()) {
                row(w, d.date(), String.valueOf(d.transactions()), csv(money(d.total())), "", "", "");
            }
        }
        row(w, "", "", "", "", "", "");

        // ── CURRENT STOCK ──
        int stockCount = r.stockLevels().size();
        row(w, "━━━━━━━━━━━━━━━━  CURRENT STOCK LEVELS  —  " + stockCount + " item(s)  ━━━━━━━━━━━━━━━━", "", "", "", "", "");
        productHeader(w);
        if (stockCount == 0) {
            row(w, "(no gadgets)", "", "", "", "", "");
        } else {
            for (Product p : r.stockLevels()) productRow(w, p);
        }
        row(w, "", "", "", "", "", "");

        // ── LOW STOCK ──
        int lowCount = r.lowStock().size();
        String lowTitle = "━━━━━━━━━━━━━━━━  LOW-STOCK GADGETS  (≤ " + r.lowStockThreshold() + ")  —  " + lowCount + " item(s)  ━━━━━━━━━━━━━━━━";
        row(w, lowTitle, "", "", "", "", "");
        productHeader(w);
        if (lowCount == 0) {
            row(w, "✓  No low-stock gadgets — all good!", "", "", "", "", "");
        } else {
            for (Product p : r.lowStock()) productRow(w, p);
        }
        row(w, "", "", "", "", "", "");
        // Footer — matches final.csv exactly (leading empty)
        row(w, "", "— End of Report —", "", "", "", "");
        row(w, "", "Thank you for using Bertzkie Gadget System  ♡", "", "", "", "");
    }

    private static final int TOP_N = 5;

    private static void productHeader(Writer w) throws IOException {
        row(w, "Gadget ID", "Gadget", "Category", "Price", "Stock", "Status");
    }

    private static void productRow(Writer w, Product p) throws IOException {
        row(w, String.valueOf(p.getGadgetId()), csv(p.getGadgetName()), csv(p.getCategoryName()),
                csv(money(p.getPrice())), String.valueOf(p.getStockQuantity()), csv(p.getStatus()));
    }

    private static void row(Writer w, String... cells) throws IOException {
        // Always write 6 columns to match final.csv presentable grid (empty strings keep commas)
        String[] out = new String[COLS];
        for (int i = 0; i < COLS; i++) {
            out[i] = i < cells.length ? cells[i] : "";
        }
        w.write(String.join(",", out));
        w.write(NL);
    }

    /** Quotes text and neutralises spreadsheet formula injection (=, +, -, @). */
    static String text(String v) {
        if (v == null) return "\"\"";
        String s = v;
        if (!s.isEmpty() && "=+-@\t\r".indexOf(s.charAt(0)) >= 0) s = "'" + s;
        return "\"" + s.replace("\"", "\"\"") + "\"";
    }

    /** Minimal quoting for presentable CSV — matches final.csv: quotes only when needed (comma, quote, newline, injection). */
    static String csv(String v) {
        if (v == null) return "";
        String s = v;
        boolean needQuote = s.contains(",") || s.contains("\"") || s.contains("\n") || s.contains("\r");
        if (!s.isEmpty() && "=+-@\t".indexOf(s.charAt(0)) >= 0) needQuote = true;
        if (needQuote) {
            if (!s.isEmpty() && "=+-@\t".indexOf(s.charAt(0)) >= 0) s = "'" + s;
            return "\"" + s.replace("\"", "\"\"") + "\"";
        }
        return s;
    }

    private static String money(double v) {
        return String.format(Locale.US, "₱%,.2f", v);
    }

    private static String num(double v) { return String.format(Locale.ROOT, "%.2f", v); }
}
