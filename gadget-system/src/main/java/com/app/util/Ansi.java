package com.app.util;

/**
 * Minimal ANSI styling helper with auto-disable when output is not a TTY
 * or when NO_COLOR env is set. Safe to use everywhere - returns plain text
 * when colors are disabled.
 */
public final class Ansi {

    private Ansi() {}

    // --- escape codes ---
    public static final String RESET      = "\u001B[0m";
    public static final String BOLD       = "\u001B[1m";
    public static final String DIM        = "\u001B[2m";
    public static final String ITALIC     = "\u001B[3m";
    public static final String UNDERLINE  = "\u001B[4m";

    public static final String BLACK      = "\u001B[30m";
    public static final String RED        = "\u001B[31m";
    public static final String GREEN      = "\u001B[32m";
    public static final String YELLOW     = "\u001B[33m";
    public static final String BLUE       = "\u001B[34m";
    public static final String MAGENTA    = "\u001B[35m";
    public static final String CYAN       = "\u001B[36m";
    public static final String WHITE      = "\u001B[37m";

    public static final String BRIGHT_BLACK  = "\u001B[90m";
    public static final String BRIGHT_RED    = "\u001B[91m";
    public static final String BRIGHT_GREEN  = "\u001B[92m";
    public static final String BRIGHT_YELLOW = "\u001B[93m";
    public static final String BRIGHT_BLUE   = "\u001B[94m";
    public static final String BRIGHT_MAGENTA= "\u001B[95m";
    public static final String BRIGHT_CYAN   = "\u001B[96m";
    public static final String BRIGHT_WHITE  = "\u001B[97m";

    public static final String BG_BLACK   = "\u001B[40m";
    public static final String BG_RED     = "\u001B[41m";
    public static final String BG_GREEN   = "\u001B[42m";
    public static final String BG_YELLOW  = "\u001B[43m";
    public static final String BG_BLUE    = "\u001B[44m";
    public static final String BG_MAGENTA = "\u001B[45m";
    public static final String BG_CYAN    = "\u001B[46m";
    public static final String BG_WHITE   = "\u001B[47m";

    // brand palette
    public static final String PRIMARY   = CYAN;
    public static final String ACCENT    = BRIGHT_CYAN;
    public static final String SUCCESS   = GREEN;
    public static final String WARNING   = YELLOW;
    public static final String DANGER    = RED;
    public static final String MUTED     = BRIGHT_BLACK;

    private static final boolean ENABLED = detectEnabled();

    private static boolean detectEnabled() {
        String noColor = System.getenv("NO_COLOR");
        if (noColor != null && !noColor.isEmpty()) return false;
        String term = System.getenv("TERM");
        // Windows + IntelliJ + modern terminals support ANSI; disable only if explicitly dumb
        if ("dumb".equals(term)) return false;
        // Respect -Dansi=false
        String prop = System.getProperty("ansi");
        if ("false".equalsIgnoreCase(prop)) return false;
        return true;
    }

    public static boolean enabled() { return ENABLED; }

    public static String color(String text, String code) {
        if (!ENABLED || text == null) return text == null ? "" : text;
        return code + text + RESET;
    }

    public static String bold(String text) { return color(text, BOLD); }
    public static String dim(String text) { return color(text, DIM); }
    public static String cyan(String text) { return color(text, CYAN); }
    public static String brightCyan(String text) { return color(text, BRIGHT_CYAN); }
    public static String green(String text) { return color(text, GREEN); }
    public static String yellow(String text) { return color(text, YELLOW); }
    public static String red(String text) { return color(text, RED); }
    public static String magenta(String text) { return color(text, MAGENTA); }
    public static String white(String text) { return color(text, WHITE); }
    public static String muted(String text) { return color(text, BRIGHT_BLACK); }

    /** Wraps text in bold + color. */
    public static String boldColor(String text, String code) {
        if (!ENABLED) return text;
        return BOLD + code + text + RESET;
    }

    /** Strip ANSI for width calculations. */
    public static String strip(String s) {
        if (s == null) return "";
        return s.replaceAll("\u001B\\[[;\\d]*m", "");
    }
}
