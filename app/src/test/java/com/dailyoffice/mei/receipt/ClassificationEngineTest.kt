package com.dailyoffice.mei.receipt

import com.dailyoffice.mei.data.Ownership
import org.junit.Assert.assertEquals
import org.junit.Test

class ClassificationEngineTest {
    @Test
    fun classifiesStoreIngredientsAsBusiness() {
        assertEquals(
            Ownership.BUSINESS,
            ClassificationEngine.suggestItem("SALMAO FRESCO").ownership
        )
        assertEquals(
            Ownership.BUSINESS,
            ClassificationEngine.suggestItem("DORITOS").ownership
        )
    }

    @Test
    fun classifiesKnownPersonalItemsAsPersonal() {
        assertEquals(
            Ownership.PERSONAL,
            ClassificationEngine.suggestItem("RACAO KITEKAT").ownership
        )
        assertEquals(
            Ownership.PERSONAL,
            ClassificationEngine.suggestItem("DESODORANTE").ownership
        )
    }

    @Test
    fun leavesUnknownItemsForReview() {
        assertEquals(
            Ownership.REVIEW,
            ClassificationEngine.suggestItem("ITEM GENERICO").ownership
        )
    }
}
