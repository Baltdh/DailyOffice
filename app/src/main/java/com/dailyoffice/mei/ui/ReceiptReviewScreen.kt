package com.dailyoffice.mei.ui

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dailyoffice.mei.data.AccountKind
import com.dailyoffice.mei.data.Ownership
import com.dailyoffice.mei.data.PaymentMethod
import com.dailyoffice.mei.data.PaymentStatus
import com.dailyoffice.mei.data.StockUnit
import com.dailyoffice.mei.viewmodel.ReceiptViewModel
import java.text.NumberFormat
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReceiptReviewScreen(
    viewModel: ReceiptViewModel,
    onBack: () -> Unit,
    onSaved: () -> Unit
) {
    val d = viewModel.draft
    val activeCompany by viewModel.activeCompany.collectAsStateWithLifecycle()
    val inventoryProducts by viewModel.inventoryProducts.collectAsStateWithLifecycle()
    val accounts by viewModel.accounts.collectAsStateWithLifecycle()
    var error by remember { mutableStateOf<String?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        if (viewModel.isEditing) {
                            "Editar comprovante"
                        } else {
                            "Conferir comprovante"
                        }
                    )
                },
                navigationIcon = {
                    TextButton(onClick = onBack) { Text("Voltar") }
                }
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
            AssistChip(
                onClick = {},
                label = {
                    Text("Empresa: ${activeCompany?.name ?: "Empresa ativa"}")
                }
            )
            if (d.classificationReason.isNotBlank()) {
                Card {
                    Column(
                        Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            "Sugestão automática",
                            style = MaterialTheme.typography.labelLarge
                        )
                        Text(d.classificationReason)
                        Text(
                            "Confiança do OCR: ${(d.confidence * 100).toInt()}%",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }

            d.warnings.forEach { warning ->
                Text(
                    warning,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall
                )
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

            if (d.items.isNotEmpty()) {
                Card(Modifier.fillMaxWidth()) {
                    Column(
                        Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(
                            "Itens detectados pelo OCR",
                            style = MaterialTheme.typography.titleMedium
                        )
                        Text(
                            "Revise cada item. Quando a soma fechar com o total, o app pode separar Empresa e Pessoal automaticamente.",
                            style = MaterialTheme.typography.bodySmall
                        )

                        d.items.forEachIndexed { index, item ->
                            Column(
                                Modifier.fillMaxWidth(),
                                verticalArrangement = Arrangement.spacedBy(5.dp)
                            ) {
                                Row(
                                    Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Text(
                                        item.description,
                                        modifier = Modifier.weight(1f)
                                    )
                                    Text(
                                        money(item.amountCents),
                                        style = MaterialTheme.typography.labelLarge
                                    )
                                }

                                Text(
                                    "Confiança da leitura: ${(item.confidence * 100).toInt()}%",
                                    style = MaterialTheme.typography.bodySmall
                                )

                                ChoiceRow(
                                    values = listOf(
                                        Ownership.BUSINESS,
                                        Ownership.PERSONAL,
                                        Ownership.REVIEW
                                    ),
                                    selected = item.ownership,
                                    label = {
                                        when (it) {
                                            Ownership.BUSINESS -> "Empresa"
                                            Ownership.PERSONAL -> "Pessoal"
                                            Ownership.REVIEW -> "Revisar"
                                            Ownership.MIXED -> "Misto"
                                        }
                                    },
                                    onSelect = {
                                        viewModel.updateItemOwnership(index, it)
                                    }
                                )

                                if (item.ownership == Ownership.BUSINESS) {
                                    Row(
                                        Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Column(Modifier.weight(1f)) {
                                            Text("Adicionar ao estoque")
                                            Text(
                                                "Cria uma entrada vinculada a este comprovante.",
                                                style = MaterialTheme.typography.bodySmall
                                            )
                                        }
                                        Switch(
                                            checked = item.addToStock,
                                            onCheckedChange = {
                                                viewModel.updateItemAddToStock(index, it)
                                            }
                                        )
                                    }

                                    if (item.addToStock) {
                                        if (inventoryProducts.isEmpty()) {
                                            Text(
                                                "Cadastre um produto na tela Estoque antes de vincular este item.",
                                                color = MaterialTheme.colorScheme.error,
                                                style = MaterialTheme.typography.bodySmall
                                            )
                                        } else {
                                            Text(
                                                "Produto no estoque",
                                                style = MaterialTheme.typography.labelLarge
                                            )
                                            Row(
                                                Modifier.horizontalScroll(rememberScrollState()),
                                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                                            ) {
                                                inventoryProducts.forEach { product ->
                                                    FilterChip(
                                                        selected = item.stockProductId == product.id,
                                                        onClick = {
                                                            viewModel.updateItemStockProduct(
                                                                index,
                                                                product.id
                                                            )
                                                        },
                                                        label = {
                                                            Text(
                                                                product.name + " • " +
                                                                    stockUnitLabel(product.unit)
                                                            )
                                                        }
                                                    )
                                                }
                                            }

                                            val selectedProduct = inventoryProducts.firstOrNull {
                                                it.id == item.stockProductId
                                            }
                                            OutlinedTextField(
                                                value = item.stockQuantity,
                                                onValueChange = {
                                                    viewModel.updateItemStockQuantity(index, it)
                                                },
                                                label = {
                                                    Text(
                                                        "Quantidade" +
                                                            selectedProduct?.let {
                                                                " (${stockUnitLabel(it.unit)})"
                                                            }.orEmpty()
                                                    )
                                                },
                                                supportingText = {
                                                    val hint = item.unitHint
                                                    if (!hint.isNullOrBlank()) {
                                                        Text(
                                                            "OCR sugeriu unidade/quantidade em “$hint”. Confira antes de salvar."
                                                        )
                                                    } else {
                                                        Text("Informe a quantidade comprada.")
                                                    }
                                                },
                                                modifier = Modifier.fillMaxWidth()
                                            )
                                        }
                                    }
                                }

                                if (index != d.items.lastIndex) {
                                    HorizontalDivider()
                                }
                            }
                        }

                        Text(
                            "Soma dos itens: ${money(d.items.sumOf { it.amountCents })}",
                            style = MaterialTheme.typography.labelLarge
                        )

                        Button(
                            onClick = {
                                error = viewModel.applyItemSplit()
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Aplicar divisão pelos itens")
                        }
                    }
                }
            }

            Text(
                "Classificação do comprovante",
                style = MaterialTheme.typography.titleMedium
            )

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

            Text(
                "Pagamento",
                style = MaterialTheme.typography.titleMedium
            )

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

            Text(
                "Conta / origem do pagamento",
                style = MaterialTheme.typography.titleMedium
            )
            Text(
                "A classificação da compra e a origem do pagamento são controles separados.",
                style = MaterialTheme.typography.bodySmall
            )

            if (accounts.isEmpty()) {
                Text(
                    "Nenhuma conta cadastrada para esta empresa.",
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall
                )
            } else {
                Row(
                    Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    accounts.forEach { account ->
                        FilterChip(
                            selected = d.accountId == account.id,
                            onClick = { viewModel.updateAccount(account.id) },
                            label = { Text(account.name) }
                        )
                    }
                }
            }

            val selectedAccount = accounts.firstOrNull { it.id == d.accountId }
            if (
                d.ownership == Ownership.BUSINESS &&
                selectedAccount != null &&
                (
                    selectedAccount.kind == AccountKind.OWNER_PERSONAL_BANK ||
                        selectedAccount.kind == AccountKind.OWNER_PERSONAL_CARD
                )
            ) {
                Text(
                    "Despesa da empresa paga pelo titular.",
                    style = MaterialTheme.typography.bodySmall
                )
            }

            Text(
                "Situação",
                style = MaterialTheme.typography.titleMedium
            )

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
                placeholder = {
                    Text("Ex.: insumos, embalagem, higiene")
                },
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
                Text(
                    it,
                    color = MaterialTheme.colorScheme.error
                )
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

private fun money(cents: Long): String =
    NumberFormat
        .getCurrencyInstance(Locale("pt", "BR"))
        .format(cents / 100.0)


private fun stockUnitLabel(unit: StockUnit): String =
    when (unit) {
        StockUnit.UNIT -> "un"
        StockUnit.GRAM -> "g"
        StockUnit.KILOGRAM -> "kg"
        StockUnit.MILLILITER -> "ml"
        StockUnit.LITER -> "L"
        StockUnit.PACK -> "pacote"
    }
