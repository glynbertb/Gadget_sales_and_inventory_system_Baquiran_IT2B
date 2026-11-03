package com.gadget_sale_inventory_system.util;

/** Unchecked wrapper for low-level SQL failures. */
public class DataAccessException extends RuntimeException {
    private static final long serialVersionUID = 1L;

    public DataAccessException(String message, Throwable cause) { super(message, cause); }
}
