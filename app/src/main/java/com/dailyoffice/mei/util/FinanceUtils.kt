package com.dailyoffice.mei.util

import java.text.NumberFormat
import java.util.Calendar
import java.util.Locale

object Money {
    private val br = NumberFormat.getCurrencyInstance(Locale("pt", "BR"))

    fun format(cents: Long): String = br.format(cents / 100.0)

    fun parseToCents(value: String): Long {
        val cleaned = value
            .replace("R$", "", ignoreCase = true)
            .replace(" ", "")
            .replace(".", "")
            .replace(",", ".")
        return cleaned.toBigDecimalOrNull()?.movePointRight(2)?.toLong() ?: 0L
    }
}

data class MeiSettings(
    val cnpj: String = "",
    val startMonth: Int = 1,
    val startYear: Int = Calendar.getInstance().get(Calendar.YEAR),
    val annualLimitCents: Long = 8_100_000L
)

object MeiCalculator {
    fun limitForYear(settings: MeiSettings, year: Int): Long {
        return when {
            year < settings.startYear -> 0L
            year > settings.startYear -> settings.annualLimitCents
            else -> {
                val months = (13 - settings.startMonth).coerceIn(1, 12)
                (settings.annualLimitCents / 12L) * months
            }
        }
    }
}
