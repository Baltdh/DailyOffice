package com.baltdh.dailyoffice;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public final class EntryDate {
    private static final ThreadLocal<SimpleDateFormat> FORMAT = new ThreadLocal<SimpleDateFormat>() {
        @Override protected SimpleDateFormat initialValue() {
            return new SimpleDateFormat("dd/MM/yyyy HH:mm", new Locale("pt", "BR"));
        }
    };

    private EntryDate() {}

    public static String format(long epochMillis) {
        return FORMAT.get().format(new Date(epochMillis));
    }

    public static String formatOrUnknown(long epochMillis) {
        return epochMillis <= 0 ? "Data não registrada (lançamento antigo)" : format(epochMillis);
    }
}
