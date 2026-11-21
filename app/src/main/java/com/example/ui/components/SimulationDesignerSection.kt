package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.model.CustomMeterSpec
import com.example.data.model.SimulationRuleModule
import com.example.data.model.SimulationSpec
import java.util.UUID

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SimulationDesignerSection(
    simulation: SimulationSpec,
    onSimulationChange: (SimulationSpec) -> Unit,
    modifier: Modifier = Modifier
) {
    var showAddMeterDialog by remember { mutableStateOf(false) }

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        HorizontalDivider()
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Icon(Icons.Outlined.Tune, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
            Text("Движок правил и Шкалы симуляции", style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold))
        }
        Text(
            text = "Модули позволяют настроить любую игру: чистый психологический RP, детектив, выживание, экономику, киберпанк или RPG.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            SimulationRuleModule.entries.forEach { module ->
                val selected = module in simulation.ruleModules
                FilterChip(
                    selected = selected,
                    onClick = {
                        val next = if (selected) {
                            simulation.ruleModules - module
                        } else {
                            simulation.ruleModules + module
                        }
                        onSimulationChange(simulation.copy(ruleModules = next))
                    },
                    label = { Text("${module.badgeIcon} ${module.labelRu}") },
                    modifier = Modifier.testTag("sim_module_${module.name}")
                )
            }
        }

        OutlinedTextField(
            value = simulation.customMechanicsPrompt,
            onValueChange = { onSimulationChange(simulation.copy(customMechanicsPrompt = it)) },
            label = { Text("Промпт правил мира и механик (необязательно)") },
            placeholder = { Text("Например: расчёт рассудка, система улик, законы магии или экономические кризисы...") },
            minLines = 2,
            maxLines = 5,
            modifier = Modifier
                .fillMaxWidth()
                .testTag("sim_custom_mechanics_input")
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Пользовательские шкалы HUD (${simulation.customMeters.size})",
                style = MaterialTheme.typography.labelLarge
            )
            OutlinedButton(
                onClick = { showAddMeterDialog = true },
                modifier = Modifier.testTag("add_custom_meter_button")
            ) {
                Icon(Icons.Outlined.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("Шкала")
            }
        }

        if (simulation.customMeters.isNotEmpty()) {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                simulation.customMeters.forEach { meter ->
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerHigh,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = meter.label,
                                    style = MaterialTheme.typography.labelLarge
                                )
                                Text(
                                    text = "Старт: ${meter.initialValue}${meter.unit} • Диапазон ${meter.minValue}..${meter.maxValue}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            IconButton(
                                onClick = {
                                    onSimulationChange(
                                        simulation.copy(
                                            customMeters = simulation.customMeters.filterNot { it.id == meter.id }
                                        )
                                    )
                                }
                            ) {
                                Icon(Icons.Outlined.Close, contentDescription = "Удалить шкалу")
                            }
                        }
                    }
                }
            }
        }
    }

    if (showAddMeterDialog) {
        var label by remember { mutableStateOf("") }
        var initialStr by remember { mutableStateOf("50") }
        var maxStr by remember { mutableStateOf("100") }
        var unit by remember { mutableStateOf("%") }
        var warnWhenBelow by remember { mutableStateOf(true) }

        AlertDialog(
            onDismissRequest = { showAddMeterDialog = false },
            title = { Text("Новая шкала симуляции") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = label,
                        onValueChange = { label = it },
                        label = { Text("Название шкалы") },
                        placeholder = { Text("Рассудок / Улики / Репутация / Тепло") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = initialStr,
                            onValueChange = { initialStr = it.filter { ch -> ch.isDigit() || ch == '-' } },
                            label = { Text("Старт") },
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedTextField(
                            value = maxStr,
                            onValueChange = { maxStr = it.filter { ch -> ch.isDigit() } },
                            label = { Text("Макс") },
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedTextField(
                            value = unit,
                            onValueChange = { unit = it.take(6) },
                            label = { Text("Ед.") },
                            singleLine = true,
                            modifier = Modifier.weight(0.7f)
                        )
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Опасно при низком значении", style = MaterialTheme.typography.bodySmall)
                        Switch(checked = warnWhenBelow, onCheckedChange = { warnWhenBelow = it })
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val cleanLabel = label.trim()
                        if (cleanLabel.isNotBlank()) {
                            val maxVal = (maxStr.toLongOrNull() ?: 100L).coerceAtLeast(1L)
                            val initVal = (initialStr.toLongOrNull() ?: (maxVal / 2)).coerceIn(0L, maxVal)
                            val newMeter = CustomMeterSpec(
                                id = "m_${UUID.randomUUID().toString().take(6)}",
                                label = cleanLabel,
                                initialValue = initVal,
                                minValue = 0L,
                                maxValue = maxVal,
                                unit = unit.trim(),
                                warningThreshold = if (warnWhenBelow) (maxVal / 5).coerceAtLeast(1L) else (maxVal * 4 / 5),
                                warnWhenBelow = warnWhenBelow,
                                showInHud = true
                            )
                            onSimulationChange(
                                simulation.copy(customMeters = simulation.customMeters + newMeter)
                            )
                            showAddMeterDialog = false
                        }
                    },
                    enabled = label.isNotBlank()
                ) {
                    Text("Добавить")
                }
            },
            dismissButton = {
                TextButton(onClick = { showAddMeterDialog = false }) { Text("Отмена") }
            }
        )
    }
}
