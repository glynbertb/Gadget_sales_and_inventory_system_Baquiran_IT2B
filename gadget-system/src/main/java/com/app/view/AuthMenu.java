package com.app.view;

import com.app.model.User;
import com.app.service.AuthService;
import com.app.util.Ansi;
import com.app.util.InputUtil;

/** Main entry menu (Login / Sign Up / Exit) and role-based routing. */
public class AuthMenu {

    private final AuthService auth;
    private final OwnerMenu ownerMenu;
    private final CashierMenu cashierMenu;
    private final InventoryMenu inventoryMenu;
    private final SupervisorMenu supervisorMenu;

    public AuthMenu(AuthService auth, OwnerMenu ownerMenu, CashierMenu cashierMenu,
                    InventoryMenu inventoryMenu, SupervisorMenu supervisorMenu) {
        this.auth = auth;
        this.ownerMenu = ownerMenu;
        this.cashierMenu = cashierMenu;
        this.inventoryMenu = inventoryMenu;
        this.supervisorMenu = supervisorMenu;
    }

    public void run() {
        while (true) {
            printWelcome();
            int option = InputUtil.readInt("Select option:  ", 1, 3);
            switch (option) {
                case 1:
                    ViewHelper.safely(this::login);
                    break;
                case 2:
                    ViewHelper.safely(this::signUp);
                    break;
                case 3:
                    System.out.println(Ansi.color("\n  Thank you for using Bertzkie Gadget System. Goodbye!  ♡", Ansi.MUTED));
                    return;
                default:
                    break;
            }
        }
    }

    private void printWelcome() {
        int w = 64;
        System.out.println();
        System.out.println(Ansi.color("╔" + "═".repeat(w) + "╗", Ansi.CYAN));
        System.out.println(Ansi.color("║", Ansi.CYAN) + Ansi.boldColor(center("◆  BERTZKIE GADGET SALES & INVENTORY  ◆", w), Ansi.BRIGHT_CYAN) + Ansi.color("║", Ansi.CYAN));
        System.out.println(Ansi.color("║", Ansi.CYAN) + Ansi.dim(center("Your trusted gadget store inventory system", w)) + Ansi.color("║", Ansi.CYAN));
        System.out.println(Ansi.color("╠" + "═".repeat(w) + "╣", Ansi.CYAN));
        printOption(1, "Login",           "", w);
        printOption(2, "Sign Up",         "", w);
        printOption(3, "Exit",            "", w);
        System.out.println(Ansi.color("╚" + "═".repeat(w) + "╝", Ansi.CYAN));
    }

    private void printOption(int num, String label, String desc, int w) {
        String numStr = Ansi.boldColor(String.format("%d", num), Ansi.CYAN);
        String line = String.format("  [%s] %-12s  %s", numStr, Ansi.bold(label), Ansi.dim(desc));
        // compute stripped length for padding
        String stripped = Ansi.strip(line);
        int pad = w - stripped.length();
        System.out.println(Ansi.color("║", Ansi.CYAN) + line + " ".repeat(Math.max(0, pad)) + Ansi.color("║", Ansi.CYAN));
    }

    private static String center(String s, int w) {
        int len = Ansi.strip(s).length();
        if (len >= w) return s;
        int pad = (w - len) / 2;
        return " ".repeat(pad) + s + " ".repeat(w - len - pad);
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
        User created = auth.register(name, username, password);
        System.out.println(Ansi.color("  └─────────────────────────────────────────────────", Ansi.MUTED));
        if (User.ROLE_OWNER.equals(created.getRole())) {
            System.out.println(Ansi.color("\n  ✓ Owner account created for " + created.getName() + ". You can now log in.", Ansi.GREEN));
        } else {
            System.out.println(Ansi.color("\n  ✓ Registration received, " + created.getName() + ".", Ansi.GREEN));
            System.out.println(Ansi.color("  Please wait for the Owner's approval before you can log in.", Ansi.YELLOW));
        }
    }
}
