package com.example.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "portfolio_positions")
data class PortfolioPositionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val importBatchId: Long? = null,
    val portfolioType: String, // "REAL" or "SIMULATION"
    val ticker: String,
    val name: String,
    val assetClass: String, // AssetClass.name
    val quantity: Double,
    val averagePrice: Double,
    val currentPrice: Double,
    val hasMarketPrice: Boolean,
    val sector: String,
    val dataComDay: Int? = null,
    val isCyclical: Boolean = true,
    val dataSourceLabel: String,
    val isDemo: Boolean = false,
    val updatedAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "portfolio_operations")
data class PortfolioOperationEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val portfolioType: String,
    val ticker: String,
    val operationType: String, // OperationType.name
    val quantity: Double,
    val unitPrice: Double,
    val totalValue: Double,
    val dateIso: String,
    val notes: String,
    val createdAt: Long = System.currentTimeMillis(),
    val externalRefId: String = "",
    val brokerAccount: String = ""
)

@Entity(tableName = "import_history")
data class ImportHistoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val fileName: String,
    val fileType: String, // "CSV", "XLSX", "DEMO"
    val importedAt: Long = System.currentTimeMillis(),
    val recordCount: Int,
    val cashBalanceBrl: Double,
    val isActiveRealReference: Boolean,
    val isDemoData: Boolean = false,
    val notes: String
)

@Entity(tableName = "strategy_criteria")
data class StrategyCriteriaEntity(
    @PrimaryKey val id: Int = 1,
    val minDyPercent: Double = 6.0,
    val maxPvp: Double = 1.25,
    val minDailyLiquidityMillions: Double = 1.5,
    val maxInadimplenciaPercent: Double = 3.0,
    val minRoePercent: Double = 12.0,
    val minDividendConsistencyMonths: Int = 6,
    val updatedAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "strategy_change_log")
data class StrategyChangeLogEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val changedAt: Long = System.currentTimeMillis(),
    val summary: String,
    val details: String
)

@Entity(tableName = "crypto_virtual_positions")
data class CryptoVirtualPositionEntity(
    @PrimaryKey val ticker: String,
    val name: String,
    val allocationBrl: Double,
    val entryPriceBrl: Double,
    val currentPriceBrl: Double,
    val updatedAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "saved_ai_recommendations")
data class SavedAiRecommendationEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val agentRole: String,
    val ticker: String,
    val conclusion: String,
    val confidence: Int,
    val thesis: String,
    val usedPersonalData: Boolean,
    val createdAt: Long = System.currentTimeMillis(),
    val sourceLabel: String = "",
    val dataBase: String = "",
    val indicatorsSummary: String = "",
    val risksSummary: String = "",
    val scenariosSummary: String = "",
    val isDemoPlaceholder: Boolean = false
)

@Entity(tableName = "ai_chat_messages")
data class ChatMessageEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val senderRole: String, // "USER" or "MODEL"
    val agentRole: String, // AiAgentRole.name
    val modelName: String,
    val content: String,
    val usedPersonalData: Boolean = false,
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "price_target_alerts")
data class PriceTargetAlertEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val ticker: String,
    val targetPriceBrl: Double,
    val condition: String = "AT_OR_BELOW",
    val isEnabled: Boolean = true,
    val hasTriggered: Boolean = false,
    val triggeredAt: Long? = null,
    val lastEvaluatedPriceBrl: Double? = null,
    val lastEvaluatedAt: Long? = null,
    val notes: String = "",
    val createdAt: Long = System.currentTimeMillis(),
    val pendingNotification: Boolean = false,
    val notificationDelivered: Boolean = false,
    val offlineQueuedCheck: Boolean = false,
    val lastCheckStatus: String = "Monitorando"
)

@Entity(tableName = "cached_market_quotes")
data class CachedMarketQuoteEntity(
    @PrimaryKey val ticker: String,
    val companyName: String,
    val price: Double,
    val changePercent1d: Double,
    val dy12m: Double? = null,
    val pvp: Double? = null,
    val pl: Double? = null,
    val roe: Double? = null,
    val dailyVolumeBrlMillions: Double? = null,
    val lastDividendBrl: Double? = null,
    val dataComDay: Int? = null,
    val paymentDay: Int? = null,
    val primarySourceLabel: String,
    val marketTimestampLabel: String,
    val fetchedAtMillis: Long
)
