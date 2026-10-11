package com.dailyoffice.mei.finance

import com.dailyoffice.mei.data.Account
import com.dailyoffice.mei.data.AccountKind
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
    fun ownerPaidBusinessExpensesUsePaymentSourceNotExpenseOwnership() {
        val accounts = listOf(
            Account(
                id = 1,
                companyId = 1,
                name = "Conta PJ",
                kind = AccountKind.BUSINESS_BANK
            ),
            Account(
                id = 2,
                companyId = 1,
                name = "Cartão pessoal",
                kind = AccountKind.OWNER_PERSONAL_CARD
            )
        )
        val transactions = listOf(
            Transaction(
                accountId = 2,
                description = "Embalagens",
                amountCents = 8_749,
                ownership = Ownership.BUSINESS,
                kind = EntryKind.EXPENSE,
                paymentStatus = PaymentStatus.PAID
            ),
            Transaction(
                accountId = 1,
                description = "Salmão",
                amountCents = 30_000,
                ownership = Ownership.BUSINESS,
                kind = EntryKind.EXPENSE,
                paymentStatus = PaymentStatus.PAID
            ),
            Transaction(
                accountId = 2,
                description = "Despesa pessoal",
                amountCents = 5_000,
                ownership = Ownership.PERSONAL,
                kind = EntryKind.EXPENSE,
                paymentStatus = PaymentStatus.PAID
            ),
            Transaction(
                accountId = 2,
                description = "Compra ainda pendente",
                amountCents = 4_000,
                ownership = Ownership.BUSINESS,
                kind = EntryKind.EXPENSE,
                paymentStatus = PaymentStatus.PENDING
            )
        )

        val result = FinanceCalculator.ownerPaidBusinessExpenses(
            transactions,
            accounts
        )

        assertEquals(8_749L, result)
    }

    @Test
    fun reimbursementReducesOutstandingOwnerBalance() {
        val accounts = listOf(
            Account(
                id = 1,
                companyId = 1,
                name = "Conta PJ",
                kind = AccountKind.BUSINESS_BANK
            ),
            Account(
                id = 2,
                companyId = 1,
                name = "Conta pessoal",
                kind = AccountKind.OWNER_PERSONAL_BANK
            )
        )
        val transactions = listOf(
            Transaction(
                accountId = 2,
                description = "Compra paga pelo titular",
                amountCents = 20_000,
                ownership = Ownership.BUSINESS,
                kind = EntryKind.EXPENSE,
                paymentStatus = PaymentStatus.PAID
            ),
            Transaction(
                accountId = 1,
                counterpartyAccountId = 2,
                description = "Reembolso parcial",
                amountCents = 7_500,
                ownership = Ownership.BUSINESS,
                kind = EntryKind.REIMBURSEMENT,
                paymentStatus = PaymentStatus.PAID
            )
        )

        assertEquals(
            7_500L,
            FinanceCalculator.ownerReimbursedCents(transactions, accounts)
        )
        assertEquals(
            12_500L,
            FinanceCalculator.ownerReimbursementOutstandingCents(
                transactions,
                accounts
            )
        )
    }

    @Test
    fun transfersMoveAccountFlowWithoutChangingIncomeOrExpenseTotals() {
        val transactions = listOf(
            Transaction(
                accountId = 1,
                counterpartyAccountId = 2,
                description = "Transferência interna",
                amountCents = 15_000,
                ownership = Ownership.BUSINESS,
                kind = EntryKind.TRANSFER,
                paymentStatus = PaymentStatus.PAID
            )
        )

        val totals = FinanceCalculator.summarize(transactions)
        val flows = FinanceCalculator.accountFlows(transactions)

        assertEquals(0L, totals.revenueCents)
        assertEquals(0L, totals.businessExpensesCents)
        assertEquals(15_000L, flows.first { it.accountId == 1L }.outflowCents)
        assertEquals(15_000L, flows.first { it.accountId == 2L }.inflowCents)
    }

    @Test
    fun monthlyClosingSeparatesRevenueExpensesAndInternalMovements() {
        val accounts = listOf(
            Account(
                id = 1,
                companyId = 1,
                name = "Conta PJ",
                kind = AccountKind.BUSINESS_BANK
            ),
            Account(
                id = 2,
                companyId = 1,
                name = "Conta pessoal",
                kind = AccountKind.OWNER_PERSONAL_BANK
            )
        )

        val transactions = listOf(
            transactionAt(
                2026, 10, 3,
                amount = 100_000,
                kind = EntryKind.REVENUE,
                ownership = Ownership.BUSINESS,
                accountId = 1
            ),
            transactionAt(
                2026, 10, 4,
                amount = 30_000,
                kind = EntryKind.EXPENSE,
                ownership = Ownership.BUSINESS,
                accountId = 1
            ),
            transactionAt(
                2026, 10, 5,
                amount = 8_000,
                kind = EntryKind.EXPENSE,
                ownership = Ownership.BUSINESS,
                accountId = 2
            ),
            transactionAt(
                2026, 10, 6,
                amount = 12_000,
                kind = EntryKind.TRANSFER,
                ownership = Ownership.BUSINESS,
                accountId = 1,
                counterpartyAccountId = 2
            ),
            transactionAt(
                2026, 9, 30,
                amount = 999_999,
                kind = EntryKind.REVENUE,
                ownership = Ownership.BUSINESS,
                accountId = 1
            ),
            transactionAt(
                2026, 10, 8,
                amount = 50_000,
                kind = EntryKind.REVENUE,
                ownership = Ownership.BUSINESS,
                accountId = 1,
                status = PaymentStatus.CANCELLED
            )
        )

        val result = FinanceCalculator.monthlyClosing(
            transactions = transactions,
            accounts = accounts,
            year = 2026,
            month = 10,
            zoneId = utc
        )

        assertEquals(100_000L, result.grossRevenueCents)
        assertEquals(38_000L, result.businessExpensesCents)
        assertEquals(62_000L, result.estimatedProfitCents)
        assertEquals(8_000L, result.ownerFundedBusinessExpensesCents)
        assertEquals(12_000L, result.transferCents)
        assertEquals(4, result.transactionCount)
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

    @Test
    fun cashFlowUsesSettlementMonthWhileClosingKeepsOriginalMonth() {
        val purchase = transactionAt(2026, 9, 20, 8_000,
            EntryKind.EXPENSE, Ownership.BUSINESS, accountId = 1).copy(
            paidAt = LocalDate.of(2026, 10, 5).atStartOfDay(utc).toInstant().toEpochMilli()
        )
        val sale = transactionAt(2026, 9, 25, 20_000,
            EntryKind.REVENUE, Ownership.BUSINESS, accountId = 1).copy(paidAt = purchase.paidAt)
        val transactions = listOf(purchase, sale)

        assertEquals(emptyList<Any>(), FinanceCalculator.monthlyAccountFlows(transactions, 2026, 9, utc))
        val october = FinanceCalculator.monthlyAccountFlows(transactions, 2026, 10, utc).single()
        assertEquals(20_000L, october.inflowCents)
        assertEquals(8_000L, october.outflowCents)
        assertEquals(12_000L, october.netCents)
        assertEquals(12_000L, FinanceCalculator.monthlyClosing(transactions, emptyList(), 2026, 9, utc).estimatedProfitCents)
        assertEquals(0L, FinanceCalculator.monthlyClosing(transactions, emptyList(), 2026, 10, utc).estimatedProfitCents)
    }

    @Test
    fun monthlyCashFlowExcludesUnpaidCancelledAndOtherYears() {
        val paid = transactionAt(2026, 10, 1, 1_000,
            EntryKind.REVENUE, Ownership.BUSINESS, accountId = 1)
        val transactions = listOf(paid,
            paid.copy(amountCents = 9_000, paymentStatus = PaymentStatus.PENDING),
            paid.copy(amountCents = 8_000, paymentStatus = PaymentStatus.OVERDUE),
            paid.copy(amountCents = 7_000, paymentStatus = PaymentStatus.CANCELLED),
            paid.copy(amountCents = 6_000,
                paidAt = LocalDate.of(2025, 10, 1).atStartOfDay(utc).toInstant().toEpochMilli()))

        // Older paid records without paidAt fall back to createdAt.
        assertEquals(1_000L, FinanceCalculator.monthlyAccountFlows(transactions, 2026, 10, utc).single().inflowCents)
    }

    @Test
    fun monthlyCashTransfersHaveMatchingSourceAndDestination() {
        val transfer = transactionAt(2026, 9, 30, 5_000,
            EntryKind.TRANSFER, Ownership.BUSINESS, accountId = 1,
            counterpartyAccountId = 2).copy(
            paidAt = LocalDate.of(2026, 10, 2).atStartOfDay(utc).toInstant().toEpochMilli())
        val flows = FinanceCalculator.monthlyAccountFlows(listOf(transfer), 2026, 10, utc)
        assertEquals(-5_000L, flows.first { it.accountId == 1L }.netCents)
        assertEquals(5_000L, flows.first { it.accountId == 2L }.netCents)
        assertEquals(0L, flows.sumOf { it.netCents })
    }

    @Test
    fun monthlyCashFlowRespectsLocalMonthBoundary() {
        val transaction = transactionAt(2026, 10, 1, 2_000,
            EntryKind.EXPENSE, Ownership.BUSINESS, accountId = 1).copy(
            paidAt = java.time.Instant.parse("2026-10-01T01:30:00Z").toEpochMilli())
        val brazil = ZoneId.of("America/Sao_Paulo")
        assertEquals(2_000L, FinanceCalculator.monthlyAccountFlows(listOf(transaction), 2026, 9, brazil).single().outflowCents)
        assertEquals(emptyList<Any>(), FinanceCalculator.monthlyAccountFlows(listOf(transaction), 2026, 10, brazil))
    }

    private fun transactionAt(
        year: Int,
        month: Int,
        day: Int,
        amount: Long,
        kind: EntryKind,
        ownership: Ownership,
        accountId: Long? = null,
        counterpartyAccountId: Long? = null,
        status: PaymentStatus = PaymentStatus.PAID
    ): Transaction =
        Transaction(
            accountId = accountId,
            counterpartyAccountId = counterpartyAccountId,
            description = "Teste",
            amountCents = amount,
            ownership = ownership,
            kind = kind,
            paymentStatus = status,
            createdAt = LocalDate.of(year, month, day)
                .atStartOfDay(utc)
                .toInstant()
                .toEpochMilli()
        )

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
