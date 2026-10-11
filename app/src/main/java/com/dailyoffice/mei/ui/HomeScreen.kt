package com.dailyoffice.mei.ui

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dailyoffice.mei.data.Company
import com.dailyoffice.mei.data.CompanyProfileRules
import com.dailyoffice.mei.data.CompanyType
import com.dailyoffice.mei.data.TaxRegime
import com.dailyoffice.mei.data.Ownership
import com.dailyoffice.mei.data.PaymentStatus
import com.dailyoffice.mei.data.Receipt
import com.dailyoffice.mei.finance.CompanyCeiling
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
    onFinance: () -> Unit,
    onInventory: () -> Unit,
    onDocuments: () -> Unit
) {
    val context = LocalContext.current
    val receipts by viewModel.receipts.collectAsStateWithLifecycle()
    val summary by viewModel.summary.collectAsStateWithLifecycle()
    val mei by viewModel.meiProjection.collectAsStateWithLifecycle()
    val meiConfig by viewModel.meiConfig.collectAsStateWithLifecycle()
    val companies by viewModel.companies.collectAsStateWithLifecycle()
    val activeCompany by viewModel.activeCompany.collectAsStateWithLifecycle()

    var message by remember { mutableStateOf<String?>(null) }
    var query by rememberSaveable { mutableStateOf("") }
    var filter by remember { mutableStateOf(ReceiptFilter.ALL) }
    var pendingDelete by remember { mutableStateOf<Receipt?>(null) }
    var showCompanies by remember { mutableStateOf(false) }
    var showCompanyForm by remember { mutableStateOf(false) }
    var companyFormTarget by remember { mutableStateOf<Company?>(null) }

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
                title = { Text("DailyOffice", maxLines = 1, style = MaterialTheme.typography.titleMedium) },
                actions = {
                    TextButton(onClick = onFinance) { Text("Financeiro") }
                    var menuExpanded by remember { mutableStateOf(false) }
                    Box {
                        TextButton(onClick = { menuExpanded = true }) { Text("Mais") }
                        DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
                            DropdownMenuItem(text = { Text("Arquivos") }, onClick = { menuExpanded = false; onDocuments() })
                            DropdownMenuItem(text = { Text("Estoque") }, onClick = { menuExpanded = false; onInventory() })
                        }
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = onCapture,
                modifier = Modifier.navigationBarsPadding().imePadding(),
                shape = androidx.compose.foundation.shape.CircleShape
            ) {
                Icon(
                    imageVector = androidx.compose.material.icons.Icons.Default.CameraAlt,
                    contentDescription = "Fotografar comprovante"
                )
            }
        },
        floatingActionButtonPosition = FabPosition.Center
    ) { pad ->
        LazyColumn(
            modifier = Modifier
                .padding(pad)
                .fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Text("Acesso rápido", style = MaterialTheme.typography.titleMedium)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { gallery.launch("image/*") }, modifier = Modifier.fillMaxWidth()) { Text("Importar da galeria") }
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = onFinance, modifier = Modifier.weight(1f)) { Text("Financeiro") }
                    OutlinedButton(onClick = onDocuments, modifier = Modifier.weight(1f)) { Text("Arquivos") }
                    OutlinedButton(onClick = onInventory, modifier = Modifier.weight(1f)) { Text("Estoque") }
                }
            }

            item {
                Text("Visão geral", style = MaterialTheme.typography.headlineSmall)
            }

            item {
                CompanyCard(
                    company = activeCompany,
                    onSwitch = { showCompanies = true },
                    onEdit = {
                        companyFormTarget = activeCompany
                        showCompanyForm = true
                    },
                    onNew = {
                        companyFormTarget = null
                        showCompanyForm = true
                    }
                )
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
                        "Pendentes",
                        money(summary.pendingCents),
                        Modifier.weight(1f)
                    )
                    MetricCard(
                        "A receber",
                        money(summary.receivableCents),
                        Modifier.weight(1f)
                    )
                }
            }

            item {
                MetricCard(
                    "Gastos pessoais identificados",
                    money(summary.personalExpensesCents),
                    Modifier.fillMaxWidth()
                )
            }

            item {
                MetricCard(
                    "A reembolsar ao titular",
                    money(summary.ownerReimbursementOutstandingCents),
                    Modifier.fillMaxWidth()
                )
            }

            item {
                if (activeCompany?.companyType == CompanyType.MEI) {
                    Card(Modifier.fillMaxWidth()) {
                        Column(
                            Modifier.padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                "Limite MEI • ${meiConfig.taxYear}",
                                style = MaterialTheme.typography.titleMedium
                            )
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
                            Text(
                                if (mei.isFirstYear) {
                                    "Primeiro ano: limite proporcional. A partir de ${mei.openingYear + 1}: ${money(mei.annualLimitCents)} por ano."
                                } else {
                                    "Limite anual completo: ${money(mei.annualLimitCents)}."
                                },
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                } else {
                    Card(Modifier.fillMaxWidth()) {
                        Column(
                            Modifier.padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text(
                                "Enquadramento: ${companyTypeLabel(activeCompany?.companyType ?: CompanyType.OTHER)}",
                                style = MaterialTheme.typography.titleMedium
                            )
                            val ceiling = CompanyCeiling.annualLimit(activeCompany?.companyType ?: CompanyType.OTHER)
                            if (ceiling > 0L) {
                                val used = mei.revenueCents
                                Text("Teto anual de porte: ${money(ceiling)}")
                                Text("Receita registrada (${meiConfig.taxYear}): ${money(used)}")
                                LinearProgressIndicator(
                                    progress = (used.toDouble() / ceiling.toDouble()).toFloat().coerceIn(0f, 1f),
                                    modifier = Modifier.fillMaxWidth()
                                )
                                Text(
                                    if (used > ceiling) "Acima do teto: ${money(used - ceiling)}"
                                    else "Margem até o teto: ${money(ceiling - used)}",
                                    style = MaterialTheme.typography.bodySmall
                                )
                            } else {
                                Text("Porte sem teto automático cadastrado.", style = MaterialTheme.typography.bodySmall)
                            }
                        }
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
                    "Comprovantes (${filteredReceipts.size} de ${receipts.size})",
                    style = MaterialTheme.typography.titleLarge
                )
            }

            if (filteredReceipts.isEmpty()) {
                item {
                    Card {
                        Text(
                            if (receipts.isEmpty()) {
                                "Nenhum comprovante arquivado. Use Fotografar ou Galeria para adicionar o primeiro."
                            } else {
                                "Nenhum resultado. Limpe a busca ou selecione Todos para visualizar os comprovantes."
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

    if (showCompanies) {
        CompanySelectorDialog(
            companies = companies,
            selectedId = activeCompany?.id,
            onSelect = {
                viewModel.selectCompany(it.id)
                showCompanies = false
            },
            onNew = {
                showCompanies = false
                companyFormTarget = null
                showCompanyForm = true
            },
            onDismiss = { showCompanies = false }
        )
    }

    if (showCompanyForm) {
        CompanyFormDialog(
            company = companyFormTarget,
            onDismiss = { showCompanyForm = false },
            onSave = { name, cnpj, owner, companyType, taxRegime ->
                val target = companyFormTarget
                if (target == null) {
                    viewModel.createCompany(
                        name = name,
                        cnpj = cnpj,
                        ownerName = owner,
                        companyType = companyType,
                        taxRegime = taxRegime,
                        onSaved = {
                            showCompanyForm = false
                            message = "Empresa cadastrada e selecionada."
                        },
                        onError = { message = it }
                    )
                } else {
                    viewModel.updateCompany(
                        company = target,
                        name = name,
                        cnpj = cnpj,
                        ownerName = owner,
                        companyType = companyType,
                        taxRegime = taxRegime,
                        onSaved = {
                            showCompanyForm = false
                            message = "Cadastro da empresa atualizado."
                        },
                        onError = { message = it }
                    )
                }
            }
        )
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
private fun CompanyCard(
    company: Company?,
    onSwitch: () -> Unit,
    onEdit: () -> Unit,
    onNew: () -> Unit
) {
    Card(Modifier.fillMaxWidth()) {
        Column(
            Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text("Empresa ativa", style = MaterialTheme.typography.labelLarge)
            Text(
                company?.name ?: "Carregando...",
                style = MaterialTheme.typography.titleLarge
            )
            if (company != null) {
                Text(
                    companyTypeLabel(company.companyType),
                    style = MaterialTheme.typography.bodySmall
                )
                Text(
                    taxRegimeLabel(company.taxRegime),
                    style = MaterialTheme.typography.bodySmall
                )
            }
            if (!company?.cnpj.isNullOrBlank()) {
                Text(
                    "CNPJ ${formatCnpj(company!!.cnpj)}",
                    style = MaterialTheme.typography.bodySmall
                )
            }
            Row(
                Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(onClick = onSwitch) { Text("Trocar") }
                TextButton(onClick = onEdit, enabled = company != null) {
                    Text("Editar")
                }
                TextButton(onClick = onNew) { Text("Nova empresa") }
            }
        }
    }
}

@Composable
private fun CompanySelectorDialog(
    companies: List<Company>,
    selectedId: Long?,
    onSelect: (Company) -> Unit,
    onNew: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Selecionar empresa") },
        text = {
            Column(
                Modifier
                    .fillMaxWidth()
                    .heightIn(max = 420.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                companies.forEach { company ->
                    FilterChip(
                        selected = company.id == selectedId,
                        onClick = { onSelect(company) },
                        label = {
                            Column {
                                Text(company.name)
                                Text(
                                    companyTypeLabel(company.companyType),
                                    style = MaterialTheme.typography.bodySmall
                                )
                                Text(
                                    taxRegimeLabel(company.taxRegime),
                                    style = MaterialTheme.typography.bodySmall
                                )
                                if (company.cnpj.isNotBlank()) {
                                    Text(
                                        formatCnpj(company.cnpj),
                                        style = MaterialTheme.typography.bodySmall
                                    )
                                }
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                OutlinedButton(
                    onClick = onNew,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Adicionar empresa")
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Fechar") }
        }
    )
}

@Composable
private fun CompanyFormDialog(
    company: Company?,
    onDismiss: () -> Unit,
    onSave: (
        name: String,
        cnpj: String,
        owner: String,
        companyType: CompanyType,
        taxRegime: TaxRegime
    ) -> Unit
) {
    var name by remember(company?.id) {
        mutableStateOf(company?.name.orEmpty())
    }
    var cnpj by remember(company?.id) {
        mutableStateOf(company?.cnpj.orEmpty())
    }
    var owner by remember(company?.id) {
        mutableStateOf(company?.ownerName.orEmpty())
    }
    var companyType by remember(company?.id) {
        mutableStateOf(company?.companyType ?: CompanyType.MEI)
    }
    var taxRegime by remember(company?.id) {
        mutableStateOf(company?.taxRegime ?: TaxRegime.SIMEI)
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(if (company == null) "Nova empresa" else "Editar empresa")
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
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Nome da loja/empresa") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = cnpj,
                    onValueChange = {
                        cnpj = it.filter(Char::isDigit).take(14)
                    },
                    label = { Text("CNPJ") },
                    supportingText = {
                        Text("14 dígitos; pode deixar vazio por enquanto.")
                    },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = owner,
                    onValueChange = { owner = it },
                    label = { Text("Titular") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Text(
                    "Porte / enquadramento",
                    style = MaterialTheme.typography.labelLarge
                )
                Text(
                    "ME e EPP representam o porte. LTDA é natureza jurídica e pode ser ME ou EPP, não um porte separado.",
                    style = MaterialTheme.typography.bodySmall
                )
                Row(
                    Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    CompanyType.entries.forEach { candidate ->
                        FilterChip(
                            selected = companyType == candidate,
                            onClick = {
                                companyType = candidate
                                taxRegime = CompanyProfileRules.normalizedTaxRegime(
                                    companyType = candidate,
                                    taxRegime = taxRegime
                                )
                            },
                            label = { Text(companyTypeLabel(candidate)) }
                        )
                    }
                }

                Text(
                    "Regime tributário",
                    style = MaterialTheme.typography.labelLarge
                )
                if (companyType == CompanyType.MEI) {
                    Text(
                        "SIMEI — regime próprio do MEI",
                        style = MaterialTheme.typography.bodyMedium
                    )
                } else {
                    Text(
                        "Selecione o regime separadamente do porte da empresa.",
                        style = MaterialTheme.typography.bodySmall
                    )
                    Column(
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        CompanyProfileRules.allowedTaxRegimes(companyType)
                            .forEach { candidate ->
                                FilterChip(
                                    selected = taxRegime == candidate,
                                    onClick = { taxRegime = candidate },
                                    label = { Text(taxRegimeLabel(candidate)) },
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onSave(
                        name,
                        cnpj,
                        owner,
                        companyType,
                        CompanyProfileRules.normalizedTaxRegime(
                            companyType = companyType,
                            taxRegime = taxRegime
                        )
                    )
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

private fun companyTypeLabel(type: CompanyType): String =
    when (type) {
        CompanyType.MEI -> "MEI — Microempreendedor Individual"
        CompanyType.ME -> "ME — Microempresa"
        CompanyType.EPP -> "EPP — Empresa de Pequeno Porte"
        CompanyType.OTHER -> "Outro enquadramento"
    }

private fun taxRegimeLabel(regime: TaxRegime): String =
    when (regime) {
        TaxRegime.SIMEI -> "SIMEI — MEI"
        TaxRegime.SIMPLES_NACIONAL -> "Simples Nacional"
        TaxRegime.LUCRO_PRESUMIDO -> "Lucro Presumido"
        TaxRegime.LUCRO_REAL -> "Lucro Real"
        TaxRegime.OTHER -> "Outro / não informado"
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

private fun formatCnpj(value: String): String {
    val digits = value.filter(Char::isDigit)
    if (digits.length != 14) return value

    return digits.substring(0, 2) + "." +
        digits.substring(2, 5) + "." +
        digits.substring(5, 8) + "/" +
        digits.substring(8, 12) + "-" +
        digits.substring(12, 14)
}
