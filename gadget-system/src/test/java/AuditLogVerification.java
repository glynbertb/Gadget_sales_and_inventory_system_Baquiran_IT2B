import com.gadget_sale_inventory_system.dao.*;
import com.gadget_sale_inventory_system.service.*;
import com.gadget_sale_inventory_system.model.*;
import java.sql.*;
import java.util.*;
public class AuditLogVerification {
    static int passed=0;
    static void check(boolean ok,String label) {
        if(!ok)throw new AssertionError(label);
        System.out.println("PASS "+label);
        passed++;
    }
    static int count(String table)throws Exception {
        try(Connection c=DatabaseConnection.getConnection();Statement s=c.createStatement();ResultSet r=s.executeQuery("SELECT COUNT(*) FROM "+table)) {
            return r.getInt(1);
        }
    }
    static void sql(String sql)throws Exception {
        try(Connection c=DatabaseConnection.getConnection();Statement s=c.createStatement()) {
            s.execute(sql);
        }
    }
    public static void main(String[] args)throws Exception {
        String scenario = args.length == 0 ? "fresh" : args[0];
        if (!scenario.equals("fresh")) {
            sql("CREATE TABLE users (user_id INTEGER PRIMARY KEY AUTOINCREMENT, name TEXT NOT NULL, username TEXT NOT NULL UNIQUE, password TEXT NOT NULL, role TEXT NOT NULL, status TEXT NOT NULL DEFAULT 'Active')");
            boolean original = scenario.equals("legacy");
            String oldTime = original ? "date_time" : "timestamp";
            String reference = original ? " REFERENCES users(user_id)" : "";
            sql("CREATE TABLE activity_logs (log_id INTEGER PRIMARY KEY AUTOINCREMENT, user_id INTEGER NOT NULL" + reference
                    + ", action TEXT NOT NULL, description TEXT, " + oldTime + " TEXT NOT NULL)");
            sql("INSERT INTO users VALUES (1,'Legacy','legacy','unused','Owner','Active')");
            sql("INSERT INTO activity_logs VALUES (7,1,'USER_APPROVE','Legacy approval','2025-01-01 10:00:00')");
            if (scenario.equals("orphan")) {
                sql("DELETE FROM users WHERE user_id=1");
                boolean rejected = false;
                try { DatabaseInitializer.initialize(); } catch (RuntimeException expected) { rejected = true; }
                check(rejected && count("activity_logs") == 1, "Orphaned legacy logs reject migration without data loss");
                try (Connection c = DatabaseConnection.getConnection(); Statement st = c.createStatement();
                        ResultSet rs = st.executeQuery("SELECT user_id FROM users")) {
                    check(!rs.next(), "Failed migration rolls back parent column rename too");
                }
                System.out.println("TOTAL " + passed + " checks passed");
                return;
            }
            sql("INSERT INTO activity_logs VALUES (90,1,'TEST','Cleared entry','2025-01-01 10:00:00')");
            sql("DELETE FROM activity_logs WHERE log_id=90");
            DatabaseInitializer.initialize();
            ActivityLog migrated = new ActivityLogDao().getAllLogs().get(0);
            check(migrated.getId()==7 && migrated.getUserId()==1 && migrated.getActionType().equals("USER_APPROVED")
                    && migrated.getTimestamp().equals("2025-01-01 10:00:00"), "Migration preserves history and normalizes action names");
            new ActivityLogDao().logAction(1,"TEST_SEQUENCE","Post-migration entry");
            check(new ActivityLogDao().getAllLogs().stream().mapToInt(ActivityLog::getId).max().orElse(0)>90,
                    "Migration preserves audit ID high-water mark");
            sql("DELETE FROM users WHERE id=1");
            check(count("activity_logs")==0, "Migrated audit rows cascade when their user is deleted");
        }
        DatabaseInitializer.initialize();
        check(count("activity_logs")==0,"Startup creates empty activity_logs");
        try (Connection c = DatabaseConnection.getConnection(); Statement st = c.createStatement()) {
            Set<String> columns = new HashSet<>();
            try (ResultSet rs = st.executeQuery("PRAGMA table_info(activity_logs)")) {
                while (rs.next()) columns.add(rs.getString("name"));
            }
            check(columns.equals(Set.of("id", "user_id", "action_type", "description", "timestamp")),
                    "Audit columns exactly match checklist");
            boolean cascade = false;
            try (ResultSet rs = st.executeQuery("PRAGMA foreign_key_list(activity_logs)")) {
                while (rs.next()) cascade |= rs.getString("table").equals("users") && rs.getString("to").equals("id")
                        && rs.getString("from").equals("user_id") && rs.getString("on_delete").equals("CASCADE");
            }
            check(cascade, "Audit foreign key references users(id) ON DELETE CASCADE");
        }
        ActivityLogDao dao=new ActivityLogDao();
        boolean invalidActor = false;
        try { dao.logAction(Integer.MAX_VALUE, "TEST_INVALID", "No such user"); }
        catch (RuntimeException expected) { invalidActor = true; }
        check(invalidActor && count("activity_logs")==0, "Foreign key rejects logs for unknown users");
        ActivityLogService logs=new ActivityLogService(dao);
        AuthService auth=new AuthService(new UserDAOImpl(),logs);
        ProductService products=new ProductService(new ProductDAOImpl(),new CategoryDAOImpl(),logs);
        SaleService sales=new SaleService(new SaleDAOImpl(),new ProductDAOImpl(),logs);
        User owner=auth.register("Test Owner","test_owner","TestPass123");
        check(count("activity_logs")==1,"Registration creates log");
        User pending=auth.register("Test Pending","test_pending","TestPass123",User.ROLE_CASHIER);
        boolean blocked=false;
        try {
            auth.login("test_pending","TestPass123");
        }
        catch(ServiceException e) {
            blocked=true;
        }
        check(blocked,"Pending login blocked");
        auth.approveUser(owner,pending.getUserId());
        ActivityLog approval=dao.findRecent(1).get(0);
        check(approval.getActionType().equals("USER_APPROVED")&&approval.getUserId()==owner.getUserId()&&approval.getDescription().contains("User ID "+pending.getUserId()),"Approval log records actor and target (actual type USER_APPROVED)");
        User cashier=auth.login("test_pending","TestPass123");
        check(dao.findRecent(1).get(0).getActionType().equals("USER_LOGIN"),"Approved user can log in; login logged");
        auth.updateUserRoleAndStatus(owner,pending.getUserId(),User.ROLE_CASHIER,User.STATUS_ACTIVE);
        check(dao.findRecent(1).get(0).getActionType().equals("USER_UPDATED"),"User update logged (actual type USER_UPDATED)");
        auth.updateUser(owner, pending.getUserId(), "Test Cashier", User.ROLE_CASHIER);
        check(new UserDAOImpl().findById(pending.getUserId()).orElseThrow().getName().equals("Test Cashier"),
                "Profile update works with migrated users(id)");
        auth.setUserStatus(owner, pending.getUserId(), false);
        blocked = false;
        try { auth.login("test_pending", "TestPass123"); } catch (ServiceException expected) { blocked = true; }
        check(blocked, "Deactivation still blocks login with users(id)");
        auth.resetPassword(owner, pending.getUserId(), "ChangedPass123");
        auth.setUserStatus(owner, pending.getUserId(), true);
        check(auth.login("test_pending", "ChangedPass123").getUserId()==pending.getUserId(),
                "Password reset and reactivation work with users(id)");
        Category cat=products.addCategory(owner,"Test Category","test");
        Product product=products.addProduct(owner,"Test Phone",cat.getCategoryId(),100,10);
        Sale sale=sales.checkout(cashier,List.of(new SaleService.CartItem(product.getGadgetId(),2)),"Cash",10000.0);
        check(sale.getTotalAmount()==200&&products.getProduct(product.getGadgetId()).getStockQuantity()==8&&dao.findRecent(1).get(0).getActionType().equals("CREATE_SALE"),"Checkout records sale, deducts stock, creates log");
        int beforeLogs=count("activity_logs"),beforeSales=count("sales");
        blocked=false;
        try {
            sales.checkout(cashier,List.of(new SaleService.CartItem(product.getGadgetId(),100)),"Cash",10000.0);
        }
        catch(ServiceException e) {
            blocked=true;
        }
        check(blocked&&count("sales")==beforeSales&&count("activity_logs")==beforeLogs&&products.getProduct(product.getGadgetId()).getStockQuantity()==8,"Insufficient-stock checkout leaves sale, stock, logs unchanged");
        check(!logs.recent(owner,500).isEmpty(),"Owner can read logs");
        blocked=false;
        try {
            logs.recent(cashier,500);
        }
        catch(ServiceException e) {
            blocked=true;
        }
        check(blocked,"Cashier denied audit-log access");
        String quoted="O'Brien '; DROP TABLE users; --";
        logs.logAction(owner.getUserId(),"TEST_QUOTES",quoted);
        check(dao.findRecent(1).get(0).getDescription().equals(quoted)&&count("users")==2,"Parameterized logging preserves quotes and SQL-like text");
        sql("CREATE TRIGGER fail_audit BEFORE INSERT ON activity_logs BEGIN SELECT RAISE(ABORT, 'test audit failure'); END");
        beforeLogs=count("activity_logs");
        beforeSales=count("sales");
        blocked=false;
        try {
            sales.checkout(cashier,List.of(new SaleService.CartItem(product.getGadgetId(),1)),"Cash",10000.0);
        }
        catch(RuntimeException e) {
            blocked=true;
        }
        check(blocked&&count("sales")==beforeSales&&count("activity_logs")==beforeLogs&&products.getProduct(product.getGadgetId()).getStockQuantity()==8,"Logging failure rolls back checkout and stock");
        blocked=false;
        try {
            logs.logAction(owner.getUserId(),"TEST_FAILURE","Expected failure");
        }
        catch(RuntimeException e) {
            blocked=true;
        }
        check(blocked&&count("activity_logs")==beforeLogs,"Standalone logging failure is visible");
        sql("DROP TRIGGER fail_audit");
        sales.voidSale(owner, sale.getSaleId(), "Verification void");
        check(dao.findRecent(1).get(0).getActionType().equals("VOID_SALE")
                && products.getProduct(product.getGadgetId()).getStockQuantity()==10, "Lifecycle void logs action and restores stock");
        Set<String> recorded = new HashSet<>();
        for (ActivityLog log : dao.getAllLogs()) recorded.add(log.getActionType());
        check(recorded.containsAll(List.of("USER_LOGIN", "USER_APPROVED", "CREATE_SALE", "VOID_SALE")),
                "All four mandatory event categories persist together");
        User disposable=auth.createStaff(owner,"Disposable","disposable","TestPass123",User.ROLE_INVENTORY);
        auth.login("disposable","TestPass123");
        check(dao.findRecent(500).stream().anyMatch(l->l.getUserId()==disposable.getUserId()),"Disposable user has audit history");
        auth.deleteUser(owner,disposable.getUserId());
        check(dao.getLogsByUserId(disposable.getUserId()).isEmpty(),"Account deletion cascades its audit rows");
        check(dao.findRecent(1).get(0).getActionType().equals("USER_DELETE")
                && dao.findRecent(1).get(0).getUserId()==owner.getUserId(), "User deletion receipt remains under admin ID");
        DatabaseInitializer.initialize();
        check(count("activity_logs")>0,"Repeat initialization preserves existing logs");
        logs.logAction(owner.getUserId(),"TEST_ORDER","Inserted later");
        sql("UPDATE activity_logs SET timestamp='2000-01-01 00:00:00' WHERE id=(SELECT MAX(id) FROM activity_logs)");
        check(!dao.findRecent(1).get(0).getActionType().equals("TEST_ORDER"),"Timestamp takes precedence over ID ordering");
        check(dao.getAllLogs().size()==count("activity_logs"),"getAllLogs returns every row");
        check(dao.getLogsByUserId(owner.getUserId()).stream().allMatch(l->l.getUserId()==owner.getUserId()),"Filter returns only selected actor");
        check(dao.getLogsByUserId(Integer.MAX_VALUE).isEmpty(),"Unknown user filter returns empty list");
        blocked=false;
        try {
            logs.getAllLogs(cashier);
        }
        catch(ServiceException e) {
            blocked=true;
        }
        check(blocked,"Cashier cannot read all logs");
        User supervisor=auth.createStaff(owner,"Supervisor","supervisor","TestPass123",User.ROLE_SUPERVISOR);
        check(!logs.getAllLogs(supervisor).isEmpty(),"Supervisor retains read-only audit access");
        beforeLogs=count("activity_logs");
        blocked=false;
        try {
            logs.clearLogs(supervisor,"CLEAR");
        }
        catch(ServiceException e) {
            blocked=true;
        }
        check(blocked&&count("activity_logs")==beforeLogs,"Supervisor cannot clear logs");
        blocked=false;
        try {
            logs.clearLogs(owner,"wrong");
        }
        catch(ServiceException e) {
            blocked=true;
        }
        check(blocked&&count("activity_logs")==beforeLogs,"Invalid confirmation preserves history");
        sql("CREATE TRIGGER fail_receipt BEFORE INSERT ON activity_logs WHEN NEW.action_type='LOGS_CLEARED' BEGIN SELECT RAISE(ABORT, 'test receipt failure'); END");
        blocked=false;
        try {
            logs.clearLogs(owner,"CLEAR");
        }
        catch(RuntimeException e) {
            blocked=true;
        }
        check(blocked&&count("activity_logs")==beforeLogs,"Failed clear receipt rolls back historical deletion");
        sql("DROP TRIGGER fail_receipt");
        int priorId=dao.getAllLogs().stream().mapToInt(ActivityLog::getId).max().orElse(0);
        check(logs.clearLogs(owner,"CLEAR")==beforeLogs&&count("activity_logs")==1,"Clear removes history and leaves one receipt");
        ActivityLog receipt=dao.getAllLogs().get(0);
        check(receipt.getActionType().equals("LOGS_CLEARED")&&receipt.getUserId()==owner.getUserId()&&receipt.getId()>priorId,"Clear receipt preserves actor and increasing IDs");
        for(int i=0;i<505;i++)dao.logAction(owner.getUserId(),"BULK_TEST","Entry "+i);
        check(dao.getAllLogs().size()==506&&dao.getLogsByUserId(owner.getUserId()).size()==506,"All-log and filtered retrieval exceed old 500-row cap");
        System.out.println("TOTAL "+passed+" checks passed");
    }
}
