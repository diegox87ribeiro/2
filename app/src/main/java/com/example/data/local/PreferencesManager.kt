package com.example.data.local

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
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
    val profitLastStatusMessage: String = "",
    val hideInfoBanners: Boolean = false,
    val targetPctAcoes: Double = 35.0,
    val targetPctFii: Double = 25.0,
    val targetPctFiagro: Double = 10.0,
    val targetPctEtf: Double = 15.0,
    val targetPctRendaFixa: Double = 10.0,
    val targetPctCripto: Double = 5.0,
    val carryLossCommonBrl: Double = 0.0,
    val carryLossFiiBrl: Double = 0.0,
    val dividendAlertsEnabled: Boolean = true,
    val dividendAlertLeadDays: Int = 3,
    val dividendAlertDataCom: Boolean = true,
    val dividendAlertDataEx: Boolean = true,
    val dividendAlertSentKeys: Set<String> = emptySet()
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
        val HIDE_INFO_BANNERS = booleanPreferencesKey("hide_info_banners")
        val TARGET_ACOES = doublePreferencesKey("target_pct_acoes")
        val TARGET_FII = doublePreferencesKey("target_pct_fii")
        val TARGET_FIAGRO = doublePreferencesKey("target_pct_fiagro")
        val TARGET_ETF = doublePreferencesKey("target_pct_etf")
        val TARGET_RF = doublePreferencesKey("target_pct_rf")
        val TARGET_CRIPTO = doublePreferencesKey("target_pct_cripto")
        val CARRY_LOSS_COMMON = doublePreferencesKey("carry_loss_common_brl")
        val CARRY_LOSS_FII = doublePreferencesKey("carry_loss_fii_brl")
        val DIV_ALERTS_ENABLED = booleanPreferencesKey("dividend_alerts_enabled")
        val DIV_ALERTS_LEAD_DAYS = intPreferencesKey("dividend_alerts_lead_days")
        val DIV_ALERTS_DATACOM = booleanPreferencesKey("dividend_alerts_datacom")
        val DIV_ALERTS_DATAEX = booleanPreferencesKey("dividend_alerts_dataex")
        val DIV_ALERTS_SENT_KEYS = stringSetPreferencesKey("dividend_alerts_sent_keys")
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
            profitLastStatusMessage = prefs[Keys.PROFIT_LAST_STATUS] ?: "",
            hideInfoBanners = prefs[Keys.HIDE_INFO_BANNERS] ?: false,
            targetPctAcoes = prefs[Keys.TARGET_ACOES] ?: 35.0,
            targetPctFii = prefs[Keys.TARGET_FII] ?: 25.0,
            targetPctFiagro = prefs[Keys.TARGET_FIAGRO] ?: 10.0,
            targetPctEtf = prefs[Keys.TARGET_ETF] ?: 15.0,
            targetPctRendaFixa = prefs[Keys.TARGET_RF] ?: 10.0,
            targetPctCripto = prefs[Keys.TARGET_CRIPTO] ?: 5.0,
            carryLossCommonBrl = prefs[Keys.CARRY_LOSS_COMMON] ?: 0.0,
            carryLossFiiBrl = prefs[Keys.CARRY_LOSS_FII] ?: 0.0,
            dividendAlertsEnabled = prefs[Keys.DIV_ALERTS_ENABLED] ?: true,
            dividendAlertLeadDays = (prefs[Keys.DIV_ALERTS_LEAD_DAYS] ?: 3).coerceIn(1, 14),
            dividendAlertDataCom = prefs[Keys.DIV_ALERTS_DATACOM] ?: true,
            dividendAlertDataEx = prefs[Keys.DIV_ALERTS_DATAEX] ?: true,
            dividendAlertSentKeys = prefs[Keys.DIV_ALERTS_SENT_KEYS] ?: emptySet()
        )
    }

    suspend fun getCurrentPreferences(): AppPreferencesState = preferencesFlow.first()

    suspend fun setCashRealBrl(amount: Double) {
        context.investPrefsDataStore.edit { it[Keys.CASH_REAL] = amount.coerceAtLeast(0.0) }
    }

    suspend fun setCashSimBrl(amount: Double) {
        context.investPrefsDataStore.edit { it[Keys.CASH_SIM] = amount.coerceAtLeast(0.0) }
    }

    suspend fun setAiPortfolioConsent(granted: Boolean) {
        context.investPrefsDataStore.edit { it[Keys.AI_CONSENT] = granted }
    }

    suspend fun setHideInfoBanners(hide: Boolean) {
        context.investPrefsDataStore.edit { it[Keys.HIDE_INFO_BANNERS] = hide }
    }

    suspend fun saveRebalanceTargets(
        acoes: Double,
        fii: Double,
        fiagro: Double,
        etf: Double,
        rendaFixa: Double,
        cripto: Double
    ) {
        context.investPrefsDataStore.edit {
            it[Keys.TARGET_ACOES] = acoes.coerceAtLeast(0.0)
            it[Keys.TARGET_FII] = fii.coerceAtLeast(0.0)
            it[Keys.TARGET_FIAGRO] = fiagro.coerceAtLeast(0.0)
            it[Keys.TARGET_ETF] = etf.coerceAtLeast(0.0)
            it[Keys.TARGET_RF] = rendaFixa.coerceAtLeast(0.0)
            it[Keys.TARGET_CRIPTO] = cripto.coerceAtLeast(0.0)
        }
    }

    suspend fun saveTaxCarryLoss(commonBrl: Double, fiiBrl: Double) {
        context.investPrefsDataStore.edit {
            it[Keys.CARRY_LOSS_COMMON] = commonBrl.coerceAtLeast(0.0)
            it[Keys.CARRY_LOSS_FII] = fiiBrl.coerceAtLeast(0.0)
        }
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

    suspend fun saveDividendDateAlertConfig(
        enabled: Boolean,
        leadDays: Int,
        notifyDataCom: Boolean,
        notifyDataEx: Boolean
    ) {
        context.investPrefsDataStore.edit {
            it[Keys.DIV_ALERTS_ENABLED] = enabled
            it[Keys.DIV_ALERTS_LEAD_DAYS] = leadDays.coerceIn(1, 14)
            it[Keys.DIV_ALERTS_DATACOM] = notifyDataCom
            it[Keys.DIV_ALERTS_DATAEX] = notifyDataEx
        }
    }

    suspend fun recordSentDividendAlertKeys(newKeys: Collection<String>) {
        if (newKeys.isEmpty()) return
        context.investPrefsDataStore.edit { prefs ->
            val existing = prefs[Keys.DIV_ALERTS_SENT_KEYS].orEmpty()
            val merged = (existing + newKeys).toList().takeLast(80).toSet()
            prefs[Keys.DIV_ALERTS_SENT_KEYS] = merged
        }
    }
}
