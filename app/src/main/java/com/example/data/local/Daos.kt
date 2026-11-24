package com.example.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
interface PortfolioDao {
    @Query("SELECT * FROM portfolio_positions ORDER BY portfolioType ASC, ticker ASC")
    fun observeAllPositions(): Flow<List<PortfolioPositionEntity>>

    @Query("SELECT * FROM portfolio_operations ORDER BY createdAt DESC")
    fun observeAllOperations(): Flow<List<PortfolioOperationEntity>>

    @Query("SELECT * FROM import_history ORDER BY importedAt DESC")
    fun observeImportHistory(): Flow<List<ImportHistoryEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPositions(items: List<PortfolioPositionEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPosition(item: PortfolioPositionEntity): Long

    @Query(
        """
        UPDATE portfolio_positions
        SET ticker = :ticker,
            name = :name,
            assetClass = :assetClass,
            quantity = :quantity,
            averagePrice = :averagePrice,
            currentPrice = :currentPrice,
            hasMarketPrice = :hasMarketPrice,
            sector = :sector,
            dataComDay = :dataComDay,
            isCyclical = :isCyclical,
            dataSourceLabel = :dataSourceLabel,
            updatedAt = :updatedAt
        WHERE id = :id
        """
    )
    suspend fun updatePositionById(
        id: Long,
        ticker: String,
        name: String,
        assetClass: String,
        quantity: Double,
        averagePrice: Double,
        currentPrice: Double,
        hasMarketPrice: Boolean,
        sector: String,
        dataComDay: Int?,
        dataSourceLabel: String,
        updatedAt: Long = System.currentTimeMillis(),
        isCyclical: Boolean = true
    )

    @Query(
        """
        UPDATE portfolio_positions
        SET currentPrice = :currentPrice,
            hasMarketPrice = 1,
            updatedAt = :updatedAt
        WHERE UPPER(ticker) = UPPER(:ticker)
        """
    )
    suspend fun updateCurrentPriceByTicker(
        ticker: String,
        currentPrice: Double,
        updatedAt: Long = System.currentTimeMillis()
    ): Int

    @Query(
        """
        UPDATE portfolio_positions
        SET dataComDay = :dataComDay,
            updatedAt = :updatedAt
        WHERE UPPER(ticker) = UPPER(:ticker) AND dataComDay IS NULL
        """
    )
    suspend fun updateDataComIfMissingByTicker(
        ticker: String,
        dataComDay: Int,
        updatedAt: Long = System.currentTimeMillis()
    ): Int

    @Query("DELETE FROM portfolio_positions WHERE id = :id")
    suspend fun deletePositionById(id: Long)

    @Query("DELETE FROM portfolio_positions WHERE portfolioType = :portfolioType")
    suspend fun clearPositionsByType(portfolioType: String)

    @Query("SELECT * FROM portfolio_positions WHERE id = :id LIMIT 1")
    suspend fun getPositionById(id: Long): PortfolioPositionEntity?

    @Query("SELECT * FROM portfolio_positions WHERE portfolioType = :portfolioType")
    suspend fun getPositionsByType(portfolioType: String): List<PortfolioPositionEntity>

    @Query("SELECT externalRefId FROM portfolio_operations WHERE externalRefId != ''")
    suspend fun getExistingExternalRefIds(): List<String>

    @Query("SELECT * FROM portfolio_operations WHERE id = :id LIMIT 1")
    suspend fun getOperationById(id: Long): PortfolioOperationEntity?

    @Query(
        """
        SELECT * FROM portfolio_operations
        WHERE portfolioType = :portfolioType
          AND UPPER(TRIM(ticker)) = UPPER(TRIM(:ticker))
        ORDER BY createdAt ASC, id ASC
        """
    )
    suspend fun getOperationsByTicker(portfolioType: String, ticker: String): List<PortfolioOperationEntity>

    @Insert
    suspend fun insertOperation(op: PortfolioOperationEntity): Long

    @Insert
    suspend fun insertOperations(ops: List<PortfolioOperationEntity>)

    @Query(
        """
        UPDATE portfolio_operations
        SET ticker = :ticker,
            operationType = :operationType,
            quantity = :quantity,
            unitPrice = :unitPrice,
            totalValue = :totalValue,
            dateIso = :dateIso,
            notes = :notes
        WHERE id = :id
        """
    )
    suspend fun updateOperationById(
        id: Long,
        ticker: String,
        operationType: String,
        quantity: Double,
        unitPrice: Double,
        totalValue: Double,
        dateIso: String,
        notes: String
    )

    @Query(
        """
        UPDATE portfolio_operations
        SET ticker = :newTicker
        WHERE UPPER(TRIM(ticker)) = UPPER(TRIM(:oldTicker))
          AND operationType = 'PROVENTO'
        """
    )
    suspend fun renameProventoOperationsTicker(oldTicker: String, newTicker: String): Int

    @Query("DELETE FROM portfolio_operations WHERE portfolioType = :portfolioType")
    suspend fun clearOperationsByType(portfolioType: String)

    @Query("DELETE FROM portfolio_operations WHERE id = :id")
    suspend fun deleteOperationById(id: Long)

    @Insert
    suspend fun insertImportHistory(history: ImportHistoryEntity): Long

    @Query("UPDATE import_history SET isActiveRealReference = 0")
    suspend fun clearActiveRealReference()

    @Transaction
    suspend fun replaceRealPortfolioFromImport(
        history: ImportHistoryEntity,
        positions: List<PortfolioPositionEntity>
    ): Long {
        clearActiveRealReference()
        val batchId = insertImportHistory(history.copy(isActiveRealReference = true))
        clearPositionsByType("REAL")
        insertPositions(positions.map { it.copy(importBatchId = batchId, portfolioType = "REAL") })
        return batchId
    }
}

@Dao
interface StrategyDao {
    @Query("SELECT * FROM strategy_criteria WHERE id = 1")
    fun observeCriteria(): Flow<StrategyCriteriaEntity?>

    @Query("SELECT * FROM strategy_change_log ORDER BY changedAt DESC")
    fun observeChangeLog(): Flow<List<StrategyChangeLogEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertCriteria(criteria: StrategyCriteriaEntity)

    @Insert
    suspend fun insertChangeLog(log: StrategyChangeLogEntity)
}

@Dao
interface CryptoVirtualDao {
    @Query("SELECT * FROM crypto_virtual_positions ORDER BY allocationBrl DESC")
    fun observePositions(): Flow<List<CryptoVirtualPositionEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(items: List<CryptoVirtualPositionEntity>)

    @Query("DELETE FROM crypto_virtual_positions")
    suspend fun clearAll()
}

@Dao
interface AiRecommendationDao {
    @Query("SELECT * FROM saved_ai_recommendations ORDER BY createdAt DESC LIMIT 30")
    fun observeSaved(): Flow<List<SavedAiRecommendationEntity>>

    @Insert
    suspend fun insert(item: SavedAiRecommendationEntity)
}

@Dao
interface ChatMessageDao {
    @Query("SELECT * FROM ai_chat_messages ORDER BY createdAt ASC, id ASC")
    fun observeMessages(): Flow<List<ChatMessageEntity>>

    @Insert
    suspend fun insert(message: ChatMessageEntity): Long

    @Query("DELETE FROM ai_chat_messages")
    suspend fun clearAll()
}

@Dao
interface PriceTargetAlertDao {
    @Query("SELECT * FROM price_target_alerts ORDER BY hasTriggered ASC, createdAt DESC")
    fun observeAll(): Flow<List<PriceTargetAlertEntity>>

    @Query("SELECT * FROM price_target_alerts WHERE isEnabled = 1 AND hasTriggered = 0")
    suspend fun getActiveAlerts(): List<PriceTargetAlertEntity>

    @Query("SELECT * FROM price_target_alerts WHERE hasTriggered = 1 AND pendingNotification = 1")
    suspend fun getPendingNotificationAlerts(): List<PriceTargetAlertEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(alert: PriceTargetAlertEntity): Long

    @Query("UPDATE price_target_alerts SET isEnabled = :enabled WHERE id = :id")
    suspend fun setEnabled(id: Long, enabled: Boolean)

    @Query(
        """
        UPDATE price_target_alerts
        SET isEnabled = 1,
            hasTriggered = 0,
            triggeredAt = NULL,
            pendingNotification = 0,
            notificationDelivered = 0,
            offlineQueuedCheck = 0,
            lastCheckStatus = 'Monitorando'
        WHERE id = :id
        """
    )
    suspend fun rearmAlert(id: Long)

    @Query(
        """
        UPDATE price_target_alerts
        SET hasTriggered = 1,
            triggeredAt = :triggeredAt,
            lastEvaluatedPriceBrl = :evaluatedPriceBrl,
            lastEvaluatedAt = :triggeredAt,
            offlineQueuedCheck = 0,
            pendingNotification = :pendingNotification,
            notificationDelivered = :notificationDelivered,
            lastCheckStatus = :status
        WHERE id = :id
        """
    )
    suspend fun markTriggered(
        id: Long,
        evaluatedPriceBrl: Double,
        triggeredAt: Long,
        pendingNotification: Boolean = false,
        notificationDelivered: Boolean = true,
        status: String = "Meta atingida • Push entregue"
    )

    @Query(
        """
        UPDATE price_target_alerts
        SET pendingNotification = 0,
            notificationDelivered = 1,
            lastCheckStatus = :status
        WHERE id = :id
        """
    )
    suspend fun markNotificationDelivered(
        id: Long,
        status: String = "Meta atingida • Push entregue"
    )

    @Query(
        """
        UPDATE price_target_alerts
        SET offlineQueuedCheck = 1,
            lastCheckStatus = :status
        WHERE id IN (:ids)
        """
    )
    suspend fun markOfflineQueued(
        ids: List<Long>,
        status: String = "Offline • Aguardando conexão (Room)"
    )

    @Query(
        """
        UPDATE price_target_alerts
        SET lastEvaluatedPriceBrl = :evaluatedPriceBrl,
            lastEvaluatedAt = :evaluatedAt,
            offlineQueuedCheck = 0,
            lastCheckStatus = :status
        WHERE id = :id
        """
    )
    suspend fun updateLastEvaluated(
        id: Long,
        evaluatedPriceBrl: Double,
        evaluatedAt: Long,
        status: String = "Monitorando"
    )

    @Query("DELETE FROM price_target_alerts WHERE id = :id")
    suspend fun deleteById(id: Long)
}

@Dao
interface CachedMarketQuoteDao {
    @Query("SELECT * FROM cached_market_quotes")
    fun observeAll(): Flow<List<CachedMarketQuoteEntity>>

    @Query("SELECT * FROM cached_market_quotes")
    suspend fun getAllOnce(): List<CachedMarketQuoteEntity>

    @Query("SELECT * FROM cached_market_quotes WHERE ticker = :ticker LIMIT 1")
    suspend fun getByTicker(ticker: String): CachedMarketQuoteEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(quote: CachedMarketQuoteEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(quotes: List<CachedMarketQuoteEntity>)
}

@Dao
interface SmartAlertDao {
    // --- Variações Bruscas ---
    @Query("SELECT * FROM asset_swing_alerts ORDER BY isEnabled DESC, createdAt DESC")
    fun observeSwingAlerts(): Flow<List<AssetSwingAlertEntity>>

    @Query("SELECT * FROM asset_swing_alerts WHERE isEnabled = 1")
    suspend fun getActiveSwingAlerts(): List<AssetSwingAlertEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSwingAlert(alert: AssetSwingAlertEntity): Long

    @Query("UPDATE asset_swing_alerts SET isEnabled = :enabled WHERE id = :id")
    suspend fun setSwingAlertEnabled(id: Long, enabled: Boolean)

    @Query(
        """
        UPDATE asset_swing_alerts
        SET lastChangePercent = :changePercent,
            lastPriceBrl = :priceBrl,
            lastEvaluatedAt = :evaluatedAt
        WHERE id = :id
        """
    )
    suspend fun updateSwingLastEvaluated(
        id: Long,
        changePercent: Double,
        priceBrl: Double,
        evaluatedAt: Long
    )

    @Query(
        """
        UPDATE asset_swing_alerts
        SET lastChangePercent = :changePercent,
            lastPriceBrl = :priceBrl,
            lastEvaluatedAt = :triggeredAt,
            lastTriggeredAt = :triggeredAt,
            lastTriggeredDayKey = :dayKey
        WHERE id = :id
        """
    )
    suspend fun markSwingTriggered(
        id: Long,
        changePercent: Double,
        priceBrl: Double,
        triggeredAt: Long,
        dayKey: String
    )

    @Query(
        """
        UPDATE asset_swing_alerts
        SET lastTriggeredDayKey = '',
            isEnabled = 1
        WHERE id = :id
        """
    )
    suspend fun resetSwingCooldown(id: Long)

    @Query("DELETE FROM asset_swing_alerts WHERE id = :id")
    suspend fun deleteSwingAlertById(id: Long)

    // --- Metas de Investimento ---
    @Query("SELECT * FROM investment_goals ORDER BY hasTriggered ASC, createdAt DESC")
    fun observeInvestmentGoals(): Flow<List<InvestmentGoalEntity>>

    @Query("SELECT * FROM investment_goals WHERE isEnabled = 1 AND hasTriggered = 0")
    suspend fun getActiveInvestmentGoals(): List<InvestmentGoalEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertInvestmentGoal(goal: InvestmentGoalEntity): Long

    @Query("UPDATE investment_goals SET isEnabled = :enabled WHERE id = :id")
    suspend fun setInvestmentGoalEnabled(id: Long, enabled: Boolean)

    @Query(
        """
        UPDATE investment_goals
        SET lastCurrentValue = :currentValue
        WHERE id = :id
        """
    )
    suspend fun updateGoalProgress(id: Long, currentValue: Double)

    @Query(
        """
        UPDATE investment_goals
        SET hasTriggered = 1,
            triggeredAt = :triggeredAt,
            lastCurrentValue = :currentValue
        WHERE id = :id
        """
    )
    suspend fun markGoalTriggered(id: Long, currentValue: Double, triggeredAt: Long)

    @Query(
        """
        UPDATE investment_goals
        SET hasTriggered = 0,
            triggeredAt = NULL,
            isEnabled = 1
        WHERE id = :id
        """
    )
    suspend fun rearmInvestmentGoal(id: Long)

    @Query("DELETE FROM investment_goals WHERE id = :id")
    suspend fun deleteInvestmentGoalById(id: Long)

    // --- Histórico de Notificações Locais ---
    @Query("SELECT * FROM alert_notification_history ORDER BY createdAt DESC LIMIT 80")
    fun observeNotificationHistory(): Flow<List<AlertNotificationHistoryEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertNotificationHistory(entry: AlertNotificationHistoryEntity): Long

    @Query("DELETE FROM alert_notification_history")
    suspend fun clearNotificationHistory()
}

