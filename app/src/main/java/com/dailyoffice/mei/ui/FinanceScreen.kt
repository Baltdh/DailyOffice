package com.dailyoffice.mei.ui

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dailyoffice.mei.data.*
import com.dailyoffice.mei.finance.FinanceCalculator
import com.dailyoffice.mei.inventory.InventoryCalculator
import com.dailyoffice.mei.viewmodel.ReceiptViewModel
import java.text.NumberFormat
import java.time.Instant
import java.time.ZoneId
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale

private enum class TransferDialogMode {
    TRANSFER,
    REIMBURSEMENT
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FinanceScreen(
    viewModel: ReceiptViewModel,
    onBack: () -> Unit
) {
    val summary by viewModel.summary.collectAsStateWithLifecycle()
    val mei by viewModel.meiProjection.collectAsStateWithLifecycle()
    val config by viewModel.meiConfig.collectAsStateWithLifecycle()
    val transactions by viewModel.transactions.collectAsStateWithLifecycle()
    val activeCompany by viewModel.activeCompany.collectAsStateWithLifecycle()
    val accounts by viewModel.accounts.collectAsStateWithLifecycle()
    val accountFlows by viewModel.accountFlows.collectAsStateWithLifecycle()
    val stockMovements by viewModel.stockMovements.collectAsStateWithLifecycle()

    var addingKind by remember { mutableStateOf<EntryKind?>(null) }
    var showAccountDialog by remember { mutableStateOf(false) }
    var transferMode by remember { mutableStateOf<TransferDialogMode?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var annualLimit by rememberSaveable { mutableStateOf("") }
    var openingDate by rememberSaveable { mutableStateOf("") }
    var taxYear by rememberSaveable { mutableStateOf("") }
    var closingYear by rememberSaveable { mutableStateOf(YearMonth.now().year) }
    var closingMonth by rememberSaveable { mutableStateOf(YearMonth.now().monthValue) }

    val monthlyClosing = remember(
        transactions,
        accounts,
        closingYear,
        closingMonth
    ) {
        FinanceCalculator.monthlyClosing(
            transactions = transactions,
            accounts = accounts,
            year = closingYear,
            month = closingMonth
        )
    }

    val inventoryMonth = remember(
        stockMovements,
        activeCompany?.id,
        closingYear,
        closingMonth
    ) {
        InventoryCalculator.monthlySummary(
            movements = stockMovements,
            companyId = activeCompany?.id ?: -1L,
            year = closingYear,
            month = closingMonth
        )
    }

    LaunchedEffect(config) {
        annualLimit = "%.2f".format(Locale("pt", "BR"), config.annualLimitCents / 100.0)
        openingDate = "%02d/%02d/%04d".format(
            Locale("pt", "BR"),
            config.openingDay,
            config.openingMonth,
            config.openingYear
        )
        taxYear = config.taxYear.toString()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        if (activeCompany?.companyType == CompanyType.MEI) {
                            "Financeiro e MEI"
                        } else {
                            "Financeiro"
                        }
                    )
                },
                navigationIcon = {
                    TextButton(onClick = onBack) { Text("Voltar") }
                }
            )
        }
    ) { pad ->
        LazyColumn(
            modifier = Modifier
                .padding(pad)
                .fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Text("Resumo financeiro", style = MaterialTheme.typography.headlineSmall)
                Text(
                    activeCompany?.name ?: "Empresa ativa",
                    style = MaterialTheme.typography.bodyMedium
                )
                Text(
                    companyTypeLabel(activeCompany?.companyType ?: CompanyType.OTHER),
                    style = MaterialTheme.typography.bodySmall
                )
            }
            item {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FinanceMetric("Receitas", money(summary.revenueCents), Modifier.weight(1f))
                    FinanceMetric("Despesas empresa", money(summary.businessExpensesCents), Modifier.weight(1f))
                }
            }
            item {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FinanceMetric("Aportes", money(summary.contributionCents), Modifier.weight(1f))
                    FinanceMetric("Retiradas", money(summary.withdrawalCents), Modifier.weight(1f))
                }
            }
            item {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FinanceMetric(
                        "Débitos pendentes",
                        money(summary.pendingCents),
                        Modifier.weight(1f)
                    )
                    FinanceMetric(
                        "A receber",
                        money(summary.receivableCents),
                        Modifier.weight(1f)
                    )
                }
            }

            item {
                Card(Modifier.fillMaxWidth()) {
                    Column(
                        Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text(
                            "Fechamento mensal",
                            style = MaterialTheme.typography.titleLarge
                        )

                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            OutlinedButton(
                                onClick = {
                                    val previous = YearMonth.of(
                                        closingYear,
                                        closingMonth
                                    ).minusMonths(1)
                                    closingYear = previous.year
                                    closingMonth = previous.monthValue
                                }
                            ) {
                                Text("←")
                            }

                            Text(
                                monthYearLabel(closingYear, closingMonth),
                                style = MaterialTheme.typography.titleMedium
                            )

                            OutlinedButton(
                                onClick = {
                                    val next = YearMonth.of(
                                        closingYear,
                                        closingMonth
                                    ).plusMonths(1)
                                    closingYear = next.year
                                    closingMonth = next.monthValue
                                }
                            ) {
                                Text("→")
                            }
                        }

                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            FinanceMetric(
                                "Faturamento bruto",
                                money(monthlyClosing.grossRevenueCents),
                                Modifier.weight(1f)
                            )
                            FinanceMetric(
                                "Lucro estimado",
                                moneySigned(monthlyClosing.estimatedProfitCents),
                                Modifier.weight(1f)
                            )
                        }

                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            FinanceMetric(
                                "Despesas empresa",
                                money(monthlyClosing.businessExpensesCents),
                                Modifier.weight(1f)
                            )
                            FinanceMetric(
                                "A receber",
                                money(monthlyClosing.receivableCents),
                                Modifier.weight(1f)
                            )
                        }

                        Text(
                            "Pagas: ${money(monthlyClosing.paidBusinessExpensesCents)} • " +
                                "Pendentes/vencidas: ${money(monthlyClosing.pendingBusinessExpensesCents)}",
                            style = MaterialTheme.typography.bodySmall
                        )
                        Text(
                            "Pessoais: ${money(monthlyClosing.personalExpensesCents)} • " +
                                "Aportes: ${money(monthlyClosing.contributionCents)} • " +
                                "Retiradas: ${money(monthlyClosing.withdrawalCents)}",
                            style = MaterialTheme.typography.bodySmall
                        )
                        Text(
                            "Reembolsos: ${money(monthlyClosing.reimbursementCents)} • " +
                                "Transferências internas: ${money(monthlyClosing.transferCents)}",
                            style = MaterialTheme.typography.bodySmall
                        )
                        Text(
                            "Pago pessoalmente pelo titular no mês: " +
                                money(monthlyClosing.ownerFundedBusinessExpensesCents),
                            style = MaterialTheme.typography.bodySmall
                        )

                        HorizontalDivider()

                        Text(
                            "Estoque no mês",
                            style = MaterialTheme.typography.titleMedium
                        )
                        Text(
                            "Entradas por compra: ${inventoryMonth.purchaseEntries} • " +
                                "Custo vinculado: ${money(inventoryMonth.purchaseCostCents)}",
                            style = MaterialTheme.typography.bodySmall
                        )
                        Text(
                            "Consumos: ${inventoryMonth.consumptionEntries} • " +
                                "Perdas: ${inventoryMonth.lossEntries} • " +
                                "Ajustes: ${inventoryMonth.adjustmentEntries}",
                            style = MaterialTheme.typography.bodySmall
                        )
                        Text(
                            "Transferências de estoque: " +
                                "${inventoryMonth.transferOutEntries} saída(s) / " +
                                "${inventoryMonth.transferInEntries} entrada(s)",
                            style = MaterialTheme.typography.bodySmall
                        )
                        Text(
                            "${monthlyClosing.transactionCount} lançamento(s) considerados. " +
                                "O lucro estimado é faturamento bruto menos despesas empresariais registradas; " +
                                "transferências, aportes e reembolsos não entram no resultado.",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }

            item {
                Card(Modifier.fillMaxWidth()) {
                    Column(
                        Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            "Dinheiro pessoal usado na empresa",
                            style = MaterialTheme.typography.titleLarge
                        )
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            FinanceMetric(
                                "Pago pelo titular",
                                money(summary.ownerPaidBusinessExpensesCents),
                                Modifier.weight(1f)
                            )
                            FinanceMetric(
                                "Reembolsado",
                                money(summary.ownerReimbursedCents),
                                Modifier.weight(1f)
                            )
                        }
                        FinanceMetric(
                            "Ainda a reembolsar",
                            money(summary.ownerReimbursementOutstandingCents),
                            Modifier.fillMaxWidth()
                        )
                        Text(
                            "Reembolso é movimentação entre contas: não cria uma nova despesa e não entra como receita.",
                            style = MaterialTheme.typography.bodySmall
                        )
                        Button(
                            onClick = { transferMode = TransferDialogMode.REIMBURSEMENT },
                            enabled = summary.ownerReimbursementOutstandingCents > 0,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Reembolsar titular")
                        }
                    }
                }
            }

            item {
                Card(Modifier.fillMaxWidth()) {
                    Column(
                        Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            "Contas e origem do dinheiro",
                            style = MaterialTheme.typography.titleLarge
                        )
                        Text(
                            "A forma de pagamento (Pix, débito, cartão) é diferente da conta que forneceu ou recebeu o dinheiro.",
                            style = MaterialTheme.typography.bodySmall
                        )
                        accounts.forEach { account ->
                            val flow = accountFlows.firstOrNull { it.accountId == account.id }
                            Column(
                                Modifier.fillMaxWidth(),
                                verticalArrangement = Arrangement.spacedBy(2.dp)
                            ) {
                                Text(
                                    account.name,
                                    style = MaterialTheme.typography.titleSmall
                                )
                                Text(
                                    accountKindLabel(account.kind),
                                    style = MaterialTheme.typography.bodySmall
                                )
                                if (flow != null) {
                                    Text(
                                        "Entradas ${money(flow.inflowCents)} • " +
                                            "Saídas ${money(flow.outflowCents)} • " +
                                            "Líquido ${moneySigned(flow.netCents)}",
                                        style = MaterialTheme.typography.bodySmall
                                    )
                                }
                            }
                            HorizontalDivider()
                        }
                        Row(
                            Modifier.horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedButton(
                                onClick = { showAccountDialog = true }
                            ) {
                                Text("Adicionar conta")
                            }
                            Button(
                                onClick = { transferMode = TransferDialogMode.TRANSFER },
                                enabled = accounts.size >= 2
                            ) {
                                Text("Transferir entre contas")
                            }
                        }
                    }
                }
            }

            item {
                Text("Novo lançamento", style = MaterialTheme.typography.titleLarge)
                Text(
                    "Para o controle do MEI, registre o faturamento bruto como Receita. Aportes não entram no faturamento.",
                    style = MaterialTheme.typography.bodySmall
                )
            }
            item {
                Row(
                    Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(onClick = { addingKind = EntryKind.REVENUE }) { Text("Receita") }
                    OutlinedButton(onClick = { addingKind = EntryKind.EXPENSE }) { Text("Despesa") }
                    OutlinedButton(onClick = { addingKind = EntryKind.CONTRIBUTION }) { Text("Aporte") }
                    OutlinedButton(onClick = { addingKind = EntryKind.WITHDRAWAL }) { Text("Retirada") }
                }
            }

            item {
                if (activeCompany?.companyType == CompanyType.MEI) {
                    Card(Modifier.fillMaxWidth()) {
                        Column(
                            Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Text(
                                "Limite do MEI • ${config.taxYear}",
                                style = MaterialTheme.typography.titleLarge
                            )
                            Text("${money(mei.revenueCents)} de ${money(mei.limitCents)}")
                            LinearProgressIndicator(
                                progress = mei.usage.coerceIn(0f, 1f),
                                modifier = Modifier.fillMaxWidth()
                            )
                            Text(
                                if (mei.excessCents > 0) {
                                    "Acima do limite em ${money(mei.excessCents)}"
                                } else {
                                    "Restante: ${money(mei.remainingCents)}"
                                }
                            )
                            Text(
                                if (mei.isFirstYear) {
                                    "Primeiro ano: ${mei.activeMonths} mês(es) considerados desde a abertura."
                                } else {
                                    "Ano completo: 12 meses considerados."
                                },
                                style = MaterialTheme.typography.bodySmall
                            )
                            Text(
                                "Limite do primeiro ano: ${money(mei.firstYearLimitCents)}",
                                style = MaterialTheme.typography.bodySmall
                            )
                            Text(
                                "Limite anual após o primeiro ano: ${money(mei.annualLimitCents)}",
                                style = MaterialTheme.typography.bodySmall
                            )
                            Text(
                                "A partir de ${config.openingYear + 1}, o app usa automaticamente o limite anual completo.",
                                style = MaterialTheme.typography.bodySmall
                            )

                            HorizontalDivider()

                            OutlinedTextField(
                                value = annualLimit,
                                onValueChange = { annualLimit = it },
                                label = { Text("Teto anual MEI (R$)") },
                                modifier = Modifier.fillMaxWidth()
                            )
                            OutlinedTextField(
                                value = taxYear,
                                onValueChange = {
                                    taxYear = it.filter(Char::isDigit).take(4)
                                },
                                label = { Text("Ano fiscal") },
                                supportingText = {
                                    Text(
                                        "Somente receitas desse ano entram no limite exibido."
                                    )
                                },
                                modifier = Modifier.fillMaxWidth()
                            )
                            OutlinedTextField(
                                value = openingDate,
                                onValueChange = { openingDate = it.take(10) },
                                label = {
                                    Text("Data de abertura (dd/mm/aaaa)")
                                },
                                supportingText = {
                                    Text(
                                        "O primeiro ano é calculado proporcionalmente. Nos anos seguintes, o limite anual completo é aplicado automaticamente."
                                    )
                                },
                                modifier = Modifier.fillMaxWidth()
                            )
                            Button(
                                onClick = {
                                    viewModel.updateMeiConfig(
                                        annualLimit = annualLimit,
                                        openingDate = openingDate,
                                        taxYear = taxYear,
                                        onError = { error = it }
                                    )
                                },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text("Salvar configuração do MEI")
                            }
                        }
                    }
                } else {
                    Card(Modifier.fillMaxWidth()) {
                        Column(
                            Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                "Enquadramento da empresa",
                                style = MaterialTheme.typography.titleLarge
                            )
                            Text(
                                companyTypeLabel(
                                    activeCompany?.companyType ?: CompanyType.OTHER
                                ),
                                style = MaterialTheme.typography.titleMedium
                            )
                            Text(
                                "O controle de teto anual específico do MEI fica desativado para este enquadramento. O fechamento mensal, contas, estoque e fluxo financeiro continuam funcionando normalmente.",
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                }
            }

            error?.let {
                item { Text(it, color = MaterialTheme.colorScheme.error) }
            }

            item {
                Text("Lançamentos recentes", style = MaterialTheme.typography.titleLarge)
            }

            val recent = transactions.take(30)
            if (recent.isEmpty()) {
                item {
                    Card {
                        Text(
                            "Ainda não há lançamentos financeiros.",
                            Modifier.padding(16.dp)
                        )
                    }
                }
            } else {
                items(recent, key = { it.id }) { tx ->
                    TransactionCard(
                        transaction = tx,
                        accountName = accounts.firstOrNull { it.id == tx.accountId }?.name,
                        counterpartyAccountName = accounts.firstOrNull {
                            it.id == tx.counterpartyAccountId
                        }?.name,
                        onDelete = if (tx.receiptId == null) {
                            {
                                viewModel.deleteManualEntry(
                                    tx,
                                    onError = { error = it }
                                )
                            }
                        } else null
                    )
                }
            }

            item { Spacer(Modifier.height(24.dp)) }
        }
    }

    addingKind?.let { kind ->
        AddEntryDialog(
            kind = kind,
            accounts = accounts,
            onDismiss = { addingKind = null },
            onSave = { description, amount, ownership, method, status, accountId, date, due ->
                viewModel.addManualEntry(
                    description = description,
                    amount = amount,
                    kind = kind,
                    ownership = ownership,
                    paymentMethod = method,
                    paymentStatus = status,
                    accountId = accountId,
                    date = date,
                    dueDate = due,
                    onSaved = {
                        error = null
                        addingKind = null
                    },
                    onError = { error = it }
                )
            }
        )
    }

    if (showAccountDialog) {
        AddAccountDialog(
            onDismiss = { showAccountDialog = false },
            onSave = { name, kind ->
                viewModel.addAccount(
                    name = name,
                    kind = kind,
                    onSaved = {
                        showAccountDialog = false
                        error = null
                    },
                    onError = { error = it }
                )
            }
        )
    }

    transferMode?.let { mode ->
        AccountTransferDialog(
            mode = mode,
            accounts = accounts,
            outstandingCents = summary.ownerReimbursementOutstandingCents,
            onDismiss = { transferMode = null },
            onSave = { amount, sourceId, destinationId, method, date, note ->
                viewModel.addAccountTransfer(
                    amount = amount,
                    sourceAccountId = sourceId,
                    destinationAccountId = destinationId,
                    paymentMethod = method,
                    date = date,
                    reimbursement = mode == TransferDialogMode.REIMBURSEMENT,
                    note = note,
                    onSaved = {
                        transferMode = null
                        error = null
                    },
                    onError = { error = it }
                )
            }
        )
    }
}

@Composable
private fun FinanceMetric(title: String, value: String, modifier: Modifier = Modifier) {
    Card(modifier) {
        Column(Modifier.padding(14.dp)) {
            Text(title, style = MaterialTheme.typography.labelLarge)
            Text(value, style = MaterialTheme.typography.titleMedium)
        }
    }
}

@Composable
private fun TransactionCard(
    transaction: Transaction,
    accountName: String?,
    counterpartyAccountName: String?,
    onDelete: (() -> Unit)?
) {
    val kind = when (transaction.kind) {
        EntryKind.EXPENSE -> "Despesa"
        EntryKind.REVENUE -> "Receita"
        EntryKind.CONTRIBUTION -> "Aporte"
        EntryKind.WITHDRAWAL -> "Retirada"
        EntryKind.REIMBURSEMENT -> "Reembolso"
        EntryKind.TRANSFER -> "Transferência"
    }
    val source = if (transaction.receiptId == null) "Manual" else "Comprovante"

    Card(Modifier.fillMaxWidth()) {
        Column(
            Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(transaction.description, style = MaterialTheme.typography.titleMedium)
            Text("$kind • ${money(transaction.amountCents)} • $source")
            if (!accountName.isNullOrBlank()) {
                Text(
                    if (!counterpartyAccountName.isNullOrBlank()) {
                        "$accountName → $counterpartyAccountName"
                    } else {
                        "Conta: $accountName"
                    },
                    style = MaterialTheme.typography.bodySmall
                )
            }
            Text(formatDate(transaction.createdAt), style = MaterialTheme.typography.bodySmall)
            if (
                transaction.paymentStatus == PaymentStatus.PENDING ||
                transaction.paymentStatus == PaymentStatus.OVERDUE
            ) {
                Text(
                    if (transaction.paymentStatus == PaymentStatus.OVERDUE) "Vencido" else "Pendente",
                    color = MaterialTheme.colorScheme.error
                )
            }
            onDelete?.let {
                TextButton(onClick = it) { Text("Excluir lançamento") }
            }
        }
    }
}

@Composable
private fun AddEntryDialog(
    kind: EntryKind,
    accounts: List<Account>,
    onDismiss: () -> Unit,
    onSave: (
        description: String,
        amount: String,
        ownership: Ownership,
        method: PaymentMethod,
        status: PaymentStatus,
        accountId: Long?,
        date: String,
        dueDate: String
    ) -> Unit
) {
    var description by rememberSaveable { mutableStateOf("") }
    var amount by rememberSaveable { mutableStateOf("") }
    var date by rememberSaveable { mutableStateOf("") }
    var dueDate by rememberSaveable { mutableStateOf("") }
    var ownership by remember { mutableStateOf(Ownership.BUSINESS) }
    var method by remember { mutableStateOf(PaymentMethod.OTHER) }
    var status by remember { mutableStateOf(PaymentStatus.PAID) }
    var accountId by remember { mutableStateOf<Long?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                when (kind) {
                    EntryKind.EXPENSE -> "Nova despesa"
                    EntryKind.REVENUE -> "Nova receita"
                    EntryKind.CONTRIBUTION -> "Novo aporte"
                    EntryKind.WITHDRAWAL -> "Nova retirada"
                    EntryKind.REIMBURSEMENT -> "Novo reembolso"
                    EntryKind.TRANSFER -> "Nova transferência"
                }
            )
        },
        text = {
            Column(
                Modifier
                    .fillMaxWidth()
                    .heightIn(max = 520.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text("Descrição") },
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = amount,
                    onValueChange = { amount = it },
                    label = { Text("Valor (R$)") },
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = date,
                    onValueChange = { date = it },
                    label = { Text("Data (dd/mm/aaaa) - opcional") },
                    modifier = Modifier.fillMaxWidth()
                )

                if (kind == EntryKind.EXPENSE) {
                    Text("Classificação")
                    ChoiceChips(
                        values = listOf(Ownership.BUSINESS, Ownership.PERSONAL),
                        selected = ownership,
                        label = { if (it == Ownership.BUSINESS) "Empresa" else "Pessoal" },
                        onSelect = { ownership = it }
                    )
                }

                Text("Forma de pagamento")
                ChoiceChips(
                    values = PaymentMethod.entries,
                    selected = method,
                    label = {
                        when (it) {
                            PaymentMethod.CASH -> "Dinheiro"
                            PaymentMethod.PIX -> "Pix"
                            PaymentMethod.DEBIT -> "Débito"
                            PaymentMethod.CREDIT -> "Crédito"
                            PaymentMethod.DIGITAL_WALLET -> "Carteira"
                            PaymentMethod.OTHER -> "Outro"
                        }
                    },
                    onSelect = { method = it }
                )

                Text("Conta / origem")
                Text(
                    "Escolha de onde saiu ou para onde entrou o dinheiro.",
                    style = MaterialTheme.typography.bodySmall
                )
                Row(
                    Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    accounts.forEach { account ->
                        FilterChip(
                            selected = accountId == account.id,
                            onClick = { accountId = account.id },
                            label = { Text(account.name) }
                        )
                    }
                }

                Text("Situação")
                ChoiceChips(
                    values = listOf(
                        PaymentStatus.PAID,
                        PaymentStatus.PENDING,
                        PaymentStatus.OVERDUE
                    ),
                    selected = status,
                    label = {
                        when (it) {
                            PaymentStatus.PAID -> "Pago"
                            PaymentStatus.PENDING -> "Pendente"
                            PaymentStatus.OVERDUE -> "Vencido"
                            PaymentStatus.CANCELLED -> "Cancelado"
                        }
                    },
                    onSelect = { status = it }
                )

                if (status != PaymentStatus.PAID) {
                    OutlinedTextField(
                        value = dueDate,
                        onValueChange = { dueDate = it },
                        label = { Text("Vencimento (dd/mm/aaaa)") },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onSave(
                        description,
                        amount,
                        ownership,
                        method,
                        status,
                        accountId,
                        date,
                        dueDate
                    )
                }
            ) { Text("Salvar") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancelar") }
        }
    )
}

@Composable
private fun AccountTransferDialog(
    mode: TransferDialogMode,
    accounts: List<Account>,
    outstandingCents: Long,
    onDismiss: () -> Unit,
    onSave: (
        amount: String,
        sourceAccountId: Long?,
        destinationAccountId: Long?,
        method: PaymentMethod,
        date: String,
        note: String
    ) -> Unit
) {
    val reimbursement = mode == TransferDialogMode.REIMBURSEMENT

    val sourceOptions = if (reimbursement) {
        accounts.filter {
            it.kind == AccountKind.BUSINESS_BANK ||
                it.kind == AccountKind.BUSINESS_CASH
        }
    } else {
        accounts
    }

    val destinationOptions = if (reimbursement) {
        accounts.filter {
            it.kind == AccountKind.OWNER_PERSONAL_BANK ||
                it.kind == AccountKind.OWNER_PERSONAL_CARD
        }
    } else {
        accounts
    }

    var amount by rememberSaveable(mode) { mutableStateOf("") }
    var date by rememberSaveable(mode) { mutableStateOf("") }
    var note by rememberSaveable(mode) { mutableStateOf("") }
    var method by remember(mode) { mutableStateOf(PaymentMethod.PIX) }
    var sourceId by remember(mode, accounts) {
        mutableStateOf(sourceOptions.firstOrNull()?.id)
    }
    var destinationId by remember(mode, accounts) {
        mutableStateOf(destinationOptions.firstOrNull { it.id != sourceId }?.id)
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                if (reimbursement) {
                    "Reembolsar titular"
                } else {
                    "Transferir entre contas"
                }
            )
        },
        text = {
            Column(
                Modifier
                    .fillMaxWidth()
                    .heightIn(max = 520.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                if (reimbursement) {
                    Text(
                        "Pendente de reembolso: ${money(outstandingCents)}",
                        style = MaterialTheme.typography.titleMedium
                    )
                    Text(
                        "O reembolso devolve dinheiro ao titular sem criar nova despesa ou receita.",
                        style = MaterialTheme.typography.bodySmall
                    )
                } else {
                    Text(
                        "Transferências movimentam valores entre contas sem virar receita ou despesa.",
                        style = MaterialTheme.typography.bodySmall
                    )
                }

                OutlinedTextField(
                    value = amount,
                    onValueChange = { amount = it },
                    label = { Text("Valor (R$)") },
                    modifier = Modifier.fillMaxWidth()
                )

                Text("Conta de origem")
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    sourceOptions.forEach { account ->
                        FilterChip(
                            selected = sourceId == account.id,
                            onClick = {
                                sourceId = account.id
                                if (destinationId == account.id) {
                                    destinationId = destinationOptions
                                        .firstOrNull { it.id != account.id }?.id
                                }
                            },
                            label = { Text(account.name) },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }

                Text("Conta de destino")
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    destinationOptions
                        .filter { it.id != sourceId }
                        .forEach { account ->
                            FilterChip(
                                selected = destinationId == account.id,
                                onClick = { destinationId = account.id },
                                label = { Text(account.name) },
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                }

                Text("Forma")
                ChoiceChips(
                    values = PaymentMethod.entries,
                    selected = method,
                    label = ::paymentMethodLabel,
                    onSelect = { method = it }
                )

                OutlinedTextField(
                    value = date,
                    onValueChange = { date = it },
                    label = { Text("Data (dd/mm/aaaa) - opcional") },
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = note,
                    onValueChange = { note = it },
                    label = { Text("Descrição / observação - opcional") },
                    minLines = 2,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onSave(
                        amount,
                        sourceId,
                        destinationId,
                        method,
                        date,
                        note
                    )
                },
                enabled = sourceId != null &&
                    destinationId != null &&
                    sourceId != destinationId
            ) {
                Text(if (reimbursement) "Registrar reembolso" else "Transferir")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancelar") }
        }
    )
}

@Composable
private fun AddAccountDialog(
    onDismiss: () -> Unit,
    onSave: (String, AccountKind) -> Unit
) {
    var name by rememberSaveable { mutableStateOf("") }
    var kind by remember { mutableStateOf(AccountKind.BUSINESS_BANK) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Nova conta") },
        text = {
            Column(
                Modifier
                    .fillMaxWidth()
                    .heightIn(max = 420.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Nome da conta") },
                    placeholder = { Text("Ex.: Nubank PJ, dinheiro, cartão pessoal") },
                    modifier = Modifier.fillMaxWidth()
                )
                Text("Tipo")
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    AccountKind.entries.forEach { candidate ->
                        FilterChip(
                            selected = kind == candidate,
                            onClick = { kind = candidate },
                            label = { Text(accountKindLabel(candidate)) },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = { onSave(name, kind) }) {
                Text("Cadastrar")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancelar") }
        }
    )
}

private fun monthYearLabel(year: Int, month: Int): String {
    val names = listOf(
        "Janeiro", "Fevereiro", "Março", "Abril",
        "Maio", "Junho", "Julho", "Agosto",
        "Setembro", "Outubro", "Novembro", "Dezembro"
    )
    return "${names[month.coerceIn(1, 12) - 1]} / $year"
}

private fun companyTypeLabel(type: CompanyType): String =
    when (type) {
        CompanyType.MEI -> "MEI — Microempreendedor Individual"
        CompanyType.ME -> "ME — Microempresa"
        CompanyType.EPP -> "EPP — Empresa de Pequeno Porte"
        CompanyType.OTHER -> "Outro enquadramento"
    }

private fun paymentMethodLabel(method: PaymentMethod): String =
    when (method) {
        PaymentMethod.CASH -> "Dinheiro"
        PaymentMethod.PIX -> "Pix"
        PaymentMethod.DEBIT -> "Débito"
        PaymentMethod.CREDIT -> "Crédito"
        PaymentMethod.DIGITAL_WALLET -> "Carteira"
        PaymentMethod.OTHER -> "Outro"
    }

private fun accountKindLabel(kind: AccountKind): String =
    when (kind) {
        AccountKind.BUSINESS_BANK -> "Conta bancária da empresa"
        AccountKind.BUSINESS_CASH -> "Dinheiro da empresa"
        AccountKind.BUSINESS_CARD -> "Cartão da empresa"
        AccountKind.OWNER_PERSONAL_BANK -> "Conta pessoal do titular"
        AccountKind.OWNER_PERSONAL_CARD -> "Cartão pessoal do titular"
        AccountKind.IFOOD_RECEIVABLE -> "Recebíveis do iFood"
        AccountKind.OTHER -> "Outra"
    }

@Composable
private fun <T> ChoiceChips(
    values: Iterable<T>,
    selected: T,
    label: (T) -> String,
    onSelect: (T) -> Unit
) {
    Row(
        Modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        values.forEach { value ->
            FilterChip(
                selected = value == selected,
                onClick = { onSelect(value) },
                label = { Text(label(value)) }
            )
        }
    }
}

private fun money(cents: Long): String =
    NumberFormat.getCurrencyInstance(Locale("pt", "BR")).format(cents / 100.0)

private fun moneySigned(cents: Long): String {
    val formatted = money(kotlin.math.abs(cents))
    return if (cents < 0) "-$formatted" else "+$formatted"
}

private fun formatDate(epoch: Long): String =
    DateTimeFormatter.ofPattern("dd/MM/yyyy")
        .withZone(ZoneId.systemDefault())
        .format(Instant.ofEpochMilli(epoch))
