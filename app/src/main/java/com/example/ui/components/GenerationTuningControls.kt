package com.example.ui.components

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.model.GenerationParams
import com.example.data.model.GenerationPresets
import com.example.data.model.SafetyLevel
import java.util.Locale
import kotlin.math.roundToInt

@Composable
fun GenerationTuningControls(
    params: GenerationParams,
    onParamsChange: (GenerationParams) -> Unit,
    modifier: Modifier = Modifier,
    showPenaltiesAndStops: Boolean = true,
    safetyLevel: SafetyLevel? = null,
    onSafetyLevelChange: ((SafetyLevel) -> Unit)? = null,
    creativeRpFraming: Boolean? = null,
    onCreativeRpFramingChange: ((Boolean) -> Unit)? = null
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        if (safetyLevel != null && onSafetyLevelChange != null) {
            Surface(
                shape = MaterialTheme.shapes.medium,
                color = MaterialTheme.colorScheme.surfaceContainerLow,
                tonalElevation = 1.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "Свобода творчества и фильтры (Creative RP)",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = safetyLevel.subtitleRu,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        SafetyLevel.entries.forEach { level ->
                            FilterChip(
                                selected = safetyLevel == level,
                                onClick = { onSafetyLevelChange(level) },
                                label = { Text(level.labelRu) },
                                modifier = Modifier.testTag("safety_level_${level.name}")
                            )
                        }
                    }
                    if (creativeRpFraming != null && onCreativeRpFramingChange != null) {
                        HorizontalDivider()
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Художественное обрамление Creative RP",
                                    style = MaterialTheme.typography.bodyMedium
                                )
                                Text(
                                    text = "Снижает ложные отказы и морализаторство в драме и нуаре",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Switch(
                                checked = creativeRpFraming,
                                onCheckedChange = onCreativeRpFramingChange,
                                modifier = Modifier.testTag("creative_rp_framing_switch")
                            )
                        }
                    }
                }
            }
        }
        Text(
            text = "Быстрые пресеты генерации",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurface
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            GenerationPresets.all.forEach { preset ->
                val isClose = kotlin.math.abs(params.temperature - preset.params.temperature) < 0.05 &&
                    kotlin.math.abs(params.topP - preset.params.topP) < 0.03
                FilterChip(
                    selected = isClose,
                    onClick = {
                        onParamsChange(
                            preset.params.copy(stopSequences = params.stopSequences)
                        )
                    },
                    label = { Text(preset.nameRu) },
                    modifier = Modifier.testTag("preset_chip_${preset.id}")
                )
            }
        }

        ParameterSliderRow(
            title = "Температура (Temperature)",
            subtitle = "Креативность и непредсказуемость ответов",
            valueDisplay = String.format(Locale.US, "%.2f", params.temperature),
            value = params.temperature.toFloat(),
            valueRange = 0f..2f,
            steps = 39,
            testTag = "slider_temperature",
            onValueChange = { v ->
                val rounded = (v * 20).roundToInt() / 20.0
                onParamsChange(params.copy(temperature = rounded))
            }
        )

        ParameterSliderRow(
            title = "Top-P (Nucleus Sampling)",
            subtitle = "Порог вероятности словаря для выбора токенов",
            valueDisplay = String.format(Locale.US, "%.2f", params.topP),
            value = params.topP.toFloat(),
            valueRange = 0.05f..1.0f,
            steps = 18,
            testTag = "slider_top_p",
            onValueChange = { v ->
                val rounded = (v * 20).roundToInt() / 20.0
                onParamsChange(params.copy(topP = rounded.coerceIn(0.05, 1.0)))
            }
        )

        ParameterSliderRow(
            title = "Top-K",
            subtitle = "Максимум кандидатов токенов на шаг (Gemini / Claude)",
            valueDisplay = "${params.topK}",
            value = params.topK.toFloat(),
            valueRange = 1f..100f,
            steps = 0,
            testTag = "slider_top_k",
            onValueChange = { v ->
                onParamsChange(params.copy(topK = v.roundToInt().coerceIn(1, 200)))
            }
        )

        ParameterSliderRow(
            title = "Макс. длина ответа (Tokens)",
            subtitle = "Ограничение объёма одного RP-хода нейросети",
            valueDisplay = "${params.maxTokens}",
            value = params.maxTokens.toFloat(),
            valueRange = 128f..8192f,
            steps = 0,
            testTag = "slider_max_tokens",
            onValueChange = { v ->
                val stepped = ((v / 64f).roundToInt() * 64).coerceIn(128, 8192)
                onParamsChange(params.copy(maxTokens = stepped))
            }
        )

        ParameterSliderRow(
            title = "Окно контекста (сообщений)",
            subtitle = "Сколько последних реплик отправляется в память LLM",
            valueDisplay = "${params.contextWindowMessages} сообщ.",
            value = params.contextWindowMessages.toFloat(),
            valueRange = 4f..100f,
            steps = 0,
            testTag = "slider_context_window",
            onValueChange = { v ->
                onParamsChange(params.copy(contextWindowMessages = v.roundToInt().coerceIn(4, 100)))
            }
        )

        if (showPenaltiesAndStops) {
            ParameterSliderRow(
                title = "Presence Penalty",
                subtitle = "Стимулирует новые темы и повороты сюжета",
                valueDisplay = String.format(Locale.US, "%.2f", params.presencePenalty),
                value = params.presencePenalty.toFloat(),
                valueRange = -2f..2f,
                steps = 39,
                testTag = "slider_presence_penalty",
                onValueChange = { v ->
                    val rounded = (v * 20).roundToInt() / 20.0
                    onParamsChange(params.copy(presencePenalty = rounded))
                }
            )

            ParameterSliderRow(
                title = "Frequency Penalty",
                subtitle = "Снижает повторы одних и тех же фраз и клише",
                valueDisplay = String.format(Locale.US, "%.2f", params.frequencyPenalty),
                value = params.frequencyPenalty.toFloat(),
                valueRange = -2f..2f,
                steps = 39,
                testTag = "slider_frequency_penalty",
                onValueChange = { v ->
                    val rounded = (v * 20).roundToInt() / 20.0
                    onParamsChange(params.copy(frequencyPenalty = rounded))
                }
            )

            OutlinedTextField(
                value = params.stopSequences.joinToString(", "),
                onValueChange = { raw ->
                    val list = raw.split(",").map { it.trim() }.filter { it.isNotEmpty() }.take(10)
                    onParamsChange(params.copy(stopSequences = list))
                },
                label = { Text("Стоп-последовательности (через запятую)") },
                placeholder = { Text("Например: User:, [OOC], ---") },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("stop_sequences_input")
            )
        }
    }
}

@Composable
private fun ParameterSliderRow(
    title: String,
    subtitle: String,
    valueDisplay: String,
    value: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    steps: Int,
    testTag: String,
    onValueChange: (Float) -> Unit
) {
    Surface(
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        tonalElevation = 1.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Surface(
                    shape = MaterialTheme.shapes.small,
                    color = MaterialTheme.colorScheme.primaryContainer
                ) {
                    Text(
                        text = valueDisplay,
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                    )
                }
            }
            Spacer(modifier = Modifier.height(4.dp))
            Slider(
                value = value.coerceIn(valueRange.start, valueRange.endInclusive),
                onValueChange = onValueChange,
                valueRange = valueRange,
                steps = steps,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag(testTag)
            )
        }
    }
}
