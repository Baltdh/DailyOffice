package com.baltdh.dailyoffice;

import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class ReceiptParserTest {
    @Test public void selectsTotalPayableInsteadOfChange() {
        String receipt = "SUPERMERCADO CENTRAL\n" +
                "CNPJ 12.345.678/0001-90\n" +
                "TOTAL A PAGAR R$ 1.234,56\n" +
                "TROCO R$ 20,00";
        ReceiptParser.Result result = ReceiptParser.parse(receipt);
        assertTrue(result.hasAmount);
        assertEquals(123456L, result.amountCents);
        assertEquals("SUPERMERCADO CENTRAL", result.description);
    }

    @Test public void prefersGrandTotalOverSubtotalAndDiscount() {
        String receipt = "MERCADO\n" +
                "SUBTOTAL R$ 40,00\n" +
                "DESCONTO R$ 5,00\n" +
                "TOTAL GERAL R$ 35,00";
        ReceiptParser.Result result = ReceiptParser.parse(receipt);
        assertTrue(result.hasAmount);
        assertEquals(3500L, result.amountCents);
    }

    @Test public void supportsValueLabelAndSingleDecimalDigit() {
        ReceiptParser.Result result = ReceiptParser.parse("LOJA ABC\nVALOR: R$ 12,5");
        assertTrue(result.hasAmount);
        assertEquals(1250L, result.amountCents);
        assertEquals("LOJA ABC", result.description);
    }

    @Test public void doesNotMistakeTaxIdOrDateForPurchaseValue() {
        String receipt = "CNPJ: 12.345.678/0001-90\n" +
                "CPF: 123.456.789-00\n" +
                "DATA: 12/10/2026";
        ReceiptParser.Result result = ReceiptParser.parse(receipt);
        assertFalse(result.hasAmount);
        assertEquals("Despesa via comprovante", result.description);
    }

    @Test public void joinsTotalLabelWithAmountOnNextLine() {
        ReceiptParser.Result result = ReceiptParser.parse("LOJA CENTRAL\nTOTAL A PAGAR\nR$ 19,90");
        assertTrue(result.hasAmount);
        assertEquals(1990L, result.amountCents);
        assertEquals("Alta", result.confidence);
    }

    @Test public void ignoresTotalItemCount() {
        ReceiptParser.Result result = ReceiptParser.parse("LOJA CENTRAL\nTOTAL DE ITENS 2");
        assertFalse(result.hasAmount);
    }

    @Test public void refusesUnlabelledNumbersWithoutCurrencySignal() {
        ReceiptParser.Result result = ReceiptParser.parse("LOJA CENTRAL\nITEM 1\nQUANTIDADE 2");
        assertFalse(result.hasAmount);
    }
}
