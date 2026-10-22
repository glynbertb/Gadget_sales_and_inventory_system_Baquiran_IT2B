package com.app.ui;

import com.app.model.User;
import com.app.service.AuthService;
import com.app.service.ServiceException;
import com.app.util.Ansi;
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
                    System.out.println(Ansi.color("\n  Thank you for using Bertzkie Gadget System. Goodbye!  ♡", Ansi.MUTED));
                    return;
                }
                default -> { }
            }
        }
    }

    private void login() {
        System.out.println();
        System.out.println(Ansi.color("  ┌─ Login ─────────────────────────────────────────", Ansi.MUTED));
        String username = InputUtil.readRequired("Username: ");
        String password = InputUtil.readPassword("Password: ");
        System.out.println(Ansi.color("  └─────────────────────────────────────────────────", Ansi.MUTED));
        System.out.println(Ansi.dim("  Authenticating…"));
        User user = auth.login(username, password);
        System.out.println(Ansi.color("\n  ✓ Welcome, " + user.getName() + "  (" + user.getRole() + ")!", Ansi.GREEN));
        routeByRole(user);
    }

    /** Routes a successfully logged-in user to their role-based menu. */
    private void routeByRole(User user) {
        switch (user.getRole()) {
            case User.ROLE_OWNER -> ownerMenu.run(user);
            case User.ROLE_CASHIER -> cashierMenu.run(user);
            case User.ROLE_INVENTORY -> inventoryMenu.run(user);
            case User.ROLE_SUPERVISOR -> supervisorMenu.run(user);
            default -> System.out.println(Ansi.color("  [!] Unknown role '" + user.getRole() + "'. Contact the Owner.", Ansi.RED));
        }
    }

    private void signUp() {
        System.out.println();
        System.out.println(Ansi.color("  ┌─ Account Registration ────────────────────────────", Ansi.MUTED));
        String name = InputUtil.readRequired("Full name: ");
        String username = InputUtil.readRequired("Username: ");
        String password = InputUtil.readPassword("Password (min 6 chars): ");
        String confirm = InputUtil.readPassword("Confirm password: ");
        if (!password.equals(confirm)) {
            System.out.println(Ansi.color("  ✗ Passwords do not match.", Ansi.RED));
            System.out.println(Ansi.color("  └─────────────────────────────────────────────────", Ansi.MUTED));
            return;
        }
        User created;
        if (auth.isFirstUser()) {
            created = auth.register(name, username, password);
        } else {
            created = auth.register(name, username, password, chooseRole());
        }
        System.out.println(Ansi.color("  └─────────────────────────────────────────────────", Ansi.MUTED));
        if (User.ROLE_OWNER.equals(created.getRole())) {
            System.out.println(Ansi.color("\n  ✓ Your account has been created, " + created.getName() + " (" + created.getRole() + ").", Ansi.GREEN));
            System.out.println(Ansi.color("  Right now your account is still PENDING. Please wait for the admin's approval before you can log in.", Ansi.YELLOW));
        } else {
            System.out.println(Ansi.color("\n  ✓ Owner account created for " + created.getName() + ". You can now log in.", Ansi.GREEN));
        }
    }

    /** Role selection: every role in {@link User#ROLES} is pickable. */
    private String chooseRole() {
        System.out.println(Ansi.color("  ┌─ Select Role ─────────────────────", Ansi.MUTED));
        for (int i = 0; i < User.ROLES.length; i++) {
            String num = Ansi.color(String.format("%d", i + 1), Ansi.CYAN);
            System.out.println(Ansi.color("  │ ", Ansi.MUTED) + " [" + num + Ansi.color("] ", Ansi.MUTED) + User.ROLES[i]);
        }
        System.out.println(Ansi.color("  └─────────────────────────────────", Ansi.MUTED));
        return User.ROLES[InputUtil.readInt("  Select role: ", 1, User.ROLES.length) - 1];
    }

    private void printWelcome() {
        int w = 64;
        System.out.println();
        System.out.println(Ansi.color("╔" + "═".repeat(w) + "╗", Ansi.CYAN));
        System.out.println(Ansi.color("║", Ansi.CYAN) + Ansi.boldColor(center("◆  BERTZKIE GADGET SALES & INVENTORY  ◆", w), Ansi.BRIGHT_CYAN) + Ansi.color("║", Ansi.CYAN));
        System.out.println(Ansi.color("║", Ansi.CYAN) + Ansi.dim(center("Your trusted gadget store inventory system", w)) + Ansi.color("║", Ansi.CYAN));
        System.out.println(Ansi.color("╠" + "═".repeat(w) + "╣", Ansi.CYAN));
        printOption(1, "Login", "", w);
        printOption(2, "Sign Up", "", w);
        printOption(3, "Exit", "", w);
        System.out.println(Ansi.color("╚" + "═".repeat(w) + "╝", Ansi.CYAN));
    }

    private void printOption(int num, String label, String desc, int w) {
        String numStr = Ansi.boldColor(String.format("%d", num), Ansi.CYAN);
        String line = String.format("  [%s] %-12s  %s", numStr, Ansi.bold(label), Ansi.dim(desc));
        int pad = w - Ansi.strip(line).length();
        System.out.println(Ansi.color("║", Ansi.CYAN) + line + " ".repeat(Math.max(0, pad)) + Ansi.color("║", Ansi.CYAN));
    }

    private static String center(String s, int w) {
        int len = Ansi.strip(s).length();
        if (len >= w) return s;
        int pad = (w - len) / 2;
        return " ".repeat(pad) + s + " ".repeat(w - len - pad);
    }

    /** Shows business/database errors instead of crashing the menu loop. */
    private static void safely(Runnable action) {
        try {
            action.run();
        } catch (ServiceException e) {
            System.out.println(Ansi.color("\n  ✗  " + e.getMessage(), Ansi.RED));
        } catch (DataAccessException e) {
            System.out.println(Ansi.color("\n  ✗  Database error: " + e.getMessage(), Ansi.RED));
        }
    }
}
