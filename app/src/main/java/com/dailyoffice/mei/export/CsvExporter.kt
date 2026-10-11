package com.dailyoffice.mei.export

import android.content.Context
import android.net.Uri
import androidx.core.content.FileProvider
import com.dailyoffice.mei.data.Account
import com.dailyoffice.mei.data.Company
import com.dailyoffice.mei.data.PaymentStatus
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
        company: Company?,
        receipts: List<Receipt>,
        transactions: List<Transaction>,
        accounts: List<Account>
    ): Uri {
        val dir = File(context.cacheDir, "exports").apply { mkdirs() }
        val file = File(dir, "DailyOffice_${System.currentTimeMillis()}.csv")

        val accountById = accounts.associateBy { it.id }

        file.bufferedWriter().use { out ->
            out.appendLine("EMPRESA;CNPJ;TIPO_EMPRESA;REGIME_TRIBUTARIO;TIPO;ID;DATA;DESCRICAO;VALOR;CLASSIFICACAO;PAGAMENTO;STATUS;CONTA_ORIGEM;CONTA_DESTINO;DOCUMENTO;CATEGORIA;COMPROVANTE_ID;ENTRA_NO_FLUXO;DATA_BAIXA;VENCIMENTO")

            receipts.forEach { receipt ->
                out.appendLine(
                    listOf(
                        company?.name.orEmpty(),
                        company?.cnpj.orEmpty(),
                        company?.companyType?.name.orEmpty(),
                        company?.taxRegime?.name.orEmpty(),
                        "COMPROVANTE",
                        receipt.id.toString(),
                        date(receipt.issuedAt ?: receipt.createdAt),
                        receipt.supplier,
                        money(receipt.totalCents),
                        receipt.ownership.name,
                        receipt.paymentMethod.name,
                        receipt.paymentStatus.name,
                        accountById[receipt.accountId]?.name.orEmpty(),
                        "",
                        receipt.documentNumber,
                        receipt.category,
                        receipt.id.toString(),
                        "NAO",
                        "",
                        receipt.dueAt?.let { date(it) }.orEmpty()
                    ).joinToString(";") { cell(it) }
                )
            }

            transactions.forEach { tx ->
                out.appendLine(
                    listOf(
                        company?.name.orEmpty(),
                        company?.cnpj.orEmpty(),
                        company?.companyType?.name.orEmpty(),
                        company?.taxRegime?.name.orEmpty(),
                        tx.kind.name,
                        tx.id.toString(),
                        date(tx.createdAt),
                        tx.description,
                        money(tx.amountCents),
                        tx.ownership.name,
                        tx.paymentMethod.name,
                        tx.paymentStatus.name,
                        accountById[tx.accountId]?.name.orEmpty(),
                        accountById[tx.counterpartyAccountId]?.name.orEmpty(),
                        "",
                        "",
                        tx.receiptId?.toString().orEmpty(),
                        if (tx.paymentStatus == PaymentStatus.PAID) "SIM" else "NAO",
                        if (tx.paymentStatus == PaymentStatus.PAID) date(tx.paidAt ?: tx.createdAt) else "",
                        tx.dueAt?.let { date(it) }.orEmpty()
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
