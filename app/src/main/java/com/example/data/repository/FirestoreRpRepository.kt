package com.example.data.repository

import android.content.Context
import com.example.R
import com.example.data.firebase.OperationType
import com.example.data.firebase.handleFirestoreError
import com.example.data.model.GenerationParams
import com.example.data.model.LlmProvider
import com.example.data.model.LoreEntry
import com.example.data.model.MessageKind
import com.example.data.model.PresetScenarios
import com.example.data.model.RpChat
import com.example.data.model.RpMessage
import com.example.data.model.RpMode
import com.example.data.model.RpScenario
import com.example.data.model.RpgState
import com.example.data.model.UserPersona
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await
import java.util.UUID

data class ExportWorkspaceBundle(
    val scenarios: List<RpScenario>,
    val personas: List<UserPersona>,
    val chatsWithMessages: List<Pair<RpChat, List<RpMessage>>>
)

class FirestoreRpRepository(
    private val db: FirebaseFirestore,
    private val auth: FirebaseAuth = Firebase.auth
) : RpDataRepository {
    constructor(context: Context) : this(
        FirebaseFirestore.getInstance(
            context.applicationContext.getString(R.string.firestore_database_id)
        )
    )

    private fun requireUid(expectedUserId: String? = null): String {
        val uid = auth.currentUser?.uid
            ?: throw IllegalStateException("Пользователь должен войти через Google.")
        if (expectedUserId != null) {
            check(uid == expectedUserId) { "Сменился аккаунт; запись отклонена." }
        }
        return uid
    }

    private fun sanitizeId(raw: String?): String {
        val cleaned = raw.orEmpty().replace(Regex("[^a-zA-Z0-9_\\-]"), "_")
        return if (cleaned.isNotBlank()) cleaned.take(128) else UUID.randomUUID().toString()
    }

    private fun sanitizeStringList(items: List<String>, maxItems: Int, maxLen: Int): List<String> =
        items.map { it.trim().take(maxLen) }.filter { it.isNotEmpty() }.take(maxItems)

    private fun serializeLoreEntries(entries: List<LoreEntry>): List<Map<String, Any>> =
        entries.take(40).map { entry ->
            mapOf(
                "id" to entry.id.ifBlank { UUID.randomUUID().toString().take(16) }.take(64),
                "title" to entry.title.trim().take(100),
                "keys" to sanitizeStringList(entry.keys, 20, 80),
                "content" to entry.content.trim().take(1500),
                "alwaysActive" to entry.alwaysActive,
                "useRegex" to entry.useRegex,
                "priority" to entry.priority.coerceIn(1L, 100L),
                "enabled" to entry.enabled
            )
        }

    private fun serializeRpgState(state: RpgState): Map<String, Any> {
        val safeMaxHp = state.maxHp.coerceIn(1L, 99999L)
        val safeMaxMp = state.maxMp.coerceIn(0L, 99999L)
        val safeStats = state.stats.entries
            .filter { it.key.isNotBlank() }
            .take(16)
            .associate { it.key.trim().take(32) to it.value.coerceIn(-999L, 9999L) }
        val safeLocations = state.locations.take(24).map { loc ->
            mapOf(
                "id" to loc.id.ifBlank { UUID.randomUUID().toString().take(12) }.take(64),
                "name" to loc.name.trim().ifEmpty { "Локация" }.take(80),
                "description" to loc.description.trim().take(400),
                "connections" to sanitizeStringList(loc.connections, 10, 64),
                "visited" to loc.visited,
                "backdropUri" to loc.backdropUri.trim().take(500),
                "posX" to loc.posX.coerceIn(-1.0, 1.0),
                "posY" to loc.posY.coerceIn(-1.0, 1.0),
                "dangerLevel" to loc.dangerLevel.trim().take(40),
                "shopItems" to sanitizeStringList(loc.shopItems, 16, 120)
            )
        }
        val safeCombat = mapOf(
            "active" to state.combat.active,
            "round" to state.combat.round.coerceIn(1L, 999L),
            "turnIndex" to state.combat.turnIndex.coerceIn(0L, 32L),
            "combatants" to state.combat.combatants.take(16).map { c ->
                val cMaxHp = c.maxHp.coerceIn(1L, 99999L)
                mapOf(
                    "id" to c.id.ifBlank { UUID.randomUUID().toString().take(12) }.take(64),
                    "name" to c.name.trim().ifEmpty { "Боец" }.take(80),
                    "hp" to c.hp.coerceIn(0L, cMaxHp),
                    "maxHp" to cMaxHp,
                    "initiative" to c.initiative.coerceIn(-20L, 100L),
                    "enemy" to c.enemy,
                    "notes" to c.notes.trim().take(120)
                )
            }
        )
        val safePlayerProfile = mapOf(
            "race" to state.playerProfile.race.trim().ifEmpty { "Человек" }.take(60),
            "charClass" to state.playerProfile.charClass.trim().ifEmpty { "Странник" }.take(60),
            "origin" to state.playerProfile.origin.trim().take(400),
            "appearance" to state.playerProfile.appearance.trim().take(500),
            "speechStyle" to state.playerProfile.speechStyle.trim().take(300),
            "equipmentSlots" to state.playerProfile.equipmentSlots.entries
                .filter { it.key.isNotBlank() }
                .take(8)
                .associate { it.key.trim().take(32) to it.value.trim().take(120) },
            "perks" to sanitizeStringList(state.playerProfile.perks, 24, 100),
            "factionReputation" to state.playerProfile.factionReputation.entries
                .filter { it.key.isNotBlank() }
                .take(16)
                .associate { it.key.trim().take(60) to it.value.coerceIn(-100L, 100L) },
            "statusDurations" to state.playerProfile.statusDurations.entries
                .filter { it.key.isNotBlank() }
                .take(20)
                .associate { it.key.trim().take(80) to it.value.coerceIn(0L, 999L) }
        )
        val safeWorldEnv = mapOf(
            "timeOfDay" to state.worldEnv.timeOfDay.trim().ifEmpty { "Сумерки" }.take(60),
            "dayCount" to state.worldEnv.dayCount.coerceIn(1L, 99999L),
            "weather" to state.worldEnv.weather.trim().ifEmpty { "Ясно" }.take(80),
            "worldLaws" to state.worldEnv.worldLaws.trim().take(800),
            "economyNotes" to state.worldEnv.economyNotes.trim().take(400)
        )
        val safeNpcs = state.npcs.take(24).map { npc ->
            mapOf(
                "id" to npc.id.ifBlank { UUID.randomUUID().toString().take(12) }.take(64),
                "name" to npc.name.trim().ifEmpty { "Незнакомец" }.take(80),
                "role" to npc.role.trim().take(80),
                "faction" to npc.faction.trim().take(80),
                "disposition" to npc.disposition.coerceIn(-100L, 100L),
                "mood" to npc.mood.trim().take(60),
                "appearance" to npc.appearance.trim().take(400),
                "stats" to npc.stats.entries.filter { it.key.isNotBlank() }.take(10)
                    .associate { it.key.trim().take(32) to it.value.coerceIn(-99L, 999L) },
                "inventory" to sanitizeStringList(npc.inventory, 20, 120),
                "secrets" to sanitizeStringList(npc.secrets, 8, 300),
                "revealedSecretCount" to npc.revealedSecretCount.coerceIn(0L, 8L),
                "statsRevealed" to npc.statsRevealed,
                "perceptionDc" to npc.perceptionDc.coerceIn(1L, 35L),
                "trustThreshold" to npc.trustThreshold.coerceIn(-100L, 100L),
                "portraitUri" to npc.portraitUri.trim().take(500),
                "notes" to npc.notes.trim().take(500)
            )
        }
        val safeQuests = state.quests.take(20).map { q ->
            mapOf(
                "id" to q.id.ifBlank { UUID.randomUUID().toString().take(12) }.take(64),
                "title" to q.title.trim().ifEmpty { "Задание" }.take(120),
                "description" to q.description.trim().take(500),
                "stages" to sanitizeStringList(q.stages, 10, 160),
                "completedStages" to q.completedStages.coerceIn(0L, 10L),
                "completed" to q.completed,
                "rewardText" to q.rewardText.trim().take(200)
            )
        }
        val safeChronicle = state.chronicle.take(40).map { ch ->
            mapOf(
                "id" to ch.id.ifBlank { UUID.randomUUID().toString().take(12) }.take(64),
                "turnNumber" to ch.turnNumber.coerceIn(0L, 99999L),
                "title" to ch.title.trim().ifEmpty { "Событие" }.take(120),
                "summary" to ch.summary.trim().take(600),
                "tags" to sanitizeStringList(ch.tags, 8, 40)
            )
        }
        return mapOf(
            "enabled" to state.enabled,
            "level" to state.level.coerceIn(1L, 999L),
            "hp" to state.hp.coerceIn(0L, safeMaxHp),
            "maxHp" to safeMaxHp,
            "mp" to state.mp.coerceIn(0L, safeMaxMp),
            "maxMp" to safeMaxMp,
            "gold" to state.gold.coerceIn(0L, 9999999L),
            "stats" to safeStats,
            "inventory" to sanitizeStringList(state.inventory, 40, 120),
            "statuses" to sanitizeStringList(state.statuses, 20, 80),
            "activeQuest" to state.activeQuest.trim().take(500),
            "currentLocationId" to state.currentLocationId.trim().take(80),
            "locations" to safeLocations,
            "combat" to safeCombat,
            "playerProfile" to safePlayerProfile,
            "worldEnv" to safeWorldEnv,
            "npcs" to safeNpcs,
            "quests" to safeQuests,
            "chronicle" to safeChronicle
        )
    }

    // --- Real-time Streams ---

    override fun observeScenarios(userId: String): Flow<List<RpScenario>> = flow {
        val path = "users/$userId/scenarios"
        emitAll(
            db.collection("users").document(userId).collection("scenarios")
                .orderBy("updatedAt", Query.Direction.DESCENDING)
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(
                        RpScenario::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    )
                }
                .catch { error ->
                    val firestoreError = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (firestoreError == null && error is CancellationException) throw error
                    val toLog = firestoreError ?: (error as? Exception)
                    if (toLog != null && auth.currentUser?.uid == userId) {
                        handleFirestoreError(toLog, OperationType.LIST, path)
                    }
                    throw error
                }
        )
    }

    override fun observeChats(userId: String): Flow<List<RpChat>> = flow {
        val path = "users/$userId/chats"
        emitAll(
            db.collection("users").document(userId).collection("chats")
                .orderBy("updatedAt", Query.Direction.DESCENDING)
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(
                        RpChat::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    )
                }
                .catch { error ->
                    val firestoreError = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (firestoreError == null && error is CancellationException) throw error
                    val toLog = firestoreError ?: (error as? Exception)
                    if (toLog != null && auth.currentUser?.uid == userId) {
                        handleFirestoreError(toLog, OperationType.LIST, path)
                    }
                    throw error
                }
        )
    }

    override fun observeChat(userId: String, chatId: String): Flow<RpChat?> = flow {
        val path = "users/$userId/chats/$chatId"
        emitAll(
            db.collection("users").document(userId).collection("chats").document(chatId)
                .snapshots()
                .map { snapshot ->
                    if (snapshot.exists()) {
                        snapshot.toObject(
                            RpChat::class.java,
                            DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                        )
                    } else null
                }
                .catch { error ->
                    val firestoreError = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (firestoreError == null && error is CancellationException) throw error
                    val toLog = firestoreError ?: (error as? Exception)
                    if (toLog != null && auth.currentUser?.uid == userId) {
                        handleFirestoreError(toLog, OperationType.GET, path)
                    }
                    throw error
                }
        )
    }

    override fun observeMessages(userId: String, chatId: String): Flow<List<RpMessage>> = flow {
        val path = "users/$userId/chats/$chatId/messages"
        emitAll(
            db.collection("users").document(userId).collection("chats").document(chatId)
                .collection("messages")
                .orderBy("orderIndex", Query.Direction.ASCENDING)
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(
                        RpMessage::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    )
                }
                .catch { error ->
                    val firestoreError = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (firestoreError == null && error is CancellationException) throw error
                    val toLog = firestoreError ?: (error as? Exception)
                    if (toLog != null && auth.currentUser?.uid == userId) {
                        handleFirestoreError(toLog, OperationType.LIST, path)
                    }
                    throw error
                }
        )
    }

    override fun observePersonas(userId: String): Flow<List<UserPersona>> = flow {
        val path = "users/$userId/personas"
        emitAll(
            db.collection("users").document(userId).collection("personas")
                .orderBy("updatedAt", Query.Direction.DESCENDING)
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(
                        UserPersona::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    )
                }
                .catch { error ->
                    val firestoreError = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (firestoreError == null && error is CancellationException) throw error
                    val toLog = firestoreError ?: (error as? Exception)
                    if (toLog != null && auth.currentUser?.uid == userId) {
                        handleFirestoreError(toLog, OperationType.LIST, path)
                    }
                    throw error
                }
        )
    }

    // --- Seeding Starter Scenarios Once ---

    override suspend fun ensureStarterDataIfEmpty(userId: String): Result<Unit> {
        val scenariosPath = "users/$userId/scenarios"
        return try {
            val uid = requireUid(userId)
            val existingScenarios = db.collection("users").document(uid)
                .collection("scenarios").limit(1).get().await()
            if (existingScenarios.isEmpty) {
                val batch = db.batch()
                PresetScenarios.starterTemplates().forEach { template ->
                    val docRef = db.collection("users").document(uid)
                        .collection("scenarios").document(sanitizeId(template.id))
                    batch.set(docRef, buildScenarioCreatePayload(uid, template))
                }
                val personaRef = db.collection("users").document(uid)
                    .collection("personas").document("default_wanderer")
                batch.set(
                    personaRef,
                    mapOf(
                        "userId" to uid,
                        "name" to "Странник",
                        "description" to "Наблюдательный путешественник с острым умом.",
                        "preferredStyle" to "От третьего лица",
                        "activeDefault" to true,
                        "createdAt" to FieldValue.serverTimestamp(),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                )
                batch.commit().await()
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, scenariosPath)
            Result.failure(e)
        }
    }

    // --- Scenarios CRUD ---

    private fun buildScenarioCreatePayload(uid: String, s: RpScenario): Map<String, Any> {
        val params = s.generationParams()
        return mapOf(
            "userId" to uid,
            "title" to s.title.trim().ifEmpty { "Новый сценарий" }.take(120),
            "subtitle" to s.subtitle.trim().take(200),
            "rpMode" to RpMode.fromString(s.rpMode).name,
            "systemPrompt" to s.systemPrompt.trim().ifEmpty { "Ты — выразительный ролевой партнёр." }.take(20000),
            "characterBio" to s.characterBio.trim().take(8000),
            "firstMessage" to s.firstMessage.trim().take(8000),
            "exampleDialogue" to s.exampleDialogue.trim().take(8000),
            "loreNotes" to sanitizeStringList(s.loreNotes, 30, 500),
            "loreEntries" to serializeLoreEntries(s.loreEntries),
            "defaultRpgState" to serializeRpgState(s.defaultRpgState),
            "defaultAgentSetupJson" to s.defaultAgentSetupJson.trim().take(16000),
            "participants" to sanitizeStringList(s.participants, 12, 100),
            "tags" to sanitizeStringList(s.tags, 10, 40),
            "defaultProvider" to LlmProvider.fromString(s.defaultProvider).name,
            "defaultModelId" to s.defaultModelId.trim().ifEmpty { "gemini-3.8-flash" }.take(120),
            "temperature" to params.temperature,
            "topP" to params.topP,
            "topK" to params.topK.toLong(),
            "maxTokens" to params.maxTokens.toLong(),
            "contextWindowMessages" to params.contextWindowMessages.toLong(),
            "presencePenalty" to params.presencePenalty,
            "frequencyPenalty" to params.frequencyPenalty,
            "stopSequences" to sanitizeStringList(params.stopSequences, 10, 60),
            "avatarIconKey" to s.avatarIconKey.trim().ifEmpty { "chronicler" }.take(50),
            "accentColorHex" to s.accentColorHex.trim().ifEmpty { "#B59CFF" }.take(12),
            "portraitUri" to s.portraitUri.trim().take(500),
            "backdropUri" to s.backdropUri.trim().take(500),
            "pinned" to s.pinned,
            "createdAt" to FieldValue.serverTimestamp(),
            "updatedAt" to FieldValue.serverTimestamp()
        )
    }

    override suspend fun saveScenario(userId: String, scenario: RpScenario): Result<String> {
        val isNew = scenario.id.isBlank()
        val docId = if (isNew) UUID.randomUUID().toString() else sanitizeId(scenario.id)
        val docPath = "users/$userId/scenarios/$docId"
        return try {
            val uid = requireUid(userId)
            val docRef = db.collection("users").document(uid).collection("scenarios").document(docId)
            val payload = buildScenarioCreatePayload(uid, scenario)
            if (isNew) {
                docRef.set(payload).await()
            } else {
                val updateMap = payload.toMutableMap().apply {
                    remove("userId")
                    remove("createdAt")
                }
                docRef.update(updateMap).await()
            }
            Result.success(docId)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, if (isNew) OperationType.CREATE else OperationType.UPDATE, docPath)
            Result.failure(e)
        }
    }

    override suspend fun toggleScenarioPin(userId: String, scenarioId: String, pinned: Boolean): Result<Unit> {
        val docPath = "users/$userId/scenarios/$scenarioId"
        return try {
            val uid = requireUid(userId)
            db.collection("users").document(uid).collection("scenarios").document(scenarioId)
                .update(mapOf("pinned" to pinned, "updatedAt" to FieldValue.serverTimestamp()))
                .await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docPath)
            Result.failure(e)
        }
    }

    override suspend fun deleteScenario(userId: String, scenarioId: String): Result<Unit> {
        val docPath = "users/$userId/scenarios/$scenarioId"
        return try {
            val uid = requireUid(userId)
            db.collection("users").document(uid).collection("scenarios").document(scenarioId)
                .delete().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, docPath)
            Result.failure(e)
        }
    }

    // --- Chats CRUD ---

    override suspend fun createChatFromScenario(
        userId: String,
        scenario: RpScenario,
        userPersona: UserPersona?,
        overrideProvider: LlmProvider?,
        overrideModelId: String?,
        overrideParams: GenerationParams?
    ): Result<String> {
        val chatId = UUID.randomUUID().toString()
        val path = "users/$userId/chats/$chatId"
        return try {
            val uid = requireUid(userId)
            val params = (overrideParams ?: scenario.generationParams()).clamped()
            val provider = overrideProvider ?: scenario.providerEnum()
            val modelId = overrideModelId?.takeIf { it.isNotBlank() } ?: scenario.defaultModelId
            val personaName = userPersona?.name?.takeIf { it.isNotBlank() } ?: "Странник"
            val personaDesc = userPersona?.description.orEmpty()

            val fullSystem = buildString {
                append(scenario.systemPrompt.trim())
                if (scenario.characterBio.isNotBlank()) {
                    append("\n\n[Описание персонажа / мира]\n")
                    append(scenario.characterBio.trim())
                }
                if (scenario.exampleDialogue.isNotBlank()) {
                    append("\n\n[Примеры стиля речи]\n")
                    append(scenario.exampleDialogue.trim())
                }
            }.ifEmpty { "Ты — выразительный ролевой партнёр." }.take(20000)

            val greeting = scenario.firstMessage.trim()
                .replace("{{user}}", personaName)
                .replace("{{char}}", scenario.title)
                .take(32000)

            val hasGreeting = greeting.isNotBlank()
            val chatRef = db.collection("users").document(uid).collection("chats").document(chatId)
            val batch = db.batch()

            val rpgInit = if (scenario.rpModeEnum() == RpMode.GAME_MASTER && !scenario.defaultRpgState.enabled) {
                scenario.defaultRpgState.copy(enabled = true)
            } else {
                scenario.defaultRpgState
            }

            val chatPayload = mapOf(
                "userId" to uid,
                "scenarioId" to (scenario.id.ifBlank { "custom" }).take(128),
                "rootChatId" to chatId,
                "parentChatId" to "",
                "branchLabel" to "Основная линия",
                "branchPointOrder" to 0L,
                "title" to scenario.title.trim().ifEmpty { "Новая ролевая" }.take(140),
                "rpMode" to RpMode.fromString(scenario.rpMode).name,
                "systemPrompt" to fullSystem,
                "characterName" to scenario.title.trim().ifEmpty { "Персонаж" }.take(100),
                "userPersonaName" to personaName.take(100),
                "userPersonaDesc" to personaDesc.take(4000),
                "authorsNote" to "",
                "memorySummary" to "",
                "loreNotes" to sanitizeStringList(scenario.loreNotes, 30, 500),
                "loreEntries" to serializeLoreEntries(scenario.loreEntries),
                "rpgState" to serializeRpgState(rpgInit),
                "agentSetupJson" to scenario.defaultAgentSetupJson.trim().take(16000),
                "participants" to sanitizeStringList(scenario.participants, 12, 100),
                "provider" to provider.name,
                "modelId" to modelId.take(120),
                "temperature" to params.temperature,
                "topP" to params.topP,
                "topK" to params.topK.toLong(),
                "maxTokens" to params.maxTokens.toLong(),
                "contextWindowMessages" to params.contextWindowMessages.toLong(),
                "presencePenalty" to params.presencePenalty,
                "frequencyPenalty" to params.frequencyPenalty,
                "stopSequences" to sanitizeStringList(params.stopSequences, 10, 60),
                "portraitUri" to scenario.portraitUri.trim().take(500),
                "backdropUri" to scenario.backdropUri.trim().take(500),
                "lastMessagePreview" to greeting.take(300),
                "messageCount" to if (hasGreeting) 1L else 0L,
                "pinned" to false,
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            )
            batch.set(chatRef, chatPayload)

            if (hasGreeting) {
                val msgId = UUID.randomUUID().toString()
                val msgRef = chatRef.collection("messages").document(msgId)
                val speaker = scenario.participants.firstOrNull()?.takeIf { it.isNotBlank() }
                    ?: scenario.title.take(100)
                batch.set(
                    msgRef,
                    mapOf(
                        "userId" to uid,
                        "chatId" to chatId,
                        "role" to "assistant",
                        "speakerName" to speaker.take(100),
                        "content" to greeting,
                        "variants" to listOf(greeting),
                        "variantModels" to listOf(modelId.take(120)),
                        "activeVariantIndex" to 0L,
                        "messageKind" to MessageKind.ROLEPLAY.name,
                        "modelUsed" to modelId.take(120),
                        "sceneArtUri" to "",
                        "agentTrace" to emptyList<String>(),
                        "orderIndex" to 0L,
                        "createdAt" to FieldValue.serverTimestamp(),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                )
            }
            batch.commit().await()
            Result.success(chatId)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, path)
            Result.failure(e)
        }
    }

    override suspend fun updateChatConfig(
        userId: String,
        chatId: String,
        title: String,
        rpMode: RpMode,
        systemPrompt: String,
        characterName: String,
        userPersonaName: String,
        userPersonaDesc: String,
        authorsNote: String,
        memorySummary: String,
        loreNotes: List<String>,
        loreEntries: List<LoreEntry>,
        participants: List<String>,
        provider: LlmProvider,
        modelId: String,
        params: GenerationParams,
        pinned: Boolean,
        portraitUri: String,
        backdropUri: String
    ): Result<Unit> {
        val path = "users/$userId/chats/$chatId"
        return try {
            val uid = requireUid(userId)
            val clamped = params.clamped()
            db.collection("users").document(uid).collection("chats").document(chatId)
                .update(
                    mapOf(
                        "title" to title.trim().ifEmpty { "Ролевая сессия" }.take(140),
                        "rpMode" to rpMode.name,
                        "systemPrompt" to systemPrompt.trim().ifEmpty { "Ты — выразительный ролевой партнёр." }.take(20000),
                        "characterName" to characterName.trim().ifEmpty { "Персонаж" }.take(100),
                        "userPersonaName" to userPersonaName.trim().ifEmpty { "Странник" }.take(100),
                        "userPersonaDesc" to userPersonaDesc.trim().take(4000),
                        "authorsNote" to authorsNote.trim().take(4000),
                        "memorySummary" to memorySummary.trim().take(12000),
                        "loreNotes" to sanitizeStringList(loreNotes, 30, 500),
                        "loreEntries" to serializeLoreEntries(loreEntries),
                        "participants" to sanitizeStringList(participants, 12, 100),
                        "provider" to provider.name,
                        "modelId" to modelId.trim().ifEmpty { "gemini-3.8-flash" }.take(120),
                        "temperature" to clamped.temperature,
                        "topP" to clamped.topP,
                        "topK" to clamped.topK.toLong(),
                        "maxTokens" to clamped.maxTokens.toLong(),
                        "contextWindowMessages" to clamped.contextWindowMessages.toLong(),
                        "presencePenalty" to clamped.presencePenalty,
                        "frequencyPenalty" to clamped.frequencyPenalty,
                        "stopSequences" to sanitizeStringList(clamped.stopSequences, 10, 60),
                        "portraitUri" to portraitUri.trim().take(500),
                        "backdropUri" to backdropUri.trim().take(500),
                        "pinned" to pinned,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    override suspend fun updateChatRpgState(
        userId: String,
        chatId: String,
        rpgState: RpgState
    ): Result<Unit> {
        val path = "users/$userId/chats/$chatId"
        return try {
            val uid = requireUid(userId)
            db.collection("users").document(uid).collection("chats").document(chatId)
                .update(
                    mapOf(
                        "rpgState" to serializeRpgState(rpgState),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    override suspend fun updateChatAgentSetup(
        userId: String,
        chatId: String,
        agentSetupJson: String
    ): Result<Unit> {
        val path = "users/$userId/chats/$chatId"
        return try {
            val uid = requireUid(userId)
            db.collection("users").document(uid).collection("chats").document(chatId)
                .update(
                    mapOf(
                        "agentSetupJson" to agentSetupJson.trim().take(16000),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    override suspend fun updateAllChatsProvider(
        userId: String,
        provider: LlmProvider,
        modelId: String
    ): Result<Int> {
        val path = "users/$userId/chats"
        return try {
            val uid = requireUid(userId)
            val safeModel = modelId.trim().ifEmpty {
                provider.suggestedModels.firstOrNull() ?: "openai"
            }.take(120)
            val docs = db.collection("users").document(uid).collection("chats").get().await().documents
            if (docs.isEmpty()) return Result.success(0)
            docs.chunked(350).forEach { chunk ->
                val batch = db.batch()
                chunk.forEach { doc ->
                    batch.update(
                        doc.reference,
                        mapOf(
                            "provider" to provider.name,
                            "modelId" to safeModel,
                            "updatedAt" to FieldValue.serverTimestamp()
                        )
                    )
                }
                batch.commit().await()
            }
            Result.success(docs.size)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    override suspend fun toggleChatPin(userId: String, chatId: String, pinned: Boolean): Result<Unit> {
        val path = "users/$userId/chats/$chatId"
        return try {
            val uid = requireUid(userId)
            db.collection("users").document(uid).collection("chats").document(chatId)
                .update(mapOf("pinned" to pinned, "updatedAt" to FieldValue.serverTimestamp()))
                .await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    override suspend fun deleteChat(userId: String, chatId: String): Result<Unit> {
        val path = "users/$userId/chats/$chatId"
        return try {
            val uid = requireUid(userId)
            val chatRef = db.collection("users").document(uid).collection("chats").document(chatId)
            val msgs = chatRef.collection("messages").get().await()
            val docs = msgs.documents
            docs.chunked(400).forEach { chunk ->
                val batch = db.batch()
                chunk.forEach { batch.delete(it.reference) }
                batch.commit().await()
            }
            chatRef.delete().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, path)
            Result.failure(e)
        }
    }

    override suspend fun branchChatFromMessage(
        userId: String,
        sourceChat: RpChat,
        messagesUpToTarget: List<RpMessage>,
        customBranchLabel: String
    ): Result<String> {
        val newChatId = UUID.randomUUID().toString()
        val path = "users/$userId/chats/$newChatId"
        return try {
            val uid = requireUid(userId)
            val params = sourceChat.generationParams()
            val newChatRef = db.collection("users").document(uid).collection("chats").document(newChatId)
            val lastMsg = messagesUpToTarget.lastOrNull()
            val rootId = sourceChat.rootChatId.ifBlank { sourceChat.id }.take(128)
            val branchPoint = lastMsg?.orderIndex ?: 0L
            val label = customBranchLabel.trim().ifEmpty {
                "Развилка #${branchPoint + 1}"
            }.take(80)
            val branchTitle = "${sourceChat.title.substringBefore(" • ").take(95)} • $label".take(140)

            val batch = db.batch()
            batch.set(
                newChatRef,
                mapOf(
                    "userId" to uid,
                    "scenarioId" to sourceChat.scenarioId.ifBlank { "custom" }.take(128),
                    "rootChatId" to rootId,
                    "parentChatId" to sourceChat.id.take(128),
                    "branchLabel" to label,
                    "branchPointOrder" to branchPoint,
                    "title" to branchTitle,
                    "rpMode" to RpMode.fromString(sourceChat.rpMode).name,
                    "systemPrompt" to sourceChat.systemPrompt.take(20000),
                    "characterName" to sourceChat.characterName.take(100),
                    "userPersonaName" to sourceChat.userPersonaName.take(100),
                    "userPersonaDesc" to sourceChat.userPersonaDesc.take(4000),
                    "authorsNote" to sourceChat.authorsNote.take(4000),
                    "memorySummary" to sourceChat.memorySummary.take(12000),
                    "loreNotes" to sanitizeStringList(sourceChat.loreNotes, 30, 500),
                    "loreEntries" to serializeLoreEntries(sourceChat.loreEntries),
                    "rpgState" to serializeRpgState(sourceChat.rpgState),
                    "agentSetupJson" to sourceChat.agentSetupJson.trim().take(16000),
                    "participants" to sanitizeStringList(sourceChat.participants, 12, 100),
                    "provider" to LlmProvider.fromString(sourceChat.provider).name,
                    "modelId" to sourceChat.modelId.take(120),
                    "temperature" to params.temperature,
                    "topP" to params.topP,
                    "topK" to params.topK.toLong(),
                    "maxTokens" to params.maxTokens.toLong(),
                    "contextWindowMessages" to params.contextWindowMessages.toLong(),
                    "presencePenalty" to params.presencePenalty,
                    "frequencyPenalty" to params.frequencyPenalty,
                    "stopSequences" to sanitizeStringList(params.stopSequences, 10, 60),
                    "portraitUri" to sourceChat.portraitUri.trim().take(500),
                    "backdropUri" to sourceChat.backdropUri.trim().take(500),
                    "lastMessagePreview" to (lastMsg?.activeContent()?.take(300).orEmpty()),
                    "messageCount" to messagesUpToTarget.size.toLong(),
                    "pinned" to false,
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            )

            messagesUpToTarget.forEachIndexed { idx, msg ->
                val msgRef = newChatRef.collection("messages").document(UUID.randomUUID().toString())
                val vars = sanitizeStringList(msg.effectiveVariants(), 12, 32000)
                val safeActive = msg.activeContent().trim().ifEmpty { "..." }.take(32000)
                val finalVars = if (vars.isNotEmpty()) vars else listOf(safeActive)
                val activeIdx = msg.effectiveActiveIndex().coerceIn(0, (finalVars.size - 1).coerceAtLeast(0)).toLong()
                batch.set(
                    msgRef,
                    mapOf(
                        "userId" to uid,
                        "chatId" to newChatId,
                        "role" to msg.role,
                        "speakerName" to msg.speakerName.take(100),
                        "content" to safeActive,
                        "variants" to finalVars,
                        "variantModels" to sanitizeStringList(msg.variantModels, 12, 120),
                        "activeVariantIndex" to activeIdx,
                        "messageKind" to MessageKind.fromString(msg.messageKind).name,
                        "modelUsed" to msg.activeModel().take(120),
                        "sceneArtUri" to msg.sceneArtUri.trim().take(500),
                        "agentTrace" to sanitizeStringList(msg.agentTrace, 16, 160),
                        "orderIndex" to idx.toLong(),
                        "createdAt" to FieldValue.serverTimestamp(),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                )
            }
            batch.commit().await()
            Result.success(newChatId)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, path)
            Result.failure(e)
        }
    }

    // --- Messages & Swipes CRUD ---

    override suspend fun addMessage(
        userId: String,
        chatId: String,
        role: String,
        speakerName: String,
        content: String,
        messageKind: MessageKind,
        modelUsed: String,
        orderIndex: Long,
        newTotalCount: Long,
        agentTrace: List<String>
    ): Result<String> {
        val msgId = UUID.randomUUID().toString()
        val path = "users/$userId/chats/$chatId/messages/$msgId"
        return try {
            val uid = requireUid(userId)
            val safeContent = content.trim().take(32000)
            require(safeContent.isNotEmpty()) { "Сообщение не может быть пустым." }
            val safeModel = modelUsed.trim().take(120)

            val chatRef = db.collection("users").document(uid).collection("chats").document(chatId)
            val msgRef = chatRef.collection("messages").document(msgId)
            val batch = db.batch()
            batch.set(
                msgRef,
                mapOf(
                    "userId" to uid,
                    "chatId" to chatId,
                    "role" to role,
                    "speakerName" to speakerName.trim().take(100),
                    "content" to safeContent,
                    "variants" to listOf(safeContent),
                    "variantModels" to if (safeModel.isNotEmpty()) listOf(safeModel) else emptyList<String>(),
                    "activeVariantIndex" to 0L,
                    "messageKind" to messageKind.name,
                    "modelUsed" to safeModel,
                    "sceneArtUri" to "",
                    "agentTrace" to sanitizeStringList(agentTrace, 16, 160),
                    "orderIndex" to orderIndex.coerceAtLeast(0L),
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            )
            batch.update(
                chatRef,
                mapOf(
                    "lastMessagePreview" to safeContent.take(300),
                    "messageCount" to newTotalCount.coerceAtLeast(0L),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            )
            batch.commit().await()
            Result.success(msgId)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, path)
            Result.failure(e)
        }
    }

    override suspend fun updateMessageSceneArt(
        userId: String,
        chatId: String,
        messageId: String,
        sceneArtUri: String
    ): Result<Unit> {
        val path = "users/$userId/chats/$chatId/messages/$messageId"
        return try {
            val uid = requireUid(userId)
            db.collection("users").document(uid).collection("chats").document(chatId)
                .collection("messages").document(messageId)
                .update(
                    mapOf(
                        "sceneArtUri" to sceneArtUri.trim().take(500),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    override suspend fun addMessageVariant(
        userId: String,
        chatId: String,
        message: RpMessage,
        newVariantText: String,
        modelUsed: String,
        isLastMessage: Boolean
    ): Result<Int> {
        val path = "users/$userId/chats/$chatId/messages/${message.id}"
        return try {
            val uid = requireUid(userId)
            val safeText = newVariantText.trim().take(32000)
            require(safeText.isNotEmpty()) { "Вариант ответа не может быть пустым." }
            val safeModel = modelUsed.trim().take(120)

            val existingVariants = message.effectiveVariants().take(11).toMutableList()
            val existingModels = message.variantModels.toMutableList()
            while (existingModels.size < existingVariants.size) {
                existingModels.add(message.modelUsed.take(120))
            }
            existingVariants.add(safeText)
            existingModels.add(safeModel)

            val newIdx = existingVariants.lastIndex.coerceIn(0, 11)
            val chatRef = db.collection("users").document(uid).collection("chats").document(chatId)
            val msgRef = chatRef.collection("messages").document(message.id)
            val batch = db.batch()
            batch.update(
                msgRef,
                mapOf(
                    "content" to safeText,
                    "variants" to sanitizeStringList(existingVariants, 12, 32000),
                    "variantModels" to existingModels.map { it.trim().take(120) }.take(12),
                    "activeVariantIndex" to newIdx.toLong(),
                    "modelUsed" to safeModel,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            )
            if (isLastMessage) {
                batch.update(
                    chatRef,
                    mapOf(
                        "lastMessagePreview" to safeText.take(300),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                )
            }
            batch.commit().await()
            Result.success(newIdx)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    override suspend fun selectMessageVariant(
        userId: String,
        chatId: String,
        message: RpMessage,
        targetIndex: Int,
        isLastMessage: Boolean
    ): Result<Unit> {
        val path = "users/$userId/chats/$chatId/messages/${message.id}"
        return try {
            val uid = requireUid(userId)
            val variants = message.effectiveVariants()
            if (variants.isEmpty()) return Result.success(Unit)
            val safeIdx = targetIndex.coerceIn(0, (variants.size - 1).coerceAtLeast(0))
            val chosenContent = variants[safeIdx].trim().ifEmpty { message.content }.take(32000)
            val chosenModel = message.variantModels.getOrElse(safeIdx) { message.modelUsed }.take(120)

            val chatRef = db.collection("users").document(uid).collection("chats").document(chatId)
            val msgRef = chatRef.collection("messages").document(message.id)
            val batch = db.batch()
            batch.update(
                msgRef,
                mapOf(
                    "content" to chosenContent,
                    "activeVariantIndex" to safeIdx.toLong(),
                    "modelUsed" to chosenModel,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            )
            if (isLastMessage) {
                batch.update(
                    chatRef,
                    mapOf(
                        "lastMessagePreview" to chosenContent.take(300),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                )
            }
            batch.commit().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    override suspend fun updateMessageContent(
        userId: String,
        chatId: String,
        message: RpMessage,
        newContent: String,
        isLastMessage: Boolean
    ): Result<Unit> {
        val path = "users/$userId/chats/$chatId/messages/${message.id}"
        return try {
            val uid = requireUid(userId)
            val safeContent = newContent.trim().take(32000)
            require(safeContent.isNotEmpty()) { "Сообщение не может быть пустым." }

            val variants = message.effectiveVariants().toMutableList()
            val idx = message.effectiveActiveIndex().coerceIn(0, (variants.size - 1).coerceAtLeast(0))
            if (variants.isEmpty()) {
                variants.add(safeContent)
            } else {
                variants[idx] = safeContent
            }

            val chatRef = db.collection("users").document(uid).collection("chats").document(chatId)
            val msgRef = chatRef.collection("messages").document(message.id)
            val batch = db.batch()
            batch.update(
                msgRef,
                mapOf(
                    "content" to safeContent,
                    "variants" to sanitizeStringList(variants, 12, 32000),
                    "activeVariantIndex" to idx.toLong(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            )
            if (isLastMessage) {
                batch.update(
                    chatRef,
                    mapOf(
                        "lastMessagePreview" to safeContent.take(300),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                )
            }
            batch.commit().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    override suspend fun deleteMessagesFrom(
        userId: String,
        chatId: String,
        messageIdsToDelete: List<String>,
        remainingCount: Long,
        remainingLastPreview: String
    ): Result<Unit> {
        val path = "users/$userId/chats/$chatId/messages"
        return try {
            val uid = requireUid(userId)
            val chatRef = db.collection("users").document(uid).collection("chats").document(chatId)
            messageIdsToDelete.chunked(350).forEach { chunk ->
                val batch = db.batch()
                chunk.forEach { msgId ->
                    batch.delete(chatRef.collection("messages").document(msgId))
                }
                batch.commit().await()
            }
            chatRef.update(
                mapOf(
                    "messageCount" to remainingCount.coerceAtLeast(0L),
                    "lastMessagePreview" to remainingLastPreview.take(300),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, path)
            Result.failure(e)
        }
    }

    // --- Personas CRUD ---

    override suspend fun savePersona(userId: String, persona: UserPersona): Result<String> {
        val isNew = persona.id.isBlank()
        val docId = if (isNew) UUID.randomUUID().toString() else sanitizeId(persona.id)
        val path = "users/$userId/personas/$docId"
        return try {
            val uid = requireUid(userId)
            val docRef = db.collection("users").document(uid).collection("personas").document(docId)
            val payload = mapOf(
                "userId" to uid,
                "name" to persona.name.trim().ifEmpty { "Странник" }.take(100),
                "description" to persona.description.trim().take(4000),
                "preferredStyle" to persona.preferredStyle.trim().take(500),
                "activeDefault" to persona.activeDefault,
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            )
            if (isNew) {
                docRef.set(payload).await()
            } else {
                val updateMap = payload.toMutableMap().apply {
                    remove("userId")
                    remove("createdAt")
                }
                docRef.update(updateMap).await()
            }
            Result.success(docId)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, if (isNew) OperationType.CREATE else OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    override suspend fun setDefaultPersona(userId: String, personaId: String, allPersonaIds: List<String>): Result<Unit> {
        val path = "users/$userId/personas/$personaId"
        return try {
            val uid = requireUid(userId)
            val batch = db.batch()
            val col = db.collection("users").document(uid).collection("personas")
            allPersonaIds.forEach { id ->
                batch.update(
                    col.document(id),
                    mapOf(
                        "activeDefault" to (id == personaId),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                )
            }
            batch.commit().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    override suspend fun deletePersona(userId: String, personaId: String): Result<Unit> {
        val path = "users/$userId/personas/$personaId"
        return try {
            val uid = requireUid(userId)
            db.collection("users").document(uid).collection("personas").document(personaId)
                .delete().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, path)
            Result.failure(e)
        }
    }

    // --- Export / Import Helpers ---

    override suspend fun fetchAllUserDataForExport(userId: String): Result<ExportWorkspaceBundle> {
        val rootPath = "users/$userId"
        return try {
            val uid = requireUid(userId)
            val userDoc = db.collection("users").document(uid)
            val scenarios = userDoc.collection("scenarios").get().await()
                .toObjects(RpScenario::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
            val personas = userDoc.collection("personas").get().await()
                .toObjects(UserPersona::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
            val chats = userDoc.collection("chats")
                .orderBy("updatedAt", Query.Direction.DESCENDING)
                .get().await()
                .toObjects(RpChat::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)

            val chatsWithMessages = chats.map { chat ->
                val msgs = userDoc.collection("chats").document(chat.id)
                    .collection("messages")
                    .orderBy("orderIndex", Query.Direction.ASCENDING)
                    .get().await()
                    .toObjects(RpMessage::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                chat to msgs
            }
            Result.success(ExportWorkspaceBundle(scenarios, personas, chatsWithMessages))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.LIST, rootPath)
            Result.failure(e)
        }
    }

    override suspend fun importChatWithMessages(
        userId: String,
        chat: RpChat,
        messages: List<RpMessage>
    ): Result<String> {
        val chatId = UUID.randomUUID().toString()
        val path = "users/$userId/chats/$chatId"
        return try {
            val uid = requireUid(userId)
            val params = chat.generationParams()
            val chatRef = db.collection("users").document(uid).collection("chats").document(chatId)
            val lastMsg = messages.lastOrNull()

            chatRef.set(
                mapOf(
                    "userId" to uid,
                    "scenarioId" to (chat.scenarioId.ifBlank { "imported" }).take(128),
                    "rootChatId" to chat.rootChatId.ifBlank { chatId }.take(128),
                    "parentChatId" to chat.parentChatId.take(128),
                    "branchLabel" to chat.branchLabel.ifBlank { "Импорт" }.take(80),
                    "branchPointOrder" to chat.branchPointOrder.coerceAtLeast(0L),
                    "title" to chat.title.trim().ifEmpty { "Импортированный чат" }.take(140),
                    "rpMode" to RpMode.fromString(chat.rpMode).name,
                    "systemPrompt" to chat.systemPrompt.trim().ifEmpty { "Ты — ролевой партнёр." }.take(20000),
                    "characterName" to chat.characterName.trim().ifEmpty { "Персонаж" }.take(100),
                    "userPersonaName" to chat.userPersonaName.trim().ifEmpty { "Странник" }.take(100),
                    "userPersonaDesc" to chat.userPersonaDesc.trim().take(4000),
                    "authorsNote" to chat.authorsNote.trim().take(4000),
                    "memorySummary" to chat.memorySummary.trim().take(12000),
                    "loreNotes" to sanitizeStringList(chat.loreNotes, 30, 500),
                    "loreEntries" to serializeLoreEntries(chat.loreEntries),
                    "rpgState" to serializeRpgState(chat.rpgState),
                    "agentSetupJson" to chat.agentSetupJson.trim().take(16000),
                    "participants" to sanitizeStringList(chat.participants, 12, 100),
                    "provider" to LlmProvider.fromString(chat.provider).name,
                    "modelId" to chat.modelId.trim().ifEmpty { "gemini-3.8-flash" }.take(120),
                    "temperature" to params.temperature,
                    "topP" to params.topP,
                    "topK" to params.topK.toLong(),
                    "maxTokens" to params.maxTokens.toLong(),
                    "contextWindowMessages" to params.contextWindowMessages.toLong(),
                    "presencePenalty" to params.presencePenalty,
                    "frequencyPenalty" to params.frequencyPenalty,
                    "stopSequences" to sanitizeStringList(params.stopSequences, 10, 60),
                    "portraitUri" to chat.portraitUri.trim().take(500),
                    "backdropUri" to chat.backdropUri.trim().take(500),
                    "lastMessagePreview" to (lastMsg?.activeContent()?.take(300).orEmpty()),
                    "messageCount" to messages.size.toLong(),
                    "pinned" to false,
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()

            messages.chunked(350).forEachIndexed { chunkIdx, chunk ->
                val batch = db.batch()
                chunk.forEachIndexed { innerIdx, msg ->
                    val orderIdx = (chunkIdx * 350 + innerIdx).toLong()
                    val msgRef = chatRef.collection("messages").document(UUID.randomUUID().toString())
                    val vars = sanitizeStringList(msg.effectiveVariants(), 12, 32000)
                    val activeText = msg.activeContent().trim().ifEmpty { "..." }.take(32000)
                    val finalVars = if (vars.isNotEmpty()) vars else listOf(activeText)
                    val activeIdx = msg.effectiveActiveIndex().coerceIn(0, (finalVars.size - 1).coerceAtLeast(0)).toLong()
                    batch.set(
                        msgRef,
                        mapOf(
                            "userId" to uid,
                            "chatId" to chatId,
                            "role" to if (msg.role in listOf("user", "assistant", "system")) msg.role else "user",
                            "speakerName" to msg.speakerName.trim().take(100),
                            "content" to activeText,
                            "variants" to finalVars,
                            "variantModels" to sanitizeStringList(msg.variantModels, 12, 120),
                            "activeVariantIndex" to activeIdx,
                            "messageKind" to MessageKind.fromString(msg.messageKind).name,
                            "modelUsed" to msg.activeModel().trim().take(120),
                            "sceneArtUri" to msg.sceneArtUri.trim().take(500),
                            "agentTrace" to sanitizeStringList(msg.agentTrace, 16, 160),
                            "orderIndex" to orderIdx,
                            "createdAt" to FieldValue.serverTimestamp(),
                            "updatedAt" to FieldValue.serverTimestamp()
                        )
                    )
                }
                batch.commit().await()
            }
            Result.success(chatId)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, path)
            Result.failure(e)
        }
    }
}
