package com.example.data.export

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import com.example.data.model.ChronicleEntry
import com.example.data.model.CombatState
import com.example.data.model.Combatant
import com.example.data.model.LlmProvider
import com.example.data.model.LoreEntry
import com.example.data.model.MessageKind
import com.example.data.model.NpcDossier
import com.example.data.model.PlayerProfile
import com.example.data.model.QuestItem
import com.example.data.model.RpChat
import com.example.data.model.RpMessage
import com.example.data.model.RpMode
import com.example.data.model.RpScenario
import com.example.data.model.RpgState
import com.example.data.model.UserPersona
import com.example.data.model.WorldEnvironment
import com.example.data.model.WorldLocationNode
import com.example.data.repository.ExportWorkspaceBundle
import com.google.firebase.Timestamp
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

data class ImportedChatData(
    val chat: RpChat,
    val messages: List<RpMessage>
)

object JsonExportManager {

    private fun isoNow(): String {
        val fmt = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US)
        return fmt.format(Date())
    }

    fun chatToJsonString(chat: RpChat, messages: List<RpMessage>): String {
        val root = JSONObject().apply {
            put("app", "LoreCraft")
            put("schemaVersion", 3)
            put("exportType", "single_chat")
            put("exportedAt", isoNow())
            put("chat", chatToJson(chat, messages))
        }
        return root.toString(2)
    }

    fun workspaceToJsonString(bundle: ExportWorkspaceBundle): String {
        val root = JSONObject().apply {
            put("app", "LoreCraft")
            put("schemaVersion", 3)
            put("exportType", "full_workspace")
            put("exportedAt", isoNow())
            put("scenarios", JSONArray().apply {
                bundle.scenarios.forEach { put(scenarioToJson(it)) }
            })
            put("personas", JSONArray().apply {
                bundle.personas.forEach { put(personaToJson(it)) }
            })
            put("chats", JSONArray().apply {
                bundle.chatsWithMessages.forEach { (chat, msgs) ->
                    put(chatToJson(chat, msgs))
                }
            })
        }
        return root.toString(2)
    }

    fun scenariosToJsonString(scenarios: List<RpScenario>): String {
        val root = JSONObject().apply {
            put("app", "LoreCraft")
            put("schemaVersion", 3)
            put("exportType", "scenarios_pack")
            put("exportedAt", isoNow())
            put("scenarios", JSONArray().apply {
                scenarios.forEach { put(scenarioToJson(it)) }
            })
        }
        return root.toString(2)
    }

    fun loreEntriesToJson(entries: List<LoreEntry>): JSONArray = JSONArray().apply {
        entries.forEach { e ->
            put(JSONObject().apply {
                put("id", e.id)
                put("title", e.title)
                put("keys", JSONArray(e.keys))
                put("content", e.content)
                put("alwaysActive", e.alwaysActive)
                put("useRegex", e.useRegex)
                put("priority", e.priority)
                put("enabled", e.enabled)
            })
        }
    }

    fun rpgStateToJson(r: RpgState): JSONObject = JSONObject().apply {
        put("enabled", r.enabled)
        put("level", r.level)
        put("hp", r.hp)
        put("maxHp", r.maxHp)
        put("mp", r.mp)
        put("maxMp", r.maxMp)
        put("gold", r.gold)
        put("stats", JSONObject().apply {
            r.stats.forEach { (k, v) -> put(k, v) }
        })
        put("inventory", JSONArray(r.inventory))
        put("statuses", JSONArray(r.statuses))
        put("activeQuest", r.activeQuest)
        put("currentLocationId", r.currentLocationId)
        put("locations", JSONArray().apply {
            r.locations.forEach { loc ->
                put(JSONObject().apply {
                    put("id", loc.id)
                    put("name", loc.name)
                    put("description", loc.description)
                    put("connections", JSONArray(loc.connections))
                    put("visited", loc.visited)
                    put("backdropUri", loc.backdropUri)
                    put("posX", loc.posX)
                    put("posY", loc.posY)
                    put("dangerLevel", loc.dangerLevel)
                    put("shopItems", JSONArray(loc.shopItems))
                })
            }
        })
        put("combat", JSONObject().apply {
            put("active", r.combat.active)
            put("round", r.combat.round)
            put("turnIndex", r.combat.turnIndex)
            put("combatants", JSONArray().apply {
                r.combat.combatants.forEach { c ->
                    put(JSONObject().apply {
                        put("id", c.id)
                        put("name", c.name)
                        put("hp", c.hp)
                        put("maxHp", c.maxHp)
                        put("initiative", c.initiative)
                        put("enemy", c.enemy)
                        put("notes", c.notes)
                    })
                }
            })
        })
        put("playerProfile", JSONObject().apply {
            put("race", r.playerProfile.race)
            put("charClass", r.playerProfile.charClass)
            put("origin", r.playerProfile.origin)
            put("appearance", r.playerProfile.appearance)
            put("speechStyle", r.playerProfile.speechStyle)
            put("equipmentSlots", JSONObject().apply {
                r.playerProfile.equipmentSlots.forEach { (k, v) -> put(k, v) }
            })
            put("perks", JSONArray(r.playerProfile.perks))
            put("factionReputation", JSONObject().apply {
                r.playerProfile.factionReputation.forEach { (k, v) -> put(k, v) }
            })
            put("statusDurations", JSONObject().apply {
                r.playerProfile.statusDurations.forEach { (k, v) -> put(k, v) }
            })
        })
        put("worldEnv", JSONObject().apply {
            put("timeOfDay", r.worldEnv.timeOfDay)
            put("dayCount", r.worldEnv.dayCount)
            put("weather", r.worldEnv.weather)
            put("worldLaws", r.worldEnv.worldLaws)
            put("economyNotes", r.worldEnv.economyNotes)
        })
        put("npcs", JSONArray().apply {
            r.npcs.forEach { n ->
                put(JSONObject().apply {
                    put("id", n.id)
                    put("name", n.name)
                    put("role", n.role)
                    put("faction", n.faction)
                    put("disposition", n.disposition)
                    put("mood", n.mood)
                    put("appearance", n.appearance)
                    put("stats", JSONObject().apply { n.stats.forEach { (k, v) -> put(k, v) } })
                    put("inventory", JSONArray(n.inventory))
                    put("secrets", JSONArray(n.secrets))
                    put("revealedSecretCount", n.revealedSecretCount)
                    put("statsRevealed", n.statsRevealed)
                    put("perceptionDc", n.perceptionDc)
                    put("trustThreshold", n.trustThreshold)
                    put("portraitUri", n.portraitUri)
                    put("notes", n.notes)
                })
            }
        })
        put("quests", JSONArray().apply {
            r.quests.forEach { q ->
                put(JSONObject().apply {
                    put("id", q.id)
                    put("title", q.title)
                    put("description", q.description)
                    put("stages", JSONArray(q.stages))
                    put("completedStages", q.completedStages)
                    put("completed", q.completed)
                    put("rewardText", q.rewardText)
                })
            }
        })
        put("chronicle", JSONArray().apply {
            r.chronicle.forEach { ch ->
                put(JSONObject().apply {
                    put("id", ch.id)
                    put("turnNumber", ch.turnNumber)
                    put("title", ch.title)
                    put("summary", ch.summary)
                    put("tags", JSONArray(ch.tags))
                })
            }
        })
    }

    fun scenarioToJson(s: RpScenario): JSONObject = JSONObject().apply {
        put("id", s.id)
        put("userId", s.userId)
        put("title", s.title)
        put("subtitle", s.subtitle)
        put("rpMode", s.rpMode)
        put("systemPrompt", s.systemPrompt)
        put("characterBio", s.characterBio)
        put("firstMessage", s.firstMessage)
        put("exampleDialogue", s.exampleDialogue)
        put("loreNotes", JSONArray(s.loreNotes))
        put("loreEntries", loreEntriesToJson(s.loreEntries))
        put("defaultRpgState", rpgStateToJson(s.defaultRpgState))
        put("defaultAgentSetupJson", s.defaultAgentSetupJson)
        put("participants", JSONArray(s.participants))
        put("tags", JSONArray(s.tags))
        put("defaultProvider", s.defaultProvider)
        put("defaultModelId", s.defaultModelId)
        put("avatarIconKey", s.avatarIconKey)
        put("accentColorHex", s.accentColorHex)
        put("portraitUri", s.portraitUri)
        put("backdropUri", s.backdropUri)
        put("pinned", s.pinned)
        put("createdAtEpochMs", s.createdAt?.toDate()?.time ?: 0L)
        put("updatedAtEpochMs", s.updatedAt?.toDate()?.time ?: 0L)
        put("generationParams", JSONObject().apply {
            put("temperature", s.temperature)
            put("topP", s.topP)
            put("topK", s.topK)
            put("maxTokens", s.maxTokens)
            put("contextWindowMessages", s.contextWindowMessages)
            put("presencePenalty", s.presencePenalty)
            put("frequencyPenalty", s.frequencyPenalty)
            put("stopSequences", JSONArray(s.stopSequences))
        })
    }

    fun scenarioFromJson(obj: JSONObject): RpScenario {
        val gen = obj.optJSONObject("generationParams")
        val createdMs = obj.optLong("createdAtEpochMs", 0L)
        val updatedMs = obj.optLong("updatedAtEpochMs", 0L)
        return RpScenario(
            id = obj.optString("id", ""),
            userId = obj.optString("userId", ""),
            title = obj.optString("title", "Сценарий").ifBlank { "Сценарий" }.take(120),
            subtitle = obj.optString("subtitle", "").take(200),
            rpMode = RpMode.fromString(obj.optString("rpMode")).name,
            systemPrompt = obj.optString("systemPrompt", "Ты — ролевой партнёр.").take(20000),
            characterBio = obj.optString("characterBio", "").take(8000),
            firstMessage = obj.optString("firstMessage", "").take(8000),
            exampleDialogue = obj.optString("exampleDialogue", "").take(8000),
            loreNotes = jsonArrayToStrings(obj.optJSONArray("loreNotes")).take(30),
            loreEntries = parseLoreEntries(obj.optJSONArray("loreEntries")),
            defaultRpgState = parseRpgState(obj.optJSONObject("defaultRpgState")),
            defaultAgentSetupJson = obj.optString("defaultAgentSetupJson", "").take(32000),
            participants = jsonArrayToStrings(obj.optJSONArray("participants")).take(12),
            tags = jsonArrayToStrings(obj.optJSONArray("tags")).take(10),
            defaultProvider = LlmProvider.fromString(obj.optString("defaultProvider")).name,
            defaultModelId = obj.optString("defaultModelId", "gemini-3.8-flash").ifBlank { "gemini-3.8-flash" },
            temperature = (gen?.optDouble("temperature") ?: obj.optDouble("temperature", 0.85)).coerceIn(0.0, 2.0),
            topP = (gen?.optDouble("topP") ?: obj.optDouble("topP", 0.95)).coerceIn(0.05, 1.0),
            topK = (gen?.optLong("topK") ?: obj.optLong("topK", 40L)).coerceIn(1L, 200L),
            maxTokens = (gen?.optLong("maxTokens") ?: obj.optLong("maxTokens", 2048L)).coerceIn(64L, 16384L),
            contextWindowMessages = (gen?.optLong("contextWindowMessages") ?: 24L).coerceIn(2L, 200L),
            presencePenalty = (gen?.optDouble("presencePenalty") ?: 0.0).coerceIn(-2.0, 2.0),
            frequencyPenalty = (gen?.optDouble("frequencyPenalty") ?: 0.0).coerceIn(-2.0, 2.0),
            stopSequences = jsonArrayToStrings(gen?.optJSONArray("stopSequences")).take(10),
            avatarIconKey = obj.optString("avatarIconKey", "chronicler").ifBlank { "chronicler" },
            accentColorHex = obj.optString("accentColorHex", "#A78BFA").ifBlank { "#A78BFA" },
            portraitUri = obj.optString("portraitUri", "").take(500),
            backdropUri = obj.optString("backdropUri", "").take(500),
            pinned = obj.optBoolean("pinned", false),
            createdAt = if (createdMs > 0L) Timestamp(Date(createdMs)) else null,
            updatedAt = if (updatedMs > 0L) Timestamp(Date(updatedMs)) else null
        )
    }

    fun personaToJson(p: UserPersona): JSONObject = JSONObject().apply {
        put("id", p.id)
        put("userId", p.userId)
        put("name", p.name)
        put("description", p.description)
        put("preferredStyle", p.preferredStyle)
        put("activeDefault", p.activeDefault)
        put("createdAtEpochMs", p.createdAt?.toDate()?.time ?: 0L)
        put("updatedAtEpochMs", p.updatedAt?.toDate()?.time ?: 0L)
    }

    fun personaFromJson(obj: JSONObject): UserPersona {
        val createdMs = obj.optLong("createdAtEpochMs", 0L)
        val updatedMs = obj.optLong("updatedAtEpochMs", 0L)
        return UserPersona(
            id = obj.optString("id", ""),
            userId = obj.optString("userId", ""),
            name = obj.optString("name", "Странник").ifBlank { "Странник" }.take(100),
            description = obj.optString("description", "").take(4000),
            preferredStyle = obj.optString("preferredStyle", "").take(500),
            activeDefault = obj.optBoolean("activeDefault", false),
            createdAt = if (createdMs > 0L) Timestamp(Date(createdMs)) else null,
            updatedAt = if (updatedMs > 0L) Timestamp(Date(updatedMs)) else null
        )
    }

    fun messageToJson(m: RpMessage): JSONObject = JSONObject().apply {
        put("id", m.id)
        put("userId", m.userId)
        put("chatId", m.chatId)
        put("orderIndex", m.orderIndex)
        put("role", m.role)
        put("speakerName", m.speakerName)
        put("messageKind", m.messageKind)
        put("modelUsed", m.modelUsed)
        put("sceneArtUri", m.sceneArtUri)
        put("agentTrace", JSONArray(m.agentTrace))
        put("content", m.activeContent())
        put("variants", JSONArray(m.effectiveVariants()))
        put("variantModels", JSONArray(m.variantModels))
        put("activeVariantIndex", m.effectiveActiveIndex())
        put("createdAtEpochMs", m.createdAt?.toDate()?.time ?: 0L)
        put("updatedAtEpochMs", m.updatedAt?.toDate()?.time ?: 0L)
    }

    fun messageFromJson(m: JSONObject, fallbackOrder: Long = 0L): RpMessage {
        val content = m.optString("content").trim()
        val variants = jsonArrayToStrings(m.optJSONArray("variants")).take(12)
        val effectiveContent = content.ifEmpty { variants.firstOrNull().orEmpty() }
        val finalVariants = if (variants.isNotEmpty()) variants else listOf(effectiveContent)
        val activeIdx = m.optLong("activeVariantIndex", 0L)
            .coerceIn(0L, (finalVariants.size - 1).coerceAtLeast(0).toLong())
        val createdMs = m.optLong("createdAtEpochMs", 0L)
        val updatedMs = m.optLong("updatedAtEpochMs", 0L)
        return RpMessage(
            id = m.optString("id", ""),
            userId = m.optString("userId", ""),
            chatId = m.optString("chatId", ""),
            role = m.optString("role", "user"),
            speakerName = m.optString("speakerName", ""),
            content = effectiveContent,
            variants = finalVariants,
            variantModels = jsonArrayToStrings(m.optJSONArray("variantModels")).take(12),
            activeVariantIndex = activeIdx,
            messageKind = MessageKind.fromString(m.optString("messageKind")).name,
            modelUsed = m.optString("modelUsed", ""),
            sceneArtUri = m.optString("sceneArtUri", "").take(500),
            agentTrace = jsonArrayToStrings(m.optJSONArray("agentTrace")).take(16),
            orderIndex = m.optLong("orderIndex", fallbackOrder),
            createdAt = if (createdMs > 0L) Timestamp(Date(createdMs)) else null,
            updatedAt = if (updatedMs > 0L) Timestamp(Date(updatedMs)) else null
        )
    }

    fun chatMetaToJson(chat: RpChat): JSONObject = JSONObject().apply {
        put("id", chat.id)
        put("userId", chat.userId)
        put("scenarioId", chat.scenarioId)
        put("rootChatId", chat.rootChatId)
        put("parentChatId", chat.parentChatId)
        put("branchLabel", chat.branchLabel)
        put("branchPointOrder", chat.branchPointOrder)
        put("title", chat.title)
        put("rpMode", chat.rpMode)
        put("characterName", chat.characterName)
        put("userPersonaName", chat.userPersonaName)
        put("userPersonaDesc", chat.userPersonaDesc)
        put("systemPrompt", chat.systemPrompt)
        put("authorsNote", chat.authorsNote)
        put("memorySummary", chat.memorySummary)
        put("loreNotes", JSONArray(chat.loreNotes))
        put("loreEntries", loreEntriesToJson(chat.loreEntries))
        put("rpgState", rpgStateToJson(chat.rpgState))
        put("agentSetupJson", chat.agentSetupJson)
        put("participants", JSONArray(chat.participants))
        put("provider", chat.provider)
        put("modelId", chat.modelId)
        put("portraitUri", chat.portraitUri)
        put("backdropUri", chat.backdropUri)
        put("lastMessagePreview", chat.lastMessagePreview)
        put("messageCount", chat.messageCount)
        put("pinned", chat.pinned)
        put("createdAtEpochMs", chat.createdAt?.toDate()?.time ?: 0L)
        put("updatedAtEpochMs", chat.updatedAt?.toDate()?.time ?: 0L)
        put("generationParams", JSONObject().apply {
            put("temperature", chat.temperature)
            put("topP", chat.topP)
            put("topK", chat.topK)
            put("maxTokens", chat.maxTokens)
            put("contextWindowMessages", chat.contextWindowMessages)
            put("presencePenalty", chat.presencePenalty)
            put("frequencyPenalty", chat.frequencyPenalty)
            put("stopSequences", JSONArray(chat.stopSequences))
        })
    }

    fun chatToJson(chat: RpChat, messages: List<RpMessage>): JSONObject =
        chatMetaToJson(chat).apply {
            put("messages", JSONArray().apply {
                messages.forEach { m -> put(messageToJson(m)) }
            })
        }

    fun chatMetaFromJson(obj: JSONObject): RpChat {
        val gen = obj.optJSONObject("generationParams")
        val createdMs = obj.optLong("createdAtEpochMs", 0L)
        val updatedMs = obj.optLong("updatedAtEpochMs", 0L)
        return RpChat(
            id = obj.optString("id", ""),
            userId = obj.optString("userId", ""),
            scenarioId = obj.optString("scenarioId", "imported"),
            rootChatId = obj.optString("rootChatId", ""),
            parentChatId = obj.optString("parentChatId", ""),
            branchLabel = obj.optString("branchLabel", ""),
            branchPointOrder = obj.optLong("branchPointOrder", 0L).coerceAtLeast(0L),
            title = obj.optString("title", "Импортированный чат").ifBlank { "Импортированный чат" },
            rpMode = RpMode.fromString(obj.optString("rpMode")).name,
            systemPrompt = obj.optString("systemPrompt", "Ты — ролевой партнёр."),
            characterName = obj.optString("characterName", "Персонаж").ifBlank { "Персонаж" },
            userPersonaName = obj.optString("userPersonaName", "Странник").ifBlank { "Странник" },
            userPersonaDesc = obj.optString("userPersonaDesc", ""),
            authorsNote = obj.optString("authorsNote", ""),
            memorySummary = obj.optString("memorySummary", ""),
            loreNotes = jsonArrayToStrings(obj.optJSONArray("loreNotes")),
            loreEntries = parseLoreEntries(obj.optJSONArray("loreEntries")),
            rpgState = parseRpgState(obj.optJSONObject("rpgState")),
            agentSetupJson = obj.optString("agentSetupJson", "").take(32000),
            participants = jsonArrayToStrings(obj.optJSONArray("participants")),
            provider = LlmProvider.fromString(obj.optString("provider")).name,
            modelId = obj.optString("modelId", "gemini-3.8-flash").ifBlank { "gemini-3.8-flash" },
            temperature = (gen?.optDouble("temperature") ?: obj.optDouble("temperature", 0.85)).coerceIn(0.0, 2.0),
            topP = (gen?.optDouble("topP") ?: obj.optDouble("topP", 0.95)).coerceIn(0.05, 1.0),
            topK = (gen?.optLong("topK") ?: obj.optLong("topK", 40L)).coerceIn(1L, 200L),
            maxTokens = (gen?.optLong("maxTokens") ?: obj.optLong("maxTokens", 2048L)).coerceIn(64L, 16384L),
            contextWindowMessages = (gen?.optLong("contextWindowMessages") ?: 24L).coerceIn(2L, 200L),
            presencePenalty = (gen?.optDouble("presencePenalty") ?: 0.0).coerceIn(-2.0, 2.0),
            frequencyPenalty = (gen?.optDouble("frequencyPenalty") ?: 0.0).coerceIn(-2.0, 2.0),
            stopSequences = jsonArrayToStrings(gen?.optJSONArray("stopSequences")),
            portraitUri = obj.optString("portraitUri", "").take(500),
            backdropUri = obj.optString("backdropUri", "").take(500),
            lastMessagePreview = obj.optString("lastMessagePreview", "").take(300),
            messageCount = obj.optLong("messageCount", 0L).coerceAtLeast(0L),
            pinned = obj.optBoolean("pinned", false),
            createdAt = if (createdMs > 0L) Timestamp(Date(createdMs)) else null,
            updatedAt = if (updatedMs > 0L) Timestamp(Date(updatedMs)) else null
        )
    }

    fun parseImportedChats(jsonString: String): Result<List<ImportedChatData>> {
        return try {
            val root = JSONObject(jsonString)
            val result = mutableListOf<ImportedChatData>()
            if (root.has("chat")) {
                result.add(parseChatObject(root.getJSONObject("chat")))
            } else if (root.has("chats")) {
                val arr = root.getJSONArray("chats")
                for (i in 0 until arr.length()) {
                    result.add(parseChatObject(arr.getJSONObject(i)))
                }
            } else if (root.has("systemPrompt") || root.has("messages")) {
                result.add(parseChatObject(root))
            } else {
                return Result.failure(IllegalArgumentException("Неизвестный формат JSON."))
            }
            if (result.isEmpty()) {
                Result.failure(IllegalArgumentException("В файле не найдено чатов для импорта."))
            } else {
                Result.success(result)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun parseImportedScenarios(jsonString: String): Result<List<RpScenario>> {
        return try {
            val root = JSONObject(jsonString)
            val out = mutableListOf<RpScenario>()
            if (root.has("scenarios")) {
                val arr = root.getJSONArray("scenarios")
                for (i in 0 until arr.length()) {
                    val obj = arr.optJSONObject(i) ?: continue
                    if (obj.has("rpMode") && obj.has("systemPrompt") && obj.has("title")) {
                        out.add(scenarioFromJson(obj))
                    } else {
                        SillyTavernCardParser.parseJsonString(obj.toString(), "LoreCraft Pack")
                            .onSuccess { out.add(it.scenario) }
                    }
                }
            } else if (root.has("rpMode") && root.has("systemPrompt") && root.has("title")) {
                out.add(scenarioFromJson(root))
            } else {
                SillyTavernCardParser.parseJsonString(jsonString, "JSON")
                    .onSuccess { out.add(it.scenario) }
            }
            if (out.isEmpty()) Result.failure(IllegalArgumentException("Сценарии не найдены в JSON."))
            else Result.success(out)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun parseLoreEntries(arr: JSONArray?): List<LoreEntry> {
        if (arr == null) return emptyList()
        val list = mutableListOf<LoreEntry>()
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            list.add(
                LoreEntry(
                    id = o.optString("id").ifBlank { UUID.randomUUID().toString().take(16) },
                    title = o.optString("title", ""),
                    keys = jsonArrayToStrings(o.optJSONArray("keys")),
                    content = o.optString("content", ""),
                    alwaysActive = o.optBoolean("alwaysActive", false),
                    useRegex = o.optBoolean("useRegex", false),
                    priority = o.optLong("priority", 10L).coerceIn(1L, 100L),
                    enabled = o.optBoolean("enabled", true)
                )
            )
        }
        return list.take(40)
    }

    private fun parseLongMap(obj: JSONObject?, maxItems: Int = 16): Map<String, Long> {
        if (obj == null) return emptyMap()
        val out = mutableMapOf<String, Long>()
        val keys = obj.keys()
        while (keys.hasNext() && out.size < maxItems) {
            val k = keys.next().trim()
            if (k.isNotEmpty()) out[k] = obj.optLong(k, 0L)
        }
        return out
    }

    private fun parseStringMap(obj: JSONObject?, maxItems: Int = 12): Map<String, String> {
        if (obj == null) return emptyMap()
        val out = mutableMapOf<String, String>()
        val keys = obj.keys()
        while (keys.hasNext() && out.size < maxItems) {
            val k = keys.next().trim()
            if (k.isNotEmpty()) out[k] = obj.optString(k, "").take(120)
        }
        return out
    }

    private fun parseRpgState(obj: JSONObject?): RpgState {
        if (obj == null) return RpgState()
        val statsMap = parseLongMap(obj.optJSONObject("stats"), 16)
        val locsArr = obj.optJSONArray("locations")
        val locations = mutableListOf<WorldLocationNode>()
        if (locsArr != null) {
            for (i in 0 until locsArr.length()) {
                val l = locsArr.optJSONObject(i) ?: continue
                if (locations.size >= 24) break
                locations.add(
                    WorldLocationNode(
                        id = l.optString("id").ifBlank { "loc_${i + 1}" }.take(64),
                        name = l.optString("name", "Локация").take(80),
                        description = l.optString("description", "").take(400),
                        connections = jsonArrayToStrings(l.optJSONArray("connections")).take(10),
                        visited = l.optBoolean("visited", false),
                        backdropUri = l.optString("backdropUri", "").take(500),
                        posX = l.optDouble("posX", -1.0),
                        posY = l.optDouble("posY", -1.0),
                        dangerLevel = l.optString("dangerLevel", "").take(60),
                        shopItems = jsonArrayToStrings(l.optJSONArray("shopItems")).take(20)
                    )
                )
            }
        }
        val combatObj = obj.optJSONObject("combat")
        val combatants = mutableListOf<Combatant>()
        val combArr = combatObj?.optJSONArray("combatants")
        if (combArr != null) {
            for (i in 0 until combArr.length()) {
                val c = combArr.optJSONObject(i) ?: continue
                if (combatants.size >= 16) break
                combatants.add(
                    Combatant(
                        id = c.optString("id").ifBlank { "c_${i + 1}" }.take(64),
                        name = c.optString("name", "Боец").take(80),
                        hp = c.optLong("hp", 12L).coerceIn(0L, 99999L),
                        maxHp = c.optLong("maxHp", 12L).coerceIn(1L, 99999L),
                        initiative = c.optLong("initiative", 10L).coerceIn(-20L, 100L),
                        enemy = c.optBoolean("enemy", true),
                        notes = c.optString("notes", "").take(120)
                    )
                )
            }
        }
        val combatState = CombatState(
            active = combatObj?.optBoolean("active", false) ?: false,
            round = (combatObj?.optLong("round", 1L) ?: 1L).coerceIn(1L, 999L),
            turnIndex = (combatObj?.optLong("turnIndex", 0L) ?: 0L).coerceIn(0L, 32L),
            combatants = combatants
        )

        val ppObj = obj.optJSONObject("playerProfile")
        val playerProfile = if (ppObj == null) PlayerProfile() else PlayerProfile(
            race = ppObj.optString("race", "Человек").take(60),
            charClass = ppObj.optString("charClass", "Странник").take(60),
            origin = ppObj.optString("origin", "").take(500),
            appearance = ppObj.optString("appearance", "").take(800),
            speechStyle = ppObj.optString("speechStyle", "").take(300),
            equipmentSlots = parseStringMap(ppObj.optJSONObject("equipmentSlots"), 10)
                .ifEmpty { PlayerProfile().equipmentSlots },
            perks = jsonArrayToStrings(ppObj.optJSONArray("perks")).take(24),
            factionReputation = parseLongMap(ppObj.optJSONObject("factionReputation"), 16),
            statusDurations = parseLongMap(ppObj.optJSONObject("statusDurations"), 20)
        )

        val envObj = obj.optJSONObject("worldEnv")
        val worldEnv = if (envObj == null) WorldEnvironment() else WorldEnvironment(
            timeOfDay = envObj.optString("timeOfDay", "Сумерки").take(40),
            dayCount = envObj.optLong("dayCount", 1L).coerceIn(1L, 99999L),
            weather = envObj.optString("weather", "Ясно").take(60),
            worldLaws = envObj.optString("worldLaws", "").take(600),
            economyNotes = envObj.optString("economyNotes", "").take(400)
        )

        val npcsArr = obj.optJSONArray("npcs")
        val npcs = mutableListOf<NpcDossier>()
        if (npcsArr != null) {
            for (i in 0 until npcsArr.length()) {
                val n = npcsArr.optJSONObject(i) ?: continue
                if (npcs.size >= 30) break
                npcs.add(
                    NpcDossier(
                        id = n.optString("id").ifBlank { "npc_${i + 1}" }.take(64),
                        name = n.optString("name", "Незнакомец").take(80),
                        role = n.optString("role", "").take(100),
                        faction = n.optString("faction", "").take(80),
                        disposition = n.optLong("disposition", 0L).coerceIn(-100L, 100L),
                        mood = n.optString("mood", "Нейтральное").take(60),
                        appearance = n.optString("appearance", "").take(400),
                        stats = parseLongMap(n.optJSONObject("stats"), 12),
                        inventory = jsonArrayToStrings(n.optJSONArray("inventory")).take(20),
                        secrets = jsonArrayToStrings(n.optJSONArray("secrets")).take(12),
                        revealedSecretCount = n.optLong("revealedSecretCount", 0L).coerceIn(0L, 12L),
                        statsRevealed = n.optBoolean("statsRevealed", false),
                        perceptionDc = n.optLong("perceptionDc", 13L).coerceIn(5L, 30L),
                        trustThreshold = n.optLong("trustThreshold", 45L).coerceIn(-100L, 100L),
                        portraitUri = n.optString("portraitUri", "").take(500),
                        notes = n.optString("notes", "").take(500)
                    )
                )
            }
        }

        val questsArr = obj.optJSONArray("quests")
        val quests = mutableListOf<QuestItem>()
        if (questsArr != null) {
            for (i in 0 until questsArr.length()) {
                val q = questsArr.optJSONObject(i) ?: continue
                if (quests.size >= 24) break
                quests.add(
                    QuestItem(
                        id = q.optString("id").ifBlank { "q_${i + 1}" }.take(64),
                        title = q.optString("title", "Задание").take(120),
                        description = q.optString("description", "").take(500),
                        stages = jsonArrayToStrings(q.optJSONArray("stages")).take(12),
                        completedStages = q.optLong("completedStages", 0L).coerceIn(0L, 12L),
                        completed = q.optBoolean("completed", false),
                        rewardText = q.optString("rewardText", "").take(200)
                    )
                )
            }
        }

        val chronArr = obj.optJSONArray("chronicle")
        val chronicle = mutableListOf<ChronicleEntry>()
        if (chronArr != null) {
            for (i in 0 until chronArr.length()) {
                val c = chronArr.optJSONObject(i) ?: continue
                if (chronicle.size >= 40) break
                chronicle.add(
                    ChronicleEntry(
                        id = c.optString("id").ifBlank { "ch_${i + 1}" }.take(64),
                        turnNumber = c.optLong("turnNumber", 0L).coerceAtLeast(0L),
                        title = c.optString("title", "Событие").take(120),
                        summary = c.optString("summary", "").take(500),
                        tags = jsonArrayToStrings(c.optJSONArray("tags")).take(8)
                    )
                )
            }
        }

        return RpgState(
            enabled = obj.optBoolean("enabled", false),
            level = obj.optLong("level", 1L).coerceIn(1L, 999L),
            hp = obj.optLong("hp", 20L).coerceIn(0L, 99999L),
            maxHp = obj.optLong("maxHp", 20L).coerceIn(1L, 99999L),
            mp = obj.optLong("mp", 10L).coerceIn(0L, 99999L),
            maxMp = obj.optLong("maxMp", 10L).coerceIn(0L, 99999L),
            gold = obj.optLong("gold", 50L).coerceIn(0L, 9999999L),
            stats = statsMap.ifEmpty { RpgState().stats },
            inventory = jsonArrayToStrings(obj.optJSONArray("inventory")).take(40),
            statuses = jsonArrayToStrings(obj.optJSONArray("statuses")).take(20),
            activeQuest = obj.optString("activeQuest", "").take(500),
            currentLocationId = obj.optString("currentLocationId", "").take(80),
            locations = locations,
            combat = combatState,
            playerProfile = playerProfile,
            worldEnv = worldEnv,
            npcs = npcs,
            quests = quests,
            chronicle = chronicle
        )
    }

    private fun parseChatObject(obj: JSONObject): ImportedChatData {
        val chat = chatMetaFromJson(obj)
        val msgsArr = obj.optJSONArray("messages") ?: JSONArray()
        val msgs = mutableListOf<RpMessage>()
        for (i in 0 until msgsArr.length()) {
            val m = msgsArr.optJSONObject(i) ?: continue
            val parsed = messageFromJson(m, fallbackOrder = i.toLong())
            if (parsed.activeContent().isNotEmpty()) {
                msgs.add(parsed)
            }
        }
        return ImportedChatData(chat, msgs)
    }

    private fun jsonArrayToStrings(arr: JSONArray?): List<String> {
        if (arr == null) return emptyList()
        return (0 until arr.length()).mapNotNull { i ->
            arr.optString(i)?.takeIf { it.isNotBlank() }
        }
    }

    fun writeJsonToUri(context: Context, uri: Uri, jsonContent: String): Result<Unit> {
        return try {
            context.contentResolver.openOutputStream(uri)?.use { out ->
                out.write(jsonContent.toByteArray(Charsets.UTF_8))
            } ?: return Result.failure(IllegalStateException("Не удалось открыть файл для записи."))
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun readTextFromUri(context: Context, uri: Uri): Result<String> {
        return try {
            val text = context.contentResolver.openInputStream(uri)?.use { input ->
                input.bufferedReader(Charsets.UTF_8).readText()
            } ?: return Result.failure(IllegalStateException("Не удалось прочитать файл."))
            Result.success(text)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun buildShareIntent(context: Context, fileName: String, jsonContent: String): Intent {
        val exportDir = File(context.cacheDir, "exports").apply { mkdirs() }
        val file = File(exportDir, fileName)
        file.writeText(jsonContent, Charsets.UTF_8)
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        return Intent(Intent.ACTION_SEND).apply {
            type = "application/json"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, fileName)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }
}
