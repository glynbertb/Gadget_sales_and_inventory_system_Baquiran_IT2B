package com.app.util;

import java.io.Console;
import java.util.Scanner;

/** Validated console input helpers (single shared Scanner) with styled prompts. */
public final class InputUtil {

    private static final Scanner SC = new Scanner(System.in);

    private InputUtil() { }

    /** The single shared Scanner for console input; pass this instance around instead of creating new ones. */
    public static Scanner sharedScanner() { return SC; }

    private static String styledPrompt(String prompt) {
        // Keep prompt cyan and add arrow
        return Ansi.color("  ➤ ", Ansi.CYAN) + Ansi.color(prompt, Ansi.BRIGHT_WHITE);
    }

    public static String readOptional(String prompt) {
        System.out.print(styledPrompt(prompt));
        return SC.nextLine().trim();
    }

    public static String readRequired(String prompt) {
        while (true) {
            String s = readOptional(prompt);
            if (!s.isEmpty()) return s;
            System.out.println(Ansi.color("    ✗ Input cannot be empty.", Ansi.YELLOW));
        }
    }

    public static int readInt(String prompt, int min, int max) {
        while (true) {
            String s = readOptional(prompt);
            try {
                int v = Integer.parseInt(s);
                if (v >= min && v <= max) return v;
                System.out.println(Ansi.color("    ✗ Please enter a number between " + min + " and " + max + ".", Ansi.YELLOW));
            } catch (NumberFormatException e) {
                System.out.println(Ansi.color("    ✗ Invalid number.", Ansi.YELLOW));
            }
        }
    }

    public static double readPositiveDouble(String prompt) {
        while (true) {
            String s = readOptional(prompt).replace(",", "").replace("₱", "").trim();
            try {
                double v = Double.parseDouble(s);
                if (v > 0 && v < 100_000_000 && !Double.isNaN(v)) return FormatUtil.round2(v);
                System.out.println(Ansi.color("    ✗ Amount must be greater than zero.", Ansi.YELLOW));
            } catch (NumberFormatException e) {
                System.out.println(Ansi.color("    ✗ Invalid amount.", Ansi.YELLOW));
            }
        }
    }

    public static boolean confirm(String prompt) {
        while (true) {
            String colored = Ansi.color(prompt, Ansi.BRIGHT_WHITE) + Ansi.color(" (y/n): ", Ansi.MUTED);
            String s = readOptional(colored).toLowerCase();
            if (s.equals("y") || s.equals("yes")) return true;
            if (s.equals("n") || s.equals("no")) return false;
            System.out.println(Ansi.color("    Please answer y or n.", Ansi.YELLOW));
        }
    }

    /** Reads a password without echo when a real console is available. */
    public static String readPassword(String prompt) {
        Console console = System.console();
        if (console != null) {
            while (true) {
                char[] pw = console.readPassword("%s", styledPrompt(prompt));
                if (pw != null && pw.length > 0) return new String(pw);
                System.out.println(Ansi.color("    ✗ Input cannot be empty.", Ansi.YELLOW));
            }
        }
        return readRequired(prompt);
    }

    public static void pause() {
        System.out.print(Ansi.color("\n  ─ Press ENTER to continue ─", Ansi.MUTED));
        SC.nextLine();
    }
}
