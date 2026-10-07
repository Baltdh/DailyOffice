package com.dailyoffice.mei.finance

import android.content.Context
import java.time.Year
import java.time.YearMonth

data class MeiConfig(
    val annualLimitCents: Long = 8_100_000L,
    val openingMonth: Int = YearMonth.now().monthValue,
    val proportionalFirstYear: Boolean = true,
    val taxYear: Int = Year.now().value
)

class MeiSettings(context: Context) {
    private val prefs = context.getSharedPreferences("mei_settings", Context.MODE_PRIVATE)

    fun load(companyId: Long): MeiConfig {
        val prefix = "company_${companyId}_"
        val hasCompanySettings = prefs.contains(prefix + "annualLimitCents")

        if (companyId == 1L && !hasCompanySettings) {
            return MeiConfig(
                annualLimitCents = prefs.getLong("annualLimitCents", 8_100_000L),
                openingMonth = prefs.getInt(
                    "openingMonth",
                    YearMonth.now().monthValue
                ),
                proportionalFirstYear = prefs.getBoolean(
                    "proportionalFirstYear",
                    true
                ),
                taxYear = prefs.getInt("taxYear", Year.now().value)
            )
        }

        return MeiConfig(
            annualLimitCents = prefs.getLong(
                prefix + "annualLimitCents",
                8_100_000L
            ),
            openingMonth = prefs.getInt(
                prefix + "openingMonth",
                YearMonth.now().monthValue
            ),
            proportionalFirstYear = prefs.getBoolean(
                prefix + "proportionalFirstYear",
                true
            ),
            taxYear = prefs.getInt(
                prefix + "taxYear",
                Year.now().value
            )
        )
    }

    fun update(companyId: Long, config: MeiConfig): MeiConfig {
        val normalized = config.copy(
            annualLimitCents = config.annualLimitCents.coerceAtLeast(0L),
            openingMonth = config.openingMonth.coerceIn(1, 12),
            taxYear = config.taxYear.coerceIn(2000, 2100)
        )

        val prefix = "company_${companyId}_"
        prefs.edit()
            .putLong(prefix + "annualLimitCents", normalized.annualLimitCents)
            .putInt(prefix + "openingMonth", normalized.openingMonth)
            .putBoolean(
                prefix + "proportionalFirstYear",
                normalized.proportionalFirstYear
            )
            .putInt(prefix + "taxYear", normalized.taxYear)
            .apply()

        return normalized
    }
}
