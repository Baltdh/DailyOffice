package com.dailyoffice.mei.viewmodel

import android.app.Application
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.dailyoffice.mei.data.*
import com.dailyoffice.mei.receipt.ClassificationEngine
import com.dailyoffice.mei.receipt.ReceiptArchive
import com.dailyoffice.mei.receipt.ReceiptOcr
import com.dailyoffice.mei.receipt.ReceiptParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

data class ReceiptDraft(
    val imageUri: String = "",
    val supplier: String = "",
    val documentNumber: String = "",
    val date: String = "",
    val total: String = "",
    val businessAmount: String = "",
    val personalAmount: String = "",
    val ownership: Ownership = Ownership.REVIEW,
    val paymentMethod: PaymentMethod = PaymentMethod.OTHER,
    val paymentStatus: PaymentStatus = PaymentStatus.PAID,
    val dueDate: String = "",
    val category: String = "",
    val notes: String = "",
    val rawOcr: String = "",
    val confidence: Float = 0f,
    val classificationReason: String = "",
    val warnings: List<String> = emptyList()
)

data class DashboardSummary(
    val businessCents: Long = 0,
    val personalCents: Long = 0,
    val pendingCents: Long = 0,
    val reviewCount: Int = 0,
    val receiptCount: Int = 0
)

class ReceiptViewModel(application: Application) : AndroidViewModel(application) {
    private val db = DailyOfficeDb.get(application)
    private val dao = db.receiptDao()

    val receipts = dao.observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val summary = receipts
        .map { list ->
            DashboardSummary(
                businessCents = list.sumOf { it.businessCents },
                personalCents = list.sumOf { it.personalCents },
                pendingCents = list.filter {
                    it.paymentStatus == PaymentStatus.PENDING || it.paymentStatus == PaymentStatus.OVERDUE
                }.sumOf { it.totalCents },
                reviewCount = list.count { it.ownership == Ownership.REVIEW },
                receiptCount = list.size
            )
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), DashboardSummary())

    var draft by mutableStateOf(ReceiptDraft())
        private set

    var saving by mutableStateOf(false)
        private set

    fun beginReview(imageUri: String, rawText: String) {
        val parsed = ReceiptParser.parse(rawText)
        val suggestion = ClassificationEngine.suggest(rawText, parsed.supplier)
        val amount = parsed.totalCents?.let(::formatCents).orEmpty()

        draft = ReceiptDraft(
            imageUri = imageUri,
            supplier = parsed.supplier.orEmpty(),
            documentNumber = parsed.document.orEmpty(),
            date = parsed.date.orEmpty(),
            total = amount,
            businessAmount = if (suggestion.ownership == Ownership.BUSINESS) amount else "",
            personalAmount = if (suggestion.ownership == Ownership.PERSONAL) amount else "",
            ownership = suggestion.ownership,
            rawOcr = rawText,
            confidence = parsed.confidence,
            classificationReason = suggestion.reason,
            warnings = parsed.warnings
        )
    }

    fun importReceipt(uri: Uri, onReady: () -> Unit, onError: (String) -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            runCatching { ReceiptArchive.archiveImported(getApplication(), uri) }
                .onSuccess { archived ->
                    withContext(Dispatchers.Main) {
                        ReceiptOcr.read(
                            getApplication(),
                            archived,
                            onSuccess = { text ->
                                beginReview(archived.toString(), text)
                                onReady()
                            },
                            onFailure = { onError(it.message ?: "Falha no OCR.") }
                        )
                    }
                }
                .onFailure {
                    withContext(Dispatchers.Main) {
                        onError(it.message ?: "Falha ao arquivar imagem.")
                    }
                }
        }
    }

    fun updateSupplier(v: String) { draft = draft.copy(supplier = v) }
    fun updateDocument(v: String) { draft = draft.copy(documentNumber = v) }
    fun updateDate(v: String) { draft = draft.copy(date = v) }
    fun updateTotal(v: String) { draft = draft.copy(total = v) }
    fun updateBusinessAmount(v: String) { draft = draft.copy(businessAmount = v) }
    fun updatePersonalAmount(v: String) { draft = draft.copy(personalAmount = v) }

    fun updateOwnership(v: Ownership) {
        val total = draft.total
        draft = when (v) {
            Ownership.BUSINESS -> draft.copy(ownership = v, businessAmount = total, personalAmount = "")
            Ownership.PERSONAL -> draft.copy(ownership = v, businessAmount = "", personalAmount = total)
            else -> draft.copy(ownership = v)
        }
    }

    fun updatePaymentMethod(v: PaymentMethod) { draft = draft.copy(paymentMethod = v) }
    fun updatePaymentStatus(v: PaymentStatus) { draft = draft.copy(paymentStatus = v) }
    fun updateDueDate(v: String) { draft = draft.copy(dueDate = v) }
    fun updateCategory(v: String) { draft = draft.copy(category = v) }
    fun updateNotes(v: String) { draft = draft.copy(notes = v) }

    fun save(onSaved: () -> Unit, onError: (String) -> Unit) {
        val total = parseCents(draft.total)
            ?: return onError("Informe um valor total válido.")

        val business = when (draft.ownership) {
            Ownership.BUSINESS -> total
            Ownership.MIXED -> parseCents(draft.businessAmount) ?: 0
            else -> 0
        }
        val personal = when (draft.ownership) {
            Ownership.PERSONAL -> total
            Ownership.MIXED -> parseCents(draft.personalAmount) ?: 0
            else -> 0
        }

        if (draft.ownership == Ownership.MIXED && business + personal != total) {
            return onError("Em compra mista, empresa + pessoal precisa ser igual ao total.")
        }

        saving = true
        viewModelScope.launch(Dispatchers.IO) {
            runCatching {
                val receipt = Receipt(
                    imageUri = draft.imageUri,
                    supplier = draft.supplier.trim(),
                    documentNumber = draft.documentNumber.trim(),
                    issuedAt = parseDate(draft.date),
                    totalCents = total,
                    businessCents = business,
                    personalCents = personal,
                    ownership = draft.ownership,
                    paymentMethod = draft.paymentMethod,
                    paymentStatus = draft.paymentStatus,
                    dueAt = parseDate(draft.dueDate),
                    category = draft.category.trim(),
                    rawOcr = draft.rawOcr,
                    ocrConfidence = draft.confidence,
                    notes = draft.notes.trim()
                )
                val receiptId = dao.insert(receipt)

                if (business > 0) {
                    db.transactionDao().insert(
                        Transaction(
                            receiptId = receiptId,
                            description = receipt.supplier.ifBlank { "Despesa da empresa" },
                            amountCents = business,
                            ownership = Ownership.BUSINESS,
                            paymentMethod = receipt.paymentMethod,
                            paymentStatus = receipt.paymentStatus,
                            paidAt = if (receipt.paymentStatus == PaymentStatus.PAID) System.currentTimeMillis() else null,
                            dueAt = receipt.dueAt
                        )
                    )
                }

                if (personal > 0) {
                    db.transactionDao().insert(
                        Transaction(
                            receiptId = receiptId,
                            description = receipt.supplier.ifBlank { "Despesa pessoal" },
                            amountCents = personal,
                            ownership = Ownership.PERSONAL,
                            paymentMethod = receipt.paymentMethod,
                            paymentStatus = receipt.paymentStatus,
                            paidAt = if (receipt.paymentStatus == PaymentStatus.PAID) System.currentTimeMillis() else null,
                            dueAt = receipt.dueAt
                        )
                    )
                }
            }.onSuccess {
                withContext(Dispatchers.Main) {
                    draft = ReceiptDraft()
                    saving = false
                    onSaved()
                }
            }.onFailure {
                withContext(Dispatchers.Main) {
                    saving = false
                    onError(it.message ?: "Não foi possível salvar o comprovante.")
                }
            }
        }
    }

    private fun parseCents(value: String): Long? = runCatching {
        val clean = value.replace("R$", "").trim()
        val normalized = if (clean.contains(',')) clean.replace(".", "").replace(",", ".") else clean
        BigDecimal(normalized)
            .movePointRight(2)
            .setScale(0, RoundingMode.HALF_UP)
            .longValueExact()
    }.getOrNull()

    private fun formatCents(cents: Long): String =
        "%.2f".format(java.util.Locale("pt", "BR"), cents / 100.0)

    private fun parseDate(value: String): Long? {
        if (value.isBlank()) return null
        val normalized = value.replace('.', '/').replace('-', '/')
        return runCatching {
            LocalDate.parse(normalized, DateTimeFormatter.ofPattern("d/M/uuuu"))
                .atStartOfDay(ZoneId.systemDefault())
                .toInstant()
                .toEpochMilli()
        }.getOrNull()
    }
}
