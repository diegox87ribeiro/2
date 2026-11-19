package com.example.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        PortfolioPositionEntity::class,
        PortfolioOperationEntity::class,
        ImportHistoryEntity::class,
        StrategyCriteriaEntity::class,
        StrategyChangeLogEntity::class,
        CryptoVirtualPositionEntity::class,
        SavedAiRecommendationEntity::class,
        ChatMessageEntity::class
    ],
    version = 3,
    exportSchema = false
)
abstract class InvestDatabase : RoomDatabase() {
    abstract fun portfolioDao(): PortfolioDao
    abstract fun strategyDao(): StrategyDao
    abstract fun cryptoVirtualDao(): CryptoVirtualDao
    abstract fun aiRecommendationDao(): AiRecommendationDao
    abstract fun chatMessageDao(): ChatMessageDao

    companion object {
        @Volatile
        private var INSTANCE: InvestDatabase? = null

        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE saved_ai_recommendations ADD COLUMN sourceLabel TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE saved_ai_recommendations ADD COLUMN dataBase TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE saved_ai_recommendations ADD COLUMN indicatorsSummary TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE saved_ai_recommendations ADD COLUMN risksSummary TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE saved_ai_recommendations ADD COLUMN scenariosSummary TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE saved_ai_recommendations ADD COLUMN isDemoPlaceholder INTEGER NOT NULL DEFAULT 0")
            }
        }

        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS ai_chat_messages (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        senderRole TEXT NOT NULL,
                        agentRole TEXT NOT NULL,
                        modelName TEXT NOT NULL,
                        content TEXT NOT NULL,
                        usedPersonalData INTEGER NOT NULL DEFAULT 0,
                        createdAt INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
            }
        }

        fun getInstance(context: Context): InvestDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    InvestDatabase::class.java,
                    "invest_ai_local.db"
                )
                    .addMigrations(MIGRATION_1_2, MIGRATION_2_3)
                    .fallbackToDestructiveMigration(dropAllTables = true)
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
