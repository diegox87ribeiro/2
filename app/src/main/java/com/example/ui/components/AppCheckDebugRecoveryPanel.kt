package com.example.ui.components

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.BuildConfig
import com.example.ai.getCurrentAppCheckDebugToken
import com.example.ai.getFirebaseAppCheckConsoleInfo
import com.example.ai.updateAppCheckDebugToken
import com.example.ai.verifyAppCheckTokenLive
import com.example.ui.theme.AmberGold
import com.example.ui.theme.CardElevated
import com.example.ui.theme.CoralRed
import com.example.ui.theme.ElectricCyan
import com.example.ui.theme.EmeraldOnPrimary
import com.example.ui.theme.EmeraldPrimary
import com.example.ui.theme.JetBrainsMonoFamily
import com.example.ui.theme.TextSecondary
import kotlinx.coroutines.launch

/**
 * Painel interativo exibido apenas em builds Debug (`app-debug.apk`) quando uma chamada
 * ao Gemini via Firebase AI Logic retorna erro de App Check.
 * Permite copiar o token de depuração do aparelho (para registrar uma única vez no Firebase Console)
 * ou colar um token já cadastrado e validar imediatamente com o servidor.
 */
@Composable
fun AppCheckDebugRecoveryPanel(
    modifier: Modifier = Modifier,
    onTokenValidated: (() -> Unit)? = null
) {
    if (!BuildConfig.DEBUG) return

    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val consoleInfo = remember(context) { getFirebaseAppCheckConsoleInfo(context) }
    var tokenInput by remember(context) {
        mutableStateOf(getCurrentAppCheckDebugToken(context).orEmpty())
    }
    var isValidating by remember { mutableStateOf(false) }
    var feedbackMessage by remember { mutableStateOf<String?>(null) }
    var feedbackIsSuccess by remember { mutableStateOf(false) }

    Surface(
        shape = RoundedCornerShape(12.dp),
        color = CardElevated,
        border = BorderStroke(1.dp, AmberGold.copy(alpha = 0.55f)),
        modifier = modifier
            .fillMaxWidth()
            .testTag("appcheck_debug_recovery_panel")
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Key,
                    contentDescription = "Configuração de Token App Check Debug",
                    tint = AmberGold,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    text = "Ativar App Check no APK Debug",
                    style = MaterialTheme.typography.titleSmall,
                    color = AmberGold,
                    fontWeight = FontWeight.SemiBold
                )
            }

            Text(
                text = "1. Copie o token abaixo (ou cole um token de depuração já cadastrado).\n" +
                    "2. No Firebase Console (${consoleInfo.projectId}) → App Check → Apps → ${consoleInfo.packageName} → ⋮ → Gerenciar tokens de depuração → Adicionar.\n" +
                    "3. Toque em \"Salvar e Validar\".",
                style = MaterialTheme.typography.bodySmall,
                color = TextSecondary
            )

            OutlinedTextField(
                value = tokenInput,
                onValueChange = { tokenInput = it },
                label = { Text("Debug Token App Check (UUID)") },
                singleLine = true,
                textStyle = MaterialTheme.typography.bodySmall.copy(fontFamily = JetBrainsMonoFamily),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("appcheck_debug_token_input")
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = {
                        val trimmed = tokenInput.trim()
                        if (trimmed.isNotEmpty()) {
                            val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                            cm?.setPrimaryClip(ClipData.newPlainText("App Check Debug Token", trimmed))
                            Toast.makeText(context, "Token App Check copiado!", Toast.LENGTH_SHORT).show()
                            feedbackIsSuccess = true
                            feedbackMessage = "Token copiado. Cadastre-o no Firebase Console e toque em \"Salvar e Validar\"."
                        }
                    },
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 48.dp)
                        .testTag("appcheck_copy_token_button")
                ) {
                    Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Copiar Token")
                }

                Button(
                    onClick = {
                        scope.launch {
                            isValidating = true
                            feedbackMessage = null
                            val updateResult = updateAppCheckDebugToken(context, tokenInput)
                            if (updateResult.isFailure) {
                                feedbackIsSuccess = false
                                feedbackMessage = updateResult.exceptionOrNull()?.localizedMessage
                                    ?: "Não foi possível salvar o token informado."
                                isValidating = false
                                return@launch
                            }
                            tokenInput = updateResult.getOrDefault(tokenInput.trim())
                            val verifyResult = verifyAppCheckTokenLive(context)
                            isValidating = false
                            if (verifyResult.isSuccess) {
                                feedbackIsSuccess = true
                                feedbackMessage = verifyResult.getOrNull()
                                    ?: "App Check validado com sucesso!"
                                onTokenValidated?.invoke()
                            } else {
                                feedbackIsSuccess = false
                                val errMsg = verifyResult.exceptionOrNull()?.localizedMessage
                                    ?: "Token ainda não autorizado pelo Firebase."
                                feedbackMessage = "Validação recusada: $errMsg. Confirme se este exato UUID foi salvo em Firebase Console > App Check > Gerenciar tokens de depuração."
                            }
                        }
                    },
                    enabled = !isValidating && tokenInput.isNotBlank(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = ElectricCyan,
                        contentColor = EmeraldOnPrimary
                    ),
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 48.dp)
                        .testTag("appcheck_validate_token_button")
                ) {
                    if (isValidating) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp,
                            color = EmeraldOnPrimary
                        )
                    } else {
                        Icon(Icons.Default.VerifiedUser, contentDescription = null, modifier = Modifier.size(16.dp))
                    }
                    Spacer(Modifier.width(6.dp))
                    Text(if (isValidating) "Validando..." else "Salvar e Validar")
                }
            }

            if (!feedbackMessage.isNullOrBlank()) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.testTag("appcheck_validation_feedback")
                ) {
                    if (feedbackIsSuccess) {
                        Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = null,
                            tint = EmeraldPrimary,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                    Text(
                        text = feedbackMessage.orEmpty(),
                        style = MaterialTheme.typography.bodySmall,
                        color = if (feedbackIsSuccess) EmeraldPrimary else CoralRed
                    )
                }
            }
        }
    }
}
