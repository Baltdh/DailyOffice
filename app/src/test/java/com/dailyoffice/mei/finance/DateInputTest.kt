package com.dailyoffice.mei.finance

import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

class DateInputTest {
    @Test fun rejectsImpossibleDatesInsteadOfAdjustingThem() {
        listOf("31/02/2026", "29/02/2025", "31/04/2026", "00/10/2026", "10/13/2026", "texto", "").forEach {
            assertNull(it, DateInput.parse(it))
        }
    }
    @Test fun acceptsLeapYearsSeparatorsAndWhitespace() {
        assertEquals(LocalDate.of(2024, 2, 29), DateInput.parse("29/02/2024"))
        listOf(" 1/10/2026 ", "1.10.2026", "1-10-2026").forEach {
            assertEquals(LocalDate.of(2026, 10, 1), DateInput.parse(it))
        }
    }
    @Test fun convertsAtMidnightInSelectedTimezone() {
        assertEquals(java.time.Instant.parse("2026-10-01T03:00:00Z").toEpochMilli(),
            DateInput.epoch("01/10/2026", ZoneId.of("America/Sao_Paulo")))
    }
}
