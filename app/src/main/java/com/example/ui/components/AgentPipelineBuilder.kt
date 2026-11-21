package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Groups
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.model.AgentConfig
import com.example.data.model.AgentPipelinePresets
import com.example.data.model.AgentRole
import com.example.data.model.AgentSetup
import com.example.data.model.AgentStage
import com.example.data.model.LlmProvider
import com.example.data.model.PipelineExecutionMode
import com.example.data.model.SemanticEntityType
import java.util.Locale
import java.util.UUID

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AgentPipelineBuilder(
    setup: AgentSetup,
    onSetupChange: (AgentSetup) -> Unit,
    customLibrary: List<AgentConfig> = emptyList(),
    onSaveCustomAgent: (AgentConfig) -> Unit = {},
    onDeleteCustomAgent: (String) -> Unit = {},
    localLlmEnabled: Boolean = false,
    freeOnlyFilter: Boolean = true,
    compact: Boolean = false,
    modifier: Modifier = Modifier
) {
    var editingAgent by remember { mutableStateOf<AgentConfig?>(null) }
    var showCreateCustomDialog by remember { mutableStateOf(false) }
    var pendingSensitiveAgent by remember { mutableStateOf<AgentConfig?>(null) }

    val availableProviders = remember(freeOnlyFilter, localLlmEnabled) {
        val base = if (freeOnlyFilter) LlmProvider.freeProviders() else LlmProvider.entries
        if (localLlmEnabled) base else base.filterNot { it == LlmProvider.LOCAL_LLM }
    }

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // Header summary
        Surface(
            shape = MaterialTheme.shapes.medium,
            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier.padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Icon(
                    imageVector = Icons.Outlined.Groups,
                    contentDescription = "Команда агентов",
                    tint = MaterialTheme.colorScheme.primary
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Команда агентов (${setup.activeAgentCount()} активных)",
                        style = MaterialTheme.typography.titleSmall
                    )
                    Text(
                        text = "Режим: ${setup.executionMode.labelRu}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                OutlinedButton(
                    onClick = { showCreateCustomDialog = true },
                    modifier = Modifier.testTag("add_custom_agent_button")
                ) {
                    Icon(Icons.Outlined.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Свой агент")
                }
            }
        }

        // Preset selector
        Text("Готовые сетапы команды", style = MaterialTheme.typography.labelLarge)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            AgentPipelinePresets.presets.forEach { preset ->
                FilterChip(
                    selected = setup.presetId == preset.id,
                    onClick = {
                        val customAgents = setup.agents.filter { it.isCustom }
                        onSetupChange(
                            setup.copy(
                                presetId = preset.id,
                                agents = AgentPipelinePresets.forPreset(preset.id) + customAgents
                            )
                        )
                    },
                    label = { Text(preset.titleRu) },
                    modifier = Modifier.testTag("agent_preset_${preset.id}")
                )
            }
        }
        AgentPipelinePresets.presets.firstOrNull { it.id == setup.presetId }?.let { activePreset ->
            Text(
                text = activePreset.subtitleRu,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        // Execution mode
        Text("Режим работы конвейера", style = MaterialTheme.typography.labelLarge)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            PipelineExecutionMode.entries.forEach { mode ->
                FilterChip(
                    selected = setup.executionMode == mode,
                    onClick = { onSetupChange(setup.copy(executionMode = mode)) },
                    label = { Text(mode.labelRu) },
                    modifier = Modifier.testTag("pipeline_mode_${mode.name}")
                )
            }
        }
        Text(
            text = setup.executionMode.subtitleRu,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        // Agent roster
        Text("Состав агентов и их настройка", style = MaterialTheme.typography.labelLarge)
        val displayedAgents = if (compact) {
            setup.agents.filter { it.enabled || it.isCustom || it.role == AgentRole.ADULT_EXPANDER || it.role == AgentRole.ANATOMY_DETAILER }
        } else {
            setup.agents
        }

        displayedAgents.forEach { agent ->
            Surface(
                shape = MaterialTheme.shapes.medium,
                color = if (agent.enabled) {
                    MaterialTheme.colorScheme.surfaceContainerHigh
                } else {
                    MaterialTheme.colorScheme.surfaceContainerLow
                },
                border = if (agent.role.isSensitive && agent.enabled) {
                    BorderStroke(1.dp, MaterialTheme.colorScheme.secondary.copy(alpha = 0.6f))
                } else null,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("agent_card_${agent.id}")
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                text = agent.displayName(),
                                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold)
                            )
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = MaterialTheme.colorScheme.surfaceContainerHighest
                            ) {
                                Text(
                                    text = when (agent.stage) {
                                        AgentStage.PRE_PLANNING -> "Совет"
                                        AgentStage.PRIMARY_PROSE -> "Проза"
                                        AgentStage.REFINEMENT -> "Детали"
                                        AgentStage.POST_PROCESS -> "Пост"
                                    },
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                            if (agent.modelOverride.isNotBlank()) {
                                Surface(
                                    shape = RoundedCornerShape(6.dp),
                                    color = MaterialTheme.colorScheme.tertiaryContainer
                                ) {
                                    Text(
                                        text = agent.modelOverride.take(18),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onTertiaryContainer,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                            }
                        }
                        Text(
                            text = if (agent.isCustom && agent.customPrompt.isNotBlank()) {
                                agent.customPrompt.take(96)
                            } else {
                                agent.role.descriptionRu
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    IconButton(
                        onClick = { editingAgent = agent },
                        modifier = Modifier.testTag("tune_agent_${agent.id}")
                    ) {
                        Icon(Icons.Outlined.Tune, contentDescription = "Настроить ${agent.displayName()}")
                    }

                    if (agent.isCustom) {
                        IconButton(onClick = {
                            onSetupChange(setup.copy(agents = setup.agents.filterNot { it.id == agent.id }))
                        }) {
                            Icon(Icons.Outlined.Close, contentDescription = "Убрать агента из чата")
                        }
                    }

                    Switch(
                        checked = agent.enabled,
                        onCheckedChange = { enable ->
                            if (enable && agent.role.isSensitive) {
                                pendingSensitiveAgent = agent
                            } else {
                                onSetupChange(
                                    setup.copy(
                                        agents = setup.agents.map {
                                            if (it.id == agent.id) it.copy(enabled = enable) else it
                                        }
                                    )
                                )
                            }
                        },
                        modifier = Modifier.testTag("toggle_agent_${agent.id}")
                    )
                }
            }
        }

        // Reusable Custom Agents Library
        if (customLibrary.isNotEmpty()) {
            HorizontalDivider()
            Text("Сохранённые пользовательские агенты", style = MaterialTheme.typography.labelLarge)
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                customLibrary.forEach { libAgent ->
                    val alreadyInChat = setup.agents.any { it.id == libAgent.id }
                    FilterChip(
                        selected = alreadyInChat,
                        onClick = {
                            val nextAgents = if (alreadyInChat) {
                                setup.agents.filterNot { it.id == libAgent.id }
                            } else {
                                setup.agents + libAgent.copy(enabled = true)
                            }
                            onSetupChange(setup.copy(agents = nextAgents))
                        },
                        label = { Text(libAgent.displayName()) },
                        trailingIcon = {
                            IconButton(
                                onClick = { onDeleteCustomAgent(libAgent.id) },
                                modifier = Modifier.size(18.dp)
                            ) {
                                Icon(
                                    Icons.Outlined.Close,
                                    contentDescription = "Удалить из библиотеки",
                                    modifier = Modifier.size(14.dp)
                                )
                            }
                        }
                    )
                }
            }
        }

        // Semantic markup tag toggles
        if (!compact && setup.isRoleEnabled(AgentRole.SEMANTIC_TAGGER)) {
            HorizontalDivider()
            Text("Цветная разметка в тексте (Разметчик)", style = MaterialTheme.typography.labelLarge)
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                SemanticEntityType.entries.forEach { tagType ->
                    val enabled = tagType in setup.enabledTagTypes
                    FilterChip(
                        selected = enabled,
                        onClick = {
                            val nextTags = if (enabled) {
                                setup.enabledTagTypes - tagType
                            } else {
                                setup.enabledTagTypes + tagType
                            }
                            onSetupChange(setup.copy(enabledTagTypes = nextTags))
                        },
                        label = { Text("${tagType.badgeIcon} ${tagType.labelRu}") }
                    )
                }
            }
        }

        // Universal simulation engine modules & custom meters
        if (!compact) {
            SimulationDesignerSection(
                simulation = setup.simulation,
                onSimulationChange = { nextSim ->
                    onSetupChange(setup.copy(simulation = nextSim))
                }
            )
        }
    }

    // Sensitive 18+ opt-in confirmation
    val sensitiveTarget = pendingSensitiveAgent
    if (sensitiveTarget != null) {
        AlertDialog(
            onDismissRequest = { pendingSensitiveAgent = null },
            icon = {
                Icon(
                    Icons.Outlined.WarningAmber,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.secondary
                )
            },
            title = { Text("Включить Агент 18+?") },
            text = {
                Text(
                    "Агент 18+ снимает художественную сухость и раскрывает зрелые, чувственные и тёмные сцены полностью в рамках правил выбранного провайдера LLM. Вы подтверждаете подключение этого агента для данного чата?"
                )
            },
            confirmButton = {
                Button(onClick = {
                    onSetupChange(
                        setup.copy(
                            agents = setup.agents.map {
                                if (it.id == sensitiveTarget.id) it.copy(enabled = true) else it
                            }
                        )
                    )
                    pendingSensitiveAgent = null
                }) {
                    Text("Подключить 18+")
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingSensitiveAgent = null }) { Text("Отмена") }
            }
        )
    }

    // Per-agent fine tuning dialog
    val tuneTarget = editingAgent
    if (tuneTarget != null) {
        AgentConfigEditorDialog(
            initial = tuneTarget,
            availableProviders = availableProviders,
            showLibrarySaveOption = tuneTarget.isCustom,
            onDismiss = { editingAgent = null },
            onConfirm = { updated, saveToLib ->
                onSetupChange(
                    setup.copy(
                        agents = setup.agents.map { if (it.id == updated.id) updated else it }
                    )
                )
                if (saveToLib && updated.isCustom) {
                    onSaveCustomAgent(updated)
                }
                editingAgent = null
            }
        )
    }

    // Custom agent creator dialog
    if (showCreateCustomDialog) {
        AgentConfigEditorDialog(
            initial = AgentConfig(
                id = "custom_${UUID.randomUUID().toString().take(8)}",
                role = AgentRole.CUSTOM,
                customName = "",
                customPrompt = "",
                enabled = true,
                stage = AgentStage.PRE_PLANNING,
                isCustom = true
            ),
            availableProviders = availableProviders,
            showLibrarySaveOption = true,
            onDismiss = { showCreateCustomDialog = false },
            onConfirm = { created, saveToLib ->
                val clean = created.copy(
                    isCustom = true,
                    role = AgentRole.CUSTOM,
                    customName = created.customName.ifBlank { "Уникальный агент" }
                )
                onSetupChange(setup.copy(agents = setup.agents + clean))
                if (saveToLib) {
                    onSaveCustomAgent(clean)
                }
                showCreateCustomDialog = false
            }
        )
    }
}

@Composable
private fun AgentConfigEditorDialog(
    initial: AgentConfig,
    availableProviders: List<LlmProvider>,
    showLibrarySaveOption: Boolean,
    onDismiss: () -> Unit,
    onConfirm: (AgentConfig, saveToLibrary: Boolean) -> Unit
) {
    var name by remember(initial.id) { mutableStateOf(initial.customName) }
    var prompt by remember(initial.id) { mutableStateOf(initial.customPrompt) }
    var stage by remember(initial.id) { mutableStateOf(initial.stage) }
    var providerOverride by remember(initial.id) { mutableStateOf(initial.providerOverride) }
    var modelOverride by remember(initial.id) { mutableStateOf(initial.modelOverride) }
    var useCustomTemp by remember(initial.id) { mutableStateOf(initial.temperatureOverride != null) }
    var tempValue by remember(initial.id) {
        mutableFloatStateOf((initial.temperatureOverride ?: 0.85).toFloat())
    }
    var detailLevel by remember(initial.id) { mutableIntStateOf(initial.detailLevel.coerceIn(1, 3)) }
    var saveToLibrary by remember(initial.id) { mutableStateOf(initial.isCustom) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                if (initial.isCustom && initial.customName.isBlank()) {
                    "Новый пользовательский агент"
                } else {
                    "Настройка: ${initial.displayName()}"
                }
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Имя агента (необязательно)") },
                    placeholder = { Text(initial.role.labelRu) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Text("Этап в конвейере:", style = MaterialTheme.typography.labelMedium)
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    AgentStage.entries.forEach { st ->
                        FilterChip(
                            selected = stage == st,
                            onClick = { stage = st },
                            label = { Text(st.labelRu.substringBefore(" (")) }
                        )
                    }
                }

                Text("Глубина детализации: $detailLevel / 3", style = MaterialTheme.typography.labelMedium)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf(1 to "Лаконично", 2 to "Баланс", 3 to "Максимум").forEach { (lvl, label) ->
                        FilterChip(
                            selected = detailLevel == lvl,
                            onClick = { detailLevel = lvl },
                            label = { Text(label) }
                        )
                    }
                }

                OutlinedTextField(
                    value = prompt,
                    onValueChange = { prompt = it },
                    label = { Text("Инструкция / роль агента") },
                    placeholder = {
                        Text("Например: Следи за средневековым этикетом, тактикой фехтования и ценами в серебре")
                    },
                    minLines = 3,
                    maxLines = 6,
                    modifier = Modifier.fillMaxWidth()
                )

                Text("Модель для этого агента (необязательно):", style = MaterialTheme.typography.labelMedium)
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    FilterChip(
                        selected = providerOverride.isBlank(),
                        onClick = {
                            providerOverride = ""
                            modelOverride = ""
                        },
                        label = { Text("Как в чате") }
                    )
                    availableProviders.forEach { prov ->
                        FilterChip(
                            selected = providerOverride == prov.name,
                            onClick = {
                                providerOverride = prov.name
                                if (modelOverride.isBlank()) {
                                    modelOverride = prov.suggestedModels.firstOrNull().orEmpty()
                                }
                            },
                            label = { Text(prov.displayName.substringBefore(" (")) }
                        )
                    }
                }

                if (providerOverride.isNotBlank()) {
                    OutlinedTextField(
                        value = modelOverride,
                        onValueChange = { modelOverride = it },
                        label = { Text("ID модели агента") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    val provObj = runCatching { LlmProvider.valueOf(providerOverride) }.getOrNull()
                    if (provObj != null) {
                        Row(
                            modifier = Modifier.horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            provObj.suggestedModels.forEach { m ->
                                AssistChip(onClick = { modelOverride = m }, label = { Text(m) })
                            }
                        }
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Своя температура (T)", style = MaterialTheme.typography.labelMedium)
                    Switch(checked = useCustomTemp, onCheckedChange = { useCustomTemp = it })
                }
                if (useCustomTemp) {
                    Text(
                        text = "T = ${String.format(Locale.US, "%.2f", tempValue)}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Slider(
                        value = tempValue,
                        onValueChange = { tempValue = it },
                        valueRange = 0.0f..1.6f
                    )
                }

                if (showLibrarySaveOption) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Сохранить в библиотеку шаблонов", style = MaterialTheme.typography.bodySmall)
                        Switch(checked = saveToLibrary, onCheckedChange = { saveToLibrary = it })
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = {
                onConfirm(
                    initial.copy(
                        customName = name.trim().take(60),
                        customPrompt = prompt.trim().take(2500),
                        stage = stage,
                        providerOverride = providerOverride,
                        modelOverride = modelOverride.trim().take(120),
                        temperatureOverride = if (useCustomTemp) tempValue.toDouble() else null,
                        detailLevel = detailLevel
                    ),
                    saveToLibrary
                )
            }) {
                Icon(Icons.Outlined.AutoAwesome, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Сохранить")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Отмена") }
        }
    )
}
