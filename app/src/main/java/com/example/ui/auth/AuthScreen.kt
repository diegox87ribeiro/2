package com.example.ui.auth

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialException
import androidx.credentials.exceptions.GetCredentialInterruptedException
import androidx.credentials.exceptions.GetCredentialProviderConfigurationException
import androidx.credentials.exceptions.GetCredentialUnsupportedException
import androidx.credentials.exceptions.NoCredentialException
import com.example.ui.components.FintechCard
import com.example.ui.theme.CoralRed
import com.example.ui.theme.ElectricCyan
import com.example.ui.theme.EmeraldContainer
import com.example.ui.theme.EmeraldOnPrimary
import com.example.ui.theme.EmeraldPrimary
import com.example.ui.theme.MonoValueStyle
import com.example.ui.theme.ObsidianBg
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential.Companion.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.auth.auth
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.security.MessageDigest

internal fun FirebaseAuth.authStateFlow(): Flow<FirebaseUser?> = callbackFlow {
    val listener = FirebaseAuth.AuthStateListener { auth ->
        trySend(auth.currentUser)
    }
    addAuthStateListener(listener)
    awaitClose { removeAuthStateListener(listener) }
}

internal tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

internal fun getApkSigningSha1(context: Context): String? {
    return runCatching {
        val pm = context.packageManager
        val certBytes: ByteArray? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val info = pm.getPackageInfo(context.packageName, PackageManager.GET_SIGNING_CERTIFICATES)
            val signingInfo = info.signingInfo ?: return@runCatching null
            val sigs = if (signingInfo.hasMultipleSigners()) {
                signingInfo.apkContentsSigners
            } else {
                signingInfo.signingCertificateHistory
            }
            sigs?.firstOrNull()?.toByteArray()
        } else {
            @Suppress("DEPRECATION")
            val info = pm.getPackageInfo(context.packageName, PackageManager.GET_SIGNATURES)
            @Suppress("DEPRECATION")
            info.signatures?.firstOrNull()?.toByteArray()
        }
        val bytes = certBytes ?: return@runCatching null
        val digest = MessageDigest.getInstance("SHA-1").digest(bytes)
        digest.joinToString(":") { b -> "%02X".format(b) }
    }.getOrNull()
}

/**
 * Reads the OAuth web client ID generated by the Google Services Gradle plugin,
 * without a compile-time R reference so builds still work when Firebase config
 * is not present. Google Sign-In requires this value at runtime.
 */
private fun getGoogleWebClientId(context: Context): String? {
    val resourceId = context.resources.getIdentifier(
        "default_web_client_id",
        "string",
        context.packageName
    )

    if (resourceId == 0) return null

    return context.getString(resourceId)
        .trim()
        .takeIf { it.isNotEmpty() }
}

fun attemptAutoSignIn(
    context: Context,
    credentialManager: CredentialManager?,
    onAuthSuccess: () -> Unit,
    onUnauthenticated: () -> Unit,
    scope: CoroutineScope
): Job? {
    val auth = runCatching { Firebase.auth }.getOrNull()
    if (auth == null) {
        onUnauthenticated()
        return null
    }
    if (auth.currentUser != null) {
        onAuthSuccess()
        return null
    }
    if (credentialManager == null) {
        onUnauthenticated()
        return null
    }

    val clientId = getGoogleWebClientId(context) ?: run {
        onUnauthenticated()
        return null
    }

    val googleIdOption = GetGoogleIdOption.Builder()
        .setFilterByAuthorizedAccounts(true)
        .setServerClientId(clientId)
        .setAutoSelectEnabled(true)
        .build()

    val request = GetCredentialRequest.Builder()
        .addCredentialOption(googleIdOption)
        .build()

    return scope.launch {
        try {
            val targetContext = context.findActivity() ?: context
            val result = credentialManager.getCredential(targetContext, request)
            val credential = result.credential
            if (credential is CustomCredential && credential.type == TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
                val googleIdToken = GoogleIdTokenCredential.createFrom(credential.data).idToken
                val authCredential = GoogleAuthProvider.getCredential(googleIdToken, null)
                auth.signInWithCredential(authCredential).await()
                onAuthSuccess()
            } else {
                onUnauthenticated()
            }
        } catch (t: Throwable) {
            if (t is CancellationException) throw t
            Log.w("Auth", "Auto sign-in unavailable: ${t.message}")
            onUnauthenticated()
        }
    }
}

fun onGoogleSignInClicked(
    context: Context,
    credentialManager: CredentialManager?,
    onAuthSuccess: () -> Unit,
    onAuthError: (String) -> Unit,
    scope: CoroutineScope,
    onAuthCancelled: () -> Unit = {},
    clearExistingSessionFirst: Boolean = false,
    onAuthCancelledDetail: (String?) -> Unit = { onAuthCancelled() },
    onDiagnosticDetail: (String?) -> Unit = {}
) {
    val auth = runCatching { Firebase.auth }.getOrNull() ?: run {
        onAuthError("FirebaseAuth não inicializado neste dispositivo.")
        return
    }

    if (credentialManager == null) {
        onAuthError("Gerenciador de credenciais Google indisponível neste dispositivo.")
        return
    }

    val clientId = getGoogleWebClientId(context) ?: run {
        onAuthError("Configuração Google Sign-In ausente: configure um cliente OAuth Web no Firebase.")
        return
    }

    val activity = context.findActivity()
    if (activity == null) {
        onAuthError("Contexto de Activity indisponível para autenticação Google.")
        return
    }

    val signInOption = GetSignInWithGoogleOption.Builder(serverClientId = clientId).build()
    val request = GetCredentialRequest.Builder()
        .addCredentialOption(signInOption)
        .build()

    scope.launch {
        try {
            if (clearExistingSessionFirst) {
                runCatching { credentialManager.clearCredentialState(ClearCredentialStateRequest()) }
            }
            val result = credentialManager.getCredential(activity, request)
            val credential = result.credential
            if (credential is CustomCredential && credential.type == TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
                val googleIdToken = GoogleIdTokenCredential.createFrom(credential.data).idToken
                val authCredential = GoogleAuthProvider.getCredential(googleIdToken, null)
                auth.signInWithCredential(authCredential).await()
                onAuthSuccess()
            } else {
                onDiagnosticDetail("Tipo recebido: ${credential.type}")
                onAuthError("Tipo de credencial inesperado recebido do Google Play Services.")
            }
        } catch (e: GetCredentialCancellationException) {
            val detail = listOfNotNull(
                e.type.takeIf { it.isNotBlank() },
                (e.errorMessage?.toString() ?: e.message)?.takeIf { it.isNotBlank() }
            ).joinToString(" • ").ifBlank { null }
            Log.w("Auth", "Google Sign-In flow cancelled or dismissed: $detail", e)
            onDiagnosticDetail(detail)
            onAuthCancelledDetail(detail)
        } catch (e: NoCredentialException) {
            val detail = "${e.type}: ${e.errorMessage ?: e.message ?: "NoCredential"}"
            Log.w("Auth", "No Google credentials available: $detail", e)
            onDiagnosticDetail(detail)
            onAuthError("Nenhuma conta Google disponível ou prompts de login desativados nas configurações Google do aparelho.")
        } catch (e: GetCredentialInterruptedException) {
            val detail = "${e.type}: ${e.errorMessage ?: e.message ?: "Interrupted"}"
            Log.w("Auth", "Google Sign-In interrupted: $detail", e)
            onDiagnosticDetail(detail)
            onAuthError("A seleção de conta foi interrompida pelo sistema. Tente novamente.")
        } catch (e: GetCredentialProviderConfigurationException) {
            val detail = "${e.type}: ${e.errorMessage ?: e.message ?: "ProviderConfig"}"
            Log.e("Auth", "Credential provider configuration error: $detail", e)
            onDiagnosticDetail(detail)
            onAuthError("Provedor Google Play Services indisponível para Credential Manager neste aparelho.")
        } catch (e: GetCredentialUnsupportedException) {
            val detail = "${e.type}: ${e.errorMessage ?: e.message ?: "Unsupported"}"
            Log.e("Auth", "Credential Manager unsupported: $detail", e)
            onDiagnosticDetail(detail)
            onAuthError("Credential Manager não suportado ou Google Play Services desatualizado neste aparelho.")
        } catch (e: GetCredentialException) {
            val detail = "${e.type}: ${e.errorMessage ?: e.message ?: "GetCredentialException"}"
            Log.e("Auth", "Google Sign-In GetCredentialException: $detail", e)
            onDiagnosticDetail(detail)
            onAuthError(e.errorMessage?.toString() ?: e.localizedMessage ?: "Falha na autenticação com Google.")
        } catch (t: Throwable) {
            if (t is CancellationException) throw t
            Log.e("Auth", "Google Sign-In failed", t)
            onDiagnosticDetail("${t::class.java.simpleName}: ${t.message}")
            onAuthError(t.localizedMessage ?: "Falha na autenticação com Google.")
        }
    }
}

fun signOutUser(
    credentialManager: CredentialManager?,
    scope: CoroutineScope,
    onSignOutComplete: () -> Unit = {}
) {
    scope.launch {
        withContext(NonCancellable) {
            try {
                credentialManager?.clearCredentialState(ClearCredentialStateRequest())
            } catch (e: Exception) {
                Log.w("Auth", "Failed to clear credential state: ${e.message}")
            } finally {
                runCatching { Firebase.auth.signOut() }
                onSignOutComplete()
            }
        }
    }
}

@Composable
fun AuthScreen(
    onAuthSuccess: () -> Unit = {}
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val credentialManager = remember(context) {
        runCatching { CredentialManager.create(context) }.getOrNull()
    }
    val apkSha1 = remember(context) { getApkSigningSha1(context) }

    var isLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var technicalDetail by remember { mutableStateOf<String?>(null) }
    var sha1Copied by remember { mutableStateOf(false) }
    var autoSignInJob by remember { mutableStateOf<Job?>(null) }

    LaunchedEffect(Unit) {
        autoSignInJob = attemptAutoSignIn(
            context = context,
            credentialManager = credentialManager,
            onAuthSuccess = onAuthSuccess,
            onUnauthenticated = {},
            scope = scope
        )
    }

    fun launchInteractiveSignIn(clearSessionFirst: Boolean) {
        autoSignInJob?.cancel()
        autoSignInJob = null
        errorMessage = null
        technicalDetail = null
        sha1Copied = false
        isLoading = true
        onGoogleSignInClicked(
            context = context,
            credentialManager = credentialManager,
            onAuthSuccess = {
                isLoading = false
                onAuthSuccess()
            },
            onAuthError = { err ->
                isLoading = false
                errorMessage = err
            },
            scope = scope,
            clearExistingSessionFirst = clearSessionFirst,
            onAuthCancelledDetail = { _ ->
                isLoading = false
                errorMessage = "Login com Google cancelado ou recusado ao selecionar a conta."
            },
            onDiagnosticDetail = { detail ->
                technicalDetail = detail
            }
        )
    }

    Scaffold(
        containerColor = ObsidianBg,
        contentWindowInsets = WindowInsets.safeDrawing
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(
                modifier = Modifier.widthIn(max = 460.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(
                    modifier = Modifier
                        .size(64.dp)
                        .clip(RoundedCornerShape(18.dp))
                        .background(EmeraldContainer),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Default.Shield,
                        contentDescription = "INVEST AI",
                        tint = EmeraldPrimary,
                        modifier = Modifier.size(34.dp)
                    )
                }

                Text("INVEST AI", style = MaterialTheme.typography.headlineMedium, color = EmeraldPrimary)
                Text(
                    "Plataforma de Análise, Auditoria HHI & Relatórios Gerenciais",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSecondary
                )

                FintechCard(borderColor = ElectricCyan.copy(alpha = 0.4f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Lock, contentDescription = null, tint = ElectricCyan, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Acesso Seguro com Conta Google", style = MaterialTheme.typography.titleSmall, color = ElectricCyan)
                    }
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "• Seus relatórios consolidados e pareceres dos agentes ficam isolados na sua conta via Firebase Auth + Firestore.\n" +
                            "• Nenhuma senha bancária é solicitada e nenhuma ordem financeira é executada.",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary
                    )
                }

                errorMessage?.let { msg ->
                    FintechCard(borderColor = CoralRed) {
                        Text(msg, style = MaterialTheme.typography.bodySmall, color = CoralRed)
                        technicalDetail?.let { detail ->
                            Spacer(Modifier.height(6.dp))
                            Text(
                                "Detalhe técnico: $detail",
                                style = MaterialTheme.typography.bodySmall,
                                color = TextSecondary
                            )
                        }
                        if (apkSha1 != null) {
                            Spacer(Modifier.height(8.dp))
                            Text(
                                "Se o seletor de contas fecha logo após tocar na conta, o certificado SHA-1 deste APK pode não estar autorizado no Firebase/Google Cloud:",
                                style = MaterialTheme.typography.bodySmall,
                                color = TextSecondary
                            )
                            Spacer(Modifier.height(6.dp))
                            Text(
                                "Pacote: ${context.packageName}\nSHA-1 do APK: $apkSha1",
                                style = MonoValueStyle,
                                color = TextPrimary
                            )
                            Spacer(Modifier.height(10.dp))
                            OutlinedButton(
                                onClick = {
                                    val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                                    cm?.setPrimaryClip(ClipData.newPlainText("INVEST AI SHA-1", apkSha1))
                                    sha1Copied = true
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(min = 48.dp)
                                    .testTag("copy_sha1_button")
                            ) {
                                Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(8.dp))
                                Text(if (sha1Copied) "SHA-1 copiado!" else "Copiar SHA-1 deste APK")
                            }
                        }
                        Spacer(Modifier.height(8.dp))
                        OutlinedButton(
                            onClick = { launchInteractiveSignIn(clearSessionFirst = true) },
                            enabled = !isLoading,
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 48.dp)
                                .testTag("clear_and_retry_google_sign_in_button")
                        ) {
                            Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("Limpar sessão Google e tentar novamente")
                        }
                    }
                }

                Button(
                    onClick = { launchInteractiveSignIn(clearSessionFirst = false) },
                    enabled = !isLoading,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                        .testTag("google_sign_in_button"),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = EmeraldPrimary,
                        contentColor = EmeraldOnPrimary
                    )
                ) {
                    if (isLoading) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(22.dp),
                            color = EmeraldOnPrimary,
                            strokeWidth = 2.dp
                        )
                    } else {
                        Icon(Icons.Default.AccountCircle, contentDescription = null, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Entrar com Google")
                    }
                }
            }
        }
    }
}

