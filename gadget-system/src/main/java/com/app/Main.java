package com.app;

import com.app.dao.ActivityLogDAO;
import com.app.dao.ActivityLogDAOImpl;
import com.app.dao.CategoryDAO;
import com.app.dao.CategoryDAOImpl;
import com.app.dao.DatabaseConnection;
import com.app.dao.DatabaseInitializer;
import com.app.dao.ProductDAO;
import com.app.dao.ProductDAOImpl;
import com.app.dao.SaleDAO;
import com.app.dao.SaleDAOImpl;
import com.app.dao.UserDAO;
import com.app.dao.UserDAOImpl;
import com.app.service.ActivityLogService;
import com.app.service.AuthService;
import com.app.service.ProductService;
import com.app.service.ReportService;
import com.app.service.SaleService;
import com.app.ui.Authentication;
import com.app.util.Ansi;
import com.app.util.DataAccessException;
import com.app.util.InputUtil;
import com.app.ui.CashierMenu;
import com.app.ui.InventoryMenu;
import com.app.ui.OwnerMenu;
import com.app.ui.SupervisorMenu;
import java.util.NoSuchElementException;

/**
 * Single-pathway startup entry point.
 *
 * <p>Execution flow: initialize Database -> instantiate DAOs &amp; utilities
 * -> delegate execution to {@code Authentication.start()}. All login,
 * registration and role-routing logic lives in {@code Authentication};
 * this class only wires dependencies.
 */
public final class Main {

    private Main() { }

    public static void main(String[] args) {
        try {
            // 1. Initialize database (creates all required tables when missing).
            DatabaseInitializer.initialize();

            System.out.println(Ansi.dim("  Database : ") + Ansi.color(DatabaseConnection.getDatabasePath().toString(), Ansi.CYAN));
            System.out.println(Ansi.color("  ✓ Database ready.", Ansi.GREEN));

            // 2. Instantiate DAOs (single shared instances) & utilities.
            UserDAO userDAO = new UserDAOImpl();
            CategoryDAO categoryDAO = new CategoryDAOImpl();
            ProductDAO productDAO = new ProductDAOImpl();
            SaleDAO saleDAO = new SaleDAOImpl();
            ActivityLogDAO logDAO = new ActivityLogDAOImpl();

            ActivityLogService logService = new ActivityLogService(logDAO);
            AuthService authService = new AuthService(userDAO, logService);
            ProductService productService = new ProductService(productDAO, categoryDAO, logService);
            SaleService saleService = new SaleService(saleDAO, productDAO, logService);
            ReportService reportService = new ReportService(saleDAO, productDAO, logService);

            // 3. Delegate execution to Authentication (single shared UserDAO + Scanner across all menus).
            new Authentication(authService,
                    InputUtil.sharedScanner(),
                    new OwnerMenu(authService, productService, saleService, reportService, logService),
                    new CashierMenu(authService, productService, saleService),
                    new InventoryMenu(authService, productService),
                    new SupervisorMenu(authService, productService, saleService, reportService, logService)
            ).start();

            System.out.println(Ansi.dim("\n  ── Session ended. Database connections closed. ──"));
        } catch (DataAccessException e) {
            System.err.println(Ansi.color("  ✗ Fatal database error: " + e.getMessage(), Ansi.RED));
            System.exit(1);
        } catch (NoSuchElementException e) { // stdin closed (Ctrl+D / piped input ended)
            System.out.println(Ansi.dim("\n  Input closed. Exiting."));
        }
    }
}
