package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.Explore
import androidx.compose.material.icons.outlined.Groups
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.data.model.AgentConfig
import com.example.data.model.AgentPipelinePresets
import com.example.data.model.AgentSetup
import com.example.data.model.ExpandedScenarioDraft
import com.example.data.model.RpMode

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun QuickCreateChatDialog(
    initialAutoFillWithAi: Boolean = true,
    isExpandingWithAi: Boolean = false,
    defaultAgentPresetId: String = "full_rpg_squad",
    customAgentsLibrary: List<AgentConfig> = emptyList(),
    onSaveCustomAgent: (AgentConfig) -> Unit = {},
    onDeleteCustomAgent: (String) -> Unit = {},
    localLlmEnabled: Boolean = false,
    freeOnlyFilter: Boolean = true,
    onExpandWithAi: (
        title: String,
        rpMode: RpMode,
        characterName: String,
        systemPrompt: String,
        firstMessage: String,
        freeformIdea: String,
        onDone: (Result<ExpandedScenarioDraft>) -> Unit
    ) -> Unit = { _, _, _, _, _, _, cb -> cb(Result.failure(IllegalStateException("AI недоступен"))) },
    onAutoFillPreferenceChange: (Boolean) -> Unit = {},
    onDismiss: () -> Unit,
    onCreate: (
        title: String,
        rpMode: RpMode,
        characterName: String,
        systemPrompt: String,
        firstMessage: String,
        freeformIdea: String,
        autoFillWithAi: Boolean,
        preExpandedDraft: ExpandedScenarioDraft?,
        agentSetup: AgentSetup
    ) -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    var freeformIdea by remember { mutableStateOf("") }
    var title by remember { mutableStateOf("") }
    var characterName by remember { mutableStateOf("") }
    var rpMode by remember { mutableStateOf(RpMode.CHARACTER_1ON1) }
    var systemPrompt by remember { mutableStateOf("") }
    var firstMessage by remember { mutableStateOf("") }
    var autoFillOnCreate by remember(initialAutoFillWithAi) { mutableStateOf(initialAutoFillWithAi) }
    var expandedDraft by remember { mutableStateOf<ExpandedScenarioDraft?>(null) }
    var aiStatusError by remember { mutableStateOf<String?>(null) }
    var agentSetupDraft by remember(defaultAgentPresetId) {
        mutableStateOf(
            AgentSetup(
                presetId = defaultAgentPresetId,
                agents = AgentPipelinePresets.forPreset(defaultAgentPresetId)
            )
        )
    }
    var hasCustomizedAgents by remember { mutableStateOf(false) }
    var agentsExpanded by remember { mutableStateOf(false) }

    ModalBottomSheet(
        onDismissRequest = { if (!isExpandingWithAi) onDismiss() },
        sheetState = sheetState
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .imePadding()
                .navigationBarsPadding()
                .heightIn(max = 660.dp)
                .padding(horizontal = 16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Новый RP-чат",
                        style = MaterialTheme.typography.headlineMedium
                    )
                    Text(
                        text = "Заполните хоть 1 поле — AI сам продумает лор, карту и статы",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f, fill = false)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedTextField(
                    value = freeformIdea,
                    onValueChange = { freeformIdea = it },
                    label = { Text("Идея или завязка (хватит пары слов / предложений)") },
                    placeholder = {
                        Text("Например: Нуар-киберпанк, наёмник ищет пропавшего андроида в затопленном нижнем городе")
                    },
                    minLines = 2,
                    maxLines = 5,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("quick_chat_idea_input")
                )

                Text(
                    text = "Формат ролевой игры",
                    style = MaterialTheme.typography.labelLarge
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    RpMode.entries.take(2).forEach { mode ->
                        FilterChip(
                            selected = rpMode == mode,
                            onClick = {
                                rpMode = mode
                                if (!hasCustomizedAgents) {
                                    agentSetupDraft = AgentPipelinePresets.setupForMode(mode)
                                }
                            },
                            label = { Text(mode.shortRu) },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    RpMode.entries.drop(2).forEach { mode ->
                        FilterChip(
                            selected = rpMode == mode,
                            onClick = {
                                rpMode = mode
                                if (!hasCustomizedAgents) {
                                    agentSetupDraft = AgentPipelinePresets.setupForMode(mode)
                                }
                            },
                            label = { Text(mode.shortRu) },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                // Configurable Multi-Agent Squad Selector
                Surface(
                    shape = MaterialTheme.shapes.medium,
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                    onClick = { agentsExpanded = !agentsExpanded },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("quick_chat_agents_toggle")
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
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.Groups,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary
                                )
                                Column {
                                    Text(
                                        text = "Команда агентов (${agentSetupDraft.activeAgentCount()} акт.)",
                                        style = MaterialTheme.typography.titleSmall
                                    )
                                    Text(
                                        text = agentSetupDraft.executionMode.labelRu +
                                            if (agentSetupDraft.hasAdultAgent()) " • 18+" else "",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                            Icon(
                                imageVector = if (agentsExpanded) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore,
                                contentDescription = if (agentsExpanded) "Свернуть агентов" else "Настроить агентов"
                            )
                        }
                        AnimatedVisibility(visible = agentsExpanded) {
                            AgentPipelineBuilder(
                                setup = agentSetupDraft,
                                onSetupChange = {
                                    agentSetupDraft = it
                                    hasCustomizedAgents = true
                                },
                                customLibrary = customAgentsLibrary,
                                onSaveCustomAgent = onSaveCustomAgent,
                                onDeleteCustomAgent = onDeleteCustomAgent,
                                localLlmEnabled = localLlmEnabled,
                                freeOnlyFilter = freeOnlyFilter,
                                compact = true
                            )
                        }
                    }
                }

                // AI Expansion Action Card
                Surface(
                    shape = MaterialTheme.shapes.medium,
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = {
                                aiStatusError = null
                                onExpandWithAi(
                                    title,
                                    rpMode,
                                    characterName,
                                    systemPrompt,
                                    firstMessage,
                                    freeformIdea
                                ) { res ->
                                    res.onSuccess { draft ->
                                        expandedDraft = draft
                                        if (title.isBlank()) title = draft.title
                                        if (characterName.isBlank()) characterName = draft.characterName
                                        if (systemPrompt.isBlank()) {
                                            systemPrompt = buildString {
                                                append(draft.systemPrompt)
                                                if (draft.characterBio.isNotBlank()) {
                                                    append("\n\n")
                                                    append(draft.characterBio)
                                                }
                                            }
                                        }
                                        if (firstMessage.isBlank()) firstMessage = draft.firstMessage
                                    }.onFailure { err ->
                                        aiStatusError = err.localizedMessage ?: "Не удалось сгенерировать мир."
                                    }
                                }
                            },
                            enabled = !isExpandingWithAi && (
                                freeformIdea.isNotBlank() ||
                                    title.isNotBlank() ||
                                    characterName.isNotBlank() ||
                                    systemPrompt.isNotBlank()
                                ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("quick_chat_ai_fill_button")
                        ) {
                            if (isExpandingWithAi) {
                                CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("AI продумывает мир, лор и статы...")
                            } else {
                                Icon(Icons.Outlined.AutoAwesome, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Дозаполнить AI (мир, лор, карта, RPG)")
                            }
                        }

                        if (aiStatusError != null) {
                            Text(
                                text = aiStatusError!!,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error
                            )
                        }

                        val previewDraft = expandedDraft
                        if (previewDraft != null) {
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = MaterialTheme.colorScheme.surfaceContainer,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(
                                    modifier = Modifier.padding(10.dp),
                                    verticalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Icon(
                                            Icons.Outlined.Check,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.tertiary,
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Text(
                                            text = "AI создал мир: лорбук (${previewDraft.loreEntries.size}), факты (${previewDraft.loreNotes.size})",
                                            style = MaterialTheme.typography.labelMedium,
                                            color = MaterialTheme.colorScheme.tertiary
                                        )
                                    }
                                    if (previewDraft.rpgState.locations.isNotEmpty()) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                                        ) {
                                            Icon(
                                                Icons.Outlined.Explore,
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.size(14.dp)
                                            )
                                            Text(
                                                text = "Локации: " + previewDraft.rpgState.locations.joinToString(" → ") { it.name },
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }
                                    if (previewDraft.rpgState.activeQuest.isNotBlank()) {
                                        Text(
                                            text = "Квест: ${previewDraft.rpgState.activeQuest}",
                                            style = MaterialTheme.typography.bodySmall
                                        )
                                    }
                                    if (previewDraft.rpgState.inventory.isNotEmpty() || previewDraft.tags.isNotEmpty()) {
                                        FlowRow(
                                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                                            verticalArrangement = Arrangement.spacedBy(4.dp)
                                        ) {
                                            (previewDraft.tags + previewDraft.rpgState.inventory).take(6).forEach { chipText ->
                                                Surface(
                                                    shape = RoundedCornerShape(6.dp),
                                                    color = MaterialTheme.colorScheme.surfaceContainerHighest
                                                ) {
                                                    Text(
                                                        text = chipText,
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

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Авто-дополнять пустые поля при «Начать RP»",
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                            Switch(
                                checked = autoFillOnCreate,
                                onCheckedChange = {
                                    autoFillOnCreate = it
                                    onAutoFillPreferenceChange(it)
                                },
                                modifier = Modifier.testTag("quick_chat_autofill_switch")
                            )
                        }
                    }
                }

                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Название чата / мира") },
                    placeholder = { Text("Например: Таверна на краю тумана (или пусто для AI)") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("quick_chat_title_input")
                )

                OutlinedTextField(
                    value = characterName,
                    onValueChange = { characterName = it },
                    label = { Text("Имя персонажа или Мастера") },
                    placeholder = { Text("Например: Капитан Рейна / DM") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("quick_chat_character_input")
                )

                OutlinedTextField(
                    value = systemPrompt,
                    onValueChange = { systemPrompt = it },
                    label = { Text("Системный промпт и характер") },
                    placeholder = {
                        Text("Оставьте пустым — AI сам пропишет характер, правила и секреты")
                    },
                    minLines = 3,
                    maxLines = 6,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("quick_chat_system_prompt_input")
                )

                OutlinedTextField(
                    value = firstMessage,
                    onValueChange = { firstMessage = it },
                    label = { Text("Стартовое сообщение / открывающая сцена") },
                    placeholder = { Text("Оставьте пустым — AI создаст живую первую сцену") },
                    minLines = 2,
                    maxLines = 5,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("quick_chat_first_msg_input")
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                TextButton(
                    onClick = onDismiss,
                    enabled = !isExpandingWithAi,
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Отмена")
                }
                Button(
                    onClick = {
                        onCreate(
                            title,
                            rpMode,
                            characterName,
                            systemPrompt,
                            firstMessage,
                            freeformIdea,
                            autoFillOnCreate,
                            expandedDraft,
                            agentSetupDraft
                        )
                    },
                    enabled = !isExpandingWithAi,
                    modifier = Modifier
                        .weight(1.4f)
                        .testTag("quick_chat_create_confirm")
                ) {
                    if (isExpandingWithAi) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            color = MaterialTheme.colorScheme.onPrimary,
                            strokeWidth = 2.dp
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Генерирую мир...")
                    } else {
                        Text("Начать RP")
                    }
                }
            }
        }
    }
}
