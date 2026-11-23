package com.example.data.repository

import android.content.Context
import androidx.annotation.Keep
import com.example.R
import com.example.data.local.ImportHistoryEntity
import com.example.data.local.SavedAiRecommendationEntity
import com.example.domain.model.AssetAuditJsonCodec
import com.example.domain.model.AssetClass
import com.example.domain.model.CloudAsset
import com.example.domain.model.CloudAssetAudit
import com.example.domain.model.ConsolidatedExecutiveReport
import com.example.domain.model.FirestoreAssetAuditSummary
import com.example.domain.model.HhiRiskReport
import com.example.domain.model.MarketAsset
import com.example.domain.model.PortfolioItemView
import com.google.firebase.Firebase
import com.google.firebase.Timestamp
import com.google.firebase.auth.auth
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.ServerTimestamp
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await
import java.util.UUID

@Keep
@IgnoreExtraProperties
data class CloudConsolidatedReport(
    @DocumentId val id: String = "",
    val userId: String = "",
    val tickerFilter: String = "GERAL",
    val consensusConclusion: String = "Aguardar",
    val averageConfidence: Long = 0L,
    val coveredAgentsCount: Long = 0L,
    val analystSummary: String = "",
    val gestorSummary: String = "",
    val auditorSummary: String = "",
    val combinedRisks: String = "",
    val totalEquityBrl: Double = 0.0,
    val hhiScore: Double = 0.0,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class CloudAgentInsight(
    @DocumentId val id: String = "",
    val userId: String = "",
    val agentRole: String = "",
    val ticker: String = "",
    val conclusion: String = "",
    val confidence: Long = 0L,
    val thesis: String = "",
    val sourceLabel: String = "",
    val dataBase: String = "",
    val indicatorsSummary: String = "",
    val risksSummary: String = "",
    val scenariosSummary: String = "",
    val usedPersonalData: Boolean = false,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

class FirestoreSyncRepository(
    private val db: FirebaseFirestore
) {
    constructor(context: Context) : this(
        FirebaseFirestore.getInstance(
            context.applicationContext.getString(R.string.firestore_database_id)
        )
    )

    private fun requireUserId(): String {
        return Firebase.auth.currentUser?.uid
            ?: throw IllegalStateException("Usuário precisa autenticar com Google antes de sincronizar com a nuvem.")
    }

    fun observeConsolidatedReports(userId: String): Flow<List<CloudConsolidatedReport>> = flow {
        val path = "users/$userId/consolidated_reports"
        emitAll(
            db.collection("users").document(userId).collection("consolidated_reports")
                .orderBy("createdAt", Query.Direction.DESCENDING)
                .limit(20)
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(
                        CloudConsolidatedReport::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    )
                }
                .catch { error ->
                    val firestoreError = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (firestoreError == null && error is CancellationException) throw error
                    val ex = firestoreError ?: (error as? Exception)
                    if (ex != null && Firebase.auth.currentUser?.uid == userId) {
                        handleFirestoreError(ex, FirestoreOpType.LIST, path)
                    }
                    throw error
                }
        )
    }

    fun observeCloudAssets(userId: String): Flow<List<CloudAsset>> = flow {
        val path = "users/$userId/assets"
        emitAll(
            db.collection("users").document(userId).collection("assets")
                .orderBy("updatedAt", Query.Direction.DESCENDING)
                .limit(100)
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(
                        CloudAsset::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    )
                }
                .catch { error ->
                    val firestoreError = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (firestoreError == null && error is CancellationException) throw error
                    val ex = firestoreError ?: (error as? Exception)
                    if (ex != null && Firebase.auth.currentUser?.uid == userId) {
                        handleFirestoreError(ex, FirestoreOpType.LIST, path)
                    }
                    throw error
                }
        )
    }

    suspend fun getCloudAsset(assetId: String): Result<CloudAsset?> {
        val uid = runCatching { requireUserId() }.getOrElse { return Result.failure(it) }
        val docId = CloudAsset.normalizeAssetDocId(assetId)
        val docRef = db.collection("users").document(uid).collection("assets").document(docId)
        return try {
            val snap = docRef.get().await()
            if (!snap.exists()) {
                Result.success(null)
            } else {
                Result.success(
                    snap.toObject(CloudAsset::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                )
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, FirestoreOpType.GET, docRef.path)
            Result.failure(e)
        }
    }

    suspend fun upsertCloudAsset(asset: CloudAsset): Result<String> {
        val uid = runCatching { requireUserId() }.getOrElse { return Result.failure(it) }
        val normalizedTicker = asset.ticker.trim().uppercase().take(24)
            .ifBlank { return Result.failure(IllegalArgumentException("Ticker inválido para sincronização no Firestore.")) }
        val docId = CloudAsset.normalizeAssetDocId(asset.id.ifBlank { normalizedTicker })
        val docRef = db.collection("users").document(uid).collection("assets").document(docId)

        val validAssetClass = AssetClass.entries.firstOrNull { it.name == asset.assetClass }?.name ?: AssetClass.ACOES_BR.name
        val mutableFields = mapOf<String, Any>(
            "name" to asset.name.trim().ifBlank { normalizedTicker }.take(120),
            "assetClass" to validAssetClass,
            "sector" to asset.sector.trim().take(100),
            "quantity" to asset.quantity.coerceAtLeast(0.0),
            "averagePrice" to asset.averagePrice.coerceAtLeast(0.0),
            "currentPrice" to asset.currentPrice.coerceAtLeast(0.0),
            "dy12m" to asset.dy12m.coerceIn(0.0, 500.0),
            "pvp" to asset.pvp.coerceIn(0.0, 1000.0),
            "pl" to asset.pl.coerceIn(-10000.0, 10000.0),
            "roe" to asset.roe.coerceIn(-500.0, 1000.0),
            "dailyLiquidityMillions" to asset.dailyLiquidityMillions.coerceAtLeast(0.0),
            "dataComDay" to asset.dataComDay.coerceIn(0L, 31L),
            "cyclical" to asset.cyclical,
            "demoData" to asset.demoData,
            "sourceLabel" to asset.sourceLabel.trim().take(300),
            "dataBaseDate" to asset.dataBaseDate.trim().take(64),
            "riskSummary" to asset.riskSummary.trim().take(1500),
            "updatedAt" to FieldValue.serverTimestamp()
        )

        return try {
            val existing = docRef.get().await()
            if (existing.exists()) {
                docRef.update(mutableFields).await()
            } else {
                val createPayload = mutableFields + mapOf(
                    "userId" to uid,
                    "ticker" to normalizedTicker,
                    "createdAt" to FieldValue.serverTimestamp()
                )
                docRef.set(createPayload).await()
            }
            Result.success(docId)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, FirestoreOpType.WRITE, docRef.path)
            Result.failure(e)
        }
    }

    suspend fun syncPortfolioAssetsToCloud(
        positions: List<PortfolioItemView>,
        marketAssets: List<MarketAsset>
    ): Result<Int> {
        val uid = runCatching { requireUserId() }.getOrElse { return Result.failure(it) }
        val marketByTicker = marketAssets.associateBy { it.ticker.trim().uppercase() }
        val assetsToSync = if (positions.isNotEmpty()) {
            positions.map { pos ->
                CloudAsset.fromLocalData(
                    position = pos,
                    marketAsset = marketByTicker[pos.ticker.trim().uppercase()],
                    userId = uid
                )
            }
        } else {
            marketAssets.take(8).map { ma ->
                CloudAsset.fromLocalData(position = null, marketAsset = ma, userId = uid)
            }
        }

        var syncedCount = 0
        for (cloudAsset in assetsToSync) {
            val res = upsertCloudAsset(cloudAsset)
            if (res.isFailure) {
                return Result.failure(res.exceptionOrNull() ?: IllegalStateException("Falha ao sincronizar ativo ${cloudAsset.ticker}"))
            }
            syncedCount++
        }
        return Result.success(syncedCount)
    }

    fun observeAssetAudits(userId: String): Flow<List<CloudAssetAudit>> = flow {
        val path = "users/$userId/asset_audits"
        emitAll(
            db.collection("users").document(userId).collection("asset_audits")
                .orderBy("createdAt", Query.Direction.DESCENDING)
                .limit(25)
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(
                        CloudAssetAudit::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    )
                }
                .catch { error ->
                    val firestoreError = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (firestoreError == null && error is CancellationException) throw error
                    val ex = firestoreError ?: (error as? Exception)
                    if (ex != null && Firebase.auth.currentUser?.uid == userId) {
                        handleFirestoreError(ex, FirestoreOpType.LIST, path)
                    }
                    throw error
                }
        )
    }

    suspend fun saveAssetAudit(
        summary: FirestoreAssetAuditSummary,
        docId: String = summary.id.ifBlank { "audit_${CloudAsset.normalizeAssetDocId(summary.ticker)}_${UUID.randomUUID().toString().take(8)}" }
    ): Result<String> {
        val uid = runCatching { requireUserId() }.getOrElse { return Result.failure(it) }
        val cleanDocId = docId.replace(Regex("[^a-zA-Z0-9_\\-]"), "_").take(120)
        val docRef = db.collection("users").document(uid)
            .collection("asset_audits").document(cleanDocId)

        val payload = mapOf(
            "userId" to uid,
            "assetId" to CloudAsset.normalizeAssetDocId(summary.assetId.ifBlank { summary.ticker }).take(64),
            "ticker" to summary.ticker.trim().uppercase().take(24).ifBlank { "ATIVO" },
            "assetName" to summary.assetName.trim().ifBlank { summary.ticker }.take(120),
            "riskLevel" to summary.riskLevel.name,
            "auditScore" to summary.auditScore.coerceIn(0, 100),
            "verdict" to summary.verdict.name,
            "executiveSummary" to summary.executiveSummary.trim().ifBlank { "Resumo de auditoria indisponível." }.take(4000),
            "risksJson" to AssetAuditJsonCodec.encodeRisks(summary.risks),
            "opportunitiesJson" to AssetAuditJsonCodec.encodeOpportunities(summary.opportunities),
            "inconsistencyAlertsJson" to AssetAuditJsonCodec.encodeStringList(summary.inconsistencyAlerts),
            "auditRecommendation" to summary.auditRecommendation.trim().ifBlank { "Monitorar relatórios oficiais." }.take(1500),
            "modelName" to summary.modelName.trim().ifBlank { "gemini-3.8-flash" }.take(64),
            "sourceSnapshotLabel" to summary.sourceSnapshotLabel.trim().take(300),
            "createdAt" to FieldValue.serverTimestamp(),
            "updatedAt" to FieldValue.serverTimestamp()
        )
        return try {
            docRef.set(payload).await()
            Result.success(cleanDocId)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, FirestoreOpType.CREATE, docRef.path)
            Result.failure(e)
        }
    }

    suspend fun getRecentAgentInsightsForTicker(ticker: String): Result<List<CloudAgentInsight>> {
        val uid = runCatching { requireUserId() }.getOrElse { return Result.failure(it) }
        val normalizedTicker = ticker.trim().uppercase()
        val collectionPath = "users/$uid/agent_insights"
        return try {
            val snap = db.collection("users").document(uid)
                .collection("agent_insights")
                .whereEqualTo("ticker", normalizedTicker)
                .limit(6)
                .get()
                .await()
            val insights = snap.toObjects(
                CloudAgentInsight::class.java,
                DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
            )
            Result.success(insights)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, FirestoreOpType.LIST, collectionPath)
            Result.failure(e)
        }
    }

    suspend fun saveConsolidatedReport(
        report: ConsolidatedExecutiveReport,
        hhiReport: HhiRiskReport,
        docId: String = UUID.randomUUID().toString()
    ): Result<String> {
        val uid = runCatching { requireUserId() }.getOrElse { return Result.failure(it) }
        val consensus = report.consensusConclusion?.label ?: return Result.failure(
            IllegalStateException("Gere ao menos um parecer dos agentes antes de salvar o relatório consolidado.")
        )
        val docRef = db.collection("users").document(uid)
            .collection("consolidated_reports").document(docId)

        val payload = mapOf(
            "userId" to uid,
            "tickerFilter" to (report.filterTicker ?: "GERAL").take(32),
            "consensusConclusion" to consensus,
            "averageConfidence" to report.averageConfidence.coerceIn(0, 100),
            "coveredAgentsCount" to report.coveredAgentsCount.coerceIn(1, 3),
            "analystSummary" to (report.analystInsight?.let { "${it.ticker}: ${it.conclusion} (${it.confidence}%) — ${it.thesis}" } ?: "").take(2000),
            "gestorSummary" to (report.gestorInsight?.let { "${it.ticker}: ${it.conclusion} (${it.confidence}%) — ${it.thesis}" } ?: "").take(2000),
            "auditorSummary" to (report.auditorInsight?.let { "${it.ticker}: ${it.conclusion} (${it.confidence}%) — ${it.thesis}" } ?: "").take(2000),
            "combinedRisks" to report.combinedRisks.joinToString(" | ").take(4000),
            "totalEquityBrl" to hhiReport.totalEquityBrl.coerceAtLeast(0.0),
            "hhiScore" to hhiReport.hhiScore.coerceIn(0.0, 10000.0),
            "createdAt" to FieldValue.serverTimestamp(),
            "updatedAt" to FieldValue.serverTimestamp()
        )
        return try {
            docRef.set(payload).await()
            Result.success(docId)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, FirestoreOpType.CREATE, docRef.path)
            Result.failure(e)
        }
    }

    suspend fun syncAgentInsight(
        entity: SavedAiRecommendationEntity,
        docId: String = "insight_${entity.agentRole.lowercase()}_${entity.ticker.lowercase().replace(Regex("[^a-z0-9]"), "")}_${entity.createdAt}"
    ): Result<String> {
        val uid = runCatching { requireUserId() }.getOrElse { return Result.failure(it) }
        val cleanDocId = docId.replace(Regex("[^a-zA-Z0-9_\\-]"), "_").take(120)
        val docRef = db.collection("users").document(uid)
            .collection("agent_insights").document(cleanDocId)

        val payload = mapOf(
            "userId" to uid,
            "agentRole" to entity.agentRole,
            "ticker" to entity.ticker.take(24),
            "conclusion" to entity.conclusion,
            "confidence" to entity.confidence.coerceIn(0, 100),
            "thesis" to entity.thesis.ifBlank { "Sem tese registrada" }.take(4000),
            "sourceLabel" to entity.sourceLabel.take(300),
            "dataBase" to entity.dataBase.take(80),
            "indicatorsSummary" to entity.indicatorsSummary.take(1500),
            "risksSummary" to entity.risksSummary.take(2000),
            "scenariosSummary" to entity.scenariosSummary.take(2000),
            "usedPersonalData" to entity.usedPersonalData,
            "createdAt" to FieldValue.serverTimestamp(),
            "updatedAt" to FieldValue.serverTimestamp()
        )
        return try {
            docRef.set(payload).await()
            Result.success(cleanDocId)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, FirestoreOpType.CREATE, docRef.path)
            Result.failure(e)
        }
    }

    suspend fun getAgentInsight(docId: String): Result<CloudAgentInsight?> {
        val uid = runCatching { requireUserId() }.getOrElse { return Result.failure(it) }
        val docRef = db.collection("users").document(uid).collection("agent_insights").document(docId)
        return try {
            val snap = docRef.get().await()
            Result.success(
                snap.toObject(CloudAgentInsight::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, FirestoreOpType.GET, docRef.path)
            Result.failure(e)
        }
    }

    suspend fun syncPortfolioSnapshot(
        latestImport: ImportHistoryEntity?,
        hhiReport: HhiRiskReport,
        positionsCount: Int,
        docId: String = UUID.randomUUID().toString()
    ): Result<String> {
        val uid = runCatching { requireUserId() }.getOrElse { return Result.failure(it) }
        val docRef = db.collection("users").document(uid)
            .collection("portfolio_snapshots").document(docId)

        val payload = mapOf(
            "userId" to uid,
            "fileName" to (latestImport?.fileName ?: "registro_manual").take(200),
            "fileType" to (latestImport?.fileType ?: "MANUAL").take(24),
            "recordCount" to positionsCount.coerceIn(0, 2000),
            "totalEquityBrl" to hhiReport.totalEquityBrl.coerceAtLeast(0.0),
            "cashBalanceBrl" to hhiReport.cashBrl.coerceAtLeast(0.0),
            "hhiScore" to hhiReport.hhiScore.coerceIn(0.0, 10000.0),
            "createdAt" to FieldValue.serverTimestamp(),
            "updatedAt" to FieldValue.serverTimestamp()
        )
        return try {
            docRef.set(payload).await()
            Result.success(docId)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, FirestoreOpType.CREATE, docRef.path)
            Result.failure(e)
        }
    }
}
