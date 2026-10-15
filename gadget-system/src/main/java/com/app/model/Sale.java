package com.app.model;

import java.util.ArrayList;
import java.util.List;

public class Sale {
    public static final String STATUS_COMPLETED = "Completed";
    public static final String STATUS_VOIDED = "Voided";
    public static final String STATUS_RETURNED = "Returned";
    public static final String[] PAYMENT_METHODS = {"Cash", "Card", "E-Wallet"};

    private int saleId;
    private int userId;
    private String cashierName; // joined, read-only convenience
    private String saleDate;
    private double totalAmount;
    private String paymentMethod;
    private String saleStatus;
    private List<SaleItem> items = new ArrayList<>();

    public int getSaleId() { return saleId; }
    public void setSaleId(int saleId) { this.saleId = saleId; }
    public int getUserId() { return userId; }
    public void setUserId(int userId) { this.userId = userId; }
    public String getCashierName() { return cashierName; }
    public void setCashierName(String cashierName) { this.cashierName = cashierName; }
    public String getSaleDate() { return saleDate; }
    public void setSaleDate(String saleDate) { this.saleDate = saleDate; }
    public double getTotalAmount() { return totalAmount; }
    public void setTotalAmount(double totalAmount) { this.totalAmount = totalAmount; }
    public String getPaymentMethod() { return paymentMethod; }
    public void setPaymentMethod(String paymentMethod) { this.paymentMethod = paymentMethod; }
    public String getSaleStatus() { return saleStatus; }
    public void setSaleStatus(String saleStatus) { this.saleStatus = saleStatus; }
    public List<SaleItem> getItems() { return items; }
    public void setItems(List<SaleItem> items) { this.items = items; }
}
