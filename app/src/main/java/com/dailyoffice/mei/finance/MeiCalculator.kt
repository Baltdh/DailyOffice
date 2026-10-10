package com.dailyoffice.mei.finance

import kotlin.math.max

data class MeiProjection(
    val limitCents: Long,
    val annualLimitCents: Long,
    val firstYearLimitCents: Long,
    val revenueCents: Long,
    val remainingCents: Long,
    val excessCents: Long,
    val usage: Float,
    val activeMonths: Int,
    val taxYear: Int,
    val openingYear: Int,
    val isFirstYear: Boolean,
    val isBeforeOpening: Boolean
)

object MeiCalculator {
    fun calculate(
        revenueCents: Long,
        annualLimitCents: Long,
        openingMonth: Int,
        openingYear: Int,
        taxYear: Int
    ): MeiProjection {
        val safeAnnualLimit = annualLimitCents.coerceAtLeast(0L)
        val safeMonth = openingMonth.coerceIn(1, 12)
        val safeOpeningYear = openingYear.coerceIn(2000, 2100)
        val safeTaxYear = taxYear.coerceIn(2000, 2100)

        val firstYearMonths = 13 - safeMonth
        val firstYearLimit = safeAnnualLimit * firstYearMonths / 12L

        val beforeOpening = safeTaxYear < safeOpeningYear
        val firstYear = safeTaxYear == safeOpeningYear

        val activeMonths = when {
            beforeOpening -> 0
            firstYear -> firstYearMonths
            else -> 12
        }

        val limit = when {
            beforeOpening -> 0L
            firstYear -> firstYearLimit
            else -> safeAnnualLimit
        }

        val remaining = max(0L, limit - revenueCents)
        val excess = if (limit > 0L) {
            max(0L, revenueCents - limit)
        } else {
            0L
        }
        val usage = if (limit > 0L) {
            revenueCents.toFloat() / limit.toFloat()
        } else {
            0f
        }

        return MeiProjection(
            limitCents = limit,
            annualLimitCents = safeAnnualLimit,
            firstYearLimitCents = firstYearLimit,
            revenueCents = revenueCents,
            remainingCents = remaining,
            excessCents = excess,
            usage = usage,
            activeMonths = activeMonths,
            taxYear = safeTaxYear,
            openingYear = safeOpeningYear,
            isFirstYear = firstYear,
            isBeforeOpening = beforeOpening
        )
    }
}
