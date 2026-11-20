package com.example.domain.model

data class ProfitChartExecution(
    val externalRefId: String,
    val ticker: String,
    val operationType: OperationType,
    val quantity: Double,
    val unitPrice: Double,
    val totalValue: Double,
    val executedAtLabel: String,
    val statusLabel: String,
    val brokerAccount: String,
    val sourceChannel: String
)

data class ProfitChartSyncSummary(
    val totalReceived: Int,
    val newImportedCount: Int,
    val skippedDuplicatesCount: Int,
    val skippedNonExecutedCount: Int,
    val updatedPositionsCount: Int,
    val syncedAtMillis: Long,
    val message: String
)

sealed interface ProfitChartConnectionState {
    data object Disconnected : ProfitChartConnectionState
    data object Syncing : ProfitChartConnectionState
    data class Connected(val summary: ProfitChartSyncSummary) : ProfitChartConnectionState
    data class Error(val message: String) : ProfitChartConnectionState
}
