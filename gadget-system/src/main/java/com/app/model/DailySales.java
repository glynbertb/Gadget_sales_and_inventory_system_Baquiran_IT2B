package com.app.model;

/** Aggregate row for the "Sales by Date" report section (Completed sales only). */
public record DailySales(String date, int transactions, double total) { }
