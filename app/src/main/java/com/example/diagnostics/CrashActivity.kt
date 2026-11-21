package com.example.diagnostics

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Bundle
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import com.example.MainActivity
import java.io.File

/**
 * Isolated crash recovery Activity built purely with Android platform Views.
 * Zero dependency on Compose, custom fonts, Material themes, or Firebase so it always renders
 * reliably on Xiaomi HyperOS / Android 14-15 even if startup fails early.
 */
class CrashActivity : Activity() {

    companion object {
        const val EXTRA_SUMMARY = "extra_crash_summary"
        const val EXTRA_REPORT_TEXT = "extra_crash_report_text"
        const val EXTRA_SAFE_MODE = "extra_safe_mode"
    }

    private fun dp(value: Int): Int = TypedValue.applyDimension(
        TypedValue.COMPLEX_UNIT_DIP,
        value.toFloat(),
        resources.displayMetrics
    ).toInt()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val stored = CrashLogStore.readLastCrash(this)
        val summary = intent?.getStringExtra(EXTRA_SUMMARY)?.takeIf { it.isNotBlank() }
            ?: stored?.first?.takeIf { it.isNotBlank() }
            ?: "Необработанный сбой запуска приложения"
        val reportText = intent?.getStringExtra(EXTRA_REPORT_TEXT)?.takeIf { it.isNotBlank() }
            ?: stored?.second?.takeIf { it.isNotBlank() }
            ?: CrashLogStore.buildDeviceDiagnosticsText(this)

        val bgObsidian = Color.parseColor("#0E0D14")
        val surfaceCard = Color.parseColor("#191724")
        val errorBg = Color.parseColor("#3B1722")
        val errorBorder = Color.parseColor("#EA5A6E")
        val primaryAccent = Color.parseColor("#A78BFA")
        val primaryText = Color.parseColor("#12101B")
        val textPrimary = Color.parseColor("#F4EFE6")
        val textMuted = Color.parseColor("#B8B2C8")

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(bgObsidian)
            setPadding(dp(16), dp(24), dp(16), dp(20))
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT_WATCH) {
            root.setOnApplyWindowInsetsListener { view, insets ->
                @Suppress("DEPRECATION")
                val top = insets.systemWindowInsetTop
                @Suppress("DEPRECATION")
                val bottom = insets.systemWindowInsetBottom
                view.setPadding(dp(16), top + dp(16), dp(16), bottom + dp(16))
                insets
            }
        }

        val header = TextView(this).apply {
            text = "LoreCraft — Диагностика сбоя"
            setTextColor(textPrimary)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 21f)
            setTypeface(Typeface.DEFAULT_BOLD)
        }
        root.addView(header)

        val subtitle = TextView(this).apply {
            text = "Приложение перехватило критическую ошибку до закрытия системы. Вы можете скопировать отчёт ниже или выполнить безопасный перезапуск."
            setTextColor(textMuted)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
            setPadding(0, dp(6), 0, dp(12))
        }
        root.addView(subtitle)

        val summaryCard = TextView(this).apply {
            text = summary
            setTextColor(Color.parseColor("#FFD6DC"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            setTypeface(Typeface.DEFAULT_BOLD)
            setPadding(dp(12), dp(10), dp(12), dp(10))
            background = GradientDrawable().apply {
                setColor(errorBg)
                cornerRadius = dp(12).toFloat()
                setStroke(dp(1), errorBorder)
            }
        }
        root.addView(summaryCard, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { bottomMargin = dp(12) })

        // Action row 1: Copy + Share
        val copyRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        val copyBtn = makeStyledButton(
            label = "Скопировать отчёт",
            bgColor = primaryAccent,
            fgColor = primaryText
        ) {
            val cm = getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
            cm?.setPrimaryClip(ClipData.newPlainText("LoreCraft Crash Report", reportText))
            Toast.makeText(this, "Отчёт скопирован в буфер обмена", Toast.LENGTH_SHORT).show()
        }
        copyRow.addView(copyBtn, LinearLayout.LayoutParams(0, dp(48), 1f).apply { rightMargin = dp(8) })

        val shareBtn = makeStyledButton(
            label = "Поделиться",
            bgColor = surfaceCard,
            fgColor = textPrimary
        ) {
            val sendIntent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_SUBJECT, "LoreCraft Crash Report")
                putExtra(Intent.EXTRA_TEXT, reportText)
            }
            startActivity(Intent.createChooser(sendIntent, "Отправить отчёт о сбое"))
        }
        copyRow.addView(shareBtn, LinearLayout.LayoutParams(0, dp(48), 1f))
        root.addView(copyRow, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { bottomMargin = dp(10) })

        // Scrollable stacktrace box
        val traceContainer = ScrollView(this).apply {
            background = GradientDrawable().apply {
                setColor(surfaceCard)
                cornerRadius = dp(12).toFloat()
                setStroke(dp(1), Color.parseColor("#2E2A40"))
            }
            setPadding(dp(10), dp(10), dp(10), dp(10))
        }
        val hScroll = HorizontalScrollView(this).apply {
            isFillViewport = true
        }
        val traceView = TextView(this).apply {
            text = reportText
            setTextColor(textPrimary)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 11.5f)
            typeface = Typeface.MONOSPACE
            setTextIsSelectable(true)
        }
        hScroll.addView(traceView)
        traceContainer.addView(hScroll)
        root.addView(traceContainer, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            0,
            1f
        ).apply { bottomMargin = dp(12) })

        // Action row 2: Safe Restart + Clear Cache & Restart
        val restartRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        val safeRestartBtn = makeStyledButton(
            label = "Безопасный перезапуск",
            bgColor = Color.parseColor("#2A4D3E"),
            fgColor = textPrimary
        ) {
            CrashLogStore.clearLastCrash(this)
            launchMain(safeMode = true)
        }
        restartRow.addView(safeRestartBtn, LinearLayout.LayoutParams(0, dp(50), 1f).apply { rightMargin = dp(8) })

        val resetCacheBtn = makeStyledButton(
            label = "Сброс кэша и старт",
            bgColor = errorBg,
            fgColor = textPrimary
        ) {
            runCatching {
                cacheDir.deleteRecursively()
                File(filesDir, "datastore").deleteRecursively()
                CrashLogStore.clearLastCrash(this)
            }
            Toast.makeText(this, "Локальный кэш очищен", Toast.LENGTH_SHORT).show()
            launchMain(safeMode = true)
        }
        restartRow.addView(resetCacheBtn, LinearLayout.LayoutParams(0, dp(50), 1f))
        root.addView(restartRow, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ))

        setContentView(root)
    }

    private fun makeStyledButton(
        label: String,
        bgColor: Int,
        fgColor: Int,
        onClick: () -> Unit
    ): Button = Button(this).apply {
        text = label
        isAllCaps = false
        setTextColor(fgColor)
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
        setTypeface(Typeface.DEFAULT_BOLD)
        minimumHeight = dp(48)
        setPadding(dp(10), dp(6), dp(10), dp(6))
        background = GradientDrawable().apply {
            setColor(bgColor)
            cornerRadius = dp(12).toFloat()
        }
        setOnClickListener { onClick() }
    }

    private fun launchMain(safeMode: Boolean) {
        val intent = Intent(this, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
            putExtra(EXTRA_SAFE_MODE, safeMode)
        }
        startActivity(intent)
        finish()
    }
}
