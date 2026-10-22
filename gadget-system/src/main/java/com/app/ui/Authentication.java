package com.app.ui;

import com.app.model.User;
import com.app.service.AuthService;
import com.app.service.ServiceException;
import com.app.util.ConsoleStyle;
import com.app.util.DataAccessException;
import com.app.util.InputUtil;
import java.util.Scanner;

/**
 * Multi-role authentication gate and UI router.
 *
 * <p>Owns the initial Login / Sign Up / Exit loop (extracted out of
 * {@code Main} so {@code Main} stays a pure startup entry point) and routes
 * each successfully logged-in user to their role-based menu.
 *
 * <p>A single shared {@link Scanner} instance is passed in through the
 * constructor and reused by every menu, so no duplicate resources are created.
 * All database access goes through {@link AuthService}; this class holds no DAO
 * reference and contains no SQL.
 */
public class Authentication {

    private final AuthService auth;
    private final Scanner scanner;
    private final OwnerMenu ownerMenu;
    private final CashierMenu cashierMenu;
    private final InventoryMenu inventoryMenu;
    private final SupervisorMenu supervisorMenu;

    public Authentication(AuthService auth, Scanner scanner,
                          OwnerMenu ownerMenu, CashierMenu cashierMenu,
                          InventoryMenu inventoryMenu, SupervisorMenu supervisorMenu) {
        this.auth = auth;
        this.scanner = scanner;
        this.ownerMenu = ownerMenu;
        this.cashierMenu = cashierMenu;
        this.inventoryMenu = inventoryMenu;
        this.supervisorMenu = supervisorMenu;
    }

    /** Starts the login / registration loop. Returns only when the user exits. */
    public void start() {
        while (true) {
            printWelcome();
            int option = InputUtil.readInt("Select option:  ", 1, 3);
            switch (option) {
                case 1 -> safely(this::login);
                case 2 -> safely(this::signUp);
                case 3 -> {
                    System.out.println(ConsoleStyle.color("\n  Thank you for using Bertzkie Gadget System. Goodbye!  ♡", ConsoleStyle.MUTED));
                    return;
                }
                default -> { }
            }
        }
    }

    private void login() {
        System.out.println();
        System.out.println(ConsoleStyle.color("  ┌─ Login ─────────────────────────────────────────", ConsoleStyle.MUTED));
        String username = InputUtil.readRequired("Username: ");
        String password = InputUtil.readPassword("Password: ");
        System.out.println(ConsoleStyle.color("  └─────────────────────────────────────────────────", ConsoleStyle.MUTED));
        System.out.println(ConsoleStyle.dim("  Authenticating…"));
        User user = auth.login(username, password);
        System.out.println(ConsoleStyle.color("\n  ✓ Welcome, " + user.getName() + "  (" + user.getRole() + ")!", ConsoleStyle.GREEN));
        routeByRole(user);
    }

    /** Routes a successfully logged-in user to their role-based menu. */
    private void routeByRole(User user) {
        switch (user.getRole()) {
            case User.ROLE_OWNER -> ownerMenu.run(user);
            case User.ROLE_CASHIER -> cashierMenu.run(user);
            case User.ROLE_INVENTORY -> inventoryMenu.run(user);
            case User.ROLE_SUPERVISOR -> supervisorMenu.run(user);
            default -> System.out.println(ConsoleStyle.color("  [!] Unknown role '" + user.getRole() + "'. Contact the Owner.", ConsoleStyle.RED));
        }
    }

    private void signUp() {
        System.out.println();
        System.out.println(ConsoleStyle.color("  ┌─ Account Registration ────────────────────────────", ConsoleStyle.MUTED));
        String name = InputUtil.readRequired("Full name: ");
        String username = InputUtil.readRequired("Username: ");
        String password = InputUtil.readPassword("Password (min 6 chars): ");
        String confirm = InputUtil.readPassword("Confirm password: ");
        if (!password.equals(confirm)) {
            System.out.println(ConsoleStyle.color("  ✗ Passwords do not match.", ConsoleStyle.RED));
            System.out.println(ConsoleStyle.color("  └─────────────────────────────────────────────────", ConsoleStyle.MUTED));
            return;
        }
        User created;
        if (auth.isFirstUser()) {
            created = auth.register(name, username, password);
        } else {
            created = auth.register(name, username, password, chooseRole());
        }
        System.out.println(ConsoleStyle.color("  └─────────────────────────────────────────────────", ConsoleStyle.MUTED));
        if (User.ROLE_OWNER.equals(created.getRole())) {
            System.out.println(ConsoleStyle.color("\n  ✓ Your account has been created, " + created.getName() + " (" + created.getRole() + ").", ConsoleStyle.GREEN));
            System.out.println(ConsoleStyle.color("  Right now your account is still PENDING. Please wait for the admin's approval before you can log in.", ConsoleStyle.YELLOW));
        } else {
            System.out.println(ConsoleStyle.color("\n  ✓ Owner account created for " + created.getName() + ". You can now log in.", ConsoleStyle.GREEN));
        }
    }

    /** Role selection: every role in {@link User#ROLES} is pickable. */
    private String chooseRole() {
        System.out.println(ConsoleStyle.color("  ┌─ Select Role ─────────────────────", ConsoleStyle.MUTED));
        for (int i = 0; i < User.ROLES.length; i++) {
            String num = ConsoleStyle.color(String.format("%d", i + 1), ConsoleStyle.CYAN);
            System.out.println(ConsoleStyle.color("  │ ", ConsoleStyle.MUTED) + " [" + num + ConsoleStyle.color("] ", ConsoleStyle.MUTED) + User.ROLES[i]);
        }
        System.out.println(ConsoleStyle.color("  └─────────────────────────────────", ConsoleStyle.MUTED));
        return User.ROLES[InputUtil.readInt("  Select role: ", 1, User.ROLES.length) - 1];
    }

    private void printWelcome() {
        int w = 64;
        System.out.println();
        System.out.println(ConsoleStyle.color("╔" + "═".repeat(w) + "╗", ConsoleStyle.CYAN));
        System.out.println(ConsoleStyle.color("║", ConsoleStyle.CYAN) + ConsoleStyle.boldColor(center("◆  BERTZKIE GADGET SALES & INVENTORY  ◆", w), ConsoleStyle.BRIGHT_CYAN) + ConsoleStyle.color("║", ConsoleStyle.CYAN));
        System.out.println(ConsoleStyle.color("║", ConsoleStyle.CYAN) + ConsoleStyle.dim(center("Your trusted gadget store inventory system", w)) + ConsoleStyle.color("║", ConsoleStyle.CYAN));
        System.out.println(ConsoleStyle.color("╠" + "═".repeat(w) + "╣", ConsoleStyle.CYAN));
        printOption(1, "Login", "", w);
        printOption(2, "Sign Up", "", w);
        printOption(3, "Exit", "", w);
        System.out.println(ConsoleStyle.color("╚" + "═".repeat(w) + "╝", ConsoleStyle.CYAN));
    }

    private void printOption(int num, String label, String desc, int w) {
        String numStr = ConsoleStyle.boldColor(String.format("%d", num), ConsoleStyle.CYAN);
        String line = String.format("  [%s] %-12s  %s", numStr, ConsoleStyle.bold(label), ConsoleStyle.dim(desc));
        int pad = w - ConsoleStyle.strip(line).length();
        System.out.println(ConsoleStyle.color("║", ConsoleStyle.CYAN) + line + " ".repeat(Math.max(0, pad)) + ConsoleStyle.color("║", ConsoleStyle.CYAN));
    }

    private static String center(String s, int w) {
        int len = ConsoleStyle.strip(s).length();
        if (len >= w) return s;
        int pad = (w - len) / 2;
        return " ".repeat(pad) + s + " ".repeat(w - len - pad);
    }

    /** Shows business/database errors instead of crashing the menu loop. */
    private static void safely(Runnable action) {
        try {
            action.run();
        } catch (ServiceException e) {
            System.out.println(ConsoleStyle.color("\n  ✗  " + e.getMessage(), ConsoleStyle.RED));
        } catch (DataAccessException e) {
            System.out.println(ConsoleStyle.color("\n  ✗  Database error: " + e.getMessage(), ConsoleStyle.RED));
        }
    }
}
