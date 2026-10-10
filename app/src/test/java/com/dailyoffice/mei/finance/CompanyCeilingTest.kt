package com.dailyoffice.mei.finance

import com.dailyoffice.mei.data.CompanyType
import org.junit.Assert.assertEquals
import org.junit.Test

class CompanyCeilingTest {
    @Test fun categoryThresholds() {
        assertEquals(8_100_000L, CompanyCeiling.annualLimit(CompanyType.MEI))
        assertEquals(36_000_000L, CompanyCeiling.annualLimit(CompanyType.ME))
        assertEquals(480_000_000L, CompanyCeiling.annualLimit(CompanyType.EPP))
        assertEquals(0L, CompanyCeiling.annualLimit(CompanyType.OTHER))
        assertEquals(36_000_000L, CompanyCeiling.lowerBoundExclusive(CompanyType.EPP))
    }

    @Test fun firstYearProportionalForAllCategories() {
        for (type in listOf(CompanyType.MEI, CompanyType.ME, CompanyType.EPP)) {
            val full = CompanyCeiling.annualLimit(type)
            val projection = MeiCalculator.calculate(0, full, 7, 2026, 2026)
            assertEquals(full / 2, projection.limitCents)
            assertEquals(6, projection.activeMonths)
        }
    }
}
