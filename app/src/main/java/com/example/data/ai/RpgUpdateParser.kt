package com.example.data.ai

import com.example.data.model.ChronicleEntry
import com.example.data.model.NpcDossier
import com.example.data.model.QuestItem
import com.example.data.model.RpgState
import com.example.data.model.WorldLocationNode
import java.util.UUID

data class NpcUpdateCommand(
    val name: String,
    val dispDelta: Long = 0L,
    val mood: String? = null,
    val role: String? = null,
    val faction: String? = null,
    val revealStats: Boolean = false,
    val revealSecretDelta: Long = 0L,
    val addSecret: String? = null,
    val perceptionDc: Long? = null
)

data class WorldUpdateCommand(
    val timeOfDay: String? = null,
    val dayDelta: Long = 0L,
    val weather: String? = null
)

data class QuestUpdateCommand(
    val title: String,
    val description: String? = null,
    val addStage: String? = null,
    val stageDoneDelta: Long = 0L,
    val completed: Boolean = false
)

data class ChronicleCommand(
    val title: String,
    val summary: String,
    val tags: List<String> = emptyList()
)

data class RpgUpdateDelta(
    val rawTag: String,
    val hpDelta: Long = 0L,
    val mpDelta: Long = 0L,
    val goldDelta: Long = 0L,
    val levelDelta: Long = 0L,
    val addItems: List<String> = emptyList(),
    val removeItems: List<String> = emptyList(),
    val addStatuses: List<String> = emptyList(),
    val removeStatuses: List<String> = emptyList(),
    val newQuest: String? = null,
    val newLocation: String? = null,
    val npcUpdates: List<NpcUpdateCommand> = emptyList(),
    val worldUpdate: WorldUpdateCommand? = null,
    val questUpdates: List<QuestUpdateCommand> = emptyList(),
    val chronicleEntries: List<ChronicleCommand> = emptyList(),
    val factionDeltas: Map<String, Long> = emptyMap(),
    val meterDeltas: Map<String, Long> = emptyMap(),
    val meterSetValues: Map<String, Long> = emptyMap()
) {
    fun summaryText(): String = buildList {
        if (hpDelta != 0L) add("HP ${formatSigned(hpDelta)}")
        if (mpDelta != 0L) add("MP ${formatSigned(mpDelta)}")
        if (goldDelta != 0L) add("Золото ${formatSigned(goldDelta)}")
        if (levelDelta != 0L) add("Ур. ${formatSigned(levelDelta)}")
        addItems.forEach { add("+$it") }
        removeItems.forEach { add("-$it") }
        addStatuses.forEach { add("[$it]") }
        removeStatuses.forEach { add("[-$it]") }
        if (!newLocation.isNullOrBlank()) add("Локация: ${newLocation.take(36)}")
        if (!newQuest.isNullOrBlank()) add("Квест: ${newQuest.take(36)}")
        npcUpdates.forEach { npc ->
            val dStr = if (npc.dispDelta != 0L) " (${formatSigned(npc.dispDelta)})" else ""
            add("NPC ${npc.name.take(24)}$dStr")
        }
        worldUpdate?.let { w ->
            val parts = listOfNotNull(w.timeOfDay, w.weather).joinToString(", ")
            if (parts.isNotBlank()) add("Мир: ${parts.take(32)}")
        }
        questUpdates.forEach { q -> add("Журнал: ${q.title.take(28)}") }
        chronicleEntries.forEach { c -> add("Хроника: ${c.title.take(28)}") }
        factionDeltas.forEach { (f, d) -> add("$f ${formatSigned(d)}") }
        meterDeltas.forEach { (m, d) -> add("$m ${formatSigned(d)}") }
        meterSetValues.forEach { (m, v) -> add("$m = $v") }
    }.joinToString(" • ")

    fun applyTo(
        state: RpgState,
        currentTurn: Long = 0L,
        customMeters: List<com.example.data.model.CustomMeterSpec> = emptyList()
    ): RpgState {
        val nextMaxHp = state.maxHp.coerceAtLeast(1L)
        val nextMaxMp = state.maxMp.coerceAtLeast(0L)
        val nextInventory = state.inventory.toMutableList().apply {
            removeItems.forEach { rem ->
                val idx = indexOfFirst { it.equals(rem, ignoreCase = true) }
                if (idx >= 0) removeAt(idx)
            }
            addItems.forEach { addItem ->
                if (size < 40 && none { it.equals(addItem, ignoreCase = true) }) {
                    add(addItem.take(120))
                }
            }
        }
        val nextStatuses = state.statuses.toMutableList().apply {
            removeStatuses.forEach { rem ->
                val idx = indexOfFirst { it.equals(rem, ignoreCase = true) }
                if (idx >= 0) removeAt(idx)
            }
            addStatuses.forEach { status ->
                if (size < 20 && none { it.equals(status, ignoreCase = true) }) {
                    add(status.take(80))
                }
            }
        }

        var nextLocationId = state.currentLocationId
        val nextLocations = state.locations.toMutableList()
        if (!newLocation.isNullOrBlank()) {
            val locName = newLocation.trim().take(80)
            val existingIdx = nextLocations.indexOfFirst { it.name.equals(locName, ignoreCase = true) }
            if (existingIdx >= 0) {
                val existing = nextLocations[existingIdx]
                nextLocations[existingIdx] = existing.copy(visited = true)
                nextLocationId = existing.id
            } else if (nextLocations.size < 24) {
                val newId = "loc_${UUID.randomUUID().toString().take(8)}"
                val prevId = state.currentLocationId
                if (prevId.isNotBlank()) {
                    val prevIdx = nextLocations.indexOfFirst { it.id == prevId }
                    if (prevIdx >= 0) {
                        val prev = nextLocations[prevIdx]
                        if (!prev.connections.contains(newId)) {
                            nextLocations[prevIdx] = prev.copy(connections = (prev.connections + newId).take(10))
                        }
                    }
                }
                nextLocations.add(
                    WorldLocationNode(
                        id = newId,
                        name = locName,
                        description = "Открыто по ходу сцены",
                        connections = if (prevId.isNotBlank()) listOf(prevId) else emptyList(),
                        visited = true
                    )
                )
                nextLocationId = newId
            }
        }

        // Apply NPC updates + Fog-of-War reveals
        val nextNpcs = state.npcs.toMutableList()
        npcUpdates.forEach { cmd ->
            val idx = nextNpcs.indexOfFirst { it.name.equals(cmd.name, ignoreCase = true) }
            if (idx >= 0) {
                val cur = nextNpcs[idx]
                val updatedSecrets = if (!cmd.addSecret.isNullOrBlank() &&
                    cur.secrets.none { it.equals(cmd.addSecret, ignoreCase = true) }
                ) {
                    (cur.secrets + cmd.addSecret.take(300)).take(8)
                } else {
                    cur.secrets
                }
                val newDisp = (cur.disposition + cmd.dispDelta).coerceIn(-100L, 100L)
                val updated = cur.copy(
                    disposition = newDisp,
                    mood = cmd.mood?.take(60) ?: cur.mood,
                    role = cmd.role?.take(80) ?: cur.role,
                    faction = cmd.faction?.take(80) ?: cur.faction,
                    secrets = updatedSecrets,
                    statsRevealed = cur.statsRevealed || cmd.revealStats,
                    revealedSecretCount = (cur.revealedSecretCount + cmd.revealSecretDelta)
                        .coerceIn(0L, updatedSecrets.size.toLong()),
                    perceptionDc = cmd.perceptionDc?.coerceIn(1L, 35L) ?: cur.perceptionDc
                )
                nextNpcs[idx] = MathEngine.applyTrustAutoReveal(updated)
            } else if (nextNpcs.size < 24) {
                val initSecrets = listOfNotNull(cmd.addSecret?.take(300))
                val created = NpcDossier(
                    id = "npc_${UUID.randomUUID().toString().take(8)}",
                    name = cmd.name.take(80),
                    role = cmd.role?.take(80) ?: "Персонаж сцены",
                    faction = cmd.faction?.take(80).orEmpty(),
                    disposition = cmd.dispDelta.coerceIn(-100L, 100L),
                    mood = cmd.mood?.take(60) ?: "Нейтральное",
                    stats = mapOf(
                        "Сила" to 11L,
                        "Ловкость" to 12L,
                        "Интеллект" to 12L,
                        "Восприятие" to 12L,
                        "Харизма" to 12L
                    ),
                    secrets = initSecrets,
                    revealedSecretCount = cmd.revealSecretDelta.coerceIn(0L, initSecrets.size.toLong()),
                    statsRevealed = cmd.revealStats,
                    perceptionDc = cmd.perceptionDc?.coerceIn(1L, 35L) ?: 13L
                )
                nextNpcs.add(MathEngine.applyTrustAutoReveal(created))
            }
        }

        // Apply Quest journal updates
        val nextQuests = state.quests.toMutableList()
        questUpdates.forEach { cmd ->
            val idx = nextQuests.indexOfFirst { it.title.equals(cmd.title, ignoreCase = true) }
            if (idx >= 0) {
                val cur = nextQuests[idx]
                val stages = if (!cmd.addStage.isNullOrBlank() &&
                    cur.stages.none { it.equals(cmd.addStage, ignoreCase = true) }
                ) {
                    (cur.stages + cmd.addStage.take(160)).take(10)
                } else {
                    cur.stages
                }
                nextQuests[idx] = cur.copy(
                    description = cmd.description?.take(500) ?: cur.description,
                    stages = stages,
                    completedStages = (cur.completedStages + cmd.stageDoneDelta).coerceIn(0L, stages.size.toLong()),
                    completed = cur.completed || cmd.completed
                )
            } else if (nextQuests.size < 20) {
                val stages = listOfNotNull(cmd.addStage?.take(160))
                nextQuests.add(
                    QuestItem(
                        id = "quest_${UUID.randomUUID().toString().take(8)}",
                        title = cmd.title.take(120),
                        description = cmd.description?.take(500).orEmpty(),
                        stages = stages,
                        completedStages = cmd.stageDoneDelta.coerceIn(0L, stages.size.toLong()),
                        completed = cmd.completed
                    )
                )
            }
        }

        // Apply Chronicle entries
        val nextChronicle = (state.chronicle + chronicleEntries.map { c ->
            ChronicleEntry(
                id = "chr_${UUID.randomUUID().toString().take(8)}",
                turnNumber = currentTurn.coerceAtLeast(0L),
                title = c.title.take(120),
                summary = c.summary.take(600),
                tags = c.tags.take(8)
            )
        }).takeLast(40)

        // World environment
        val nextWorld = worldUpdate?.let { w ->
            state.worldEnv.copy(
                timeOfDay = w.timeOfDay?.take(60) ?: state.worldEnv.timeOfDay,
                dayCount = (state.worldEnv.dayCount + w.dayDelta).coerceIn(1L, 99999L),
                weather = w.weather?.take(80) ?: state.worldEnv.weather
            )
        } ?: state.worldEnv

        // Factions
        val nextFactions = state.playerProfile.factionReputation.toMutableMap().apply {
            factionDeltas.forEach { (k, d) ->
                val clean = k.trim().take(60)
                if (clean.isNotEmpty() && size < 16) {
                    val existing = this[clean] ?: 0L
                    this[clean] = (existing + d).coerceIn(-100L, 100L)
                }
            }
        }

        // Custom & dynamic simulation meters -> stored in RpgState.stats (Firestore-safe)
        val meterBoundsByName = customMeters.associateBy { it.label.lowercase() }
        val nextStats = state.stats.toMutableMap().apply {
            meterDeltas.forEach { (rawName, delta) ->
                val clean = rawName.trim().take(40)
                if (clean.isNotEmpty() && (size < 24 || containsKey(clean))) {
                    val existingKey = keys.firstOrNull { it.equals(clean, ignoreCase = true) } ?: clean
                    val spec = meterBoundsByName[clean.lowercase()]
                    val curVal = this[existingKey] ?: spec?.initialValue ?: 0L
                    val minBound = spec?.minValue ?: -999L
                    val maxBound = spec?.maxValue ?: 99999L
                    this[existingKey] = (curVal + delta).coerceIn(minBound, maxBound)
                }
            }
            meterSetValues.forEach { (rawName, targetVal) ->
                val clean = rawName.trim().take(40)
                if (clean.isNotEmpty() && (size < 24 || containsKey(clean))) {
                    val existingKey = keys.firstOrNull { it.equals(clean, ignoreCase = true) } ?: clean
                    val spec = meterBoundsByName[clean.lowercase()]
                    val minBound = spec?.minValue ?: -999L
                    val maxBound = spec?.maxValue ?: 99999L
                    this[existingKey] = targetVal.coerceIn(minBound, maxBound)
                }
            }
        }

        val rawNext = state.copy(
            enabled = true,
            level = (state.level + levelDelta).coerceIn(1L, 999L),
            hp = (state.hp + hpDelta).coerceIn(0L, nextMaxHp),
            mp = (state.mp + mpDelta).coerceIn(0L, nextMaxMp),
            gold = (state.gold + goldDelta).coerceIn(0L, 9999999L),
            stats = nextStats,
            inventory = nextInventory,
            statuses = nextStatuses,
            activeQuest = newQuest?.trim()?.take(500) ?: state.activeQuest,
            currentLocationId = nextLocationId,
            locations = nextLocations,
            npcs = nextNpcs,
            quests = nextQuests,
            chronicle = nextChronicle,
            worldEnv = nextWorld,
            playerProfile = state.playerProfile.copy(factionReputation = nextFactions)
        )
        return MathEngine.validateAndClampRpgState(state, rawNext).first
    }

    private fun formatSigned(v: Long): String = if (v > 0) "+$v" else "$v"
}

data class ParsedAssistantContent(
    val thinkingText: String?,
    val isThinkingOpen: Boolean,
    val visibleMarkdown: String,
    val rpgDelta: RpgUpdateDelta?
)

object RpgAndThinkParser {

    private val thinkClosedRegex = Regex("(?is)<think>(.*?)</think>")
    private val thinkOpenRegex = Regex("(?is)<think>(.*)$")
    private val rpgTagRegex = Regex("""(?is)\[RPG_UPDATE:\s*([^\]]+)]""")
    private val npcTagRegex = Regex("""(?is)\[NPC_UPDATE:\s*([^\]]+)]""")
    private val worldTagRegex = Regex("""(?is)\[WORLD_UPDATE:\s*([^\]]+)]""")
    private val questTagRegex = Regex("""(?is)\[QUEST_UPDATE:\s*([^\]]+)]""")
    private val chronicleTagRegex = Regex("""(?is)\[CHRONICLE:\s*([^\]]+)]""")
    private val factionsTagRegex = Regex("""(?is)\[FACTIONS:\s*([^\]]+)]""")
    private val meterTagRegex = Regex("""(?is)\[(?:METER|STAT|METRIC):\s*([^\]]+)]""")

    fun stripThinkingForPrompt(raw: String): String {
        var cleaned = thinkClosedRegex.replace(raw, "")
        cleaned = thinkOpenRegex.replace(cleaned, "")
        return SemanticMarkupEngine.stripTagsToPlainText(cleaned).trim()
    }

    fun stripMachineTagsOnly(raw: String): String {
        var body = raw
        body = rpgTagRegex.replace(body, "")
        body = npcTagRegex.replace(body, "")
        body = worldTagRegex.replace(body, "")
        body = questTagRegex.replace(body, "")
        body = chronicleTagRegex.replace(body, "")
        body = factionsTagRegex.replace(body, "")
        body = meterTagRegex.replace(body, "")
        return body.trim()
    }

    fun parseForDisplay(raw: String): ParsedAssistantContent {
        val thoughts = mutableListOf<String>()
        var isThinkingOpen = false

        var body = thinkClosedRegex.replace(raw) { match ->
            val inner = match.groupValues.getOrNull(1)?.trim().orEmpty()
            if (inner.isNotEmpty()) thoughts.add(inner)
            ""
        }
        val unclosed = thinkOpenRegex.find(body)
        if (unclosed != null) {
            isThinkingOpen = true
            val inner = unclosed.groupValues.getOrNull(1)?.trim().orEmpty()
            if (inner.isNotEmpty()) thoughts.add(inner)
            body = body.removeRange(unclosed.range)
        }

        val rawMatches = mutableListOf<String>()
        var baseDelta = RpgUpdateDelta(rawTag = "")

        val rpgMatch = rpgTagRegex.findAll(body).lastOrNull()
        if (rpgMatch != null) {
            rawMatches += rpgMatch.value
            baseDelta = parseRpgTag(rpgMatch.value, rpgMatch.groupValues.getOrNull(1).orEmpty()) ?: baseDelta
            body = rpgTagRegex.replace(body, "")
        }

        val npcUpdates = mutableListOf<NpcUpdateCommand>()
        npcTagRegex.findAll(body).forEach { m ->
            rawMatches += m.value
            parseNpcTag(m.groupValues.getOrNull(1).orEmpty())?.let { npcUpdates += it }
        }
        if (npcUpdates.isNotEmpty()) body = npcTagRegex.replace(body, "")

        var worldCmd: WorldUpdateCommand? = null
        worldTagRegex.findAll(body).lastOrNull()?.let { m ->
            rawMatches += m.value
            worldCmd = parseWorldTag(m.groupValues.getOrNull(1).orEmpty())
            body = worldTagRegex.replace(body, "")
        }

        val questCmds = mutableListOf<QuestUpdateCommand>()
        questTagRegex.findAll(body).forEach { m ->
            rawMatches += m.value
            parseQuestTag(m.groupValues.getOrNull(1).orEmpty())?.let { questCmds += it }
        }
        if (questCmds.isNotEmpty()) body = questTagRegex.replace(body, "")

        val chronicleCmds = mutableListOf<ChronicleCommand>()
        chronicleTagRegex.findAll(body).forEach { m ->
            rawMatches += m.value
            parseChronicleTag(m.groupValues.getOrNull(1).orEmpty())?.let { chronicleCmds += it }
        }
        if (chronicleCmds.isNotEmpty()) body = chronicleTagRegex.replace(body, "")

        val factionMap = LinkedHashMap<String, Long>()
        factionsTagRegex.findAll(body).forEach { m ->
            rawMatches += m.value
            parseKvPairs(m.groupValues.getOrNull(1).orEmpty()).forEach { (k, v) ->
                v.toLongOrNull()?.let { factionMap[k] = it }
            }
        }
        if (factionMap.isNotEmpty()) body = factionsTagRegex.replace(body, "")

        val meterDeltas = LinkedHashMap<String, Long>()
        val meterSetValues = LinkedHashMap<String, Long>()
        meterTagRegex.findAll(body).forEach { m ->
            rawMatches += m.value
            parseMeterAssignments(m.groupValues.getOrNull(1).orEmpty(), meterDeltas, meterSetValues)
        }
        if (meterDeltas.isNotEmpty() || meterSetValues.isNotEmpty()) {
            body = meterTagRegex.replace(body, "")
        }

        val combinedDelta = baseDelta.copy(
            rawTag = rawMatches.joinToString("\n"),
            npcUpdates = npcUpdates,
            worldUpdate = worldCmd,
            questUpdates = questCmds,
            chronicleEntries = chronicleCmds,
            factionDeltas = factionMap,
            meterDeltas = meterDeltas,
            meterSetValues = meterSetValues
        )
        val finalDelta = if (combinedDelta.summaryText().isNotBlank()) combinedDelta else null

        return ParsedAssistantContent(
            thinkingText = thoughts.joinToString("\n\n").takeIf { it.isNotBlank() },
            isThinkingOpen = isThinkingOpen,
            visibleMarkdown = body.trim(),
            rpgDelta = finalDelta
        )
    }

    private fun parseMeterAssignments(
        inner: String,
        deltasOut: MutableMap<String, Long>,
        setsOut: MutableMap<String, Long>
    ) {
        inner.split(",", ";", "|").forEach { segment ->
            val part = segment.trim()
            if (part.isEmpty()) return@forEach
            val assignMatch = Regex("""^(.+?)(:=|=)\s*([+-]?\d+)\s*(?:[a-zA-Zа-яА-Я%/]+)?$""").find(part)
            if (assignMatch != null) {
                val key = assignMatch.groupValues[1].trim().trim('"', '\'', '«', '»')
                val op = assignMatch.groupValues[2]
                val numStr = assignMatch.groupValues[3]
                val num = numStr.toLongOrNull() ?: return@forEach
                if (key.isBlank() || key.equals("max", ignoreCase = true) || key.equals("min", ignoreCase = true)) {
                    return@forEach
                }
                if (op == "=" && (numStr.startsWith("+") || numStr.startsWith("-"))) {
                    deltasOut[key] = (deltasOut[key] ?: 0L) + num
                } else {
                    setsOut[key] = num
                }
                return@forEach
            }
            val spaceDelta = Regex("""^(.+?)\s+([+-]\d+)\s*(?:[a-zA-Zа-яА-Я%/]+)?$""").find(part)
            if (spaceDelta != null) {
                val key = spaceDelta.groupValues[1].trim().trim('"', '\'', '«', '»')
                val num = spaceDelta.groupValues[2].toLongOrNull() ?: return@forEach
                if (key.isNotBlank()) {
                    deltasOut[key] = (deltasOut[key] ?: 0L) + num
                }
            }
        }
    }

    private fun parseKvPairs(inner: String): List<Pair<String, String>> {
        val result = mutableListOf<Pair<String, String>>()
        inner.split(",", ";").forEach { part ->
            val trimmed = part.trim()
            val eqIdx = trimmed.indexOf('=')
            if (eqIdx <= 0) return@forEach
            val key = trimmed.substring(0, eqIdx).trim().trim('"', '\'', '«', '»')
            val value = trimmed.substring(eqIdx + 1).trim().trim('"', '\'', '«', '»')
            if (key.isNotEmpty() && value.isNotEmpty()) {
                result += key to value
            }
        }
        return result
    }

    private fun parseRpgTag(rawTag: String, inner: String): RpgUpdateDelta? {
        if (inner.isBlank()) return null
        var hpDelta = 0L
        var mpDelta = 0L
        var goldDelta = 0L
        var levelDelta = 0L
        val addItems = mutableListOf<String>()
        val removeItems = mutableListOf<String>()
        val addStatuses = mutableListOf<String>()
        val removeStatuses = mutableListOf<String>()
        var quest: String? = null
        var loc: String? = null

        parseKvPairs(inner).forEach { (rawKey, value) ->
            when (rawKey.lowercase()) {
                "hp" -> hpDelta += value.toLongOrNull() ?: 0L
                "mp" -> mpDelta += value.toLongOrNull() ?: 0L
                "gold" -> goldDelta += value.toLongOrNull() ?: 0L
                "level", "lvl" -> levelDelta += value.toLongOrNull() ?: 0L
                "+item", "item+" -> addItems.add(value)
                "-item", "item-" -> removeItems.add(value)
                "+status", "status+" -> addStatuses.add(value)
                "-status", "status-" -> removeStatuses.add(value)
                "quest" -> quest = value
                "loc", "location" -> loc = value
            }
        }

        val delta = RpgUpdateDelta(
            rawTag = rawTag,
            hpDelta = hpDelta,
            mpDelta = mpDelta,
            goldDelta = goldDelta,
            levelDelta = levelDelta,
            addItems = addItems,
            removeItems = removeItems,
            addStatuses = addStatuses,
            removeStatuses = removeStatuses,
            newQuest = quest,
            newLocation = loc
        )
        return if (delta.summaryText().isNotBlank()) delta else null
    }

    private fun parseNpcTag(inner: String): NpcUpdateCommand? {
        var name = ""
        var disp = 0L
        var mood: String? = null
        var role: String? = null
        var faction: String? = null
        var revealStats = false
        var revealSecret = 0L
        var secret: String? = null
        var dc: Long? = null
        parseKvPairs(inner).forEach { (k, v) ->
            when (k.lowercase()) {
                "name", "npc" -> name = v
                "disp", "trust" -> disp += v.toLongOrNull() ?: 0L
                "mood" -> mood = v
                "role" -> role = v
                "faction" -> faction = v
                "revealstats", "reveal_stats" -> revealStats = v.equals("true", ignoreCase = true) || v == "1"
                "revealsecret", "reveal_secret" -> revealSecret = v.toLongOrNull() ?: 1L
                "secret", "+secret", "secret+" -> secret = v
                "dc", "perceptiondc", "perception_dc" -> dc = v.toLongOrNull()
            }
        }
        return if (name.isBlank()) null else NpcUpdateCommand(
            name = name,
            dispDelta = disp,
            mood = mood,
            role = role,
            faction = faction,
            revealStats = revealStats,
            revealSecretDelta = revealSecret,
            addSecret = secret,
            perceptionDc = dc
        )
    }

    private fun parseWorldTag(inner: String): WorldUpdateCommand? {
        var time: String? = null
        var day = 0L
        var weather: String? = null
        parseKvPairs(inner).forEach { (k, v) ->
            when (k.lowercase()) {
                "time", "timeofday", "time_of_day" -> time = v
                "day" -> day += v.toLongOrNull() ?: 0L
                "weather" -> weather = v
            }
        }
        return if (time == null && day == 0L && weather == null) null else WorldUpdateCommand(time, day, weather)
    }

    private fun parseQuestTag(inner: String): QuestUpdateCommand? {
        var title = ""
        var desc: String? = null
        var stage: String? = null
        var stageDone = 0L
        var done = false
        parseKvPairs(inner).forEach { (k, v) ->
            when (k.lowercase()) {
                "title", "quest" -> title = v
                "desc", "description" -> desc = v
                "addstage", "add_stage", "+stage" -> stage = v
                "stagedone", "stage_done" -> stageDone += v.toLongOrNull() ?: 0L
                "complete", "completed" -> done = v.equals("true", ignoreCase = true)
            }
        }
        return if (title.isBlank()) null else QuestUpdateCommand(title, desc, stage, stageDone, done)
    }

    private fun parseChronicleTag(inner: String): ChronicleCommand? {
        var title = ""
        var summary = ""
        val tags = mutableListOf<String>()
        if (inner.contains("|")) {
            inner.split("|").forEach { segment ->
                val part = segment.trim()
                if (part.isEmpty()) return@forEach
                val eqIdx = part.indexOf('=')
                if (eqIdx > 0) {
                    val k = part.substring(0, eqIdx).trim().lowercase()
                    val v = part.substring(eqIdx + 1).trim().trim('"', '\'', '«', '»')
                    when (k) {
                        "title" -> title = v
                        "summary", "text" -> summary = v
                        "tags", "теги" -> tags.addAll(v.split(",", ";").map { it.trim() }.filter { it.isNotEmpty() })
                    }
                } else if (summary.isBlank()) {
                    summary = part
                } else if (title.isBlank()) {
                    title = summary
                    summary = part
                }
            }
        } else {
            parseKvPairs(inner).forEach { (k, v) ->
                when (k.lowercase()) {
                    "title" -> title = v
                    "summary", "text" -> summary = v
                    "tags", "теги" -> tags.addAll(v.split("|", ",").map { it.trim() }.filter { it.isNotEmpty() })
                }
            }
        }
        return if (title.isBlank() && summary.isBlank()) null else ChronicleCommand(
            title = title.ifBlank { "Веха" },
            summary = summary.ifBlank { title },
            tags = tags.distinct()
        )
    }
}
