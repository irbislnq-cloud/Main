package com.example.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.FileUpload
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.R
import com.example.data.model.AgentSetupCodec
import com.example.data.model.RpScenario
import com.example.data.model.SimulationRuleModule
import com.example.ui.viewmodel.UiState
import java.util.Locale

private enum class ScenarioFilterCategory(val labelRu: String) {
    ALL("Все миры"),
    PURE_RP("Чистый RP"),
    DETECTIVE("Детектив"),
    SURVIVAL("Выживание"),
    SCIFI("Sci-Fi / Кибер"),
    RPG("RPG"),
    POLITICS("Политика"),
    MATURE("18+ / Роман")
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ScenariosTab(
    scenariosState: UiState<List<RpScenario>>,
    onCreateNewScenario: () -> Unit,
    onImportCard: () -> Unit,
    onEditScenario: (RpScenario) -> Unit,
    onLaunchScenario: (RpScenario) -> Unit,
    onTogglePin: (RpScenario) -> Unit,
    onDeleteScenario: (RpScenario) -> Unit
) {
    var selectedFilter by remember { mutableStateOf(ScenarioFilterCategory.ALL) }

    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxSize()
    ) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Column {
                    Text(
                        text = "Библиотека миров и ролей",
                        style = MaterialTheme.typography.headlineMedium
                    )
                    Text(
                        text = "Персонажи, Мастера RPG, умный лорбук и импорт карточек PNG/JSON",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = onImportCard,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("import_card_button")
                    ) {
                        Icon(Icons.Outlined.FileUpload, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Импорт PNG / JSON")
                    }
                    Button(
                        onClick = onCreateNewScenario,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("new_scenario_button")
                    ) {
                        Icon(Icons.Outlined.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Создать")
                    }
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    ScenarioFilterCategory.entries.forEach { cat ->
                        FilterChip(
                            selected = cat == selectedFilter,
                            onClick = { selectedFilter = cat },
                            label = { Text(cat.labelRu) },
                            modifier = Modifier.testTag("scenario_filter_${cat.name}")
                        )
                    }
                }
            }
        }

        when (scenariosState) {
            is UiState.Loading -> {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(32.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator()
                    }
                }
            }
            is UiState.Error -> {
                item {
                    Surface(
                        shape = MaterialTheme.shapes.medium,
                        color = MaterialTheme.colorScheme.errorContainer,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = scenariosState.message,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            modifier = Modifier.padding(16.dp)
                        )
                    }
                }
            }
            is UiState.Success -> {
                val sorted = scenariosState.data
                    .filter { sc ->
                        if (selectedFilter == ScenarioFilterCategory.ALL) return@filter true
                        val setup = AgentSetupCodec.decode(sc.defaultAgentSetupJson)
                        val modules = setup.simulation.ruleModules
                        val tagsLower = sc.tags.joinToString(" ").lowercase() + " " + sc.subtitle.lowercase()
                        when (selectedFilter) {
                            ScenarioFilterCategory.ALL -> true
                            ScenarioFilterCategory.PURE_RP -> SimulationRuleModule.PURE_NARRATIVE in modules || !sc.defaultRpgState.enabled
                            ScenarioFilterCategory.DETECTIVE -> SimulationRuleModule.MYSTERY_INVESTIGATION in modules || tagsLower.contains("детектив")
                            ScenarioFilterCategory.SURVIVAL -> SimulationRuleModule.SURVIVAL_PRESSURE in modules || tagsLower.contains("выжив")
                            ScenarioFilterCategory.SCIFI -> tagsLower.contains("sci-fi") || tagsLower.contains("космос") || tagsLower.contains("кибер")
                            ScenarioFilterCategory.RPG -> SimulationRuleModule.TACTICAL_RPG in modules || sc.defaultRpgState.enabled
                            ScenarioFilterCategory.POLITICS -> SimulationRuleModule.WORLD_SIMULATION in modules || tagsLower.contains("интриг") || tagsLower.contains("политик")
                            ScenarioFilterCategory.MATURE -> SimulationRuleModule.SENSORY_INTIMACY in modules || tagsLower.contains("18+") || tagsLower.contains("роман")
                        }
                    }
                    .sortedByDescending { it.pinned }
                items(sorted, key = { it.id }) { scenario ->
                    val loreCount = scenario.loreNotes.size + scenario.loreEntries.size
                    val scenarioSetup = remember(scenario.defaultAgentSetupJson) {
                        AgentSetupCodec.decode(scenario.defaultAgentSetupJson)
                    }
                    Surface(
                        shape = MaterialTheme.shapes.large,
                        color = MaterialTheme.colorScheme.surfaceContainer,
                        tonalElevation = if (scenario.pinned) 3.dp else 1.dp,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("scenario_card_${scenario.id}")
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.Top
                            ) {
                                Row(
                                    modifier = Modifier.weight(1f),
                                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                                    verticalAlignment = Alignment.Top
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(52.dp)
                                            .clip(RoundedCornerShape(12.dp))
                                    ) {
                                        if (scenario.portraitUri.isNotBlank()) {
                                            AsyncImage(
                                                model = scenario.portraitUri,
                                                contentDescription = "Портрет ${scenario.title}",
                                                contentScale = ContentScale.Crop,
                                                modifier = Modifier.fillMaxSize()
                                            )
                                        } else {
                                            Image(
                                                painter = painterResource(id = R.drawable.img_world_emblem),
                                                contentDescription = null,
                                                contentScale = ContentScale.Crop,
                                                modifier = Modifier.fillMaxSize()
                                            )
                                        }
                                    }

                                    Column(
                                        modifier = Modifier.weight(1f),
                                        verticalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                            Surface(
                                                shape = RoundedCornerShape(8.dp),
                                                color = MaterialTheme.colorScheme.secondaryContainer
                                            ) {
                                                Text(
                                                    text = scenario.rpModeEnum().labelRu,
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                                )
                                            }
                                            if (scenario.defaultRpgState.enabled) {
                                                Surface(
                                                    shape = RoundedCornerShape(8.dp),
                                                    color = MaterialTheme.colorScheme.primaryContainer
                                                ) {
                                                    Text(
                                                        text = if (scenario.defaultRpgState.locations.isNotEmpty()) {
                                                            "RPG • Карта"
                                                        } else {
                                                            "RPG-лист"
                                                        },
                                                        style = MaterialTheme.typography.labelSmall,
                                                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                                    )
                                                }
                                            }
                                            if (scenarioSetup.simulation.customMeters.isNotEmpty()) {
                                                Surface(
                                                    shape = RoundedCornerShape(8.dp),
                                                    color = MaterialTheme.colorScheme.tertiaryContainer
                                                ) {
                                                    Text(
                                                        text = "Шкалы: ${scenarioSetup.simulation.customMeters.size}",
                                                        style = MaterialTheme.typography.labelSmall,
                                                        color = MaterialTheme.colorScheme.onTertiaryContainer,
                                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                                    )
                                                }
                                            }
                                        }
                                        Text(
                                            text = scenario.title,
                                            style = MaterialTheme.typography.titleLarge
                                        )
                                        if (scenario.subtitle.isNotBlank()) {
                                            Text(
                                                text = scenario.subtitle,
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }
                                }

                                Row {
                                    IconButton(onClick = { onTogglePin(scenario) }) {
                                        Icon(
                                            imageVector = if (scenario.pinned) Icons.Filled.PushPin else Icons.Outlined.PushPin,
                                            contentDescription = "Закрепить",
                                            tint = if (scenario.pinned) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    IconButton(onClick = { onDeleteScenario(scenario) }) {
                                        Icon(
                                            imageVector = Icons.Outlined.DeleteOutline,
                                            contentDescription = "Удалить сценарий",
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }

                            Text(
                                text = scenario.systemPrompt,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 3,
                                overflow = TextOverflow.Ellipsis
                            )

                            if (scenario.tags.isNotEmpty() || loreCount > 0) {
                                FlowRow(
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    verticalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    scenario.tags.forEach { tag ->
                                        Surface(
                                            shape = RoundedCornerShape(50),
                                            color = MaterialTheme.colorScheme.surfaceContainerHighest
                                        ) {
                                            Text(
                                                text = "#$tag",
                                                style = MaterialTheme.typography.labelSmall,
                                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                            )
                                        }
                                    }
                                    if (loreCount > 0) {
                                        Surface(
                                            shape = RoundedCornerShape(50),
                                            color = MaterialTheme.colorScheme.tertiaryContainer
                                        ) {
                                            Text(
                                                text = "Лор: $loreCount",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onTertiaryContainer,
                                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                            )
                                        }
                                    }
                                }
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "T=${String.format(Locale.US, "%.2f", scenario.temperature)} • Top-P=${String.format(Locale.US, "%.2f", scenario.topP)}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    OutlinedButton(
                                        onClick = { onEditScenario(scenario) },
                                        modifier = Modifier.testTag("edit_scenario_${scenario.id}")
                                    ) {
                                        Icon(Icons.Outlined.Edit, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("Промпт")
                                    }
                                    Button(
                                        onClick = { onLaunchScenario(scenario) },
                                        modifier = Modifier.testTag("launch_scenario_${scenario.id}")
                                    ) {
                                        Icon(Icons.Outlined.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("Играть")
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        item { Spacer(modifier = Modifier.height(72.dp)) }
    }
}
