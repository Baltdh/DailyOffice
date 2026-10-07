package com.dailyoffice.mei.receipt

data class ParsedLineItem(
    val description: String,
    val amountCents: Long,
    val lineIndex: Int,
    val confidence: Float,
    val quantityMilli: Long? = null,
    val unitHint: String? = null
)

data class ParsedReceipt(
    val supplier: String?,
    val totalCents: Long?,
    val date: String?,
    val document: String?,
    val confidence: Float,
    val warnings: List<String>,
    val items: List<ParsedLineItem>
)

object ReceiptParser {
    private val money = Regex("""(?:R\$\s*)?(\d{1,3}(?:\.\d{3})*|\d+)[,.](\d{2})""")
    private val date = Regex("""\b([0-3]?\d[/.-][01]?\d[/.-]20\d{2})\b""")
    private val document = Regex("""(?i)(?:NFC-?E|NFCE|CUPOM|NOTA|COO)\D{0,20}(\d{3,})""")
    private val totalKeywords = listOf("valor total", "total a pagar", "a pagar", "total")
    private val quantityWithUnit = Regex(
        """(?i)\b(\d+(?:[,.]\d{1,3})?)\s*(kg|g|ml|l|un|und|unid|pc|pct)\b"""
    )
    private val quantityTimesPrice = Regex(
        """(?i)\b(\d+(?:[,.]\d{1,3})?)\s*[xX]\s*(?:R\$\s*)?\d"""
    )
    private val nonItemTerms = listOf(
        "valor total", "total a pagar", "subtotal", "desconto", "acréscimo", "acrescimo",
        "troco", "forma de pagamento", "pagamento", "dinheiro", "pix", "cartão",
        "cartao", "crédito", "credito", "débito", "debito", "tribut", "imposto",
        "cnpj", "cpf", "nfce", "nfc-e", "chave de acesso", "protocolo", "consumidor",
        "operador", "caixa", "qtd total", "qtd. total", "quantidade total", "valor pago"
    )

    fun parse(text: String): ParsedReceipt {
        val lines = text.lines().map { it.trim() }.filter { it.isNotEmpty() }
        val supplier = lines.firstOrNull { line ->
            line.length >= 3 &&
                !line.contains("documento auxiliar", ignoreCase = true) &&
                !line.contains("cnpj", ignoreCase = true)
        }

        val preferred = lines.firstNotNullOfOrNull { line ->
            if (totalKeywords.any { line.contains(it, ignoreCase = true) }) {
                money.findAll(line).lastOrNull()?.let(::toCents)
            } else null
        }

        val fallback = money.findAll(text)
            .mapNotNull { runCatching { toCents(it) }.getOrNull() }
            .maxOrNull()

        val total = preferred ?: fallback
        val items = parseItems(lines)

        val warnings = buildList {
            if (preferred == null && total != null) {
                add("Total estimado pelo maior valor encontrado; confirme antes de salvar.")
            }
            if (total == null) add("Não foi possível identificar o valor total.")
            if (supplier == null) add("Fornecedor não identificado.")
            if (items.isEmpty()) {
                add("Nenhum item individual foi identificado com segurança.")
            }
        }

        return ParsedReceipt(
            supplier = supplier,
            totalCents = total,
            date = date.find(text)?.groupValues?.get(1)?.replace('.', '/'),
            document = document.find(text)?.groupValues?.get(1),
            confidence = when {
                preferred != null && supplier != null -> 0.90f
                total != null -> 0.60f
                else -> 0.25f
            },
            warnings = warnings,
            items = items
        )
    }

    private fun parseItems(lines: List<String>): List<ParsedLineItem> {
        val result = mutableListOf<ParsedLineItem>()

        lines.forEachIndexed { index, line ->
            val lower = line.lowercase()
            if (nonItemTerms.any(lower::contains)) return@forEachIndexed

            val matches = money.findAll(line).toList()
            if (matches.isEmpty()) return@forEachIndexed

            val amount = runCatching { toCents(matches.last()) }.getOrNull()
                ?: return@forEachIndexed
            if (amount <= 0) return@forEachIndexed

            val firstMoney = matches.first()
            var description = line.substring(0, firstMoney.range.first)
                .replace(Regex("""^\s*\d{1,6}\s*[-.)]?\s*"""), "")
                .replace(Regex("""(?i)\bqtd\.?\s*\d+[.,]?\d*\s*[xX]?\s*$"""), "")
                .trim(' ', '-', ':', ';', '|')

            if (!looksLikeItemDescription(description)) {
                val previous = lines.getOrNull(index - 1).orEmpty()
                if (looksLikeItemDescription(previous)) {
                    description = previous.trim()
                }
            }

            if (!looksLikeItemDescription(description)) return@forEachIndexed

            val quantity = extractQuantity(line)

            result += ParsedLineItem(
                description = description.take(120),
                amountCents = amount,
                lineIndex = index,
                confidence = if (matches.size == 1) 0.80f else 0.65f,
                quantityMilli = quantity?.first,
                unitHint = quantity?.second
            )
        }

        return result
            .distinctBy { Triple(it.lineIndex, it.description.lowercase(), it.amountCents) }
            .take(50)
    }

    private fun extractQuantity(line: String): Pair<Long, String?>? {
        val withUnit = quantityWithUnit.find(line)
        if (withUnit != null) {
            val quantity = decimalToMilli(withUnit.groupValues[1]) ?: return null
            val unit = withUnit.groupValues[2].lowercase()
            return quantity to unit
        }

        val timesPrice = quantityTimesPrice.find(line)
        if (timesPrice != null) {
            val quantity = decimalToMilli(timesPrice.groupValues[1]) ?: return null
            return quantity to "un"
        }

        return null
    }

    private fun decimalToMilli(value: String): Long? =
        runCatching {
            java.math.BigDecimal(value.replace(",", "."))
                .movePointRight(3)
                .setScale(0, java.math.RoundingMode.HALF_UP)
                .longValueExact()
        }.getOrNull()

    private fun looksLikeItemDescription(value: String): Boolean {
        val trimmed = value.trim()
        if (trimmed.length < 3) return false
        if (!trimmed.any(Char::isLetter)) return false
        val lower = trimmed.lowercase()
        return nonItemTerms.none(lower::contains) &&
            !lower.startsWith("r$") &&
            !lower.startsWith("data") &&
            !lower.startsWith("hora")
    }

    private fun toCents(match: MatchResult): Long {
        val integer = match.groupValues[1].replace(".", "")
        val decimal = match.groupValues[2]
        return integer.toLong() * 100L + decimal.toLong()
    }
}
