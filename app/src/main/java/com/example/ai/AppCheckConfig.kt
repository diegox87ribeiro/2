package com.example.ai

import android.content.Context
import android.content.Intent
import android.util.Log
import com.example.BuildConfig
import com.google.firebase.FirebaseApp
import com.google.firebase.appcheck.FirebaseAppCheck
import com.google.firebase.appcheck.debug.DebugAppCheckProviderFactory
import com.google.firebase.appcheck.recaptcha.RecaptchaAppCheckProviderFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import java.util.UUID
import kotlin.coroutines.resume

private const val TAG = "AppCheckInit"

// Chaves contratuais do Firebase App Check SDK e do harness do emulador (não renomear).
private const val DEBUG_TOKEN_EXTRA = "FIREBASE_APPCHECK_DEBUG_TOKEN"
private const val DEBUG_STORE_PREFIX = "com.google.firebase.appcheck.debug.store."
private const val TOKEN_CACHE_STORE_PREFIX = "com.google.firebase.appcheck.store."
private const val DEBUG_SECRET_KEY = "com.google.firebase.appcheck.debug.DEBUG_SECRET"
private const val RECAPTCHA_SITE_KEY_RES = "recaptcha_site_key"

enum class AppCheckProviderMode {
    DEBUG_TOKEN,
    RECAPTCHA_ENTERPRISE
}

data class FirebaseAppCheckConsoleInfo(
    val projectId: String,
    val packageName: String,
    val isDebugBuild: Boolean
)

@Volatile
private var installedProviderMode: AppCheckProviderMode? = null

internal fun resolveAppCheckMode(
    isDebugBuild: Boolean,
    intentDebugToken: String?,
    recaptchaSiteKey: String?
): AppCheckProviderMode {
    // Contrato oficial Firebase AI Logic (references/app-check.md):
    // - Build Debug (emulador ou APK Debug instalado manualmente): usa DebugAppCheckProviderFactory
    //   porque reCAPTCHA Enterprise / Play Integrity recusam APKs debug sideloaded.
    // - Build Release: usa RecaptchaAppCheckProviderFactory com recaptcha_site_key.
    return if (isDebugBuild) {
        AppCheckProviderMode.DEBUG_TOKEN
    } else {
        AppCheckProviderMode.RECAPTCHA_ENTERPRISE
    }
}

fun configureAppCheck(context: Context, intent: Intent?) {
    try {
        if (FirebaseApp.getApps(context).isEmpty()) {
            FirebaseApp.initializeApp(context)
        }

        val intentDebugToken = intent?.getStringExtra(DEBUG_TOKEN_EXTRA)?.trim()
        val siteKey = resolveRecaptchaSiteKey(context)

        val mode = resolveAppCheckMode(
            isDebugBuild = BuildConfig.DEBUG,
            intentDebugToken = intentDebugToken,
            recaptchaSiteKey = siteKey
        )

        when (mode) {
            AppCheckProviderMode.DEBUG_TOKEN -> {
                val persistenceKey = FirebaseApp.getInstance().persistenceKey
                val debugPrefs = context.getSharedPreferences(
                    "$DEBUG_STORE_PREFIX$persistenceKey",
                    Context.MODE_PRIVATE
                )
                if (!intentDebugToken.isNullOrBlank()) {
                    // CRÍTICO: usar commit() síncrono antes do DebugAppCheckProviderFactory ler o segredo.
                    debugPrefs.edit()
                        .putString(DEBUG_SECRET_KEY, intentDebugToken)
                        .commit()
                    clearAppCheckTokenCache(context, persistenceKey)
                    Log.d(TAG, "App Check debug secret configurado via Intent com sucesso.")
                } else {
                    val existing = debugPrefs.getString(DEBUG_SECRET_KEY, null)?.trim()
                    if (existing.isNullOrBlank()) {
                        val generated = UUID.randomUUID().toString()
                        debugPrefs.edit()
                            .putString(DEBUG_SECRET_KEY, generated)
                            .commit()
                        Log.d(TAG, "App Check debug secret local gerado e persistido com commit().")
                    }
                }

                if (installedProviderMode != AppCheckProviderMode.DEBUG_TOKEN || !intentDebugToken.isNullOrBlank()) {
                    FirebaseAppCheck.getInstance().installAppCheckProviderFactory(
                        DebugAppCheckProviderFactory.getInstance()
                    )
                    installedProviderMode = AppCheckProviderMode.DEBUG_TOKEN
                    Log.d(TAG, "DebugAppCheckProviderFactory instalado.")
                }
            }

            AppCheckProviderMode.RECAPTCHA_ENTERPRISE -> {
                if (!siteKey.isNullOrBlank()) {
                    if (installedProviderMode != AppCheckProviderMode.RECAPTCHA_ENTERPRISE) {
                        FirebaseAppCheck.getInstance().installAppCheckProviderFactory(
                            RecaptchaAppCheckProviderFactory.getInstance(siteKey)
                        )
                        installedProviderMode = AppCheckProviderMode.RECAPTCHA_ENTERPRISE
                        Log.d(TAG, "RecaptchaAppCheckProviderFactory instalado (Release).")
                    }
                } else {
                    Log.e(TAG, "reCAPTCHA site key não encontrada ou em branco nos recursos.")
                }
            }
        }
    } catch (e: Exception) {
        Log.e(TAG, "Falha ao inicializar Firebase App Check", e)
    }
}

/**
 * Lê (ou inicializa de forma síncrona em Debug) o Debug Secret atualmente salvo no aparelho
 * pelo SDK `DebugAppCheckProviderFactory`.
 */
fun getCurrentAppCheckDebugToken(context: Context): String? {
    if (!BuildConfig.DEBUG) return null
    return runCatching {
        if (FirebaseApp.getApps(context).isEmpty()) {
            FirebaseApp.initializeApp(context)
        }
        val persistenceKey = FirebaseApp.getInstance().persistenceKey
        val debugPrefs = context.getSharedPreferences(
            "$DEBUG_STORE_PREFIX$persistenceKey",
            Context.MODE_PRIVATE
        )
        val existing = debugPrefs.getString(DEBUG_SECRET_KEY, null)?.trim()
        if (!existing.isNullOrBlank()) {
            existing
        } else {
            val generated = UUID.randomUUID().toString()
            debugPrefs.edit().putString(DEBUG_SECRET_KEY, generated).commit()
            generated
        }
    }.getOrNull()
}

/**
 * Atualiza o Debug Secret do Firebase App Check de forma síncrona (`.commit()`),
 * limpa o cache antigo de tokens e reinstala o `DebugAppCheckProviderFactory`.
 */
fun updateAppCheckDebugToken(context: Context, rawToken: String): Result<String> {
    if (!BuildConfig.DEBUG) {
        return Result.failure(IllegalStateException("Token de depuração só está disponível em builds Debug."))
    }
    val sanitized = rawToken.trim()
    if (sanitized.length < 8 || sanitized.any { it.isWhitespace() }) {
        return Result.failure(
            IllegalArgumentException("Token inválido. Cole o UUID completo do Firebase App Check (ex.: XXXXXXXX-XXXX-XXXX-XXXX-XXXXXXXXXXXX).")
        )
    }
    return runCatching {
        if (FirebaseApp.getApps(context).isEmpty()) {
            FirebaseApp.initializeApp(context)
        }
        val persistenceKey = FirebaseApp.getInstance().persistenceKey
        val debugPrefs = context.getSharedPreferences(
            "$DEBUG_STORE_PREFIX$persistenceKey",
            Context.MODE_PRIVATE
        )
        debugPrefs.edit()
            .putString(DEBUG_SECRET_KEY, sanitized)
            .commit()
        clearAppCheckTokenCache(context, persistenceKey)
        FirebaseAppCheck.getInstance().installAppCheckProviderFactory(
            DebugAppCheckProviderFactory.getInstance()
        )
        installedProviderMode = AppCheckProviderMode.DEBUG_TOKEN
        sanitized
    }
}

/**
 * Solicita um token atualizado ao Firebase App Check (forceRefresh = true)
 * para diagnosticar e confirmar imediatamente se o token atual é aceito pelo servidor.
 */
suspend fun verifyAppCheckTokenLive(context: Context): Result<String> = withContext(Dispatchers.IO) {
    runCatching {
        if (FirebaseApp.getApps(context).isEmpty()) {
            FirebaseApp.initializeApp(context)
        }
        withTimeout(15_000L) {
            suspendCancellableCoroutine<String> { cont ->
                FirebaseAppCheck.getInstance()
                    .getAppCheckToken(true)
                    .addOnSuccessListener { tokenResult ->
                        if (cont.isActive) {
                            val token = tokenResult.token
                            if (token.isNotBlank()) {
                                cont.resume("App Check validado com sucesso pelo Firebase! Você já pode usar a IA.")
                            } else {
                                cont.resumeWith(
                                    Result.failure(
                                        IllegalStateException("O Firebase retornou um token App Check vazio.")
                                    )
                                )
                            }
                        }
                    }
                    .addOnFailureListener { err ->
                        if (cont.isActive) {
                            cont.resumeWith(Result.failure(err))
                        }
                    }
            }
        }
    }
}

fun getFirebaseAppCheckConsoleInfo(context: Context): FirebaseAppCheckConsoleInfo {
    val projectId = runCatching {
        if (FirebaseApp.getApps(context).isEmpty()) {
            FirebaseApp.initializeApp(context)
        }
        FirebaseApp.getInstance().options.projectId
    }.getOrNull().orEmpty().ifBlank { "seu projeto Firebase" }

    return FirebaseAppCheckConsoleInfo(
        projectId = projectId,
        packageName = context.packageName,
        isDebugBuild = BuildConfig.DEBUG
    )
}

fun isAppCheckRelatedError(message: String?): Boolean {
    if (message.isNullOrBlank()) return false
    val lower = message.lowercase()
    return lower.contains("app check") ||
        lower.contains("appcheck") ||
        lower.contains("attestation") ||
        lower.contains("403")
}

private fun clearAppCheckTokenCache(context: Context, persistenceKey: String) {
    runCatching {
        context.getSharedPreferences("$TOKEN_CACHE_STORE_PREFIX$persistenceKey", Context.MODE_PRIVATE)
            .edit()
            .clear()
            .commit()
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
    return if (isAppCheckRelatedError(raw)) {
        if (BuildConfig.DEBUG) {
            "$raw\n\nEste APK está em modo Debug: cadastre o Token de Depuração do App Check exibido abaixo no Firebase Console (App Check > Apps > Gerenciar tokens de depuração) ou cole um token já cadastrado e toque em Validar."
        } else {
            "$raw\n\nDica App Check: verifique sua conexão com a internet e o Google Play Services atualizado (atestação reCAPTCHA Enterprise) ou reabra o app e tente novamente."
        }
    } else {
        raw
    }
}

