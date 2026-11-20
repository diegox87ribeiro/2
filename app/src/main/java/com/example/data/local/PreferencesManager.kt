package com.example.data.local

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.investPrefsDataStore by preferencesDataStore(name = "invest_ai_prefs")

data class AppPreferencesState(
    val cashRealBrl: Double = 0.0,
    val cashSimBrl: Double = 25000.0,
    val aiPortfolioConsentGranted: Boolean = false,
    val supabaseEndpointConfigured: String = "",
    val githubRepoConfigured: String = "",
    val profitEndpointUrl: String = "",
    val profitAuthToken: String = "",
    val profitAutoSyncEnabled: Boolean = false,
    val profitApplyToPositions: Boolean = true,
    val profitLastSyncEpochMs: Long = 0L,
    val profitLastStatusMessage: String = ""
)

class PreferencesManager(private val context: Context) {
    private object Keys {
        val CASH_REAL = doublePreferencesKey("cash_real_brl")
        val CASH_SIM = doublePreferencesKey("cash_sim_brl")
        val AI_CONSENT = booleanPreferencesKey("ai_portfolio_consent")
        val SUPABASE_URL = stringPreferencesKey("supabase_endpoint")
        val GITHUB_REPO = stringPreferencesKey("github_repo")
        val PROFIT_ENDPOINT_URL = stringPreferencesKey("profit_endpoint_url")
        val PROFIT_AUTH_TOKEN = stringPreferencesKey("profit_auth_token")
        val PROFIT_AUTO_SYNC = booleanPreferencesKey("profit_auto_sync")
        val PROFIT_APPLY_POSITIONS = booleanPreferencesKey("profit_apply_positions")
        val PROFIT_LAST_SYNC_MS = longPreferencesKey("profit_last_sync_ms")
        val PROFIT_LAST_STATUS = stringPreferencesKey("profit_last_status")
    }

    val preferencesFlow: Flow<AppPreferencesState> = context.investPrefsDataStore.data.map { prefs ->
        AppPreferencesState(
            cashRealBrl = prefs[Keys.CASH_REAL] ?: 0.0,
            cashSimBrl = prefs[Keys.CASH_SIM] ?: 25000.0,
            aiPortfolioConsentGranted = prefs[Keys.AI_CONSENT] ?: false,
            supabaseEndpointConfigured = prefs[Keys.SUPABASE_URL] ?: "",
            githubRepoConfigured = prefs[Keys.GITHUB_REPO] ?: "",
            profitEndpointUrl = prefs[Keys.PROFIT_ENDPOINT_URL] ?: "",
            profitAuthToken = prefs[Keys.PROFIT_AUTH_TOKEN] ?: "",
            profitAutoSyncEnabled = prefs[Keys.PROFIT_AUTO_SYNC] ?: false,
            profitApplyToPositions = prefs[Keys.PROFIT_APPLY_POSITIONS] ?: true,
            profitLastSyncEpochMs = prefs[Keys.PROFIT_LAST_SYNC_MS] ?: 0L,
            profitLastStatusMessage = prefs[Keys.PROFIT_LAST_STATUS] ?: ""
        )
    }

    suspend fun setCashRealBrl(amount: Double) {
        context.investPrefsDataStore.edit { it[Keys.CASH_REAL] = amount.coerceAtLeast(0.0) }
    }

    suspend fun setCashSimBrl(amount: Double) {
        context.investPrefsDataStore.edit { it[Keys.CASH_SIM] = amount.coerceAtLeast(0.0) }
    }

    suspend fun setAiPortfolioConsent(granted: Boolean) {
        context.investPrefsDataStore.edit { it[Keys.AI_CONSENT] = granted }
    }

    suspend fun setFutureIntegrationMeta(supabaseUrl: String, githubRepo: String) {
        context.investPrefsDataStore.edit {
            it[Keys.SUPABASE_URL] = supabaseUrl.trim()
            it[Keys.GITHUB_REPO] = githubRepo.trim()
        }
    }

    suspend fun saveProfitChartConfig(
        endpointUrl: String,
        authToken: String,
        autoSyncEnabled: Boolean,
        applyToPositions: Boolean
    ) {
        context.investPrefsDataStore.edit {
            it[Keys.PROFIT_ENDPOINT_URL] = endpointUrl.trim()
            it[Keys.PROFIT_AUTH_TOKEN] = authToken.trim()
            it[Keys.PROFIT_AUTO_SYNC] = autoSyncEnabled
            it[Keys.PROFIT_APPLY_POSITIONS] = applyToPositions
        }
    }

    suspend fun updateProfitSyncStatus(epochMs: Long, statusMessage: String) {
        context.investPrefsDataStore.edit {
            it[Keys.PROFIT_LAST_SYNC_MS] = epochMs
            it[Keys.PROFIT_LAST_STATUS] = statusMessage
        }
    }

    suspend fun disconnectProfitChart() {
        context.investPrefsDataStore.edit {
            it[Keys.PROFIT_AUTO_SYNC] = false
            it[Keys.PROFIT_LAST_STATUS] = "Desconectado manualmente"
        }
    }
}
