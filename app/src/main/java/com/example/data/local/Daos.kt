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
        updatedAt: Long = System.currentTimeMillis()
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

    @Query("DELETE FROM portfolio_positions WHERE id = :id")
    suspend fun deletePositionById(id: Long)

    @Query("DELETE FROM portfolio_positions WHERE portfolioType = :portfolioType")
    suspend fun clearPositionsByType(portfolioType: String)

    @Query("SELECT * FROM portfolio_positions WHERE portfolioType = :portfolioType")
    suspend fun getPositionsByType(portfolioType: String): List<PortfolioPositionEntity>

    @Query("SELECT externalRefId FROM portfolio_operations WHERE externalRefId != ''")
    suspend fun getExistingExternalRefIds(): List<String>

    @Insert
    suspend fun insertOperation(op: PortfolioOperationEntity): Long

    @Insert
    suspend fun insertOperations(ops: List<PortfolioOperationEntity>)

    @Query("DELETE FROM portfolio_operations WHERE portfolioType = :portfolioType")
    suspend fun clearOperationsByType(portfolioType: String)

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

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(alert: PriceTargetAlertEntity): Long

    @Query("UPDATE price_target_alerts SET isEnabled = :enabled WHERE id = :id")
    suspend fun setEnabled(id: Long, enabled: Boolean)

    @Query(
        """
        UPDATE price_target_alerts
        SET isEnabled = 1,
            hasTriggered = 0,
            triggeredAt = NULL
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
            lastEvaluatedAt = :triggeredAt
        WHERE id = :id
        """
    )
    suspend fun markTriggered(id: Long, evaluatedPriceBrl: Double, triggeredAt: Long)

    @Query(
        """
        UPDATE price_target_alerts
        SET lastEvaluatedPriceBrl = :evaluatedPriceBrl,
            lastEvaluatedAt = :evaluatedAt
        WHERE id = :id
        """
    )
    suspend fun updateLastEvaluated(id: Long, evaluatedPriceBrl: Double, evaluatedAt: Long)

    @Query("DELETE FROM price_target_alerts WHERE id = :id")
    suspend fun deleteById(id: Long)
}


