package com.example.data.repository

import com.example.data.local.DividendEventDao
import com.example.data.local.DividendEventEntity
import com.example.data.local.FinancialAuditLogEntity
import com.example.data.local.PortfolioDao
import com.example.data.local.PortfolioOperationEntity
import com.example.data.local.PortfolioPositionEntity
import com.example.domain.model.DividendDisplayEvent
import com.example.domain.model.DividendEventType
import com.example.domain.model.DividendHistoryEngine
import com.example.domain.model.DividendStatus
import com.example.domain.model.LiveMarketQuote
import com.example.domain.model.OperationType
import com.example.domain.model.PortfolioItemView
import com.example.domain.model.PortfolioType
import org.json.JSONObject
import java.util.Locale
import kotlin.math.abs

data class DividendSyncResult(
    val createdCount: Int = 0,
    val updatedCount: Int = 0,
    val divergencesCount: Int = 0,
    val checkedAssetsCount: Int = 0
)

class DividendManagementHelper(
    private val portfolioDao: PortfolioDao,
    private val dividendEventDao: DividendEventDao,
    private val onMirrorRealPositions: suspend () -> Unit,
    private val onMirrorRealOperation: suspend (PortfolioOperationEntity) -> Unit
) {

    suspend fun mirrorLegacyProventoOperation(
        opId: Long,
        ticker: String,
        quantity: Double,
        unitPrice: Double,
        totalValue: Double,
        dateIso: String,
        notes: String
    ): Long {
        val cleanTicker = ticker.trim().uppercase(Locale.ROOT)
        val existing = dividendEventDao.getByLegacyOperationId(opId)
        val now = System.currentTimeMillis()
        val inferredType = DividendEventType.infer(null, notes)
        val dedup = "$cleanTicker|LEGACY|$opId"
        if (existing != null) {
            val updated = existing.copy(
                assetTicker = cleanTicker,
                eventType = inferredType.name,
                status = DividendStatus.RECEIVED.name,
                paymentDate = dateIso,
                unitAmount = unitPrice,
                quantityReference = quantity,
                totalAmount = totalValue,
                notes = notes,
                isDeleted = false,
                deletedAt = null,
                updatedAt = now
            )
            dividendEventDao.update(updated)
            return updated.id
        }
        val entity = DividendEventEntity(
            assetTicker = cleanTicker,
            eventType = inferredType.name,
            status = DividendStatus.RECEIVED.name,
            exDate = "",
            paymentDate = dateIso,
            unitAmount = unitPrice,
            quantityReference = quantity,
            totalAmount = totalValue,
            source = "Carteira",
            sourceReference = "legacy_op_$opId",
            dedupKey = dedup,
            isRecurring = false,
            estimated = false,
            userEdited = false,
            notes = notes,
            legacyOperationId = opId,
            createdAt = now,
            updatedAt = now
        )
        return dividendEventDao.insert(entity)
    }

    suspend fun saveDividendEvent(
        id: Long?,
        ticker: String,
        eventType: DividendEventType,
        status: DividendStatus,
        exDate: String,
        paymentDate: String,
        unitAmount: Double,
        quantityReference: Double,
        totalAmount: Double,
        source: String,
        notes: String,
        isRecurring: Boolean,
        reason: String = ""
    ): Long {
        val cleanTicker = ticker.trim().uppercase(Locale.ROOT)
        val now = System.currentTimeMillis()
        val resolvedQty = quantityReference.coerceAtLeast(0.0)
        val resolvedUnit = unitAmount.coerceAtLeast(0.0)
        val resolvedTotal = when {
            totalAmount > 0.0 -> totalAmount
            resolvedQty > 0.0 && resolvedUnit > 0.0 -> resolvedQty * resolvedUnit
            else -> 0.0
        }
        val effectiveUnit = if (resolvedUnit > 0.0) resolvedUnit
        else if (resolvedQty > 0.0) resolvedTotal / resolvedQty else resolvedTotal
        val effectiveQty = if (resolvedQty > 0.0) resolvedQty else 1.0
        val cleanEx = exDate.trim()
        val cleanPay = paymentDate.trim()
        val cleanSource = source.trim().ifBlank { "Manual" }
        val cleanNotes = notes.trim()
        val isEstimated = status == DividendStatus.PROJECTED

        if (id != null && id > 0L) {
            val old = dividendEventDao.getById(id)
            if (old != null) {
                var linkedOpId = old.legacyOperationId
                if (status == DividendStatus.RECEIVED) {
                    if (linkedOpId != null) {
                        portfolioDao.updateOperationById(
                            id = linkedOpId,
                            ticker = cleanTicker,
                            operationType = OperationType.PROVENTO.name,
                            quantity = effectiveQty,
                            unitPrice = effectiveUnit,
                            totalValue = resolvedTotal,
                            dateIso = cleanPay.ifBlank { old.paymentDate },
                            notes = buildOperationNotes(eventType, cleanNotes)
                        )
                    } else {
                        val opEntity = PortfolioOperationEntity(
                            portfolioType = PortfolioType.REAL.name,
                            ticker = cleanTicker,
                            operationType = OperationType.PROVENTO.name,
                            quantity = effectiveQty,
                            unitPrice = effectiveUnit,
                            totalValue = resolvedTotal,
                            dateIso = cleanPay.ifBlank { old.paymentDate },
                            notes = buildOperationNotes(eventType, cleanNotes)
                        )
                        linkedOpId = portfolioDao.insertOperation(opEntity)
                        onMirrorRealOperation(opEntity.copy(id = linkedOpId))
                    }
                } else if (linkedOpId != null) {
                    portfolioDao.deleteOperationById(linkedOpId)
                    linkedOpId = null
                }

                val updated = old.copy(
                    assetTicker = cleanTicker,
                    eventType = eventType.name,
                    status = status.name,
                    exDate = cleanEx,
                    paymentDate = cleanPay,
                    unitAmount = effectiveUnit,
                    quantityReference = effectiveQty,
                    totalAmount = resolvedTotal,
                    source = cleanSource,
                    dedupKey = DividendHistoryEngine.buildDedupKey(
                        ticker = cleanTicker,
                        eventType = eventType,
                        exDate = cleanEx,
                        paymentDate = cleanPay,
                        sourceReference = old.sourceReference
                    ),
                    isRecurring = isRecurring,
                    estimated = isEstimated,
                    userEdited = true,
                    syncDivergenceNote = "",
                    notes = cleanNotes,
                    legacyOperationId = linkedOpId,
                    isDeleted = false,
                    deletedAt = null,
                    updatedAt = now
                )
                dividendEventDao.update(updated)
                dividendEventDao.insertAuditLog(
                    FinancialAuditLogEntity(
                        entityType = "DIVIDEND_EVENT",
                        entityId = updated.id,
                        ticker = cleanTicker,
                        actionType = "UPDATE",
                        previousStatus = old.status,
                        newStatus = updated.status,
                        previousAmount = old.totalAmount,
                        newAmount = updated.totalAmount,
                        previousSummary = "${old.assetTicker} • ${old.eventType} • ${old.paymentDate} • R$ ${"%.2f".format(old.totalAmount)}",
                        newSummary = "${updated.assetTicker} • ${updated.eventType} • ${updated.paymentDate} • R$ ${"%.2f".format(updated.totalAmount)}",
                        source = "Usuário",
                        reason = reason.ifBlank { "Edição manual de provento" },
                        changedAt = now
                    )
                )
                return updated.id
            }
        }

        // Criação de novo evento de provento
        var linkedOpId: Long? = null
        if (status == DividendStatus.RECEIVED) {
            val opEntity = PortfolioOperationEntity(
                portfolioType = PortfolioType.REAL.name,
                ticker = cleanTicker,
                operationType = OperationType.PROVENTO.name,
                quantity = effectiveQty,
                unitPrice = effectiveUnit,
                totalValue = resolvedTotal,
                dateIso = cleanPay,
                notes = buildOperationNotes(eventType, cleanNotes)
            )
            linkedOpId = portfolioDao.insertOperation(opEntity)
            onMirrorRealOperation(opEntity.copy(id = linkedOpId))
        }

        val dedup = DividendHistoryEngine.buildDedupKey(
            ticker = cleanTicker,
            eventType = eventType,
            exDate = cleanEx,
            paymentDate = cleanPay,
            sourceReference = if (linkedOpId != null) "op_$linkedOpId" else "manual_$now"
        )
        val entity = DividendEventEntity(
            assetTicker = cleanTicker,
            eventType = eventType.name,
            status = status.name,
            exDate = cleanEx,
            paymentDate = cleanPay,
            unitAmount = effectiveUnit,
            quantityReference = effectiveQty,
            totalAmount = resolvedTotal,
            source = cleanSource,
            sourceReference = if (linkedOpId != null) "op_$linkedOpId" else "manual_$now",
            dedupKey = dedup,
            isRecurring = isRecurring,
            estimated = isEstimated,
            userEdited = true,
            notes = cleanNotes,
            legacyOperationId = linkedOpId,
            createdAt = now,
            updatedAt = now
        )
        val newId = dividendEventDao.insert(entity)
        dividendEventDao.insertAuditLog(
            FinancialAuditLogEntity(
                entityType = "DIVIDEND_EVENT",
                entityId = newId,
                ticker = cleanTicker,
                actionType = "CREATE",
                previousStatus = "",
                newStatus = status.name,
                previousAmount = null,
                newAmount = resolvedTotal,
                previousSummary = "",
                newSummary = "$cleanTicker • ${eventType.label} • $cleanPay • R$ ${"%.2f".format(resolvedTotal)}",
                source = cleanSource,
                reason = reason.ifBlank { "Cadastro manual de provento" },
                changedAt = now
            )
        )
        return newId
    }

    suspend fun transitionDividendStatus(
        eventId: Long,
        draftProjection: DividendDisplayEvent?,
        targetStatus: DividendStatus,
        reason: String = ""
    ): Long {
        val now = System.currentTimeMillis()
        if (eventId <= 0L && draftProjection != null) {
            return saveDividendEvent(
                id = null,
                ticker = draftProjection.assetTicker,
                eventType = draftProjection.eventType,
                status = targetStatus,
                exDate = draftProjection.exDate,
                paymentDate = draftProjection.paymentDate,
                unitAmount = draftProjection.unitAmount,
                quantityReference = draftProjection.quantityReference,
                totalAmount = draftProjection.totalAmount,
                source = if (targetStatus == DividendStatus.CONFIRMED) "Confirmação Usuário" else "Manual",
                notes = draftProjection.notes,
                isRecurring = draftProjection.isRecurring,
                reason = reason.ifBlank { "Transição de PROJETADO para ${targetStatus.label}" }
            )
        }

        val existing = dividendEventDao.getById(eventId) ?: return -1L
        val oldStatus = existing.status
        var linkedOpId = existing.legacyOperationId

        if (targetStatus == DividendStatus.RECEIVED && linkedOpId == null) {
            val opEntity = PortfolioOperationEntity(
                portfolioType = PortfolioType.REAL.name,
                ticker = existing.assetTicker,
                operationType = OperationType.PROVENTO.name,
                quantity = existing.quantityReference.coerceAtLeast(1.0),
                unitPrice = existing.unitAmount,
                totalValue = existing.totalAmount,
                dateIso = existing.paymentDate,
                notes = buildOperationNotes(DividendEventType.fromCode(existing.eventType), existing.notes)
            )
            linkedOpId = portfolioDao.insertOperation(opEntity)
            onMirrorRealOperation(opEntity.copy(id = linkedOpId))
        } else if (targetStatus != DividendStatus.RECEIVED && linkedOpId != null) {
            portfolioDao.deleteOperationById(linkedOpId)
            linkedOpId = null
        }

        val updated = existing.copy(
            status = targetStatus.name,
            estimated = targetStatus == DividendStatus.PROJECTED,
            userEdited = true,
            syncDivergenceNote = "",
            legacyOperationId = linkedOpId,
            isDeleted = false,
            deletedAt = null,
            updatedAt = now
        )
        dividendEventDao.update(updated)
        dividendEventDao.insertAuditLog(
            FinancialAuditLogEntity(
                entityType = "DIVIDEND_EVENT",
                entityId = updated.id,
                ticker = updated.assetTicker,
                actionType = "STATUS_CHANGE",
                previousStatus = oldStatus,
                newStatus = targetStatus.name,
                previousAmount = existing.totalAmount,
                newAmount = updated.totalAmount,
                previousSummary = "${existing.assetTicker} ($oldStatus) • R$ ${"%.2f".format(existing.totalAmount)}",
                newSummary = "${updated.assetTicker} (${targetStatus.name}) • R$ ${"%.2f".format(updated.totalAmount)}",
                source = "Usuário",
                reason = reason.ifBlank { "Alteração de status para ${targetStatus.label}" },
                changedAt = now
            )
        )
        return updated.id
    }

    suspend fun softDeleteDividendEvent(eventId: Long, reason: String = "Exclusão pelo usuário"): Boolean {
        val existing = dividendEventDao.getById(eventId) ?: return false
        val now = System.currentTimeMillis()
        existing.legacyOperationId?.let { opId ->
            portfolioDao.deleteOperationById(opId)
        }
        dividendEventDao.softDelete(id = eventId, deletedAt = now, updatedAt = now)
        dividendEventDao.insertAuditLog(
            FinancialAuditLogEntity(
                entityType = "DIVIDEND_EVENT",
                entityId = existing.id,
                ticker = existing.assetTicker,
                actionType = "DELETE",
                previousStatus = existing.status,
                newStatus = "DELETED",
                previousAmount = existing.totalAmount,
                newAmount = 0.0,
                previousSummary = "${existing.assetTicker} • ${existing.paymentDate} • R$ ${"%.2f".format(existing.totalAmount)}",
                newSummary = "Removido (restaurável na auditoria)",
                restorePayloadJson = JSONObject().apply {
                    put("dividendEventId", existing.id)
                    put("previousStatus", existing.status)
                }.toString(),
                source = "Usuário",
                reason = reason,
                changedAt = now
            )
        )
        return true
    }

    suspend fun restoreDividendEvent(eventId: Long, preferredStatus: String? = null): Boolean {
        val existing = dividendEventDao.getById(eventId) ?: return false
        val now = System.currentTimeMillis()
        val targetStatusCode = when {
            !preferredStatus.isNullOrBlank() && preferredStatus != "DELETED" -> preferredStatus
            existing.status == DividendStatus.CANCELLED.name ->
                if (existing.estimated) DividendStatus.PROJECTED.name else DividendStatus.CONFIRMED.name
            else -> existing.status
        }
        val targetStatus = DividendStatus.fromCode(targetStatusCode)
        var linkedOpId = existing.legacyOperationId
        if (targetStatus == DividendStatus.RECEIVED && linkedOpId == null) {
            val opEntity = PortfolioOperationEntity(
                portfolioType = PortfolioType.REAL.name,
                ticker = existing.assetTicker,
                operationType = OperationType.PROVENTO.name,
                quantity = existing.quantityReference.coerceAtLeast(1.0),
                unitPrice = existing.unitAmount,
                totalValue = existing.totalAmount,
                dateIso = existing.paymentDate,
                notes = buildOperationNotes(DividendEventType.fromCode(existing.eventType), existing.notes)
            )
            linkedOpId = portfolioDao.insertOperation(opEntity)
            onMirrorRealOperation(opEntity.copy(id = linkedOpId))
        }
        dividendEventDao.update(
            existing.copy(
                status = targetStatus.name,
                isDeleted = false,
                deletedAt = null,
                legacyOperationId = linkedOpId,
                updatedAt = now
            )
        )
        dividendEventDao.insertAuditLog(
            FinancialAuditLogEntity(
                entityType = "DIVIDEND_EVENT",
                entityId = existing.id,
                ticker = existing.assetTicker,
                actionType = "RESTORE",
                previousStatus = if (existing.isDeleted) "DELETED" else existing.status,
                newStatus = targetStatus.name,
                previousAmount = 0.0,
                newAmount = existing.totalAmount,
                previousSummary = "Registro excluído/cancelado",
                newSummary = "${existing.assetTicker} restaurado (${targetStatus.label}) • R$ ${"%.2f".format(existing.totalAmount)}",
                source = "Usuário",
                reason = "Restauração pela auditoria",
                changedAt = now
            )
        )
        return true
    }

    /**
     * Renomeia o ticker de proventos em TODAS as tabelas relevantes (dividend_events e portfolio_operations PROVENTO).
     * Corrige definitivamente o caso de ativos cadastrados apenas via dividendo sem posição na carteira.
     */
    suspend fun renameDividendTicker(oldTicker: String, newTicker: String): Int {
        val cleanOld = oldTicker.trim().uppercase(Locale.ROOT)
        val cleanNew = newTicker.trim().uppercase(Locale.ROOT)
        if (cleanOld.isBlank() || cleanNew.isBlank() || cleanOld == cleanNew) return 0
        val now = System.currentTimeMillis()
        val updatedEvents = dividendEventDao.renameTicker(cleanOld, cleanNew, now)
        val updatedOps = portfolioDao.renameProventoOperationsTicker(cleanOld, cleanNew)
        val totalAffected = maxOf(updatedEvents, updatedOps)
        if (totalAffected > 0) {
            dividendEventDao.insertAuditLog(
                FinancialAuditLogEntity(
                    entityType = "DIVIDEND_EVENT",
                    entityId = 0L,
                    ticker = cleanNew,
                    actionType = "RENAME_TICKER",
                    previousStatus = cleanOld,
                    newStatus = cleanNew,
                    previousSummary = "Ticker antigo: $cleanOld",
                    newSummary = "Ticker atualizado para: $cleanNew ($updatedEvents evento(s), $updatedOps lançamento(s))",
                    source = "Usuário",
                    reason = "Correção de ticker de ativo com proventos",
                    changedAt = now
                )
            )
        }
        return totalAffected
    }

    /**
     * Sincroniza anúncios CONFIRMADOS com base nas cotações reais obtidas sem duplicar registros
     * nem sobrescrever silenciosamente edições feitas manualmente pelo usuário.
     */
    suspend fun syncConfirmedDividendsFromQuotes(
        realPositions: List<PortfolioItemView>,
        quotesByTicker: Map<String, LiveMarketQuote>
    ): DividendSyncResult {
        val candidates = DividendHistoryEngine.buildSyncCandidatesFromQuotes(realPositions, quotesByTicker)
        if (candidates.isEmpty()) {
            return DividendSyncResult(checkedAssetsCount = realPositions.count { it.quantity > 0.0 })
        }

        val activeEvents = dividendEventDao.getActiveDividendEvents()
        val now = System.currentTimeMillis()
        var created = 0
        var updated = 0
        var divergences = 0

        candidates.forEach { cand ->
            val candDate = DividendHistoryEngine.parseDateToYmd(cand.paymentDate)
            val existingByDedup = dividendEventDao.findByDedupKey(cand.dedupKey)
            val existingSameCycle = existingByDedup ?: activeEvents.firstOrNull { ev ->
                if (!ev.assetTicker.equals(cand.ticker, ignoreCase = true)) return@firstOrNull false
                if (ev.status == DividendStatus.CANCELLED.name) return@firstOrNull false
                val evDate = DividendHistoryEngine.parseDateToYmd(ev.paymentDate)
                    ?: DividendHistoryEngine.parseDateToYmd(ev.exDate)
                candDate != null && evDate != null &&
                    evDate.first == candDate.first && evDate.second == candDate.second
            }

            if (existingSameCycle == null) {
                val newEntity = DividendEventEntity(
                    assetTicker = cand.ticker,
                    eventType = cand.eventType.name,
                    status = DividendStatus.CONFIRMED.name,
                    exDate = cand.exDate,
                    paymentDate = cand.paymentDate,
                    unitAmount = cand.unitAmount,
                    quantityReference = cand.quantityReference,
                    totalAmount = cand.totalAmount,
                    source = cand.source,
                    sourceReference = cand.sourceReference,
                    dedupKey = cand.dedupKey,
                    isRecurring = cand.isRecurring,
                    estimated = false,
                    userEdited = false,
                    notes = "Provento identificado na sincronização automática",
                    createdAt = now,
                    updatedAt = now
                )
                val insertedId = dividendEventDao.insert(newEntity)
                dividendEventDao.insertAuditLog(
                    FinancialAuditLogEntity(
                        entityType = "DIVIDEND_EVENT",
                        entityId = insertedId,
                        ticker = cand.ticker,
                        actionType = "AUTO_SYNC",
                        previousStatus = "",
                        newStatus = DividendStatus.CONFIRMED.name,
                        newAmount = cand.totalAmount,
                        newSummary = "${cand.ticker} • Confirmado ${cand.paymentDate} • R$ ${"%.2f".format(cand.totalAmount)}",
                        source = cand.source,
                        reason = "Sincronização automática de provento confirmado",
                        changedAt = now
                    )
                )
                created++
            } else if (existingSameCycle.status == DividendStatus.CONFIRMED.name) {
                val diffRatio = if (existingSameCycle.unitAmount > 0.0) {
                    abs(cand.unitAmount - existingSameCycle.unitAmount) / existingSameCycle.unitAmount
                } else {
                    1.0
                }
                if (existingSameCycle.userEdited) {
                    if (diffRatio > 0.01) {
                        val note = "Divergência detectada (${cand.source}): R$ ${String.format(Locale("pt", "BR"), "%.4f", cand.unitAmount)}/cota"
                        dividendEventDao.update(
                            existingSameCycle.copy(
                                syncDivergenceNote = note,
                                updatedAt = now
                            )
                        )
                        dividendEventDao.insertAuditLog(
                            FinancialAuditLogEntity(
                                entityType = "DIVIDEND_EVENT",
                                entityId = existingSameCycle.id,
                                ticker = cand.ticker,
                                actionType = "SYNC_DIVERGENCE",
                                previousStatus = existingSameCycle.status,
                                newStatus = existingSameCycle.status,
                                previousAmount = existingSameCycle.totalAmount,
                                newAmount = cand.totalAmount,
                                previousSummary = "Editado manualmente: R$ ${"%.2f".format(existingSameCycle.totalAmount)}",
                                newSummary = note,
                                source = cand.source,
                                reason = "Edição manual preservada; divergência sinalizada",
                                changedAt = now
                            )
                        )
                        divergences++
                    }
                } else if (diffRatio > 0.005 || abs(cand.quantityReference - existingSameCycle.quantityReference) > 0.001) {
                    dividendEventDao.update(
                        existingSameCycle.copy(
                            exDate = cand.exDate,
                            paymentDate = cand.paymentDate,
                            unitAmount = cand.unitAmount,
                            quantityReference = cand.quantityReference,
                            totalAmount = cand.totalAmount,
                            source = cand.source,
                            updatedAt = now
                        )
                    )
                    updated++
                }
            }
            // Se status já for RECEIVED: jamais rebaixa nem duplica!
        }

        return DividendSyncResult(
            createdCount = created,
            updatedCount = updated,
            divergencesCount = divergences,
            checkedAssetsCount = realPositions.count { it.quantity > 0.0 }
        )
    }

    /**
     * Edita qualquer operação (COMPRA, VENDA, PROVENTO) com log de auditoria e recálculo automático
     * de quantidade e preço médio da(s) posição(ões) afetada(s).
     */
    suspend fun updateOperationWithRecalc(
        operationId: Long,
        ticker: String,
        operationType: OperationType,
        quantity: Double,
        unitPrice: Double,
        dateIso: String,
        notes: String
    ): Boolean {
        val old = portfolioDao.getOperationById(operationId) ?: return false
        val cleanTicker = ticker.trim().uppercase(Locale.ROOT)
        val cleanQty = quantity.coerceAtLeast(0.0)
        val cleanPrice = unitPrice.coerceAtLeast(0.0)
        val total = cleanQty * cleanPrice
        val cleanDate = dateIso.trim().ifBlank { old.dateIso }
        val cleanNotes = notes.trim()
        val now = System.currentTimeMillis()

        portfolioDao.updateOperationById(
            id = operationId,
            ticker = cleanTicker,
            operationType = operationType.name,
            quantity = cleanQty,
            unitPrice = cleanPrice,
            totalValue = total,
            dateIso = cleanDate,
            notes = cleanNotes
        )

        // Sincroniza dividend_events se for PROVENTO
        val linkedDiv = dividendEventDao.getByLegacyOperationId(operationId)
        if (operationType == OperationType.PROVENTO) {
            mirrorLegacyProventoOperation(
                opId = operationId,
                ticker = cleanTicker,
                quantity = cleanQty,
                unitPrice = cleanPrice,
                totalValue = total,
                dateIso = cleanDate,
                notes = cleanNotes
            )
        } else if (linkedDiv != null) {
            dividendEventDao.softDelete(linkedDiv.id, now, now)
        }

        // Recalcula posição se envolver COMPRA ou VENDA
        val affectedTickers = setOf(old.ticker.trim().uppercase(Locale.ROOT), cleanTicker)
        if (old.operationType == OperationType.COMPRA.name ||
            old.operationType == OperationType.VENDA.name ||
            operationType == OperationType.COMPRA ||
            operationType == OperationType.VENDA
        ) {
            val portfolioType = runCatching { PortfolioType.valueOf(old.portfolioType) }.getOrDefault(PortfolioType.REAL)
            affectedTickers.forEach { t ->
                recalculateAndPersistPosition(portfolioType, t)
            }
        }

        dividendEventDao.insertAuditLog(
            FinancialAuditLogEntity(
                entityType = "OPERATION",
                entityId = operationId,
                ticker = cleanTicker,
                actionType = "UPDATE",
                previousStatus = old.operationType,
                newStatus = operationType.name,
                previousAmount = old.totalValue,
                newAmount = total,
                previousSummary = "${old.ticker} • ${old.operationType} • ${old.quantity} x R$ ${"%.2f".format(old.unitPrice)} (${old.dateIso})",
                newSummary = "$cleanTicker • ${operationType.name} • $cleanQty x R$ ${"%.2f".format(cleanPrice)} ($cleanDate)",
                source = "Usuário",
                reason = "Edição de operação com recálculo de posição",
                changedAt = now
            )
        )
        return true
    }

    suspend fun deleteOperationWithAudit(operationId: Long): Boolean {
        val old = portfolioDao.getOperationById(operationId) ?: return false
        val now = System.currentTimeMillis()
        val payload = JSONObject().apply {
            put("portfolioType", old.portfolioType)
            put("ticker", old.ticker)
            put("operationType", old.operationType)
            put("quantity", old.quantity)
            put("unitPrice", old.unitPrice)
            put("totalValue", old.totalValue)
            put("dateIso", old.dateIso)
            put("notes", old.notes)
            put("externalRefId", old.externalRefId)
            put("brokerAccount", old.brokerAccount)
        }.toString()

        portfolioDao.deleteOperationById(operationId)
        dividendEventDao.getByLegacyOperationId(operationId)?.let { linkedDiv ->
            dividendEventDao.softDelete(linkedDiv.id, now, now)
        }

        if (old.operationType == OperationType.COMPRA.name || old.operationType == OperationType.VENDA.name) {
            val portfolioType = runCatching { PortfolioType.valueOf(old.portfolioType) }.getOrDefault(PortfolioType.REAL)
            recalculateAndPersistPosition(portfolioType, old.ticker)
        }

        dividendEventDao.insertAuditLog(
            FinancialAuditLogEntity(
                entityType = "OPERATION",
                entityId = operationId,
                ticker = old.ticker,
                actionType = "DELETE",
                previousStatus = old.operationType,
                newStatus = "DELETED",
                previousAmount = old.totalValue,
                newAmount = 0.0,
                previousSummary = "${old.ticker} • ${old.operationType} • ${old.quantity} x R$ ${"%.2f".format(old.unitPrice)} (${old.dateIso})",
                newSummary = "Operação excluída (restaurável na auditoria)",
                restorePayloadJson = payload,
                source = "Usuário",
                reason = "Exclusão de operação com recálculo automático",
                changedAt = now
            )
        )
        return true
    }

    suspend fun recordPositionDeletionAudit(position: PortfolioPositionEntity) {
        val now = System.currentTimeMillis()
        val payload = JSONObject().apply {
            put("portfolioType", position.portfolioType)
            put("ticker", position.ticker)
            put("name", position.name)
            put("assetClass", position.assetClass)
            put("quantity", position.quantity)
            put("averagePrice", position.averagePrice)
            put("currentPrice", position.currentPrice)
            put("hasMarketPrice", position.hasMarketPrice)
            put("sector", position.sector)
            put("dataComDay", position.dataComDay ?: JSONObject.NULL)
            put("isCyclical", position.isCyclical)
            put("dataSourceLabel", position.dataSourceLabel)
            put("isDemo", position.isDemo)
        }.toString()

        dividendEventDao.insertAuditLog(
            FinancialAuditLogEntity(
                entityType = "POSITION",
                entityId = position.id,
                ticker = position.ticker,
                actionType = "DELETE",
                previousStatus = position.portfolioType,
                newStatus = "DELETED",
                previousAmount = position.quantity * (if (position.hasMarketPrice && position.currentPrice > 0.0) position.currentPrice else position.averagePrice),
                newAmount = 0.0,
                previousSummary = "${position.ticker} • ${position.quantity} unid. • PM R$ ${"%.2f".format(position.averagePrice)}",
                newSummary = "Posição removida (histórico preservado e restaurável)",
                restorePayloadJson = payload,
                source = "Usuário",
                reason = "Exclusão confirmada de ativo da carteira",
                changedAt = now
            )
        )
    }

    suspend fun restoreFromAuditLog(auditId: Long): String {
        val log = dividendEventDao.getAuditLogById(auditId) ?: return "Registro de auditoria não encontrado."
        val now = System.currentTimeMillis()

        return when (log.entityType) {
            "DIVIDEND_EVENT" -> {
                val json = runCatching { JSONObject(log.restorePayloadJson) }.getOrNull()
                val eventId = json?.optLong("dividendEventId", log.entityId) ?: log.entityId
                val prevStatus = json?.optString("previousStatus", log.previousStatus) ?: log.previousStatus
                if (restoreDividendEvent(eventId, prevStatus)) {
                    "Provento de ${log.ticker} restaurado com sucesso."
                } else {
                    "Não foi possível localizar o provento para restauração."
                }
            }
            "OPERATION" -> {
                if (log.restorePayloadJson.isBlank()) return "Este registro não possui backup restaurável."
                val json = JSONObject(log.restorePayloadJson)
                val restoredOp = PortfolioOperationEntity(
                    portfolioType = json.optString("portfolioType", PortfolioType.REAL.name),
                    ticker = json.optString("ticker", log.ticker),
                    operationType = json.optString("operationType", OperationType.COMPRA.name),
                    quantity = json.optDouble("quantity", 0.0),
                    unitPrice = json.optDouble("unitPrice", 0.0),
                    totalValue = json.optDouble("totalValue", 0.0),
                    dateIso = json.optString("dateIso", ""),
                    notes = json.optString("notes", ""),
                    externalRefId = json.optString("externalRefId", ""),
                    brokerAccount = json.optString("brokerAccount", "")
                )
                val newOpId = portfolioDao.insertOperation(restoredOp)
                if (restoredOp.portfolioType == PortfolioType.REAL.name) {
                    onMirrorRealOperation(restoredOp.copy(id = newOpId))
                }
                if (restoredOp.operationType == OperationType.PROVENTO.name) {
                    mirrorLegacyProventoOperation(
                        opId = newOpId,
                        ticker = restoredOp.ticker,
                        quantity = restoredOp.quantity,
                        unitPrice = restoredOp.unitPrice,
                        totalValue = restoredOp.totalValue,
                        dateIso = restoredOp.dateIso,
                        notes = restoredOp.notes
                    )
                } else {
                    val portfolioType = runCatching { PortfolioType.valueOf(restoredOp.portfolioType) }.getOrDefault(PortfolioType.REAL)
                    recalculateAndPersistPosition(portfolioType, restoredOp.ticker)
                }
                dividendEventDao.insertAuditLog(
                    FinancialAuditLogEntity(
                        entityType = "OPERATION",
                        entityId = newOpId,
                        ticker = restoredOp.ticker,
                        actionType = "RESTORE",
                        previousStatus = "DELETED",
                        newStatus = restoredOp.operationType,
                        newAmount = restoredOp.totalValue,
                        newSummary = "Operação ${restoredOp.ticker} (${restoredOp.operationType}) restaurada",
                        source = "Usuário",
                        reason = "Restauração pela auditoria",
                        changedAt = now
                    )
                )
                "Operação ${restoredOp.ticker} restaurada e posição recalculada."
            }
            "POSITION" -> {
                if (log.restorePayloadJson.isBlank()) return "Este registro não possui backup restaurável."
                val json = JSONObject(log.restorePayloadJson)
                val dcDay = if (json.isNull("dataComDay")) null else json.optInt("dataComDay").takeIf { it in 1..31 }
                val restoredPos = PortfolioPositionEntity(
                    portfolioType = json.optString("portfolioType", PortfolioType.REAL.name),
                    ticker = json.optString("ticker", log.ticker),
                    name = json.optString("name", log.ticker),
                    assetClass = json.optString("assetClass", "ACOES_BR"),
                    quantity = json.optDouble("quantity", 0.0),
                    averagePrice = json.optDouble("averagePrice", 0.0),
                    currentPrice = json.optDouble("currentPrice", 0.0),
                    hasMarketPrice = json.optBoolean("hasMarketPrice", false),
                    sector = json.optString("sector", "Geral"),
                    dataComDay = dcDay,
                    isCyclical = json.optBoolean("isCyclical", true),
                    dataSourceLabel = json.optString("dataSourceLabel", "Restaurado"),
                    isDemo = json.optBoolean("isDemo", false),
                    updatedAt = now
                )
                val newId = portfolioDao.insertPosition(restoredPos)
                if (restoredPos.portfolioType == PortfolioType.REAL.name) {
                    onMirrorRealPositions()
                }
                dividendEventDao.insertAuditLog(
                    FinancialAuditLogEntity(
                        entityType = "POSITION",
                        entityId = newId,
                        ticker = restoredPos.ticker,
                        actionType = "RESTORE",
                        previousStatus = "DELETED",
                        newStatus = restoredPos.portfolioType,
                        newAmount = restoredPos.quantity * restoredPos.averagePrice,
                        newSummary = "Posição ${restoredPos.ticker} restaurada (${restoredPos.quantity} unid.)",
                        source = "Usuário",
                        reason = "Restauração pela auditoria",
                        changedAt = now
                    )
                )
                "Posição ${restoredPos.ticker} restaurada na carteira."
            }
            else -> "Tipo de registro não suportado para restauração."
        }
    }

    private suspend fun recalculateAndPersistPosition(portfolioType: PortfolioType, ticker: String) {
        val cleanTicker = ticker.trim().uppercase(Locale.ROOT)
        if (cleanTicker.isBlank()) return
        val existingPos = portfolioDao.getPositionsByType(portfolioType.name)
            .firstOrNull { it.ticker.equals(cleanTicker, ignoreCase = true) }
        val ops = portfolioDao.getOperationsByTicker(portfolioType.name, cleanTicker)
        val recalc = DividendHistoryEngine.recalculatePositionFromOperations(existingPos, ops)
        if (recalc.hadBuyOrSellOperations && existingPos != null) {
            portfolioDao.updatePositionById(
                id = existingPos.id,
                ticker = existingPos.ticker,
                name = existingPos.name,
                assetClass = existingPos.assetClass,
                quantity = recalc.quantity,
                averagePrice = recalc.averagePrice,
                currentPrice = existingPos.currentPrice,
                hasMarketPrice = existingPos.hasMarketPrice,
                sector = existingPos.sector,
                dataComDay = existingPos.dataComDay,
                dataSourceLabel = existingPos.dataSourceLabel,
                updatedAt = System.currentTimeMillis(),
                isCyclical = existingPos.isCyclical
            )
            if (portfolioType == PortfolioType.REAL) {
                onMirrorRealPositions()
            }
        }
    }

    private fun buildOperationNotes(eventType: DividendEventType, notes: String): String {
        val label = eventType.label
        return when {
            notes.isBlank() -> label
            notes.contains(label, ignoreCase = true) -> notes
            else -> "$label • $notes"
        }
    }
}
