package com.dailyoffice.mei.viewmodel

import android.app.Application
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.room.withTransaction
import com.dailyoffice.mei.data.*
import com.dailyoffice.mei.export.CsvExporter
import com.dailyoffice.mei.finance.MeiCalculator
import com.dailyoffice.mei.finance.MeiConfig
import com.dailyoffice.mei.finance.MeiProjection
import com.dailyoffice.mei.finance.MeiSettings
import com.dailyoffice.mei.receipt.ClassificationEngine
import com.dailyoffice.mei.receipt.ReceiptArchive
import com.dailyoffice.mei.receipt.ReceiptOcr
import com.dailyoffice.mei.receipt.ReceiptParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

data class ReceiptItemDraft(
    val description: String,
    val amountCents: Long,
    val ownership: Ownership = Ownership.REVIEW,
    val confidence: Float = 0f,
    val lineIndex: Int = 0
)

data class ReceiptDraft(
    val imageUri: String = "",
    val imageSha256: String = "",
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
    val warnings: List<String> = emptyList(),
    val items: List<ReceiptItemDraft> = emptyList()
)

data class DashboardSummary(
    val businessExpensesCents: Long = 0,
    val personalExpensesCents: Long = 0,
    val revenueCents: Long = 0,
    val contributionCents: Long = 0,
    val withdrawalCents: Long = 0,
    val pendingCents: Long = 0,
    val receivableCents: Long = 0,
    val reviewCount: Int = 0,
    val receiptCount: Int = 0
)

class ReceiptViewModel(application: Application) : AndroidViewModel(application) {
    private val db = DailyOfficeDb.get(application)
    private val dao = db.receiptDao()
    private val itemDao = db.receiptItemDao()
    private val transactionDao = db.transactionDao()
    private val meiSettings = MeiSettings(application)

    val receipts = dao.observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val transactions = transactionDao.observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val meiConfig = meiSettings.state

    val summary = combine(receipts, transactions) { receiptList, txList ->
        DashboardSummary(
            businessExpensesCents = txList
                .filter { it.kind == EntryKind.EXPENSE && it.ownership == Ownership.BUSINESS }
                .sumOf { it.amountCents },
            personalExpensesCents = txList
                .filter { it.kind == EntryKind.EXPENSE && it.ownership == Ownership.PERSONAL }
                .sumOf { it.amountCents },
            revenueCents = txList
                .filter { it.kind == EntryKind.REVENUE }
                .sumOf { it.amountCents },
            contributionCents = txList
                .filter { it.kind == EntryKind.CONTRIBUTION }
                .sumOf { it.amountCents },
            withdrawalCents = txList
                .filter { it.kind == EntryKind.WITHDRAWAL }
                .sumOf { it.amountCents },
            pendingCents = txList.filter {
                it.kind == EntryKind.EXPENSE &&
                    (it.paymentStatus == PaymentStatus.PENDING ||
                        it.paymentStatus == PaymentStatus.OVERDUE)
            }.sumOf { it.amountCents },
            receivableCents = txList.filter {
                it.kind == EntryKind.REVENUE &&
                    (it.paymentStatus == PaymentStatus.PENDING ||
                        it.paymentStatus == PaymentStatus.OVERDUE)
            }.sumOf { it.amountCents },
            reviewCount = receiptList.count { it.ownership == Ownership.REVIEW },
            receiptCount = receiptList.size
        )
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        DashboardSummary()
    )

    val meiProjection: StateFlow<MeiProjection> = combine(transactions, meiConfig) { txList, config ->
        val revenue = txList
            .filter { it.kind == EntryKind.REVENUE }
            .sumOf { it.amountCents }

        MeiCalculator.calculate(
            revenueCents = revenue,
            annualLimitCents = config.annualLimitCents,
            openingMonth = config.openingMonth,
            proportionalFirstYear = config.proportionalFirstYear
        )
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        MeiCalculator.calculate(0, 8_100_000L, 8, true)
    )

    init {
        viewModelScope.launch(Dispatchers.IO) {
            dao.withoutHash().forEach { receipt ->
                runCatching {
                    val hash = ReceiptArchive.sha256(
                        getApplication(),
                        Uri.parse(receipt.imageUri)
                    )
                    dao.update(receipt.copy(imageSha256 = hash))
                }
            }
        }
    }

    var draft by mutableStateOf(ReceiptDraft())
        private set

    var saving by mutableStateOf(false)
        private set

    var editingReceiptId by mutableStateOf<Long?>(null)
        private set

    private var editingCreatedAt: Long? = null

    val isEditing: Boolean
        get() = editingReceiptId != null

    fun beginReview(imageUri: String, rawText: String, imageSha256: String = "") {
        editingReceiptId = null
        editingCreatedAt = null

        val parsed = ReceiptParser.parse(rawText)
        val overallSuggestion = ClassificationEngine.suggest(rawText, parsed.supplier)
        val amount = parsed.totalCents?.let(::formatCents).orEmpty()

        val itemDrafts = parsed.items.map { item ->
            val suggestion = ClassificationEngine.suggestItem(item.description)
            ReceiptItemDraft(
                description = item.description,
                amountCents = item.amountCents,
                ownership = suggestion.ownership,
                confidence = item.confidence,
                lineIndex = item.lineIndex
            )
        }

        val autoSplit = exactItemSplit(itemDrafts, parsed.totalCents)

        val autoOwnership = when {
            autoSplit == null -> overallSuggestion.ownership
            autoSplit.first > 0 && autoSplit.second > 0 -> Ownership.MIXED
            autoSplit.first > 0 -> Ownership.BUSINESS
            autoSplit.second > 0 -> Ownership.PERSONAL
            else -> overallSuggestion.ownership
        }

        val businessAmount = when {
            autoSplit != null && autoSplit.first > 0 -> formatCents(autoSplit.first)
            autoOwnership == Ownership.BUSINESS -> amount
            else -> ""
        }

        val personalAmount = when {
            autoSplit != null && autoSplit.second > 0 -> formatCents(autoSplit.second)
            autoOwnership == Ownership.PERSONAL -> amount
            else -> ""
        }

        val itemWarnings = buildList {
            if (itemDrafts.isNotEmpty() && autoSplit == null) {
                val reviewCount = itemDrafts.count { it.ownership == Ownership.REVIEW }
                if (reviewCount > 0) {
                    add("$reviewCount item(ns) ainda precisam ser classificados.")
                }

                val itemTotal = itemDrafts.sumOf { it.amountCents }
                if (parsed.totalCents != null && itemTotal != parsed.totalCents) {
                    add(
                        "A soma dos itens reconhecidos (${formatCents(itemTotal)}) " +
                            "não fecha com o total (${formatCents(parsed.totalCents)})."
                    )
                }
            }
        }

        val reason = if (autoSplit != null && itemDrafts.isNotEmpty()) {
            "Divisão sugerida automaticamente a partir dos itens reconhecidos."
        } else {
            overallSuggestion.reason
        }

        draft = ReceiptDraft(
            imageUri = imageUri,
            imageSha256 = imageSha256,
            supplier = parsed.supplier.orEmpty(),
            documentNumber = parsed.document.orEmpty(),
            date = parsed.date.orEmpty(),
            total = amount,
            businessAmount = businessAmount,
            personalAmount = personalAmount,
            ownership = autoOwnership,
            rawOcr = rawText,
            confidence = parsed.confidence,
            classificationReason = reason,
            warnings = parsed.warnings + itemWarnings,
            items = itemDrafts
        )
    }

    fun beginEdit(receipt: Receipt) {
        editingReceiptId = receipt.id
        editingCreatedAt = receipt.createdAt

        draft = ReceiptDraft(
            imageUri = receipt.imageUri,
            imageSha256 = receipt.imageSha256,
            supplier = receipt.supplier,
            documentNumber = receipt.documentNumber,
            date = formatDate(receipt.issuedAt),
            total = formatCents(receipt.totalCents),
            businessAmount = if (receipt.businessCents > 0) {
                formatCents(receipt.businessCents)
            } else "",
            personalAmount = if (receipt.personalCents > 0) {
                formatCents(receipt.personalCents)
            } else "",
            ownership = receipt.ownership,
            paymentMethod = receipt.paymentMethod,
            paymentStatus = receipt.paymentStatus,
            dueDate = formatDate(receipt.dueAt),
            category = receipt.category,
            notes = receipt.notes,
            rawOcr = receipt.rawOcr,
            confidence = receipt.ocrConfidence,
            classificationReason = "Editando comprovante arquivado.",
            warnings = emptyList()
        )

        viewModelScope.launch(Dispatchers.IO) {
            val storedItems = itemDao.byReceiptId(receipt.id)
            val drafts = storedItems.map { item ->
                ReceiptItemDraft(
                    description = item.description,
                    amountCents = item.amountCents,
                    ownership = item.ownership,
                    confidence = item.confidence,
                    lineIndex = item.lineIndex
                )
            }

            withContext(Dispatchers.Main) {
                if (editingReceiptId == receipt.id) {
                    draft = draft.copy(items = drafts)
                }
            }
        }
    }

    fun importReceipt(uri: Uri, onReady: () -> Unit, onError: (String) -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            runCatching {
                val archived = ReceiptArchive.archiveImported(getApplication(), uri)
                val hash = ReceiptArchive.sha256(getApplication(), archived)
                val existing = dao.findByHash(hash)

                if (existing != null) {
                    ReceiptArchive.deleteArchived(archived.toString())
                    error("Este comprovante já está arquivado.")
                }

                archived to hash
            }.onSuccess { (archived, hash) ->
                withContext(Dispatchers.Main) {
                    ReceiptOcr.read(
                        getApplication(),
                        archived,
                        onSuccess = { text ->
                            beginReview(archived.toString(), text, hash)
                            onReady()
                        },
                        onFailure = {
                            onError(it.message ?: "Falha no OCR.")
                        }
                    )
                }
            }.onFailure {
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
            Ownership.BUSINESS -> draft.copy(
                ownership = v,
                businessAmount = total,
                personalAmount = ""
            )
            Ownership.PERSONAL -> draft.copy(
                ownership = v,
                businessAmount = "",
                personalAmount = total
            )
            else -> draft.copy(ownership = v)
        }
    }

    fun updateItemOwnership(index: Int, ownership: Ownership) {
        if (index !in draft.items.indices) return

        val updated = draft.items.toMutableList()
        updated[index] = updated[index].copy(ownership = ownership)
        draft = draft.copy(items = updated)
    }

    fun applyItemSplit(): String? {
        val total = parseCents(draft.total)
            ?: return "Informe um valor total válido antes de aplicar a divisão."

        if (draft.items.isEmpty()) {
            return "Nenhum item individual foi identificado."
        }

        if (draft.items.any {
                it.ownership == Ownership.REVIEW ||
                    it.ownership == Ownership.MIXED
            }) {
            return "Classifique todos os itens como Empresa ou Pessoal antes de aplicar."
        }

        val itemTotal = draft.items.sumOf { it.amountCents }
        if (itemTotal != total) {
            return "A soma dos itens (${formatCents(itemTotal)}) não é igual ao total (${formatCents(total)})."
        }

        val business = draft.items
            .filter { it.ownership == Ownership.BUSINESS }
            .sumOf { it.amountCents }

        val personal = draft.items
            .filter { it.ownership == Ownership.PERSONAL }
            .sumOf { it.amountCents }

        val ownership = when {
            business > 0 && personal > 0 -> Ownership.MIXED
            business > 0 -> Ownership.BUSINESS
            personal > 0 -> Ownership.PERSONAL
            else -> Ownership.REVIEW
        }

        draft = draft.copy(
            ownership = ownership,
            businessAmount = if (business > 0) formatCents(business) else "",
            personalAmount = if (personal > 0) formatCents(personal) else "",
            classificationReason = "Divisão atualizada a partir da classificação dos itens."
        )

        return null
    }

    fun updatePaymentMethod(v: PaymentMethod) {
        draft = draft.copy(paymentMethod = v)
    }

    fun updatePaymentStatus(v: PaymentStatus) {
        draft = draft.copy(paymentStatus = v)
    }

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
                val duplicate = if (draft.imageSha256.isNotBlank()) {
                    dao.findByHash(draft.imageSha256)
                } else null

                if (duplicate != null && duplicate.id != editingReceiptId) {
                    error("Este comprovante já está arquivado.")
                }

                db.withTransaction {
                    val now = System.currentTimeMillis()

                    val receipt = Receipt(
                        id = editingReceiptId ?: 0,
                        imageUri = draft.imageUri,
                        imageSha256 = draft.imageSha256,
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
                        notes = draft.notes.trim(),
                        createdAt = editingCreatedAt ?: now
                    )

                    val receiptId = if (editingReceiptId == null) {
                        dao.insert(receipt)
                    } else {
                        dao.update(receipt)
                        editingReceiptId!!
                    }

                    itemDao.deleteByReceiptId(receiptId)
                    if (draft.items.isNotEmpty()) {
                        itemDao.insertAll(
                            draft.items.map { item ->
                                ReceiptItem(
                                    receiptId = receiptId,
                                    description = item.description,
                                    amountCents = item.amountCents,
                                    ownership = item.ownership,
                                    confidence = item.confidence,
                                    lineIndex = item.lineIndex
                                )
                            }
                        )
                    }

                    transactionDao.deleteByReceiptId(receiptId)
                    val txDate = receipt.issuedAt ?: receipt.createdAt

                    if (business > 0) {
                        transactionDao.insert(
                            Transaction(
                                receiptId = receiptId,
                                description = receipt.supplier.ifBlank { "Despesa da empresa" },
                                amountCents = business,
                                ownership = Ownership.BUSINESS,
                                kind = EntryKind.EXPENSE,
                                paymentMethod = receipt.paymentMethod,
                                paymentStatus = receipt.paymentStatus,
                                paidAt = if (receipt.paymentStatus == PaymentStatus.PAID) {
                                    txDate
                                } else null,
                                dueAt = receipt.dueAt,
                                createdAt = txDate
                            )
                        )
                    }

                    if (personal > 0) {
                        transactionDao.insert(
                            Transaction(
                                receiptId = receiptId,
                                description = receipt.supplier.ifBlank { "Despesa pessoal" },
                                amountCents = personal,
                                ownership = Ownership.PERSONAL,
                                kind = EntryKind.EXPENSE,
                                paymentMethod = receipt.paymentMethod,
                                paymentStatus = receipt.paymentStatus,
                                paidAt = if (receipt.paymentStatus == PaymentStatus.PAID) {
                                    txDate
                                } else null,
                                dueAt = receipt.dueAt,
                                createdAt = txDate
                            )
                        )
                    }
                }
            }.onSuccess {
                withContext(Dispatchers.Main) {
                    draft = ReceiptDraft()
                    editingReceiptId = null
                    editingCreatedAt = null
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

    fun deleteReceipt(
        receipt: Receipt,
        onDone: () -> Unit = {},
        onError: (String) -> Unit = {}
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            runCatching {
                db.withTransaction {
                    transactionDao.deleteByReceiptId(receipt.id)
                    dao.delete(receipt)
                }
                ReceiptArchive.deleteArchived(receipt.imageUri)
            }.onSuccess {
                withContext(Dispatchers.Main) { onDone() }
            }.onFailure {
                withContext(Dispatchers.Main) {
                    onError(it.message ?: "Não foi possível excluir o comprovante.")
                }
            }
        }
    }

    fun addManualEntry(
        description: String,
        amount: String,
        kind: EntryKind,
        ownership: Ownership,
        paymentMethod: PaymentMethod,
        paymentStatus: PaymentStatus,
        date: String,
        dueDate: String,
        onSaved: () -> Unit,
        onError: (String) -> Unit
    ) {
        val cents = parseCents(amount)
            ?: return onError("Informe um valor válido.")

        if (cents <= 0) {
            return onError("O valor precisa ser maior que zero.")
        }

        val effectiveOwnership = when (kind) {
            EntryKind.REVENUE,
            EntryKind.CONTRIBUTION,
            EntryKind.WITHDRAWAL -> Ownership.BUSINESS
            EntryKind.EXPENSE -> ownership
        }

        val createdAt = parseDate(date) ?: System.currentTimeMillis()
        val dueAt = parseDate(dueDate)

        viewModelScope.launch(Dispatchers.IO) {
            runCatching {
                transactionDao.insert(
                    Transaction(
                        description = description.trim().ifBlank { labelFor(kind) },
                        amountCents = cents,
                        ownership = effectiveOwnership,
                        kind = kind,
                        paymentMethod = paymentMethod,
                        paymentStatus = paymentStatus,
                        paidAt = if (paymentStatus == PaymentStatus.PAID) createdAt else null,
                        dueAt = dueAt,
                        createdAt = createdAt
                    )
                )
            }.onSuccess {
                withContext(Dispatchers.Main) { onSaved() }
            }.onFailure {
                withContext(Dispatchers.Main) {
                    onError(it.message ?: "Não foi possível salvar o lançamento.")
                }
            }
        }
    }

    fun deleteManualEntry(
        transaction: Transaction,
        onError: (String) -> Unit = {}
    ) {
        if (transaction.receiptId != null) {
            onError("Lançamentos ligados a comprovantes devem ser alterados pelo comprovante.")
            return
        }

        viewModelScope.launch(Dispatchers.IO) {
            runCatching {
                transactionDao.delete(transaction)
            }.onFailure {
                withContext(Dispatchers.Main) {
                    onError(it.message ?: "Não foi possível excluir o lançamento.")
                }
            }
        }
    }

    fun updateMeiConfig(
        annualLimit: String,
        openingMonth: String,
        firstYear: Boolean,
        onError: (String) -> Unit = {}
    ) {
        val limit = parseCents(annualLimit)
            ?: return onError("Informe um teto anual válido.")

        val month = openingMonth.toIntOrNull()
            ?: return onError("Informe o mês de abertura de 1 a 12.")

        if (month !in 1..12) {
            return onError("O mês de abertura deve estar entre 1 e 12.")
        }

        meiSettings.update(
            MeiConfig(
                annualLimitCents = limit,
                openingMonth = month,
                proportionalFirstYear = firstYear
            )
        )
    }

    fun exportCsv(
        onReady: (Uri) -> Unit,
        onError: (String) -> Unit
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            runCatching {
                CsvExporter.export(
                    getApplication(),
                    receipts.value,
                    transactions.value
                )
            }.onSuccess {
                withContext(Dispatchers.Main) {
                    onReady(it)
                }
            }.onFailure {
                withContext(Dispatchers.Main) {
                    onError(it.message ?: "Não foi possível exportar os dados.")
                }
            }
        }
    }

    private fun exactItemSplit(
        items: List<ReceiptItemDraft>,
        totalCents: Long?
    ): Pair<Long, Long>? {
        if (totalCents == null || items.isEmpty()) return null

        if (items.any {
                it.ownership == Ownership.REVIEW ||
                    it.ownership == Ownership.MIXED
            }) {
            return null
        }

        if (items.sumOf { it.amountCents } != totalCents) {
            return null
        }

        val business = items
            .filter { it.ownership == Ownership.BUSINESS }
            .sumOf { it.amountCents }

        val personal = items
            .filter { it.ownership == Ownership.PERSONAL }
            .sumOf { it.amountCents }

        return business to personal
    }

    private fun labelFor(kind: EntryKind): String = when (kind) {
        EntryKind.EXPENSE -> "Despesa"
        EntryKind.REVENUE -> "Receita"
        EntryKind.CONTRIBUTION -> "Aporte"
        EntryKind.WITHDRAWAL -> "Retirada"
    }

    private fun parseCents(value: String): Long? = runCatching {
        val clean = value.replace("R$", "").trim()
        val normalized = if (clean.contains(',')) {
            clean.replace(".", "").replace(",", ".")
        } else {
            clean
        }

        BigDecimal(normalized)
            .movePointRight(2)
            .setScale(0, RoundingMode.HALF_UP)
            .longValueExact()
    }.getOrNull()

    private fun formatCents(cents: Long): String =
        "%.2f".format(java.util.Locale("pt", "BR"), cents / 100.0)

    private fun parseDate(value: String): Long? {
        if (value.isBlank()) return null

        val normalized = value
            .replace('.', '/')
            .replace('-', '/')

        return runCatching {
            LocalDate.parse(
                normalized,
                DateTimeFormatter.ofPattern("d/M/uuuu")
            )
                .atStartOfDay(ZoneId.systemDefault())
                .toInstant()
                .toEpochMilli()
        }.getOrNull()
    }

    private fun formatDate(epoch: Long?): String {
        if (epoch == null) return ""

        return DateTimeFormatter.ofPattern("dd/MM/uuuu")
            .withZone(ZoneId.systemDefault())
            .format(Instant.ofEpochMilli(epoch))
    }
}
