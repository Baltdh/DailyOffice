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

data class AccountFlow(
    val accountId: Long,
    val inflowCents: Long = 0,
    val outflowCents: Long = 0
) {
    val netCents: Long
        get() = inflowCents - outflowCents
}

data class MonthlyClosing(
    val year: Int,
    val month: Int,
    val grossRevenueCents: Long = 0,
    val businessExpensesCents: Long = 0,
    val paidBusinessExpensesCents: Long = 0,
    val pendingBusinessExpensesCents: Long = 0,
    val personalExpensesCents: Long = 0,
    val contributionCents: Long = 0,
    val withdrawalCents: Long = 0,
    val reimbursementCents: Long = 0,
    val transferCents: Long = 0,
    val receivableCents: Long = 0,
    val ownerFundedBusinessExpensesCents: Long = 0,
    val estimatedProfitCents: Long = 0,
    val transactionCount: Int = 0
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

    fun ownerReimbursedCents(
        transactions: List<Transaction>,
        accounts: List<Account>
    ): Long {
        val accountById = accounts.associateBy { it.id }

        return transactions.asSequence()
            .filter {
                it.kind == EntryKind.REIMBURSEMENT &&
                    it.paymentStatus == PaymentStatus.PAID
            }
            .filter { transaction ->
                val sourceKind = accountById[transaction.accountId]?.kind
                val destinationKind = accountById[transaction.counterpartyAccountId]?.kind

                val sourceIsBusiness = sourceKind == AccountKind.BUSINESS_BANK ||
                    sourceKind == AccountKind.BUSINESS_CASH

                val destinationIsOwner = destinationKind == AccountKind.OWNER_PERSONAL_BANK ||
                    destinationKind == AccountKind.OWNER_PERSONAL_CARD

                sourceIsBusiness && destinationIsOwner
            }
            .sumOf { it.amountCents }
    }

    fun ownerReimbursementOutstandingCents(
        transactions: List<Transaction>,
        accounts: List<Account>
    ): Long {
        val paidPersonally = ownerPaidBusinessExpenses(transactions, accounts)
        val reimbursed = ownerReimbursedCents(transactions, accounts)
        return (paidPersonally - reimbursed).coerceAtLeast(0L)
    }

    fun accountFlows(transactions: List<Transaction>): List<AccountFlow> {
        val totals = linkedMapOf<Long, Pair<Long, Long>>()

        fun addInflow(accountId: Long?, amount: Long) {
            if (accountId == null) return
            val current = totals[accountId] ?: (0L to 0L)
            totals[accountId] = (current.first + amount) to current.second
        }

        fun addOutflow(accountId: Long?, amount: Long) {
            if (accountId == null) return
            val current = totals[accountId] ?: (0L to 0L)
            totals[accountId] = current.first to (current.second + amount)
        }

        transactions.asSequence()
            .filter { it.paymentStatus == PaymentStatus.PAID }
            .forEach { transaction ->
                when (transaction.kind) {
                    EntryKind.EXPENSE -> addOutflow(transaction.accountId, transaction.amountCents)
                    EntryKind.REVENUE,
                    EntryKind.CONTRIBUTION -> addInflow(transaction.accountId, transaction.amountCents)
                    EntryKind.WITHDRAWAL -> addOutflow(transaction.accountId, transaction.amountCents)
                    EntryKind.REIMBURSEMENT,
                    EntryKind.TRANSFER -> {
                        addOutflow(transaction.accountId, transaction.amountCents)
                        addInflow(transaction.counterpartyAccountId, transaction.amountCents)
                    }
                }
            }

        return totals.map { (accountId, pair) ->
            AccountFlow(
                accountId = accountId,
                inflowCents = pair.first,
                outflowCents = pair.second
            )
        }
    }

    fun monthlyClosing(
        transactions: List<Transaction>,
        accounts: List<Account>,
        year: Int,
        month: Int,
        zoneId: ZoneId = ZoneId.systemDefault()
    ): MonthlyClosing {
        val safeMonth = month.coerceIn(1, 12)
        val monthTransactions = transactions.filter { transaction ->
            if (transaction.paymentStatus == PaymentStatus.CANCELLED) {
                return@filter false
            }

            val date = Instant.ofEpochMilli(transaction.createdAt)
                .atZone(zoneId)

            date.year == year && date.monthValue == safeMonth
        }

        val grossRevenue = monthTransactions
            .filter { it.kind == EntryKind.REVENUE }
            .sumOf { it.amountCents }

        val businessExpenses = monthTransactions
            .filter {
                it.kind == EntryKind.EXPENSE &&
                    it.ownership == Ownership.BUSINESS
            }
            .sumOf { it.amountCents }

        val paidBusinessExpenses = monthTransactions
            .filter {
                it.kind == EntryKind.EXPENSE &&
                    it.ownership == Ownership.BUSINESS &&
                    it.paymentStatus == PaymentStatus.PAID
            }
            .sumOf { it.amountCents }

        val pendingBusinessExpenses = monthTransactions
            .filter {
                it.kind == EntryKind.EXPENSE &&
                    it.ownership == Ownership.BUSINESS &&
                    (
                        it.paymentStatus == PaymentStatus.PENDING ||
                            it.paymentStatus == PaymentStatus.OVERDUE
                        )
            }
            .sumOf { it.amountCents }

        val personalExpenses = monthTransactions
            .filter {
                it.kind == EntryKind.EXPENSE &&
                    it.ownership == Ownership.PERSONAL
            }
            .sumOf { it.amountCents }

        val contributions = monthTransactions
            .filter { it.kind == EntryKind.CONTRIBUTION }
            .sumOf { it.amountCents }

        val withdrawals = monthTransactions
            .filter { it.kind == EntryKind.WITHDRAWAL }
            .sumOf { it.amountCents }

        val reimbursements = monthTransactions
            .filter { it.kind == EntryKind.REIMBURSEMENT }
            .sumOf { it.amountCents }

        val transfers = monthTransactions
            .filter { it.kind == EntryKind.TRANSFER }
            .sumOf { it.amountCents }

        val receivables = monthTransactions
            .filter {
                it.kind == EntryKind.REVENUE &&
                    (
                        it.paymentStatus == PaymentStatus.PENDING ||
                            it.paymentStatus == PaymentStatus.OVERDUE
                        )
            }
            .sumOf { it.amountCents }

        val ownerFunded = ownerPaidBusinessExpenses(
            transactions = monthTransactions,
            accounts = accounts
        )

        return MonthlyClosing(
            year = year,
            month = safeMonth,
            grossRevenueCents = grossRevenue,
            businessExpensesCents = businessExpenses,
            paidBusinessExpensesCents = paidBusinessExpenses,
            pendingBusinessExpensesCents = pendingBusinessExpenses,
            personalExpensesCents = personalExpenses,
            contributionCents = contributions,
            withdrawalCents = withdrawals,
            reimbursementCents = reimbursements,
            transferCents = transfers,
            receivableCents = receivables,
            ownerFundedBusinessExpensesCents = ownerFunded,
            estimatedProfitCents = grossRevenue - businessExpenses,
            transactionCount = monthTransactions.size
        )
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
