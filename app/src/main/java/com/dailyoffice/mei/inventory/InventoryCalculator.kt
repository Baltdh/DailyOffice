package com.dailyoffice.mei.inventory

import com.dailyoffice.mei.data.InventoryProduct
import com.dailyoffice.mei.data.StockMovement
import com.dailyoffice.mei.data.StockMovementType

data class InventoryBalance(
    val product: InventoryProduct,
    val companyQuantityMilli: Long,
    val physicalQuantityMilli: Long
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
