package com.example.data.firebase

import android.content.Context
import android.content.Intent
import com.example.BuildConfig
import com.example.diagnostics.AppLogBuffer
import com.google.firebase.FirebaseApp
import com.google.firebase.appcheck.FirebaseAppCheck
import com.google.firebase.appcheck.debug.DebugAppCheckProviderFactory
import com.google.firebase.appcheck.recaptcha.RecaptchaAppCheckProviderFactory

private const val TAG = "AppCheckInit"
private const val DEBUG_TOKEN_EXTRA = "FIREBASE_APPCHECK_DEBUG_TOKEN"
private const val DEBUG_STORE_PREFIX = "com.google.firebase.appcheck.debug.store."
private const val DEBUG_SECRET_KEY = "com.google.firebase.appcheck.debug.DEBUG_SECRET"
private const val RECAPTCHA_SITE_KEY_RES = "recaptcha_site_key"

data class FirebaseHealthStatus(
    val isReady: Boolean,
    val errorSummary: String? = null
)

fun configureAppCheck(context: Context, intent: Intent?): FirebaseHealthStatus {
    return try {
        val apps = FirebaseApp.getApps(context)
        val app = if (apps.isEmpty()) {
            FirebaseApp.initializeApp(context)
        } else {
            apps.first()
        }

        if (app == null) {
            val msg = "Firebase не настроен (нет app/google-services.json в этой сборке) — активен автономный режим."
            AppLogBuffer.w(TAG, msg)
            return FirebaseHealthStatus(isReady = false, errorSummary = msg)
        }

        AppLogBuffer.i(TAG, "FirebaseApp ready: ${app.name}")

        if (BuildConfig.DEBUG) {
            val debugToken = intent?.getStringExtra(DEBUG_TOKEN_EXTRA)
            if (!debugToken.isNullOrEmpty()) {
                val persistenceKey = app.persistenceKey
                val prefs = context.getSharedPreferences(
                    "$DEBUG_STORE_PREFIX$persistenceKey",
                    Context.MODE_PRIVATE
                )
                prefs.edit()
                    .putString(DEBUG_SECRET_KEY, debugToken)
                    .commit()
                AppLogBuffer.d(TAG, "App Check debug secret configured from intent.")
            } else {
                AppLogBuffer.d(TAG, "No debug token extra; using device-local debug secret.")
            }

            FirebaseAppCheck.getInstance(app).installAppCheckProviderFactory(
                DebugAppCheckProviderFactory.getInstance()
            )
            AppLogBuffer.d(TAG, "DebugAppCheckProviderFactory installed.")
        } else {
            val resId = context.resources.getIdentifier(
                RECAPTCHA_SITE_KEY_RES,
                "string",
                context.packageName
            )
            if (resId != 0) {
                val siteKey = context.getString(resId)
                if (siteKey.isNotBlank()) {
                    FirebaseAppCheck.getInstance(app).installAppCheckProviderFactory(
                        RecaptchaAppCheckProviderFactory.getInstance(siteKey)
                    )
                    AppLogBuffer.d(TAG, "RecaptchaAppCheckProviderFactory installed.")
                } else {
                    AppLogBuffer.w(TAG, "recaptcha_site_key resource string is blank.")
                }
            } else {
                AppLogBuffer.w(TAG, "recaptcha_site_key string resource not found.")
            }
        }
        FirebaseHealthStatus(isReady = true)
    } catch (t: Throwable) {
        AppLogBuffer.e(TAG, "Failed to initialize Firebase / App Check", t)
        FirebaseHealthStatus(
            isReady = false,
            errorSummary = t.localizedMessage ?: t::class.java.simpleName
        )
    }
}

fun readAppCheckDebugToken(context: Context): String? {
    if (!BuildConfig.DEBUG) return null
    return runCatching {
        val app = FirebaseApp.getApps(context).firstOrNull() ?: return@runCatching null
        val prefs = context.getSharedPreferences(
            "$DEBUG_STORE_PREFIX${app.persistenceKey}",
            Context.MODE_PRIVATE
        )
        prefs.getString(DEBUG_SECRET_KEY, null)?.takeIf { it.isNotBlank() }
    }.getOrNull()
}

