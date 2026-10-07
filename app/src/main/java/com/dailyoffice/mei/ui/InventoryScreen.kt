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
import com.dailyoffice.mei.data.Company
import com.dailyoffice.mei.data.InventoryProduct
import com.dailyoffice.mei.data.StockMovement
import com.dailyoffice.mei.data.StockMovementType
import com.dailyoffice.mei.data.StockUnit
import com.dailyoffice.mei.inventory.InventoryBalance
import com.dailyoffice.mei.viewmodel.ReceiptViewModel
import java.math.BigDecimal

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InventoryScreen(
    viewModel: ReceiptViewModel,
    onBack: () -> Unit
) {
    val activeCompany by viewModel.activeCompany.collectAsStateWithLifecycle()
    val companies by viewModel.companies.collectAsStateWithLifecycle()
    val balances by viewModel.inventoryBalances.collectAsStateWithLifecycle()
    val movements by viewModel.stockMovements.collectAsStateWithLifecycle()

    var message by remember { mutableStateOf<String?>(null) }
    var showNewProduct by remember { mutableStateOf(false) }
    var movementTarget by remember {
        mutableStateOf<Pair<InventoryProduct, StockMovementType>?>(null)
    }
    var transferTarget by remember { mutableStateOf<InventoryProduct?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Estoque compartilhado") },
                navigationIcon = {
                    TextButton(onClick = onBack) { Text("Voltar") }
                }
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { showNewProduct = true }
            ) {
                Text("Novo produto")
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
                Card(Modifier.fillMaxWidth()) {
                    Column(
                        Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            "Empresa ativa",
                            style = MaterialTheme.typography.labelLarge
                        )
                        Text(
                            activeCompany?.name ?: "Carregando...",
                            style = MaterialTheme.typography.titleLarge
                        )
                        Text(
                            "O estoque físico pode ficar junto. O DailyOffice mantém o saldo pertencente a cada empresa separado.",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }

            message?.let {
                item {
                    Card(Modifier.fillMaxWidth()) {
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
                Text(
                    "Produtos (${balances.size})",
                    style = MaterialTheme.typography.headlineSmall
                )
            }

            if (balances.isEmpty()) {
                item {
                    Card(Modifier.fillMaxWidth()) {
                        Text(
                            "Cadastre o primeiro produto para começar o controle de estoque.",
                            Modifier.padding(16.dp)
                        )
                    }
                }
            } else {
                items(balances, key = { it.product.id }) { balance ->
                    InventoryCard(
                        balance = balance,
                        canTransfer = companies.any {
                            it.id != activeCompany?.id
                        },
                        onEntry = {
                            movementTarget =
                                balance.product to StockMovementType.PURCHASE
                        },
                        onConsumption = {
                            movementTarget =
                                balance.product to StockMovementType.CONSUMPTION
                        },
                        onLoss = {
                            movementTarget =
                                balance.product to StockMovementType.LOSS
                        },
                        onAdjustIn = {
                            movementTarget =
                                balance.product to StockMovementType.ADJUSTMENT_IN
                        },
                        onAdjustOut = {
                            movementTarget =
                                balance.product to StockMovementType.ADJUSTMENT_OUT
                        },
                        onTransfer = {
                            transferTarget = balance.product
                        }
                    )
                }
            }

            val activeMovements = movements
                .filter { it.companyId == activeCompany?.id }
                .take(25)

            item {
                Text(
                    "Movimentações recentes",
                    style = MaterialTheme.typography.titleLarge
                )
            }

            if (activeMovements.isEmpty()) {
                item {
                    Text(
                        "Nenhuma movimentação registrada para esta empresa.",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            } else {
                items(activeMovements, key = { "movement-${it.id}" }) { movement ->
                    StockMovementCard(
                        movement = movement,
                        product = balances
                            .firstOrNull { it.product.id == movement.productId }
                            ?.product,
                        companies = companies
                    )
                }
            }

            item { Spacer(Modifier.height(72.dp)) }
        }
    }

    if (showNewProduct) {
        NewProductDialog(
            onDismiss = { showNewProduct = false },
            onSave = { name, unit ->
                viewModel.addInventoryProduct(
                    name = name,
                    unit = unit,
                    onSaved = {
                        showNewProduct = false
                        message = "Produto cadastrado."
                    },
                    onError = { message = it }
                )
            }
        )
    }

    movementTarget?.let { (product, type) ->
        StockMovementDialog(
            product = product,
            type = type,
            onDismiss = { movementTarget = null },
            onSave = { quantity, totalCost, note ->
                viewModel.addStockMovement(
                    productId = product.id,
                    type = type,
                    quantity = quantity,
                    totalCost = totalCost,
                    note = note,
                    onSaved = {
                        movementTarget = null
                        message = "Movimentação registrada."
                    },
                    onError = { message = it }
                )
            }
        )
    }

    transferTarget?.let { product ->
        TransferStockDialog(
            product = product,
            companies = companies.filter {
                it.id != activeCompany?.id
            },
            onDismiss = { transferTarget = null },
            onTransfer = { companyId, quantity, note ->
                viewModel.transferStock(
                    productId = product.id,
                    targetCompanyId = companyId,
                    quantity = quantity,
                    note = note,
                    onSaved = {
                        transferTarget = null
                        message = "Transferência registrada entre as empresas."
                    },
                    onError = { message = it }
                )
            }
        )
    }
}

@Composable
private fun InventoryCard(
    balance: InventoryBalance,
    canTransfer: Boolean,
    onEntry: () -> Unit,
    onConsumption: () -> Unit,
    onLoss: () -> Unit,
    onAdjustIn: () -> Unit,
    onAdjustOut: () -> Unit,
    onTransfer: () -> Unit
) {
    val product = balance.product
    val unit = unitLabel(product.unit)

    Card(Modifier.fillMaxWidth()) {
        Column(
            Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(
                product.name,
                style = MaterialTheme.typography.titleLarge
            )
            Text(
                "Da empresa: ${formatQuantity(balance.companyQuantityMilli)} $unit",
                style = MaterialTheme.typography.titleMedium
            )
            Text(
                "Físico total: ${formatQuantity(balance.physicalQuantityMilli)} $unit",
                style = MaterialTheme.typography.bodyMedium
            )
            if (balance.companyQuantityMilli < 0) {
                Text(
                    "Saldo negativo: revise as movimentações.",
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall
                )
            }

            Row(
                Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(onClick = onEntry) { Text("Entrada") }
                OutlinedButton(onClick = onConsumption) {
                    Text("Consumo")
                }
                OutlinedButton(onClick = onLoss) {
                    Text("Perda")
                }
                OutlinedButton(onClick = onAdjustIn) {
                    Text("Ajuste +")
                }
                OutlinedButton(onClick = onAdjustOut) {
                    Text("Ajuste -")
                }
                OutlinedButton(
                    onClick = onTransfer,
                    enabled = canTransfer
                ) {
                    Text("Transferir")
                }
            }
        }
    }
}

@Composable
private fun NewProductDialog(
    onDismiss: () -> Unit,
    onSave: (String, StockUnit) -> Unit
) {
    var name by rememberSaveable { mutableStateOf("") }
    var unit by remember { mutableStateOf(StockUnit.UNIT) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Novo produto") },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Produto") },
                    placeholder = { Text("Ex.: Salmão, açaí, embalagem") },
                    modifier = Modifier.fillMaxWidth()
                )
                Text("Unidade")
                Row(
                    Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    StockUnit.entries.forEach { candidate ->
                        FilterChip(
                            selected = unit == candidate,
                            onClick = { unit = candidate },
                            label = { Text(unitLabel(candidate)) }
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = { onSave(name, unit) }) {
                Text("Cadastrar")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancelar") }
        }
    )
}

@Composable
private fun StockMovementDialog(
    product: InventoryProduct,
    type: StockMovementType,
    onDismiss: () -> Unit,
    onSave: (quantity: String, totalCost: String, note: String) -> Unit
) {
    var quantity by rememberSaveable { mutableStateOf("") }
    var totalCost by rememberSaveable { mutableStateOf("") }
    var note by rememberSaveable { mutableStateOf("") }

    val title = when (type) {
        StockMovementType.PURCHASE -> "Entrada / compra"
        StockMovementType.CONSUMPTION -> "Registrar consumo"
        StockMovementType.LOSS -> "Registrar perda"
        StockMovementType.ADJUSTMENT_IN -> "Ajuste positivo"
        StockMovementType.ADJUSTMENT_OUT -> "Ajuste negativo"
        StockMovementType.TRANSFER_IN,
        StockMovementType.TRANSFER_OUT -> "Transferência"
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("$title • ${product.name}") },
        text = {
            Column(
                Modifier
                    .fillMaxWidth()
                    .heightIn(max = 420.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedTextField(
                    value = quantity,
                    onValueChange = { quantity = it },
                    label = {
                        Text("Quantidade (${unitLabel(product.unit)})")
                    },
                    supportingText = {
                        Text("Aceita até 3 casas decimais.")
                    },
                    modifier = Modifier.fillMaxWidth()
                )

                if (type == StockMovementType.PURCHASE) {
                    OutlinedTextField(
                        value = totalCost,
                        onValueChange = { totalCost = it },
                        label = { Text("Custo total (R$) — opcional") },
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                OutlinedTextField(
                    value = note,
                    onValueChange = { note = it },
                    label = { Text("Observação — opcional") },
                    minLines = 2,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onSave(quantity, totalCost, note)
                }
            ) {
                Text("Salvar")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancelar") }
        }
    )
}

@Composable
private fun TransferStockDialog(
    product: InventoryProduct,
    companies: List<Company>,
    onDismiss: () -> Unit,
    onTransfer: (companyId: Long, quantity: String, note: String) -> Unit
) {
    var selectedCompanyId by remember(companies) {
        mutableStateOf(companies.firstOrNull()?.id)
    }
    var quantity by rememberSaveable { mutableStateOf("") }
    var note by rememberSaveable { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Transferir • ${product.name}") },
        text = {
            Column(
                Modifier
                    .fillMaxWidth()
                    .heightIn(max = 460.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    "A transferência reduz o saldo da empresa atual e aumenta o saldo da empresa de destino sem alterar o estoque físico total.",
                    style = MaterialTheme.typography.bodySmall
                )
                Text(
                    "Este registro é controle interno e não substitui eventual documento fiscal exigido para a operação entre CNPJs.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )

                Text("Empresa de destino")
                companies.forEach { company ->
                    FilterChip(
                        selected = selectedCompanyId == company.id,
                        onClick = {
                            selectedCompanyId = company.id
                        },
                        label = { Text(company.name) },
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                OutlinedTextField(
                    value = quantity,
                    onValueChange = { quantity = it },
                    label = {
                        Text("Quantidade (${unitLabel(product.unit)})")
                    },
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = note,
                    onValueChange = { note = it },
                    label = { Text("Motivo / documento — opcional") },
                    minLines = 2,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    selectedCompanyId?.let {
                        onTransfer(it, quantity, note)
                    }
                },
                enabled = selectedCompanyId != null
            ) {
                Text("Transferir")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancelar") }
        }
    )
}

@Composable
private fun StockMovementCard(
    movement: StockMovement,
    product: InventoryProduct?,
    companies: List<Company>
) {
    val signed = when (movement.type) {
        StockMovementType.PURCHASE,
        StockMovementType.ADJUSTMENT_IN,
        StockMovementType.TRANSFER_IN -> movement.quantityMilli
        else -> -movement.quantityMilli
    }

    val label = when (movement.type) {
        StockMovementType.PURCHASE -> "Entrada"
        StockMovementType.CONSUMPTION -> "Consumo"
        StockMovementType.LOSS -> "Perda"
        StockMovementType.ADJUSTMENT_IN -> "Ajuste +"
        StockMovementType.ADJUSTMENT_OUT -> "Ajuste -"
        StockMovementType.TRANSFER_IN -> "Transferência recebida"
        StockMovementType.TRANSFER_OUT -> "Transferência enviada"
    }

    val counterparty = movement.counterpartyCompanyId?.let { id ->
        companies.firstOrNull { it.id == id }?.name
    }

    Card(Modifier.fillMaxWidth()) {
        Column(
            Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            Text(
                product?.name ?: "Produto #${movement.productId}",
                style = MaterialTheme.typography.titleMedium
            )
            Text(
                "$label • ${if (signed >= 0) "+" else ""}${formatQuantity(signed)} " +
                    unitLabel(product?.unit ?: StockUnit.UNIT)
            )
            counterparty?.let {
                Text(
                    "Outra empresa: $it",
                    style = MaterialTheme.typography.bodySmall
                )
            }
            if (movement.note.isNotBlank()) {
                Text(
                    movement.note,
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
    }
}

private fun unitLabel(unit: StockUnit): String =
    when (unit) {
        StockUnit.UNIT -> "un"
        StockUnit.GRAM -> "g"
        StockUnit.KILOGRAM -> "kg"
        StockUnit.MILLILITER -> "ml"
        StockUnit.LITER -> "L"
        StockUnit.PACK -> "pacote"
    }

private fun formatQuantity(quantityMilli: Long): String =
    BigDecimal(quantityMilli)
        .divide(BigDecimal(1000))
        .stripTrailingZeros()
        .toPlainString()
        .replace('.', ',')
