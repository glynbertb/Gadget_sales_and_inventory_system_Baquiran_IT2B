package com.app.service;

import com.app.dao.CategoryDAO;
import com.app.dao.ProductDAO;
import com.app.model.Category;
import com.app.model.Product;
import com.app.model.User;
import com.app.util.FormatUtil;
import com.app.util.Tx;
import java.util.Arrays;
import java.util.List;

/** Category + gadget (product) management and stock control. */
public class ProductService {

    public static final int LOW_STOCK_THRESHOLD = 5;

    private final ProductDAO productDAO;
    private final CategoryDAO categoryDAO;
    private final ActivityLogService logService;

    public ProductService(ProductDAO productDAO, CategoryDAO categoryDAO, ActivityLogService logService) {
        this.productDAO = productDAO;
        this.categoryDAO = categoryDAO;
        this.logService = logService;
    }

    // ================================================================ categories

    public List<Category> listCategories() { return categoryDAO.findAll(); }

    public Category addCategory(User actor, String name, String description) {
        AccessControl.require(actor, User.ROLE_OWNER);
        String n = required(name, "Category name");
        if (categoryDAO.existsByName(n)) throw new ServiceException("Category '" + n + "' already exists.");
        Category cat = new Category(0, n, blankToNull(description));
        int id = Tx.execute(c -> {
            int newId = categoryDAO.insert(c, cat);
            logService.log(c, actor.getUserId(), "CATEGORY_ADD", "Added category #" + newId + " '" + n + "'.");
            return newId;
        });
        cat.setCategoryId(id);
        return cat;
    }

    public void updateCategory(User actor, int id, String name, String description) {
        AccessControl.require(actor, User.ROLE_OWNER);
        Category cat = categoryDAO.findById(id).orElseThrow(() -> new ServiceException("Category #" + id + " not found."));
        String n = required(name, "Category name");
        if (!n.equalsIgnoreCase(cat.getCategoryName()) && categoryDAO.existsByName(n)) {
            throw new ServiceException("Category '" + n + "' already exists.");
        }
        cat.setCategoryName(n);
        cat.setDescription(blankToNull(description));
        Tx.run(c -> {
            categoryDAO.update(c, cat);
            logService.log(c, actor.getUserId(), "CATEGORY_UPDATE", "Updated category #" + id + " -> '" + n + "'.");
        });
    }

    /** Only categories without any gadgets can be removed. */
    public void deleteCategory(User actor, int id) {
        AccessControl.require(actor, User.ROLE_OWNER);
        Category cat = categoryDAO.findById(id).orElseThrow(() -> new ServiceException("Category #" + id + " not found."));
        if (categoryDAO.isInUse(id)) throw new ServiceException("Category is in use by existing gadgets and cannot be removed.");
        Tx.run(c -> {
            categoryDAO.delete(c, id);
            logService.log(c, actor.getUserId(), "CATEGORY_DELETE", "Removed category #" + id + " '" + cat.getCategoryName() + "'.");
        });
    }

    // ================================================================ gadgets (read)

    public List<Product> listProducts() { return productDAO.findAll(); }
    public List<Product> listAvailable() { return productDAO.findByStatus(Product.STATUS_AVAILABLE); }
    public List<Product> lowStock() { return productDAO.findLowStock(LOW_STOCK_THRESHOLD); }

    public List<Product> search(String keyword) {
        return productDAO.search(required(keyword, "Search keyword"));
    }

    public Product getProduct(int id) {
        return productDAO.findById(id).orElseThrow(() -> new ServiceException("Gadget #" + id + " not found."));
    }

    // ================================================================ gadgets (write)

    public Product addProduct(User actor, String name, int categoryId, double price, int quantity) {
        AccessControl.require(actor, User.ROLE_OWNER, User.ROLE_INVENTORY);
        String n = required(name, "Gadget name");
        if (price <= 0) throw new ServiceException("Price must be greater than zero.");
        if (quantity < 0) throw new ServiceException("Quantity cannot be negative.");
        categoryDAO.findById(categoryId).orElseThrow(() -> new ServiceException("Category #" + categoryId + " not found."));

        Product p = new Product();
        p.setGadgetName(n);
        p.setCategoryId(categoryId);
        p.setPrice(FormatUtil.round2(price));
        p.setStockQuantity(quantity);
        p.setStatus(quantity > 0 ? Product.STATUS_AVAILABLE : Product.STATUS_OUT_OF_STOCK);
        int id = Tx.execute(c -> {
            int newId = productDAO.insert(c, p);
            logService.log(c, actor.getUserId(), "PRODUCT_ADD",
                    "Added gadget #" + newId + " '" + n + "' (qty " + quantity + ", price " + FormatUtil.money(p.getPrice()) + ").");
            return newId;
        });
        p.setGadgetId(id);
        return p;
    }

    public void updateProductInfo(User actor, int id, String name, int categoryId, double price) {
        AccessControl.require(actor, User.ROLE_OWNER);
        Product p = getProduct(id);
        String n = required(name, "Gadget name");
        if (price <= 0) throw new ServiceException("Price must be greater than zero.");
        categoryDAO.findById(categoryId).orElseThrow(() -> new ServiceException("Category #" + categoryId + " not found."));
        double oldPrice = p.getPrice();
        p.setGadgetName(n);
        p.setCategoryId(categoryId);
        p.setPrice(FormatUtil.round2(price));
        Tx.run(c -> {
            productDAO.updateInfo(c, p);
            logService.log(c, actor.getUserId(), "PRODUCT_UPDATE", "Updated gadget #" + id + " '" + n + "' (price "
                    + FormatUtil.money(oldPrice) + " -> " + FormatUtil.money(p.getPrice()) + ").");
        });
    }

    /** Records newly received stock (adds to the current quantity).
     *  Rule: Out of Stock / Discontinued must have stock 0, so any receipt revives the gadget to Available. */
    public void receiveStock(User actor, int id, int quantityToAdd) {
        AccessControl.require(actor, User.ROLE_OWNER, User.ROLE_INVENTORY);
        Product p = getProduct(id);
        if (quantityToAdd <= 0) throw new ServiceException("Quantity to add must be greater than zero.");
        if (Product.STATUS_DISCONTINUED.equals(p.getStatus())) {
            // Receiving for a discontinued item automatically re-activates it (stock 0 -> >0 must be Available)
            // If you truly want it discontinued, don't add stock.
        }
        Tx.run(c -> {
            productDAO.increaseStock(c, id, quantityToAdd);
            logService.log(c, actor.getUserId(), "STOCK_RECEIVE",
                    "Received " + quantityToAdd + " unit(s) of #" + id + " '" + p.getGadgetName() + "' (was " + p.getStockQuantity() + ").");
        });
    }

    /** Overwrites the stock quantity (e.g. correction) and keeps the status consistent. */
    public void setStock(User actor, int id, int newQuantity) {
        AccessControl.require(actor, User.ROLE_OWNER, User.ROLE_INVENTORY);
        Product p = getProduct(id);
        if (newQuantity < 0) throw new ServiceException("Quantity cannot be negative.");
        applyStock(actor, p, newQuantity, "STOCK_UPDATE", "Stock of #" + id + " '" + p.getGadgetName() + "' changed "
                + p.getStockQuantity() + " -> " + newQuantity + ".");
    }

    public void updateStatus(User actor, int id, String newStatus) {
        AccessControl.require(actor, User.ROLE_OWNER, User.ROLE_INVENTORY);
        Product p = getProduct(id);
        if (newStatus == null || !Arrays.asList(Product.STATUSES).contains(newStatus)) throw new ServiceException("Invalid status.");
        if (Product.STATUS_AVAILABLE.equals(newStatus) && p.getStockQuantity() <= 0) {
            throw new ServiceException("Cannot mark a gadget with zero stock as Available. Add stock first.");
        }
        // Rule: Out of Stock and Discontinued must have stock 0 -> zero the stock automatically
        if (Product.STATUS_OUT_OF_STOCK.equals(newStatus) || Product.STATUS_DISCONTINUED.equals(newStatus)) {
            if (p.getStockQuantity() != 0 || !newStatus.equals(p.getStatus())) {
                Tx.run(c -> {
                    productDAO.updateStock(c, id, 0, newStatus);
                    logService.log(c, actor.getUserId(), "PRODUCT_STATUS",
                            "Gadget #" + id + " '" + p.getGadgetName() + "' status " + p.getStatus() + " -> " + newStatus + " (stock zeroed).");
                });
                return;
            }
        }
        Tx.run(c -> {
            productDAO.updateStatus(c, id, newStatus);
            logService.log(c, actor.getUserId(), "PRODUCT_STATUS",
                    "Gadget #" + id + " '" + p.getGadgetName() + "' status " + p.getStatus() + " -> " + newStatus + ".");
        });
    }

    /**
     * Stock audit: compares the physical count with the system quantity.
     * @return variance = counted - system (negative means shrinkage)
     */
    public int auditStock(User actor, int id, int countedQuantity, boolean adjustSystemStock) {
        AccessControl.require(actor, User.ROLE_SUPERVISOR, User.ROLE_OWNER);
        Product p = getProduct(id);
        if (countedQuantity < 0) throw new ServiceException("Counted quantity cannot be negative.");
        int variance = countedQuantity - p.getStockQuantity();
        String desc = "Audit of #" + id + " '" + p.getGadgetName() + "': system=" + p.getStockQuantity()
                + ", counted=" + countedQuantity + ", variance=" + variance
                + (adjustSystemStock && variance != 0 ? " (system stock adjusted)." : ".");
        if (adjustSystemStock && variance != 0) {
            applyStock(actor, p, countedQuantity, "STOCK_AUDIT", desc);
        } else {
            logService.log(actor.getUserId(), "STOCK_AUDIT", desc);
        }
        return variance;
    }

    // ================================================================ helpers

    private void applyStock(User actor, Product p, int newQuantity, String action, String description) {
        // Rule: Available => stock>0, Out of Stock => stock==0, Discontinued => stock==0
        // Any stock >0 must be Available (even if previously Discontinued – revives the gadget).
        // Stock 0 keeps Discontinued if it was already Discontinued, otherwise Out of Stock.
        String status;
        if (newQuantity > 0) {
            status = Product.STATUS_AVAILABLE;
        } else {
            status = Product.STATUS_DISCONTINUED.equals(p.getStatus()) ? Product.STATUS_DISCONTINUED : Product.STATUS_OUT_OF_STOCK;
        }
        String finalStatus = status;
        Tx.run(c -> {
            productDAO.updateStock(c, p.getGadgetId(), newQuantity, finalStatus);
            logService.log(c, actor.getUserId(), action, description);
        });
    }

    private static String required(String v, String field) {
        if (v == null || v.trim().isEmpty()) throw new ServiceException(field + " is required.");
        return v.trim();
    }

    private static String blankToNull(String v) { return v == null || v.trim().isEmpty() ? null : v.trim(); }
}
