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
import com.dailyoffice.mei.viewmodel.ReceiptViewModel
import java.text.NumberFormat
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

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

    var addingKind by remember { mutableStateOf<EntryKind?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var annualLimit by rememberSaveable { mutableStateOf("") }
    var openingMonth by rememberSaveable { mutableStateOf("") }
    var firstYear by rememberSaveable { mutableStateOf(true) }

    LaunchedEffect(config) {
        annualLimit = "%.2f".format(Locale("pt", "BR"), config.annualLimitCents / 100.0)
        openingMonth = config.openingMonth.toString()
        firstYear = config.proportionalFirstYear
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Financeiro e MEI") },
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
                FinanceMetric("Débitos pendentes", money(summary.pendingCents), Modifier.fillMaxWidth())
            }

            item {
                Text("Novo lançamento", style = MaterialTheme.typography.titleLarge)
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
                Card(Modifier.fillMaxWidth()) {
                    Column(
                        Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text("Limite do MEI", style = MaterialTheme.typography.titleLarge)
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
                            "${mei.activeMonths} mês(es) considerados no cálculo.",
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
                            value = openingMonth,
                            onValueChange = { openingMonth = it.filter(Char::isDigit).take(2) },
                            label = { Text("Mês de abertura (1 a 12)") },
                            modifier = Modifier.fillMaxWidth()
                        )
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text("Primeiro ano do MEI")
                                Text(
                                    "Usa limite proporcional pelos meses ativos.",
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                            Switch(
                                checked = firstYear,
                                onCheckedChange = { firstYear = it }
                            )
                        }
                        Button(
                            onClick = {
                                viewModel.updateMeiConfig(
                                    annualLimit = annualLimit,
                                    openingMonth = openingMonth,
                                    firstYear = firstYear,
                                    onError = { error = it }
                                )
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Salvar configuração do MEI")
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
            onDismiss = { addingKind = null },
            onSave = { description, amount, ownership, method, status, date, due ->
                viewModel.addManualEntry(
                    description = description,
                    amount = amount,
                    kind = kind,
                    ownership = ownership,
                    paymentMethod = method,
                    paymentStatus = status,
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
    onDelete: (() -> Unit)?
) {
    val kind = when (transaction.kind) {
        EntryKind.EXPENSE -> "Despesa"
        EntryKind.REVENUE -> "Receita"
        EntryKind.CONTRIBUTION -> "Aporte"
        EntryKind.WITHDRAWAL -> "Retirada"
    }
    val source = if (transaction.receiptId == null) "Manual" else "Comprovante"

    Card(Modifier.fillMaxWidth()) {
        Column(
            Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(transaction.description, style = MaterialTheme.typography.titleMedium)
            Text("$kind • ${money(transaction.amountCents)} • $source")
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
    onDismiss: () -> Unit,
    onSave: (
        description: String,
        amount: String,
        ownership: Ownership,
        method: PaymentMethod,
        status: PaymentStatus,
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

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                when (kind) {
                    EntryKind.EXPENSE -> "Nova despesa"
                    EntryKind.REVENUE -> "Nova receita"
                    EntryKind.CONTRIBUTION -> "Novo aporte"
                    EntryKind.WITHDRAWAL -> "Nova retirada"
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

private fun formatDate(epoch: Long): String =
    DateTimeFormatter.ofPattern("dd/MM/yyyy")
        .withZone(ZoneId.systemDefault())
        .format(Instant.ofEpochMilli(epoch))
