package com.example

import android.content.Context
import android.os.Build
import android.util.Log

object CrashReporter {
    private const val PREFS_NAME = "invest_ai_crash_diag"
    private const val KEY_LAST_CRASH = "last_crash_trace"
    private const val MAX_TRACE_LENGTH = 8000

    @Volatile
    private var installed = false

    fun install(context: Context) {
        if (installed) return
        synchronized(this) {
            if (installed) return
            val safeContext = context.applicationContext ?: context
            val previousHandler = Thread.getDefaultUncaughtExceptionHandler()
            Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
                recordCrash(safeContext, throwable, thread.name)
                previousHandler?.uncaughtException(thread, throwable)
            }
            installed = true
        }
    }

    fun recordCrash(
        context: Context,
        throwable: Throwable,
        threadName: String = Thread.currentThread().name
    ): String {
        val safeContext = context.applicationContext ?: context
        val report = formatCrashReport(throwable, threadName)
        runCatching {
            safeContext
                .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit()
                .putString(KEY_LAST_CRASH, report)
                .commit()
        }
        return report
    }

    fun formatCrashReport(
        throwable: Throwable,
        threadName: String = Thread.currentThread().name
    ): String {
        val stackTrace = Log.getStackTraceString(throwable)
        return buildString {
            appendLine("INVEST AI v${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})")
            appendLine("Dispositivo: ${Build.MANUFACTURER} ${Build.MODEL} • Android ${Build.VERSION.RELEASE} (SDK ${Build.VERSION.SDK_INT})")
            appendLine("Thread: $threadName")
            appendLine("Erro: ${throwable::class.java.name}: ${throwable.message ?: "sem mensagem"}")
            appendLine()
            append(stackTrace)
        }.take(MAX_TRACE_LENGTH)
    }

    fun consumeLastCrash(context: Context): String? {
        return runCatching {
            val safeContext = context.applicationContext ?: context
            val prefs = safeContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val trace = prefs.getString(KEY_LAST_CRASH, null)?.takeIf { it.isNotBlank() }
            if (trace != null) {
                prefs.edit().remove(KEY_LAST_CRASH).commit()
            }
            trace
        }.getOrNull()
    }
}
