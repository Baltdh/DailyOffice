package com.baltdh.dailyoffice;

/** CSV field escaping with semicolon delimiter for Brazilian spreadsheet applications. */
public final class CsvUtils {
    private CsvUtils() {}

    public static String escape(String value) {
        if (value == null) return "";
        boolean quote = value.indexOf(';') >= 0 || value.indexOf('"') >= 0
                || value.indexOf('\n') >= 0 || value.indexOf('\r') >= 0;
        String escaped = value.replace("\"", "\"\"");
        return quote ? "\"" + escaped + "\"" : escaped;
    }
}
