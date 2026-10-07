package com.dailyoffice.mei.export

import android.content.Context
import android.net.Uri
import androidx.core.content.FileProvider
import com.dailyoffice.mei.data.Receipt
import com.dailyoffice.mei.data.Transaction
import java.io.File
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

object CsvExporter {
    private val dateFormat = DateTimeFormatter.ofPattern("dd/MM/yyyy")
        .withZone(ZoneId.systemDefault())

    fun export(
        context: Context,
        receipts: List<Receipt>,
        transactions: List<Transaction>
    ): Uri {
        val dir = File(context.cacheDir, "exports").apply { mkdirs() }
        val file = File(dir, "DailyOffice_${System.currentTimeMillis()}.csv")

        file.bufferedWriter().use { out ->
            out.appendLine("TIPO;ID;DATA;DESCRICAO;VALOR;CLASSIFICACAO;PAGAMENTO;STATUS;DOCUMENTO;CATEGORIA;COMPROVANTE_ID")

            receipts.forEach { receipt ->
                out.appendLine(
                    listOf(
                        "COMPROVANTE",
                        receipt.id.toString(),
                        date(receipt.issuedAt ?: receipt.createdAt),
                        receipt.supplier,
                        money(receipt.totalCents),
                        receipt.ownership.name,
                        receipt.paymentMethod.name,
                        receipt.paymentStatus.name,
                        receipt.documentNumber,
                        receipt.category,
                        receipt.id.toString()
                    ).joinToString(";") { cell(it) }
                )
            }

            transactions.forEach { tx ->
                out.appendLine(
                    listOf(
                        tx.kind.name,
                        tx.id.toString(),
                        date(tx.createdAt),
                        tx.description,
                        money(tx.amountCents),
                        tx.ownership.name,
                        tx.paymentMethod.name,
                        tx.paymentStatus.name,
                        "",
                        "",
                        tx.receiptId?.toString().orEmpty()
                    ).joinToString(";") { cell(it) }
                )
            }
        }

        return FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )
    }

    private fun date(epoch: Long): String =
        dateFormat.format(Instant.ofEpochMilli(epoch))

    private fun money(cents: Long): String =
        "%.2f".format(java.util.Locale.US, cents / 100.0)

    private fun cell(value: String): String =
        "\"" + value.replace("\"", "\"\"") + "\""
}
