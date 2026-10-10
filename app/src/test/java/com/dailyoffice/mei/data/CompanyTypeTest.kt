package com.dailyoffice.mei.data

import org.junit.Assert.assertEquals
import org.junit.Test

class CompanyTypeTest {
    @Test
    fun existingBehaviorDefaultsNewCompanyToMei() {
        val company = Company(name = "Loja")

        assertEquals(CompanyType.MEI, company.companyType)
        assertEquals(TaxRegime.SIMEI, company.taxRegime)
    }

    @Test
    fun converterRoundTripsCompanyTypeAndTaxRegime() {
        val converters = Converters()

        CompanyType.entries.forEach { type ->
            val stored = converters.companyTypeToString(type)
            assertEquals(type, converters.stringToCompanyType(stored))
        }

        TaxRegime.entries.forEach { regime ->
            val stored = converters.taxRegimeToString(regime)
            assertEquals(regime, converters.stringToTaxRegime(stored))
        }
    }

    @Test
    fun meiAlwaysNormalizesToSimei() {
        val normalized = CompanyProfileRules.normalizedTaxRegime(
            companyType = CompanyType.MEI,
            taxRegime = TaxRegime.LUCRO_REAL
        )

        assertEquals(TaxRegime.SIMEI, normalized)
    }

    @Test
    fun nonMeiCannotKeepSimei() {
        val normalized = CompanyProfileRules.normalizedTaxRegime(
            companyType = CompanyType.ME,
            taxRegime = TaxRegime.SIMEI
        )

        assertEquals(TaxRegime.OTHER, normalized)
    }

    @Test
    fun meAndEppExposeNormalTaxRegimes() {
        val regimes = CompanyProfileRules.allowedTaxRegimes(CompanyType.ME)

        assertEquals(
            listOf(
                TaxRegime.SIMPLES_NACIONAL,
                TaxRegime.LUCRO_PRESUMIDO,
                TaxRegime.LUCRO_REAL,
                TaxRegime.OTHER
            ),
            regimes
        )
    }
}
