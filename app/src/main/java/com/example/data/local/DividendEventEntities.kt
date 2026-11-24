package com.example.data.local

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Evento de provento com ciclo de vida completo e status mutuamente exclusivos:
 * RECEIVED (Recebido), CONFIRMED (Confirmado), PROJECTED (Projetado) e CANCELLED (Cancelado).
 * Suporta exclusão lógica (soft-delete) e proteção contra sobrescrita silenciosa de edições manuais.
 */
@Entity(
    tableName = "dividend_events",
    indices = [
        Index(value = ["assetTicker"]),
        Index(value = ["status"]),
        Index(value = ["paymentDate"]),
        Index(value = ["dedupKey"])
    ]
)
data class DividendEventEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val assetTicker: String,
    val eventType: String = "DIVIDENDO",
    val status: String = "RECEIVED",
    val exDate: String = "",
    val paymentDate: String = "",
    val unitAmount: Double = 0.0,
    val quantityReference: Double = 0.0,
    val totalAmount: Double = 0.0,
    val source: String = "Manual",
    val sourceReference: String = "",
    val dedupKey: String = "",
    val isRecurring: Boolean = false,
    val estimated: Boolean = false,
    val userEdited: Boolean = false,
    val syncDivergenceNote: String = "",
    val notes: String = "",
    val legacyOperationId: Long? = null,
    val isDeleted: Boolean = false,
    val deletedAt: Long? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

/**
 * Histórico de auditoria global para eventos de proventos, operações e posições.
 * Registra estado anterior/novo, motivo, origem e payload JSON para restauração segura.
 */
@Entity(
    tableName = "financial_audit_log",
    indices = [
        Index(value = ["entityType"]),
        Index(value = ["ticker"]),
        Index(value = ["changedAt"])
    ]
)
data class FinancialAuditLogEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val entityType: String,
    val entityId: Long,
    val ticker: String,
    val actionType: String,
    val previousStatus: String = "",
    val newStatus: String = "",
    val previousAmount: Double? = null,
    val newAmount: Double? = null,
    val previousSummary: String = "",
    val newSummary: String = "",
    val restorePayloadJson: String = "",
    val source: String = "Usuário",
    val reason: String = "",
    val changedAt: Long = System.currentTimeMillis()
)
