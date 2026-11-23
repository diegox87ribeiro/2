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
        ChatMessageEntity::class,
        PriceTargetAlertEntity::class,
        CachedMarketQuoteEntity::class,
        Ativo::class,
        Transacao::class
    ],
    version = 7,
    exportSchema = false
)
abstract class InvestDatabase : RoomDatabase() {
    abstract fun portfolioDao(): PortfolioDao
    abstract fun carteiraLocalDao(): CarteiraLocalDao
    abstract fun strategyDao(): StrategyDao
    abstract fun cryptoVirtualDao(): CryptoVirtualDao
    abstract fun aiRecommendationDao(): AiRecommendationDao
    abstract fun chatMessageDao(): ChatMessageDao
    abstract fun priceTargetAlertDao(): PriceTargetAlertDao
    abstract fun cachedMarketQuoteDao(): CachedMarketQuoteDao

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

        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE portfolio_operations ADD COLUMN externalRefId TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE portfolio_operations ADD COLUMN brokerAccount TEXT NOT NULL DEFAULT ''")
            }
        }

        val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS price_target_alerts (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        ticker TEXT NOT NULL,
                        targetPriceBrl REAL NOT NULL,
                        condition TEXT NOT NULL DEFAULT 'AT_OR_BELOW',
                        isEnabled INTEGER NOT NULL DEFAULT 1,
                        hasTriggered INTEGER NOT NULL DEFAULT 0,
                        triggeredAt INTEGER,
                        lastEvaluatedPriceBrl REAL,
                        lastEvaluatedAt INTEGER,
                        notes TEXT NOT NULL DEFAULT '',
                        createdAt INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
            }
        }

        val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE price_target_alerts ADD COLUMN pendingNotification INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE price_target_alerts ADD COLUMN notificationDelivered INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE price_target_alerts ADD COLUMN offlineQueuedCheck INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE price_target_alerts ADD COLUMN lastCheckStatus TEXT NOT NULL DEFAULT 'Monitorando'")
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS cached_market_quotes (
                        ticker TEXT NOT NULL PRIMARY KEY,
                        companyName TEXT NOT NULL,
                        price REAL NOT NULL,
                        changePercent1d REAL NOT NULL,
                        dy12m REAL,
                        pvp REAL,
                        pl REAL,
                        roe REAL,
                        dailyVolumeBrlMillions REAL,
                        lastDividendBrl REAL,
                        dataComDay INTEGER,
                        paymentDay INTEGER,
                        primarySourceLabel TEXT NOT NULL,
                        marketTimestampLabel TEXT NOT NULL,
                        fetchedAtMillis INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
            }
        }

        val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `ativos` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `ticker` TEXT NOT NULL,
                        `nome` TEXT NOT NULL,
                        `classe` TEXT NOT NULL,
                        `setor` TEXT NOT NULL,
                        `quantidade` REAL NOT NULL,
                        `precoMedio` REAL NOT NULL,
                        `precoAtual` REAL NOT NULL,
                        `possuiCotacaoMercado` INTEGER NOT NULL,
                        `dataComDia` INTEGER,
                        `dataExDia` INTEGER,
                        `origemDados` TEXT NOT NULL,
                        `atualizadoEm` INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_ativos_ticker` ON `ativos` (`ticker`)")

                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `transacoes` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `ativoTicker` TEXT NOT NULL,
                        `tipo` TEXT NOT NULL,
                        `quantidade` REAL NOT NULL,
                        `precoUnitario` REAL NOT NULL,
                        `valorTotal` REAL NOT NULL,
                        `taxas` REAL NOT NULL,
                        `dataIso` TEXT NOT NULL,
                        `observacoes` TEXT NOT NULL,
                        `referenciaExterna` TEXT NOT NULL,
                        `criadoEm` INTEGER NOT NULL,
                        FOREIGN KEY(`ativoTicker`) REFERENCES `ativos`(`ticker`) ON UPDATE CASCADE ON DELETE CASCADE
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_transacoes_ativoTicker` ON `transacoes` (`ativoTicker`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_transacoes_dataIso` ON `transacoes` (`dataIso`)")

                // Migra posições e operações existentes da Carteira Real para ativos e transacoes
                db.execSQL(
                    """
                    INSERT OR IGNORE INTO `ativos` (
                        `ticker`, `nome`, `classe`, `setor`, `quantidade`, `precoMedio`,
                        `precoAtual`, `possuiCotacaoMercado`, `dataComDia`, `dataExDia`, `origemDados`, `atualizadoEm`
                    )
                    SELECT
                        UPPER(TRIM(ticker)),
                        name,
                        assetClass,
                        sector,
                        quantity,
                        averagePrice,
                        currentPrice,
                        hasMarketPrice,
                        dataComDay,
                        NULL,
                        dataSourceLabel,
                        updatedAt
                    FROM portfolio_positions
                    WHERE portfolioType = 'REAL' AND TRIM(ticker) != ''
                    """.trimIndent()
                )

                db.execSQL(
                    """
                    INSERT INTO `transacoes` (
                        `ativoTicker`, `tipo`, `quantidade`, `precoUnitario`, `valorTotal`,
                        `taxas`, `dataIso`, `observacoes`, `referenciaExterna`, `criadoEm`
                    )
                    SELECT
                        UPPER(TRIM(op.ticker)),
                        op.operationType,
                        op.quantity,
                        op.unitPrice,
                        op.totalValue,
                        0.0,
                        op.dateIso,
                        op.notes,
                        op.externalRefId,
                        op.createdAt
                    FROM portfolio_operations op
                    INNER JOIN `ativos` a ON a.ticker = UPPER(TRIM(op.ticker))
                    WHERE op.portfolioType = 'REAL'
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
                    .addMigrations(
                        MIGRATION_1_2,
                        MIGRATION_2_3,
                        MIGRATION_3_4,
                        MIGRATION_4_5,
                        MIGRATION_5_6,
                        MIGRATION_6_7
                    )
                    .fallbackToDestructiveMigration(dropAllTables = true)
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
