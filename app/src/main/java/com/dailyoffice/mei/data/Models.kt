package com.dailyoffice.mei.data

import android.content.Context
import androidx.room.*
import kotlinx.coroutines.flow.Flow

enum class Ownership { BUSINESS, PERSONAL, MIXED, REVIEW }
enum class PaymentStatus { PAID, PENDING, OVERDUE, CANCELLED }
enum class PaymentMethod { CASH, PIX, DEBIT, CREDIT, DIGITAL_WALLET, OTHER }

@Entity(
    tableName = "receipts",
    indices = [Index("createdAt"), Index("ownership"), Index("paymentStatus")]
)
data class Receipt(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val imageUri: String,
    val supplier: String = "",
    val documentNumber: String = "",
    val issuedAt: Long? = null,
    val totalCents: Long = 0,
    val businessCents: Long = 0,
    val personalCents: Long = 0,
    val ownership: Ownership = Ownership.REVIEW,
    val paymentMethod: PaymentMethod = PaymentMethod.OTHER,
    val paymentStatus: PaymentStatus = PaymentStatus.PAID,
    val dueAt: Long? = null,
    val category: String = "",
    val rawOcr: String = "",
    val ocrConfidence: Float = 0f,
    val notes: String = "",
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "transactions",
    indices = [Index("receiptId"), Index("dueAt")]
)
data class Transaction(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val receiptId: Long? = null,
    val description: String,
    val amountCents: Long,
    val ownership: Ownership,
    val paymentMethod: PaymentMethod = PaymentMethod.OTHER,
    val paymentStatus: PaymentStatus = PaymentStatus.PAID,
    val paidAt: Long? = null,
    val dueAt: Long? = null
)

@Dao
interface ReceiptDao {
    @Insert
    suspend fun insert(receipt: Receipt): Long

    @Update
    suspend fun update(receipt: Receipt)

    @Query("SELECT * FROM receipts ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<Receipt>>

    @Query("SELECT * FROM receipts WHERE ownership = 'REVIEW' ORDER BY createdAt DESC")
    fun observeReviewQueue(): Flow<List<Receipt>>

    @Query("SELECT * FROM receipts WHERE paymentStatus IN ('PENDING','OVERDUE') ORDER BY dueAt ASC")
    fun observePending(): Flow<List<Receipt>>
}

@Dao
interface TransactionDao {
    @Insert
    suspend fun insert(transaction: Transaction): Long
}

@Database(
    entities = [Receipt::class, Transaction::class],
    version = 1,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class DailyOfficeDb : RoomDatabase() {
    abstract fun receiptDao(): ReceiptDao
    abstract fun transactionDao(): TransactionDao

    companion object {
        @Volatile private var instance: DailyOfficeDb? = null

        fun get(context: Context): DailyOfficeDb =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    DailyOfficeDb::class.java,
                    "dailyoffice.db"
                ).build().also { instance = it }
            }
    }
}

class Converters {
    @TypeConverter fun ownershipToString(value: Ownership) = value.name
    @TypeConverter fun stringToOwnership(value: String) = Ownership.valueOf(value)
    @TypeConverter fun paymentToString(value: PaymentMethod) = value.name
    @TypeConverter fun stringToPayment(value: String) = PaymentMethod.valueOf(value)
    @TypeConverter fun statusToString(value: PaymentStatus) = value.name
    @TypeConverter fun stringToStatus(value: String) = PaymentStatus.valueOf(value)
}
