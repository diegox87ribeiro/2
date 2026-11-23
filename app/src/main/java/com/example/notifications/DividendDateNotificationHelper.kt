package com.example.notifications

import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.example.MainActivity
import com.example.R
import com.example.domain.model.DividendDateEventType
import com.example.domain.model.UpcomingDividendDateAlert
import java.text.NumberFormat
import java.util.Locale

object DividendDateNotificationHelper {

    const val CHANNEL_ID = "invest_ai_dividend_dates"
    private const val CHANNEL_NAME = "Alertas de Data-Com e Data-Ex"
    private const val CHANNEL_DESC =
        "Notificações locais quando a Data-Com ou Data-Ex de um ativo da carteira está próxima."

    fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return
            if (manager.getNotificationChannel(CHANNEL_ID) == null) {
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
        return PriceAlertNotificationHelper.hasNotificationPermission(context)
    }

    @SuppressLint("MissingPermission")
    fun sendDividendDateNotification(
        context: Context,
        alert: UpcomingDividendDateAlert,
        isTestPreview: Boolean = false
    ): Boolean {
        ensureChannel(context)
        if (!hasNotificationPermission(context)) return false

        val ptBr = Locale.forLanguageTag("pt-BR")
        val currency = NumberFormat.getCurrencyInstance(ptBr)
        val whenLabel = when (alert.daysUntil) {
            0 -> "HOJE"
            1 -> "AMANHÃ"
            else -> "em ${alert.daysUntil} dias"
        }
        val testPrefix = if (isTestPreview) "[Teste] " else ""
        val title = "$testPrefix${alert.eventType.label} $whenLabel: ${alert.ticker}"

        val shortSummary = when (alert.eventType) {
            DividendDateEventType.DATA_COM ->
                "${alert.ticker} tem Data-Com em ${alert.eventDateFormatted} • Garanta posição até o fechamento."
            DividendDateEventType.DATA_EX ->
                "${alert.ticker} entra em Data-Ex em ${alert.eventDateFormatted} • Negocia ex-provento."
        }

        val projSuffix = if (alert.projectedDividendBrl > 0.0) {
            "\nProvento projetado (${"%.0f".format(alert.quantity)} unid.): ${currency.format(alert.projectedDividendBrl)}"
        } else {
            "\nPosição em carteira: ${"%.0f".format(alert.quantity)} unid."
        }
        val bigText = "$shortSummary$projSuffix"

        val notificationId = ("DIV_DATE:${alert.dedupeKey}${if (isTestPreview) ":TEST" else ""}")
            .hashCode() and 0x7FFFFFFF

        val launchIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val contentIntent = PendingIntent.getActivity(
            context,
            notificationId,
            launchIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_price_alert)
            .setContentTitle(title)
            .setContentText(shortSummary)
            .setStyle(NotificationCompat.BigTextStyle().bigText(bigText))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setAutoCancel(true)
            .setContentIntent(contentIntent)
            .build()

        return runCatching {
            NotificationManagerCompat.from(context).notify(notificationId, notification)
            true
        }.getOrDefault(false)
    }
}
