package com.dailyoffice.mei.finance

import android.content.Context
import java.time.LocalDate
import java.time.Year

data class MeiConfig(
    val annualLimitCents: Long = 8_100_000L,
    val openingDay: Int = LocalDate.now().dayOfMonth,
    val openingMonth: Int = LocalDate.now().monthValue,
    val openingYear: Int = Year.now().value,
    val taxYear: Int = Year.now().value
)

class MeiSettings(context: Context) {
    private val prefs = context.getSharedPreferences("mei_settings", Context.MODE_PRIVATE)

    fun load(companyId: Long): MeiConfig {
        val prefix = "company_${companyId}_"
        val hasCompanySettings = prefs.contains(prefix + "annualLimitCents")

        if (companyId == 1L && !hasCompanySettings) {
            val legacyTaxYear = prefs.getInt("taxYear", Year.now().value)
            val legacyOpeningMonth = prefs.getInt(
                "openingMonth",
                LocalDate.now().monthValue
            )
            val legacyFirstYear = prefs.getBoolean("proportionalFirstYear", true)

            return MeiConfig(
                annualLimitCents = prefs.getLong("annualLimitCents", 8_100_000L),
                openingDay = prefs.getInt(
                    "openingDay",
                    1
                ),
                openingMonth = legacyOpeningMonth,
                openingYear = prefs.getInt(
                    "openingYear",
                    if (legacyFirstYear) legacyTaxYear else legacyTaxYear - 1
                ),
                taxYear = legacyTaxYear
            )
        }

        val taxYear = prefs.getInt(
            prefix + "taxYear",
            Year.now().value
        )
        val legacyFirstYear = prefs.getBoolean(
            prefix + "proportionalFirstYear",
            true
        )

        return MeiConfig(
            annualLimitCents = prefs.getLong(
                prefix + "annualLimitCents",
                8_100_000L
            ),
            openingDay = prefs.getInt(
                prefix + "openingDay",
                1
            ),
            openingMonth = prefs.getInt(
                prefix + "openingMonth",
                LocalDate.now().monthValue
            ),
            openingYear = prefs.getInt(
                prefix + "openingYear",
                if (legacyFirstYear) taxYear else taxYear - 1
            ),
            taxYear = taxYear
        )
    }

    fun update(companyId: Long, config: MeiConfig): MeiConfig {
        val safeYear = config.openingYear.coerceIn(2000, 2100)
        val safeMonth = config.openingMonth.coerceIn(1, 12)
        val maxDay = java.time.YearMonth.of(safeYear, safeMonth).lengthOfMonth()

        val normalized = config.copy(
            annualLimitCents = config.annualLimitCents.coerceAtLeast(0L),
            openingDay = config.openingDay.coerceIn(1, maxDay),
            openingMonth = safeMonth,
            openingYear = safeYear,
            taxYear = config.taxYear.coerceIn(2000, 2100)
        )

        val prefix = "company_${companyId}_"
        prefs.edit()
            .putLong(prefix + "annualLimitCents", normalized.annualLimitCents)
            .putInt(prefix + "openingDay", normalized.openingDay)
            .putInt(prefix + "openingMonth", normalized.openingMonth)
            .putInt(prefix + "openingYear", normalized.openingYear)
            .putInt(prefix + "taxYear", normalized.taxYear)
            .apply()

        return normalized
    }
}
