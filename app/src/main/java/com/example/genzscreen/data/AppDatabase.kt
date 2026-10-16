package com.example.genzscreen.data

import android.content.Context
import androidx.room.*
import kotlinx.coroutines.flow.Flow

// 1. Entity for Daily Usage
@Entity(tableName = "daily_usage", primaryKeys = ["date", "packageName"])
data class DailyUsage(
    val date: String, // Format: YYYY-MM-DD
    val packageName: String, // e.g., "com.facebook.katana"
    val reelCount: Int = 0,
    val watchTimeMillis: Long = 0L
)

// 2. Entity for Limits Configured by User
@Entity(tableName = "app_limits")
data class AppLimit(
    @PrimaryKey val packageName: String,
    val maxReels: Int? = null,
    val maxTimeMillis: Long? = null
)

// 3. Data Access Object (DAO)
@Dao
interface UsageDao {
    // Usage Queries
    @Query("SELECT * FROM daily_usage WHERE date = :date")
    fun getUsageForDate(date: String): Flow<List<DailyUsage>>

    @Query("SELECT * FROM daily_usage WHERE date >= :startDate AND date <= :endDate AND packageName = :packageName")
    fun getUsageForAppDateRange(packageName: String, startDate: String, endDate: String): Flow<List<DailyUsage>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertUsage(usage: DailyUsage)

    @Query("UPDATE daily_usage SET reelCount = reelCount + :countToAdd, watchTimeMillis = watchTimeMillis + :timeToAdd WHERE date = :date AND packageName = :packageName")
    suspend fun updateUsage(date: String, packageName: String, countToAdd: Int, timeToAdd: Long)

    // Limit Queries
    @Query("SELECT * FROM app_limits")
    fun getAllLimits(): Flow<List<AppLimit>>

    @Query("SELECT * FROM app_limits WHERE packageName = :packageName")
    suspend fun getLimitForApp(packageName: String): AppLimit?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertLimit(limit: AppLimit)

    @Query("DELETE FROM app_limits WHERE packageName = :packageName")
    suspend fun deleteLimit(packageName: String)
}

// 4. Room Database Setup
@Database(entities = [DailyUsage::class, AppLimit::class], version = 1, exportSchema = false)
abstract class GenZDatabase : RoomDatabase() {
    abstract fun usageDao(): UsageDao

    companion object {
        @Volatile
        private var INSTANCE: GenZDatabase? = null

        fun getDatabase(context: Context): GenZDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    GenZDatabase::class.java,
                    "genz_database"
                ).build()
                INSTANCE = instance
                instance
            }
        }
    }
}
