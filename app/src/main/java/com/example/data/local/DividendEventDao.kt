package com.example.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface DividendEventDao {
    @Query("SELECT * FROM dividend_events ORDER BY updatedAt DESC, id DESC")
    fun observeAllDividendEvents(): Flow<List<DividendEventEntity>>

    @Query("SELECT * FROM dividend_events WHERE isDeleted = 0 ORDER BY updatedAt DESC, id DESC")
    suspend fun getActiveDividendEvents(): List<DividendEventEntity>

    @Query("SELECT * FROM dividend_events ORDER BY updatedAt DESC, id DESC")
    suspend fun getAllDividendEvents(): List<DividendEventEntity>

    @Query("SELECT * FROM dividend_events WHERE id = :id LIMIT 1")
    suspend fun getById(id: Long): DividendEventEntity?

    @Query("SELECT * FROM dividend_events WHERE legacyOperationId = :legacyOpId LIMIT 1")
    suspend fun getByLegacyOperationId(legacyOpId: Long): DividendEventEntity?

    @Query("SELECT * FROM dividend_events WHERE dedupKey = :dedupKey AND dedupKey != '' LIMIT 1")
    suspend fun findByDedupKey(dedupKey: String): DividendEventEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(event: DividendEventEntity): Long

    @Update
    suspend fun update(event: DividendEventEntity)

    @Query(
        """
        UPDATE dividend_events
        SET isDeleted = 1,
            deletedAt = :deletedAt,
            updatedAt = :updatedAt
        WHERE id = :id
        """
    )
    suspend fun softDelete(id: Long, deletedAt: Long, updatedAt: Long)

    @Query(
        """
        UPDATE dividend_events
        SET isDeleted = 0,
            deletedAt = NULL,
            status = :restoredStatus,
            updatedAt = :updatedAt
        WHERE id = :id
        """
    )
    suspend fun restore(id: Long, restoredStatus: String, updatedAt: Long)

    @Query(
        """
        UPDATE dividend_events
        SET assetTicker = :newTicker,
            userEdited = 1,
            updatedAt = :updatedAt
        WHERE UPPER(TRIM(assetTicker)) = UPPER(TRIM(:oldTicker))
        """
    )
    suspend fun renameTicker(oldTicker: String, newTicker: String, updatedAt: Long): Int

    @Query("SELECT * FROM financial_audit_log ORDER BY changedAt DESC, id DESC LIMIT :limit")
    fun observeAuditLogs(limit: Int = 150): Flow<List<FinancialAuditLogEntity>>

    @Query("SELECT * FROM financial_audit_log WHERE id = :id LIMIT 1")
    suspend fun getAuditLogById(id: Long): FinancialAuditLogEntity?

    @Insert
    suspend fun insertAuditLog(log: FinancialAuditLogEntity): Long
}
