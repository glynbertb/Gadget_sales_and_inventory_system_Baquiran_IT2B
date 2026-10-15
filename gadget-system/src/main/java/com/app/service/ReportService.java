package com.app.service;

import com.app.dao.ProductDAO;
import com.app.dao.SaleDAO;
import com.app.model.Sale;
import com.app.model.SummaryReport;
import com.app.model.User;
import com.app.util.CSVExporter;
import com.app.util.FormatUtil;
import java.io.IOException;
import java.nio.file.Path;

/** Builds the "Sales and Inventory Summary Report" and exports it to CSV. */
public class ReportService {

    private static final int TOP_N = 5;

    private final SaleDAO saleDAO;
    private final ProductDAO productDAO;
    private final ActivityLogService logService;

    public ReportService(SaleDAO saleDAO, ProductDAO productDAO, ActivityLogService logService) {
        this.saleDAO = saleDAO;
        this.productDAO = productDAO;
        this.logService = logService;
    }

    public SummaryReport buildSummary(User actor) {
        AccessControl.require(actor, User.ROLE_OWNER, User.ROLE_SUPERVISOR);
        return new SummaryReport(
                FormatUtil.now(),
                saleDAO.totalSalesAmount(),
                saleDAO.countByStatus(Sale.STATUS_COMPLETED),
                saleDAO.countByStatus(Sale.STATUS_VOIDED),
                saleDAO.topSelling(TOP_N),
                saleDAO.salesByDate(),
                productDAO.findAll(),
                productDAO.findLowStock(ProductService.LOW_STOCK_THRESHOLD),
                ProductService.LOW_STOCK_THRESHOLD);
    }

    /** Result of a register (till) count: expected cash vs. what was actually counted. */
    public record RegisterAudit(String date, double expectedCash, double countedCash, double variance) { }

    /**
     * Register audit: compares today's Completed Cash sales against the physical
     * cash count. Card / E-Wallet sales never sit in the drawer, so only Cash counts.
     * The outcome is written to the activity log either way.
     */
    public RegisterAudit registerAudit(User actor, double countedCash) {
        AccessControl.require(actor, User.ROLE_OWNER, User.ROLE_SUPERVISOR);
        if (countedCash < 0) throw new ServiceException("Counted cash cannot be negative.");
        String today = java.time.LocalDate.now().toString(); // matches "yyyy-MM-dd HH:mm:ss" prefix
        double expected = FormatUtil.round2(saleDAO.totalCashOnDate(today));
        double variance = FormatUtil.round2(countedCash - expected);
        logService.log(actor.getUserId(), "REGISTER_AUDIT", "Register audit for " + today + ": expected "
                + FormatUtil.money(expected) + ", counted " + FormatUtil.money(countedCash)
                + ", variance " + FormatUtil.money(variance) + ".");
        return new RegisterAudit(today, expected, countedCash, variance);
    }

    /** Owner only. Returns the absolute path of the generated file. */
    public Path exportCsv(User actor, String fileName) {
        AccessControl.require(actor, User.ROLE_OWNER);
        SummaryReport report = buildSummary(actor);
        try {
            Path out = CSVExporter.exportSummary(report, Path.of(fileName)).toAbsolutePath();
            logService.log(actor.getUserId(), "REPORT_EXPORT", "Exported summary report to " + out.getFileName() + ".");
            return out;
        } catch (IOException e) {
            throw new ServiceException("Could not write CSV file: " + e.getMessage());
        }
    }
}
