package com.example.data.ai

import com.example.data.model.LoreEntry
import com.example.data.model.NpcDossier
import com.example.data.model.QuestItem
import com.example.data.model.RpgState
import com.example.data.model.SemanticEntityType
import com.example.data.model.WorldLocationNode
import java.util.UUID

sealed interface SemanticSegment {
    data class PlainText(val text: String) : SemanticSegment
    data class SemanticSpan(
        val type: SemanticEntityType,
        val entityId: String,
        val displayText: String,
        val checkStat: String = "Восприятие",
        val checkDc: Long = 13L
    ) : SemanticSegment
}

object SemanticMarkupEngine {

    // Matches [[loc:id|Text]], [[npc:id|Text]], [[item:id|Text]], [[quest:id|Text]],
    // [[check:stat:dc|Text]], [[lore:id|Text]], [[thought|Text]]
    private val semanticTagRegex = Regex("""\[\[([a-zA-Z]+)(?::([^|\]]*))?\|([^\]]+)]]""")
    private val incompleteTrailingTagRegex = Regex("""\[\[[^\]]*$""")

    // Fallback check matcher for raw text when model didn't emit [[check:...]]
    private val plainCheckRegex = Regex(
        """(?i)(?:проверк[аи]\s+([а-яА-Яa-zA-ZёЁ]+)\s*(?:\(|\[)?DC\s*(\d{1,2})(?:\)|\])?|\(([а-яА-Яa-zA-ZёЁ]+)\s+DC\s*(\d{1,2})\)|DC\s*(\d{1,2}))"""
    )

    fun parseSegments(rawText: String, streaming: Boolean = false): List<SemanticSegment> {
        val source = if (streaming) {
            incompleteTrailingTagRegex.replace(rawText, "")
        } else {
            rawText
        }
        val out = mutableListOf<SemanticSegment>()
        var cursor = 0
        semanticTagRegex.findAll(source).forEach { match ->
            val start = match.range.first
            val end = match.range.last + 1
            if (start > cursor) {
                out += SemanticSegment.PlainText(source.substring(cursor, start))
            }
            val rawType = match.groupValues.getOrNull(1).orEmpty()
            val rawMeta = match.groupValues.getOrNull(2).orEmpty().trim()
            val displayText = match.groupValues.getOrNull(3).orEmpty().trim()
            val type = SemanticEntityType.fromTagKey(rawType)
            if (type != null && displayText.isNotBlank()) {
                if (type == SemanticEntityType.CHECK) {
                    val parts = rawMeta.split(":")
                    val stat = parts.getOrNull(0)?.trim().orEmpty().ifBlank { "Восприятие" }
                    val dc = parts.getOrNull(1)?.trim()?.toLongOrNull()?.coerceIn(1L, 35L) ?: 13L
                    out += SemanticSegment.SemanticSpan(
                        type = type,
                        entityId = "${stat.lowercase()}_$dc",
                        displayText = displayText,
                        checkStat = stat,
                        checkDc = dc
                    )
                } else {
                    val entityId = rawMeta.ifBlank { slugify(displayText) }
                    out += SemanticSegment.SemanticSpan(
                        type = type,
                        entityId = entityId,
                        displayText = displayText
                    )
                }
            } else {
                out += SemanticSegment.PlainText(match.value)
            }
            cursor = end
        }
        if (cursor < source.length) {
            out += SemanticSegment.PlainText(source.substring(cursor))
        }
        return out
    }

    fun extractUniqueSpans(rawText: String): List<SemanticSegment.SemanticSpan> {
        return parseSegments(rawText, streaming = false)
            .filterIsInstance<SemanticSegment.SemanticSpan>()
            .distinctBy { "${it.type.tagKey}:${it.entityId.lowercase()}:${it.displayText.lowercase()}" }
    }

    fun stripTagsToPlainText(rawText: String): String {
        val cleaned = semanticTagRegex.replace(rawText) { match ->
            match.groupValues.getOrNull(3).orEmpty().ifBlank { match.value }
        }
        return incompleteTrailingTagRegex.replace(cleaned, "")
    }

    /**
     * Local deterministic Tagger fallback ("Разметчик") for models that didn't emit [[tag:...]] markup.
     * Preserves any existing [[...]] tags and only wraps outside segments.
     */
    fun enrichPlainTextFallback(
        text: String,
        rpgState: RpgState,
        loreEntries: List<LoreEntry>,
        enabledTypes: Set<SemanticEntityType>
    ): String {
        if (text.isBlank()) return text
        val segments = parseSegments(text, streaming = false)
        // Collect already tagged displays so we don't over-duplicate
        val alreadyTaggedLower = segments
            .filterIsInstance<SemanticSegment.SemanticSpan>()
            .map { it.displayText.lowercase() }
            .toMutableSet()

        val targets = mutableListOf<Triple<SemanticEntityType, String, String>>()
        if (SemanticEntityType.LOCATION in enabledTypes) {
            rpgState.locations.forEach { loc ->
                if (loc.name.length >= 3) targets += Triple(SemanticEntityType.LOCATION, loc.id, loc.name)
            }
        }
        if (SemanticEntityType.NPC in enabledTypes) {
            rpgState.npcs.forEach { npc ->
                if (npc.name.length >= 3) targets += Triple(SemanticEntityType.NPC, npc.id, npc.name)
            }
        }
        if (SemanticEntityType.QUEST in enabledTypes) {
            rpgState.quests.forEach { q ->
                if (q.title.length >= 4) targets += Triple(SemanticEntityType.QUEST, q.id, q.title)
            }
        }
        if (SemanticEntityType.ITEM in enabledTypes) {
            rpgState.inventory.forEach { item ->
                val clean = item.trim()
                if (clean.length >= 4) targets += Triple(SemanticEntityType.ITEM, slugify(clean), clean)
            }
        }
        if (SemanticEntityType.LORE in enabledTypes) {
            loreEntries.filter { it.enabled }.forEach { le ->
                if (le.title.length >= 4) targets += Triple(SemanticEntityType.LORE, le.id, le.title)
            }
        }

        val sortedTargets = targets.sortedByDescending { it.third.length }

        return segments.joinToString("") { seg ->
            when (seg) {
                is SemanticSegment.SemanticSpan -> {
                    if (seg.type == SemanticEntityType.CHECK) {
                        "[[check:${seg.checkStat}:${seg.checkDc}|${seg.displayText}]]"
                    } else if (seg.type == SemanticEntityType.THOUGHT) {
                        "[[thought|${seg.displayText}]]"
                    } else {
                        "[[${seg.type.tagKey}:${seg.entityId}|${seg.displayText}]]"
                    }
                }
                is SemanticSegment.PlainText -> {
                    var working = seg.text
                    if (SemanticEntityType.CHECK in enabledTypes) {
                        working = plainCheckRegex.replace(working) { m ->
                            val full = m.value
                            val stat = m.groupValues.getOrNull(1)?.takeIf { it.isNotBlank() }
                                ?: m.groupValues.getOrNull(3)?.takeIf { it.isNotBlank() }
                                ?: "Восприятие"
                            val dcStr = m.groupValues.getOrNull(2)?.takeIf { it.isNotBlank() }
                                ?: m.groupValues.getOrNull(4)?.takeIf { it.isNotBlank() }
                                ?: m.groupValues.getOrNull(5)?.takeIf { it.isNotBlank() }
                                ?: "13"
                            "[[check:${stat.trim()}:$dcStr|$full]]"
                        }
                    }
                    sortedTargets.forEach { (type, id, name) ->
                        val lower = name.lowercase()
                        if (lower in alreadyTaggedLower) return@forEach
                        val escaped = Regex.escape(name)
                        val matchRegex = Regex(
                            """(?<![\p{L}\p{N}_\[\]|])$escaped(?![\p{L}\p{N}_])(?![^\[\]]*]])""",
                            RegexOption.IGNORE_CASE
                        )
                        val found = matchRegex.find(working)
                        if (found != null) {
                            alreadyTaggedLower += lower
                            working = working.replaceRange(
                                found.range,
                                "[[${type.tagKey}:$id|${found.value}]]"
                            )
                        }
                    }
                    working
                }
            }
        }
    }

    /**
     * Resolves a tapped semantic tag to an existing RPG entity or auto-creates a new native card
     * (e.g., if AI coined a new location "Поле светлых" in chat).
     */
    fun resolveOrRegisterEntity(
        state: RpgState,
        type: SemanticEntityType,
        entityId: String,
        displayText: String
    ): Pair<RpgState, String> {
        val cleanName = displayText.trim().ifBlank { "Объект" }
        return when (type) {
            SemanticEntityType.LOCATION -> {
                val existing = state.locations.firstOrNull {
                    it.id.equals(entityId, ignoreCase = true) ||
                        it.name.equals(cleanName, ignoreCase = true)
                }
                if (existing != null) {
                    state to existing.id
                } else {
                    val newId = entityId.takeIf { it.isNotBlank() && it != cleanName }
                        ?: "loc_${UUID.randomUUID().toString().take(8)}"
                    val prevId = state.currentLocationId
                    val updatedLocs = state.locations.map { loc ->
                        if (prevId.isNotBlank() && loc.id == prevId && !loc.connections.contains(newId)) {
                            loc.copy(connections = (loc.connections + newId).take(10))
                        } else loc
                    } + WorldLocationNode(
                        id = newId,
                        name = cleanName.take(80),
                        description = "Обнаружено в ходе ролевой сцены",
                        connections = if (prevId.isNotBlank()) listOf(prevId) else emptyList(),
                        visited = false
                    )
                    state.copy(enabled = true, locations = updatedLocs.take(24)) to newId
                }
            }
            SemanticEntityType.NPC -> {
                val existing = state.npcs.firstOrNull {
                    it.id.equals(entityId, ignoreCase = true) ||
                        it.name.equals(cleanName, ignoreCase = true)
                }
                if (existing != null) {
                    state to existing.id
                } else {
                    val newId = entityId.takeIf { it.isNotBlank() && it != cleanName }
                        ?: "npc_${UUID.randomUUID().toString().take(8)}"
                    val newNpc = NpcDossier(
                        id = newId,
                        name = cleanName.take(80),
                        role = "Персонаж сцены",
                        mood = "Настороженное",
                        stats = mapOf(
                            "Сила" to 11L,
                            "Ловкость" to 12L,
                            "Интеллект" to 12L,
                            "Восприятие" to 12L,
                            "Харизма" to 12L
                        ),
                        secrets = listOf("Мотивы и прошлое этого персонажа пока скрыты."),
                        statsRevealed = false,
                        perceptionDc = 13L,
                        trustThreshold = 45L,
                        notes = "Встречен по ходу повествования"
                    )
                    state.copy(enabled = true, npcs = (state.npcs + newNpc).take(24)) to newId
                }
            }
            SemanticEntityType.QUEST -> {
                val existing = state.quests.firstOrNull {
                    it.id.equals(entityId, ignoreCase = true) ||
                        it.title.equals(cleanName, ignoreCase = true)
                }
                if (existing != null) {
                    state to existing.id
                } else {
                    val newId = entityId.takeIf { it.isNotBlank() && it != cleanName }
                        ?: "quest_${UUID.randomUUID().toString().take(8)}"
                    val newQuest = QuestItem(
                        id = newId,
                        title = cleanName.take(120),
                        description = "Зафиксировано из диалога сцены.",
                        stages = listOf("Изучить зацепку")
                    )
                    state.copy(
                        enabled = true,
                        activeQuest = state.activeQuest.ifBlank { cleanName.take(500) },
                        quests = (state.quests + newQuest).take(20)
                    ) to newId
                }
            }
            SemanticEntityType.ITEM -> {
                val existing = state.inventory.firstOrNull { it.equals(cleanName, ignoreCase = true) }
                if (existing != null) {
                    state to existing
                } else {
                    val item = cleanName.take(120)
                    state.copy(
                        enabled = true,
                        inventory = (state.inventory + item).take(40)
                    ) to item
                }
            }
            else -> state to entityId
        }
    }

    fun slugify(raw: String): String {
        val cleaned = raw.lowercase()
            .replace(Regex("[^a-zа-яё0-9]+"), "_")
            .trim('_')
            .take(32)
        return cleaned.ifBlank { UUID.randomUUID().toString().take(8) }
    }
}
