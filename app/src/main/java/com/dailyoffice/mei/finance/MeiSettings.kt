package com.dailyoffice.mei.finance

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

data class MeiConfig(
    val annualLimitCents: Long = 8_100_000L,
    val openingMonth: Int = 8,
    val proportionalFirstYear: Boolean = true
)

class MeiSettings(context: Context) {
    private val prefs = context.getSharedPreferences("mei_settings", Context.MODE_PRIVATE)

    private val _state = MutableStateFlow(
        MeiConfig(
            annualLimitCents = prefs.getLong("annualLimitCents", 8_100_000L),
            openingMonth = prefs.getInt("openingMonth", 8),
            proportionalFirstYear = prefs.getBoolean("proportionalFirstYear", true)
        )
    )

    val state: StateFlow<MeiConfig> = _state

    fun update(config: MeiConfig) {
        val normalized = config.copy(
            annualLimitCents = config.annualLimitCents.coerceAtLeast(0L),
            openingMonth = config.openingMonth.coerceIn(1, 12)
        )
        prefs.edit()
            .putLong("annualLimitCents", normalized.annualLimitCents)
            .putInt("openingMonth", normalized.openingMonth)
            .putBoolean("proportionalFirstYear", normalized.proportionalFirstYear)
            .apply()
        _state.value = normalized
    }
}
