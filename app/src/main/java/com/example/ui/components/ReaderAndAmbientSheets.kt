package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoStories
import androidx.compose.material.icons.outlined.GraphicEq
import androidx.compose.material.icons.outlined.Memory
import androidx.compose.material.icons.outlined.TextFields
import androidx.compose.material.icons.outlined.Vibration
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.local.AppLocalSettings
import com.example.device.AmbientScenePreset
import com.example.ui.theme.ReaderFontStyle
import java.util.Locale
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ReaderSettingsSheet(
    settings: AppLocalSettings,
    onToggleBookMode: (Boolean) -> Unit,
    onSelectFontStyle: (ReaderFontStyle) -> Unit,
    onChangeFontSizeSp: (Int) -> Unit,
    onChangeLineHeight: (Float) -> Unit,
    onToggleHaptics: (Boolean) -> Unit,
    onToggleAutoFailover: (Boolean) -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val selectedFont = settings.readerFontStyle

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .padding(bottom = 24.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(Icons.Outlined.AutoStories, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Text("Книжный режим и чтение", style = MaterialTheme.typography.titleLarge)
            }

            // Book Mode Toggle
            Surface(
                shape = MaterialTheme.shapes.medium,
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Книжная вёрстка (без пузырей)", style = MaterialTheme.typography.titleSmall)
                        Text(
                            text = "Литературный поток на всю ширину с разделителями сцен",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = settings.readerBookMode,
                        onCheckedChange = onToggleBookMode,
                        modifier = Modifier.testTag("reader_book_mode_switch")
                    )
                }
            }

            // Font family selector
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Icon(Icons.Outlined.TextFields, contentDescription = null, modifier = Modifier.size(18.dp))
                Text("Шрифт текста сцены", style = MaterialTheme.typography.labelLarge)
            }
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                ReaderFontStyle.entries.forEach { style ->
                    FilterChip(
                        selected = style == selectedFont,
                        onClick = { onSelectFontStyle(style) },
                        label = { Text(style.labelRu) },
                        modifier = Modifier.testTag("reader_font_${style.name}")
                    )
                }
            }

            // Font Size Slider
            Column {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Размер шрифта", style = MaterialTheme.typography.labelLarge)
                    Text("${settings.readerFontSizeSp} sp", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                }
                Slider(
                    value = settings.readerFontSizeSp.toFloat(),
                    onValueChange = { onChangeFontSizeSp(it.roundToInt()) },
                    valueRange = 13f..22f,
                    steps = 8
                )
            }

            // Line height Slider
            Column {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Межстрочный интервал", style = MaterialTheme.typography.labelLarge)
                    Text(
                        String.format(Locale.US, "%.2fx", settings.readerLineHeight),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                Slider(
                    value = settings.readerLineHeight,
                    onValueChange = onChangeLineHeight,
                    valueRange = 1.25f..1.85f,
                    steps = 11
                )
            }

            // Live typography preview
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surfaceContainerLow,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "Дождь мягко барабанил по тёмному стеклу, пока она прислушивалась к каждому шагу за дверью...",
                    style = MaterialTheme.typography.bodyLarge.copy(
                        fontFamily = selectedFont.fontFamily(),
                        fontSize = settings.readerFontSizeSp.sp,
                        lineHeight = (settings.readerFontSizeSp * settings.readerLineHeight).sp
                    ),
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(12.dp)
                )
            }

            HorizontalDivider()

            // Haptics & Auto-failover
            Surface(
                shape = MaterialTheme.shapes.medium,
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        modifier = Modifier.weight(1f),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(Icons.Outlined.Vibration, contentDescription = null, tint = MaterialTheme.colorScheme.secondary)
                        Column {
                            Text("Тактильный отклик (Haptics)", style = MaterialTheme.typography.titleSmall)
                            Text(
                                text = "Вибрация на кубиках, тегах и сдвиге параметров",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    Switch(
                        checked = settings.hapticsEnabled,
                        onCheckedChange = onToggleHaptics,
                        modifier = Modifier.testTag("haptics_toggle_switch")
                    )
                }
            }

            Surface(
                shape = MaterialTheme.shapes.medium,
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Авто-фолбэк LLM при сбое/лимите", style = MaterialTheme.typography.titleSmall)
                        Text(
                            text = "При 429/402/оффлайне автоматически переключится на резерв",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = settings.autoFailoverEnabled,
                        onCheckedChange = onToggleAutoFailover,
                        modifier = Modifier.testTag("auto_failover_switch")
                    )
                }
            }

            Button(
                onClick = onDismiss,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Готово")
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun AmbientControlSheet(
    settings: AppLocalSettings,
    resolvedActivePreset: AmbientScenePreset,
    isProbingLocalGguf: Boolean,
    localGgufServerUrl: String,
    localGgufModels: List<String>,
    onToggleAmbient: (Boolean) -> Unit,
    onSelectPreset: (AmbientScenePreset) -> Unit,
    onChangeVolume: (Float) -> Unit,
    onQuickSwitchLocalGguf: () -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val configuredPreset = settings.ambientPreset

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .padding(bottom = 24.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(Icons.Outlined.GraphicEq, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Column {
                    Text("Атмосфера сцены и оффлайн-GGUF", style = MaterialTheme.typography.titleLarge)
                    Text(
                        text = if (settings.ambientEnabled) {
                            "Сейчас звучит: ${resolvedActivePreset.badgeIcon} ${resolvedActivePreset.labelRu}"
                        } else {
                            "Процедурный звуковой фон отключён"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Surface(
                shape = MaterialTheme.shapes.medium,
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Процедурный эмбиент", style = MaterialTheme.typography.titleSmall)
                        Text(
                            text = "Живой синтез дождя, таверны, космоса или боя без файлов",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = settings.ambientEnabled,
                        onCheckedChange = onToggleAmbient,
                        modifier = Modifier.testTag("ambient_enabled_switch")
                    )
                }
            }

            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                AmbientScenePreset.entries.forEach { preset ->
                    FilterChip(
                        selected = preset == configuredPreset,
                        onClick = { onSelectPreset(preset) },
                        label = { Text("${preset.badgeIcon} ${preset.labelRu}") },
                        modifier = Modifier.testTag("ambient_preset_${preset.name}")
                    )
                }
            }

            Text(
                text = configuredPreset.subtitleRu,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Column {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Громкость эмбиента", style = MaterialTheme.typography.labelLarge)
                    Text("${(settings.ambientVolume * 100).roundToInt()}%", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                }
                Slider(
                    value = settings.ambientVolume,
                    onValueChange = onChangeVolume,
                    valueRange = 0.05f..0.95f
                )
            }

            HorizontalDivider()

            // Quick 1-Tap Local GGUF Checker & Switcher
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.45f),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.tertiary.copy(alpha = 0.4f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(Icons.Outlined.Memory, contentDescription = null, tint = MaterialTheme.colorScheme.tertiary)
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Быстрый переход на локальную GGUF",
                                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold)
                            )
                            Text(
                                text = "Сервер: $localGgufServerUrl",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    if (localGgufModels.isNotEmpty()) {
                        Text(
                            text = "Доступны модели: ${localGgufModels.take(3).joinToString(", ")}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    OutlinedButton(
                        onClick = onQuickSwitchLocalGguf,
                        enabled = !isProbingLocalGguf,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("quick_local_gguf_button")
                    ) {
                        if (isProbingLocalGguf) {
                            CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Проверка локального сервера...")
                        } else {
                            Text("Проверить GGUF и переключить чат")
                        }
                    }
                }
            }

            TextButton(
                onClick = onDismiss,
                modifier = Modifier.align(Alignment.End)
            ) {
                Text("Закрыть")
            }
        }
    }
}
