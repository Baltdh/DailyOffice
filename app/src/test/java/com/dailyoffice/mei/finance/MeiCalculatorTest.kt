package com.dailyoffice.mei.finance

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MeiCalculatorTest {
    @Test
    fun calculatesProportionalLimitInOpeningYear() {
        val result = MeiCalculator.calculate(
            revenueCents = 1_284_871L,
            annualLimitCents = 8_100_000L,
            openingMonth = 8,
            openingYear = 2026,
            taxYear = 2026
        )

        assertTrue(result.isFirstYear)
        assertEquals(5, result.activeMonths)
        assertEquals(3_375_000L, result.limitCents)
        assertEquals(3_375_000L, result.firstYearLimitCents)
        assertEquals(8_100_000L, result.annualLimitCents)
        assertEquals(2_090_129L, result.remainingCents)
        assertTrue(result.usage > 0.38f && result.usage < 0.39f)
    }

    @Test
    fun usesFullAnnualLimitAfterOpeningYear() {
        val result = MeiCalculator.calculate(
            revenueCents = 4_500_000L,
            annualLimitCents = 8_100_000L,
            openingMonth = 8,
            openingYear = 2026,
            taxYear = 2027
        )

        assertFalse(result.isFirstYear)
        assertFalse(result.isBeforeOpening)
        assertEquals(12, result.activeMonths)
        assertEquals(8_100_000L, result.limitCents)
        assertEquals(3_600_000L, result.remainingCents)
    }

    @Test
    fun reportsExcessWithoutNegativeRemaining() {
        val result = MeiCalculator.calculate(
            revenueCents = 9_000_000L,
            annualLimitCents = 8_100_000L,
            openingMonth = 1,
            openingYear = 2025,
            taxYear = 2026
        )

        assertEquals(0L, result.remainingCents)
        assertEquals(900_000L, result.excessCents)
    }

    @Test
    fun yearBeforeOpeningHasNoApplicableLimit() {
        val result = MeiCalculator.calculate(
            revenueCents = 0L,
            annualLimitCents = 8_100_000L,
            openingMonth = 10,
            openingYear = 2026,
            taxYear = 2025
        )

        assertTrue(result.isBeforeOpening)
        assertEquals(0, result.activeMonths)
        assertEquals(0L, result.limitCents)
    }
}
