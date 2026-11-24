package com.baltdh.dailyoffice;

import org.junit.Test;
import static org.junit.Assert.assertEquals;

public class BrazilianMoneyTest {
    @Test public void parsesBrazilianThousandsAndDecimalSeparators() {
        assertEquals(123456L, BrazilianMoney.parseToCents("R$ 1.234,56"));
    }

    @Test public void parsesUsThousandsAndDecimalSeparatorsFromOcr() {
        assertEquals(123456L, BrazilianMoney.parseToCents("1,234.56"));
    }

    @Test public void parsesOneDecimalPlaceWithoutLosingCents() {
        assertEquals(1250L, BrazilianMoney.parseToCents("12,5"));
        assertEquals(1250L, BrazilianMoney.parseToCents("12.5"));
    }

    @Test public void treatsThreeTrailingDigitsAsThousandsGrouping() {
        assertEquals(123400L, BrazilianMoney.parseToCents("1.234"));
    }

    @Test public void formatsLargeValuesWithoutFloatingPoint() {
        assertEquals("1.234.567,89", BrazilianMoney.formatCents(123456789L));
    }

    @Test(expected = IllegalArgumentException.class)
    public void rejectsAmbiguousOrInvalidValues() {
        BrazilianMoney.parseToCents("12.3456");
    }

    @Test(expected = IllegalArgumentException.class)
    public void rejectsMalformedMixedSeparators() {
        BrazilianMoney.parseToCents("1.234,56.70");
    }
}
