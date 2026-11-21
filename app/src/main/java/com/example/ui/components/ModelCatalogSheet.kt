package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.NetworkCheck
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.data.model.LlmProvider
import com.example.data.model.ModelCatalogItem
import com.example.data.model.ModelProbeStatus
import com.example.data.model.ModelTag
import com.example.ui.theme.MonoFontFamily

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ModelCatalogSheet(
    models: List<ModelCatalogItem>,
    currentProvider: LlmProvider,
    currentModelId: String,
    isDiscovering: Boolean,
    discoveryProgress: String,
    onDiscoverFreeModels: () -> Unit,
    onProbeModel: (ModelCatalogItem) -> Unit,
    onSelectModel: (LlmProvider, String) -> Unit,
    onDismiss: () -> Unit
) {
    var searchQuery by remember { mutableStateOf("") }
    var selectedTag by remember { mutableStateOf<ModelTag?>(null) }

    val filteredModels = remember(models, searchQuery, selectedTag) {
        val q = searchQuery.trim().lowercase()
        models.filter { item ->
            val matchesTag = selectedTag == null || item.tags.contains(selectedTag)
            val matchesQuery = q.isEmpty() ||
                item.displayName.lowercase().contains(q) ||
                item.modelId.lowercase().contains(q) ||
                item.provider.displayName.lowercase().contains(q) ||
                item.descriptionRu.lowercase().contains(q) ||
                item.tags.any { it.labelRu.lowercase().contains(q) }
            matchesTag && matchesQuery
        }.sortedWith(
            compareBy<ModelCatalogItem>(
                { statusRank(it.status) },
                { it.latencyMs ?: Long.MAX_VALUE },
                { if (it.provider.requiresApiKey) 1 else 0 },
                { it.displayName }
            )
        )
    }

    val onlineCount = remember(models) { models.count { it.status == ModelProbeStatus.ONLINE } }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background
        ) {
            Scaffold(
                contentWindowInsets = WindowInsets.safeDrawing,
                topBar = {
                    TopAppBar(
                        title = {
                            Column {
                                Text(
                                    text = "Каталог и автопоиск моделей",
                                    style = MaterialTheme.typography.titleLarge,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = "Найдено: ${models.size} • Онлайн: $onlineCount",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        },
                        actions = {
                            IconButton(
                                onClick = onDismiss,
                                modifier = Modifier
                                    .minimumInteractiveComponentSize()
                                    .testTag("catalog_close_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.Close,
                                    contentDescription = "Закрыть каталог моделей"
                                )
                            }
                        }
                    )
                }
            ) { innerPadding ->
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Column(
                        modifier = Modifier
                            .widthIn(max = 820.dp)
                            .fillMaxSize()
                            .padding(horizontal = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        OutlinedTextField(
                            value = searchQuery,
                            onValueChange = { searchQuery = it },
                            placeholder = { Text("Поиск: mistral, dolphin, gemma, без ключа...") },
                            leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) },
                            trailingIcon = {
                                if (searchQuery.isNotEmpty()) {
                                    IconButton(onClick = { searchQuery = "" }) {
                                        Icon(Icons.Outlined.Close, contentDescription = "Очистить поиск")
                                    }
                                }
                            },
                            singleLine = true,
                            shape = RoundedCornerShape(16.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("catalog_search_input")
                        )

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            FilterChip(
                                selected = selectedTag == null,
                                onClick = { selectedTag = null },
                                label = { Text("Все") },
                                modifier = Modifier.testTag("catalog_filter_all")
                            )
                            ModelTag.entries.forEach { tag ->
                                FilterChip(
                                    selected = selectedTag == tag,
                                    onClick = {
                                        selectedTag = if (selectedTag == tag) null else tag
                                    },
                                    label = { Text(tag.labelRu) },
                                    modifier = Modifier.testTag("catalog_filter_${tag.name}")
                                )
                            }
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Button(
                                onClick = onDiscoverFreeModels,
                                enabled = !isDiscovering,
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("catalog_auto_discover_button")
                            ) {
                                if (isDiscovering) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(18.dp),
                                        color = MaterialTheme.colorScheme.onPrimary,
                                        strokeWidth = 2.dp
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(discoveryProgress.ifBlank { "Автопоиск и проверка..." })
                                } else {
                                    Icon(Icons.Outlined.AutoAwesome, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("Автопоиск и проверка бесплатных")
                                }
                            }
                        }

                        if (discoveryProgress.isNotBlank() && !isDiscovering) {
                            Text(
                                text = discoveryProgress,
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }

                        LazyColumn(
                            contentPadding = PaddingValues(bottom = 24.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            if (filteredModels.isEmpty()) {
                                item {
                                    Surface(
                                        shape = MaterialTheme.shapes.large,
                                        color = MaterialTheme.colorScheme.surfaceContainerLow,
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Text(
                                            text = "По вашему запросу моделей не найдено. Попробуйте сбросить фильтры или запустить автопоиск.",
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.padding(16.dp)
                                        )
                                    }
                                }
                            } else {
                                items(
                                    items = filteredModels,
                                    key = { it.stableKey }
                                ) { item ->
                                    val isSelected = item.provider == currentProvider &&
                                        item.modelId.equals(currentModelId, ignoreCase = true)
                                    ModelCatalogCard(
                                        item = item,
                                        isSelected = isSelected,
                                        onProbe = { onProbeModel(item) },
                                        onSelect = {
                                            onSelectModel(item.provider, item.modelId)
                                            onDismiss()
                                        }
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

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ModelCatalogCard(
    item: ModelCatalogItem,
    isSelected: Boolean,
    onProbe: () -> Unit,
    onSelect: () -> Unit
) {
    val border = if (isSelected) {
        BorderStroke(2.dp, MaterialTheme.colorScheme.primary)
    } else {
        BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    }

    val statusBg = when (item.status) {
        ModelProbeStatus.ONLINE -> MaterialTheme.colorScheme.tertiaryContainer
        ModelProbeStatus.PROBING -> MaterialTheme.colorScheme.primaryContainer
        ModelProbeStatus.RATE_LIMITED -> MaterialTheme.colorScheme.secondaryContainer
        ModelProbeStatus.AUTH_ERROR,
        ModelProbeStatus.OFFLINE -> MaterialTheme.colorScheme.errorContainer
        ModelProbeStatus.NEEDS_KEY,
        ModelProbeStatus.UNKNOWN -> MaterialTheme.colorScheme.surfaceContainerHighest
    }
    val statusFg = when (item.status) {
        ModelProbeStatus.ONLINE -> MaterialTheme.colorScheme.onTertiaryContainer
        ModelProbeStatus.PROBING -> MaterialTheme.colorScheme.onPrimaryContainer
        ModelProbeStatus.RATE_LIMITED -> MaterialTheme.colorScheme.onSecondaryContainer
        ModelProbeStatus.AUTH_ERROR,
        ModelProbeStatus.OFFLINE -> MaterialTheme.colorScheme.onErrorContainer
        ModelProbeStatus.NEEDS_KEY,
        ModelProbeStatus.UNKNOWN -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    val statusLabel = when (item.status) {
        ModelProbeStatus.ONLINE -> "✓ ${item.latencyMs ?: 0} мс"
        else -> item.status.labelRu
    }

    Surface(
        shape = MaterialTheme.shapes.large,
        color = if (isSelected) {
            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.25f)
        } else {
            MaterialTheme.colorScheme.surfaceContainer
        },
        border = border,
        tonalElevation = if (isSelected) 3.dp else 1.dp,
        modifier = Modifier
            .fillMaxWidth()
            .testTag("catalog_item_${item.provider.name}_${item.modelId}")
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = item.displayName,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "${item.provider.displayName} • ${item.modelId}",
                        style = MaterialTheme.typography.labelSmall.copy(fontFamily = MonoFontFamily),
                        color = MaterialTheme.colorScheme.primary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = statusBg
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        if (item.status == ModelProbeStatus.PROBING) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(12.dp),
                                strokeWidth = 2.dp
                            )
                        }
                        Text(
                            text = statusLabel,
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                            color = statusFg
                        )
                    }
                }
            }

            if (item.descriptionRu.isNotBlank()) {
                Text(
                    text = item.descriptionRu,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            if (item.statusNote.isNotBlank()) {
                Text(
                    text = item.statusNote,
                    style = MaterialTheme.typography.labelSmall,
                    color = if (item.status == ModelProbeStatus.ONLINE) {
                        MaterialTheme.colorScheme.tertiary
                    } else {
                        MaterialTheme.colorScheme.error
                    },
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }

            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                item.tags.forEach { tag ->
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = if (tag == ModelTag.UNCENSORED_FRIENDLY) {
                            MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.7f)
                        } else {
                            MaterialTheme.colorScheme.surfaceContainerHighest
                        }
                    ) {
                        Text(
                            text = tag.labelRu,
                            style = MaterialTheme.typography.labelSmall,
                            color = if (tag == ModelTag.UNCENSORED_FRIENDLY) {
                                MaterialTheme.colorScheme.onTertiaryContainer
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                            modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp)
                        )
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = onProbe,
                    enabled = item.status != ModelProbeStatus.PROBING,
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                ) {
                    Icon(Icons.Outlined.NetworkCheck, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Пинг")
                }
                Button(
                    onClick = onSelect,
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                ) {
                    if (isSelected) {
                        Icon(Icons.Outlined.Check, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Активна")
                    } else {
                        Text("Выбрать")
                    }
                }
            }
        }
    }
}

private fun statusRank(status: ModelProbeStatus): Int = when (status) {
    ModelProbeStatus.ONLINE -> 0
    ModelProbeStatus.PROBING -> 1
    ModelProbeStatus.UNKNOWN -> 2
    ModelProbeStatus.RATE_LIMITED -> 3
    ModelProbeStatus.NEEDS_KEY -> 4
    ModelProbeStatus.AUTH_ERROR -> 5
    ModelProbeStatus.OFFLINE -> 6
}
