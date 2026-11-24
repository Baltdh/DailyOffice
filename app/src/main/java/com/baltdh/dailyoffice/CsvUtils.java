package com.baltdh.dailyoffice;

/** CSV escaping for Brazilian spreadsheets, including formula-injection protection. */
public final class CsvUtils {
    private CsvUtils() {}

    public static String escape(String value) {
        if (value == null) return "";
        String safe = value;
        int first = 0;
        while (first < safe.length() && Character.isWhitespace(safe.charAt(first))
                && safe.charAt(first) != '\t' && safe.charAt(first) != '\r') {
            first++;
        }
        if (first < safe.length()) {
            char initial = safe.charAt(first);
            if (initial == '=' || initial == '+' || initial == '-' || initial == '@'
                    || initial == '\t' || initial == '\r') {
                safe = "'" + safe;
            }
        }
        boolean quote = safe.indexOf(';') >= 0 || safe.indexOf('"') >= 0
                || safe.indexOf('\n') >= 0 || safe.indexOf('\r') >= 0;
        String escaped = safe.replace("\"", "\"\"");
        return quote ? "\"" + escaped + "\"" : escaped;
    }
}
