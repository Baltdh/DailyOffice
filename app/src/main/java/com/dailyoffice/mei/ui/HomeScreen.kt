package com.dailyoffice.mei.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dailyoffice.mei.data.Ownership
import com.dailyoffice.mei.data.PaymentStatus
import com.dailyoffice.mei.data.Receipt
import com.dailyoffice.mei.viewmodel.ReceiptViewModel
import java.text.NumberFormat
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    viewModel: ReceiptViewModel,
    onCapture: () -> Unit,
    onReviewReady: () -> Unit
) {
    val receipts by viewModel.receipts.collectAsStateWithLifecycle()
    val summary by viewModel.summary.collectAsStateWithLifecycle()
    var message by remember { mutableStateOf<String?>(null) }

    val gallery = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) {
            viewModel.importReceipt(
                uri,
                onReady = onReviewReady,
                onError = { message = it }
            )
        }
    }

    Scaffold(
        topBar = { TopAppBar(title = { Text("DailyOffice • Contador MEI") }) },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = onCapture,
                text = { Text("Fotografar") }
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
                Text("Visão geral", style = MaterialTheme.typography.headlineSmall)
            }
            item {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    MetricCard("Empresa", money(summary.businessCents), Modifier.weight(1f))
                    MetricCard("Pessoal", money(summary.personalCents), Modifier.weight(1f))
                }
            }
            item {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    MetricCard("Pendentes", money(summary.pendingCents), Modifier.weight(1f))
                    MetricCard("A revisar", summary.reviewCount.toString(), Modifier.weight(1f))
                }
            }
            item {
                OutlinedButton(
                    onClick = { gallery.launch("image/*") },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Importar foto da galeria")
                }
            }
            message?.let {
                item {
                    Text(it, color = MaterialTheme.colorScheme.error)
                }
            }
            item {
                Text("Comprovantes arquivados", style = MaterialTheme.typography.titleLarge)
            }
            if (receipts.isEmpty()) {
                item {
                    Card {
                        Text(
                            "Nenhum comprovante salvo ainda. Fotografe ou importe o primeiro.",
                            modifier = Modifier.padding(16.dp)
                        )
                    }
                }
            } else {
                items(receipts, key = { it.id }) { receipt ->
                    ReceiptCard(receipt)
                }
            }
            item { Spacer(Modifier.height(72.dp)) }
        }
    }
}

@Composable
private fun MetricCard(title: String, value: String, modifier: Modifier = Modifier) {
    Card(modifier) {
        Column(Modifier.padding(14.dp)) {
            Text(title, style = MaterialTheme.typography.labelLarge)
            Text(value, style = MaterialTheme.typography.titleLarge)
        }
    }
}

@Composable
private fun ReceiptCard(receipt: Receipt) {
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
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                receipt.supplier.ifBlank { "Fornecedor não informado" },
                style = MaterialTheme.typography.titleMedium
            )
            Text("${money(receipt.totalCents)} • $type • $status")
            if (receipt.documentNumber.isNotBlank()) {
                Text(
                    "Documento ${receipt.documentNumber}",
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
    }
}

private fun money(cents: Long): String =
    NumberFormat.getCurrencyInstance(Locale("pt", "BR")).format(cents / 100.0)
