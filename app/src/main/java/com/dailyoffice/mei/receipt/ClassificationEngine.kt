package com.dailyoffice.mei.receipt

import com.dailyoffice.mei.data.Ownership

data class ClassificationSuggestion(
    val ownership: Ownership,
    val reason: String
)

object ClassificationEngine {
    private val businessSuppliers = listOf(
        "mar e rio", "ilha dos pescados", "ice fest", "belão embalagens",
        "belao embalagens", "jn casa da embalagem", "coral pescados",
        "viaplast", "casa da embalagem"
    )

    private val businessTerms = listOf(
        "salmão", "salmao", "alga", "nori", "açaí", "acai", "cream cheese",
        "shoyu", "panko", "paçoca", "pacoca", "granola", "morango", "banana",
        "copo", "tampa", "sacola", "embalagem", "ovomaltine", "chocobol",
        "chocoboll", "doritos", "cebolinha", "couve", "kani", "gergelim",
        "arroz", "vinagre", "leite condensado", "leite em pó", "leite em po",
        "oreo", "uva", "kiwi", "avelã", "avela", "cacau", "óleo", "oleo",
        "pote", "guardanapo", "hashi", "sachê", "sache"
    )

    private val personalTerms = listOf(
        "ração", "racao", "kitekat", "dreamies", "barbeador", "desodorante",
        "esmalte", "orquídea", "orquidea", "sabonete", "enxaguante",
        "presunto", "salame", "aparelho de barbear"
    )

    fun suggest(rawText: String, supplier: String?): ClassificationSuggestion {
        val source = (supplier.orEmpty() + "\n" + rawText).lowercase()
        val hasBusiness = businessTerms.any(source::contains) ||
            businessSuppliers.any(source::contains)
        val hasPersonal = personalTerms.any(source::contains)

        return when {
            hasBusiness && hasPersonal -> ClassificationSuggestion(
                Ownership.MIXED,
                "Há itens com sinais de uso da empresa e itens pessoais."
            )
            hasPersonal -> ClassificationSuggestion(
                Ownership.PERSONAL,
                "Foram encontrados termos normalmente pessoais."
            )
            hasBusiness -> ClassificationSuggestion(
                Ownership.BUSINESS,
                "Fornecedor ou itens conhecidos da operação foram identificados."
            )
            else -> ClassificationSuggestion(
                Ownership.REVIEW,
                "Sem dados suficientes para classificar automaticamente."
            )
        }
    }

    fun suggestItem(description: String): ClassificationSuggestion {
        val source = description.lowercase()

        val personal = personalTerms.firstOrNull(source::contains)
        if (personal != null) {
            return ClassificationSuggestion(
                Ownership.PERSONAL,
                "Item reconhecido como pessoal por “$personal”."
            )
        }

        val business = businessTerms.firstOrNull(source::contains)
        if (business != null) {
            return ClassificationSuggestion(
                Ownership.BUSINESS,
                "Item reconhecido como da empresa por “$business”."
            )
        }

        return ClassificationSuggestion(
            Ownership.REVIEW,
            "Item ainda não reconhecido."
        )
    }
}
