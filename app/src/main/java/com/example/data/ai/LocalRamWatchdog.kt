package com.example.data.ai

import android.app.ActivityManager
import android.content.Context
import com.example.data.model.LlmProvider
import com.example.data.model.LocalRamPreset
import com.example.data.model.PipelineExecutionMode
import kotlinx.coroutines.sync.Mutex

data class DeviceRamSnapshot(
    val availableMb: Long,
    val totalMb: Long,
    val isLowMemory: Boolean,
    val lowMemoryThresholdMb: Long,
    val safeLocalContextBudget: Int,
    val safeLocalMaxTokens: Int,
    val warningText: String?
)

object LocalRamWatchdog {

    /**
     * Guards local GGUF / 127.0.0.1 inference so only one LLM pass hits device memory at a time.
     */
    val localInferenceMutex: Mutex = Mutex()

    fun inspectRam(context: Context?, preset: LocalRamPreset): DeviceRamSnapshot {
        if (context == null) {
            return DeviceRamSnapshot(
                availableMb = 4096L,
                totalMb = 8192L,
                isLowMemory = false,
                lowMemoryThresholdMb = 1024L,
                safeLocalContextBudget = preset.contextSize,
                safeLocalMaxTokens = 1024,
                warningText = null
            )
        }
        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
        val info = ActivityManager.MemoryInfo()
        return if (am != null) {
            am.getMemoryInfo(info)
            val availMb = (info.availMem / (1024L * 1024L)).coerceAtLeast(1L)
            val totalMb = (info.totalMem / (1024L * 1024L)).coerceAtLeast(1L)
            val threshMb = (info.threshold / (1024L * 1024L)).coerceAtLeast(256L)
            val lowMem = info.lowMemory || availMb < 1100L
            val criticalMem = info.lowMemory || availMb < 750L

            val ctxBudget = when {
                criticalMem -> 1536
                lowMem -> minOf(preset.contextSize, 2048)
                else -> preset.contextSize
            }
            val tokBudget = when {
                criticalMem -> 512
                lowMem -> 768
                else -> 1280
            }
            val warning = when {
                criticalMem ->
                    "Критически мало свободной ОЗУ (${availMb} МБ). Локальная модель сжата до 1536 токенов контекста во избежание вылета."
                lowMem ->
                    "Свободно ОЗУ: ${availMb} МБ. Включён эконом-режим локального контекста (${ctxBudget} токенов)."
                else -> null
            }
            DeviceRamSnapshot(
                availableMb = availMb,
                totalMb = totalMb,
                isLowMemory = lowMem,
                lowMemoryThresholdMb = threshMb,
                safeLocalContextBudget = ctxBudget,
                safeLocalMaxTokens = tokBudget,
                warningText = warning
            )
        } else {
            DeviceRamSnapshot(
                availableMb = 4096L,
                totalMb = 8192L,
                isLowMemory = false,
                lowMemoryThresholdMb = 1024L,
                safeLocalContextBudget = preset.contextSize,
                safeLocalMaxTokens = 1024,
                warningText = null
            )
        }
    }

    fun shouldCollapsePipelineForDevice(
        context: Context?,
        provider: LlmProvider,
        apiKey: String,
        mode: PipelineExecutionMode,
        preset: LocalRamPreset
    ): Pair<Boolean, String?> {
        if (mode == PipelineExecutionMode.MOBILE_COMPACT) {
            return true to "Мобильный компактный режим (1 LLM-проход + локальный Математик и Разметчик)"
        }
        val ram = inspectRam(context, preset)
        if (provider == LlmProvider.LOCAL_LLM) {
            val note = ram.warningText
                ?: "Локальная GGUF: включён безопасный 1-проходный режим с блокировкой ОЗУ (${ram.availableMb} МБ свободно)"
            return true to note
        }
        if (mode == PipelineExecutionMode.ADAPTIVE) {
            if (provider == LlmProvider.POLLINATIONS && apiKey.isBlank()) {
                return true to "Pollinations без ключа: 1 оптимизированный запрос во избежание лимита 402"
            }
            if (ram.isLowMemory) {
                return true to "Мало свободной ОЗУ (${ram.availableMb} МБ): конвейер свёрнут в 1 проход"
            }
        }
        return false to null
    }
}
