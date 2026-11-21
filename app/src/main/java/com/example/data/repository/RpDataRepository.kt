package com.example.data.repository

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

interface RpDataRepository {
    fun observeScenarios(userId: String): Flow<List<RpScenario>>
    fun observeChats(userId: String): Flow<List<RpChat>>
    fun observeChat(userId: String, chatId: String): Flow<RpChat?>
    fun observeMessages(userId: String, chatId: String): Flow<List<RpMessage>>
    fun observePersonas(userId: String): Flow<List<UserPersona>>

    suspend fun ensureStarterDataIfEmpty(userId: String): Result<Unit>

    suspend fun saveScenario(userId: String, scenario: RpScenario): Result<String>
    suspend fun toggleScenarioPin(userId: String, scenarioId: String, pinned: Boolean): Result<Unit>
    suspend fun deleteScenario(userId: String, scenarioId: String): Result<Unit>

    suspend fun createChatFromScenario(
        userId: String,
        scenario: RpScenario,
        userPersona: UserPersona?,
        overrideProvider: LlmProvider? = null,
        overrideModelId: String? = null,
        overrideParams: GenerationParams? = null
    ): Result<String>

    suspend fun updateChatConfig(
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
        portraitUri: String = "",
        backdropUri: String = ""
    ): Result<Unit>

    suspend fun updateChatRpgState(
        userId: String,
        chatId: String,
        rpgState: RpgState
    ): Result<Unit>

    suspend fun updateChatAgentSetup(
        userId: String,
        chatId: String,
        agentSetupJson: String
    ): Result<Unit>

    suspend fun updateAllChatsProvider(
        userId: String,
        provider: LlmProvider,
        modelId: String
    ): Result<Int>

    suspend fun toggleChatPin(userId: String, chatId: String, pinned: Boolean): Result<Unit>
    suspend fun deleteChat(userId: String, chatId: String): Result<Unit>

    suspend fun branchChatFromMessage(
        userId: String,
        sourceChat: RpChat,
        messagesUpToTarget: List<RpMessage>,
        customBranchLabel: String = ""
    ): Result<String>

    suspend fun addMessage(
        userId: String,
        chatId: String,
        role: String,
        speakerName: String,
        content: String,
        messageKind: MessageKind,
        modelUsed: String,
        orderIndex: Long,
        newTotalCount: Long,
        agentTrace: List<String> = emptyList()
    ): Result<String>

    suspend fun updateMessageSceneArt(
        userId: String,
        chatId: String,
        messageId: String,
        sceneArtUri: String
    ): Result<Unit>

    suspend fun addMessageVariant(
        userId: String,
        chatId: String,
        message: RpMessage,
        newVariantText: String,
        modelUsed: String,
        isLastMessage: Boolean
    ): Result<Int>

    suspend fun selectMessageVariant(
        userId: String,
        chatId: String,
        message: RpMessage,
        targetIndex: Int,
        isLastMessage: Boolean
    ): Result<Unit>

    suspend fun updateMessageContent(
        userId: String,
        chatId: String,
        message: RpMessage,
        newContent: String,
        isLastMessage: Boolean
    ): Result<Unit>

    suspend fun deleteMessagesFrom(
        userId: String,
        chatId: String,
        messageIdsToDelete: List<String>,
        remainingCount: Long,
        remainingLastPreview: String
    ): Result<Unit>

    suspend fun savePersona(userId: String, persona: UserPersona): Result<String>
    suspend fun setDefaultPersona(userId: String, personaId: String, allPersonaIds: List<String>): Result<Unit>
    suspend fun deletePersona(userId: String, personaId: String): Result<Unit>

    suspend fun fetchAllUserDataForExport(userId: String): Result<ExportWorkspaceBundle>
    suspend fun importChatWithMessages(
        userId: String,
        chat: RpChat,
        messages: List<RpMessage>
    ): Result<String>
}
