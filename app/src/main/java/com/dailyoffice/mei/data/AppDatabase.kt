package com.dailyoffice.mei.data

import android.content.Context
import androidx.room.Database
import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "receipts")
data class ReceiptEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val imageUri: String,
    val createdAt: Long,
    val documentDate: String?,
    val dueDate: String?,
    val merchant: String,
    val cnpj: String?,
    val amountCents: Long,
    val classification: String,
    val paymentMethod: String,
    val status: String,
    val notes: String,
    val rawText: String
)

@Entity(tableName = "revenues")
data class RevenueEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val receivedAt: Long,
    val amountCents: Long,
    val source: String,
    val notes: String
)

@Dao
interface DailyOfficeDao {
    @Query("SELECT * FROM receipts ORDER BY createdAt DESC")
    fun observeReceipts(): Flow<List<ReceiptEntity>>

    @Query("SELECT * FROM revenues ORDER BY receivedAt DESC")
    fun observeRevenues(): Flow<List<RevenueEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertReceipt(receipt: ReceiptEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRevenue(revenue: RevenueEntity)

    @Query("DELETE FROM receipts WHERE id = :id")
    suspend fun deleteReceipt(id: Long)
}

@Database(
    entities = [ReceiptEntity::class, RevenueEntity::class],
    version = 1,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun dao(): DailyOfficeDao

    companion object {
        @Volatile private var INSTANCE: AppDatabase? = null

        fun get(context: Context): AppDatabase =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "dailyoffice.db"
                ).build().also { INSTANCE = it }
            }
    }
}
