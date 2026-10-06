package com.dailyoffice.mei

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.weight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.dailyoffice.mei.data.ReceiptEntity
import com.dailyoffice.mei.data.RevenueEntity
import com.dailyoffice.mei.util.CsvExporter
import com.dailyoffice.mei.util.MeiCalculator
import com.dailyoffice.mei.util.MeiSettings
import com.dailyoffice.mei.util.Money
import com.dailyoffice.mei.util.ReceiptImageStore
import java.util.Calendar

class MainActivity : ComponentActivity() {
    private val vm: DailyOfficeViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                DailyOfficeApp(vm)
            }
        }
    }
}

private enum class Tab(val label: String) {
    DASHBOARD("Resumo"),
    RECEIPTS("Comprovantes"),
    ADD("Adicionar"),
    PENDING("Pendências"),
    SETTINGS("Config.")
}

@Composable
private fun DailyOfficeApp(vm: DailyOfficeViewModel) {
    val receipts by vm.receipts.collectAsState()
    val revenues by vm.revenues.collectAsState()
    val settings by vm.settings.collectAsState()
    val message by vm.message.collectAsState()
    var tab by remember { mutableStateOf(Tab.DASHBOARD) }
    val snackbar = remember { SnackbarHostState() }

    LaunchedEffect(message) {
        val current = message
        if (current != null) {
            snackbar.showSnackbar(current)
            vm.consumeMessage()
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        bottomBar = {
            NavigationBar {
                Tab.entries.forEach { item ->
                    NavigationBarItem(
                        selected = tab == item,
                        onClick = { tab = item },
                        icon = { Text(if (tab == item) "●" else "○") },
                        label = { Text(item.label) }
                    )
                }
            }
        }
    ) { padding ->
        Box(Modifier.padding(padding).fillMaxSize()) {
            when (tab) {
                Tab.DASHBOARD -> DashboardScreen(vm, receipts, revenues, settings)
                Tab.RECEIPTS -> ReceiptListScreen(receipts, false)
                Tab.ADD -> AddReceiptScreen(vm) { tab = Tab.RECEIPTS }
                Tab.PENDING -> ReceiptListScreen(receipts, true)
                Tab.SETTINGS -> SettingsScreen(vm, receipts, revenues, settings)
            }
        }
    }
}

@Composable
private fun DashboardScreen(
    vm: DailyOfficeViewModel,
    receipts: List<ReceiptEntity>,
    revenues: List<RevenueEntity>,
    settings: MeiSettings
) {
    var revenueDialog by remember { mutableStateOf(false) }
    val now = Calendar.getInstance()
    val year = now.get(Calendar.YEAR)
    val start = Calendar.getInstance().apply {
        set(year, Calendar.JANUARY, 1, 0, 0, 0)
        set(Calendar.MILLISECOND, 0)
    }.timeInMillis
    val end = Calendar.getInstance().apply {
        set(year, Calendar.DECEMBER, 31, 23, 59, 59)
        set(Calendar.MILLISECOND, 999)
    }.timeInMillis

    val gross = revenues.filter { it.receivedAt in start..end }.sumOf { it.amountCents }
    val limit = MeiCalculator.limitForYear(settings, year)
    val progress = if (limit > 0) (gross.toFloat() / limit).coerceIn(0f, 1f) else 0f
    val companyExpenses = receipts.filter { it.classification == "EMPRESA" }.sumOf { it.amountCents }
    val personalExpenses = receipts.filter { it.classification == "PESSOAL" }.sumOf { it.amountCents }
    val mixedExpenses = receipts.filter { it.classification == "MISTO" }.sumOf { it.amountCents }
    val pending = receipts.filter { it.status == "PENDENTE" || it.classification == "REVISAR" }

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Text("DailyOffice MEI", style = MaterialTheme.typography.headlineMedium)
            Text("Seu arquivo de comprovantes e painel do MEI.")
        }
        item { SummaryCard("Receita bruta registrada no ano", Money.format(gross)) }
        item {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    Text("Limite MEI configurado")
                    Text(Money.format(gross) + " de " + Money.format(limit))
                    Spacer(Modifier.height(8.dp))
                    LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth())
                    Text((progress * 100).toInt().toString() + "% utilizado")
                }
            }
        }
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SummaryCard("Empresa", Money.format(companyExpenses), Modifier.weight(1f))
                SummaryCard("Pessoal", Money.format(personalExpenses), Modifier.weight(1f))
            }
        }
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SummaryCard("Mistos", Money.format(mixedExpenses), Modifier.weight(1f))
                SummaryCard("Pendências", pending.size.toString(), Modifier.weight(1f))
            }
        }
        item {
            Button(onClick = { revenueDialog = true }, modifier = Modifier.fillMaxWidth()) {
                Text("Adicionar receita bruta")
            }
        }
        item {
            Text(
                "O painel é um controle administrativo. Para obrigações fiscais e desenquadramento, confirme os valores com as regras oficiais ou um contador.",
                style = MaterialTheme.typography.bodySmall
            )
        }
    }

    if (revenueDialog) {
        RevenueDialog(
            onDismiss = { revenueDialog = false },
            onSave = { amount, source, notes ->
                vm.addRevenue(amount, source, notes)
                revenueDialog = false
            }
        )
    }
}

@Composable
private fun SummaryCard(title: String, value: String, modifier: Modifier = Modifier) {
    Card(modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Text(title, style = MaterialTheme.typography.labelLarge)
            Text(value, style = MaterialTheme.typography.headlineSmall)
        }
    }
}

@Composable
private fun ReceiptListScreen(receipts: List<ReceiptEntity>, pendingOnly: Boolean) {
    val visible = if (pendingOnly) {
        receipts.filter { it.status == "PENDENTE" || it.classification == "REVISAR" }
    } else receipts

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            Text(
                if (pendingOnly) "Pendências" else "Comprovantes arquivados",
                style = MaterialTheme.typography.headlineMedium
            )
        }
        if (visible.isEmpty()) item { Text("Nenhum registro aqui ainda.") }

        items(visible, key = { it.id }) { receipt ->
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(14.dp)) {
                    Text(receipt.merchant, style = MaterialTheme.typography.titleMedium)
                    Text(Money.format(receipt.amountCents), style = MaterialTheme.typography.headlineSmall)
                    Text(receipt.classification + " • " + receipt.paymentMethod + " • " + receipt.status)
                    val docDate = receipt.documentDate
                    if (docDate != null) Text("Documento: " + docDate)
                    val dueDate = receipt.dueDate
                    if (dueDate != null) Text("Vencimento: " + dueDate)
                    if (receipt.notes.isNotBlank()) Text(receipt.notes)
                }
            }
        }
    }
}

@Composable
private fun AddReceiptScreen(vm: DailyOfficeViewModel, onSaved: () -> Unit) {
    val context = LocalContext.current
    val draft by vm.draft.collectAsState()
    val busy by vm.ocrBusy.collectAsState()
    var cameraUri by remember { mutableStateOf<android.net.Uri?>(null) }

    val camera = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
        if (ok) {
            val uri = cameraUri
            if (uri != null) vm.processImage(uri)
        }
    }

    val gallery = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { source ->
        if (source != null) {
            runCatching { ReceiptImageStore.importToArchive(context, source) }
                .onSuccess { vm.processImage(it) }
        }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item { Text("Novo comprovante", style = MaterialTheme.typography.headlineMedium) }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = {
                    val uri = ReceiptImageStore.newCameraUri(context)
                    cameraUri = uri
                    camera.launch(uri)
                }) { Text("Tirar foto") }
                OutlinedButton(onClick = { gallery.launch("image/*") }) { Text("Escolher foto") }
            }
        }
        if (busy) item { LinearProgressIndicator(Modifier.fillMaxWidth()) }
        item {
            OutlinedTextField(
                value = draft.merchant,
                onValueChange = { value -> vm.updateDraft { it.copy(merchant = value) } },
                label = { Text("Fornecedor") },
                modifier = Modifier.fillMaxWidth()
            )
        }
        item {
            OutlinedTextField(
                value = draft.cnpj,
                onValueChange = { value -> vm.updateDraft { it.copy(cnpj = value) } },
                label = { Text("CNPJ do fornecedor") },
                modifier = Modifier.fillMaxWidth()
            )
        }
        item {
            OutlinedTextField(
                value = draft.date,
                onValueChange = { value -> vm.updateDraft { it.copy(date = value) } },
                label = { Text("Data do documento") },
                placeholder = { Text("dd/mm/aaaa") },
                modifier = Modifier.fillMaxWidth()
            )
        }
        item {
            OutlinedTextField(
                value = draft.amount,
                onValueChange = { value -> vm.updateDraft { it.copy(amount = value) } },
                label = { Text("Valor") },
                placeholder = { Text("0,00") },
                modifier = Modifier.fillMaxWidth()
            )
        }
        item {
            ChoiceField("Classificação", draft.classification, listOf("EMPRESA", "PESSOAL", "MISTO", "REVISAR")) {
                choice -> vm.updateDraft { it.copy(classification = choice) }
            }
        }
        item {
            ChoiceField(
                "Pagamento",
                draft.paymentMethod,
                listOf("DINHEIRO", "DEBITO_PJ", "DEBITO_PESSOAL", "CREDITO", "PIX", "REVISAR")
            ) { choice -> vm.updateDraft { it.copy(paymentMethod = choice) } }
        }
        item {
            ChoiceField("Situação", draft.status, listOf("PAGO", "PENDENTE", "CANCELADO")) {
                choice -> vm.updateDraft { it.copy(status = choice) }
            }
        }
        item {
            OutlinedTextField(
                value = draft.dueDate,
                onValueChange = { value -> vm.updateDraft { it.copy(dueDate = value) } },
                label = { Text("Vencimento, se houver") },
                placeholder = { Text("dd/mm/aaaa") },
                modifier = Modifier.fillMaxWidth()
            )
        }
        item {
            OutlinedTextField(
                value = draft.notes,
                onValueChange = { value -> vm.updateDraft { it.copy(notes = value) } },
                label = { Text("Observações") },
                modifier = Modifier.fillMaxWidth()
            )
        }
        item {
            Button(
                onClick = {
                    val valid = draft.imageUri.isNotBlank() && Money.parseToCents(draft.amount) > 0
                    vm.saveDraft()
                    if (valid) onSaved()
                },
                enabled = !busy,
                modifier = Modifier.fillMaxWidth()
            ) { Text("Arquivar comprovante") }
        }
        item {
            Text(
                "A leitura automática preenche um rascunho. Revise fornecedor, valor, classificação e forma de pagamento antes de salvar.",
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}

@Composable
private fun ChoiceField(label: String, value: String, options: List<String>, onChoose: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Column {
        Text(label, style = MaterialTheme.typography.labelLarge)
        OutlinedButton(onClick = { expanded = true }, modifier = Modifier.fillMaxWidth()) {
            Text(value)
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEach { option ->
                DropdownMenuItem(
                    text = { Text(option) },
                    onClick = {
                        onChoose(option)
                        expanded = false
                    }
                )
            }
        }
    }
}

@Composable
private fun SettingsScreen(
    vm: DailyOfficeViewModel,
    receipts: List<ReceiptEntity>,
    revenues: List<RevenueEntity>,
    settings: MeiSettings
) {
    val context = LocalContext.current
    var cnpj by remember(settings) { mutableStateOf(settings.cnpj) }
    var month by remember(settings) { mutableStateOf(settings.startMonth.toString()) }
    var year by remember(settings) { mutableStateOf(settings.startYear.toString()) }
    var limit by remember(settings) {
        mutableStateOf("%.2f".format(settings.annualLimitCents / 100.0).replace('.', ','))
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item { Text("Configurações", style = MaterialTheme.typography.headlineMedium) }
        item {
            OutlinedTextField(cnpj, { cnpj = it }, label = { Text("CNPJ do MEI") }, modifier = Modifier.fillMaxWidth())
        }
        item {
            OutlinedTextField(month, { month = it }, label = { Text("Mês de abertura (1-12)") }, modifier = Modifier.fillMaxWidth())
        }
        item {
            OutlinedTextField(year, { year = it }, label = { Text("Ano de abertura") }, modifier = Modifier.fillMaxWidth())
        }
        item {
            OutlinedTextField(limit, { limit = it }, label = { Text("Limite anual configurado") }, modifier = Modifier.fillMaxWidth())
        }
        item {
            Button(
                onClick = {
                    vm.updateSettings(
                        cnpj,
                        month.toIntOrNull() ?: 1,
                        year.toIntOrNull() ?: Calendar.getInstance().get(Calendar.YEAR),
                        limit
                    )
                },
                modifier = Modifier.fillMaxWidth()
            ) { Text("Salvar configurações") }
        }
        item {
            OutlinedButton(
                onClick = { CsvExporter.share(context, receipts, revenues) },
                modifier = Modifier.fillMaxWidth()
            ) { Text("Exportar CSV para contador/backup") }
        }
        item {
            Text(
                "Privacidade: fotos e banco de dados ficam no armazenamento privado do app. O projeto no GitHub não recebe seus comprovantes automaticamente.",
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}

@Composable
private fun RevenueDialog(onDismiss: () -> Unit, onSave: (String, String, String) -> Unit) {
    var amount by remember { mutableStateOf("") }
    var source by remember { mutableStateOf("iFood") }
    var notes by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Adicionar receita bruta") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(amount, { amount = it }, label = { Text("Valor") })
                OutlinedTextField(source, { source = it }, label = { Text("Fonte") })
                OutlinedTextField(notes, { notes = it }, label = { Text("Observação") })
            }
        },
        confirmButton = { Button(onClick = { onSave(amount, source, notes) }) { Text("Salvar") } },
        dismissButton = { OutlinedButton(onClick = onDismiss) { Text("Cancelar") } }
    )
}
