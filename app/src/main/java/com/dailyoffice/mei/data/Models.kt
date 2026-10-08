package com.dailyoffice.mei.data

import android.content.Context
import androidx.room.*
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.flow.Flow

enum class Ownership { BUSINESS, PERSONAL, MIXED, REVIEW }
enum class PaymentStatus { PAID, PENDING, OVERDUE, CANCELLED }
enum class PaymentMethod { CASH, PIX, DEBIT, CREDIT, DIGITAL_WALLET, OTHER }
enum class EntryKind { EXPENSE, REVENUE, CONTRIBUTION, WITHDRAWAL, REIMBURSEMENT, TRANSFER }
enum class AccountKind {
    BUSINESS_BANK,
    BUSINESS_CASH,
    BUSINESS_CARD,
    OWNER_PERSONAL_BANK,
    OWNER_PERSONAL_CARD,
    IFOOD_RECEIVABLE,
    OTHER
}
enum class StockUnit { UNIT, GRAM, KILOGRAM, MILLILITER, LITER, PACK }
enum class StockMovementType {
    PURCHASE,
    CONSUMPTION,
    LOSS,
    ADJUSTMENT_IN,
    ADJUSTMENT_OUT,
    TRANSFER_IN,
    TRANSFER_OUT
}

@Entity(
    tableName = "companies",
    indices = [Index("active")]
)
data class Company(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val cnpj: String = "",
    val ownerName: String = "",
    val active: Boolean = true,
    val createdAt: Long = System.currentTimeMillis()
)



@Entity(
    tableName = "accounts",
    indices = [
        Index("companyId"),
        Index("active"),
        Index("kind")
    ]
)
data class Account(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val companyId: Long,
    val name: String,
    val kind: AccountKind = AccountKind.OTHER,
    val active: Boolean = true,
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "inventory_products",
    indices = [Index("active"), Index("name")]
)
data class InventoryProduct(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val unit: StockUnit = StockUnit.UNIT,
    val active: Boolean = true,
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "stock_movements",
    indices = [
        Index("productId"),
        Index("companyId"),
        Index("counterpartyCompanyId"),
        Index("type"),
        Index("createdAt"),
        Index("transferGroupId"),
        Index("receiptId")
    ]
)
data class StockMovement(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val productId: Long,
    val companyId: Long,
    val counterpartyCompanyId: Long? = null,
    val type: StockMovementType,
    val quantityMilli: Long,
    val totalCostCents: Long? = null,
    val note: String = "",
    val transferGroupId: String = "",
    val receiptId: Long? = null,
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "receipts",
    indices = [
        Index("createdAt"),
        Index("ownership"),
        Index("paymentStatus"),
        Index("imageSha256"),
        Index("companyId"),
        Index("accountId")
    ]
)
data class Receipt(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(defaultValue = "1")
    val companyId: Long = 1,
    val accountId: Long? = null,
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
    val lineIndex: Int = 0,
    val stockProductId: Long? = null,
    @ColumnInfo(defaultValue = "0")
    val stockQuantityMilli: Long = 0,
    @ColumnInfo(defaultValue = "0")
    val addToStock: Boolean = false
)

@Entity(
    tableName = "transactions",
    indices = [
        Index("receiptId"),
        Index("dueAt"),
        Index("kind"),
        Index("createdAt"),
        Index("companyId"),
        Index("accountId"),
        Index("counterpartyAccountId")
    ]
)
data class Transaction(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(defaultValue = "1")
    val companyId: Long = 1,
    val accountId: Long? = null,
    val counterpartyAccountId: Long? = null,
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
interface CompanyDao {
    @Insert
    suspend fun insert(company: Company): Long

    @Update
    suspend fun update(company: Company)

    @Query("SELECT * FROM companies WHERE active = 1 ORDER BY name COLLATE NOCASE ASC")
    fun observeActive(): Flow<List<Company>>

    @Query("SELECT * FROM companies WHERE id = :id LIMIT 1")
    suspend fun byId(id: Long): Company?

    @Query("SELECT COUNT(*) FROM companies")
    suspend fun count(): Int

    @Query("SELECT * FROM companies ORDER BY id ASC")
    suspend fun all(): List<Company>
}



@Dao
interface AccountDao {
    @Insert
    suspend fun insert(account: Account): Long

    @Update
    suspend fun update(account: Account)

    @Query(
        "SELECT * FROM accounts WHERE companyId = :companyId AND active = 1 " +
            "ORDER BY name COLLATE NOCASE ASC"
    )
    fun observeByCompany(companyId: Long): Flow<List<Account>>

    @Query(
        "SELECT * FROM accounts WHERE companyId = :companyId AND active = 1 " +
            "ORDER BY name COLLATE NOCASE ASC"
    )
    suspend fun activeByCompany(companyId: Long): List<Account>

    @Query("SELECT COUNT(*) FROM accounts WHERE companyId = :companyId")
    suspend fun countForCompany(companyId: Long): Int

    @Query("SELECT * FROM accounts WHERE id = :id LIMIT 1")
    suspend fun byId(id: Long): Account?
}

@Dao
interface InventoryProductDao {
    @Insert
    suspend fun insert(product: InventoryProduct): Long

    @Update
    suspend fun update(product: InventoryProduct)

    @Query("SELECT * FROM inventory_products WHERE active = 1 ORDER BY name COLLATE NOCASE ASC")
    fun observeActive(): Flow<List<InventoryProduct>>

    @Query("SELECT * FROM inventory_products WHERE id = :id LIMIT 1")
    suspend fun byId(id: Long): InventoryProduct?
}

@Dao
interface StockMovementDao {
    @Insert
    suspend fun insert(movement: StockMovement): Long

    @Query("SELECT * FROM stock_movements ORDER BY createdAt DESC, id DESC")
    fun observeAll(): Flow<List<StockMovement>>

    @Query("DELETE FROM stock_movements WHERE receiptId = :receiptId AND type = 'PURCHASE'")
    suspend fun deleteReceiptPurchases(receiptId: Long)

    @Query(
        """
        SELECT COALESCE(SUM(
            CASE
                WHEN type IN ('PURCHASE','ADJUSTMENT_IN','TRANSFER_IN') THEN quantityMilli
                ELSE -quantityMilli
            END
        ), 0)
        FROM stock_movements
        WHERE productId = :productId AND companyId = :companyId
        """
    )
    suspend fun balanceFor(productId: Long, companyId: Long): Long
}

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
    entities = [
        Company::class,
        Account::class,
        InventoryProduct::class,
        StockMovement::class,
        Receipt::class,
        ReceiptItem::class,
        Transaction::class
    ],
    version = 9,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class DailyOfficeDb : RoomDatabase() {
    abstract fun companyDao(): CompanyDao
    abstract fun accountDao(): AccountDao
    abstract fun inventoryProductDao(): InventoryProductDao
    abstract fun stockMovementDao(): StockMovementDao
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

        private val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS companies (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        name TEXT NOT NULL,
                        cnpj TEXT NOT NULL,
                        ownerName TEXT NOT NULL,
                        active INTEGER NOT NULL,
                        createdAt INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS index_companies_active ON companies(active)"
                )
                db.execSQL(
                    """
                    INSERT OR IGNORE INTO companies
                    (id, name, cnpj, ownerName, active, createdAt)
                    VALUES (1, 'Empresa principal', '', '', 1, CAST(strftime('%s','now') AS INTEGER) * 1000)
                    """.trimIndent()
                )

                db.execSQL(
                    "ALTER TABLE receipts ADD COLUMN companyId INTEGER NOT NULL DEFAULT 1"
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS index_receipts_companyId ON receipts(companyId)"
                )

                db.execSQL(
                    "ALTER TABLE transactions ADD COLUMN companyId INTEGER NOT NULL DEFAULT 1"
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS index_transactions_companyId ON transactions(companyId)"
                )
            }
        }

        private val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS inventory_products (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        name TEXT NOT NULL,
                        unit TEXT NOT NULL,
                        active INTEGER NOT NULL,
                        createdAt INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS index_inventory_products_active ON inventory_products(active)"
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS index_inventory_products_name ON inventory_products(name)"
                )

                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS stock_movements (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        productId INTEGER NOT NULL,
                        companyId INTEGER NOT NULL,
                        counterpartyCompanyId INTEGER,
                        type TEXT NOT NULL,
                        quantityMilli INTEGER NOT NULL,
                        totalCostCents INTEGER,
                        note TEXT NOT NULL,
                        transferGroupId TEXT NOT NULL,
                        createdAt INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS index_stock_movements_productId ON stock_movements(productId)"
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS index_stock_movements_companyId ON stock_movements(companyId)"
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS index_stock_movements_counterpartyCompanyId ON stock_movements(counterpartyCompanyId)"
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS index_stock_movements_type ON stock_movements(type)"
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS index_stock_movements_createdAt ON stock_movements(createdAt)"
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS index_stock_movements_transferGroupId ON stock_movements(transferGroupId)"
                )
            }
        }

        private val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE receipt_items ADD COLUMN stockProductId INTEGER"
                )
                db.execSQL(
                    "ALTER TABLE receipt_items ADD COLUMN stockQuantityMilli INTEGER NOT NULL DEFAULT 0"
                )
                db.execSQL(
                    "ALTER TABLE receipt_items ADD COLUMN addToStock INTEGER NOT NULL DEFAULT 0"
                )

                db.execSQL(
                    "ALTER TABLE stock_movements ADD COLUMN receiptId INTEGER"
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS index_stock_movements_receiptId ON stock_movements(receiptId)"
                )
            }
        }

        private val MIGRATION_7_8 = object : Migration(7, 8) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS accounts (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        companyId INTEGER NOT NULL,
                        name TEXT NOT NULL,
                        kind TEXT NOT NULL,
                        active INTEGER NOT NULL,
                        createdAt INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS index_accounts_companyId ON accounts(companyId)"
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS index_accounts_active ON accounts(active)"
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS index_accounts_kind ON accounts(kind)"
                )

                db.execSQL(
                    "ALTER TABLE receipts ADD COLUMN accountId INTEGER"
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS index_receipts_accountId ON receipts(accountId)"
                )

                db.execSQL(
                    "ALTER TABLE transactions ADD COLUMN accountId INTEGER"
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS index_transactions_accountId ON transactions(accountId)"
                )
            }
        }

        private val MIGRATION_8_9 = object : Migration(8, 9) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE transactions ADD COLUMN counterpartyAccountId INTEGER"
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS index_transactions_counterpartyAccountId ON transactions(counterpartyAccountId)"
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
                    .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7, MIGRATION_7_8, MIGRATION_8_9)
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
    @TypeConverter fun accountKindToString(value: AccountKind) = value.name
    @TypeConverter fun stringToAccountKind(value: String) = AccountKind.valueOf(value)
    @TypeConverter fun stockUnitToString(value: StockUnit) = value.name
    @TypeConverter fun stringToStockUnit(value: String) = StockUnit.valueOf(value)
    @TypeConverter fun stockMovementTypeToString(value: StockMovementType) = value.name
    @TypeConverter fun stringToStockMovementType(value: String) = StockMovementType.valueOf(value)
}
