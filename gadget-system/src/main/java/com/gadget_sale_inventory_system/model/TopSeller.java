package com.gadget_sale_inventory_system.model;

/** Aggregate row for the "Top-Selling Gadgets" report section. */
public record TopSeller(String name, int quantity, double revenue) { }
