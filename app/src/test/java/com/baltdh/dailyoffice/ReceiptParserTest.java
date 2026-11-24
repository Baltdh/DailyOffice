package com.baltdh.dailyoffice;

import org.junit.Test;
import static org.junit.Assert.*;

public class ReceiptParserTest {
    @Test public void selectsTotalPayableInsteadOfChange() {
        ReceiptParser.Result r = ReceiptParser.parse("SUPERMERCADO CENTRAL\nCNPJ 12.345.678/0001-90\nTOTAL A PAGAR R$ 1.234,56\nTROCO R$ 20,00");
        assertTrue(r.hasAmount); assertEquals(123456L, r.amountCents);
        assertEquals("SUPERMERCADO CENTRAL", r.description);
    }

    @Test public void prefersGrandTotalOverSubtotalAndDiscount() {
        ReceiptParser.Result r = ReceiptParser.parse("MERCADO\nSUBTOTAL R$ 40,00\nDESCONTO R$ 5,00\nTOTAL GERAL R$ 35,00");
        assertTrue(r.hasAmount); assertEquals(3500L, r.amountCents);
    }

    @Test public void supportsValueLabelAndSingleDecimalDigit() {
        ReceiptParser.Result r = ReceiptParser.parse("LOJA ABC\nVALOR: R$ 12,5");
        assertTrue(r.hasAmount); assertEquals(1250L, r.amountCents);
    }

    @Test public void doesNotMistakeTaxIdOrDateForPurchaseValue() {
        ReceiptParser.Result r = ReceiptParser.parse("CNPJ: 12.345.678/0001-90\nCPF: 123.456.789-00\nDATA: 12/10/2026");
        assertFalse(r.hasAmount);
    }

    @Test public void joinsTotalLabelWithAmountOnNextLine() {
        ReceiptParser.Result r = ReceiptParser.parse("LOJA CENTRAL\nTOTAL A PAGAR\nR$ 19,90");
        assertTrue(r.hasAmount); assertEquals(1990L, r.amountCents);
        assertEquals("Alta", r.confidence);
    }

    @Test public void ignoresTotalItemCount() {
        assertFalse(ReceiptParser.parse("LOJA CENTRAL\nTOTAL DE ITENS 2").hasAmount);
    }

    @Test public void refusesUnlabelledNumbersWithoutCurrencySignal() {
        assertFalse(ReceiptParser.parse("LOJA CENTRAL\nITEM 1\nQUANTIDADE 2").hasAmount);
    }

    @Test public void vacationReceiptSelectsNetAmountNotGrossOrDeductions() {
        String sample = "AVISO E RECIBO DE FERIAS\nAVISO PREVIO DE FERIAS\n" +
                "BASE PARA CALCULO\nSALARIO BASE: 2.157,00\nTOTAL BASE DE CALCULO: 2.157,00\n" +
                "PROVENTOS\nFerias: 1.078,50\n1/3 Ferias: 359,50\nTOTAL DOS PROVENTOS: 1.438,00\n" +
                "DESCONTOS\nDesconto da Previdencia: 107,85\nTOTAL DOS DESCONTOS: 107,85\n" +
                "TOTAL LIQUIDO: 1.330,15\nA importancia liquida de R$ 1.330,15 a ser paga antecipadamente.";
        ReceiptParser.Result r = ReceiptParser.parse(sample);
        assertTrue(r.hasAmount);
        assertEquals(133015L, r.amountCents);
        assertTrue(r.payrollDocument);
        assertEquals("Receita pessoal", r.suggestedKind);
        assertEquals("Recebimento de férias / salário", r.description);
    }

    @Test public void ignoresPurchaseWithOnlyTaxAndIdentificationValues() {
        ReceiptParser.Result r = ReceiptParser.parse("CUPOM FISCAL\nCNPJ 12.345.678/0001-90\nDATA 24/11/2026\nICMS 2,50\nDESCONTO 5,00");
        assertFalse(r.hasAmount);
    }

    @Test public void selectsPaidTotalAndDoesNotChooseChange() {
        ReceiptParser.Result r = ReceiptParser.parse("LOJA EXEMPLO\nTOTAL PAGO R$ 89,90\nTROCO R$ 10,10");
        assertTrue(r.hasAmount);
        assertEquals(8990L, r.amountCents);
    }

    @Test public void payrollPrefersNetWhenGrossAndDiscountsAreAlsoPresent() {
        String sample = "DEMONSTRATIVO DE PAGAMENTO\nPROVENTOS 1.500,00\nTOTAL DOS PROVENTOS 1.500,00\n" +
                "DESCONTOS 120,00\nTOTAL DOS DESCONTOS 120,00\nTOTAL LIQUIDO 1.380,00";
        ReceiptParser.Result r = ReceiptParser.parse(sample);
        assertEquals(138000L, r.amountCents);
        assertTrue(r.payrollDocument);
    }
}
