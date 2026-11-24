package com.baltdh.dailyoffice;

import org.junit.Test;
import static org.junit.Assert.assertEquals;

public class CsvUtilsTest {
    @Test public void leavesSimpleFieldsUnquoted() {
        assertEquals("Receita empresarial", CsvUtils.escape("Receita empresarial"));
    }

    @Test public void quotesSemicolonsAndDoublesEmbeddedQuotes() {
        assertEquals("\"Loja; filial \"\"Centro\"\"\"", CsvUtils.escape("Loja; filial \"Centro\""));
    }

    @Test public void quotesMultilineFields() {
        assertEquals("\"linha 1\nlinha 2\"", CsvUtils.escape("linha 1\nlinha 2"));
    }

    @Test public void nullBecomesEmptyField() {
        assertEquals("", CsvUtils.escape(null));
    }

    @Test public void protectsSpreadsheetFormulaPrefixes() {
        assertEquals("'=SUM(A1:A2)", CsvUtils.escape("=SUM(A1:A2)"));
        assertEquals("'  +1+1", CsvUtils.escape("  +1+1"));
        assertEquals("'-100", CsvUtils.escape("-100"));
        assertEquals("'@SUM(A1:A2)", CsvUtils.escape("@SUM(A1:A2)"));
    }
}
