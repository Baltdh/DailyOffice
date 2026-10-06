package com.dailyoffice.mei

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.dailyoffice.mei.data.AppDatabase
import com.dailyoffice.mei.data.ReceiptEntity
import com.dailyoffice.mei.data.RevenueEntity
import com.dailyoffice.mei.ocr.ReceiptOcr
import com.dailyoffice.mei.util.MeiSettings
import com.dailyoffice.mei.util.Money
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.Calendar

data class ReceiptDraft(
    val imageUri: String = "",
    val merchant: String = "",
    val cnpj: String = "",
    val date: String = "",
    val dueDate: String = "",
    val amount: String = "",
    val classification: String = "REVISAR",
    val paymentMethod: String = "REVISAR",
    val status: String = "PAGO",
    val notes: String = "",
    val rawText: String = ""
)

class DailyOfficeViewModel(application: Application) : AndroidViewModel(application) {
    private val dao = AppDatabase.get(application).dao()
    private val prefs = application.getSharedPreferences("dailyoffice_settings", 0)

    val receipts = dao.observeReceipts()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val revenues = dao.observeRevenues()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _draft = MutableStateFlow(ReceiptDraft())
    val draft = _draft.asStateFlow()

    private val _ocrBusy = MutableStateFlow(false)
    val ocrBusy = _ocrBusy.asStateFlow()

    private val _message = MutableStateFlow<String?>(null)
    val message = _message.asStateFlow()

    private val _settings = MutableStateFlow(loadSettings())
    val settings = _settings.asStateFlow()

    fun processImage(uri: Uri) {
        _ocrBusy.value = true
        _message.value = null
        viewModelScope.launch {
            runCatching { ReceiptOcr.read(getApplication(), uri) }
                .onSuccess { result ->
                    val raw = result.first
                    val parsed = result.second
                    _draft.value = ReceiptDraft(
                        imageUri = uri.toString(),
                        merchant = parsed.merchant,
                        cnpj = parsed.cnpj.orEmpty(),
                        date = parsed.date.orEmpty(),
                        amount = if (parsed.amountCents > 0) {
                            "%.2f".format(parsed.amountCents / 100.0).replace('.', ',')
                        } else "",
                        classification = parsed.classification,
                        paymentMethod = parsed.paymentMethod,
                        rawText = raw
                    )
                }
                .onFailure {
                    _draft.value = _draft.value.copy(imageUri = uri.toString())
                    _message.value = "Não consegui ler automaticamente. Você pode preencher manualmente."
                }
            _ocrBusy.value = false
        }
    }

    fun updateDraft(transform: (ReceiptDraft) -> ReceiptDraft) {
        _draft.value = transform(_draft.value)
    }

    fun clearDraft() {
        _draft.value = ReceiptDraft()
    }

    fun saveDraft() {
        val d = _draft.value
        if (d.imageUri.isBlank()) {
            _message.value = "Tire ou escolha uma foto primeiro."
            return
        }
        if (Money.parseToCents(d.amount) <= 0) {
            _message.value = "Confirme o valor do comprovante."
            return
        }

        viewModelScope.launch {
            dao.insertReceipt(
                ReceiptEntity(
                    imageUri = d.imageUri,
                    createdAt = System.currentTimeMillis(),
                    documentDate = d.date.ifBlank { null },
                    dueDate = d.dueDate.ifBlank { null },
                    merchant = d.merchant.ifBlank { "Fornecedor não identificado" },
                    cnpj = d.cnpj.ifBlank { null },
                    amountCents = Money.parseToCents(d.amount),
                    classification = d.classification,
                    paymentMethod = d.paymentMethod,
                    status = d.status,
                    notes = d.notes,
                    rawText = d.rawText
                )
            )
            _draft.value = ReceiptDraft()
            _message.value = "Comprovante arquivado."
        }
    }

    fun addRevenue(amount: String, source: String, notes: String) {
        val cents = Money.parseToCents(amount)
        if (cents <= 0) {
            _message.value = "Informe um valor de receita válido."
            return
        }

        viewModelScope.launch {
            dao.insertRevenue(
                RevenueEntity(
                    receivedAt = System.currentTimeMillis(),
                    amountCents = cents,
                    source = source.ifBlank { "Venda" },
                    notes = notes
                )
            )
            _message.value = "Receita adicionada."
        }
    }

    fun deleteReceipt(id: Long) {
        viewModelScope.launch { dao.deleteReceipt(id) }
    }

    fun updateSettings(cnpj: String, month: Int, year: Int, annualLimit: String) {
        val limit = Money.parseToCents(annualLimit).takeIf { it > 0 } ?: 8_100_000L
        val safeMonth = month.coerceIn(1, 12)
        val settings = MeiSettings(cnpj, safeMonth, year, limit)

        prefs.edit()
            .putString("cnpj", settings.cnpj)
            .putInt("startMonth", settings.startMonth)
            .putInt("startYear", settings.startYear)
            .putLong("annualLimitCents", settings.annualLimitCents)
            .apply()

        _settings.value = settings
        _message.value = "Configurações salvas."
    }

    fun consumeMessage() {
        _message.value = null
    }

    private fun loadSettings(): MeiSettings {
        val nowYear = Calendar.getInstance().get(Calendar.YEAR)
        return MeiSettings(
            cnpj = prefs.getString("cnpj", "").orEmpty(),
            startMonth = prefs.getInt("startMonth", 1),
            startYear = prefs.getInt("startYear", nowYear),
            annualLimitCents = prefs.getLong("annualLimitCents", 8_100_000L)
        )
    }
}
