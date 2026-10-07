package com.dailyoffice.mei.finance

import com.dailyoffice.mei.data.Account
import com.dailyoffice.mei.data.AccountKind
import com.dailyoffice.mei.data.EntryKind
import com.dailyoffice.mei.data.Ownership
import com.dailyoffice.mei.data.PaymentStatus
import com.dailyoffice.mei.data.Transaction
import java.time.Instant
import java.time.ZoneId

data class FinancialTotals(
    val businessExpensesCents: Long = 0,
    val personalExpensesCents: Long = 0,
    val revenueCents: Long = 0,
    val contributionCents: Long = 0,
    val withdrawalCents: Long = 0,
    val pendingCents: Long = 0,
    val receivableCents: Long = 0
)

object FinanceCalculator {
    fun summarize(transactions: List<Transaction>): FinancialTotals {
        val active = transactions.filter {
            it.paymentStatus != PaymentStatus.CANCELLED
        }

        return FinancialTotals(
            businessExpensesCents = active
                .filter {
                    it.kind == EntryKind.EXPENSE &&
                        it.ownership == Ownership.BUSINESS
                }
                .sumOf { it.amountCents },
            personalExpensesCents = active
                .filter {
                    it.kind == EntryKind.EXPENSE &&
                        it.ownership == Ownership.PERSONAL
                }
                .sumOf { it.amountCents },
            revenueCents = active
                .filter { it.kind == EntryKind.REVENUE }
                .sumOf { it.amountCents },
            contributionCents = active
                .filter { it.kind == EntryKind.CONTRIBUTION }
                .sumOf { it.amountCents },
            withdrawalCents = active
                .filter { it.kind == EntryKind.WITHDRAWAL }
                .sumOf { it.amountCents },
            pendingCents = active
                .filter {
                    it.kind == EntryKind.EXPENSE &&
                        (
                            it.paymentStatus == PaymentStatus.PENDING ||
                                it.paymentStatus == PaymentStatus.OVERDUE
                            )
                }
                .sumOf { it.amountCents },
            receivableCents = active
                .filter {
                    it.kind == EntryKind.REVENUE &&
                        (
                            it.paymentStatus == PaymentStatus.PENDING ||
                                it.paymentStatus == PaymentStatus.OVERDUE
                            )
                }
                .sumOf { it.amountCents }
        )
    }

    fun ownerPaidBusinessExpenses(
        transactions: List<Transaction>,
        accounts: List<Account>
    ): Long {
        val accountById = accounts.associateBy { it.id }

        return transactions.asSequence()
            .filter {
                it.kind == EntryKind.EXPENSE &&
                    it.ownership == Ownership.BUSINESS &&
                    it.paymentStatus == PaymentStatus.PAID
            }
            .filter { transaction ->
                when (accountById[transaction.accountId]?.kind) {
                    AccountKind.OWNER_PERSONAL_BANK,
                    AccountKind.OWNER_PERSONAL_CARD -> true
                    else -> false
                }
            }
            .sumOf { it.amountCents }
    }

    fun revenueForYear(
        transactions: List<Transaction>,
        year: Int,
        zoneId: ZoneId = ZoneId.systemDefault()
    ): Long =
        transactions.asSequence()
            .filter {
                it.kind == EntryKind.REVENUE &&
                    it.paymentStatus != PaymentStatus.CANCELLED
            }
            .filter {
                Instant.ofEpochMilli(it.createdAt)
                    .atZone(zoneId)
                    .year == year
            }
            .sumOf { it.amountCents }
}
