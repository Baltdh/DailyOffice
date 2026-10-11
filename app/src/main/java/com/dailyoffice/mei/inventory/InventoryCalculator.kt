package com.dailyoffice.mei.inventory

import com.dailyoffice.mei.data.InventoryProduct
import com.dailyoffice.mei.data.StockMovement
import com.dailyoffice.mei.data.StockMovementType
import java.time.Instant
import java.time.ZoneId

data class InventoryBalance(
    val product: InventoryProduct,
    val companyQuantityMilli: Long,
    val physicalQuantityMilli: Long
)

data class InventoryMonthSummary(
    val purchaseCostCents: Long = 0,
    val purchaseEntries: Int = 0,
    val consumptionEntries: Int = 0,
    val lossEntries: Int = 0,
    val transferInEntries: Int = 0,
    val transferOutEntries: Int = 0,
    val adjustmentEntries: Int = 0
)

object InventoryCalculator {
    fun balanceFor(
        product: InventoryProduct,
        movements: List<StockMovement>,
        companyId: Long
    ): InventoryBalance {
        val productMovements = movements.filter { it.productId == product.id }

        val companyBalance = productMovements
            .filter { it.companyId == companyId }
            .sumOf(::signedQuantity)

        val physicalBalance = productMovements.sumOf(::signedQuantity)

        return InventoryBalance(
            product = product,
            companyQuantityMilli = companyBalance,
            physicalQuantityMilli = physicalBalance
        )
    }

    fun monthlySummary(
        movements: List<StockMovement>,
        companyId: Long,
        year: Int,
        month: Int,
        zoneId: ZoneId = ZoneId.systemDefault()
    ): InventoryMonthSummary {
        val safeMonth = month.coerceIn(1, 12)
        val selected = movements.filter { movement ->
            if (movement.companyId != companyId) {
                return@filter false
            }

            val date = Instant.ofEpochMilli(movement.createdAt)
                .atZone(zoneId)

            date.year == year && date.monthValue == safeMonth
        }

        return InventoryMonthSummary(
            purchaseCostCents = selected
                .filter { it.type == StockMovementType.PURCHASE }
                .sumOf { it.totalCostCents ?: 0L },
            purchaseEntries = selected.count {
                it.type == StockMovementType.PURCHASE
            },
            consumptionEntries = selected.count {
                it.type == StockMovementType.CONSUMPTION
            },
            lossEntries = selected.count {
                it.type == StockMovementType.LOSS
            },
            transferInEntries = selected.count {
                it.type == StockMovementType.TRANSFER_IN
            },
            transferOutEntries = selected.count {
                it.type == StockMovementType.TRANSFER_OUT
            },
            adjustmentEntries = selected.count {
                it.type == StockMovementType.ADJUSTMENT_IN ||
                    it.type == StockMovementType.ADJUSTMENT_OUT
            }
        )
    }

    fun signedQuantity(movement: StockMovement): Long =
        when (movement.type) {
            StockMovementType.PURCHASE,
            StockMovementType.ADJUSTMENT_IN,
            StockMovementType.TRANSFER_IN -> movement.quantityMilli

            StockMovementType.CONSUMPTION,
            StockMovementType.LOSS,
            StockMovementType.ADJUSTMENT_OUT,
            StockMovementType.TRANSFER_OUT -> -movement.quantityMilli
        }
}
