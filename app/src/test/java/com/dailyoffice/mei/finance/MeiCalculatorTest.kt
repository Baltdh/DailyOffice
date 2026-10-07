package com.dailyoffice.mei.finance

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MeiCalculatorTest {
    @Test
    fun calculatesProportionalFirstYearFromAugust() {
        val result = MeiCalculator.calculate(
            revenueCents = 1_284_871L,
            annualLimitCents = 8_100_000L,
            openingMonth = 8,
            proportionalFirstYear = true
        )

        assertEquals(5, result.activeMonths)
        assertEquals(3_375_000L, result.limitCents)
        assertEquals(2_090_129L, result.remainingCents)
        assertTrue(result.usage > 0.38f && result.usage < 0.39f)
    }

    @Test
    fun reportsExcessWithoutNegativeRemaining() {
        val result = MeiCalculator.calculate(
            revenueCents = 9_000_000L,
            annualLimitCents = 8_100_000L,
            openingMonth = 1,
            proportionalFirstYear = false
        )

        assertEquals(0L, result.remainingCents)
        assertEquals(900_000L, result.excessCents)
    }
}
