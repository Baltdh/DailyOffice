package com.dailyoffice.mei.finance

import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.ResolverStyle

object DateInput {
    private val format = DateTimeFormatter.ofPattern("d/M/uuuu")
        .withResolverStyle(ResolverStyle.STRICT)

    fun parse(value: String): LocalDate? = runCatching {
        LocalDate.parse(value.trim().replace('.', '/').replace('-', '/'), format)
    }.getOrNull()

    fun epoch(value: String, zoneId: ZoneId = ZoneId.systemDefault()): Long? =
        parse(value)?.atStartOfDay(zoneId)?.toInstant()?.toEpochMilli()
}
