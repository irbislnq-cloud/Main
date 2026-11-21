package com.example.data.repository

import com.example.data.local.cache.OfflineLocalRpRepository
import com.example.data.model.GenerationParams
import com.example.data.model.LlmProvider
import com.example.data.model.LoreEntry
import com.example.data.model.MessageKind
import com.example.data.model.RpChat
import com.example.data.model.RpMessage
import com.example.data.model.RpMode
import com.example.data.model.RpScenario
import com.example.data.model.RpgState
import com.example.data.model.UserPersona
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.onEach

class HybridRpRepository(
    private val remote: FirestoreRpRepository,
    private val localCache: OfflineLocalRpRepository
) : RpDataRepository {

    override fun observeScenarios(userId: String): Flow<List<RpScenario>> =
        remote.observeScenarios(userId).onEach { localCache.mirrorScenarios(userId, it) }

    override fun observeChats(userId: String): Flow<List<RpChat>> =
        remote.observeChats(userId).onEach { localCache.mirrorChats(userId, it) }

    override fun observeChat(userId: String, chatId: String): Flow<RpChat?> =
        remote.observeChat(userId, chatId)

    override fun observeMessages(userId: String, chatId: String): Flow<List<RpMessage>> =
        remote.observeMessages(userId, chatId).onEach { localCache.mirrorMessages(userId, chatId, it) }

    override fun observePersonas(userId: String): Flow<List<UserPersona>> =
        remote.observePersonas(userId).onEach { localCache.mirrorPersonas(userId, it) }

    override suspend fun ensureStarterDataIfEmpty(userId: String): Result<Unit> =
        remote.ensureStarterDataIfEmpty(userId)

    override suspend fun saveScenario(userId: String, scenario: RpScenario): Result<String> =
        remote.saveScenario(userId, scenario)

    override suspend fun toggleScenarioPin(userId: String, scenarioId: String, pinned: Boolean): Result<Unit> =
        remote.toggleScenarioPin(userId, scenarioId, pinned)

    override suspend fun deleteScenario(userId: String, scenarioId: String): Result<Unit> =
        remote.deleteScenario(userId, scenarioId)

    override suspend fun createChatFromScenario(
        userId: String,
        scenario: RpScenario,
        userPersona: UserPersona?,
        overrideProvider: LlmProvider?,
        overrideModelId: String?,
        overrideParams: GenerationParams?
    ): Result<String> = remote.createChatFromScenario(
        userId, scenario, userPersona, overrideProvider, overrideModelId, overrideParams
    )

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
    ): Result<Unit> = remote.updateChatConfig(
        userId, chatId, title, rpMode, systemPrompt, characterName,
        userPersonaName, userPersonaDesc, authorsNote, memorySummary,
        loreNotes, loreEntries, participants, provider, modelId, params, pinned,
        portraitUri, backdropUri
    )

    override suspend fun updateChatRpgState(userId: String, chatId: String, rpgState: RpgState): Result<Unit> =
        remote.updateChatRpgState(userId, chatId, rpgState)

    override suspend fun updateChatAgentSetup(userId: String, chatId: String, agentSetupJson: String): Result<Unit> =
        remote.updateChatAgentSetup(userId, chatId, agentSetupJson)

    override suspend fun updateAllChatsProvider(
        userId: String,
        provider: LlmProvider,
        modelId: String
    ): Result<Int> = remote.updateAllChatsProvider(userId, provider, modelId)

    override suspend fun toggleChatPin(userId: String, chatId: String, pinned: Boolean): Result<Unit> =
        remote.toggleChatPin(userId, chatId, pinned)

    override suspend fun deleteChat(userId: String, chatId: String): Result<Unit> =
        remote.deleteChat(userId, chatId)

    override suspend fun branchChatFromMessage(
        userId: String,
        sourceChat: RpChat,
        messagesUpToTarget: List<RpMessage>,
        customBranchLabel: String
    ): Result<String> = remote.branchChatFromMessage(userId, sourceChat, messagesUpToTarget, customBranchLabel)

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
    ): Result<String> = remote.addMessage(
        userId, chatId, role, speakerName, content, messageKind, modelUsed, orderIndex, newTotalCount, agentTrace
    )

    override suspend fun updateMessageSceneArt(
        userId: String,
        chatId: String,
        messageId: String,
        sceneArtUri: String
    ): Result<Unit> = remote.updateMessageSceneArt(userId, chatId, messageId, sceneArtUri)

    override suspend fun addMessageVariant(
        userId: String,
        chatId: String,
        message: RpMessage,
        newVariantText: String,
        modelUsed: String,
        isLastMessage: Boolean
    ): Result<Int> = remote.addMessageVariant(userId, chatId, message, newVariantText, modelUsed, isLastMessage)

    override suspend fun selectMessageVariant(
        userId: String,
        chatId: String,
        message: RpMessage,
        targetIndex: Int,
        isLastMessage: Boolean
    ): Result<Unit> = remote.selectMessageVariant(userId, chatId, message, targetIndex, isLastMessage)

    override suspend fun updateMessageContent(
        userId: String,
        chatId: String,
        message: RpMessage,
        newContent: String,
        isLastMessage: Boolean
    ): Result<Unit> = remote.updateMessageContent(userId, chatId, message, newContent, isLastMessage)

    override suspend fun deleteMessagesFrom(
        userId: String,
        chatId: String,
        messageIdsToDelete: List<String>,
        remainingCount: Long,
        remainingLastPreview: String
    ): Result<Unit> = remote.deleteMessagesFrom(
        userId, chatId, messageIdsToDelete, remainingCount, remainingLastPreview
    )

    override suspend fun savePersona(userId: String, persona: UserPersona): Result<String> =
        remote.savePersona(userId, persona)

    override suspend fun setDefaultPersona(userId: String, personaId: String, allPersonaIds: List<String>): Result<Unit> =
        remote.setDefaultPersona(userId, personaId, allPersonaIds)

    override suspend fun deletePersona(userId: String, personaId: String): Result<Unit> =
        remote.deletePersona(userId, personaId)

    override suspend fun fetchAllUserDataForExport(userId: String): Result<ExportWorkspaceBundle> =
        remote.fetchAllUserDataForExport(userId)

    override suspend fun importChatWithMessages(
        userId: String,
        chat: RpChat,
        messages: List<RpMessage>
    ): Result<String> = remote.importChatWithMessages(userId, chat, messages)
}
