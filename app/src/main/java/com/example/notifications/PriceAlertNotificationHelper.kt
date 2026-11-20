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
import com.example.data.local.PriceTargetAlertEntity
import com.example.domain.model.PriceAlertCondition
import java.text.NumberFormat
import java.util.Locale

object PriceAlertNotificationHelper {

    const val CHANNEL_ID = "invest_ai_price_targets"
    private const val CHANNEL_NAME = "Metas de Preço (Push)"
    private const val CHANNEL_DESC = "Notificações quando um ativo monitorado atinge a meta de preço definida na análise."

    fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return
            val existing = manager.getNotificationChannel(CHANNEL_ID)
            if (existing == null) {
                val channel = NotificationChannel(
                    CHANNEL_ID,
                    CHANNEL_NAME,
                    NotificationManager.IMPORTANCE_HIGH
                ).apply {
                    description = CHANNEL_DESC
                    enableVibration(true)
                }
                manager.createNotificationChannel(channel)
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
    fun sendPriceTargetNotification(
        context: Context,
        alert: PriceTargetAlertEntity,
        currentPriceBrl: Double,
        sourceLabel: String
    ): Boolean {
        ensureChannel(context)
        if (!hasNotificationPermission(context)) return false

        val ptBr = Locale.forLanguageTag("pt-BR")
        val currency = NumberFormat.getCurrencyInstance(ptBr)
        val currentText = currency.format(currentPriceBrl)
        val targetText = currency.format(alert.targetPriceBrl)
        val cond = PriceAlertCondition.fromCode(alert.condition)

        val notificationId = ((alert.id.takeIf { it > 0 }?.toInt()) ?: alert.ticker.hashCode()) and 0x7FFFFFFF
        val launchIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val contentIntent = PendingIntent.getActivity(
            context,
            notificationId,
            launchIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val title = "Meta atingida: ${alert.ticker} ($currentText)"
        val summary = "${alert.ticker} atingiu sua meta ${cond.shortSymbol} $targetText"
        val noteLine = alert.notes.takeIf { it.isNotBlank() }?.let { "\nObservação: $it" }.orEmpty()
        val bigText = "$summary • Cotação atual: $currentText (Fonte: $sourceLabel)$noteLine"

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
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
}
