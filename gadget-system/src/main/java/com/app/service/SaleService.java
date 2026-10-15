package com.app.service;

import com.app.dao.ProductDAO;
import com.app.dao.SaleDAO;
import com.app.model.Product;
import com.app.model.Sale;
import com.app.model.SaleItem;
import com.app.model.User;
import com.app.util.FormatUtil;
import com.app.util.Tx;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Sales terminal (atomic checkout), void/cancellation and transaction history. */
public class SaleService {

    /** One requested line of the cart. */
    public record CartItem(int gadgetId, int quantity) { }

    private static final int HISTORY_LIMIT = 200;

    private final SaleDAO saleDAO;
    private final ProductDAO productDAO;
    private final ActivityLogService logService;

    public SaleService(SaleDAO saleDAO, ProductDAO productDAO, ActivityLogService logService) {
        this.saleDAO = saleDAO;
        this.productDAO = productDAO;
        this.logService = logService;
    }

    /**
     * Atomic checkout: sales row -> sale_items -> stock deduction (+auto 'Out of Stock') -> activity log.
     * Any failure rolls the entire transaction back, so stock and sales can never diverge.
     */
    public Sale checkout(User cashier, List<CartItem> cart, String paymentMethod) {
        AccessControl.require(cashier, User.ROLE_CASHIER);
        if (cart == null || cart.isEmpty()) throw new ServiceException("The cart is empty.");
        if (paymentMethod == null || !Arrays.asList(Sale.PAYMENT_METHODS).contains(paymentMethod)) {
            throw new ServiceException("Invalid payment method.");
        }

        Map<Integer, Integer> lines = new LinkedHashMap<>(); // merge duplicate gadgets
        for (CartItem ci : cart) {
            if (ci.quantity() <= 0) throw new ServiceException("Quantities must be greater than zero.");
            lines.merge(ci.gadgetId(), ci.quantity(), Integer::sum);
        }

        return Tx.execute(c -> {
            List<SaleItem> items = new ArrayList<>();
            double total = 0;

            // 1) validate stock inside the transaction (fresh data) and price every line
            for (Map.Entry<Integer, Integer> e : lines.entrySet()) {
                Product p = productDAO.findById(c, e.getKey())
                        .orElseThrow(() -> new ServiceException("Gadget #" + e.getKey() + " not found."));
                if (!Product.STATUS_AVAILABLE.equals(p.getStatus())) {
                    throw new ServiceException("'" + p.getGadgetName() + "' is not available for sale (" + p.getStatus() + ").");
                }
                if (p.getStockQuantity() < e.getValue()) {
                    throw new ServiceException("Insufficient stock for '" + p.getGadgetName() + "': requested "
                            + e.getValue() + ", available " + p.getStockQuantity() + ".");
                }
                SaleItem item = new SaleItem();
                item.setGadgetId(p.getGadgetId());
                item.setGadgetName(p.getGadgetName());
                item.setQuantity(e.getValue());
                item.setUnitPrice(p.getPrice());
                item.setSubtotal(FormatUtil.round2(p.getPrice() * e.getValue()));
                items.add(item);
                total += item.getSubtotal();
            }

            // 2) sales header
            Sale sale = new Sale();
            sale.setUserId(cashier.getUserId());
            sale.setCashierName(cashier.getName());
            sale.setSaleDate(FormatUtil.now());
            sale.setTotalAmount(FormatUtil.round2(total));
            sale.setPaymentMethod(paymentMethod);
            sale.setSaleStatus(Sale.STATUS_COMPLETED);
            sale.setSaleId(saleDAO.insertSale(c, sale));

            // 3) line items + guarded stock deduction
            for (SaleItem item : items) {
                item.setSaleId(sale.getSaleId());
                item.setSaleItemId(saleDAO.insertItem(c, item));
                if (!productDAO.deductStock(c, item.getGadgetId(), item.getQuantity())) {
                    throw new ServiceException("Stock for '" + item.getGadgetName() + "' changed during checkout. Please retry.");
                }
            }

            // 4) audit trail (same transaction)
            logService.log(c, cashier.getUserId(), "CREATE_SALE", "Sale #" + sale.getSaleId() + " completed: "
                    + items.size() + " line(s), total " + FormatUtil.money(sale.getTotalAmount()) + " via " + paymentMethod + ".");

            sale.setItems(items);
            return sale; // 5) commit happens in Tx.execute
        });
    }

    /** Void a Completed sale (Owner / Store Supervisor): restores stock and records the mandatory reason. */
    public void voidSale(User actor, int saleId, String reason) {
        AccessControl.require(actor, User.ROLE_SUPERVISOR, User.ROLE_OWNER);
        if (reason == null || reason.trim().isEmpty()) throw new ServiceException("A void reason is mandatory.");
        String why = reason.trim();

        Tx.run(c -> {
            Sale sale = saleDAO.findById(c, saleId).orElseThrow(() -> new ServiceException("Sale #" + saleId + " not found."));
            if (!Sale.STATUS_COMPLETED.equals(sale.getSaleStatus())) {
                throw new ServiceException("Sale #" + saleId + " is '" + sale.getSaleStatus() + "'; only Completed sales can be voided.");
            }
            saleDAO.updateStatus(c, saleId, Sale.STATUS_VOIDED);
            for (SaleItem item : saleDAO.findItems(c, saleId)) {
                productDAO.increaseStock(c, item.getGadgetId(), item.getQuantity());
            }
            logService.log(c, actor.getUserId(), "VOID_SALE", "Sale #" + saleId + " voided. Reason: " + why);
        });
    }

    /**
     * Return units of one sale line back to stock (Owner / Store Supervisor).
     * Shrinks the line and the sale total so reports stay accurate; a sale left
     * with no lines becomes "Returned". The shrinking line is what prevents the
     * same units from being returned twice — no extra table needed.
     */
    public void returnItem(User actor, int saleId, int gadgetId, int quantity, String reason) {
        AccessControl.require(actor, User.ROLE_SUPERVISOR, User.ROLE_OWNER);
        if (quantity <= 0) throw new ServiceException("Return quantity must be greater than zero.");
        if (reason == null || reason.trim().isEmpty()) throw new ServiceException("A return reason is mandatory.");
        String why = reason.trim();

        Tx.run(c -> {
            Sale sale = saleDAO.findById(c, saleId).orElseThrow(() -> new ServiceException("Sale #" + saleId + " not found."));
            if (!Sale.STATUS_COMPLETED.equals(sale.getSaleStatus())) {
                throw new ServiceException("Sale #" + saleId + " is '" + sale.getSaleStatus() + "'; only Completed sales accept returns.");
            }
            SaleItem line = saleDAO.findItems(c, saleId).stream()
                    .filter(i -> i.getGadgetId() == gadgetId)
                    .findFirst()
                    .orElseThrow(() -> new ServiceException("Gadget #" + gadgetId + " is not part of sale #" + saleId + "."));
            if (quantity > line.getQuantity()) {
                throw new ServiceException("Only " + line.getQuantity() + " unit(s) of '" + line.getGadgetName() + "' can still be returned.");
            }
            if (shrinkLine(c, sale, line, quantity)) {
                saleDAO.updateStatus(c, saleId, Sale.STATUS_RETURNED);
            }
            logService.log(c, actor.getUserId(), "RETURN_ITEM", "Sale #" + saleId + ": returned " + quantity
                    + " x '" + line.getGadgetName() + "'. Reason: " + why);
        });
    }

    /**
     * Exchange returned units for a different gadget in one transaction (Owner / Store Supervisor).
     * The old units go back to stock, the new units are sold at their current price,
     * and the sale total is adjusted by the difference. Any cash difference is settled
     * at the register (see register audit).
     */
    public void exchangeItem(User actor, int saleId, int returnGadgetId, int returnQuantity,
                             int newGadgetId, int newQuantity, String reason) {
        AccessControl.require(actor, User.ROLE_SUPERVISOR, User.ROLE_OWNER);
        if (returnQuantity <= 0 || newQuantity <= 0) throw new ServiceException("Quantities must be greater than zero.");
        if (returnGadgetId == newGadgetId) throw new ServiceException("Exchanging a gadget for itself changes nothing — use return or a new sale instead.");
        if (reason == null || reason.trim().isEmpty()) throw new ServiceException("An exchange reason is mandatory.");
        String why = reason.trim();

        Tx.run(c -> {
            Sale sale = saleDAO.findById(c, saleId).orElseThrow(() -> new ServiceException("Sale #" + saleId + " not found."));
            if (!Sale.STATUS_COMPLETED.equals(sale.getSaleStatus())) {
                throw new ServiceException("Sale #" + saleId + " is '" + sale.getSaleStatus() + "'; only Completed sales accept exchanges.");
            }
            SaleItem oldLine = saleDAO.findItems(c, saleId).stream()
                    .filter(i -> i.getGadgetId() == returnGadgetId)
                    .findFirst()
                    .orElseThrow(() -> new ServiceException("Gadget #" + returnGadgetId + " is not part of sale #" + saleId + "."));
            if (returnQuantity > oldLine.getQuantity()) {
                throw new ServiceException("Only " + oldLine.getQuantity() + " unit(s) of '" + oldLine.getGadgetName() + "' can still be exchanged.");
            }
            Product fresh = productDAO.findById(c, newGadgetId)
                    .orElseThrow(() -> new ServiceException("Gadget #" + newGadgetId + " not found."));
            if (!Product.STATUS_AVAILABLE.equals(fresh.getStatus())) {
                throw new ServiceException("'" + fresh.getGadgetName() + "' is not available for sale (" + fresh.getStatus() + ").");
            }
            if (!productDAO.deductStock(c, newGadgetId, newQuantity)) {
                throw new ServiceException("Insufficient stock for '" + fresh.getGadgetName() + "'.");
            }
            double refund = FormatUtil.round2(returnQuantity * oldLine.getUnitPrice());
            // Ignores the empty check: the replacement line below keeps the sale alive and Completed.
            shrinkLine(c, sale, oldLine, returnQuantity);

            SaleItem added = new SaleItem();
            added.setSaleId(saleId);
            added.setGadgetId(fresh.getGadgetId());
            added.setGadgetName(fresh.getGadgetName());
            added.setQuantity(newQuantity);
            added.setUnitPrice(fresh.getPrice());
            added.setSubtotal(FormatUtil.round2(fresh.getPrice() * newQuantity));
            saleDAO.insertItem(c, added);

            // reload the total after shrinkLine, then add the new line value
            double total = saleDAO.findById(c, saleId).orElseThrow().getTotalAmount();
            saleDAO.updateTotal(c, saleId, FormatUtil.round2(total + added.getSubtotal()));
            logService.log(c, actor.getUserId(), "EXCHANGE_ITEM", "Sale #" + saleId + ": exchanged " + returnQuantity
                    + " x '" + oldLine.getGadgetName() + "' (" + FormatUtil.money(refund) + ") for " + newQuantity
                    + " x '" + fresh.getGadgetName() + "' (" + FormatUtil.money(added.getSubtotal()) + "). Reason: " + why);
        });
    }

    /** Restores stock, shrinks (or removes) the line and lowers the sale total. Returns true when no lines remain. */
    private boolean shrinkLine(java.sql.Connection c, Sale sale, SaleItem line, int quantity) {
        productDAO.increaseStock(c, line.getGadgetId(), quantity);
        int left = line.getQuantity() - quantity;
        if (left == 0) {
            saleDAO.deleteItem(c, line.getSaleItemId());
        } else {
            saleDAO.updateItem(c, line.getSaleItemId(), left, FormatUtil.round2(left * line.getUnitPrice()));
        }
        saleDAO.updateTotal(c, sale.getSaleId(),
                FormatUtil.round2(sale.getTotalAmount() - FormatUtil.round2(quantity * line.getUnitPrice())));
        return saleDAO.findItems(c, sale.getSaleId()).isEmpty();
    }

    // ------------------------------------------------------------------ queries

    /** Owner/Supervisor: all recent sales. Cashier: only their own transactions. */
    public List<Sale> listSales(User actor) {
        AccessControl.require(actor, User.ROLE_OWNER, User.ROLE_SUPERVISOR, User.ROLE_CASHIER);
        return User.ROLE_CASHIER.equals(actor.getRole())
                ? saleDAO.findByUser(actor.getUserId())
                : saleDAO.findRecent(HISTORY_LIMIT);
    }

    public Sale getSaleDetails(User actor, int saleId) {
        AccessControl.require(actor, User.ROLE_OWNER, User.ROLE_SUPERVISOR, User.ROLE_CASHIER);
        Sale s = saleDAO.findById(saleId).orElseThrow(() -> new ServiceException("Sale #" + saleId + " not found."));
        if (User.ROLE_CASHIER.equals(actor.getRole()) && s.getUserId() != actor.getUserId()) {
            throw new ServiceException("Access denied: you can only view your own transactions.");
        }
        s.setItems(saleDAO.findItems(saleId));
        return s;
    }
}
