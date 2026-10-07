package com.dailyoffice.mei.ui

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.dailyoffice.mei.data.Ownership
import com.dailyoffice.mei.data.PaymentMethod
import com.dailyoffice.mei.data.PaymentStatus
import com.dailyoffice.mei.viewmodel.ReceiptViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReceiptReviewScreen(
    viewModel: ReceiptViewModel,
    onBack: () -> Unit,
    onSaved: () -> Unit
) {
    val d = viewModel.draft
    var error by remember { mutableStateOf<String?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (viewModel.isEditing) "Editar comprovante" else "Conferir comprovante") },
                navigationIcon = { TextButton(onClick = onBack) { Text("Voltar") } }
            )
        }
    ) { pad ->
        Column(
            Modifier
                .padding(pad)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (d.classificationReason.isNotBlank()) {
                Card {
                    Column(Modifier.padding(12.dp)) {
                        Text("Sugestão automática", style = MaterialTheme.typography.labelLarge)
                        Text(d.classificationReason)
                        Text("Confiança do OCR: ${(d.confidence * 100).toInt()}%")
                    }
                }
            }

            d.warnings.forEach { warning ->
                Text(warning, color = MaterialTheme.colorScheme.error)
            }

            OutlinedTextField(
                value = d.supplier,
                onValueChange = viewModel::updateSupplier,
                label = { Text("Fornecedor") },
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = d.total,
                onValueChange = viewModel::updateTotal,
                label = { Text("Valor total (R$)") },
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = d.date,
                onValueChange = viewModel::updateDate,
                label = { Text("Data (dd/mm/aaaa)") },
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = d.documentNumber,
                onValueChange = viewModel::updateDocument,
                label = { Text("Número do documento") },
                modifier = Modifier.fillMaxWidth()
            )

            Text("Classificação", style = MaterialTheme.typography.titleMedium)
            ChoiceRow(
                values = Ownership.entries,
                selected = d.ownership,
                label = {
                    when (it) {
                        Ownership.BUSINESS -> "Empresa"
                        Ownership.PERSONAL -> "Pessoal"
                        Ownership.MIXED -> "Misto"
                        Ownership.REVIEW -> "Revisar"
                    }
                },
                onSelect = viewModel::updateOwnership
            )

            if (d.ownership == Ownership.MIXED) {
                OutlinedTextField(
                    value = d.businessAmount,
                    onValueChange = viewModel::updateBusinessAmount,
                    label = { Text("Parte da empresa (R$)") },
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = d.personalAmount,
                    onValueChange = viewModel::updatePersonalAmount,
                    label = { Text("Parte pessoal (R$)") },
                    modifier = Modifier.fillMaxWidth()
                )
            }

            Text("Pagamento", style = MaterialTheme.typography.titleMedium)
            ChoiceRow(
                values = PaymentMethod.entries,
                selected = d.paymentMethod,
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
                onSelect = viewModel::updatePaymentMethod
            )

            Text("Situação", style = MaterialTheme.typography.titleMedium)
            ChoiceRow(
                values = PaymentStatus.entries,
                selected = d.paymentStatus,
                label = {
                    when (it) {
                        PaymentStatus.PAID -> "Pago"
                        PaymentStatus.PENDING -> "Pendente"
                        PaymentStatus.OVERDUE -> "Vencido"
                        PaymentStatus.CANCELLED -> "Cancelado"
                    }
                },
                onSelect = viewModel::updatePaymentStatus
            )

            if (
                d.paymentStatus == PaymentStatus.PENDING ||
                d.paymentStatus == PaymentStatus.OVERDUE
            ) {
                OutlinedTextField(
                    value = d.dueDate,
                    onValueChange = viewModel::updateDueDate,
                    label = { Text("Vencimento (dd/mm/aaaa)") },
                    modifier = Modifier.fillMaxWidth()
                )
            }

            OutlinedTextField(
                value = d.category,
                onValueChange = viewModel::updateCategory,
                label = { Text("Categoria") },
                placeholder = { Text("Ex.: insumos, embalagem, higiene") },
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = d.notes,
                onValueChange = viewModel::updateNotes,
                label = { Text("Observações") },
                minLines = 2,
                modifier = Modifier.fillMaxWidth()
            )

            error?.let {
                Text(it, color = MaterialTheme.colorScheme.error)
            }

            Button(
                onClick = {
                    viewModel.save(
                        onSaved = onSaved,
                        onError = { error = it }
                    )
                },
                enabled = !viewModel.saving,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    when {
                        viewModel.saving -> "Salvando..."
                        viewModel.isEditing -> "Salvar alterações"
                        else -> "Arquivar comprovante"
                    }
                )
            }

            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun <T> ChoiceRow(
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
