package com.app.util;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

public final class FormatUtil {

    private static final DateTimeFormatter DTF = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private FormatUtil() { }

    public static String now() { return LocalDateTime.now().format(DTF); }

    public static double round2(double v) { return Math.round(v * 100.0) / 100.0; }

    public static String money(double v) { return String.format(Locale.US, "₱%,.2f", v); }

    public static String moneyPlain(double v) { return String.format(Locale.US, "%,.2f", v); }

    public static String trunc(String s, int max) {
        if (s == null) return "";
        return s.length() <= max ? s : s.substring(0, Math.max(0, max - 1)) + "~";
    }
}
