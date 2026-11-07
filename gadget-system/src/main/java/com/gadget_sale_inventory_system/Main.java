package com.gadget_sale_inventory_system;

import com.gadget_sale_inventory_system.dao.ActivityLogDao;
import com.gadget_sale_inventory_system.dao.CategoryDAO;
import com.gadget_sale_inventory_system.dao.CategoryDAOImpl;
import com.gadget_sale_inventory_system.dao.DatabaseConnection;
import com.gadget_sale_inventory_system.dao.DatabaseInitializer;
import com.gadget_sale_inventory_system.dao.ProductDAO;
import com.gadget_sale_inventory_system.dao.ProductDAOImpl;
import com.gadget_sale_inventory_system.dao.SaleDAO;
import com.gadget_sale_inventory_system.dao.SaleDAOImpl;
import com.gadget_sale_inventory_system.dao.UserDAO;
import com.gadget_sale_inventory_system.dao.UserDAOImpl;
import com.gadget_sale_inventory_system.service.ActivityLogService;
import com.gadget_sale_inventory_system.service.AuthService;
import com.gadget_sale_inventory_system.service.ProductService;
import com.gadget_sale_inventory_system.service.ReportService;
import com.gadget_sale_inventory_system.service.SaleService;
import com.gadget_sale_inventory_system.ui.Authentication;
import com.gadget_sale_inventory_system.util.ConsoleStyle;
import com.gadget_sale_inventory_system.util.DataAccessException;
import com.gadget_sale_inventory_system.util.InputUtil;
import com.gadget_sale_inventory_system.ui.CashierMenu;
import com.gadget_sale_inventory_system.ui.InventoryMenu;
import com.gadget_sale_inventory_system.ui.OwnerMenu;
import com.gadget_sale_inventory_system.ui.SupervisorMenu;
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

            System.out.println(ConsoleStyle.dim("  Database : ") + ConsoleStyle.color(DatabaseConnection.getDatabasePath().toString(), ConsoleStyle.CYAN));
            System.out.println(ConsoleStyle.color("  ✓ Database ready.", ConsoleStyle.GREEN));

            // 2. Instantiate DAOs (single shared instances) & utilities.
            UserDAO userDAO = new UserDAOImpl();
            CategoryDAO categoryDAO = new CategoryDAOImpl();
            ProductDAO productDAO = new ProductDAOImpl();
            SaleDAO saleDAO = new SaleDAOImpl();
            ActivityLogDao logDAO = new ActivityLogDao();

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

            System.out.println(ConsoleStyle.dim("\n  ── Session ended. Database connections closed. ──"));
        } catch (DataAccessException e) {
            System.err.println(ConsoleStyle.color("  ✗ Fatal database error: " + e.getMessage(), ConsoleStyle.RED));
            System.exit(1);
        } catch (NoSuchElementException e) { // stdin closed (Ctrl+D / piped input ended)
            System.out.println(ConsoleStyle.dim("\n  Input closed. Exiting."));
        }
    }
}
