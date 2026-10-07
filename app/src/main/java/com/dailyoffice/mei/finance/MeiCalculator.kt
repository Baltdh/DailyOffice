package com.dailyoffice.mei.finance

import kotlin.math.max

data class MeiProjection(
    val limitCents: Long,
    val revenueCents: Long,
    val remainingCents: Long,
    val excessCents: Long,
    val usage: Float,
    val activeMonths: Int
)

object MeiCalculator {
    fun calculate(
        revenueCents: Long,
        annualLimitCents: Long,
        openingMonth: Int,
        proportionalFirstYear: Boolean
    ): MeiProjection {
        val safeMonth = openingMonth.coerceIn(1, 12)
        val activeMonths = if (proportionalFirstYear) 13 - safeMonth else 12
        val monthlyLimit = annualLimitCents / 12L
        val limit = monthlyLimit * activeMonths
        val remaining = max(0L, limit - revenueCents)
        val excess = max(0L, revenueCents - limit)
        val usage = if (limit > 0) revenueCents.toFloat() / limit.toFloat() else 0f

        return MeiProjection(
            limitCents = limit,
            revenueCents = revenueCents,
            remainingCents = remaining,
            excessCents = excess,
            usage = usage,
            activeMonths = activeMonths
        )
    }
}
