package com.dailyoffice.mei.data

import androidx.room.*

enum class Ownership { BUSINESS, PERSONAL, MIXED, REVIEW }
enum class PaymentStatus { PAID, PENDING, OVERDUE, CANCELLED }
enum class PaymentMethod { CASH, PIX, DEBIT, CREDIT, DIGITAL_WALLET, OTHER }

@Entity(tableName = "receipts")
data class Receipt(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val imageUri: String,
    val supplier: String = "",
    val documentNumber: String = "",
    val issuedAt: Long? = null,
    val totalCents: Long = 0,
    val ownership: Ownership = Ownership.REVIEW,
    val paymentMethod: PaymentMethod = PaymentMethod.OTHER,
    val paymentStatus: PaymentStatus = PaymentStatus.PAID,
    val dueAt: Long? = null,
    val rawOcr: String = "",
    val notes: String = "",
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "transactions")
data class Transaction(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val receiptId: Long? = null,
    val description: String,
    val amountCents: Long,
    val ownership: Ownership,
    val paidAt: Long? = null,
    val dueAt: Long? = null
)

@Dao
interface ReceiptDao {
    @Insert suspend fun insert(receipt: Receipt): Long
    @Update suspend fun update(receipt: Receipt)
    @Query("SELECT * FROM receipts ORDER BY createdAt DESC") suspend fun all(): List<Receipt>
    @Query("SELECT * FROM receipts WHERE ownership = 'REVIEW' ORDER BY createdAt DESC") suspend fun reviewQueue(): List<Receipt>
}

@Database(entities=[Receipt::class, Transaction::class], version=1, exportSchema=false)
@TypeConverters(Converters::class)
abstract class DailyOfficeDb: RoomDatabase() { abstract fun receiptDao(): ReceiptDao }

class Converters {
    @TypeConverter fun ownership(v: Ownership)=v.name
    @TypeConverter fun ownership(v:String)=Ownership.valueOf(v)
    @TypeConverter fun payment(v: PaymentMethod)=v.name
    @TypeConverter fun payment(v:String)=PaymentMethod.valueOf(v)
    @TypeConverter fun status(v: PaymentStatus)=v.name
    @TypeConverter fun status(v:String)=PaymentStatus.valueOf(v)
}
