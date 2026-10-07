package com.dailyoffice.mei.inventory

import com.dailyoffice.mei.data.InventoryProduct
import com.dailyoffice.mei.data.StockMovement
import com.dailyoffice.mei.data.StockMovementType
import com.dailyoffice.mei.data.StockUnit
import org.junit.Assert.assertEquals
import org.junit.Test

class InventoryCalculatorTest {
    private val salmon = InventoryProduct(
        id = 1,
        name = "Salmão",
        unit = StockUnit.KILOGRAM
    )

    @Test
    fun transferChangesOwnershipButNotPhysicalStock() {
        val movements = listOf(
            StockMovement(
                productId = 1,
                companyId = 1,
                type = StockMovementType.PURCHASE,
                quantityMilli = 10_000
            ),
            StockMovement(
                productId = 1,
                companyId = 1,
                counterpartyCompanyId = 2,
                type = StockMovementType.TRANSFER_OUT,
                quantityMilli = 3_000,
                transferGroupId = "transfer-1"
            ),
            StockMovement(
                productId = 1,
                companyId = 2,
                counterpartyCompanyId = 1,
                type = StockMovementType.TRANSFER_IN,
                quantityMilli = 3_000,
                transferGroupId = "transfer-1"
            )
        )

        val companyOne = InventoryCalculator.balanceFor(
            salmon,
            movements,
            companyId = 1
        )
        val companyTwo = InventoryCalculator.balanceFor(
            salmon,
            movements,
            companyId = 2
        )

        assertEquals(7_000L, companyOne.companyQuantityMilli)
        assertEquals(3_000L, companyTwo.companyQuantityMilli)
        assertEquals(10_000L, companyOne.physicalQuantityMilli)
        assertEquals(10_000L, companyTwo.physicalQuantityMilli)
    }

    @Test
    fun consumptionAndLossReducePhysicalStock() {
        val movements = listOf(
            StockMovement(
                productId = 1,
                companyId = 1,
                type = StockMovementType.PURCHASE,
                quantityMilli = 5_000
            ),
            StockMovement(
                productId = 1,
                companyId = 1,
                type = StockMovementType.CONSUMPTION,
                quantityMilli = 1_500
            ),
            StockMovement(
                productId = 1,
                companyId = 1,
                type = StockMovementType.LOSS,
                quantityMilli = 500
            )
        )

        val result = InventoryCalculator.balanceFor(
            salmon,
            movements,
            companyId = 1
        )

        assertEquals(3_000L, result.companyQuantityMilli)
        assertEquals(3_000L, result.physicalQuantityMilli)
    }
}
