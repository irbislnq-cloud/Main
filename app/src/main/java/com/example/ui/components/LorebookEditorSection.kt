package com.example.ui.components

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
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.model.LoreEntry
import java.util.UUID

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun LorebookEditorSection(
    entries: List<LoreEntry>,
    onEntriesChange: (List<LoreEntry>) -> Unit,
    modifier: Modifier = Modifier
) {
    var editingEntry by remember { mutableStateOf<LoreEntry?>(null) }

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Умный лорбук (триггеры)",
                    style = MaterialTheme.typography.titleMedium
                )
                Text(
                    text = "Активируется при появлении ключевых слов в последних сообщениях",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            OutlinedButton(
                onClick = {
                    editingEntry = LoreEntry(
                        id = "",
                        title = "",
                        keys = emptyList(),
                        content = "",
                        alwaysActive = false,
                        useRegex = false,
                        priority = 15L,
                        enabled = true
                    )
                },
                enabled = entries.size < 40,
                modifier = Modifier.testTag("add_lore_entry_button")
            ) {
                Icon(Icons.Outlined.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("Запись")
            }
        }

        if (entries.isEmpty()) {
            Surface(
                shape = MaterialTheme.shapes.small,
                color = MaterialTheme.colorScheme.surfaceContainerLow,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "Пока нет триггерных записей. Добавьте персонажа, локацию или правило мира.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(12.dp)
                )
            }
        } else {
            entries.forEachIndexed { idx, entry ->
                Surface(
                    shape = MaterialTheme.shapes.medium,
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("lore_entry_card_$idx")
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                modifier = Modifier.weight(1f),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Switch(
                                    checked = entry.enabled,
                                    onCheckedChange = { checked ->
                                        val updated = entries.toMutableList()
                                        updated[idx] = entry.copy(enabled = checked)
                                        onEntriesChange(updated)
                                    }
                                )
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = entry.title.ifBlank {
                                            entry.keys.firstOrNull() ?: "Запись лора #${idx + 1}"
                                        },
                                        style = MaterialTheme.typography.titleSmall,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = "Приоритет: ${entry.priority} • " +
                                            if (entry.alwaysActive) "Всегда активен" else "Ключи: ${entry.keys.joinToString(", ").ifBlank { "нет" }}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = if (entry.alwaysActive) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.primary,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                            Row {
                                IconButton(onClick = { editingEntry = entry }) {
                                    Icon(Icons.Outlined.Edit, contentDescription = "Редактировать лор")
                                }
                                IconButton(onClick = {
                                    val updated = entries.toMutableList()
                                    updated.removeAt(idx)
                                    onEntriesChange(updated)
                                }) {
                                    Icon(Icons.Outlined.DeleteOutline, contentDescription = "Удалить лор")
                                }
                            }
                        }

                        Text(
                            text = entry.content,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )

                        if (entry.keys.isNotEmpty() && !entry.alwaysActive) {
                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                entry.keys.take(6).forEach { key ->
                                    Surface(
                                        shape = RoundedCornerShape(6.dp),
                                        color = MaterialTheme.colorScheme.surfaceContainerHighest
                                    ) {
                                        Text(
                                            text = key,
                                            style = MaterialTheme.typography.labelSmall,
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    val draft = editingEntry
    if (draft != null) {
        var title by remember(draft.id) { mutableStateOf(draft.title) }
        var keysRaw by remember(draft.id) { mutableStateOf(draft.keys.joinToString(", ")) }
        var content by remember(draft.id) { mutableStateOf(draft.content) }
        var alwaysActive by remember(draft.id) { mutableStateOf(draft.alwaysActive) }
        var useRegex by remember(draft.id) { mutableStateOf(draft.useRegex) }
        var priority by remember(draft.id) { mutableFloatStateOf(draft.priority.toFloat()) }

        AlertDialog(
            onDismissRequest = { editingEntry = null },
            title = { Text(if (draft.id.isBlank()) "Новая запись лорбука" else "Редактировать запись") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = title,
                        onValueChange = { title = it },
                        label = { Text("Название (локация / NPC / артефакт)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = keysRaw,
                        onValueChange = { keysRaw = it },
                        label = { Text("Триггерные слова (через запятую)") },
                        placeholder = { Text("таверна, гильдия, артефакт") },
                        enabled = !alwaysActive,
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Всегда в контексте", style = MaterialTheme.typography.bodyMedium)
                        Switch(checked = alwaysActive, onCheckedChange = { alwaysActive = it })
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Использовать RegExp", style = MaterialTheme.typography.bodyMedium)
                        Switch(checked = useRegex, onCheckedChange = { useRegex = it })
                    }
                    Column {
                        Text("Приоритет в контексте: ${priority.toInt()}", style = MaterialTheme.typography.labelMedium)
                        Slider(
                            value = priority,
                            onValueChange = { priority = it },
                            valueRange = 1f..100f
                        )
                    }
                    OutlinedTextField(
                        value = content,
                        onValueChange = { content = it },
                        label = { Text("Текст лора для LLM *") },
                        minLines = 3,
                        maxLines = 6,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    enabled = content.isNotBlank(),
                    onClick = {
                        val keys = keysRaw.split(",").map { it.trim() }.filter { it.isNotEmpty() }.take(20)
                        val saved = draft.copy(
                            id = draft.id.ifBlank { UUID.randomUUID().toString().take(16) },
                            title = title.trim().take(100),
                            keys = keys,
                            content = content.trim().take(1500),
                            alwaysActive = alwaysActive,
                            useRegex = useRegex,
                            priority = priority.toLong().coerceIn(1L, 100L)
                        )
                        val updated = entries.toMutableList()
                        val existingIdx = updated.indexOfFirst { it.id == draft.id && draft.id.isNotBlank() }
                        if (existingIdx >= 0) updated[existingIdx] = saved else updated.add(saved)
                        onEntriesChange(updated)
                        editingEntry = null
                    }
                ) {
                    Text("Сохранить")
                }
            },
            dismissButton = {
                TextButton(onClick = { editingEntry = null }) { Text("Отмена") }
            }
        )
    }
}
