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
import androidx.compose.material.icons.outlined.Casino
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.LockOpen
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.ShoppingBag
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
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
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.data.model.NpcDossier

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun NpcDossierDialog(
    npc: NpcDossier,
    playerGold: Long,
    perceptionMod: Long,
    onDismiss: () -> Unit,
    onRollPerception: (npcId: String) -> Unit,
    onBuyItem: (npcId: String, itemName: String, priceGold: Long) -> Unit,
    onUpdateNpc: (NpcDossier) -> Unit
) {
    var notesDraft by remember(npc.id, npc.notes) { mutableStateOf(npc.notes) }
    val dispNormalized = ((npc.disposition + 100L) / 200f).coerceIn(0f, 1f)
    val trustProgress = if (npc.trustThreshold > 0L) {
        (npc.disposition.coerceAtLeast(0L).toFloat() / npc.trustThreshold.toFloat()).coerceIn(0f, 1f)
    } else 1f

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 4.dp,
            modifier = Modifier
                .fillMaxWidth(0.94f)
                .padding(vertical = 16.dp)
                .testTag("npc_dossier_dialog_${npc.id}")
        ) {
            Column(
                modifier = Modifier
                    .padding(18.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = MaterialTheme.colorScheme.primaryContainer,
                            modifier = Modifier.size(48.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Person,
                                contentDescription = npc.name,
                                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.padding(10.dp)
                            )
                        }
                        Column {
                            Text(
                                text = npc.name.ifBlank { "Безымянный NPC" },
                                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
                            )
                            Text(
                                text = listOf(npc.role, npc.faction)
                                    .filter { it.isNotBlank() }
                                    .joinToString(" • ")
                                    .ifBlank { "Персонаж сцены" },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Outlined.Close, contentDescription = "Закрыть досье")
                    }
                }

                // Disposition & Trust card
                Surface(
                    shape = MaterialTheme.shapes.medium,
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "Отношение: ${npc.dispositionLabelRu()} (${npc.disposition})",
                                style = MaterialTheme.typography.titleSmall
                            )
                            Text(
                                text = "Настроение: ${npc.mood}",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                        LinearProgressIndicator(
                            progress = { dispNormalized },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(8.dp)
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Порог доверия: ${npc.disposition}/${npc.trustThreshold}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                TextButton(onClick = {
                                    onUpdateNpc(npc.copy(disposition = (npc.disposition - 10).coerceIn(-100L, 100L)))
                                }) { Text("-10") }
                                TextButton(onClick = {
                                    onUpdateNpc(npc.copy(disposition = (npc.disposition + 10).coerceIn(-100L, 100L)))
                                }) { Text("+10") }
                            }
                        }
                        if (!npc.statsRevealed && trustProgress < 1f) {
                            Text(
                                text = "Статы и секреты откроются при доверии ≥ ${npc.trustThreshold} или через проверку Восприятия.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                // Fog of War Stats section
                Surface(
                    shape = MaterialTheme.shapes.medium,
                    color = if (npc.statsRevealed) {
                        MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.45f)
                    } else {
                        MaterialTheme.colorScheme.surfaceContainerHigh
                    },
                    border = BorderStroke(
                        1.dp,
                        if (npc.statsRevealed) MaterialTheme.colorScheme.tertiary.copy(alpha = 0.5f)
                        else MaterialTheme.colorScheme.outlineVariant
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(
                                    imageVector = if (npc.statsRevealed) Icons.Outlined.LockOpen else Icons.Outlined.Lock,
                                    contentDescription = null,
                                    tint = if (npc.statsRevealed) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.secondary,
                                    modifier = Modifier.size(18.dp)
                                )
                                Text(
                                    text = if (npc.statsRevealed) "Характеристики (Раскрыты)" else "Характеристики (Скрыты туманом)",
                                    style = MaterialTheme.typography.titleSmall
                                )
                            }
                            if (!npc.statsRevealed) {
                                TextButton(onClick = {
                                    onUpdateNpc(
                                        npc.copy(
                                            statsRevealed = true,
                                            revealedSecretCount = maxOf(npc.revealedSecretCount, 1L)
                                                .coerceAtMost(npc.secrets.size.toLong())
                                        )
                                    )
                                }) {
                                    Text("Открыть GM")
                                }
                            }
                        }

                        if (npc.statsRevealed && npc.stats.isNotEmpty()) {
                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                npc.stats.forEach { (k, v) ->
                                    Surface(
                                        shape = RoundedCornerShape(8.dp),
                                        color = MaterialTheme.colorScheme.surface
                                    ) {
                                        Text(
                                            text = "$k: $v",
                                            style = MaterialTheme.typography.labelMedium,
                                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                                        )
                                    }
                                }
                            }
                        } else {
                            Text(
                                text = "Сила: ??? • Ловкость: ??? • Интеллект: ??? • Харизма: ???",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        val modStr = if (perceptionMod >= 0) "+$perceptionMod" else "$perceptionMod"
                        if (!npc.statsRevealed || npc.hasHiddenSecrets()) {
                            Button(
                                onClick = { onRollPerception(npc.id) },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("npc_perception_check_button")
                            ) {
                                Icon(Icons.Outlined.Casino, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Проверить Восприятие (d20 $modStr vs DC ${npc.perceptionDc})")
                            }
                        }
                    }
                }

                // Secrets (Visible vs Hidden count)
                if (npc.secrets.isNotEmpty() || npc.revealedSecretCount > 0L) {
                    Text(
                        text = "Секреты (${npc.visibleSecrets().size}/${npc.secrets.size})",
                        style = MaterialTheme.typography.titleSmall
                    )
                    npc.visibleSecrets().forEach { secret ->
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.55f),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = "• $secret",
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.padding(10.dp)
                            )
                        }
                    }
                    val hiddenLeft = (npc.secrets.size - npc.visibleSecrets().size).coerceAtLeast(0)
                    if (hiddenLeft > 0) {
                        Text(
                            text = "Скрытых тайн осталось: $hiddenLeft (пройдите проверку Восприятия или повысьте доверие)",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                // Trade / Barter section
                if (npc.inventory.isNotEmpty()) {
                    HorizontalDivider()
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(Icons.Outlined.ShoppingBag, contentDescription = null, tint = MaterialTheme.colorScheme.secondary, modifier = Modifier.size(18.dp))
                            Text("Лавка / Обмен NPC", style = MaterialTheme.typography.titleSmall)
                        }
                        Text(
                            text = "Ваше золото: ${playerGold}g",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.secondary
                        )
                    }
                    npc.inventory.forEach { item ->
                        val basePrice = (12L + (item.length % 7) * 5L - (npc.disposition / 15L)).coerceAtLeast(4L)
                        Surface(
                            shape = MaterialTheme.shapes.small,
                            color = MaterialTheme.colorScheme.surfaceContainerLow,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(item, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                                OutlinedButton(
                                    onClick = { onBuyItem(npc.id, item, basePrice) },
                                    enabled = playerGold >= basePrice
                                ) {
                                    Text("Купить (${basePrice}g)")
                                }
                            }
                        }
                    }
                }

                if (npc.appearance.isNotBlank()) {
                    Text(
                        text = "Внешность: ${npc.appearance}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                OutlinedTextField(
                    value = notesDraft,
                    onValueChange = { notesDraft = it },
                    label = { Text("Личные заметки о персонаже") },
                    minLines = 2,
                    maxLines = 4,
                    modifier = Modifier.fillMaxWidth()
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onDismiss) { Text("Закрыть") }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(onClick = {
                        onUpdateNpc(npc.copy(notes = notesDraft.trim().take(600)))
                        onDismiss()
                    }) {
                        Text("Сохранить заметки")
                    }
                }
            }
        }
    }
}
