package com.dailyoffice.mei.inventory

import com.dailyoffice.mei.data.InventoryProduct
import com.dailyoffice.mei.data.StockMovement
import com.dailyoffice.mei.data.StockMovementType
import com.dailyoffice.mei.data.StockUnit
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

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
    fun monthlySummaryUsesOnlySelectedCompanyAndMonth() {
        val utc = ZoneId.of("UTC")
        val movements = listOf(
            StockMovement(
                productId = 1,
                companyId = 1,
                type = StockMovementType.PURCHASE,
                quantityMilli = 5_000,
                totalCostCents = 40_000,
                createdAt = LocalDate.of(2026, 10, 2)
                    .atStartOfDay(utc).toInstant().toEpochMilli()
            ),
            StockMovement(
                productId = 1,
                companyId = 1,
                type = StockMovementType.LOSS,
                quantityMilli = 500,
                createdAt = LocalDate.of(2026, 10, 4)
                    .atStartOfDay(utc).toInstant().toEpochMilli()
            ),
            StockMovement(
                productId = 1,
                companyId = 2,
                type = StockMovementType.PURCHASE,
                quantityMilli = 2_000,
                totalCostCents = 99_000,
                createdAt = LocalDate.of(2026, 10, 4)
                    .atStartOfDay(utc).toInstant().toEpochMilli()
            ),
            StockMovement(
                productId = 1,
                companyId = 1,
                type = StockMovementType.CONSUMPTION,
                quantityMilli = 1_000,
                createdAt = LocalDate.of(2026, 9, 30)
                    .atStartOfDay(utc).toInstant().toEpochMilli()
            )
        )

        val result = InventoryCalculator.monthlySummary(
            movements = movements,
            companyId = 1,
            year = 2026,
            month = 10,
            zoneId = utc
        )

        assertEquals(40_000L, result.purchaseCostCents)
        assertEquals(1, result.purchaseEntries)
        assertEquals(1, result.lossEntries)
        assertEquals(0, result.consumptionEntries)
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
