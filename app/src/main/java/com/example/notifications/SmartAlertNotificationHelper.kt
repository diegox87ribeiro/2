package com.example.notifications

import android.Manifest
import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.example.MainActivity
import com.example.R
import com.example.data.local.AssetSwingAlertEntity
import com.example.data.local.InvestmentGoalEntity
import com.example.domain.model.InvestmentGoalType
import com.example.domain.model.SmartAlertEvaluator
import com.example.domain.model.SwingDirection
import java.text.NumberFormat
import java.util.Locale

object SmartAlertNotificationHelper {

    const val CHANNEL_SWING_ID = "invest_ai_sharp_swings"
    private const val CHANNEL_SWING_NAME = "Variações Bruscas de Ativos"
    private const val CHANNEL_SWING_DESC =
        "Alertas locais quando um ativo específico apresenta alta ou queda brusca no pregão."

    const val CHANNEL_GOAL_ID = "invest_ai_investment_goals"
    private const val CHANNEL_GOAL_NAME = "Metas de Investimento Alcançadas"
    private const val CHANNEL_GOAL_DESC =
        "Alertas locais quando sua carteira ou posição atinge uma meta financeira ou de renda passiva."

    fun ensureChannels(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return
            if (manager.getNotificationChannel(CHANNEL_SWING_ID) == null) {
                val swingChannel = NotificationChannel(
                    CHANNEL_SWING_ID,
                    CHANNEL_SWING_NAME,
                    NotificationManager.IMPORTANCE_HIGH
                ).apply {
                    description = CHANNEL_SWING_DESC
                    enableVibration(true)
                }
                manager.createNotificationChannel(swingChannel)
            }
            if (manager.getNotificationChannel(CHANNEL_GOAL_ID) == null) {
                val goalChannel = NotificationChannel(
                    CHANNEL_GOAL_ID,
                    CHANNEL_GOAL_NAME,
                    NotificationManager.IMPORTANCE_HIGH
                ).apply {
                    description = CHANNEL_GOAL_DESC
                    enableVibration(true)
                }
                manager.createNotificationChannel(goalChannel)
            }
        }
    }

    fun hasNotificationPermission(context: Context): Boolean {
        val runtimeGranted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
        if (!runtimeGranted) return false
        return NotificationManagerCompat.from(context).areNotificationsEnabled()
    }

    @SuppressLint("MissingPermission")
    fun sendSharpSwingNotification(
        context: Context,
        alert: AssetSwingAlertEntity,
        changePercent1d: Double,
        currentPriceBrl: Double,
        sourceLabel: String
    ): Boolean {
        ensureChannels(context)
        if (!hasNotificationPermission(context)) return false

        val ptBr = Locale.forLanguageTag("pt-BR")
        val currency = NumberFormat.getCurrencyInstance(ptBr)
        val priceText = currency.format(currentPriceBrl)
        val signedPct = String.format(ptBr, "%+.2f%%", changePercent1d)
        val thresholdText = String.format(ptBr, "%.1f%%", alert.thresholdPercent)
        val directionLabel = if (changePercent1d >= 0.0) "Alta brusca" else "Queda brusca"
        val dirRule = SwingDirection.fromCode(alert.direction)

        val notificationId = (30_000 + ((alert.id.takeIf { it > 0 }?.toInt()) ?: alert.ticker.hashCode())) and 0x7FFFFFFF
        val contentIntent = buildLaunchIntent(context, notificationId)

        val title = "$directionLabel: ${alert.ticker} ($signedPct)"
        val summary = "${alert.ticker} variou $signedPct hoje • Cotação: $priceText"
        val noteLine = alert.notes.takeIf { it.isNotBlank() }?.let { "\nNota: $it" }.orEmpty()
        val bigText = "$summary\nRegra disparada: ${dirRule.shortSymbol}$thresholdText (Fonte: $sourceLabel)$noteLine"

        val notification = NotificationCompat.Builder(context, CHANNEL_SWING_ID)
            .setSmallIcon(R.drawable.ic_stat_price_alert)
            .setContentTitle(title)
            .setContentText(summary)
            .setStyle(NotificationCompat.BigTextStyle().bigText(bigText))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setAutoCancel(true)
            .setContentIntent(contentIntent)
            .build()

        return runCatching {
            NotificationManagerCompat.from(context).notify(notificationId, notification)
            true
        }.getOrDefault(false)
    }

    @SuppressLint("MissingPermission")
    fun sendInvestmentGoalNotification(
        context: Context,
        goal: InvestmentGoalEntity,
        currentValue: Double,
        targetValue: Double
    ): Boolean {
        ensureChannels(context)
        if (!hasNotificationPermission(context)) return false

        val type = InvestmentGoalType.fromCode(goal.goalType)
        val currentText = SmartAlertEvaluator.formatGoalValue(type, currentValue)
        val targetText = SmartAlertEvaluator.formatGoalValue(type, targetValue)
        val tickerSuffix = goal.ticker.trim().uppercase(Locale.ROOT).takeIf { it.isNotBlank() }?.let { " ($it)" }.orEmpty()

        val notificationId = (40_000 + ((goal.id.takeIf { it > 0 }?.toInt()) ?: goal.title.hashCode())) and 0x7FFFFFFF
        val contentIntent = buildLaunchIntent(context, notificationId)

        val title = "Meta alcançada: ${goal.title}$tickerSuffix"
        val summary = "Atual: $currentText • Alvo: $targetText"
        val noteLine = goal.notes.takeIf { it.isNotBlank() }?.let { "\nNota: $it" }.orEmpty()
        val bigText = "Parabéns! Sua meta '${goal.title}' (${type.label}) foi alcançada.\n$summary$noteLine"

        val notification = NotificationCompat.Builder(context, CHANNEL_GOAL_ID)
            .setSmallIcon(R.drawable.ic_stat_price_alert)
            .setContentTitle(title)
            .setContentText(summary)
            .setStyle(NotificationCompat.BigTextStyle().bigText(bigText))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .setAutoCancel(true)
            .setContentIntent(contentIntent)
            .build()

        return runCatching {
            NotificationManagerCompat.from(context).notify(notificationId, notification)
            true
        }.getOrDefault(false)
    }

    @SuppressLint("MissingPermission")
    fun sendTestSmartNotification(
        context: Context,
        sampleTicker: String,
        sampleEquityBrl: Double
    ): Boolean {
        ensureChannels(context)
        if (!hasNotificationPermission(context)) return false

        val ptBr = Locale.forLanguageTag("pt-BR")
        val equityText = NumberFormat.getCurrencyInstance(ptBr).format(sampleEquityBrl)
        val notificationId = 49_999
        val contentIntent = buildLaunchIntent(context, notificationId)

        val title = "Teste de Alerta • INVEST AI"
        val summary = "Notificações locais de variações bruscas ($sampleTicker) e metas ($equityText) ativas!"
        val bigText = "Tudo pronto! Você receberá alertas locais neste aparelho sempre que um ativo monitorado sofrer variação brusca no pregão ou quando suas metas de investimento forem alcançadas."

        val notification = NotificationCompat.Builder(context, CHANNEL_SWING_ID)
            .setSmallIcon(R.drawable.ic_stat_price_alert)
            .setContentTitle(title)
            .setContentText(summary)
            .setStyle(NotificationCompat.BigTextStyle().bigText(bigText))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(contentIntent)
            .build()

        return runCatching {
            NotificationManagerCompat.from(context).notify(notificationId, notification)
            true
        }.getOrDefault(false)
    }

    private fun buildLaunchIntent(context: Context, requestCode: Int): PendingIntent {
        val launchIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        return PendingIntent.getActivity(
            context,
            requestCode,
            launchIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }
}
