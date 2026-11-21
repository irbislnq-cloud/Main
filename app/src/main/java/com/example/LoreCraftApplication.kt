package com.example

import android.app.Application
import com.example.diagnostics.AppLogBuffer
import com.example.diagnostics.CrashGuard

class LoreCraftApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        CrashGuard.install(this)
        AppLogBuffer.i("Application", "LoreCraftApplication initialized")
    }
}
