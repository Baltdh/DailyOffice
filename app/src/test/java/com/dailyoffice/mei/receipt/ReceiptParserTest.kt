package com.dailyoffice.mei.receipt

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ReceiptParserTest {
    @Test
    fun parsesBrazilianReceiptTotalAndDate() {
        val text = """
            MERCADO TESTE LTDA
            CNPJ 00.000.000/0001-00
            NFC-e 123456
            05/10/2026
            VALOR TOTAL R$ 137,64
        """.trimIndent()

        val parsed = ReceiptParser.parse(text)

        assertEquals("MERCADO TESTE LTDA", parsed.supplier)
        assertEquals(13764L, parsed.totalCents)
        assertEquals("05/10/2026", parsed.date)
        assertEquals("123456", parsed.document)
        assertTrue(parsed.confidence >= 0.8f)
    }

    @Test
    fun fallsBackToLargestMoneyValueWithWarning() {
        val parsed = ReceiptParser.parse(
            "LOJA TESTE\nitem 10,00\nitem 35,50"
        )

        assertEquals(3550L, parsed.totalCents)
        assertTrue(parsed.warnings.isNotEmpty())
    }

    @Test
    fun parsesLineItemsAndIgnoresReceiptTotal() {
        val text = """
            MERCADO TESTE
            SALMAO 20,00
            RACAO GATO 10,00
            VALOR TOTAL R$ 30,00
        """.trimIndent()

        val parsed = ReceiptParser.parse(text)

        assertEquals(2, parsed.items.size)
        assertEquals("SALMAO", parsed.items[0].description)
        assertEquals(2000L, parsed.items[0].amountCents)
        assertEquals("RACAO GATO", parsed.items[1].description)
        assertEquals(1000L, parsed.items[1].amountCents)
    }
}
