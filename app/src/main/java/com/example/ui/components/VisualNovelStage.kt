package com.example.ui.components

import androidx.compose.animation.Crossfade
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Explore
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.R
import com.example.data.ai.RpgUpdateDelta
import com.example.data.ai.SemanticSegment
import com.example.data.model.RpChat
import com.example.data.model.RpMessage

@Composable
fun VisualNovelStage(
    chat: RpChat?,
    messages: List<RpMessage>,
    streamingText: String,
    isGenerating: Boolean,
    autoAppliedDeltaIds: Set<String> = emptySet(),
    onSemanticSpanClick: (SemanticSegment.SemanticSpan) -> Unit,
    onApplyRpgDelta: (RpgUpdateDelta) -> Unit,
    onOpenNpcDossier: (npcId: String) -> Unit,
    onOpenLocationMap: () -> Unit,
    onGenerateArtForMessage: (RpMessage) -> Unit,
    modifier: Modifier = Modifier
) {
    val isDark = isSystemInDarkTheme()
    val totalCount = messages.size + if (isGenerating) 1 else 0
    var viewIndex by remember(chat?.id) { mutableIntStateOf((totalCount - 1).coerceAtLeast(0)) }

    LaunchedEffect(totalCount, isGenerating) {
        if (totalCount > 0) {
            viewIndex = totalCount - 1
        }
    }

    val currentLoc = chat?.rpgState?.currentLocation()
    val backdropModel = currentLoc?.backdropUri?.takeIf { it.isNotBlank() }
        ?: chat?.backdropUri?.takeIf { it.isNotBlank() }

    val displayedMsg = if (isGenerating && viewIndex >= messages.size) {
        RpMessage(
            id = "vn_streaming",
            role = "assistant",
            speakerName = chat?.characterName ?: "Персонаж",
            content = streamingText.ifBlank { "Пишет сцену..." }
        )
    } else {
        messages.getOrNull(viewIndex.coerceIn(0, (messages.size - 1).coerceAtLeast(0)))
    }

    val matchedNpc = remember(displayedMsg?.speakerName, chat?.rpgState?.npcs) {
        val speaker = displayedMsg?.speakerName.orEmpty().trim()
        if (speaker.isBlank()) null
        else chat?.rpgState?.npcs?.firstOrNull { it.name.equals(speaker, ignoreCase = true) }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .testTag("visual_novel_stage")
    ) {
        // Crossfading full-bleed backdrop
        Crossfade(
            targetState = backdropModel,
            label = "vn_backdrop_crossfade",
            modifier = Modifier.fillMaxSize()
        ) { uri ->
            if (!uri.isNullOrBlank()) {
                AsyncImage(
                    model = uri,
                    contentDescription = "Фон сцены визуальной новеллы",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                Image(
                    painter = painterResource(id = R.drawable.img_world_emblem),
                    contentDescription = "Фон мира",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            }
        }

        // Atmospheric readability gradient scrim
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            MaterialTheme.colorScheme.background.copy(alpha = 0.28f),
                            MaterialTheme.colorScheme.background.copy(alpha = 0.55f),
                            MaterialTheme.colorScheme.background.copy(alpha = 0.94f)
                        )
                    )
                )
        )

        // Top Stage HUD (Location + World time/weather)
        Row(
            modifier = Modifier
                .align(Alignment.TopStart)
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                shape = RoundedCornerShape(50),
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.85f),
                modifier = Modifier.clickable { onOpenLocationMap() }
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(Icons.Outlined.Explore, contentDescription = null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary)
                    Text(
                        text = currentLoc?.name ?: chat?.title ?: "Сцена",
                        style = MaterialTheme.typography.labelLarge
                    )
                }
            }

            if (chat?.rpgState?.enabled == true) {
                Surface(
                    shape = RoundedCornerShape(50),
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.85f)
                ) {
                    val env = chat.rpgState.worldEnv
                    Text(
                        text = "День ${env.dayCount} • ${env.timeOfDay} • ${env.weather}",
                        style = MaterialTheme.typography.labelMedium,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                    )
                }
            }
        }

        // Bottom Dialogue / Narration Plate
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (displayedMsg != null) {
                // Speaker pill + NPC dossier shortcut
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        shape = RoundedCornerShape(50),
                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.94f),
                        modifier = Modifier.clickable(enabled = matchedNpc != null) {
                            matchedNpc?.let { onOpenNpcDossier(it.id) }
                        }
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            val portraitUri = chat?.portraitUri.orEmpty()
                            if (displayedMsg.role == "assistant" && portraitUri.isNotBlank()) {
                                AsyncImage(
                                    model = portraitUri,
                                    contentDescription = displayedMsg.speakerName,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier
                                        .size(26.dp)
                                        .clip(CircleShape)
                                )
                            } else {
                                Icon(Icons.Outlined.Person, contentDescription = null, modifier = Modifier.size(18.dp))
                            }
                            Text(
                                text = displayedMsg.speakerName.ifBlank {
                                    if (displayedMsg.role == "user") "Вы" else "Рассказчик"
                                },
                                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                            if (matchedNpc != null) {
                                Text(
                                    text = "• Досье",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (!isGenerating && displayedMsg.id != "vn_streaming") {
                            IconButton(
                                onClick = { onGenerateArtForMessage(displayedMsg) },
                                modifier = Modifier.minimumInteractiveComponentSize()
                            ) {
                                Icon(
                                    Icons.Outlined.AutoAwesome,
                                    contentDescription = "Арт сцены",
                                    tint = MaterialTheme.colorScheme.secondary
                                )
                            }
                        }
                        IconButton(
                            onClick = { if (viewIndex > 0) viewIndex-- },
                            enabled = viewIndex > 0,
                            modifier = Modifier.minimumInteractiveComponentSize()
                        ) {
                            Icon(Icons.AutoMirrored.Outlined.KeyboardArrowLeft, contentDescription = "Предыдущая реплика")
                        }
                        Text(
                            text = if (totalCount > 0) "${viewIndex + 1}/$totalCount" else "0/0",
                            style = MaterialTheme.typography.labelMedium
                        )
                        IconButton(
                            onClick = { if (viewIndex < totalCount - 1) viewIndex++ },
                            enabled = viewIndex < totalCount - 1,
                            modifier = Modifier.minimumInteractiveComponentSize()
                        ) {
                            Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, contentDescription = "Следующая реплика")
                        }
                    }
                }

                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                    tonalElevation = 4.dp,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 120.dp, max = 300.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .padding(16.dp)
                            .verticalScroll(rememberScrollState())
                    ) {
                        RpMarkdownContent(
                            rawText = displayedMsg.activeContent(),
                            baseColor = MaterialTheme.colorScheme.onSurface,
                            isStreaming = isGenerating && viewIndex >= messages.size,
                            isDarkTheme = isDark,
                            isDeltaApplied = displayedMsg.id in autoAppliedDeltaIds,
                            onSemanticSpanClick = onSemanticSpanClick,
                            onApplyRpgDelta = onApplyRpgDelta
                        )
                    }
                }
            }
        }
    }
}
