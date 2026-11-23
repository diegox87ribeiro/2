package com.example.ai

import android.content.Context
import android.content.Intent
import android.util.Log
import com.example.BuildConfig
import com.google.firebase.FirebaseApp
import com.google.firebase.appcheck.FirebaseAppCheck
import com.google.firebase.appcheck.debug.DebugAppCheckProviderFactory
import com.google.firebase.appcheck.recaptcha.RecaptchaAppCheckProviderFactory

private const val TAG = "AppCheckInit"

// Chaves contratuais do Firebase App Check SDK e do harness do emulador (não renomear).
private const val DEBUG_TOKEN_EXTRA = "FIREBASE_APPCHECK_DEBUG_TOKEN"
private const val DEBUG_STORE_PREFIX = "com.google.firebase.appcheck.debug.store."
private const val DEBUG_SECRET_KEY = "com.google.firebase.appcheck.debug.DEBUG_SECRET"
private const val RECAPTCHA_SITE_KEY_RES = "recaptcha_site_key"

// Metadados internos para distinguir um emulador/harness com token de depuração injetado
// de um dispositivo físico instalando app-debug.apk diretamente pelo ícone do launcher.
private const val APPCHECK_META_PREFS = "invest_ai_appcheck_meta"
private const val KEY_HAS_HARNESS_DEBUG_TOKEN = "has_harness_debug_token"

enum class AppCheckProviderMode {
    DEBUG_HARNESS_TOKEN,
    RECAPTCHA_ENTERPRISE,
    DEBUG_FALLBACK
}

@Volatile
private var installedProviderMode: AppCheckProviderMode? = null

internal fun resolveAppCheckMode(
    isDebugBuild: Boolean,
    intentDebugToken: String?,
    hasSavedHarnessDebugToken: Boolean,
    recaptchaSiteKey: String?
): AppCheckProviderMode {
    if (!isDebugBuild) {
        return AppCheckProviderMode.RECAPTCHA_ENTERPRISE
    }
    if (!intentDebugToken.isNullOrBlank() || hasSavedHarnessDebugToken) {
        return AppCheckProviderMode.DEBUG_HARNESS_TOKEN
    }
    if (!recaptchaSiteKey.isNullOrBlank()) {
        return AppCheckProviderMode.RECAPTCHA_ENTERPRISE
    }
    return AppCheckProviderMode.DEBUG_FALLBACK
}

fun configureAppCheck(context: Context, intent: Intent?) {
    try {
        if (FirebaseApp.getApps(context).isEmpty()) {
            FirebaseApp.initializeApp(context)
        }

        val intentDebugToken = intent?.getStringExtra(DEBUG_TOKEN_EXTRA)?.trim()
        val metaPrefs = context.getSharedPreferences(APPCHECK_META_PREFS, Context.MODE_PRIVATE)
        val hasSavedHarnessDebugToken = metaPrefs.getBoolean(KEY_HAS_HARNESS_DEBUG_TOKEN, false)
        val siteKey = resolveRecaptchaSiteKey(context)

        val mode = resolveAppCheckMode(
            isDebugBuild = BuildConfig.DEBUG,
            intentDebugToken = intentDebugToken,
            hasSavedHarnessDebugToken = hasSavedHarnessDebugToken,
            recaptchaSiteKey = siteKey
        )

        when (mode) {
            AppCheckProviderMode.DEBUG_HARNESS_TOKEN -> {
                if (!intentDebugToken.isNullOrBlank()) {
                    val persistenceKey = FirebaseApp.getInstance().persistenceKey
                    val debugPrefs = context.getSharedPreferences(
                        "$DEBUG_STORE_PREFIX$persistenceKey",
                        Context.MODE_PRIVATE
                    )
                    // CRÍTICO: usar commit() síncrono antes do DebugAppCheckProviderFactory ler o segredo.
                    debugPrefs.edit()
                        .putString(DEBUG_SECRET_KEY, intentDebugToken)
                        .commit()
                    metaPrefs.edit()
                        .putBoolean(KEY_HAS_HARNESS_DEBUG_TOKEN, true)
                        .commit()
                    Log.d(TAG, "App Check debug secret configurado via Intent com sucesso.")
                }
                if (installedProviderMode != AppCheckProviderMode.DEBUG_HARNESS_TOKEN || !intentDebugToken.isNullOrBlank()) {
                    FirebaseAppCheck.getInstance().installAppCheckProviderFactory(
                        DebugAppCheckProviderFactory.getInstance()
                    )
                    installedProviderMode = AppCheckProviderMode.DEBUG_HARNESS_TOKEN
                    Log.d(TAG, "DebugAppCheckProviderFactory instalado (harness debug token).")
                }
            }

            AppCheckProviderMode.RECAPTCHA_ENTERPRISE -> {
                if (!siteKey.isNullOrBlank()) {
                    if (installedProviderMode != AppCheckProviderMode.RECAPTCHA_ENTERPRISE) {
                        FirebaseAppCheck.getInstance().installAppCheckProviderFactory(
                            RecaptchaAppCheckProviderFactory.getInstance(siteKey)
                        )
                        installedProviderMode = AppCheckProviderMode.RECAPTCHA_ENTERPRISE
                        Log.d(
                            TAG,
                            if (BuildConfig.DEBUG) {
                                "RecaptchaAppCheckProviderFactory instalado em APK Debug (dispositivo físico sem token via Intent)."
                            } else {
                                "RecaptchaAppCheckProviderFactory instalado (Release)."
                            }
                        )
                    }
                } else {
                    Log.e(TAG, "reCAPTCHA site key não encontrada ou em branco nos recursos.")
                }
            }

            AppCheckProviderMode.DEBUG_FALLBACK -> {
                Log.w(TAG, "Nenhum token de debug via Intent nem recaptcha_site_key encontrados; usando DebugAppCheckProviderFactory.")
                if (installedProviderMode != AppCheckProviderMode.DEBUG_FALLBACK) {
                    FirebaseAppCheck.getInstance().installAppCheckProviderFactory(
                        DebugAppCheckProviderFactory.getInstance()
                    )
                    installedProviderMode = AppCheckProviderMode.DEBUG_FALLBACK
                }
            }
        }
    } catch (e: Exception) {
        Log.e(TAG, "Falha ao inicializar Firebase App Check", e)
    }
}

private fun resolveRecaptchaSiteKey(context: Context): String? {
    val resId = context.resources.getIdentifier(
        RECAPTCHA_SITE_KEY_RES,
        "string",
        context.packageName
    )
    if (resId == 0) return null
    return runCatching { context.getString(resId).trim() }
        .getOrNull()
        ?.takeIf { it.isNotBlank() }
}

fun formatFirebaseAiError(error: Throwable, fallback: String): String {
    val raw = error.localizedMessage?.takeIf { it.isNotBlank() } ?: fallback
    val lower = raw.lowercase()
    return if (lower.contains("app check") || lower.contains("appcheck") || lower.contains("403")) {
        "$raw\n\nDica App Check: verifique sua conexão com a internet e o Google Play Services atualizado (atestação reCAPTCHA Enterprise) ou reabra o app e tente novamente."
    } else {
        raw
    }
}
