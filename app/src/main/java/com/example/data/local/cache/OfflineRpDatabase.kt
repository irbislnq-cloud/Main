package com.example.data.local.cache

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.example.data.export.JsonExportManager
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
import com.example.data.repository.ExportWorkspaceBundle
import com.example.data.repository.RpDataRepository
import com.example.diagnostics.AppLogBuffer
import com.google.firebase.Timestamp
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import org.json.JSONObject
import java.util.Date
import java.util.UUID

@Entity(tableName = "cached_scenarios")
data class CachedScenarioEntity(
    @PrimaryKey val compositeId: String,
    val userId: String,
    val scenarioId: String,
    val updatedAtMs: Long,
    val jsonPayload: String
)

@Entity(tableName = "cached_chats")
data class CachedChatEntity(
    @PrimaryKey val compositeId: String,
    val userId: String,
    val chatId: String,
    val updatedAtMs: Long,
    val jsonPayload: String
)

@Entity(tableName = "cached_messages")
data class CachedMessageEntity(
    @PrimaryKey val compositeId: String,
    val userId: String,
    val chatId: String,
    val messageId: String,
    val orderIndex: Long,
    val jsonPayload: String
)

@Entity(tableName = "cached_personas")
data class CachedPersonaEntity(
    @PrimaryKey val compositeId: String,
    val userId: String,
    val personaId: String,
    val updatedAtMs: Long,
    val jsonPayload: String
)

@Dao
interface OfflineRpDao {
    @Query("SELECT * FROM cached_scenarios WHERE userId = :userId ORDER BY updatedAtMs DESC")
    fun observeScenarios(userId: String): Flow<List<CachedScenarioEntity>>

    @Query("SELECT COUNT(*) FROM cached_scenarios WHERE userId = :userId")
    suspend fun countScenarios(userId: String): Int

    @Query("SELECT * FROM cached_scenarios WHERE userId = :userId")
    suspend fun getScenariosOnce(userId: String): List<CachedScenarioEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertScenario(entity: CachedScenarioEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertScenarios(entities: List<CachedScenarioEntity>)

    @Query("SELECT * FROM cached_scenarios WHERE compositeId = :compositeId LIMIT 1")
    suspend fun getScenario(compositeId: String): CachedScenarioEntity?

    @Query("DELETE FROM cached_scenarios WHERE userId = :userId AND scenarioId = :scenarioId")
    suspend fun deleteScenario(userId: String, scenarioId: String)

    @Query("DELETE FROM cached_scenarios WHERE userId = :userId")
    suspend fun deleteAllScenariosForUser(userId: String)

    @Query("SELECT * FROM cached_chats WHERE userId = :userId ORDER BY updatedAtMs DESC")
    fun observeChats(userId: String): Flow<List<CachedChatEntity>>

    @Query("SELECT * FROM cached_chats WHERE userId = :userId ORDER BY updatedAtMs DESC")
    suspend fun getChatsOnce(userId: String): List<CachedChatEntity>

    @Query("SELECT * FROM cached_chats WHERE userId = :userId AND chatId = :chatId LIMIT 1")
    fun observeChat(userId: String, chatId: String): Flow<CachedChatEntity?>

    @Query("SELECT * FROM cached_chats WHERE userId = :userId AND chatId = :chatId LIMIT 1")
    suspend fun getChatOnce(userId: String, chatId: String): CachedChatEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertChat(entity: CachedChatEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertChats(entities: List<CachedChatEntity>)

    @Query("DELETE FROM cached_chats WHERE userId = :userId AND chatId = :chatId")
    suspend fun deleteChat(userId: String, chatId: String)

    @Query("DELETE FROM cached_chats WHERE userId = :userId")
    suspend fun deleteAllChatsForUser(userId: String)

    @Query("SELECT * FROM cached_messages WHERE userId = :userId AND chatId = :chatId ORDER BY orderIndex ASC")
    fun observeMessages(userId: String, chatId: String): Flow<List<CachedMessageEntity>>

    @Query("SELECT * FROM cached_messages WHERE userId = :userId AND chatId = :chatId ORDER BY orderIndex ASC")
    suspend fun getMessagesOnce(userId: String, chatId: String): List<CachedMessageEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertMessage(entity: CachedMessageEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertMessages(entities: List<CachedMessageEntity>)

    @Query("DELETE FROM cached_messages WHERE userId = :userId AND chatId = :chatId")
    suspend fun deleteMessagesForChat(userId: String, chatId: String)

    @Query("DELETE FROM cached_messages WHERE userId = :userId AND chatId = :chatId AND messageId IN (:messageIds)")
    suspend fun deleteMessagesByIds(userId: String, chatId: String, messageIds: List<String>)

    @Query("SELECT * FROM cached_personas WHERE userId = :userId ORDER BY updatedAtMs DESC")
    fun observePersonas(userId: String): Flow<List<CachedPersonaEntity>>

    @Query("SELECT * FROM cached_personas WHERE userId = :userId ORDER BY updatedAtMs DESC")
    suspend fun getPersonasOnce(userId: String): List<CachedPersonaEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertPersona(entity: CachedPersonaEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertPersonas(entities: List<CachedPersonaEntity>)

    @Query("DELETE FROM cached_personas WHERE userId = :userId AND personaId = :personaId")
    suspend fun deletePersona(userId: String, personaId: String)

    @Query("DELETE FROM cached_personas WHERE userId = :userId")
    suspend fun deleteAllPersonasForUser(userId: String)
}

@Database(
    entities = [
        CachedScenarioEntity::class,
        CachedChatEntity::class,
        CachedMessageEntity::class,
        CachedPersonaEntity::class,
        CharacterProfileEntity::class
    ],
    version = 2,
    exportSchema = false
)
abstract class OfflineRpDatabase : RoomDatabase() {
    abstract fun offlineRpDao(): OfflineRpDao
    abstract fun characterProfileDao(): CharacterProfileDao

    companion object {
        @Volatile
        private var instance: OfflineRpDatabase? = null

        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `character_profiles` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `name` TEXT NOT NULL,
                        `bio` TEXT NOT NULL,
                        `personalityTraits` TEXT NOT NULL,
                        `createdAtMs` INTEGER NOT NULL,
                        `updatedAtMs` INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
            }
        }

        fun getInstance(context: Context): OfflineRpDatabase {
            return instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    OfflineRpDatabase::class.java,
                    "lorecraft_offline_cache.db"
                )
                    .addMigrations(MIGRATION_1_2)
                    .fallbackToDestructiveMigration(dropAllTables = true)
                    .build()
                    .also { instance = it }
            }
        }
    }
}

class OfflineLocalRpRepository(
    private val dao: OfflineRpDao
) : RpDataRepository {

    companion object {
        private const val TAG = "OfflineRepo"
        const val LOCAL_OFFLINE_USER_ID = "local_offline"
    }

    private fun key(userId: String, id: String) = "${userId}__${id}"
    private fun msgKey(userId: String, chatId: String, id: String) = "${userId}__${chatId}__${id}"

    private fun encodeScenario(userId: String, scenario: RpScenario): CachedScenarioEntity {
        val nowMs = scenario.updatedAt?.toDate()?.time ?: System.currentTimeMillis()
        val withMeta = scenario.copy(
            userId = userId,
            createdAt = scenario.createdAt ?: Timestamp(Date(nowMs)),
            updatedAt = Timestamp(Date(nowMs))
        )
        return CachedScenarioEntity(
            compositeId = key(userId, scenario.id),
            userId = userId,
            scenarioId = scenario.id,
            updatedAtMs = nowMs,
            jsonPayload = JsonExportManager.scenarioToJson(withMeta).toString()
        )
    }

    private fun encodeChat(userId: String, chat: RpChat): CachedChatEntity {
        val nowMs = chat.updatedAt?.toDate()?.time ?: System.currentTimeMillis()
        val withMeta = chat.copy(
            userId = userId,
            createdAt = chat.createdAt ?: Timestamp(Date(nowMs)),
            updatedAt = Timestamp(Date(nowMs))
        )
        return CachedChatEntity(
            compositeId = key(userId, chat.id),
            userId = userId,
            chatId = chat.id,
            updatedAtMs = nowMs,
            jsonPayload = JsonExportManager.chatMetaToJson(withMeta).toString()
        )
    }

    private fun encodeMessage(userId: String, chatId: String, msg: RpMessage): CachedMessageEntity {
        val nowMs = msg.updatedAt?.toDate()?.time ?: System.currentTimeMillis()
        val withMeta = msg.copy(
            userId = userId,
            chatId = chatId,
            createdAt = msg.createdAt ?: Timestamp(Date(nowMs)),
            updatedAt = Timestamp(Date(nowMs))
        )
        return CachedMessageEntity(
            compositeId = msgKey(userId, chatId, msg.id),
            userId = userId,
            chatId = chatId,
            messageId = msg.id,
            orderIndex = msg.orderIndex,
            jsonPayload = JsonExportManager.messageToJson(withMeta).toString()
        )
    }

    private fun encodePersona(userId: String, p: UserPersona): CachedPersonaEntity {
        val nowMs = p.updatedAt?.toDate()?.time ?: System.currentTimeMillis()
        val withMeta = p.copy(
            userId = userId,
            createdAt = p.createdAt ?: Timestamp(Date(nowMs)),
            updatedAt = Timestamp(Date(nowMs))
        )
        return CachedPersonaEntity(
            compositeId = key(userId, p.id),
            userId = userId,
            personaId = p.id,
            updatedAtMs = nowMs,
            jsonPayload = JsonExportManager.personaToJson(withMeta).toString()
        )
    }

    suspend fun mirrorScenarios(userId: String, list: List<RpScenario>) {
        runCatching {
            dao.deleteAllScenariosForUser(userId)
            dao.upsertScenarios(list.map { encodeScenario(userId, it) })
        }
    }

    suspend fun mirrorChats(userId: String, list: List<RpChat>) {
        runCatching {
            dao.deleteAllChatsForUser(userId)
            dao.upsertChats(list.map { encodeChat(userId, it) })
        }
    }

    suspend fun mirrorMessages(userId: String, chatId: String, list: List<RpMessage>) {
        runCatching {
            dao.deleteMessagesForChat(userId, chatId)
            dao.upsertMessages(list.map { encodeMessage(userId, chatId, it) })
        }
    }

    suspend fun mirrorPersonas(userId: String, list: List<UserPersona>) {
        runCatching {
            dao.deleteAllPersonasForUser(userId)
            dao.upsertPersonas(list.map { encodePersona(userId, it) })
        }
    }

    override fun observeScenarios(userId: String): Flow<List<RpScenario>> =
        dao.observeScenarios(userId).map { entities ->
            entities.mapNotNull { e ->
                runCatching { JsonExportManager.scenarioFromJson(JSONObject(e.jsonPayload)) }.getOrNull()
            }
        }

    override fun observeChats(userId: String): Flow<List<RpChat>> =
        dao.observeChats(userId).map { entities ->
            entities.mapNotNull { e ->
                runCatching { JsonExportManager.chatMetaFromJson(JSONObject(e.jsonPayload)) }.getOrNull()
            }
        }

    override fun observeChat(userId: String, chatId: String): Flow<RpChat?> =
        dao.observeChat(userId, chatId).map { e ->
            e?.let { runCatching { JsonExportManager.chatMetaFromJson(JSONObject(it.jsonPayload)) }.getOrNull() }
        }

    override fun observeMessages(userId: String, chatId: String): Flow<List<RpMessage>> =
        dao.observeMessages(userId, chatId).map { entities ->
            entities.mapNotNull { e ->
                runCatching { JsonExportManager.messageFromJson(JSONObject(e.jsonPayload), e.orderIndex) }.getOrNull()
            }
        }

    override fun observePersonas(userId: String): Flow<List<UserPersona>> =
        dao.observePersonas(userId).map { entities ->
            entities.mapNotNull { e ->
                runCatching { JsonExportManager.personaFromJson(JSONObject(e.jsonPayload)) }.getOrNull()
            }
        }

    override suspend fun ensureStarterDataIfEmpty(userId: String): Result<Unit> = runCatching {
        if (dao.countScenarios(userId) == 0) {
            val now = Timestamp.now()
            val starters = PresetScenarios.starterTemplates().map { tpl ->
                encodeScenario(userId, tpl.copy(userId = userId, createdAt = now, updatedAt = now))
            }
            dao.upsertScenarios(starters)
            if (dao.getPersonasOnce(userId).isEmpty()) {
                dao.upsertPersona(
                    encodePersona(
                        userId,
                        UserPersona(
                            id = "default_wanderer",
                            userId = userId,
                            name = "Странник",
                            description = "Наблюдательный путешественник с острым умом.",
                            preferredStyle = "От третьего лица",
                            activeDefault = true,
                            createdAt = now,
                            updatedAt = now
                        )
                    )
                )
            }
            AppLogBuffer.i(TAG, "Seeded ${starters.size} offline starter scenarios for $userId")
        }
    }

    override suspend fun saveScenario(userId: String, scenario: RpScenario): Result<String> = runCatching {
        val docId = scenario.id.ifBlank { UUID.randomUUID().toString() }
        val now = Timestamp.now()
        val final = scenario.copy(
            id = docId,
            userId = userId,
            createdAt = scenario.createdAt ?: now,
            updatedAt = now
        )
        dao.upsertScenario(encodeScenario(userId, final))
        docId
    }

    override suspend fun toggleScenarioPin(userId: String, scenarioId: String, pinned: Boolean): Result<Unit> =
        runCatching {
            val entity = dao.getScenario(key(userId, scenarioId)) ?: return@runCatching
            val current = JsonExportManager.scenarioFromJson(JSONObject(entity.jsonPayload))
            dao.upsertScenario(encodeScenario(userId, current.copy(pinned = pinned, updatedAt = Timestamp.now())))
        }

    override suspend fun deleteScenario(userId: String, scenarioId: String): Result<Unit> = runCatching {
        dao.deleteScenario(userId, scenarioId)
    }

    override suspend fun createChatFromScenario(
        userId: String,
        scenario: RpScenario,
        userPersona: UserPersona?,
        overrideProvider: LlmProvider?,
        overrideModelId: String?,
        overrideParams: GenerationParams?
    ): Result<String> = runCatching {
        val chatId = UUID.randomUUID().toString()
        val now = Timestamp.now()
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

        val rpgInit = if (scenario.rpModeEnum() == RpMode.GAME_MASTER && !scenario.defaultRpgState.enabled) {
            scenario.defaultRpgState.copy(enabled = true)
        } else {
            scenario.defaultRpgState
        }

        val chat = RpChat(
            id = chatId,
            userId = userId,
            scenarioId = scenario.id.ifBlank { "custom" }.take(128),
            rootChatId = chatId,
            parentChatId = "",
            branchLabel = "Основная линия",
            branchPointOrder = 0L,
            title = scenario.title.trim().ifEmpty { "Новая ролевая" }.take(140),
            rpMode = RpMode.fromString(scenario.rpMode).name,
            systemPrompt = fullSystem,
            characterName = scenario.title.trim().ifEmpty { "Персонаж" }.take(100),
            userPersonaName = personaName.take(100),
            userPersonaDesc = personaDesc.take(4000),
            loreNotes = scenario.loreNotes,
            loreEntries = scenario.loreEntries,
            rpgState = rpgInit,
            agentSetupJson = scenario.defaultAgentSetupJson.trim().take(16000),
            participants = scenario.participants,
            provider = provider.name,
            modelId = modelId.take(120),
            temperature = params.temperature,
            topP = params.topP,
            topK = params.topK.toLong(),
            maxTokens = params.maxTokens.toLong(),
            contextWindowMessages = params.contextWindowMessages.toLong(),
            presencePenalty = params.presencePenalty,
            frequencyPenalty = params.frequencyPenalty,
            stopSequences = params.stopSequences,
            portraitUri = scenario.portraitUri.take(500),
            backdropUri = scenario.backdropUri.take(500),
            lastMessagePreview = greeting.take(300),
            messageCount = if (hasGreeting) 1L else 0L,
            pinned = false,
            createdAt = now,
            updatedAt = now
        )
        dao.upsertChat(encodeChat(userId, chat))

        if (hasGreeting) {
            val msgId = UUID.randomUUID().toString()
            val speaker = scenario.participants.firstOrNull()?.takeIf { it.isNotBlank() } ?: scenario.title.take(100)
            dao.upsertMessage(
                encodeMessage(
                    userId,
                    chatId,
                    RpMessage(
                        id = msgId,
                        userId = userId,
                        chatId = chatId,
                        role = "assistant",
                        speakerName = speaker,
                        content = greeting,
                        variants = listOf(greeting),
                        variantModels = listOf(modelId),
                        activeVariantIndex = 0L,
                        messageKind = MessageKind.ROLEPLAY.name,
                        modelUsed = modelId,
                        orderIndex = 0L,
                        createdAt = now,
                        updatedAt = now
                    )
                )
            )
        }
        chatId
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
    ): Result<Unit> = runCatching {
        val existingEntity = dao.getChatOnce(userId, chatId)
            ?: throw IllegalStateException("Чат не найден в локальной базе.")
        val current = JsonExportManager.chatMetaFromJson(JSONObject(existingEntity.jsonPayload))
        val clamped = params.clamped()
        val updated = current.copy(
            title = title.trim().ifEmpty { "Ролевая сессия" }.take(140),
            rpMode = rpMode.name,
            systemPrompt = systemPrompt.trim().ifEmpty { "Ты — выразительный ролевой партнёр." }.take(20000),
            characterName = characterName.trim().ifEmpty { "Персонаж" }.take(100),
            userPersonaName = userPersonaName.trim().ifEmpty { "Странник" }.take(100),
            userPersonaDesc = userPersonaDesc.trim().take(4000),
            authorsNote = authorsNote.trim().take(4000),
            memorySummary = memorySummary.trim().take(12000),
            loreNotes = loreNotes,
            loreEntries = loreEntries,
            participants = participants,
            provider = provider.name,
            modelId = modelId.trim().ifEmpty {
                provider.suggestedModels.firstOrNull() ?: "openai"
            }.take(120),
            temperature = clamped.temperature,
            topP = clamped.topP,
            topK = clamped.topK.toLong(),
            maxTokens = clamped.maxTokens.toLong(),
            contextWindowMessages = clamped.contextWindowMessages.toLong(),
            presencePenalty = clamped.presencePenalty,
            frequencyPenalty = clamped.frequencyPenalty,
            stopSequences = clamped.stopSequences,
            portraitUri = portraitUri.take(500),
            backdropUri = backdropUri.take(500),
            pinned = pinned,
            updatedAt = Timestamp.now()
        )
        dao.upsertChat(encodeChat(userId, updated))
    }

    override suspend fun updateChatRpgState(userId: String, chatId: String, rpgState: RpgState): Result<Unit> =
        runCatching {
            val existingEntity = dao.getChatOnce(userId, chatId)
                ?: throw IllegalStateException("Чат не найден.")
            val current = JsonExportManager.chatMetaFromJson(JSONObject(existingEntity.jsonPayload))
            dao.upsertChat(encodeChat(userId, current.copy(rpgState = rpgState, updatedAt = Timestamp.now())))
        }

    override suspend fun updateChatAgentSetup(userId: String, chatId: String, agentSetupJson: String): Result<Unit> =
        runCatching {
            val existingEntity = dao.getChatOnce(userId, chatId)
                ?: throw IllegalStateException("Чат не найден.")
            val current = JsonExportManager.chatMetaFromJson(JSONObject(existingEntity.jsonPayload))
            dao.upsertChat(
                encodeChat(
                    userId,
                    current.copy(agentSetupJson = agentSetupJson.trim().take(16000), updatedAt = Timestamp.now())
                )
            )
        }

    override suspend fun updateAllChatsProvider(
        userId: String,
        provider: LlmProvider,
        modelId: String
    ): Result<Int> = runCatching {
        val safeModel = modelId.trim().ifEmpty {
            provider.suggestedModels.firstOrNull() ?: "openai"
        }.take(120)
        val entities = dao.getChatsOnce(userId)
        if (entities.isEmpty()) return@runCatching 0
        val now = Timestamp.now()
        val updated = entities.map { ent ->
            val current = JsonExportManager.chatMetaFromJson(JSONObject(ent.jsonPayload))
            encodeChat(
                userId,
                current.copy(
                    provider = provider.name,
                    modelId = safeModel,
                    updatedAt = now
                )
            )
        }
        dao.upsertChats(updated)
        updated.size
    }

    override suspend fun toggleChatPin(userId: String, chatId: String, pinned: Boolean): Result<Unit> =
        runCatching {
            val existingEntity = dao.getChatOnce(userId, chatId) ?: return@runCatching
            val current = JsonExportManager.chatMetaFromJson(JSONObject(existingEntity.jsonPayload))
            dao.upsertChat(encodeChat(userId, current.copy(pinned = pinned, updatedAt = Timestamp.now())))
        }

    override suspend fun deleteChat(userId: String, chatId: String): Result<Unit> = runCatching {
        dao.deleteMessagesForChat(userId, chatId)
        dao.deleteChat(userId, chatId)
    }

    override suspend fun branchChatFromMessage(
        userId: String,
        sourceChat: RpChat,
        messagesUpToTarget: List<RpMessage>,
        customBranchLabel: String
    ): Result<String> = runCatching {
        val newChatId = UUID.randomUUID().toString()
        val now = Timestamp.now()
        val lastMsg = messagesUpToTarget.lastOrNull()
        val rootId = sourceChat.rootChatId.ifBlank { sourceChat.id }.take(128)
        val branchPoint = lastMsg?.orderIndex ?: 0L
        val label = customBranchLabel.trim().ifEmpty { "Развилка #${branchPoint + 1}" }.take(80)
        val branchTitle = "${sourceChat.title.substringBefore(" • ").take(95)} • $label".take(140)
        val branched = sourceChat.copy(
            id = newChatId,
            userId = userId,
            rootChatId = rootId,
            parentChatId = sourceChat.id,
            branchLabel = label,
            branchPointOrder = branchPoint,
            title = branchTitle,
            lastMessagePreview = lastMsg?.activeContent()?.take(300).orEmpty(),
            messageCount = messagesUpToTarget.size.toLong(),
            pinned = false,
            createdAt = now,
            updatedAt = now
        )
        dao.upsertChat(encodeChat(userId, branched))
        dao.upsertMessages(messagesUpToTarget.mapIndexed { idx, msg ->
            encodeMessage(
                userId,
                newChatId,
                msg.copy(
                    id = UUID.randomUUID().toString(),
                    userId = userId,
                    chatId = newChatId,
                    orderIndex = idx.toLong(),
                    createdAt = now,
                    updatedAt = now
                )
            )
        })
        newChatId
    }

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
    ): Result<String> = runCatching {
        val msgId = UUID.randomUUID().toString()
        val safeContent = content.trim().take(32000)
        require(safeContent.isNotEmpty()) { "Сообщение не может быть пустым." }
        val now = Timestamp.now()
        dao.upsertMessage(
            encodeMessage(
                userId,
                chatId,
                RpMessage(
                    id = msgId,
                    userId = userId,
                    chatId = chatId,
                    role = role,
                    speakerName = speakerName.trim().take(100),
                    content = safeContent,
                    variants = listOf(safeContent),
                    variantModels = if (modelUsed.isNotBlank()) listOf(modelUsed.take(120)) else emptyList(),
                    activeVariantIndex = 0L,
                    messageKind = messageKind.name,
                    modelUsed = modelUsed.take(120),
                    agentTrace = agentTrace.map { it.trim().take(160) }.filter { it.isNotEmpty() }.take(16),
                    orderIndex = orderIndex.coerceAtLeast(0L),
                    createdAt = now,
                    updatedAt = now
                )
            )
        )
        dao.getChatOnce(userId, chatId)?.let { chatEnt ->
            val current = JsonExportManager.chatMetaFromJson(JSONObject(chatEnt.jsonPayload))
            dao.upsertChat(
                encodeChat(
                    userId,
                    current.copy(
                        lastMessagePreview = safeContent.take(300),
                        messageCount = newTotalCount.coerceAtLeast(0L),
                        updatedAt = now
                    )
                )
            )
        }
        msgId
    }

    override suspend fun updateMessageSceneArt(
        userId: String,
        chatId: String,
        messageId: String,
        sceneArtUri: String
    ): Result<Unit> = runCatching {
        val list = dao.getMessagesOnce(userId, chatId)
        val match = list.firstOrNull { it.messageId == messageId } ?: return@runCatching
        val parsed = JsonExportManager.messageFromJson(JSONObject(match.jsonPayload), match.orderIndex)
        dao.upsertMessage(
            encodeMessage(
                userId,
                chatId,
                parsed.copy(sceneArtUri = sceneArtUri.trim().take(500), updatedAt = Timestamp.now())
            )
        )
    }

    override suspend fun addMessageVariant(
        userId: String,
        chatId: String,
        message: RpMessage,
        newVariantText: String,
        modelUsed: String,
        isLastMessage: Boolean
    ): Result<Int> = runCatching {
        val safeText = newVariantText.trim().take(32000)
        require(safeText.isNotEmpty()) { "Вариант ответа не может быть пустым." }
        val variants = message.effectiveVariants().take(11).toMutableList().apply { add(safeText) }
        val models = message.variantModels.toMutableList()
        while (models.size < variants.size - 1) models.add(message.modelUsed.take(120))
        models.add(modelUsed.take(120))
        val newIdx = variants.lastIndex.coerceIn(0, 11)
        val now = Timestamp.now()
        dao.upsertMessage(
            encodeMessage(
                userId,
                chatId,
                message.copy(
                    content = safeText,
                    variants = variants,
                    variantModels = models.take(12),
                    activeVariantIndex = newIdx.toLong(),
                    modelUsed = modelUsed.take(120),
                    updatedAt = now
                )
            )
        )
        if (isLastMessage) {
            dao.getChatOnce(userId, chatId)?.let { chatEnt ->
                val current = JsonExportManager.chatMetaFromJson(JSONObject(chatEnt.jsonPayload))
                dao.upsertChat(encodeChat(userId, current.copy(lastMessagePreview = safeText.take(300), updatedAt = now)))
            }
        }
        newIdx
    }

    override suspend fun selectMessageVariant(
        userId: String,
        chatId: String,
        message: RpMessage,
        targetIndex: Int,
        isLastMessage: Boolean
    ): Result<Unit> = runCatching {
        val variants = message.effectiveVariants()
        if (variants.isEmpty()) return@runCatching
        val safeIdx = targetIndex.coerceIn(0, (variants.size - 1).coerceAtLeast(0))
        val chosen = variants[safeIdx].trim().ifEmpty { message.content }.take(32000)
        val chosenModel = message.variantModels.getOrElse(safeIdx) { message.modelUsed }.take(120)
        val now = Timestamp.now()
        dao.upsertMessage(
            encodeMessage(
                userId,
                chatId,
                message.copy(
                    content = chosen,
                    activeVariantIndex = safeIdx.toLong(),
                    modelUsed = chosenModel,
                    updatedAt = now
                )
            )
        )
        if (isLastMessage) {
            dao.getChatOnce(userId, chatId)?.let { chatEnt ->
                val current = JsonExportManager.chatMetaFromJson(JSONObject(chatEnt.jsonPayload))
                dao.upsertChat(encodeChat(userId, current.copy(lastMessagePreview = chosen.take(300), updatedAt = now)))
            }
        }
    }

    override suspend fun updateMessageContent(
        userId: String,
        chatId: String,
        message: RpMessage,
        newContent: String,
        isLastMessage: Boolean
    ): Result<Unit> = runCatching {
        val safeContent = newContent.trim().take(32000)
        require(safeContent.isNotEmpty()) { "Сообщение не может быть пустым." }
        val variants = message.effectiveVariants().toMutableList()
        val idx = message.effectiveActiveIndex().coerceIn(0, (variants.size - 1).coerceAtLeast(0))
        if (variants.isEmpty()) variants.add(safeContent) else variants[idx] = safeContent
        val now = Timestamp.now()
        dao.upsertMessage(
            encodeMessage(
                userId,
                chatId,
                message.copy(
                    content = safeContent,
                    variants = variants,
                    activeVariantIndex = idx.toLong(),
                    updatedAt = now
                )
            )
        )
        if (isLastMessage) {
            dao.getChatOnce(userId, chatId)?.let { chatEnt ->
                val current = JsonExportManager.chatMetaFromJson(JSONObject(chatEnt.jsonPayload))
                dao.upsertChat(encodeChat(userId, current.copy(lastMessagePreview = safeContent.take(300), updatedAt = now)))
            }
        }
    }

    override suspend fun deleteMessagesFrom(
        userId: String,
        chatId: String,
        messageIdsToDelete: List<String>,
        remainingCount: Long,
        remainingLastPreview: String
    ): Result<Unit> = runCatching {
        if (messageIdsToDelete.isNotEmpty()) {
            dao.deleteMessagesByIds(userId, chatId, messageIdsToDelete)
        }
        dao.getChatOnce(userId, chatId)?.let { chatEnt ->
            val current = JsonExportManager.chatMetaFromJson(JSONObject(chatEnt.jsonPayload))
            dao.upsertChat(
                encodeChat(
                    userId,
                    current.copy(
                        messageCount = remainingCount.coerceAtLeast(0L),
                        lastMessagePreview = remainingLastPreview.take(300),
                        updatedAt = Timestamp.now()
                    )
                )
            )
        }
    }

    override suspend fun savePersona(userId: String, persona: UserPersona): Result<String> = runCatching {
        val docId = persona.id.ifBlank { UUID.randomUUID().toString() }
        val now = Timestamp.now()
        dao.upsertPersona(
            encodePersona(
                userId,
                persona.copy(id = docId, userId = userId, createdAt = persona.createdAt ?: now, updatedAt = now)
            )
        )
        docId
    }

    override suspend fun setDefaultPersona(userId: String, personaId: String, allPersonaIds: List<String>): Result<Unit> =
        runCatching {
            val all = dao.getPersonasOnce(userId)
            val now = Timestamp.now()
            dao.upsertPersonas(all.map { entity ->
                val p = JsonExportManager.personaFromJson(JSONObject(entity.jsonPayload))
                encodePersona(userId, p.copy(activeDefault = (p.id == personaId), updatedAt = now))
            })
        }

    override suspend fun deletePersona(userId: String, personaId: String): Result<Unit> = runCatching {
        dao.deletePersona(userId, personaId)
    }

    override suspend fun fetchAllUserDataForExport(userId: String): Result<ExportWorkspaceBundle> = runCatching {
        val scenarios = dao.getScenariosOnce(userId).map { JsonExportManager.scenarioFromJson(JSONObject(it.jsonPayload)) }
        val personas = dao.getPersonasOnce(userId).map { JsonExportManager.personaFromJson(JSONObject(it.jsonPayload)) }
        val chats = dao.getChatsOnce(userId).map { JsonExportManager.chatMetaFromJson(JSONObject(it.jsonPayload)) }
        val chatsWithMsgs = chats.map { chat ->
            val msgs = dao.getMessagesOnce(userId, chat.id)
                .map { JsonExportManager.messageFromJson(JSONObject(it.jsonPayload), it.orderIndex) }
            chat to msgs
        }
        ExportWorkspaceBundle(scenarios, personas, chatsWithMsgs)
    }

    override suspend fun importChatWithMessages(
        userId: String,
        chat: RpChat,
        messages: List<RpMessage>
    ): Result<String> = runCatching {
        val chatId = UUID.randomUUID().toString()
        val now = Timestamp.now()
        val lastMsg = messages.lastOrNull()
        val newChat = chat.copy(
            id = chatId,
            userId = userId,
            rootChatId = chat.rootChatId.ifBlank { chatId },
            branchLabel = chat.branchLabel.ifBlank { "Импорт" },
            lastMessagePreview = lastMsg?.activeContent()?.take(300).orEmpty(),
            messageCount = messages.size.toLong(),
            pinned = false,
            createdAt = now,
            updatedAt = now
        )
        dao.upsertChat(encodeChat(userId, newChat))
        dao.upsertMessages(messages.mapIndexed { idx, msg ->
            encodeMessage(
                userId,
                chatId,
                msg.copy(
                    id = UUID.randomUUID().toString(),
                    userId = userId,
                    chatId = chatId,
                    orderIndex = idx.toLong(),
                    createdAt = now,
                    updatedAt = now
                )
            )
        })
        chatId
    }
}
