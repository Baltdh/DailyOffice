package com.dailyoffice.mei.data

import org.junit.Assert.assertEquals
import org.junit.Test

class CompanyTypeTest {
    @Test
    fun existingBehaviorDefaultsNewCompanyToMei() {
        val company = Company(name = "Loja")

        assertEquals(CompanyType.MEI, company.companyType)
    }

    @Test
    fun converterRoundTripsCompanyType() {
        val converters = Converters()

        CompanyType.entries.forEach { type ->
            val stored = converters.companyTypeToString(type)
            assertEquals(type, converters.stringToCompanyType(stored))
        }
    }
}
