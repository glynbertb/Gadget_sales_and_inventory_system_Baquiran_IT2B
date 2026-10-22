package com.app.model;

public class User {
    public static final String ROLE_OWNER = "Owner";
    public static final String ROLE_CASHIER = "Cashier";
    public static final String ROLE_INVENTORY = "Inventory Staff";
    public static final String ROLE_SUPERVISOR = "Store Supervisor";
    public static final String[] ROLES = {ROLE_OWNER, ROLE_CASHIER, ROLE_INVENTORY, ROLE_SUPERVISOR};

    /** Placeholder for self-registered accounts waiting for Owner approval. Never pickable in any role menu. */
    public static final String ROLE_UNDEFINED = "Undefined";

    public static final String STATUS_ACTIVE = "Active";
    public static final String STATUS_INACTIVE = "Inactive";
    /** A pending registration the Owner has approved. Logs in exactly like an active account. */
    public static final String STATUS_APPROVED = "Approved";
    /** Status of a self-registered account waiting for Owner approval. Cannot log in until activated. */
    public static final String STATUS_PENDING = "Pending";

    private int userId;
    private String name;
    private String username;
    private String password;
    private String role;
    private String status;

    public User() { }

    public User(String name, String username, String password, String role, String status) {
        this.name = name; this.username = username; this.password = password; this.role = role; this.status = status;
    }

    public int getUserId() { return userId; }
    public void setUserId(int userId) { this.userId = userId; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }
    public String getPassword() { return password; }
    public void setPassword(String password) { this.password = password; }
    public String getRole() { return role; }
    public void setRole(String role) { this.role = role; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
}
