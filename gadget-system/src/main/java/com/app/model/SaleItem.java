package com.app.model;

public class SaleItem {
    private int saleItemId;
    private int saleId;
    private int gadgetId;
    private String gadgetName; // joined, read-only convenience
    private int quantity;
    private double unitPrice;
    private double subtotal;

    public int getSaleItemId() { return saleItemId; }
    public void setSaleItemId(int saleItemId) { this.saleItemId = saleItemId; }
    public int getSaleId() { return saleId; }
    public void setSaleId(int saleId) { this.saleId = saleId; }
    public int getGadgetId() { return gadgetId; }
    public void setGadgetId(int gadgetId) { this.gadgetId = gadgetId; }
    public String getGadgetName() { return gadgetName; }
    public void setGadgetName(String gadgetName) { this.gadgetName = gadgetName; }
    public int getQuantity() { return quantity; }
    public void setQuantity(int quantity) { this.quantity = quantity; }
    public double getUnitPrice() { return unitPrice; }
    public void setUnitPrice(double unitPrice) { this.unitPrice = unitPrice; }
    public double getSubtotal() { return subtotal; }
    public void setSubtotal(double subtotal) { this.subtotal = subtotal; }
}
