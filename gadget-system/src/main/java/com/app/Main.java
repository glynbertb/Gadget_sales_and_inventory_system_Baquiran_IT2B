package com.app;

import com.app.dao.ActivityLogDAO;
import com.app.dao.ActivityLogDAOImpl;
import com.app.dao.CategoryDAO;
import com.app.dao.CategoryDAOImpl;
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
import com.app.util.Ansi;
import com.app.util.DataAccessException;
import com.app.util.DatabaseConfig;
import com.app.util.SchemaInitializer;
import com.app.view.AuthMenu;
import com.app.view.CashierMenu;
import com.app.view.InventoryMenu;
import com.app.view.OwnerMenu;
import com.app.view.SupervisorMenu;
import java.util.NoSuchElementException;

/** Composition root: wires DAOs -> services -> menus and starts the CLI. */
public final class Main {

    private Main() { }

    public static void main(String[] args) {
        try {

            System.out.println(Ansi.dim("  Database : ") + Ansi.color(DatabaseConfig.getDatabasePath().toString(), Ansi.CYAN));
            if (!DatabaseConfig.databaseFileExists()) {
                System.out.println(Ansi.color("  ⚠  That file does not exist yet — a NEW empty database will be created.", Ansi.YELLOW));
                System.out.println(Ansi.dim("     If you expected your existing Gadget.db, run from its folder or use -Dgadget.db=<full path>."));
            } else {
                System.out.println(Ansi.color("  ✓ Database ready.", Ansi.GREEN));
            }
            SchemaInitializer.ensureSchema(); // no-op when the tables already exist

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

            new AuthMenu(authService,
                    new OwnerMenu(authService, productService, saleService, reportService, logService),
                    new CashierMenu(authService, productService, saleService),
                    new InventoryMenu(authService, productService),
                    new SupervisorMenu(authService, productService, saleService, reportService, logService)
            ).run();

            System.out.println(Ansi.dim("\n  ── Session ended. Database connections closed. ──"));
        } catch (DataAccessException e) {
            System.err.println(Ansi.color("  ✗ Fatal database error: " + e.getMessage(), Ansi.RED));
            System.exit(1);
        } catch (NoSuchElementException e) { // stdin closed (Ctrl+D / piped input ended)
            System.out.println(Ansi.dim("\n  Input closed. Exiting."));
        }
    }

    private static String center(String s, int w) {
        int len = Ansi.strip(s).length();
        if (len >= w) return s;
        int pad = (w - len) / 2;
        return " ".repeat(pad) + s + " ".repeat(w - len - pad);
    }
}
