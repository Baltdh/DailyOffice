package com.dailyoffice.mei.finance

import com.dailyoffice.mei.data.EntryKind
import com.dailyoffice.mei.data.Ownership
import com.dailyoffice.mei.data.PaymentStatus
import com.dailyoffice.mei.data.Transaction
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

class FinanceCalculatorTest {
    private val utc = ZoneId.of("UTC")

    @Test
    fun cancelledEntriesDoNotAffectFinancialTotals() {
        val transactions = listOf(
            Transaction(
                description = "Compra válida",
                amountCents = 10_000,
                ownership = Ownership.BUSINESS,
                kind = EntryKind.EXPENSE,
                paymentStatus = PaymentStatus.PAID
            ),
            Transaction(
                description = "Compra cancelada",
                amountCents = 99_000,
                ownership = Ownership.BUSINESS,
                kind = EntryKind.EXPENSE,
                paymentStatus = PaymentStatus.CANCELLED
            ),
            Transaction(
                description = "Venda cancelada",
                amountCents = 50_000,
                ownership = Ownership.BUSINESS,
                kind = EntryKind.REVENUE,
                paymentStatus = PaymentStatus.CANCELLED
            )
        )

        val result = FinanceCalculator.summarize(transactions)

        assertEquals(10_000L, result.businessExpensesCents)
        assertEquals(0L, result.revenueCents)
    }

    @Test
    fun meiRevenueUsesOnlySelectedCalendarYear() {
        val transactions = listOf(
            revenue(2025, 12, 31, 20_000),
            revenue(2026, 1, 1, 30_000),
            revenue(2026, 10, 7, 40_000),
            revenue(
                2026,
                5,
                1,
                100_000,
                status = PaymentStatus.CANCELLED
            )
        )

        val revenue2026 = FinanceCalculator.revenueForYear(
            transactions = transactions,
            year = 2026,
            zoneId = utc
        )

        assertEquals(70_000L, revenue2026)
    }

    private fun revenue(
        year: Int,
        month: Int,
        day: Int,
        cents: Long,
        status: PaymentStatus = PaymentStatus.PAID
    ): Transaction =
        Transaction(
            description = "Receita",
            amountCents = cents,
            ownership = Ownership.BUSINESS,
            kind = EntryKind.REVENUE,
            paymentStatus = status,
            createdAt = LocalDate.of(year, month, day)
                .atStartOfDay(utc)
                .toInstant()
                .toEpochMilli()
        )
}
