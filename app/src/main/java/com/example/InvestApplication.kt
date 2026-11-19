package com.example

import android.app.Application
import android.content.Context

class InvestApplication : Application() {
    override fun attachBaseContext(base: Context) {
        super.attachBaseContext(base)
        CrashReporter.install(this)
    }

    override fun onCreate() {
        super.onCreate()
        CrashReporter.install(this)
    }
}
