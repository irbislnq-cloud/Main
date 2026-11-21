package com.example.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Casino
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.ForkRight
import androidx.compose.material.icons.outlined.Forum
import androidx.compose.material.icons.outlined.Groups
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.AutoStories
import androidx.compose.material.icons.outlined.MenuBook
import androidx.compose.material.icons.outlined.Psychology
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.AssistChip
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import com.example.data.ai.RpgAndThinkParser
import com.example.data.ai.RpgUpdateDelta
import com.example.data.ai.SemanticMarkupEngine
import com.example.data.ai.SemanticSegment
import com.example.data.model.MessageKind
import com.example.data.model.RpMessage
import com.example.data.model.SemanticEntityType

@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun RpMessageBubble(
    message: RpMessage,
    showModelBadge: Boolean,
    isLastAssistant: Boolean,
    isGenerating: Boolean,
    isSwipingThisMessage: Boolean = false,
    streamingOverrideText: String? = null,
    isDarkTheme: Boolean = isSystemInDarkTheme(),
    stageLabel: String = "",
    activeAgents: List<String> = emptyList(),
    isDeltaApplied: Boolean = false,
    isDeepening: Boolean = false,
    isGeneratingArt: Boolean = false,
    bookMode: Boolean = false,
    readerFontFamily: FontFamily? = null,
    readerFontSizeSp: Float = 16f,
    readerLineHeightMultiplier: Float = 1.48f,
    hapticsEnabled: Boolean = true,
    onSemanticSpanClick: ((SemanticSegment.SemanticSpan) -> Unit)? = null,
    onDeepenMessage: () -> Unit = {},
    onGenerateSceneArt: () -> Unit = {},
    onQuickRegisterSelection: (SemanticEntityType, String) -> Unit = { _, _ -> },
    onEdit: () -> Unit,
    onRegenerate: () -> Unit,
    onBranch: () -> Unit,
    onCopy: () -> Unit,
    onDeleteFromHere: () -> Unit,
    onSelectVariant: (Int) -> Unit = {},
    onGenerateNewVariant: () -> Unit = {},
    onApplyRpgDelta: ((RpgUpdateDelta) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val isUser = message.role == "user"
    val kind = message.kindEnum()

    var showTraceDetails by remember(message.id) { mutableStateOf(false) }
    var showLoreDetails by remember(message.id) { mutableStateOf(false) }
    var showLightbox by remember { mutableStateOf(false) }
    var showContextSheet by remember { mutableStateOf(false) }

    val useEditorialBook = bookMode && !isUser && kind != MessageKind.DICE && kind != MessageKind.OOC

    val containerColor = when {
        useEditorialBook -> Color.Transparent
        kind == MessageKind.DICE -> MaterialTheme.colorScheme.secondaryContainer
        kind == MessageKind.OOC -> MaterialTheme.colorScheme.surfaceContainerHighest
        isUser -> MaterialTheme.colorScheme.primaryContainer
        else -> MaterialTheme.colorScheme.surfaceContainer
    }
    val contentColor = when {
        kind == MessageKind.DICE -> MaterialTheme.colorScheme.onSecondaryContainer
        kind == MessageKind.OOC -> MaterialTheme.colorScheme.onSurfaceVariant
        isUser -> MaterialTheme.colorScheme.onPrimaryContainer
        else -> MaterialTheme.colorScheme.onSurface
    }
    val borderColor = when {
        kind == MessageKind.DICE -> MaterialTheme.colorScheme.secondary.copy(alpha = 0.55f)
        kind == MessageKind.OOC -> MaterialTheme.colorScheme.outline
        isUser -> MaterialTheme.colorScheme.primary.copy(alpha = 0.25f)
        else -> MaterialTheme.colorScheme.outlineVariant
    }

    val variants = message.effectiveVariants()
    val activeIdx = message.effectiveActiveIndex()
    val displayText = if (isSwipingThisMessage && !streamingOverrideText.isNullOrBlank()) {
        streamingOverrideText
    } else {
        message.activeContent()
    }
    val activeModel = message.activeModel()
    val showSwipeBar = !isUser && !isGenerating && message.id.isNotBlank() &&
        (variants.size > 1 || isLastAssistant)

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(
                horizontal = if (useEditorialBook) 16.dp else 12.dp,
                vertical = if (useEditorialBook) 8.dp else 4.dp
            ),
        horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start
    ) {
        Surface(
            shape = if (useEditorialBook) {
                RoundedCornerShape(8.dp)
            } else {
                RoundedCornerShape(
                    topStart = if (isUser) 18.dp else 6.dp,
                    topEnd = if (isUser) 6.dp else 18.dp,
                    bottomStart = 18.dp,
                    bottomEnd = 18.dp
                )
            },
            color = containerColor,
            border = if (useEditorialBook) null else BorderStroke(1.dp, borderColor),
            tonalElevation = if (useEditorialBook) 0.dp else if (isUser) 2.dp else 1.dp,
            modifier = Modifier
                .widthIn(max = if (useEditorialBook) 640.dp else 560.dp)
                .fillMaxWidth(
                    when {
                        useEditorialBook -> 1f
                        kind == MessageKind.DICE || kind == MessageKind.NARRATOR -> 0.96f
                        else -> 0.92f
                    }
                )
                .combinedClickable(
                    onClick = {},
                    onLongClick = {
                        if (!isGenerating && message.id.isNotBlank()) {
                            showContextSheet = true
                        }
                    }
                )
                .testTag("message_bubble_${message.id}")
        ) {
            Column(
                modifier = Modifier.padding(
                    horizontal = if (useEditorialBook) 4.dp else 14.dp,
                    vertical = if (useEditorialBook) 6.dp else 10.dp
                )
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
                        if (kind == MessageKind.DICE) {
                            Icon(
                                imageVector = Icons.Outlined.Casino,
                                contentDescription = "Кубик",
                                tint = MaterialTheme.colorScheme.secondary,
                                modifier = Modifier.size(16.dp)
                            )
                        } else if (kind == MessageKind.OOC) {
                            Icon(
                                imageVector = Icons.Outlined.Forum,
                                contentDescription = "Вне роли",
                                tint = MaterialTheme.colorScheme.tertiary,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                        Text(
                            text = message.speakerName.ifBlank {
                                if (isUser) "Вы" else "Персонаж"
                            },
                            style = MaterialTheme.typography.labelLarge,
                            color = if (kind == MessageKind.DICE) {
                                MaterialTheme.colorScheme.secondary
                            } else {
                                MaterialTheme.colorScheme.primary
                            }
                        )
                        if (kind != MessageKind.ROLEPLAY) {
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.55f)
                            ) {
                                Text(
                                    text = kind.labelRu,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }
                    }

                    if (showModelBadge && !isUser && activeModel.isNotBlank()) {
                        Text(
                            text = activeModel.substringAfter(": ").take(24),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                // Live multi-agent stage badge while generating
                if ((isGenerating || isSwipingThisMessage) && stageLabel.isNotBlank()) {
                    Spacer(modifier = Modifier.height(6.dp))
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)) {
                            Text(
                                text = stageLabel,
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                                color = MaterialTheme.colorScheme.primary
                            )
                            if (activeAgents.isNotEmpty()) {
                                Text(
                                    text = activeAgents.joinToString(" • "),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }

                // Inline generated Scene Art
                if (message.sceneArtUri.isNotBlank()) {
                    Spacer(modifier = Modifier.height(8.dp))
                    AsyncImage(
                        model = message.sceneArtUri,
                        contentDescription = "Иллюстрация сцены",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(176.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .clickable { showLightbox = true }
                            .testTag("scene_art_image_${message.id}")
                    )
                }

                Spacer(modifier = Modifier.height(6.dp))

                if (isGenerating || isSwipingThisMessage) {
                    RpMarkdownContent(
                        rawText = displayText,
                        baseColor = contentColor,
                        isStreaming = true,
                        isDarkTheme = isDarkTheme,
                        isDeltaApplied = isDeltaApplied,
                        readerFontFamily = readerFontFamily,
                        readerFontSizeSp = readerFontSizeSp,
                        readerLineHeightMultiplier = readerLineHeightMultiplier,
                        hapticsEnabled = hapticsEnabled,
                        onSemanticSpanClick = onSemanticSpanClick,
                        onApplyRpgDelta = onApplyRpgDelta
                    )
                } else {
                    AnimatedContent(
                        targetState = activeIdx,
                        label = "swipe_variant_transition"
                    ) { _ ->
                        RpMarkdownContent(
                            rawText = displayText,
                            baseColor = contentColor,
                            isStreaming = false,
                            isDarkTheme = isDarkTheme,
                            isDeltaApplied = isDeltaApplied,
                            readerFontFamily = readerFontFamily,
                            readerFontSizeSp = readerFontSizeSp,
                            readerLineHeightMultiplier = readerLineHeightMultiplier,
                            hapticsEnabled = hapticsEnabled,
                            onSemanticSpanClick = onSemanticSpanClick,
                            onApplyRpgDelta = onApplyRpgDelta
                        )
                    }
                }

                // Collapsible "Совет Агентов" Spoiler + Clickable "Активный лор (N)" Badge
                if (!isUser && !isGenerating && message.agentTrace.isNotEmpty()) {
                    val loreRaw = remember(message.agentTrace) {
                        message.agentTrace.filter { it.startsWith("[Лор]") }
                    }
                    val triggeredLoreTitles = remember(loreRaw) {
                        loreRaw.flatMap { entry ->
                            entry.removePrefix("[Лор]").split(",").map { it.trim() }
                        }.filter { it.isNotBlank() }.distinct()
                    }
                    val councilSteps = remember(message.agentTrace) {
                        message.agentTrace.filterNot { it.startsWith("[Лор]") }
                    }

                    Spacer(modifier = Modifier.height(6.dp))
                    if (triggeredLoreTitles.isNotEmpty()) {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.45f),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.secondary.copy(alpha = 0.35f)),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { showLoreDetails = !showLoreDetails }
                                .testTag("active_lore_badge_${message.id}")
                        ) {
                            Column(modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)) {
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
                                            Icons.Outlined.MenuBook,
                                            contentDescription = "Активный лор",
                                            tint = MaterialTheme.colorScheme.secondary,
                                            modifier = Modifier.size(14.dp)
                                        )
                                        Text(
                                            text = "Активный лор (${triggeredLoreTitles.size})",
                                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                                            color = MaterialTheme.colorScheme.secondary
                                        )
                                    }
                                    Icon(
                                        imageVector = if (showLoreDetails) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore,
                                        contentDescription = "Раскрыть активный лор",
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                                AnimatedVisibility(visible = showLoreDetails) {
                                    FlowRow(
                                        modifier = Modifier.padding(top = 6.dp),
                                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                                        verticalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        triggeredLoreTitles.forEach { title ->
                                            Surface(
                                                shape = RoundedCornerShape(6.dp),
                                                color = MaterialTheme.colorScheme.surface
                                            ) {
                                                Text(
                                                    text = "📜 $title",
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = MaterialTheme.colorScheme.onSurface,
                                                    modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp)
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                        if (councilSteps.isNotEmpty()) {
                            Spacer(modifier = Modifier.height(4.dp))
                        }
                    }

                    if (councilSteps.isNotEmpty()) {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.surfaceContainerLow,
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { showTraceDetails = !showTraceDetails }
                                .testTag("agent_trace_pill_${message.id}")
                        ) {
                            Column(modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)) {
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
                                            Icons.Outlined.Psychology,
                                            contentDescription = "Совет Агентов",
                                            tint = MaterialTheme.colorScheme.tertiary,
                                            modifier = Modifier.size(14.dp)
                                        )
                                        Text(
                                            text = "Спойлер: Совет Агентов (${councilSteps.size})",
                                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                                            color = MaterialTheme.colorScheme.tertiary
                                        )
                                    }
                                    Icon(
                                        imageVector = if (showTraceDetails) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore,
                                        contentDescription = if (showTraceDetails) "Свернуть совет" else "Показать совет",
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                                AnimatedVisibility(visible = showTraceDetails) {
                                    Column(
                                        modifier = Modifier.padding(top = 6.dp),
                                        verticalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        councilSteps.forEach { step ->
                                            val isDirective = step.startsWith("[Совет]")
                                            Text(
                                                text = if (isDirective) step else "• $step",
                                                style = MaterialTheme.typography.labelSmall.copy(
                                                    fontStyle = if (isDirective) FontStyle.Italic else FontStyle.Normal
                                                ),
                                                color = if (isDirective) {
                                                    MaterialTheme.colorScheme.onSurface
                                                } else {
                                                    MaterialTheme.colorScheme.onSurfaceVariant
                                                }
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                if (showSwipeBar) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerLow,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 6.dp, vertical = 2.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                IconButton(
                                    onClick = { onSelectVariant(activeIdx - 1) },
                                    enabled = activeIdx > 0,
                                    modifier = Modifier
                                        .minimumInteractiveComponentSize()
                                        .testTag("swipe_prev_${message.id}")
                                ) {
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Outlined.KeyboardArrowLeft,
                                        contentDescription = "Предыдущий вариант"
                                    )
                                }
                                Text(
                                    text = "${activeIdx + 1} / ${variants.size}",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                IconButton(
                                    onClick = {
                                        if (activeIdx < variants.lastIndex) {
                                            onSelectVariant(activeIdx + 1)
                                        } else {
                                            onGenerateNewVariant()
                                        }
                                    },
                                    enabled = variants.size < 12,
                                    modifier = Modifier
                                        .minimumInteractiveComponentSize()
                                        .testTag("swipe_next_${message.id}")
                                ) {
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Outlined.KeyboardArrowRight,
                                        contentDescription = if (activeIdx < variants.lastIndex) {
                                            "Следующий вариант"
                                        } else {
                                            "Сгенерировать новый вариант"
                                        }
                                    )
                                }
                            }

                            TextButton(
                                onClick = onGenerateNewVariant,
                                enabled = variants.size < 12,
                                modifier = Modifier
                                    .minimumInteractiveComponentSize()
                                    .testTag("swipe_add_${message.id}")
                            ) {
                                Icon(Icons.Outlined.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Вариант")
                            }
                        }
                    }
                }

                if (!isGenerating && !isSwipingThisMessage && message.id.isNotBlank()) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.End,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (!isUser && kind == MessageKind.ROLEPLAY) {
                            IconButton(
                                onClick = onDeepenMessage,
                                enabled = !isDeepening,
                                modifier = Modifier
                                    .minimumInteractiveComponentSize()
                                    .testTag("msg_deepen_button_${message.id}")
                            ) {
                                if (isDeepening) {
                                    CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                                } else {
                                    Icon(
                                        imageVector = Icons.Outlined.AutoAwesome,
                                        contentDescription = "Детализировать (Агент)",
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                            IconButton(
                                onClick = onGenerateSceneArt,
                                enabled = !isGeneratingArt,
                                modifier = Modifier
                                    .minimumInteractiveComponentSize()
                                    .testTag("msg_art_button_${message.id}")
                            ) {
                                if (isGeneratingArt) {
                                    CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                                } else {
                                    Icon(
                                        imageVector = Icons.Outlined.Image,
                                        contentDescription = "Сгенерировать арт сцены",
                                        tint = MaterialTheme.colorScheme.secondary,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                        }
                        if (isLastAssistant) {
                            IconButton(
                                onClick = onRegenerate,
                                modifier = Modifier
                                    .minimumInteractiveComponentSize()
                                    .testTag("msg_regenerate_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.Refresh,
                                    contentDescription = "Новый свайп-вариант",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                        IconButton(
                            onClick = onEdit,
                            modifier = Modifier
                                .minimumInteractiveComponentSize()
                                .testTag("msg_edit_button_${message.id}")
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Edit,
                                contentDescription = "Редактировать",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                        IconButton(
                            onClick = onBranch,
                            modifier = Modifier
                                .minimumInteractiveComponentSize()
                                .testTag("msg_branch_button_${message.id}")
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.ForkRight,
                                contentDescription = "Создать ветку сюжета",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                        IconButton(
                            onClick = onCopy,
                            modifier = Modifier
                                .minimumInteractiveComponentSize()
                                .testTag("msg_copy_button_${message.id}")
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.ContentCopy,
                                contentDescription = "Копировать",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                        IconButton(
                            onClick = onDeleteFromHere,
                            modifier = Modifier
                                .minimumInteractiveComponentSize()
                                .testTag("msg_delete_button_${message.id}")
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.DeleteOutline,
                                contentDescription = "Удалить",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
                if (useEditorialBook && !isGenerating) {
                    Spacer(modifier = Modifier.height(10.dp))
                    HorizontalDivider(
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f),
                        modifier = Modifier.padding(horizontal = 24.dp)
                    )
                }
            }
        }
    }

    // Lightbox for full-screen scene illustration preview
    if (showLightbox && message.sceneArtUri.isNotBlank()) {
        Dialog(
            onDismissRequest = { showLightbox = false },
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            Surface(
                color = MaterialTheme.colorScheme.background,
                modifier = Modifier.fillMaxSize()
            ) {
                Box(modifier = Modifier.fillMaxSize()) {
                    AsyncImage(
                        model = message.sceneArtUri,
                        contentDescription = "Иллюстрация сцены крупно",
                        contentScale = ContentScale.Fit,
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(16.dp)
                    )
                    IconButton(
                        onClick = { showLightbox = false },
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(16.dp)
                    ) {
                        Icon(Icons.Outlined.Close, contentDescription = "Закрыть иллюстрацию")
                    }
                }
            }
        }
    }

    // Long-press Context Sheet: extract/register new Location/NPC/Item from text or invoke agents
    if (showContextSheet) {
        val plainPreview = remember(displayText) {
            SemanticMarkupEngine.stripTagsToPlainText(
                RpgAndThinkParser.stripMachineTagsOnly(displayText)
            )
        }
        val candidateWords = remember(plainPreview) {
            Regex("""[А-ЯЁA-Z][а-яёa-zА-ЯЁA-Z\-]{2,}(?:\s+[а-яёa-zА-ЯЁA-Z\-]{2,}){0,2}""")
                .findAll(plainPreview)
                .map { it.value.trim() }
                .distinct()
                .take(10)
                .toList()
        }
        var selectedPhrase by remember { mutableStateOf(candidateWords.firstOrNull().orEmpty()) }
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

        ModalBottomSheet(
            onDismissRequest = { showContextSheet = false },
            sheetState = sheetState
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .padding(bottom = 24.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text("Действия с фрагментом сцены", style = MaterialTheme.typography.titleMedium)
                OutlinedTextField(
                    value = selectedPhrase,
                    onValueChange = { selectedPhrase = it },
                    label = { Text("Выделенное имя, место или предмет") },
                    placeholder = { Text("Например: Поле светлых") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                if (candidateWords.isNotEmpty()) {
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        candidateWords.forEach { w ->
                            AssistChip(
                                onClick = { selectedPhrase = w },
                                label = { Text(w) }
                            )
                        }
                    }
                }
                Text("Открыть / создать нативную карточку:", style = MaterialTheme.typography.labelMedium)
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    listOf(
                        SemanticEntityType.LOCATION,
                        SemanticEntityType.NPC,
                        SemanticEntityType.ITEM,
                        SemanticEntityType.QUEST
                    ).forEach { entityType ->
                        OutlinedButton(
                            onClick = {
                                val clean = selectedPhrase.trim()
                                if (clean.isNotEmpty()) {
                                    showContextSheet = false
                                    onQuickRegisterSelection(entityType, clean)
                                }
                            },
                            enabled = selectedPhrase.isNotBlank()
                        ) {
                            Text("${entityType.badgeIcon} ${entityType.labelRu}")
                        }
                    }
                }

                HorizontalDivider()

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (!isUser) {
                        OutlinedButton(
                            onClick = {
                                showContextSheet = false
                                onDeepenMessage()
                            },
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Outlined.AutoAwesome, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Детализировать")
                        }
                        OutlinedButton(
                            onClick = {
                                showContextSheet = false
                                onGenerateSceneArt()
                            },
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Outlined.Image, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Арт сцены")
                        }
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    TextButton(
                        onClick = {
                            showContextSheet = false
                            onCopy()
                        },
                        modifier = Modifier.weight(1f)
                    ) { Text("Копировать") }
                    TextButton(
                        onClick = {
                            showContextSheet = false
                            onBranch()
                        },
                        modifier = Modifier.weight(1f)
                    ) { Text("Ветка сюжета") }
                }
            }
        }
    }
}
