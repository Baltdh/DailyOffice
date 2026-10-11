package com.dailyoffice.mei.ui

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dailyoffice.mei.data.AccountingDocument
import com.dailyoffice.mei.data.AccountingDocumentType
import com.dailyoffice.mei.viewmodel.ReceiptViewModel
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DocumentsScreen(viewModel: ReceiptViewModel, onBack: () -> Unit) {
    val context = LocalContext.current
    val documents by viewModel.accountingDocuments.collectAsStateWithLifecycle()
    val company by viewModel.activeCompany.collectAsStateWithLifecycle()
    var selectedType by remember { mutableStateOf(AccountingDocumentType.BOLETO) }
    var message by remember { mutableStateOf<String?>(null) }
    var pendingDelete by remember { mutableStateOf<AccountingDocument?>(null) }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) viewModel.importAccountingDocument(
            uri, selectedType,
            onSaved = { message = "Documento arquivado com sucesso." },
            onError = { message = it }
        )
    }

    Scaffold(topBar = {
        TopAppBar(
            title = { Text("Documentos contábeis") },
            navigationIcon = { TextButton(onClick = onBack) { Text("Voltar") } }
        )
    }) { padding ->
        LazyColumn(
            Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            item {
                Text("Empresa: ${company?.name ?: "Selecionando..."}")
                Text("Boletos, notas fiscais, guias, extratos e arquivos contábeis ficam guardados somente no aplicativo, separados por CNPJ.", style = MaterialTheme.typography.bodySmall)
                Text("Tipo do arquivo", style = MaterialTheme.typography.titleMedium)
                Column {
                    AccountingDocumentType.entries.forEach { type ->
                        FilterChip(
                            selected = selectedType == type,
                            onClick = { selectedType = type },
                            label = { Text(documentTypeLabel(type)) }
                        )
                    }
                }
                Button(
                    onClick = {
                        picker.launch(arrayOf(
                            "application/pdf", "application/xml", "text/xml",
                            "text/csv", "text/plain", "application/octet-stream", "*/*"
                        ))
                    },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = company != null
                ) { Text("Selecionar arquivo (até 25 MB)") }
                Text("Importar não registra pagamento, vencimento ou despesa automaticamente. Confira os dados antes de lançar no financeiro.", style = MaterialTheme.typography.bodySmall)
            }
            message?.let { notice ->
                item {
                    Text(notice)
                    TextButton(onClick = { message = null }) { Text("Fechar") }
                }
            }
            items(documents, key = { it.id }) { doc ->
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(doc.displayName, style = MaterialTheme.typography.titleSmall)
                        Text(documentTypeLabel(doc.type))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(onClick = {
                                runCatching {
                                    val file = File(doc.localPath)
                                    val uri = FileProvider.getUriForFile(
                                        context, "${context.packageName}.fileprovider", file
                                    )
                                    val intent = Intent(Intent.ACTION_VIEW).apply {
                                        setDataAndType(uri, doc.mimeType)
                                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                    }
                                    context.startActivity(Intent.createChooser(intent, "Abrir documento"))
                                }.onFailure { message = it.message ?: "Não foi possível abrir." }
                            }) { Text("Abrir") }
                            TextButton(onClick = { pendingDelete = doc }) { Text("Excluir") }
                        }
                    }
                }
            }
        }
    }
    pendingDelete?.let { doc ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text("Excluir arquivo?") },
            text = { Text("O documento ${doc.displayName} será removido permanentemente do aplicativo.") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.deleteAccountingDocument(doc,
                        onDone = { message = "Documento excluído." },
                        onError = { message = it }
                    )
                    pendingDelete = null
                }) { Text("Excluir") }
            },
            dismissButton = { TextButton(onClick = { pendingDelete = null }) { Text("Cancelar") } }
        )
    }
}

private fun documentTypeLabel(type: AccountingDocumentType): String = when (type) {
    AccountingDocumentType.BOLETO -> "Boleto"
    AccountingDocumentType.NOTA_FISCAL -> "Nota fiscal"
    AccountingDocumentType.EXTRATO -> "Extrato bancário"
    AccountingDocumentType.GUIA_TRIBUTO -> "Guia de tributo"
    AccountingDocumentType.CONTRATO -> "Contrato"
    AccountingDocumentType.OUTRO -> "Outro documento"
}
