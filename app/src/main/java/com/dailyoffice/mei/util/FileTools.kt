package com.dailyoffice.mei.util

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import com.dailyoffice.mei.data.ReceiptEntity
import com.dailyoffice.mei.data.RevenueEntity
import java.io.File

object ReceiptImageStore {
    fun newCameraUri(context: Context): Uri {
        val dir = File(context.filesDir, "receipts").apply { mkdirs() }
        val file = File(dir, "receipt_" + System.currentTimeMillis() + ".jpg")
        return FileProvider.getUriForFile(context, context.packageName + ".files", file)
    }

    fun importToArchive(context: Context, source: Uri): Uri {
        val dir = File(context.filesDir, "receipts").apply { mkdirs() }
        val file = File(dir, "receipt_" + System.currentTimeMillis() + ".jpg")
        context.contentResolver.openInputStream(source).use { input ->
            requireNotNull(input) { "Não foi possível abrir a imagem." }
            file.outputStream().use { output -> input.copyTo(output) }
        }
        return FileProvider.getUriForFile(context, context.packageName + ".files", file)
    }
}

object CsvExporter {
    fun share(context: Context, receipts: List<ReceiptEntity>, revenues: List<RevenueEntity>) {
        val dir = File(context.cacheDir, "exports").apply { mkdirs() }
        val file = File(dir, "dailyoffice_export_" + System.currentTimeMillis() + ".csv")

        file.bufferedWriter().use { out ->
            out.appendLine("TIPO;DATA;FORNECEDOR/FONTE;VALOR;CLASSIFICACAO;PAGAMENTO;STATUS;OBSERVACAO")
            receipts.forEach { r ->
                out.appendLine(listOf(
                    "DESPESA", r.documentDate.orEmpty(), csv(r.merchant),
                    (r.amountCents / 100.0).toString().replace('.', ','),
                    r.classification, r.paymentMethod, r.status, csv(r.notes)
                ).joinToString(";"))
            }
            revenues.forEach { r ->
                out.appendLine(listOf(
                    "RECEITA", r.receivedAt.toString(), csv(r.source),
                    (r.amountCents / 100.0).toString().replace('.', ','),
                    "EMPRESA", "", "RECEBIDO", csv(r.notes)
                ).joinToString(";"))
            }
        }

        val uri = FileProvider.getUriForFile(context, context.packageName + ".files", file)
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/csv"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, "Exportar DailyOffice"))
    }

    private fun csv(value: String): String = """ + value.replace(""", """") + """
}
