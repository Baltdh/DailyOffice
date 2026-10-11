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
import com.dailyoffice.mei.document.AccountingArchive
import com.dailyoffice.mei.export.CsvExporter
import com.dailyoffice.mei.finance.AccountFlow
import com.dailyoffice.mei.finance.FinanceCalculator
import com.dailyoffice.mei.finance.MeiCalculator
import com.dailyoffice.mei.finance.MeiConfig
import com.dailyoffice.mei.finance.MeiProjection
import com.dailyoffice.mei.finance.MeiSettings
import com.dailyoffice.mei.inventory.InventoryBalance
import com.dailyoffice.mei.inventory.InventoryCalculator
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
import java.util.UUID

data class ReceiptItemDraft(
    val description: String,
    val amountCents: Long,
    val ownership: Ownership = Ownership.REVIEW,
    val confidence: Float = 0f,
    val lineIndex: Int = 0,
    val stockProductId: Long? = null,
    val stockQuantity: String = "",
    val addToStock: Boolean = false,
    val unitHint: String? = null
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
    val accountId: Long? = null,
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
    val ownerPaidBusinessExpensesCents: Long = 0,
    val ownerReimbursedCents: Long = 0,
    val ownerReimbursementOutstandingCents: Long = 0,
    val reviewCount: Int = 0,
    val receiptCount: Int = 0
)

class ReceiptViewModel(application: Application) : AndroidViewModel(application) {
    private val db = DailyOfficeDb.get(application)
    private val accountingDocumentDao = db.accountingDocumentDao()
    private val companyDao = db.companyDao()
    private val accountDao = db.accountDao()
    private val inventoryProductDao = db.inventoryProductDao()
    private val stockMovementDao = db.stockMovementDao()
    private val dao = db.receiptDao()
    private val itemDao = db.receiptItemDao()
    private val transactionDao = db.transactionDao()
    private val meiSettings = MeiSettings(application)
    private val companyPrefs = application.getSharedPreferences(
        "company_settings",
        android.content.Context.MODE_PRIVATE
    )

    private val _activeCompanyId = MutableStateFlow(
        companyPrefs.getLong("activeCompanyId", 1L)
    )
    val activeCompanyId: StateFlow<Long> = _activeCompanyId.asStateFlow()

    val companies = companyDao.observeActive()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val activeCompany: StateFlow<Company?> = combine(
        companies,
        _activeCompanyId
    ) { companyList, activeId ->
        companyList.firstOrNull { it.id == activeId }
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        null
    )

    val accountingDocuments: StateFlow<List<AccountingDocument>> = _activeCompanyId
        .flatMapLatest { accountingDocumentDao.observeByCompany(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun importAccountingDocument(
        uri: Uri,
        type: AccountingDocumentType,
        onSaved: () -> Unit,
        onError: (String) -> Unit
    ) {
        val companyId = _activeCompanyId.value
        viewModelScope.launch(Dispatchers.IO) {
            var archived: AccountingDocument? = null
            try {
                archived = AccountingArchive.import(getApplication(), uri, companyId, type)
                val duplicate = accountingDocumentDao.findDuplicate(companyId, archived.sha256)
                if (duplicate != null) {
                    java.io.File(archived.localPath).delete()
                    archived = null
                    throw IllegalArgumentException("Arquivo já cadastrado nesta empresa: ${duplicate.displayName}")
                }
                accountingDocumentDao.insert(archived)
                withContext(Dispatchers.Main) { onSaved() }
            } catch (e: Exception) {
                archived?.let { java.io.File(it.localPath).delete() }
                withContext(Dispatchers.Main) { onError(e.message ?: "Falha ao importar documento.") }
            }
        }
    }

    fun deleteAccountingDocument(
        document: AccountingDocument,
        onDone: () -> Unit,
        onError: (String) -> Unit
    ) {
        val companyId = _activeCompanyId.value
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val stored = accountingDocumentDao.byId(document.id, companyId)
                    ?: error("Documento não pertence à empresa ativa.")
                accountingDocumentDao.delete(stored.id, companyId)
                java.io.File(stored.localPath).delete()
                withContext(Dispatchers.Main) { onDone() }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) { onError(e.message ?: "Falha ao excluir.") }
            }
        }
    }

    val accounts: StateFlow<List<Account>> = _activeCompanyId
        .flatMapLatest { companyId ->
            accountDao.observeByCompany(companyId)
        }
        .stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5_000),
            emptyList()
        )

    private val allReceipts = dao.observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val receipts = combine(allReceipts, _activeCompanyId) { receiptList, activeId ->
        receiptList.filter { it.companyId == activeId }
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        emptyList()
    )

    private val allTransactions = transactionDao.observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val transactions = combine(
        allTransactions,
        _activeCompanyId
    ) { transactionList, activeId ->
        transactionList.filter { it.companyId == activeId }
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        emptyList()
    )

    val accountFlows: StateFlow<List<AccountFlow>> = transactions
        .map { FinanceCalculator.accountFlows(it) }
        .stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5_000),
            emptyList()
        )

    private val _meiConfig = MutableStateFlow(
        meiSettings.load(_activeCompanyId.value)
    )
    val meiConfig: StateFlow<MeiConfig> = _meiConfig.asStateFlow()

    val inventoryProducts = inventoryProductDao.observeActive()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val stockMovements = stockMovementDao.observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val inventoryBalances: StateFlow<List<InventoryBalance>> = combine(
        inventoryProducts,
        stockMovements,
        _activeCompanyId
    ) { products, movements, companyId ->
        products.map { product ->
            InventoryCalculator.balanceFor(
                product = product,
                movements = movements,
                companyId = companyId
            )
        }
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        emptyList()
    )

    val summary = combine(receipts, transactions, accounts) { receiptList, txList, accountList ->
        val totals = FinanceCalculator.summarize(txList)
        val ownerPaid = FinanceCalculator.ownerPaidBusinessExpenses(
            transactions = txList,
            accounts = accountList
        )
        val ownerReimbursed = FinanceCalculator.ownerReimbursedCents(
            transactions = txList,
            accounts = accountList
        )
        val ownerOutstanding = FinanceCalculator.ownerReimbursementOutstandingCents(
            transactions = txList,
            accounts = accountList
        )

        DashboardSummary(
            businessExpensesCents = totals.businessExpensesCents,
            personalExpensesCents = totals.personalExpensesCents,
            revenueCents = totals.revenueCents,
            contributionCents = totals.contributionCents,
            withdrawalCents = totals.withdrawalCents,
            pendingCents = totals.pendingCents,
            receivableCents = totals.receivableCents,
            ownerPaidBusinessExpensesCents = ownerPaid,
            ownerReimbursedCents = ownerReimbursed,
            ownerReimbursementOutstandingCents = ownerOutstanding,
            reviewCount = receiptList.count { it.ownership == Ownership.REVIEW },
            receiptCount = receiptList.size
        )
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        DashboardSummary()
    )

    val meiProjection: StateFlow<MeiProjection> = combine(transactions, meiConfig, activeCompany) { txList, config, company ->
        val revenue = FinanceCalculator.revenueForYear(
            transactions = txList,
            year = config.taxYear
        )

        MeiCalculator.calculate(
            revenueCents = revenue,
            annualLimitCents = com.dailyoffice.mei.finance.CompanyCeiling.annualLimit(company?.companyType ?: CompanyType.MEI, config.annualLimitCents),
            openingMonth = config.openingMonth,
            openingYear = config.openingYear,
            taxYear = config.taxYear
        )
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        MeiCalculator.calculate(
            revenueCents = 0,
            annualLimitCents = 8_100_000L,
            openingMonth = LocalDate.now().monthValue,
            openingYear = LocalDate.now().year,
            taxYear = LocalDate.now().year
        )
    )

    init {
        viewModelScope.launch(Dispatchers.IO) {
            if (companyDao.count() == 0) {
                companyDao.insert(
                    Company(
                        id = 1,
                        name = "Empresa principal"
                    )
                )
            }

            companyDao.all().forEach { company ->
                seedDefaultAccounts(company.id)
            }

            val selected = companyDao.byId(_activeCompanyId.value)
                ?: companyDao.byId(1L)

            if (selected != null && selected.id != _activeCompanyId.value) {
                _activeCompanyId.value = selected.id
                companyPrefs.edit()
                    .putLong("activeCompanyId", selected.id)
                    .apply()
                _meiConfig.value = meiSettings.load(selected.id)
            }

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
    private var editingCompanyId: Long? = null

    val isEditing: Boolean
        get() = editingReceiptId != null

    fun beginReview(imageUri: String, rawText: String, imageSha256: String = "") {
        editingReceiptId = null
        editingCreatedAt = null
        editingCompanyId = null

        val parsed = ReceiptParser.parse(rawText)
        val overallSuggestion = ClassificationEngine.suggest(rawText, parsed.supplier)
        val amount = parsed.totalCents?.let(::formatCents).orEmpty()

        val itemDrafts = parsed.items.map { item ->
            val suggestion = ClassificationEngine.suggestItem(item.description)
            val inventoryMatch = inventoryProducts.value.firstOrNull { product ->
                item.description.contains(product.name, ignoreCase = true) ||
                    product.name.contains(item.description, ignoreCase = true)
            }

            ReceiptItemDraft(
                description = item.description,
                amountCents = item.amountCents,
                ownership = suggestion.ownership,
                confidence = item.confidence,
                lineIndex = item.lineIndex,
                stockProductId = inventoryMatch?.id,
                stockQuantity = item.quantityMilli?.let(::formatQuantityMilli).orEmpty(),
                unitHint = item.unitHint
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
        editingCompanyId = receipt.companyId

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
            accountId = receipt.accountId,
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
                    lineIndex = item.lineIndex,
                    stockProductId = item.stockProductId,
                    stockQuantity = if (item.stockQuantityMilli > 0) {
                        formatQuantityMilli(item.stockQuantityMilli)
                    } else "",
                    addToStock = item.addToStock
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
        updated[index] = updated[index].copy(
            ownership = ownership,
            addToStock = if (ownership == Ownership.BUSINESS) {
                updated[index].addToStock
            } else {
                false
            }
        )
        draft = draft.copy(items = updated)
    }

    fun updateItemStockProduct(index: Int, productId: Long?) {
        if (index !in draft.items.indices) return

        val updated = draft.items.toMutableList()
        updated[index] = updated[index].copy(stockProductId = productId)
        draft = draft.copy(items = updated)
    }

    fun updateItemStockQuantity(index: Int, quantity: String) {
        if (index !in draft.items.indices) return

        val updated = draft.items.toMutableList()
        updated[index] = updated[index].copy(stockQuantity = quantity)
        draft = draft.copy(items = updated)
    }

    fun updateItemAddToStock(index: Int, enabled: Boolean) {
        if (index !in draft.items.indices) return

        val updated = draft.items.toMutableList()
        updated[index] = updated[index].copy(addToStock = enabled)
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

    fun updateAccount(accountId: Long?) {
        draft = draft.copy(accountId = accountId)
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

        if (
            draft.paymentStatus == PaymentStatus.PAID &&
            draft.accountId == null
        ) {
            return onError("Selecione de onde saiu o dinheiro desta compra.")
        }

        val stockDrafts = draft.items.filter { it.addToStock }
        stockDrafts.forEach { item ->
            if (item.ownership != Ownership.BUSINESS) {
                return onError(
                    "Somente itens classificados como Empresa podem entrar no estoque."
                )
            }
            if (item.stockProductId == null) {
                return onError(
                    "Selecione o produto de estoque para “${item.description}”."
                )
            }
            val quantity = parseQuantityMilli(item.stockQuantity)
            if (quantity == null || quantity <= 0) {
                return onError(
                    "Informe uma quantidade válida para “${item.description}”."
                )
            }
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
                        companyId = editingCompanyId ?: _activeCompanyId.value,
                        accountId = draft.accountId,
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
                                    lineIndex = item.lineIndex,
                                    stockProductId = item.stockProductId,
                                    stockQuantityMilli = parseQuantityMilli(
                                        item.stockQuantity
                                    ) ?: 0,
                                    addToStock = item.addToStock
                                )
                            }
                        )
                    }

                    transactionDao.deleteByReceiptId(receiptId)
                    stockMovementDao.deleteReceiptPurchases(receiptId)
                    val txDate = receipt.issuedAt ?: receipt.createdAt

                    if (receipt.paymentStatus != PaymentStatus.CANCELLED) {
                        draft.items
                            .filter { it.addToStock }
                            .forEach { item ->
                                val productId = item.stockProductId
                                    ?: error("Produto de estoque não selecionado.")
                                val quantityMilli = parseQuantityMilli(
                                    item.stockQuantity
                                ) ?: error("Quantidade de estoque inválida.")

                                stockMovementDao.insert(
                                    StockMovement(
                                        productId = productId,
                                        companyId = receipt.companyId,
                                        type = StockMovementType.PURCHASE,
                                        quantityMilli = quantityMilli,
                                        totalCostCents = item.amountCents,
                                        note = buildString {
                                            append("Compra via comprovante")
                                            if (receipt.supplier.isNotBlank()) {
                                                append(" • ")
                                                append(receipt.supplier)
                                            }
                                            if (receipt.documentNumber.isNotBlank()) {
                                                append(" • doc ")
                                                append(receipt.documentNumber)
                                            }
                                        },
                                        receiptId = receiptId,
                                        createdAt = txDate
                                    )
                                )
                            }
                    }

                    if (receipt.paymentStatus != PaymentStatus.CANCELLED && business > 0) {
                        transactionDao.insert(
                            Transaction(
                                companyId = receipt.companyId,
                                accountId = receipt.accountId,
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

                    if (receipt.paymentStatus != PaymentStatus.CANCELLED && personal > 0) {
                        transactionDao.insert(
                            Transaction(
                                companyId = receipt.companyId,
                                accountId = receipt.accountId,
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
                    editingCompanyId = null
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
                    stockMovementDao.deleteReceiptPurchases(receipt.id)
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
        accountId: Long?,
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

        if (paymentStatus == PaymentStatus.PAID && accountId == null) {
            return onError("Selecione a conta/origem do dinheiro.")
        }

        if (
            kind == EntryKind.REIMBURSEMENT ||
            kind == EntryKind.TRANSFER
        ) {
            return onError("Use a transferência entre contas para esse tipo de lançamento.")
        }

        val effectiveOwnership = when (kind) {
            EntryKind.REVENUE,
            EntryKind.CONTRIBUTION,
            EntryKind.WITHDRAWAL -> Ownership.BUSINESS
            EntryKind.EXPENSE -> ownership
            EntryKind.REIMBURSEMENT,
            EntryKind.TRANSFER -> Ownership.BUSINESS
        }

        val createdAt = parseDate(date) ?: System.currentTimeMillis()
        val dueAt = parseDate(dueDate)

        viewModelScope.launch(Dispatchers.IO) {
            runCatching {
                transactionDao.insert(
                    Transaction(
                        companyId = _activeCompanyId.value,
                        accountId = accountId,
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

    fun addAccountTransfer(
        amount: String,
        sourceAccountId: Long?,
        destinationAccountId: Long?,
        paymentMethod: PaymentMethod,
        date: String,
        reimbursement: Boolean,
        note: String,
        onSaved: () -> Unit,
        onError: (String) -> Unit
    ) {
        val cents = parseCents(amount)
            ?: return onError("Informe um valor válido.")

        if (cents <= 0) {
            return onError("O valor precisa ser maior que zero.")
        }

        val sourceId = sourceAccountId
            ?: return onError("Selecione a conta de origem.")
        val destinationId = destinationAccountId
            ?: return onError("Selecione a conta de destino.")

        if (sourceId == destinationId) {
            return onError("Origem e destino precisam ser contas diferentes.")
        }

        val source = accounts.value.firstOrNull { it.id == sourceId }
            ?: return onError("Conta de origem não encontrada.")
        val destination = accounts.value.firstOrNull { it.id == destinationId }
            ?: return onError("Conta de destino não encontrada.")

        if (
            source.companyId != _activeCompanyId.value ||
            destination.companyId != _activeCompanyId.value
        ) {
            return onError("As duas contas precisam pertencer à empresa ativa.")
        }

        val kind = if (reimbursement) {
            val sourceIsBusiness =
                source.kind == AccountKind.BUSINESS_BANK ||
                    source.kind == AccountKind.BUSINESS_CASH
            val destinationIsOwner =
                destination.kind == AccountKind.OWNER_PERSONAL_BANK ||
                    destination.kind == AccountKind.OWNER_PERSONAL_CARD

            if (!sourceIsBusiness) {
                return onError(
                    "O reembolso deve sair de uma conta bancária ou caixa da empresa."
                )
            }
            if (!destinationIsOwner) {
                return onError(
                    "O destino do reembolso deve ser uma conta pessoal do titular."
                )
            }

            val outstanding = FinanceCalculator.ownerReimbursementOutstandingCents(
                transactions = transactions.value,
                accounts = accounts.value
            )
            if (outstanding <= 0) {
                return onError("Não há valor pendente de reembolso ao titular.")
            }
            if (cents > outstanding) {
                return onError(
                    "O reembolso é maior que o saldo pendente de R$ ${formatCents(outstanding)}."
                )
            }

            EntryKind.REIMBURSEMENT
        } else {
            EntryKind.TRANSFER
        }

        val createdAt = parseDate(date) ?: System.currentTimeMillis()
        val description = note.trim().ifBlank {
            if (reimbursement) {
                "Reembolso ao titular"
            } else {
                "Transferência entre contas"
            }
        }

        viewModelScope.launch(Dispatchers.IO) {
            runCatching {
                transactionDao.insert(
                    Transaction(
                        companyId = _activeCompanyId.value,
                        accountId = source.id,
                        counterpartyAccountId = destination.id,
                        description = description,
                        amountCents = cents,
                        ownership = Ownership.BUSINESS,
                        kind = kind,
                        paymentMethod = paymentMethod,
                        paymentStatus = PaymentStatus.PAID,
                        paidAt = createdAt,
                        createdAt = createdAt
                    )
                )
            }.onSuccess {
                withContext(Dispatchers.Main) { onSaved() }
            }.onFailure {
                withContext(Dispatchers.Main) {
                    onError(it.message ?: "Não foi possível registrar a transferência.")
                }
            }
        }
    }

    fun settleEntry(
        transactionId: Long,
        accountId: Long?,
        date: String,
        onSaved: () -> Unit,
        onError: (String) -> Unit
    ) {
        val companyId = _activeCompanyId.value
        if (accountId == null) return onError("Selecione a conta do pagamento ou recebimento.")
        val paidAt = runCatching {
            LocalDate.parse(date.trim(), DateTimeFormatter.ofPattern("d/M/uuuu")
                .withResolverStyle(java.time.format.ResolverStyle.STRICT))
                .atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
        }.getOrNull() ?: return onError("Informe uma data válida no formato dd/mm/aaaa.")
        viewModelScope.launch(Dispatchers.IO) {
            runCatching {
                db.withTransaction {
                    val account = accountDao.byId(accountId)
                    require(account != null && account.active && account.companyId == companyId) {
                        "A conta precisa pertencer à empresa selecionada."
                    }
                    val transaction = transactionDao.byId(transactionId, companyId)
                        ?: error("Lançamento não encontrado nesta empresa.")
                    require(transaction.paymentStatus == PaymentStatus.PENDING ||
                        transaction.paymentStatus == PaymentStatus.OVERDUE) {
                        "Este lançamento já foi baixado ou cancelado."
                    }
                    val linkedId = transaction.receiptId
                    val entries = if (linkedId == null) listOf(transaction) else {
                        val receipt = dao.byId(linkedId)
                            ?: error("Comprovante não encontrado.")
                        require(receipt.companyId == companyId) { "Comprovante de outra empresa." }
                        require(receipt.paymentStatus == PaymentStatus.PENDING ||
                            receipt.paymentStatus == PaymentStatus.OVERDUE) {
                            "O comprovante já foi baixado ou cancelado."
                        }
                        dao.update(receipt.copy(
                            paymentStatus = PaymentStatus.PAID, accountId = accountId
                        ))
                        transactionDao.byReceiptId(linkedId, companyId)
                    }
                    entries.forEach { entry ->
                        require(entry.paymentStatus == PaymentStatus.PENDING ||
                            entry.paymentStatus == PaymentStatus.OVERDUE) {
                            "Há uma parcela já baixada neste comprovante."
                        }
                        transactionDao.update(entry.copy(
                            paymentStatus = PaymentStatus.PAID,
                            accountId = accountId,
                            paidAt = paidAt
                        ))
                    }
                }
            }.onSuccess {
                withContext(Dispatchers.Main) { onSaved() }
            }.onFailure {
                withContext(Dispatchers.Main) {
                    onError(it.message ?: "Não foi possível registrar a baixa.")
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
        openingDate: String,
        taxYear: String,
        onError: (String) -> Unit = {}
    ) {
        val limit = parseCents(annualLimit)
            ?: return onError("Informe um teto anual válido.")

        val opening = parseLocalDate(openingDate)
            ?: return onError("Informe a data de abertura no formato dd/mm/aaaa.")

        val year = taxYear.toIntOrNull()
            ?: return onError("Informe um ano fiscal válido.")

        if (year !in 2000..2100) {
            return onError("O ano fiscal deve estar entre 2000 e 2100.")
        }

        if (year < opening.year) {
            return onError(
                "O ano fiscal não pode ser anterior ao ano de abertura da empresa."
            )
        }

        _meiConfig.value = meiSettings.update(
            companyId = _activeCompanyId.value,
            config = MeiConfig(
                annualLimitCents = limit,
                openingDay = opening.dayOfMonth,
                openingMonth = opening.monthValue,
                openingYear = opening.year,
                taxYear = year
            )
        )
    }

    fun addInventoryProduct(
        name: String,
        unit: StockUnit,
        onSaved: () -> Unit = {},
        onError: (String) -> Unit = {}
    ) {
        val cleanName = name.trim()
        if (cleanName.isBlank()) {
            onError("Informe o nome do produto.")
            return
        }

        if (inventoryProducts.value.any {
                it.name.equals(cleanName, ignoreCase = true)
            }) {
            onError("Já existe um produto com esse nome.")
            return
        }

        viewModelScope.launch(Dispatchers.IO) {
            runCatching {
                inventoryProductDao.insert(
                    InventoryProduct(
                        name = cleanName,
                        unit = unit
                    )
                )
            }.onSuccess {
                withContext(Dispatchers.Main) { onSaved() }
            }.onFailure {
                withContext(Dispatchers.Main) {
                    onError(it.message ?: "Não foi possível cadastrar o produto.")
                }
            }
        }
    }

    fun addStockMovement(
        productId: Long,
        type: StockMovementType,
        quantity: String,
        totalCost: String,
        note: String,
        onSaved: () -> Unit = {},
        onError: (String) -> Unit = {}
    ) {
        if (
            type == StockMovementType.TRANSFER_IN ||
            type == StockMovementType.TRANSFER_OUT
        ) {
            onError("Use a função de transferência entre empresas.")
            return
        }

        val quantityMilli = parseQuantityMilli(quantity)
            ?: return onError("Informe uma quantidade válida.")

        if (quantityMilli <= 0) {
            onError("A quantidade precisa ser maior que zero.")
            return
        }

        val costCents = if (totalCost.isBlank()) {
            null
        } else {
            parseCents(totalCost)
                ?: return onError("Informe um custo total válido.")
        }

        val companyId = _activeCompanyId.value

        viewModelScope.launch(Dispatchers.IO) {
            runCatching {
                if (type.isStockOut()) {
                    val current = stockMovementDao.balanceFor(productId, companyId)
                    if (quantityMilli > current) {
                        error("Estoque insuficiente para essa saída.")
                    }
                }

                stockMovementDao.insert(
                    StockMovement(
                        productId = productId,
                        companyId = companyId,
                        type = type,
                        quantityMilli = quantityMilli,
                        totalCostCents = costCents,
                        note = note.trim()
                    )
                )
            }.onSuccess {
                withContext(Dispatchers.Main) { onSaved() }
            }.onFailure {
                withContext(Dispatchers.Main) {
                    onError(it.message ?: "Não foi possível registrar o estoque.")
                }
            }
        }
    }

    fun transferStock(
        productId: Long,
        targetCompanyId: Long,
        quantity: String,
        note: String,
        onSaved: () -> Unit = {},
        onError: (String) -> Unit = {}
    ) {
        val sourceCompanyId = _activeCompanyId.value

        if (targetCompanyId == sourceCompanyId) {
            onError("Selecione outra empresa para a transferência.")
            return
        }

        if (companies.value.none { it.id == targetCompanyId }) {
            onError("Empresa de destino não encontrada.")
            return
        }

        val quantityMilli = parseQuantityMilli(quantity)
            ?: return onError("Informe uma quantidade válida.")

        if (quantityMilli <= 0) {
            onError("A quantidade precisa ser maior que zero.")
            return
        }

        viewModelScope.launch(Dispatchers.IO) {
            runCatching {
                val current = stockMovementDao.balanceFor(
                    productId,
                    sourceCompanyId
                )
                if (quantityMilli > current) {
                    error("Estoque insuficiente para transferir.")
                }

                val groupId = UUID.randomUUID().toString()
                val now = System.currentTimeMillis()

                db.withTransaction {
                    stockMovementDao.insert(
                        StockMovement(
                            productId = productId,
                            companyId = sourceCompanyId,
                            counterpartyCompanyId = targetCompanyId,
                            type = StockMovementType.TRANSFER_OUT,
                            quantityMilli = quantityMilli,
                            note = note.trim(),
                            transferGroupId = groupId,
                            createdAt = now
                        )
                    )
                    stockMovementDao.insert(
                        StockMovement(
                            productId = productId,
                            companyId = targetCompanyId,
                            counterpartyCompanyId = sourceCompanyId,
                            type = StockMovementType.TRANSFER_IN,
                            quantityMilli = quantityMilli,
                            note = note.trim(),
                            transferGroupId = groupId,
                            createdAt = now
                        )
                    )
                }
            }.onSuccess {
                withContext(Dispatchers.Main) { onSaved() }
            }.onFailure {
                withContext(Dispatchers.Main) {
                    onError(it.message ?: "Não foi possível transferir o estoque.")
                }
            }
        }
    }

    fun selectCompany(companyId: Long) {
        if (companyId == _activeCompanyId.value) return
        if (companies.value.none { it.id == companyId }) return

        _activeCompanyId.value = companyId
        companyPrefs.edit()
            .putLong("activeCompanyId", companyId)
            .apply()
        _meiConfig.value = meiSettings.load(companyId)

        draft = ReceiptDraft()
        editingReceiptId = null
        editingCreatedAt = null
        editingCompanyId = null
    }

    fun createCompany(
        name: String,
        cnpj: String,
        ownerName: String,
        companyType: CompanyType,
        taxRegime: TaxRegime,
        onSaved: () -> Unit = {},
        onError: (String) -> Unit = {}
    ) {
        val cleanName = name.trim()
        if (cleanName.isBlank()) {
            onError("Informe o nome da empresa.")
            return
        }

        val cleanCnpj = cnpj.filter(Char::isDigit)
        if (cleanCnpj.isNotEmpty() && cleanCnpj.length != 14) {
            onError("O CNPJ deve ter 14 dígitos.")
            return
        }

        viewModelScope.launch(Dispatchers.IO) {
            runCatching {
                val normalizedTaxRegime = CompanyProfileRules.normalizedTaxRegime(
                    companyType = companyType,
                    taxRegime = taxRegime
                )
                val id = companyDao.insert(
                    Company(
                        name = cleanName,
                        cnpj = cleanCnpj,
                        ownerName = ownerName.trim(),
                        companyType = companyType,
                        taxRegime = normalizedTaxRegime
                    )
                )
                seedDefaultAccounts(id)
                id
            }.onSuccess { id ->
                withContext(Dispatchers.Main) {
                    _activeCompanyId.value = id
                    companyPrefs.edit()
                        .putLong("activeCompanyId", id)
                        .apply()
                    _meiConfig.value = meiSettings.load(id)
                    onSaved()
                }
            }.onFailure {
                withContext(Dispatchers.Main) {
                    onError(it.message ?: "Não foi possível cadastrar a empresa.")
                }
            }
        }
    }

    fun addAccount(
        name: String,
        kind: AccountKind,
        onSaved: () -> Unit = {},
        onError: (String) -> Unit = {}
    ) {
        val cleanName = name.trim()
        if (cleanName.isBlank()) {
            onError("Informe o nome da conta.")
            return
        }

        if (accounts.value.any { it.name.equals(cleanName, ignoreCase = true) }) {
            onError("Já existe uma conta com esse nome.")
            return
        }

        val companyId = _activeCompanyId.value
        viewModelScope.launch(Dispatchers.IO) {
            runCatching {
                accountDao.insert(
                    Account(
                        companyId = companyId,
                        name = cleanName,
                        kind = kind
                    )
                )
            }.onSuccess {
                withContext(Dispatchers.Main) { onSaved() }
            }.onFailure {
                withContext(Dispatchers.Main) {
                    onError(it.message ?: "Não foi possível cadastrar a conta.")
                }
            }
        }
    }

    fun updateCompany(
        company: Company,
        name: String,
        cnpj: String,
        ownerName: String,
        companyType: CompanyType,
        taxRegime: TaxRegime,
        onSaved: () -> Unit = {},
        onError: (String) -> Unit = {}
    ) {
        val cleanName = name.trim()
        if (cleanName.isBlank()) {
            onError("Informe o nome da empresa.")
            return
        }

        val cleanCnpj = cnpj.filter(Char::isDigit)
        if (cleanCnpj.isNotEmpty() && cleanCnpj.length != 14) {
            onError("O CNPJ deve ter 14 dígitos.")
            return
        }

        viewModelScope.launch(Dispatchers.IO) {
            runCatching {
                val normalizedTaxRegime = CompanyProfileRules.normalizedTaxRegime(
                    companyType = companyType,
                    taxRegime = taxRegime
                )
                companyDao.update(
                    company.copy(
                        name = cleanName,
                        cnpj = cleanCnpj,
                        ownerName = ownerName.trim(),
                        companyType = companyType,
                        taxRegime = normalizedTaxRegime
                    )
                )
            }.onSuccess {
                withContext(Dispatchers.Main) { onSaved() }
            }.onFailure {
                withContext(Dispatchers.Main) {
                    onError(it.message ?: "Não foi possível atualizar a empresa.")
                }
            }
        }
    }

    fun exportCsv(
        onReady: (Uri) -> Unit,
        onError: (String) -> Unit
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            runCatching {
                CsvExporter.export(
                    getApplication(),
                    activeCompany.value,
                    receipts.value,
                    transactions.value,
                    accounts.value
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

    private suspend fun seedDefaultAccounts(companyId: Long) {
        if (accountDao.countForCompany(companyId) > 0) return

        val defaults = listOf(
            "Conta PJ" to AccountKind.BUSINESS_BANK,
            "Dinheiro da empresa" to AccountKind.BUSINESS_CASH,
            "Cartão da empresa" to AccountKind.BUSINESS_CARD,
            "Conta pessoal do titular" to AccountKind.OWNER_PERSONAL_BANK,
            "Cartão pessoal do titular" to AccountKind.OWNER_PERSONAL_CARD,
            "iFood a receber" to AccountKind.IFOOD_RECEIVABLE
        )

        defaults.forEach { (name, kind) ->
            accountDao.insert(
                Account(
                    companyId = companyId,
                    name = name,
                    kind = kind
                )
            )
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

    private fun formatQuantityMilli(quantityMilli: Long): String =
        BigDecimal(quantityMilli)
            .divide(BigDecimal(1000))
            .stripTrailingZeros()
            .toPlainString()
            .replace('.', ',')

    private fun parseQuantityMilli(value: String): Long? = runCatching {
        val clean = value.trim()
        val normalized = if (clean.contains(',')) {
            clean.replace(".", "").replace(",", ".")
        } else {
            clean
        }

        BigDecimal(normalized)
            .movePointRight(3)
            .setScale(0, RoundingMode.HALF_UP)
            .longValueExact()
    }.getOrNull()

    private fun StockMovementType.isStockOut(): Boolean =
        this == StockMovementType.CONSUMPTION ||
            this == StockMovementType.LOSS ||
            this == StockMovementType.ADJUSTMENT_OUT

    private fun labelFor(kind: EntryKind): String = when (kind) {
        EntryKind.EXPENSE -> "Despesa"
        EntryKind.REVENUE -> "Receita"
        EntryKind.CONTRIBUTION -> "Aporte"
        EntryKind.WITHDRAWAL -> "Retirada"
        EntryKind.REIMBURSEMENT -> "Reembolso ao titular"
        EntryKind.TRANSFER -> "Transferência"
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

    private fun parseLocalDate(value: String): LocalDate? {
        if (value.isBlank()) return null

        val normalized = value
            .replace('.', '/')
            .replace('-', '/')

        return runCatching {
            LocalDate.parse(
                normalized,
                DateTimeFormatter.ofPattern("d/M/uuuu")
            )
        }.getOrNull()
    }

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
