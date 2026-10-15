package com.app.model;

public class Product {
    public static final String STATUS_AVAILABLE = "Available";
    public static final String STATUS_OUT_OF_STOCK = "Out of Stock";
    public static final String STATUS_DISCONTINUED = "Discontinued";
    public static final String[] STATUSES = {STATUS_AVAILABLE, STATUS_OUT_OF_STOCK, STATUS_DISCONTINUED};

    private int gadgetId;
    private String gadgetName;
    private int categoryId;
    private String categoryName; // joined, read-only convenience
    private double price;
    private int stockQuantity;
    private String status;

    public int getGadgetId() { return gadgetId; }
    public void setGadgetId(int gadgetId) { this.gadgetId = gadgetId; }
    public String getGadgetName() { return gadgetName; }
    public void setGadgetName(String gadgetName) { this.gadgetName = gadgetName; }
    public int getCategoryId() { return categoryId; }
    public void setCategoryId(int categoryId) { this.categoryId = categoryId; }
    public String getCategoryName() { return categoryName; }
    public void setCategoryName(String categoryName) { this.categoryName = categoryName; }
    public double getPrice() { return price; }
    public void setPrice(double price) { this.price = price; }
    public int getStockQuantity() { return stockQuantity; }
    public void setStockQuantity(int stockQuantity) { this.stockQuantity = stockQuantity; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
}
