package com.example.diagnostics

import android.app.Application
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Process
import android.util.Log
import com.example.BuildConfig
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.system.exitProcess

data class DiagnosticLogEntry(
    val timestampMs: Long,
    val level: String,
    val tag: String,
    val message: String
) {
    fun formatted(): String {
        val fmt = SimpleDateFormat("HH:mm:ss.SSS", Locale.US)
        return "${fmt.format(Date(timestampMs))} [$level/$tag] $message"
    }
}

object AppLogBuffer {
    private const val MAX_ENTRIES = 200
    private val lock = Any()
    private val entries = ArrayDeque<DiagnosticLogEntry>(MAX_ENTRIES)

    fun d(tag: String, message: String) = record("D", tag, message, null)
    fun i(tag: String, message: String) = record("I", tag, message, null)
    fun w(tag: String, message: String, throwable: Throwable? = null) = record("W", tag, message, throwable)
    fun e(tag: String, message: String, throwable: Throwable? = null) = record("E", tag, message, throwable)

    fun record(level: String, tag: String, message: String, throwable: Throwable? = null) {
        val fullMsg = if (throwable != null) {
            "$message (${throwable::class.java.simpleName}: ${throwable.message})"
        } else {
            message
        }
        runCatching {
            when (level) {
                "E" -> Log.e(tag, fullMsg, throwable)
                "W" -> Log.w(tag, fullMsg, throwable)
                "I" -> Log.i(tag, fullMsg)
                else -> Log.d(tag, fullMsg)
            }
        }
        synchronized(lock) {
            if (entries.size >= MAX_ENTRIES) {
                entries.removeFirst()
            }
            entries.addLast(
                DiagnosticLogEntry(
                    timestampMs = System.currentTimeMillis(),
                    level = level,
                    tag = tag.take(40),
                    message = fullMsg.take(800)
                )
            )
        }
    }

    fun snapshot(): List<DiagnosticLogEntry> = synchronized(lock) { entries.toList() }

    fun snapshotFormatted(limit: Int = MAX_ENTRIES): String {
        val list = snapshot().takeLast(limit)
        if (list.isEmpty()) return "Логи отсутствуют."
        return list.joinToString("\n") { it.formatted() }
    }

    fun clear() {
        synchronized(lock) {
            entries.clear()
        }
    }
}

object CrashLogStore {
    private const val DIR_NAME = "lorecraft_diagnostics"
    private const val LAST_CRASH_FILE = "last_crash_report.txt"
    private const val LAST_CRASH_SUMMARY_FILE = "last_crash_summary.txt"

    private fun diagDir(context: Context): File =
        File(context.applicationContext.filesDir, DIR_NAME).apply { mkdirs() }

    fun saveLastCrash(context: Context, summary: String, fullReport: String) {
        runCatching {
            val dir = diagDir(context)
            File(dir, LAST_CRASH_SUMMARY_FILE).writeText(summary, Charsets.UTF_8)
            File(dir, LAST_CRASH_FILE).writeText(fullReport, Charsets.UTF_8)
        }
    }

    fun readLastCrash(context: Context): Pair<String, String>? {
        return runCatching {
            val dir = diagDir(context)
            val reportFile = File(dir, LAST_CRASH_FILE)
            if (!reportFile.exists()) return@runCatching null
            val report = reportFile.readText(Charsets.UTF_8)
            if (report.isBlank()) return@runCatching null
            val summaryFile = File(dir, LAST_CRASH_SUMMARY_FILE)
            val summary = if (summaryFile.exists()) {
                summaryFile.readText(Charsets.UTF_8)
            } else {
                report.lineSequence().firstOrNull().orEmpty()
            }
            summary to report
        }.getOrNull()
    }

    fun clearLastCrash(context: Context) {
        runCatching {
            val dir = diagDir(context)
            File(dir, LAST_CRASH_FILE).delete()
            File(dir, LAST_CRASH_SUMMARY_FILE).delete()
        }
    }

    fun buildDeviceDiagnosticsText(context: Context): String {
        val timeFmt = SimpleDateFormat("yyyy-MM-dd HH:mm:ss Z", Locale.US)
        return buildString {
            appendLine("=== LoreCraft Диагностика ===")
            appendLine("Время: ${timeFmt.format(Date())}")
            appendLine("Версия: ${BuildConfig.VERSION_NAME} (code=${BuildConfig.VERSION_CODE}, debug=${BuildConfig.DEBUG})")
            appendLine("Пакет: ${context.packageName}")
            appendLine("Устройство: ${Build.MANUFACTURER} ${Build.MODEL} (${Build.DEVICE})")
            appendLine("Android: ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})")
            appendLine("ABI: ${Build.SUPPORTED_ABIS.joinToString(", ")}")
            appendLine("==============================")
        }
    }
}

object CrashGuard {
    private const val TAG = "CrashGuard"
    private val installed = AtomicBoolean(false)
    private val handlingCrash = AtomicBoolean(false)

    fun install(application: Application) {
        if (!installed.compareAndSet(false, true)) return
        val appContext = application.applicationContext
        val previousHandler = Thread.getDefaultUncaughtExceptionHandler()

        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            if (!handlingCrash.compareAndSet(false, true)) {
                previousHandler?.uncaughtException(thread, throwable)
                return@setDefaultUncaughtExceptionHandler
            }

            val summary = "${throwable::class.java.simpleName}: ${throwable.message ?: "Неизвестная ошибка"}"
            AppLogBuffer.e(TAG, "Uncaught exception on thread '${thread.name}'", throwable)
            val fullReport = formatCrashReport(appContext, thread.name, summary, throwable)
            CrashLogStore.saveLastCrash(appContext, summary, fullReport)

            val launched = runCatching {
                val intent = Intent(appContext, CrashActivity::class.java).apply {
                    addFlags(
                        Intent.FLAG_ACTIVITY_NEW_TASK or
                            Intent.FLAG_ACTIVITY_CLEAR_TASK or
                            Intent.FLAG_ACTIVITY_NO_ANIMATION
                    )
                    putExtra(CrashActivity.EXTRA_SUMMARY, summary)
                    putExtra(CrashActivity.EXTRA_REPORT_TEXT, fullReport)
                }
                appContext.startActivity(intent)
                true
            }.getOrDefault(false)

            if (launched) {
                runCatching { Thread.sleep(150L) }
                Process.killProcess(Process.myPid())
                exitProcess(10)
            } else {
                previousHandler?.uncaughtException(thread, throwable)
            }
        }

        AppLogBuffer.i(TAG, "CrashGuard installed cleanly")
    }

    fun formatCrashReport(
        context: Context,
        threadName: String,
        summary: String,
        throwable: Throwable
    ): String {
        val sw = StringWriter()
        throwable.printStackTrace(PrintWriter(sw))
        return buildString {
            append(CrashLogStore.buildDeviceDiagnosticsText(context))
            appendLine("Поток: $threadName")
            appendLine("Ошибка: $summary")
            appendLine()
            appendLine("--- Стектрейс ---")
            appendLine(sw.toString().trim())
            appendLine()
            appendLine("--- Последние события журнала ---")
            appendLine(AppLogBuffer.snapshotFormatted(60))
        }
    }
}
