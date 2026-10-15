package com.app.model;

import java.util.List;

/** Immutable snapshot used by both the console report and the CSV export. */
public record SummaryReport(
        String generatedAt,
        double totalSalesAmount,
        int totalTransactions,
        int voidedTransactions,
        List<TopSeller> topSellers,
        List<DailySales> salesByDate,
        List<Product> stockLevels,
        List<Product> lowStock,
        int lowStockThreshold) { }
