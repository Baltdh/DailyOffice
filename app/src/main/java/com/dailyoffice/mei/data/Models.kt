package com.dailyoffice.mei.data

import android.content.Context
import androidx.room.*
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.flow.Flow

enum class Ownership { BUSINESS, PERSONAL, MIXED, REVIEW }
enum class PaymentStatus { PAID, PENDING, OVERDUE, CANCELLED }
enum class PaymentMethod { CASH, PIX, DEBIT, CREDIT, DIGITAL_WALLET, OTHER }
enum class EntryKind { EXPENSE, REVENUE, CONTRIBUTION, WITHDRAWAL }

@Entity(
    tableName = "receipts",
    indices = [
        Index("createdAt"),
        Index("ownership"),
        Index("paymentStatus"),
        Index("imageSha256")
    ]
)
data class Receipt(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val imageUri: String,
    @ColumnInfo(defaultValue = "''")
    val imageSha256: String = "",
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
    tableName = "receipt_items",
    foreignKeys = [
        ForeignKey(
            entity = Receipt::class,
            parentColumns = ["id"],
            childColumns = ["receiptId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("receiptId")]
)
data class ReceiptItem(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val receiptId: Long,
    val description: String,
    val amountCents: Long,
    val ownership: Ownership = Ownership.REVIEW,
    val confidence: Float = 0f,
    val lineIndex: Int = 0
)

@Entity(
    tableName = "transactions",
    indices = [
        Index("receiptId"),
        Index("dueAt"),
        Index("kind"),
        Index("createdAt")
    ]
)
data class Transaction(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val receiptId: Long? = null,
    val description: String,
    val amountCents: Long,
    val ownership: Ownership,
    @ColumnInfo(defaultValue = "'EXPENSE'")
    val kind: EntryKind = EntryKind.EXPENSE,
    val paymentMethod: PaymentMethod = PaymentMethod.OTHER,
    val paymentStatus: PaymentStatus = PaymentStatus.PAID,
    val paidAt: Long? = null,
    val dueAt: Long? = null,
    @ColumnInfo(defaultValue = "0")
    val createdAt: Long = System.currentTimeMillis()
)

@Dao
interface ReceiptDao {
    @Insert
    suspend fun insert(receipt: Receipt): Long

    @Update
    suspend fun update(receipt: Receipt)

    @Delete
    suspend fun delete(receipt: Receipt)

    @Query("SELECT * FROM receipts ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<Receipt>>

    @Query("SELECT * FROM receipts WHERE id = :id LIMIT 1")
    suspend fun byId(id: Long): Receipt?

    @Query("SELECT * FROM receipts WHERE imageSha256 = :hash AND imageSha256 != '' LIMIT 1")
    suspend fun findByHash(hash: String): Receipt?

    @Query("SELECT * FROM receipts WHERE imageSha256 = ''")
    suspend fun withoutHash(): List<Receipt>

    @Query("SELECT * FROM receipts WHERE ownership = 'REVIEW' ORDER BY createdAt DESC")
    fun observeReviewQueue(): Flow<List<Receipt>>

    @Query("SELECT * FROM receipts WHERE paymentStatus IN ('PENDING','OVERDUE') ORDER BY dueAt ASC")
    fun observePending(): Flow<List<Receipt>>
}

@Dao
interface ReceiptItemDao {
    @Insert
    suspend fun insertAll(items: List<ReceiptItem>)

    @Query("SELECT * FROM receipt_items WHERE receiptId = :receiptId ORDER BY lineIndex ASC, id ASC")
    suspend fun byReceiptId(receiptId: Long): List<ReceiptItem>

    @Query("DELETE FROM receipt_items WHERE receiptId = :receiptId")
    suspend fun deleteByReceiptId(receiptId: Long)
}

@Dao
interface TransactionDao {
    @Insert
    suspend fun insert(transaction: Transaction): Long

    @Update
    suspend fun update(transaction: Transaction)

    @Delete
    suspend fun delete(transaction: Transaction)

    @Query("DELETE FROM transactions WHERE receiptId = :receiptId")
    suspend fun deleteByReceiptId(receiptId: Long)

    @Query("SELECT * FROM transactions ORDER BY createdAt DESC, id DESC")
    fun observeAll(): Flow<List<Transaction>>
}

@Database(
    entities = [Receipt::class, ReceiptItem::class, Transaction::class],
    version = 4,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class DailyOfficeDb : RoomDatabase() {
    abstract fun receiptDao(): ReceiptDao
    abstract fun receiptItemDao(): ReceiptItemDao
    abstract fun transactionDao(): TransactionDao

    companion object {
        @Volatile private var instance: DailyOfficeDb? = null

        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE receipts ADD COLUMN imageSha256 TEXT NOT NULL DEFAULT ''")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_receipts_imageSha256 ON receipts(imageSha256)")
                db.execSQL("ALTER TABLE transactions ADD COLUMN kind TEXT NOT NULL DEFAULT 'EXPENSE'")
                db.execSQL("ALTER TABLE transactions ADD COLUMN createdAt INTEGER NOT NULL DEFAULT 0")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_transactions_kind ON transactions(kind)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_transactions_createdAt ON transactions(createdAt)")
            }
        }

        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS receipt_items (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        receiptId INTEGER NOT NULL,
                        description TEXT NOT NULL,
                        amountCents INTEGER NOT NULL,
                        ownership TEXT NOT NULL,
                        confidence REAL NOT NULL,
                        lineIndex INTEGER NOT NULL,
                        FOREIGN KEY(receiptId) REFERENCES receipts(id) ON UPDATE NO ACTION ON DELETE CASCADE
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS index_receipt_items_receiptId ON receipt_items(receiptId)"
                )
            }
        }

        private val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    UPDATE transactions
                    SET createdAt = COALESCE(
                        (SELECT issuedAt FROM receipts WHERE receipts.id = transactions.receiptId),
                        (SELECT createdAt FROM receipts WHERE receipts.id = transactions.receiptId),
                        CAST(strftime('%s','now') AS INTEGER) * 1000
                    )
                    WHERE createdAt = 0
                    """.trimIndent()
                )
            }
        }

        fun get(context: Context): DailyOfficeDb =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    DailyOfficeDb::class.java,
                    "dailyoffice.db"
                )
                    .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4)
                    .build()
                    .also { instance = it }
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
    @TypeConverter fun kindToString(value: EntryKind) = value.name
    @TypeConverter fun stringToKind(value: String) = EntryKind.valueOf(value)
}
