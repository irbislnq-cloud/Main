package com.example.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.R
import com.example.data.local.AppLocalSettings
import com.example.data.model.RpChat
import com.example.data.model.RpMode
import com.example.ui.viewmodel.UiState
import java.util.Locale

@Composable
fun ChatsTab(
    chatsState: UiState<List<RpChat>>,
    settings: AppLocalSettings,
    onOpenChat: (String) -> Unit,
    onQuickCreateChat: () -> Unit,
    onGoToScenarios: () -> Unit,
    onTogglePin: (RpChat) -> Unit,
    onDeleteChat: (RpChat) -> Unit
) {
    var query by remember { mutableStateOf("") }
    var filterMode by remember { mutableStateOf<RpMode?>(null) }

    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxSize()
    ) {
        item {
            // Hero Studio Card
            Surface(
                shape = MaterialTheme.shapes.extraLarge,
                tonalElevation = 4.dp,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(190.dp)
            ) {
                Box(modifier = Modifier.fillMaxSize()) {
                    Image(
                        painter = painterResource(id = R.drawable.img_studio_hero),
                        contentDescription = "Баннер ролевой студии LoreCraft",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                Brush.horizontalGradient(
                                    colors = listOf(
                                        MaterialTheme.colorScheme.surface.copy(alpha = 0.94f),
                                        MaterialTheme.colorScheme.surface.copy(alpha = 0.65f),
                                        Color.Transparent
                                    )
                                )
                            )
                    )
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(18.dp),
                        verticalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.9f)
                            ) {
                                Text(
                                    text = "${settings.defaultProvider.displayName} • ${settings.defaultModelId}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                )
                            }
                            Text(
                                text = "Мастерская RP",
                                style = MaterialTheme.typography.headlineMedium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "T=${String.format(Locale.US, "%.2f", settings.defaultParams.temperature)} • Top-P=${String.format(Locale.US, "%.2f", settings.defaultParams.topP)} • Окно ${settings.defaultParams.contextWindowMessages}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Button(
                                onClick = onQuickCreateChat,
                                modifier = Modifier.testTag("hero_quick_chat_button")
                            ) {
                                Icon(Icons.Outlined.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Быстрый RP")
                            }
                            OutlinedButton(
                                onClick = onGoToScenarios,
                                modifier = Modifier.testTag("hero_browse_scenarios_button")
                            ) {
                                Icon(Icons.Outlined.AutoAwesome, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Миры")
                            }
                        }
                    }
                }
            }
        }

        item {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                placeholder = { Text("Поиск по чатам и персонажам...") },
                leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) },
                singleLine = true,
                shape = MaterialTheme.shapes.large,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("chats_search_input")
            )
        }

        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilterChip(
                    selected = filterMode == null,
                    onClick = { filterMode = null },
                    label = { Text("Все") }
                )
                RpMode.entries.forEach { mode ->
                    FilterChip(
                        selected = filterMode == mode,
                        onClick = { filterMode = if (filterMode == mode) null else mode },
                        label = { Text(mode.shortRu) }
                    )
                }
            }
        }

        when (chatsState) {
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
                            text = chatsState.message,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            modifier = Modifier.padding(16.dp)
                        )
                    }
                }
            }
            is UiState.Success -> {
                val filtered = chatsState.data
                    .filter { chat ->
                        (filterMode == null || chat.rpModeEnum() == filterMode) &&
                            (query.isBlank() ||
                                chat.title.contains(query, ignoreCase = true) ||
                                chat.characterName.contains(query, ignoreCase = true) ||
                                chat.lastMessagePreview.contains(query, ignoreCase = true))
                    }
                    .sortedByDescending { it.pinned }

                if (filtered.isEmpty()) {
                    item {
                        Surface(
                            shape = MaterialTheme.shapes.large,
                            color = MaterialTheme.colorScheme.surfaceContainerLow,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier.padding(24.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.ChatBubbleOutline,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(36.dp)
                                )
                                Text(
                                    text = "Пока нет активных RP-сессий",
                                    style = MaterialTheme.typography.titleMedium
                                )
                                Text(
                                    text = "Выберите готовый мир во вкладке «Сценарии» или запустите свободную сцену с собственным системным промптом.",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Button(onClick = onGoToScenarios) {
                                    Text("Открыть сценарии")
                                }
                            }
                        }
                    }
                } else {
                    items(filtered, key = { it.id }) { chat ->
                        ChatSessionCard(
                            chat = chat,
                            onClick = { onOpenChat(chat.id) },
                            onTogglePin = { onTogglePin(chat) },
                            onDelete = { onDeleteChat(chat) }
                        )
                    }
                }
            }
        }

        item { Spacer(modifier = Modifier.height(72.dp)) }
    }
}

@Composable
private fun ChatSessionCard(
    chat: RpChat,
    onClick: () -> Unit,
    onTogglePin: () -> Unit,
    onDelete: () -> Unit
) {
    Surface(
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainer,
        tonalElevation = if (chat.pinned) 3.dp else 1.dp,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .testTag("chat_card_${chat.id}")
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    if (chat.portraitUri.isNotBlank()) {
                        AsyncImage(
                            model = chat.portraitUri,
                            contentDescription = "Портрет ${chat.title}",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .size(38.dp)
                                .clip(CircleShape)
                        )
                    } else {
                        Image(
                            painter = painterResource(id = R.drawable.img_world_emblem),
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .size(38.dp)
                                .clip(CircleShape)
                        )
                    }
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(3.dp)
                    ) {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.primaryContainer
                        ) {
                            Text(
                                text = chat.rpModeEnum().shortRu,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                            )
                        }
                        Text(
                            text = chat.title,
                            style = MaterialTheme.typography.titleMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onTogglePin) {
                        Icon(
                            imageVector = if (chat.pinned) Icons.Filled.PushPin else Icons.Outlined.PushPin,
                            contentDescription = "Закрепить чат",
                            tint = if (chat.pinned) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    IconButton(onClick = onDelete) {
                        Icon(
                            imageVector = Icons.Outlined.DeleteOutline,
                            contentDescription = "Удалить чат",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            if (chat.lastMessagePreview.isNotBlank()) {
                Text(
                    text = chat.lastMessagePreview,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "${chat.modelId} • T=${String.format(Locale.US, "%.2f", chat.temperature)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = "Сообщений: ${chat.messageCount}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
