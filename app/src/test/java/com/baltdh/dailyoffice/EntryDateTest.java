package com.baltdh.dailyoffice;

import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class EntryDateTest {
    @Test public void identifiesLegacyEntriesWithoutInventingADate() {
        assertEquals("Data não registrada (lançamento antigo)", EntryDate.formatOrUnknown(0));
        assertEquals("Data não registrada (lançamento antigo)", EntryDate.formatOrUnknown(-1));
    }

    @Test public void formatsTimestampUsingBrazilianDayFirstPattern() {
        String formatted = EntryDate.format(0L);
        assertTrue(formatted.matches("\\d{2}/\\d{2}/\\d{4} \\d{2}:\\d{2}"));
    }
}
