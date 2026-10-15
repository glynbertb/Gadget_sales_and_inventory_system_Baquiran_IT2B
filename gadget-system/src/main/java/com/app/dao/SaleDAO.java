package com.app.dao;

import com.app.model.DailySales;
import com.app.model.Sale;
import com.app.model.SaleItem;
import com.app.model.TopSeller;
import java.sql.Connection;
import java.util.List;
import java.util.Optional;

public interface SaleDAO {
    int insertSale(Connection c, Sale sale);
    int insertItem(Connection c, SaleItem item);
    void updateStatus(Connection c, int saleId, String status);
    void updateItem(Connection c, int saleItemId, int quantity, double subtotal);
    void deleteItem(Connection c, int saleItemId);
    void updateTotal(Connection c, int saleId, double total);

    Optional<Sale> findById(int saleId);
    Optional<Sale> findById(Connection c, int saleId);
    List<Sale> findRecent(int limit);
    List<Sale> findByUser(int userId);
    List<SaleItem> findItems(int saleId);
    List<SaleItem> findItems(Connection c, int saleId);

    // reporting aggregates (Completed sales only unless stated)
    double totalSalesAmount();
    /** Sum of Completed Cash sales whose sale_date starts with the given "yyyy-MM-dd" prefix (for register audits). */
    double totalCashOnDate(String datePrefix);
    int countByStatus(String status);
    List<TopSeller> topSelling(int limit);
    /** Completed sales grouped by day (yyyy-MM-dd), most recent first, capped at 30 rows. */
    List<DailySales> salesByDate();
}
