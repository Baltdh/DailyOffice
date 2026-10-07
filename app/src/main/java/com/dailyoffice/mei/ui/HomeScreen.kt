package com.dailyoffice.mei.ui

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dailyoffice.mei.data.Ownership
import com.dailyoffice.mei.data.PaymentStatus
import com.dailyoffice.mei.data.Receipt
import com.dailyoffice.mei.receipt.ReceiptArchive
import com.dailyoffice.mei.viewmodel.ReceiptViewModel
import java.text.NumberFormat
import java.util.Locale

private enum class ReceiptFilter {
    ALL, BUSINESS, PERSONAL, MIXED, PENDING, REVIEW
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    viewModel: ReceiptViewModel,
    onCapture: () -> Unit,
    onReviewReady: () -> Unit,
    onFinance: () -> Unit
) {
    val context = LocalContext.current
    val receipts by viewModel.receipts.collectAsStateWithLifecycle()
    val summary by viewModel.summary.collectAsStateWithLifecycle()
    val mei by viewModel.meiProjection.collectAsStateWithLifecycle()

    var message by remember { mutableStateOf<String?>(null) }
    var query by rememberSaveable { mutableStateOf("") }
    var filter by remember { mutableStateOf(ReceiptFilter.ALL) }
    var pendingDelete by remember { mutableStateOf<Receipt?>(null) }

    val gallery = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) {
            viewModel.importReceipt(
                uri,
                onReady = onReviewReady,
                onError = { message = it }
            )
        }
    }

    val filteredReceipts = remember(receipts, query, filter) {
        receipts.filter { receipt ->
            val matchesText = query.isBlank() ||
                receipt.supplier.contains(query, ignoreCase = true) ||
                receipt.documentNumber.contains(query, ignoreCase = true) ||
                receipt.category.contains(query, ignoreCase = true) ||
                receipt.notes.contains(query, ignoreCase = true)

            val matchesFilter = when (filter) {
                ReceiptFilter.ALL -> true
                ReceiptFilter.BUSINESS -> receipt.ownership == Ownership.BUSINESS
                ReceiptFilter.PERSONAL -> receipt.ownership == Ownership.PERSONAL
                ReceiptFilter.MIXED -> receipt.ownership == Ownership.MIXED
                ReceiptFilter.PENDING ->
                    receipt.paymentStatus == PaymentStatus.PENDING ||
                        receipt.paymentStatus == PaymentStatus.OVERDUE
                ReceiptFilter.REVIEW -> receipt.ownership == Ownership.REVIEW
            }
            matchesText && matchesFilter
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("DailyOffice • Contador MEI") },
                actions = {
                    TextButton(onClick = onFinance) { Text("Financeiro") }
                }
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(onClick = onCapture) {
                Text("Fotografar")
            }
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
                Text("Visão geral", style = MaterialTheme.typography.headlineSmall)
            }

            item {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    MetricCard("Receitas", money(summary.revenueCents), Modifier.weight(1f))
                    MetricCard(
                        "Despesas empresa",
                        money(summary.businessExpensesCents),
                        Modifier.weight(1f)
                    )
                }
            }

            item {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    MetricCard(
                        "Pessoal",
                        money(summary.personalExpensesCents),
                        Modifier.weight(1f)
                    )
                    MetricCard(
                        "Pendentes",
                        money(summary.pendingCents),
                        Modifier.weight(1f)
                    )
                }
            }

            item {
                Card(Modifier.fillMaxWidth()) {
                    Column(
                        Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text("Limite MEI", style = MaterialTheme.typography.titleMedium)
                        Text("${money(mei.revenueCents)} de ${money(mei.limitCents)}")
                        LinearProgressIndicator(
                            progress = mei.usage.coerceIn(0f, 1f),
                            modifier = Modifier.fillMaxWidth()
                        )
                        Text(
                            if (mei.excessCents > 0) {
                                "Excesso: ${money(mei.excessCents)}"
                            } else {
                                "Restante: ${money(mei.remainingCents)}"
                            },
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }

            item {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = { gallery.launch("image/*") },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Galeria")
                    }
                    OutlinedButton(
                        onClick = {
                            viewModel.exportCsv(
                                onReady = { uri ->
                                    val share = Intent(Intent.ACTION_SEND).apply {
                                        type = "text/csv"
                                        putExtra(Intent.EXTRA_STREAM, uri)
                                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                    }
                                    context.startActivity(
                                        Intent.createChooser(share, "Exportar dados do DailyOffice")
                                    )
                                },
                                onError = { message = it }
                            )
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Exportar CSV")
                    }
                }
            }

            message?.let {
                item {
                    Card {
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(it, modifier = Modifier.weight(1f))
                            TextButton(onClick = { message = null }) {
                                Text("Fechar")
                            }
                        }
                    }
                }
            }

            item {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    label = { Text("Buscar comprovante") },
                    placeholder = { Text("Fornecedor, documento, categoria...") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            item {
                Row(
                    Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilterChoice("Todos", filter == ReceiptFilter.ALL) {
                        filter = ReceiptFilter.ALL
                    }
                    FilterChoice("Empresa", filter == ReceiptFilter.BUSINESS) {
                        filter = ReceiptFilter.BUSINESS
                    }
                    FilterChoice("Pessoal", filter == ReceiptFilter.PERSONAL) {
                        filter = ReceiptFilter.PERSONAL
                    }
                    FilterChoice("Mistos", filter == ReceiptFilter.MIXED) {
                        filter = ReceiptFilter.MIXED
                    }
                    FilterChoice("Pendentes", filter == ReceiptFilter.PENDING) {
                        filter = ReceiptFilter.PENDING
                    }
                    FilterChoice("Revisar", filter == ReceiptFilter.REVIEW) {
                        filter = ReceiptFilter.REVIEW
                    }
                }
            }

            item {
                Text(
                    "Comprovantes arquivados (${filteredReceipts.size})",
                    style = MaterialTheme.typography.titleLarge
                )
            }

            if (filteredReceipts.isEmpty()) {
                item {
                    Card {
                        Text(
                            if (receipts.isEmpty()) {
                                "Nenhum comprovante salvo ainda. Fotografe ou importe o primeiro."
                            } else {
                                "Nenhum comprovante corresponde aos filtros."
                            },
                            modifier = Modifier.padding(16.dp)
                        )
                    }
                }
            } else {
                items(filteredReceipts, key = { it.id }) { receipt ->
                    ReceiptCard(
                        receipt = receipt,
                        onView = {
                            runCatching {
                                val uri = ReceiptArchive.shareUri(context, receipt.imageUri)
                                val intent = Intent(Intent.ACTION_VIEW).apply {
                                    setDataAndType(uri, "image/*")
                                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                }
                                context.startActivity(intent)
                            }.onFailure {
                                message = it.message ?: "Não foi possível abrir a foto."
                            }
                        },
                        onEdit = {
                            viewModel.beginEdit(receipt)
                            onReviewReady()
                        },
                        onDelete = { pendingDelete = receipt }
                    )
                }
            }

            item { Spacer(Modifier.height(72.dp)) }
        }
    }

    pendingDelete?.let { receipt ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text("Excluir comprovante?") },
            text = {
                Text(
                    "A foto arquivada e os lançamentos financeiros ligados a este comprovante serão excluídos."
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.deleteReceipt(
                            receipt,
                            onDone = { pendingDelete = null },
                            onError = {
                                message = it
                                pendingDelete = null
                            }
                        )
                    }
                ) { Text("Excluir") }
            },
            dismissButton = {
                TextButton(onClick = { pendingDelete = null }) {
                    Text("Cancelar")
                }
            }
        )
    }
}

@Composable
private fun FilterChoice(
    label: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(label) }
    )
}

@Composable
private fun MetricCard(title: String, value: String, modifier: Modifier = Modifier) {
    Card(modifier) {
        Column(Modifier.padding(14.dp)) {
            Text(title, style = MaterialTheme.typography.labelLarge)
            Text(value, style = MaterialTheme.typography.titleMedium)
        }
    }
}

@Composable
private fun ReceiptCard(
    receipt: Receipt,
    onView: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    val type = when (receipt.ownership) {
        Ownership.BUSINESS -> "Empresa"
        Ownership.PERSONAL -> "Pessoal"
        Ownership.MIXED -> "Misto"
        Ownership.REVIEW -> "Revisar"
    }
    val status = when (receipt.paymentStatus) {
        PaymentStatus.PAID -> "Pago"
        PaymentStatus.PENDING -> "Pendente"
        PaymentStatus.OVERDUE -> "Vencido"
        PaymentStatus.CANCELLED -> "Cancelado"
    }

    Card(Modifier.fillMaxWidth()) {
        Column(
            Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            Text(
                receipt.supplier.ifBlank { "Fornecedor não informado" },
                style = MaterialTheme.typography.titleMedium
            )
            Text("${money(receipt.totalCents)} • $type • $status")
            if (receipt.ownership == Ownership.MIXED) {
                Text(
                    "Empresa ${money(receipt.businessCents)} • Pessoal ${money(receipt.personalCents)}",
                    style = MaterialTheme.typography.bodySmall
                )
            }
            if (receipt.documentNumber.isNotBlank()) {
                Text(
                    "Documento ${receipt.documentNumber}",
                    style = MaterialTheme.typography.bodySmall
                )
            }
            if (receipt.category.isNotBlank()) {
                Text(receipt.category, style = MaterialTheme.typography.bodySmall)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = onView) { Text("Ver foto") }
                TextButton(onClick = onEdit) { Text("Editar") }
                TextButton(onClick = onDelete) { Text("Excluir") }
            }
        }
    }
}

private fun money(cents: Long): String =
    NumberFormat.getCurrencyInstance(Locale("pt", "BR")).format(cents / 100.0)
