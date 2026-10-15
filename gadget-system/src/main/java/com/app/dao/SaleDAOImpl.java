package com.app.dao;

import com.app.model.DailySales;
import com.app.model.Sale;
import com.app.model.SaleItem;
import com.app.model.TopSeller;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;

public class SaleDAOImpl implements SaleDAO {

    private static final String SALE_SELECT =
            "SELECT s.sale_id, s.user_id, u.name AS cashier_name, s.sale_date, s.total_amount, s.payment_method, s.sale_status " +
            "FROM sales s LEFT JOIN users u ON u.user_id = s.user_id";

    private static final String ITEM_SELECT =
            "SELECT si.sale_item_id, si.sale_id, si.gadget_id, p.gadget_name, si.quantity, si.unit_price, si.subtotal " +
            "FROM sale_items si LEFT JOIN products p ON p.gadget_id = si.gadget_id " +
            "WHERE si.sale_id = ? ORDER BY si.sale_item_id";

    private static Sale mapSale(ResultSet rs) throws SQLException {
        Sale s = new Sale();
        s.setSaleId(rs.getInt("sale_id"));
        s.setUserId(rs.getInt("user_id"));
        s.setCashierName(rs.getString("cashier_name"));
        s.setSaleDate(rs.getString("sale_date"));
        s.setTotalAmount(rs.getDouble("total_amount"));
        s.setPaymentMethod(rs.getString("payment_method"));
        s.setSaleStatus(rs.getString("sale_status"));
        return s;
    }

    private static SaleItem mapItem(ResultSet rs) throws SQLException {
        SaleItem i = new SaleItem();
        i.setSaleItemId(rs.getInt("sale_item_id"));
        i.setSaleId(rs.getInt("sale_id"));
        i.setGadgetId(rs.getInt("gadget_id"));
        i.setGadgetName(rs.getString("gadget_name"));
        i.setQuantity(rs.getInt("quantity"));
        i.setUnitPrice(rs.getDouble("unit_price"));
        i.setSubtotal(rs.getDouble("subtotal"));
        return i;
    }

    @Override public int insertSale(Connection c, Sale s) {
        return JdbcHelper.insert(c,
                "INSERT INTO sales (user_id, sale_date, total_amount, payment_method, sale_status) VALUES (?, ?, ?, ?, ?)", ps -> {
                    ps.setInt(1, s.getUserId());
                    ps.setString(2, s.getSaleDate());
                    ps.setDouble(3, s.getTotalAmount());
                    ps.setString(4, s.getPaymentMethod());
                    ps.setString(5, s.getSaleStatus());
                });
    }

    @Override public int insertItem(Connection c, SaleItem i) {
        return JdbcHelper.insert(c,
                "INSERT INTO sale_items (sale_id, gadget_id, quantity, unit_price, subtotal) VALUES (?, ?, ?, ?, ?)", ps -> {
                    ps.setInt(1, i.getSaleId());
                    ps.setInt(2, i.getGadgetId());
                    ps.setInt(3, i.getQuantity());
                    ps.setDouble(4, i.getUnitPrice());
                    ps.setDouble(5, i.getSubtotal());
                });
    }

    @Override public void updateStatus(Connection c, int saleId, String status) {
        JdbcHelper.update(c, "UPDATE sales SET sale_status = ? WHERE sale_id = ?", ps -> { ps.setString(1, status); ps.setInt(2, saleId); });
    }

    @Override public void updateItem(Connection c, int saleItemId, int quantity, double subtotal) {
        JdbcHelper.update(c, "UPDATE sale_items SET quantity = ?, subtotal = ? WHERE sale_item_id = ?",
                ps -> { ps.setInt(1, quantity); ps.setDouble(2, subtotal); ps.setInt(3, saleItemId); });
    }

    @Override public void deleteItem(Connection c, int saleItemId) {
        JdbcHelper.update(c, "DELETE FROM sale_items WHERE sale_item_id = ?", ps -> ps.setInt(1, saleItemId));
    }

    @Override public void updateTotal(Connection c, int saleId, double total) {
        JdbcHelper.update(c, "UPDATE sales SET total_amount = ? WHERE sale_id = ?", ps -> { ps.setDouble(1, total); ps.setInt(2, saleId); });
    }

    @Override public Optional<Sale> findById(int saleId) {
        return JdbcHelper.queryOne(SALE_SELECT + " WHERE s.sale_id = ?", ps -> ps.setInt(1, saleId), SaleDAOImpl::mapSale);
    }

    @Override public Optional<Sale> findById(Connection c, int saleId) {
        return JdbcHelper.queryOne(c, SALE_SELECT + " WHERE s.sale_id = ?", ps -> ps.setInt(1, saleId), SaleDAOImpl::mapSale);
    }

    @Override public List<Sale> findRecent(int limit) {
        return JdbcHelper.query(SALE_SELECT + " ORDER BY s.sale_id DESC LIMIT ?", ps -> ps.setInt(1, limit), SaleDAOImpl::mapSale);
    }

    @Override public List<Sale> findByUser(int userId) {
        return JdbcHelper.query(SALE_SELECT + " WHERE s.user_id = ? ORDER BY s.sale_id DESC", ps -> ps.setInt(1, userId), SaleDAOImpl::mapSale);
    }

    @Override public List<SaleItem> findItems(int saleId) {
        return JdbcHelper.query(ITEM_SELECT, ps -> ps.setInt(1, saleId), SaleDAOImpl::mapItem);
    }

    @Override public List<SaleItem> findItems(Connection c, int saleId) {
        return JdbcHelper.query(c, ITEM_SELECT, ps -> ps.setInt(1, saleId), SaleDAOImpl::mapItem);
    }

    @Override public double totalSalesAmount() {
        return JdbcHelper.queryOne("SELECT COALESCE(SUM(total_amount), 0) FROM sales WHERE sale_status = 'Completed'",
                null, rs -> rs.getDouble(1)).orElse(0.0);
    }

    @Override public double totalCashOnDate(String datePrefix) {
        return JdbcHelper.queryOne(
                "SELECT COALESCE(SUM(total_amount), 0) FROM sales " +
                "WHERE sale_status = 'Completed' AND payment_method = 'Cash' AND sale_date LIKE ?",
                ps -> ps.setString(1, datePrefix + "%"), rs -> rs.getDouble(1)).orElse(0.0);
    }

    @Override public int countByStatus(String status) {
        return JdbcHelper.queryOne("SELECT COUNT(*) FROM sales WHERE sale_status = ?",
                ps -> ps.setString(1, status), rs -> rs.getInt(1)).orElse(0);
    }

    @Override public List<TopSeller> topSelling(int limit) {
        return JdbcHelper.query(
                "SELECT p.gadget_name AS name, SUM(si.quantity) AS qty, SUM(si.subtotal) AS revenue " +
                "FROM sale_items si " +
                "JOIN sales s ON s.sale_id = si.sale_id " +
                "JOIN products p ON p.gadget_id = si.gadget_id " +
                "WHERE s.sale_status = 'Completed' " +
                "GROUP BY si.gadget_id, p.gadget_name ORDER BY qty DESC, revenue DESC LIMIT ?",
                ps -> ps.setInt(1, limit),
                rs -> new TopSeller(rs.getString("name"), rs.getInt("qty"), rs.getDouble("revenue")));
    }

    @Override public List<DailySales> salesByDate() {
        return JdbcHelper.query(
                "SELECT substr(sale_date, 1, 10) AS day, COUNT(*) AS tx, COALESCE(SUM(total_amount), 0) AS total " +
                "FROM sales WHERE sale_status = 'Completed' GROUP BY day ORDER BY day DESC LIMIT 30",
                null,
                rs -> new DailySales(rs.getString("day"), rs.getInt("tx"), rs.getDouble("total")));
    }
}
