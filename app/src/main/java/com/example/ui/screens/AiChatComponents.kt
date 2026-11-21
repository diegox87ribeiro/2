package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.data.local.ChatMessageEntity
import com.example.data.repository.GeminiTaskModel
import com.example.domain.model.AiAgentRole
import com.example.domain.model.MarketAsset
import com.example.ui.components.FilterChipBar
import com.example.ui.components.FintechCard
import com.example.ui.theme.AmberGold
import com.example.ui.theme.CardElevated
import com.example.ui.theme.CoralRed
import com.example.ui.theme.DeepNavySurface
import com.example.ui.theme.ElectricCyan
import com.example.ui.theme.EmeraldContainer
import com.example.ui.theme.EmeraldOnPrimary
import com.example.ui.theme.EmeraldPrimary
import com.example.ui.theme.SubtleBorder
import com.example.ui.theme.TextSecondary
import com.example.ui.theme.VioletAccent
import com.example.ui.viewmodel.AiAudioUiState
import com.example.ui.viewmodel.AiChatUiState
import com.example.ui.viewmodel.VoiceCaptureMode
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun MultiTurnGeminiChatPanel(
    selectedRole: AiAgentRole,
    onSelectRole: (AiAgentRole) -> Unit,
    selectedModel: GeminiTaskModel,
    onSelectModel: (GeminiTaskModel) -> Unit,
    useGoogleSearch: Boolean = true,
    onToggleGoogleSearch: (Boolean) -> Unit = {},
    focusedTicker: String?,
    availableAssets: List<MarketAsset>,
    onSelectFocusedTicker: (String?) -> Unit,
    messages: List<ChatMessageEntity>,
    chatUiState: AiChatUiState,
    audioUiState: AiAudioUiState = AiAudioUiState.Idle,
    draft: String,
    onDraftChange: (String) -> Unit,
    onSendMessage: (String) -> Unit,
    onToggleTranscribeAudio: () -> Unit = {},
    onToggleVoiceConversation: () -> Unit = {},
    onStopSpeaking: () -> Unit = {},
    onDismissAudioError: () -> Unit = {},
    onClearHistory: () -> Unit,
    onDismissError: () -> Unit
) {
    val listState = rememberLazyListState()
    val isBusy = chatUiState is AiChatUiState.Loading ||
        chatUiState is AiChatUiState.Streaming ||
        audioUiState is AiAudioUiState.Processing
    val isRecordingTranscribe = audioUiState is AiAudioUiState.Recording &&
        audioUiState.mode == VoiceCaptureMode.TRANSCRIBE_ONLY
    val isRecordingVoiceChat = audioUiState is AiAudioUiState.Recording &&
        audioUiState.mode == VoiceCaptureMode.VOICE_CONVERSATION

    LaunchedEffect(messages.size, chatUiState) {
        val targetIndex = when {
            chatUiState is AiChatUiState.Streaming || chatUiState is AiChatUiState.Loading -> messages.size
            messages.isNotEmpty() -> messages.size - 1
            else -> 0
        }
        if (messages.isNotEmpty() || isBusy) {
            listState.animateScrollToItem(targetIndex.coerceAtLeast(0))
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        FintechCard(borderColor = ElectricCyan.copy(alpha = 0.35f)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                    Icon(
                        Icons.Default.Psychology,
                        contentDescription = "Configuração do Chatbot Gemini",
                        tint = ElectricCyan,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Column {
                        Text("Chat Multi-Turno com Especialistas", style = MaterialTheme.typography.titleMedium)
                        Text(
                            "Histórico persistido localmente com contexto contínuo por turno",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextSecondary
                        )
                    }
                }
                if (messages.isNotEmpty()) {
                    TextButton(
                        onClick = onClearHistory,
                        enabled = !isBusy,
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("clear_chat_history_button")
                    ) {
                        Icon(Icons.Default.DeleteSweep, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Limpar")
                    }
                }
            }

            Spacer(Modifier.height(10.dp))
            Text("Papel do Agente (System Instruction)", style = MaterialTheme.typography.labelLarge, color = ElectricCyan)
            Spacer(Modifier.height(4.dp))
            FilterChipBar(
                options = AiAgentRole.entries.map { it to it.title },
                selected = selectedRole,
                onSelect = onSelectRole
            )
            Text(selectedRole.subtitle, style = MaterialTheme.typography.bodySmall, color = TextSecondary)

            Spacer(Modifier.height(10.dp))
            Text("Modelo Gemini por Tarefa", style = MaterialTheme.typography.labelLarge, color = ElectricCyan)
            Spacer(Modifier.height(4.dp))
            FilterChipBar(
                options = GeminiTaskModel.entries.map { it to it.label },
                selected = selectedModel,
                onSelect = onSelectModel
            )
            Text(
                text = selectedModel.subtitle,
                style = MaterialTheme.typography.labelSmall,
                color = if (selectedModel.requiresBilling) AmberGold else TextSecondary
            )

            Spacer(Modifier.height(10.dp))
            Text("Ativo em Foco (opcional)", style = MaterialTheme.typography.labelLarge, color = ElectricCyan)
            Spacer(Modifier.height(4.dp))
            val tickerOptions: List<Pair<String?, String>> =
                listOf(null to "Geral") + availableAssets.take(8).map { it.ticker to it.ticker }
            FilterChipBar(
                options = tickerOptions,
                selected = focusedTicker,
                onSelect = onSelectFocusedTicker
            )

            Spacer(Modifier.height(10.dp))
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = DeepNavySurface,
                modifier = Modifier
                    .fillMaxWidth()
                    .border(
                        1.dp,
                        if (useGoogleSearch) EmeraldPrimary.copy(alpha = 0.45f) else SubtleBorder,
                        RoundedCornerShape(12.dp)
                    )
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                        Icon(
                            Icons.Default.Public,
                            contentDescription = "Pesquisa Google em tempo real",
                            tint = if (useGoogleSearch) EmeraldPrimary else TextSecondary,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        Column {
                            Text(
                                "Pesquisa Google (Search Grounding)",
                                style = MaterialTheme.typography.labelLarge,
                                color = if (useGoogleSearch) EmeraldPrimary else MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                "Busca fatos relevantes, notícias e proventos recentes com citações da web",
                                style = MaterialTheme.typography.labelSmall,
                                color = TextSecondary
                            )
                        }
                    }
                    Switch(
                        checked = useGoogleSearch,
                        onCheckedChange = onToggleGoogleSearch,
                        modifier = Modifier.testTag("ai_google_search_switch")
                    )
                }
            }
        }

        FintechCard(borderColor = SubtleBorder) {
            Text("Conversa Contínua", style = MaterialTheme.typography.titleSmall, color = EmeraldPrimary)
            Spacer(Modifier.height(8.dp))

            if (messages.isEmpty() && chatUiState is AiChatUiState.Idle) {
                EmptyChatThreadBox(
                    selectedRole = selectedRole,
                    focusedTicker = focusedTicker,
                    onPickSuggestion = { suggestion ->
                        onDraftChange(suggestion)
                    }
                )
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 180.dp, max = 380.dp)
                        .testTag("chat_thread_list"),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(messages, key = { it.id }) { msg ->
                        ChatBubbleItem(message = msg)
                    }

                    when (chatUiState) {
                        is AiChatUiState.Loading -> {
                            item(key = "streaming_loading") {
                                StreamingChatPlaceholder(
                                    role = chatUiState.role,
                                    modelLabel = chatUiState.modelLabel,
                                    partialText = null
                                )
                            }
                        }
                        is AiChatUiState.Streaming -> {
                            item(key = "streaming_active") {
                                StreamingChatPlaceholder(
                                    role = chatUiState.role,
                                    modelLabel = chatUiState.modelLabel,
                                    partialText = chatUiState.partialText
                                )
                            }
                        }
                        else -> Unit
                    }
                }
            }

            if (chatUiState is AiChatUiState.Error) {
                Spacer(Modifier.height(10.dp))
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = CoralRed.copy(alpha = 0.14f),
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, CoralRed.copy(alpha = 0.45f), RoundedCornerShape(10.dp))
                ) {
                    Column(modifier = Modifier.padding(10.dp)) {
                        Text("Erro no Chat Gemini", style = MaterialTheme.typography.labelLarge, color = CoralRed)
                        Spacer(Modifier.height(2.dp))
                        Text(chatUiState.message, style = MaterialTheme.typography.bodySmall)
                        Spacer(Modifier.height(6.dp))
                        OutlinedButton(onClick = onDismissError) {
                            Text("Dispensar")
                        }
                    }
                }
            }

            if (audioUiState !is AiAudioUiState.Idle) {
                Spacer(Modifier.height(10.dp))
                val bannerColor = when (audioUiState) {
                    is AiAudioUiState.Recording -> CoralRed
                    is AiAudioUiState.Processing -> ElectricCyan
                    is AiAudioUiState.Speaking -> EmeraldPrimary
                    is AiAudioUiState.Error -> CoralRed
                    AiAudioUiState.Idle -> SubtleBorder
                }
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = bannerColor.copy(alpha = 0.14f),
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, bannerColor.copy(alpha = 0.45f), RoundedCornerShape(10.dp))
                        .testTag("ai_audio_status_banner")
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                            when (audioUiState) {
                                is AiAudioUiState.Processing -> CircularProgressIndicator(
                                    modifier = Modifier.size(16.dp),
                                    strokeWidth = 2.dp,
                                    color = ElectricCyan
                                )
                                else -> Icon(
                                    Icons.Default.GraphicEq,
                                    contentDescription = null,
                                    tint = bannerColor,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                            Spacer(Modifier.width(8.dp))
                            Text(
                                text = when (audioUiState) {
                                    is AiAudioUiState.Recording -> if (audioUiState.mode == VoiceCaptureMode.VOICE_CONVERSATION) {
                                        "Gravando conversa por voz... toque em Parar para enviar ao Gemini."
                                    } else {
                                        "Gravando áudio para transcrição... toque novamente no microfone para transcrever."
                                    }
                                    is AiAudioUiState.Processing -> audioUiState.statusLabel
                                    is AiAudioUiState.Speaking -> "Falando resposta do ${selectedRole.title}..."
                                    is AiAudioUiState.Error -> audioUiState.message
                                    AiAudioUiState.Idle -> ""
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                        if (audioUiState is AiAudioUiState.Speaking) {
                            TextButton(
                                onClick = onStopSpeaking,
                                modifier = Modifier
                                    .minimumInteractiveComponentSize()
                                    .testTag("ai_stop_speaking_button")
                            ) {
                                Icon(Icons.AutoMirrored.Filled.VolumeOff, contentDescription = null, tint = EmeraldPrimary, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("Parar Voz", color = EmeraldPrimary)
                            }
                        } else if (audioUiState is AiAudioUiState.Error) {
                            TextButton(
                                onClick = onDismissAudioError,
                                modifier = Modifier.minimumInteractiveComponentSize()
                            ) {
                                Text("Fechar", color = CoralRed)
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(10.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = onToggleTranscribeAudio,
                    enabled = !isBusy && !isRecordingVoiceChat,
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 48.dp)
                        .testTag("ai_transcribe_audio_button")
                ) {
                    Icon(
                        imageVector = if (isRecordingTranscribe) Icons.Default.Stop else Icons.Default.Mic,
                        contentDescription = "Transcrever áudio pelo microfone",
                        tint = if (isRecordingTranscribe) CoralRed else ElectricCyan,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        if (isRecordingTranscribe) "Parar e Transcrever" else "Transcrever Áudio",
                        style = MaterialTheme.typography.labelMedium,
                        color = if (isRecordingTranscribe) CoralRed else ElectricCyan
                    )
                }

                Button(
                    onClick = onToggleVoiceConversation,
                    enabled = !isBusy && !isRecordingTranscribe,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isRecordingVoiceChat) CoralRed else VioletAccent,
                        contentColor = EmeraldOnPrimary
                    ),
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 48.dp)
                        .testTag("ai_voice_conversation_button")
                ) {
                    Icon(
                        imageVector = if (isRecordingVoiceChat) Icons.Default.Stop else Icons.Default.RecordVoiceOver,
                        contentDescription = "Conversa por voz com o agente Gemini",
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        if (isRecordingVoiceChat) "Enviar Voz" else "Conversa por Voz",
                        style = MaterialTheme.typography.labelMedium
                    )
                }
            }

            Spacer(Modifier.height(10.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Bottom,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = draft,
                    onValueChange = onDraftChange,
                    label = { Text("Pergunte ao ${selectedRole.title}...") },
                    placeholder = { Text("Digite ou grave áudio sobre ativos, P/VP, DY...") },
                    maxLines = 4,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("chat_message_input")
                )
                FilledIconButton(
                    onClick = { onSendMessage(draft) },
                    enabled = draft.isNotBlank() && !isBusy,
                    colors = IconButtonDefaults.filledIconButtonColors(
                        containerColor = EmeraldPrimary,
                        contentColor = EmeraldOnPrimary
                    ),
                    modifier = Modifier
                        .size(52.dp)
                        .testTag("send_chat_message_button")
                ) {
                    Icon(
                        Icons.AutoMirrored.Filled.Send,
                        contentDescription = "Enviar mensagem ao chat Gemini"
                    )
                }
            }
        }
    }
}

@Composable
private fun EmptyChatThreadBox(
    selectedRole: AiAgentRole,
    focusedTicker: String?,
    onPickSuggestion: (String) -> Unit
) {
    val target = focusedTicker ?: "HGLG11"
    val suggestions = listOf(
        "Avalie $target considerando P/VP, DY e riscos principais.",
        "Como reduzir concentração HHI mantendo renda mensal entre G1 e G4?",
        "Quais documentos da CVM/B3 devo auditar antes de aportar em $target?"
    )
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(DeepNavySurface)
            .padding(12.dp)
            .testTag("chat_empty_state"),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.SmartToy, contentDescription = null, tint = EmeraldPrimary, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text(
                "Inicie uma conversa com o ${selectedRole.title}",
                style = MaterialTheme.typography.titleSmall,
                color = EmeraldPrimary
            )
        }
        Text(
            "Você pode alternar o papel entre Analista, Gestor e Auditor no meio da conversa mantendo o histórico completo.",
            style = MaterialTheme.typography.bodySmall,
            color = TextSecondary
        )
        suggestions.forEach { prompt ->
            OutlinedButton(
                onClick = { onPickSuggestion(prompt) },
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Default.AutoAwesome, contentDescription = null, modifier = Modifier.size(15.dp))
                Spacer(Modifier.width(6.dp))
                Text(prompt, style = MaterialTheme.typography.labelMedium)
            }
        }
    }
}

@Composable
private fun ChatBubbleItem(message: ChatMessageEntity) {
    val isUser = message.senderRole.equals("USER", ignoreCase = true)
    val fmt = SimpleDateFormat("HH:mm", Locale.forLanguageTag("pt-BR"))
    val timeLabel = fmt.format(Date(message.createdAt))
    val roleTitle = AiAgentRole.entries.firstOrNull { it.name == message.agentRole }?.title ?: message.agentRole

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(0.90f)
                .clip(
                    RoundedCornerShape(
                        topStart = 14.dp,
                        topEnd = 14.dp,
                        bottomStart = if (isUser) 14.dp else 4.dp,
                        bottomEnd = if (isUser) 4.dp else 14.dp
                    )
                )
                .background(if (isUser) EmeraldContainer else CardElevated)
                .border(
                    width = 1.dp,
                    color = if (isUser) EmeraldPrimary.copy(alpha = 0.45f) else ElectricCyan.copy(alpha = 0.25f),
                    shape = RoundedCornerShape(
                        topStart = 14.dp,
                        topEnd = 14.dp,
                        bottomStart = if (isUser) 14.dp else 4.dp,
                        bottomEnd = if (isUser) 4.dp else 14.dp
                    )
                )
                .padding(10.dp)
        ) {
            Column {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (isUser) "Você -> $roleTitle" else "$roleTitle • ${message.modelName}",
                        style = MaterialTheme.typography.labelSmall,
                        color = if (isUser) EmeraldPrimary else VioletAccent
                    )
                    Text(timeLabel, style = MaterialTheme.typography.labelSmall, color = TextSecondary)
                }
                Spacer(Modifier.height(4.dp))
                Text(
                    text = message.content,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        }
    }
}

@Composable
private fun StreamingChatPlaceholder(
    role: AiAgentRole,
    modelLabel: String,
    partialText: String?
) {
    Box(
        modifier = Modifier
            .fillMaxWidth(0.90f)
            .clip(RoundedCornerShape(topStart = 14.dp, topEnd = 14.dp, bottomStart = 4.dp, bottomEnd = 14.dp))
            .background(CardElevated)
            .border(
                1.dp,
                ElectricCyan.copy(alpha = 0.4f),
                RoundedCornerShape(topStart = 14.dp, topEnd = 14.dp, bottomStart = 4.dp, bottomEnd = 14.dp)
            )
            .padding(10.dp)
    ) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp, color = ElectricCyan)
                Spacer(Modifier.width(8.dp))
                Text(
                    "${role.title} ($modelLabel) respondendo...",
                    style = MaterialTheme.typography.labelSmall,
                    color = ElectricCyan
                )
            }
            if (!partialText.isNullOrBlank()) {
                Spacer(Modifier.height(6.dp))
                Text(partialText, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}
