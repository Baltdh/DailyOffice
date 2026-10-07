package com.dailyoffice.mei.receipt

data class ParsedReceipt(
    val supplier: String?,
    val totalCents: Long?,
    val date: String?,
    val document: String?,
    val confidence: Float,
    val warnings: List<String>
)

object ReceiptParser {
    private val money = Regex("""(?:R\$\s*)?(\d{1,3}(?:\.\d{3})*|\d+)[,.](\d{2})""")
    private val date = Regex("""\b([0-3]?\d[/.-][01]?\d[/.-]20\d{2})\b""")
    private val document = Regex("""(?i)(?:NFC-?E|NFCE|CUPOM|NOTA|COO)\D{0,20}(\d{3,})""")
    private val totalKeywords = listOf("valor total", "total a pagar", "a pagar", "total")

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
        val warnings = buildList {
            if (preferred == null && total != null) add("Total estimado pelo maior valor encontrado; confirme antes de salvar.")
            if (total == null) add("Não foi possível identificar o valor total.")
            if (supplier == null) add("Fornecedor não identificado.")
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
            warnings = warnings
        )
    }

    private fun toCents(match: MatchResult): Long {
        val integer = match.groupValues[1].replace(".", "")
        val decimal = match.groupValues[2]
        return integer.toLong() * 100L + decimal.toLong()
    }
}
