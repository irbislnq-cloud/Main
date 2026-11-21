package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.Psychology
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withLink
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.ai.RpgAndThinkParser
import com.example.data.ai.RpgUpdateDelta
import com.example.data.ai.SemanticMarkupEngine
import com.example.data.ai.SemanticSegment
import com.example.data.model.SemanticEntityType
import com.example.device.HapticEvent
import com.example.device.HapticFeedbackEngine
import com.example.ui.theme.MonoFontFamily
import com.example.ui.theme.semanticEntityColor

private sealed interface MdBlock {
    data class Heading(val level: Int, val text: String) : MdBlock
    data class Quote(val text: String) : MdBlock
    data class CodeBlock(val lang: String, val code: String) : MdBlock
    data class BulletItem(val marker: String, val text: String) : MdBlock
    data object Divider : MdBlock
    data class Paragraph(val text: String) : MdBlock
}

@Composable
fun RpMarkdownContent(
    rawText: String,
    baseColor: Color,
    isStreaming: Boolean = false,
    isDarkTheme: Boolean = isSystemInDarkTheme(),
    isDeltaApplied: Boolean = false,
    readerFontFamily: FontFamily? = null,
    readerFontSizeSp: Float = 16f,
    readerLineHeightMultiplier: Float = 1.48f,
    hapticsEnabled: Boolean = true,
    onSemanticSpanClick: ((SemanticSegment.SemanticSpan) -> Unit)? = null,
    onApplyRpgDelta: ((RpgUpdateDelta) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val view = LocalView.current
    val parsed = remember(rawText) { RpgAndThinkParser.parseForDisplay(rawText) }
    val actionColor = MaterialTheme.colorScheme.onSurfaceVariant
    val boldColor = MaterialTheme.colorScheme.primary
    val codeBg = MaterialTheme.colorScheme.surfaceContainerHighest
    val codeFg = MaterialTheme.colorScheme.tertiary

    val resolvedFontSize = readerFontSizeSp.coerceIn(13f, 24f).sp
    val resolvedLineHeight = (readerFontSizeSp.coerceIn(13f, 24f) * readerLineHeightMultiplier.coerceIn(1.2f, 1.9f)).sp
    val bodyStyle = MaterialTheme.typography.bodyLarge.copy(
        fontFamily = readerFontFamily ?: MaterialTheme.typography.bodyLarge.fontFamily,
        fontSize = resolvedFontSize,
        lineHeight = resolvedLineHeight
    )
    val quoteStyle = MaterialTheme.typography.bodyMedium.copy(
        fontFamily = readerFontFamily ?: MaterialTheme.typography.bodyMedium.fontFamily,
        fontSize = (readerFontSizeSp.coerceIn(13f, 24f) - 1f).coerceAtLeast(12f).sp,
        lineHeight = resolvedLineHeight
    )

    val blocks = remember(parsed.visibleMarkdown) {
        parseMarkdownBlocks(parsed.visibleMarkdown)
    }
    val uniqueSpans = remember(parsed.visibleMarkdown, isStreaming) {
        if (isStreaming) emptyList()
        else SemanticMarkupEngine.extractUniqueSpans(parsed.visibleMarkdown)
            .filter { it.type != SemanticEntityType.THOUGHT }
            .take(8)
    }

    val containerModifier = if (isStreaming) modifier else modifier.animateContentSize()
    Column(
        modifier = containerModifier,
        verticalArrangement = Arrangement.spacedBy(7.dp)
    ) {
        if (!parsed.thinkingText.isNullOrBlank()) {
            ThoughtProcessCard(
                thinkingText = parsed.thinkingText,
                isThinkingOpen = parsed.isThinkingOpen && isStreaming
            )
        }

        blocks.forEach { block ->
            when (block) {
                is MdBlock.Heading -> {
                    val style = when (block.level) {
                        1 -> MaterialTheme.typography.titleLarge
                        2 -> MaterialTheme.typography.titleMedium
                        else -> MaterialTheme.typography.titleSmall
                    }
                    Text(
                        text = parseInlineMarkdown(
                            raw = block.text,
                            baseColor = boldColor,
                            actionColor = actionColor,
                            boldColor = boldColor,
                            codeBg = codeBg,
                            codeFg = codeFg,
                            isStreaming = isStreaming,
                            isDarkTheme = isDarkTheme,
                            onSemanticSpanClick = onSemanticSpanClick
                        ),
                        style = style
                    )
                }

                is MdBlock.Quote -> {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.55f),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(modifier = Modifier.padding(vertical = 6.dp, horizontal = 10.dp)) {
                            Box(
                                modifier = Modifier
                                    .width(3.dp)
                                    .height(22.dp)
                                    .background(MaterialTheme.colorScheme.secondary, RoundedCornerShape(2.dp))
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = parseInlineMarkdown(
                                    raw = block.text,
                                    baseColor = baseColor.copy(alpha = 0.92f),
                                    actionColor = actionColor,
                                    boldColor = boldColor,
                                    codeBg = codeBg,
                                    codeFg = codeFg,
                                    isStreaming = isStreaming,
                                    isDarkTheme = isDarkTheme,
                                    onSemanticSpanClick = onSemanticSpanClick
                                ),
                                style = quoteStyle,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }

                is MdBlock.CodeBlock -> {
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerHighest,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(10.dp)) {
                            if (block.lang.isNotBlank()) {
                                Text(
                                    text = block.lang.uppercase(),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.tertiary
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                            }
                            Text(
                                text = block.code,
                                style = MaterialTheme.typography.bodySmall.copy(fontFamily = MonoFontFamily),
                                color = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.horizontalScroll(rememberScrollState())
                            )
                        }
                    }
                }

                is MdBlock.BulletItem -> {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = block.marker,
                            style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.secondary
                        )
                        Text(
                            text = parseInlineMarkdown(
                                raw = block.text,
                                baseColor = baseColor,
                                actionColor = actionColor,
                                boldColor = boldColor,
                                codeBg = codeBg,
                                codeFg = codeFg,
                                isStreaming = isStreaming,
                                isDarkTheme = isDarkTheme,
                                onSemanticSpanClick = onSemanticSpanClick
                            ),
                            style = bodyStyle,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                MdBlock.Divider -> {
                    HorizontalDivider(
                        modifier = Modifier.padding(vertical = 4.dp),
                        color = MaterialTheme.colorScheme.outlineVariant
                    )
                }

                is MdBlock.Paragraph -> {
                    Text(
                        text = parseInlineMarkdown(
                            raw = block.text,
                            baseColor = baseColor,
                            actionColor = actionColor,
                            boldColor = boldColor,
                            codeBg = codeBg,
                            codeFg = codeFg,
                            isStreaming = isStreaming,
                            isDarkTheme = isDarkTheme,
                            onSemanticSpanClick = onSemanticSpanClick?.let { callback ->
                                { span ->
                                    HapticFeedbackEngine.trigger(view, hapticsEnabled, HapticEvent.TAG_INSPECT)
                                    callback(span)
                                }
                            }
                        ),
                        style = bodyStyle
                    )
                }
            }
        }

        if (parsed.visibleMarkdown.isBlank() && isStreaming && !parsed.thinkingText.isNullOrBlank()) {
            Text(
                text = "Формирую ответ после размышления...",
                style = MaterialTheme.typography.bodySmall.copy(fontStyle = FontStyle.Italic),
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        if (uniqueSpans.isNotEmpty() && onSemanticSpanClick != null) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(top = 2.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                uniqueSpans.forEach { span ->
                    val spanColor = semanticEntityColor(span.type, isDarkTheme)
                    Surface(
                        shape = RoundedCornerShape(50),
                        color = spanColor.copy(alpha = 0.14f),
                        border = BorderStroke(1.dp, spanColor.copy(alpha = 0.45f)),
                        modifier = Modifier
                            .clickable {
                                HapticFeedbackEngine.trigger(view, hapticsEnabled, HapticEvent.TAG_INSPECT)
                                onSemanticSpanClick(span)
                            }
                            .testTag("semantic_chip_${span.type.tagKey}_${span.entityId}")
                    ) {
                        Text(
                            text = "${span.type.badgeIcon} ${span.displayText}",
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                            color = spanColor,
                            modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp)
                        )
                    }
                }
            }
        }

        val rpgDelta = parsed.rpgDelta
        if (rpgDelta != null && !isStreaming && onApplyRpgDelta != null) {
            Spacer(modifier = Modifier.height(2.dp))
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = if (isDeltaApplied) {
                    MaterialTheme.colorScheme.surfaceContainerHigh
                } else {
                    MaterialTheme.colorScheme.tertiaryContainer
                },
                border = BorderStroke(
                    1.dp,
                    if (isDeltaApplied) MaterialTheme.colorScheme.outlineVariant
                    else MaterialTheme.colorScheme.tertiary.copy(alpha = 0.45f)
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onApplyRpgDelta(rpgDelta) }
                    .minimumInteractiveComponentSize()
                    .testTag("apply_rpg_delta_chip")
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = if (isDeltaApplied) {
                                "✓ Авто-применено к миру и листу"
                            } else {
                                "Обновление мира и RPG-листа"
                            },
                            style = MaterialTheme.typography.labelSmall,
                            color = if (isDeltaApplied) {
                                MaterialTheme.colorScheme.tertiary
                            } else {
                                MaterialTheme.colorScheme.onTertiaryContainer
                            }
                        )
                        Text(
                            text = rpgDelta.summaryText(),
                            style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold),
                            color = if (isDeltaApplied) {
                                MaterialTheme.colorScheme.onSurface
                            } else {
                                MaterialTheme.colorScheme.onTertiaryContainer
                            }
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (isDeltaApplied) "Повторить" else "Применить",
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }
    }
}

@Composable
private fun ThoughtProcessCard(
    thinkingText: String,
    isThinkingOpen: Boolean
) {
    var expanded by remember { mutableStateOf(false) }

    Surface(
        shape = RoundedCornerShape(10.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.65f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier
            .fillMaxWidth()
            .clickable { expanded = !expanded }
            .testTag("thought_process_card")
    ) {
        Column(modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Psychology,
                        contentDescription = "Ход мыслей",
                        tint = MaterialTheme.colorScheme.tertiary,
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        text = if (isThinkingOpen) "Модель размышляет..." else "Ход мыслей модели",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.tertiary
                    )
                }
                Icon(
                    imageVector = if (expanded) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore,
                    contentDescription = if (expanded) "Свернуть мысли" else "Развернуть мысли",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(18.dp)
                )
            }

            AnimatedVisibility(visible = expanded) {
                Text(
                    text = thinkingText,
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontFamily = MonoFontFamily,
                        fontStyle = FontStyle.Italic
                    ),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }
        }
    }
}

private fun parseMarkdownBlocks(raw: String): List<MdBlock> {
    if (raw.isBlank()) return emptyList()
    val lines = raw.lines()
    val blocks = mutableListOf<MdBlock>()
    val paraBuf = StringBuilder()

    fun flushPara() {
        val t = paraBuf.toString().trim()
        if (t.isNotEmpty()) blocks.add(MdBlock.Paragraph(t))
        paraBuf.clear()
    }

    var i = 0
    while (i < lines.size) {
        val line = lines[i]
        val trimmed = line.trim()

        if (trimmed.startsWith("```")) {
            flushPara()
            val lang = trimmed.removePrefix("```").trim()
            val codeBuf = StringBuilder()
            i++
            while (i < lines.size && !lines[i].trim().startsWith("```")) {
                if (codeBuf.isNotEmpty()) codeBuf.append('\n')
                codeBuf.append(lines[i])
                i++
            }
            blocks.add(MdBlock.CodeBlock(lang, codeBuf.toString()))
            i++
            continue
        }

        if (trimmed.isEmpty()) {
            flushPara()
            i++
            continue
        }

        if (trimmed == "---" || trimmed == "***" || trimmed == "___") {
            flushPara()
            blocks.add(MdBlock.Divider)
            i++
            continue
        }

        if (trimmed.startsWith("### ")) {
            flushPara()
            blocks.add(MdBlock.Heading(3, trimmed.removePrefix("### ").trim()))
            i++
            continue
        }
        if (trimmed.startsWith("## ")) {
            flushPara()
            blocks.add(MdBlock.Heading(2, trimmed.removePrefix("## ").trim()))
            i++
            continue
        }
        if (trimmed.startsWith("# ")) {
            flushPara()
            blocks.add(MdBlock.Heading(1, trimmed.removePrefix("# ").trim()))
            i++
            continue
        }

        if (trimmed.startsWith(">")) {
            flushPara()
            val quoteLines = mutableListOf(trimmed.removePrefix(">").trim())
            while (i + 1 < lines.size && lines[i + 1].trim().startsWith(">")) {
                i++
                quoteLines.add(lines[i].trim().removePrefix(">").trim())
            }
            blocks.add(MdBlock.Quote(quoteLines.joinToString("\n")))
            i++
            continue
        }

        val bulletMatch = Regex("""^([-*•]|\d+\.)\s+(.+)$""").find(trimmed)
        if (bulletMatch != null) {
            flushPara()
            val rawMarker = bulletMatch.groupValues[1]
            val marker = if (rawMarker in listOf("-", "*")) "•" else rawMarker
            blocks.add(MdBlock.BulletItem(marker, bulletMatch.groupValues[2]))
            i++
            continue
        }

        if (paraBuf.isNotEmpty()) paraBuf.append('\n')
        paraBuf.append(line)
        i++
    }
    flushPara()
    return blocks
}

private fun parseInlineMarkdown(
    raw: String,
    baseColor: Color,
    actionColor: Color,
    boldColor: Color,
    codeBg: Color,
    codeFg: Color,
    isStreaming: Boolean = false,
    isDarkTheme: Boolean = false,
    onSemanticSpanClick: ((SemanticSegment.SemanticSpan) -> Unit)? = null
): AnnotatedString {
    val segments = SemanticMarkupEngine.parseSegments(raw, streaming = isStreaming)
    return buildAnnotatedString {
        segments.forEach { seg ->
            when (seg) {
                is SemanticSegment.PlainText -> {
                    appendPlainMarkdown(
                        raw = seg.text,
                        baseColor = baseColor,
                        actionColor = actionColor,
                        boldColor = boldColor,
                        codeBg = codeBg,
                        codeFg = codeFg
                    )
                }
                is SemanticSegment.SemanticSpan -> {
                    val entityColor = semanticEntityColor(seg.type, isDarkTheme)
                    val spanStyle = if (seg.type == SemanticEntityType.THOUGHT) {
                        SpanStyle(
                            color = entityColor,
                            fontStyle = FontStyle.Italic,
                            background = entityColor.copy(alpha = 0.08f)
                        )
                    } else {
                        SpanStyle(
                            color = entityColor,
                            fontWeight = FontWeight.SemiBold,
                            textDecoration = TextDecoration.Underline,
                            background = entityColor.copy(alpha = 0.13f)
                        )
                    }
                    if (onSemanticSpanClick != null) {
                        val linkTag = "${seg.type.tagKey}:${seg.entityId}:${seg.displayText}"
                        withLink(
                            LinkAnnotation.Clickable(
                                tag = linkTag,
                                styles = TextLinkStyles(style = spanStyle)
                            ) {
                                onSemanticSpanClick(seg)
                            }
                        ) {
                            append(seg.displayText)
                        }
                    } else {
                        withStyle(spanStyle) {
                            append(seg.displayText)
                        }
                    }
                }
            }
        }
    }
}

private fun AnnotatedString.Builder.appendPlainMarkdown(
    raw: String,
    baseColor: Color,
    actionColor: Color,
    boldColor: Color,
    codeBg: Color,
    codeFg: Color
) {
    val tokenRegex = Regex("""(`[^`\n]+`|\*\*[^*\n]+\*\*|~~[^~\n]+~~|\*[^*\n]+\*|\[([^\]\n]+)\]\([^)\n]+\))""")
    var lastIndex = 0
    for (match in tokenRegex.findAll(raw)) {
        if (match.range.first > lastIndex) {
            withStyle(SpanStyle(color = baseColor)) {
                append(raw.substring(lastIndex, match.range.first))
            }
        }
        val token = match.value
        when {
            token.startsWith("`") && token.endsWith("`") && token.length > 2 -> {
                withStyle(
                    SpanStyle(
                        fontFamily = MonoFontFamily,
                        color = codeFg,
                        background = codeBg
                    )
                ) {
                    append(" ${token.substring(1, token.length - 1)} ")
                }
            }
            token.startsWith("**") && token.endsWith("**") && token.length > 4 -> {
                withStyle(SpanStyle(fontWeight = FontWeight.Bold, color = boldColor)) {
                    append(token.substring(2, token.length - 2))
                }
            }
            token.startsWith("~~") && token.endsWith("~~") && token.length > 4 -> {
                withStyle(
                    SpanStyle(
                        color = actionColor,
                        textDecoration = TextDecoration.LineThrough
                    )
                ) {
                    append(token.substring(2, token.length - 2))
                }
            }
            token.startsWith("*") && token.endsWith("*") && token.length > 2 -> {
                withStyle(SpanStyle(fontStyle = FontStyle.Italic, color = actionColor)) {
                    append(token.substring(1, token.length - 1))
                }
            }
            token.startsWith("[") -> {
                val label = match.groupValues.getOrNull(2).orEmpty()
                withStyle(
                    SpanStyle(
                        color = boldColor,
                        textDecoration = TextDecoration.Underline
                    )
                ) {
                    append(label.ifBlank { token })
                }
            }
            else -> append(token)
        }
        lastIndex = match.range.last + 1
    }
    if (lastIndex < raw.length) {
        withStyle(SpanStyle(color = baseColor)) {
            append(raw.substring(lastIndex))
        }
    }
}

