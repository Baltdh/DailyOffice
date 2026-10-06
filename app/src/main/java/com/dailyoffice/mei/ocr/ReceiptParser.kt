package com.dailyoffice.mei.ocr

data class ParsedReceipt(
    val merchant: String = "",
    val cnpj: String? = null,
    val date: String? = null,
    val amountCents: Long = 0,
    val paymentMethod: String = "REVISAR",
    val classification: String = "REVISAR"
)

object ReceiptParser {
    private val cnpjRegex = Regex("""\b\d{2}\.?\d{3}\.?\d{3}/?\d{4}-?\d{2}\b""")
    private val dateRegex = Regex("""\b([0-3]?\d)[/.-]([01]?\d)[/.-](20\d{2})\b""")
    private val moneyRegex = Regex("""(?:R\$\s*)?(\d{1,3}(?:\.\d{3})*,\d{2}|\d+,\d{2})""")

    private val personalWords = listOf(
        "drogasil", "farmacia", "farmácia", "barbear", "ração", "racao",
        "petisco", "dreamies", "kitekat", "esmalte", "desodorante",
        "sabonete", "enxaguante", "orquidea", "orquídea", "presunto", "salame"
    )

    private val businessWords = listOf(
        "salmao", "salmão", "acai", "açaí", "cream cheese", "embalagem",
        "alga", "nori", "arroz", "tempura", "tempurá", "panko", "shoyu",
        "morango", "banana", "granola", "pacoca", "paçoca", "ovomaltine",
        "copo", "tampa", "saco kraft", "leite condensado"
    )

    fun parse(text: String): ParsedReceipt {
        val normalized = text.lowercase()
        val cnpj = cnpjRegex.find(text)?.value
        val date = dateRegex.find(text)?.value?.replace('.', '/')?.replace('-', '/')
        val payment = when {
            "pix" in normalized -> "PIX"
            "débito" in normalized || "debito" in normalized -> "DEBITO"
            "crédito" in normalized || "credito" in normalized -> "CREDITO"
            "dinheiro" in normalized -> "DINHEIRO"
            else -> "REVISAR"
        }

        val hasPersonal = personalWords.any { it in normalized }
        val hasBusiness = businessWords.any { it in normalized }

        return ParsedReceipt(
            merchant = guessMerchant(text),
            cnpj = cnpj,
            date = date,
            amountCents = findAmount(text),
            paymentMethod = payment,
            classification = when {
                hasPersonal && hasBusiness -> "MISTO"
                hasBusiness -> "EMPRESA"
                hasPersonal -> "PESSOAL"
                else -> "REVISAR"
            }
        )
    }

    private fun findAmount(text: String): Long {
        val lines = text.lines().filter { it.isNotBlank() }
        val preferred = lines.filter {
            val n = it.lowercase()
            "total" in n || "valor pago" in n || "a pagar" in n
        }

        fun values(source: List<String>): List<Long> = source.flatMap { line ->
            moneyRegex.findAll(line).mapNotNull { match ->
                toCents(match.groupValues[1])
            }.toList()
        }

        return values(preferred).maxOrNull() ?: values(lines).maxOrNull() ?: 0L
    }

    private fun toCents(value: String): Long? {
        val normalized = value.replace(".", "").replace(",", ".")
        return normalized.toBigDecimalOrNull()?.movePointRight(2)?.longValueExact()
    }

    private fun guessMerchant(text: String): String {
        val ignored = listOf(
            "cnpj", "cpf", "nfce", "nf-e", "danfe", "documento auxiliar",
            "cupom fiscal", "consumidor", "total", "subtotal"
        )
        return text.lines()
            .map { it.trim() }
            .firstOrNull { line ->
                line.length in 4..60 &&
                    line.any(Char::isLetter) &&
                    ignored.none { it in line.lowercase() }
            }.orEmpty()
    }
}
