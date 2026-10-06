package com.dailyoffice.mei.ocr

import org.junit.Assert.assertEquals
import org.junit.Test

class ReceiptParserTest {
    @Test
    fun parsesCommonBrazilianReceipt() {
        val text = """
            CORAL PESCADOS
            CNPJ 12.345.678/0001-99
            05/10/2026
            Salmão
            Alga Nori
            TOTAL R$ 137,64
            Débito
        """.trimIndent()

        val parsed = ReceiptParser.parse(text)

        assertEquals("CORAL PESCADOS", parsed.merchant)
        assertEquals("12.345.678/0001-99", parsed.cnpj)
        assertEquals("05/10/2026", parsed.date)
        assertEquals(13764L, parsed.amountCents)
        assertEquals("DEBITO", parsed.paymentMethod)
        assertEquals("EMPRESA", parsed.classification)
    }

    @Test
    fun marksMixedPurchaseForReview() {
        val text = """
            MERCADO
            Açaí
            Ração
            TOTAL 50,00
            dinheiro
        """.trimIndent()

        val parsed = ReceiptParser.parse(text)

        assertEquals(5000L, parsed.amountCents)
        assertEquals("DINHEIRO", parsed.paymentMethod)
        assertEquals("MISTO", parsed.classification)
    }
}
