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
}
