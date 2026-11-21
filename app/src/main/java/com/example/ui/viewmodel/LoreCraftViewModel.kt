package com.example.ui.viewmodel

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.ai.AgentPipelineEngine
import com.example.data.ai.AgentPipelineEvent
import com.example.data.ai.DeviceRamSnapshot
import com.example.data.ai.LlmRouterService
import com.example.data.ai.LocalRamWatchdog
import com.example.data.ai.MathEngine
import com.example.data.ai.ProviderTestResult
import com.example.data.ai.RpgUpdateDelta
import com.example.data.ai.SemanticMarkupEngine
import com.example.data.ai.SemanticSegment
import com.example.data.export.GitHubGistService
import com.example.data.export.JsonExportManager
import com.example.data.export.ParsedCharacterCard
import com.example.data.export.PublishedGistResult
import com.example.data.export.SillyTavernCardParser
import com.example.data.local.AppLocalSettings
import com.example.data.local.PreferencesRepository
import com.example.data.local.SceneArtManager
import com.example.data.model.AgentConfig
import com.example.data.model.AgentPipelinePresets
import com.example.data.model.AgentRole
import com.example.data.model.AgentSetup
import com.example.data.model.AgentSetupCodec
import com.example.data.model.CombatState
import com.example.data.model.ExpandedScenarioDraft
import com.example.data.model.FreeAiPresets
import com.example.data.model.FreeModelCatalog
import com.example.data.model.GenerationParams
import com.example.data.model.LlmProvider
import com.example.data.model.LocalModelProfile
import com.example.data.model.LoreEntry
import com.example.data.model.MessageKind
import com.example.data.model.ModelCatalogItem
import com.example.data.model.ModelProbeStatus
import com.example.data.model.ProviderEndpointConfig
import com.example.data.model.RpChat
import com.example.data.model.RpMessage
import com.example.data.model.RpMode
import com.example.data.model.RpScenario
import com.example.data.model.RpgState
import com.example.data.model.SafetyLevel
import com.example.data.model.SemanticEntityType
import com.example.data.model.ThemeMode
import com.example.data.model.UserPersona
import com.example.data.repository.RpDataRepository
import com.example.device.AmbientScenePreset
import com.example.device.HapticEvent
import com.example.diagnostics.AppLogBuffer
import com.example.ui.theme.ReaderFontStyle
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlin.random.Random

sealed interface UiState<out T> {
    data object Loading : UiState<Nothing>
    data class Success<T>(val data: T) : UiState<T>
    data class Error(val message: String) : UiState<Nothing>
}

data class GenerationUiState(
    val isGenerating: Boolean = false,
    val streamingText: String = "",
    val swipingMessageId: String? = null,
    val errorMessage: String? = null,
    val isCompressingMemory: Boolean = false,
    val stageLabel: String = "",
    val activeAgents: List<String> = emptyList()
)

private const val TAG = "LoreCraftVM"
private const val STOP_TIMEOUT_MS = 5000L

@OptIn(ExperimentalCoroutinesApi::class)
class LoreCraftViewModel(
    private val repository: RpDataRepository,
    private val preferencesRepository: PreferencesRepository,
    private val llmRouter: LlmRouterService,
    val currentUserId: String,
    val isOfflineMode: Boolean = false,
    val firebaseError: String? = null,
    private val appContext: Context? = null,
    private val onSwitchToCloudRequested: () -> Unit = {}
) : ViewModel() {

    private val pipelineEngine = AgentPipelineEngine(llmRouter)

    private val _toastEvents = MutableSharedFlow<String>(extraBufferCapacity = 8)
    val toastEvents: SharedFlow<String> = _toastEvents.asSharedFlow()

    private val _hapticEvents = MutableSharedFlow<HapticEvent>(extraBufferCapacity = 12)
    val hapticEvents: SharedFlow<HapticEvent> = _hapticEvents.asSharedFlow()

    private val _providerCooldowns = MutableStateFlow<Map<LlmProvider, Int>>(emptyMap())
    val providerCooldowns: StateFlow<Map<LlmProvider, Int>> = _providerCooldowns.asStateFlow()

    private val _isProbingLocalGguf = MutableStateFlow(false)
    val isProbingLocalGguf: StateFlow<Boolean> = _isProbingLocalGguf.asStateFlow()

    private val _localGgufModels = MutableStateFlow<List<String>>(emptyList())
    val localGgufModels: StateFlow<List<String>> = _localGgufModels.asStateFlow()

    private var cooldownTickerJob: Job? = null

    val localSettings: StateFlow<AppLocalSettings> = preferencesRepository.settingsFlow
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
            initialValue = AppLocalSettings()
        )

    val scenariosState: StateFlow<UiState<List<RpScenario>>> = repository.observeScenarios(currentUserId)
        .toUiStateFlow("Не удалось загрузить сценарии")
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), UiState.Loading)

    val chatsState: StateFlow<UiState<List<RpChat>>> = repository.observeChats(currentUserId)
        .toUiStateFlow("Не удалось загрузить историю чатов")
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), UiState.Loading)

    val personasState: StateFlow<UiState<List<UserPersona>>> = repository.observePersonas(currentUserId)
        .toUiStateFlow("Не удалось загрузить персоны")
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), UiState.Loading)

    private val _activeChatId = MutableStateFlow<String?>(null)
    val activeChatId: StateFlow<String?> = _activeChatId.asStateFlow()

    val activeChat: StateFlow<RpChat?> = _activeChatId
        .flatMapLatest { chatId ->
            if (chatId.isNullOrBlank()) {
                flowOf(null)
            } else {
                repository.observeChat(currentUserId, chatId)
                    .catch { error ->
                        val fsErr = unwrapFirestoreException(error)
                        if (fsErr == null && error is CancellationException) throw error
                        AppLogBuffer.w(TAG, "Error observing active chat", fsErr ?: error)
                        emit(null)
                    }
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), null)

    val activeMessagesState: StateFlow<UiState<List<RpMessage>>> = _activeChatId
        .flatMapLatest { chatId ->
            if (chatId.isNullOrBlank()) {
                flowOf(UiState.Success(emptyList()))
            } else {
                repository.observeMessages(currentUserId, chatId)
                    .toUiStateFlow("Не удалось загрузить сообщения")
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), UiState.Loading)

    private val _generationState = MutableStateFlow(GenerationUiState())
    val generationState: StateFlow<GenerationUiState> = _generationState.asStateFlow()

    private val _catalogModels = MutableStateFlow(FreeModelCatalog.curated())
    val catalogModelsState: StateFlow<List<ModelCatalogItem>> = _catalogModels.asStateFlow()

    private val _isDiscoveringModels = MutableStateFlow(false)
    val isDiscoveringModels: StateFlow<Boolean> = _isDiscoveringModels.asStateFlow()

    private val _discoveryProgress = MutableStateFlow("")
    val discoveryProgress: StateFlow<String> = _discoveryProgress.asStateFlow()

    private val _isExpandingDraft = MutableStateFlow(false)
    val isExpandingDraft: StateFlow<Boolean> = _isExpandingDraft.asStateFlow()

    private val _suggestedReplies = MutableStateFlow<List<String>>(emptyList())
    val suggestedReplies: StateFlow<List<String>> = _suggestedReplies.asStateFlow()

    private val _isGeneratingSuggestions = MutableStateFlow(false)
    val isGeneratingSuggestions: StateFlow<Boolean> = _isGeneratingSuggestions.asStateFlow()

    private val _deepeningMessageId = MutableStateFlow<String?>(null)
    val deepeningMessageId: StateFlow<String?> = _deepeningMessageId.asStateFlow()

    private val _generatingArtMessageId = MutableStateFlow<String?>(null)
    val generatingArtMessageId: StateFlow<String?> = _generatingArtMessageId.asStateFlow()

    private val _autoAppliedDeltaMessageIds = MutableStateFlow<Set<String>>(emptySet())
    val autoAppliedDeltaMessageIds: StateFlow<Set<String>> = _autoAppliedDeltaMessageIds.asStateFlow()

    private val _deviceRamSnapshot = MutableStateFlow(
        LocalRamWatchdog.inspectRam(appContext, LocalModelProfile().ramPreset)
    )
    val deviceRamSnapshot: StateFlow<DeviceRamSnapshot> = _deviceRamSnapshot.asStateFlow()

    val customAgentsLibrary: StateFlow<List<AgentConfig>> = localSettings
        .map { AgentSetupCodec.decodeCustomLibrary(it.customAgentsLibraryJson) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), emptyList())

    private var activeGenerationJob: Job? = null

    init {
        AppLogBuffer.i(TAG, "ViewModel init: uid=$currentUserId, offline=$isOfflineMode")
        refreshRamSnapshot()
        viewModelScope.launch {
            repository.ensureStarterDataIfEmpty(currentUserId)
                .onFailure { err -> AppLogBuffer.w(TAG, "Starter seed failed", err) }
        }
    }

    fun refreshRamSnapshot() {
        _deviceRamSnapshot.value = LocalRamWatchdog.inspectRam(
            appContext,
            localSettings.value.localModelProfile.ramPreset
        )
    }

    private fun <T> Flow<T>.toUiStateFlow(fallbackError: String): Flow<UiState<T>> =
        this.map<T, UiState<T>> { UiState.Success(it) }
            .catch { error ->
                val fsErr = unwrapFirestoreException(error)
                if (fsErr == null && error is CancellationException) throw error
                val root = fsErr ?: error
                AppLogBuffer.w(TAG, fallbackError, root)
                emit(UiState.Error(root.localizedMessage ?: fallbackError))
            }

    private fun unwrapFirestoreException(error: Throwable): FirebaseFirestoreException? =
        generateSequence(error) { it.cause }
            .filterIsInstance<FirebaseFirestoreException>()
            .firstOrNull()

    fun requestCloudSignIn() {
        onSwitchToCloudRequested()
    }

    fun selectChat(chatId: String?) {
        if (_activeChatId.value != chatId) {
            stopGeneration(savePartial = false)
            _generationState.value = GenerationUiState()
            _suggestedReplies.value = emptyList()
            _activeChatId.value = chatId
        }
    }

    fun clearGenerationError() {
        _generationState.value = _generationState.value.copy(errorMessage = null)
    }

    // --- Theme & Local Preferences ---

    fun setThemeMode(mode: ThemeMode) {
        viewModelScope.launch { preferencesRepository.setThemeMode(mode) }
    }

    fun setStreamingEnabled(enabled: Boolean) {
        viewModelScope.launch { preferencesRepository.setStreamingEnabled(enabled) }
    }

    fun setShowModelBadges(show: Boolean) {
        viewModelScope.launch { preferencesRepository.setShowModelBadges(show) }
    }

    fun setFreeOnlyFilter(freeOnly: Boolean) {
        viewModelScope.launch { preferencesRepository.setFreeOnlyFilter(freeOnly) }
    }

    fun setSafetyLevel(level: SafetyLevel) {
        viewModelScope.launch { preferencesRepository.setSafetyLevel(level) }
    }

    fun setCreativeRpFraming(enabled: Boolean) {
        viewModelScope.launch { preferencesRepository.setCreativeRpFraming(enabled) }
    }

    fun setAutoFillNewChatWithAi(enabled: Boolean) {
        viewModelScope.launch { preferencesRepository.setAutoFillNewChatWithAi(enabled) }
    }

    fun setVisualNovelMode(enabled: Boolean) {
        viewModelScope.launch { preferencesRepository.setVisualNovelMode(enabled) }
    }

    fun setAutoSuggestActions(enabled: Boolean) {
        viewModelScope.launch { preferencesRepository.setAutoSuggestActions(enabled) }
    }

    fun setAutoSendSuggestedAction(enabled: Boolean) {
        viewModelScope.launch { preferencesRepository.setAutoSendSuggestedAction(enabled) }
    }

    fun setAutoSummarizeEveryN(everyN: Int) {
        viewModelScope.launch { preferencesRepository.setAutoSummarizeEveryN(everyN) }
    }

    fun setLocalLlmEnabled(enabled: Boolean) {
        viewModelScope.launch { preferencesRepository.setLocalLlmEnabled(enabled) }
    }

    fun setDefaultAgentPresetId(presetId: String) {
        viewModelScope.launch { preferencesRepository.setDefaultAgentPresetId(presetId) }
    }

    fun setReaderBookMode(enabled: Boolean) {
        viewModelScope.launch { preferencesRepository.setReaderBookMode(enabled) }
    }

    fun setReaderFontStyle(style: ReaderFontStyle) {
        viewModelScope.launch { preferencesRepository.setReaderFontStyle(style) }
    }

    fun setReaderFontSizeSp(sizeSp: Int) {
        viewModelScope.launch { preferencesRepository.setReaderFontSizeSp(sizeSp) }
    }

    fun setReaderLineHeight(multiplier: Float) {
        viewModelScope.launch { preferencesRepository.setReaderLineHeight(multiplier) }
    }

    fun setHapticsEnabled(enabled: Boolean) {
        viewModelScope.launch { preferencesRepository.setHapticsEnabled(enabled) }
    }

    fun setAmbientEnabled(enabled: Boolean) {
        viewModelScope.launch { preferencesRepository.setAmbientEnabled(enabled) }
    }

    fun setAmbientPreset(preset: AmbientScenePreset) {
        viewModelScope.launch { preferencesRepository.setAmbientPreset(preset) }
    }

    fun setAmbientVolume(volume: Float) {
        viewModelScope.launch { preferencesRepository.setAmbientVolume(volume) }
    }

    fun setAutoFailoverEnabled(enabled: Boolean) {
        viewModelScope.launch { preferencesRepository.setAutoFailoverEnabled(enabled) }
    }

    fun emitHaptic(event: HapticEvent) {
        _hapticEvents.tryEmit(event)
    }

    fun quickCheckAndSwitchLocalGguf() {
        if (_isProbingLocalGguf.value) return
        val settingsSnapshot = localSettings.value
        val profile = settingsSnapshot.localModelProfile
        val serverUrl = profile.serverUrl.ifBlank { LlmProvider.LOCAL_LLM.defaultBaseUrl }
        val apiKey = settingsSnapshot.configFor(LlmProvider.LOCAL_LLM).apiKey
        viewModelScope.launch {
            _isProbingLocalGguf.value = true
            val res = llmRouter.testOpenAiServerModels(
                provider = LlmProvider.LOCAL_LLM,
                baseUrl = serverUrl,
                apiKey = apiKey
            )
            _isProbingLocalGguf.value = false
            res.onSuccess { models ->
                _localGgufModels.value = models
                val chosenModel = models.firstOrNull()?.takeIf { it.isNotBlank() }
                    ?: settingsSnapshot.configFor(LlmProvider.LOCAL_LLM).customModelId.ifBlank {
                        profile.ggufDisplayName.substringBeforeLast('.').ifBlank {
                            LlmProvider.LOCAL_LLM.suggestedModels.firstOrNull() ?: "local-gguf-q4"
                        }
                    }
                preferencesRepository.setLocalLlmEnabled(true)
                val chat = activeChat.value
                if (chat != null) {
                    repository.updateChatConfig(
                        userId = currentUserId,
                        chatId = chat.id,
                        title = chat.title,
                        rpMode = chat.rpModeEnum(),
                        systemPrompt = chat.systemPrompt,
                        characterName = chat.characterName,
                        userPersonaName = chat.userPersonaName,
                        userPersonaDesc = chat.userPersonaDesc,
                        authorsNote = chat.authorsNote,
                        memorySummary = chat.memorySummary,
                        loreNotes = chat.loreNotes,
                        loreEntries = chat.loreEntries,
                        participants = chat.participants,
                        provider = LlmProvider.LOCAL_LLM,
                        modelId = chosenModel,
                        params = chat.generationParams(),
                        pinned = chat.pinned,
                        portraitUri = chat.portraitUri,
                        backdropUri = chat.backdropUri
                    )
                    _toastEvents.tryEmit("GGUF онлайн ($chosenModel) — чат переключён на локальную LLM")
                } else {
                    preferencesRepository.setDefaultModel(LlmProvider.LOCAL_LLM, chosenModel)
                    _toastEvents.tryEmit("Локальная GGUF готова: $chosenModel")
                }
            }.onFailure { err ->
                _localGgufModels.value = emptyList()
                _toastEvents.tryEmit(
                    "Локальный GGUF-сервер ($serverUrl) не отвечает: ${err.localizedMessage ?: "проверьте Termux / llama.cpp"}"
                )
            }
        }
    }

    private fun refreshCooldownTicker() {
        val snap = llmRouter.activeCooldownSnapshot()
        _providerCooldowns.value = snap
        if (snap.isEmpty()) {
            cooldownTickerJob?.cancel()
            cooldownTickerJob = null
            return
        }
        if (cooldownTickerJob?.isActive == true) return
        cooldownTickerJob = viewModelScope.launch {
            while (true) {
                delay(1000L)
                val next = llmRouter.activeCooldownSnapshot()
                _providerCooldowns.value = next
                if (next.isEmpty()) break
            }
        }
    }

    fun saveCustomAgentToLibrary(agent: AgentConfig) {
        val normalized = agent.copy(isCustom = true, role = AgentRole.CUSTOM)
        val current = customAgentsLibrary.value.toMutableList()
        val idx = current.indexOfFirst { it.id == normalized.id }
        if (idx >= 0) {
            current[idx] = normalized
        } else {
            current.add(normalized)
        }
        viewModelScope.launch {
            preferencesRepository.saveCustomAgentsLibraryJson(
                AgentSetupCodec.encodeCustomLibrary(current)
            )
            _toastEvents.tryEmit("Агент «${normalized.displayName()}» сохранён в библиотеку")
        }
    }

    fun deleteCustomAgentFromLibrary(agentId: String) {
        val next = customAgentsLibrary.value.filterNot { it.id == agentId }
        viewModelScope.launch {
            preferencesRepository.saveCustomAgentsLibraryJson(
                AgentSetupCodec.encodeCustomLibrary(next)
            )
            _toastEvents.tryEmit("Пользовательский агент удалён")
        }
    }

    fun updateChatAgentSetup(setup: AgentSetup) {
        val chat = activeChat.value ?: return
        val encoded = AgentSetupCodec.encode(setup)
        viewModelScope.launch {
            repository.updateChatAgentSetup(currentUserId, chat.id, encoded)
                .onSuccess { _toastEvents.tryEmit("Состав агентов обновлён (${setup.activeAgentCount()})") }
                .onFailure { _toastEvents.tryEmit(it.localizedMessage ?: "Ошибка сохранения агентов") }
        }
    }

    fun setOfflineModePreferred(offline: Boolean) {
        viewModelScope.launch { preferencesRepository.setOfflineModePreferred(offline) }
    }

    fun saveLocalModelProfile(profile: LocalModelProfile) {
        viewModelScope.launch {
            preferencesRepository.saveLocalModelProfile(profile)
            _toastEvents.tryEmit("Локальный GGUF-профиль сохранён")
        }
    }

    fun saveImageEndpointTemplate(template: String) {
        viewModelScope.launch {
            preferencesRepository.saveImageEndpointTemplate(template)
            _toastEvents.tryEmit("Эндпоинт генерации артов обновлён")
        }
    }

    fun testLocalServer(
        provider: LlmProvider = LlmProvider.CUSTOM,
        baseUrl: String,
        apiKey: String = "",
        onResult: (Result<List<String>>) -> Unit
    ) {
        viewModelScope.launch {
            val res = llmRouter.testOpenAiServerModels(
                baseUrl = baseUrl,
                apiKey = apiKey,
                provider = provider
            )
            onResult(res)
        }
    }

    fun testProviderConnection(
        provider: LlmProvider,
        baseUrl: String,
        apiKey: String,
        modelId: String,
        onResult: (Result<ProviderTestResult>) -> Unit
    ) {
        viewModelScope.launch {
            val res = llmRouter.testChatCompletion(
                provider = provider,
                baseUrl = baseUrl,
                apiKey = apiKey,
                modelId = modelId,
                settings = localSettings.value
            )
            onResult(res)
        }
    }

    fun refreshModelCatalog(probeFreeLive: Boolean = true) {
        if (_isDiscoveringModels.value) return
        viewModelScope.launch {
            val snapshot = localSettings.value
            _isDiscoveringModels.value = true
            _discoveryProgress.value = "Обновление списка бесплатных моделей..."
            val previousByKey = _catalogModels.value.associateBy { it.stableKey }
            val discovered = llmRouter.discoverCatalogModels(snapshot).map { item ->
                val prev = previousByKey[item.stableKey]
                if (prev != null && prev.status == ModelProbeStatus.ONLINE && item.status == ModelProbeStatus.UNKNOWN) {
                    item.copy(status = prev.status, latencyMs = prev.latencyMs, statusNote = prev.statusNote)
                } else {
                    item
                }
            }
            _catalogModels.value = discovered

            if (probeFreeLive) {
                val probeTargets = buildList {
                    val hasPollinationsKey = snapshot.configFor(LlmProvider.POLLINATIONS).apiKey.isNotBlank()
                    val pollItems = discovered.filter { it.provider == LlmProvider.POLLINATIONS }
                    addAll(if (hasPollinationsKey) pollItems.take(3) else pollItems.take(1))

                    if (snapshot.configFor(LlmProvider.OPENROUTER).apiKey.isNotBlank()) {
                        addAll(discovered.filter { it.provider == LlmProvider.OPENROUTER }.take(3))
                    }
                    if (snapshot.configFor(LlmProvider.GROQ).apiKey.isNotBlank()) {
                        addAll(discovered.filter { it.provider == LlmProvider.GROQ }.take(2))
                    }
                    if (snapshot.configFor(LlmProvider.GEMINI_DIRECT).apiKey.isNotBlank()) {
                        addAll(discovered.filter { it.provider == LlmProvider.GEMINI_DIRECT }.take(1))
                    }
                    addAll(discovered.filter { it.provider == LlmProvider.GEMINI_AI_LOGIC }.take(1))
                }.distinctBy { it.stableKey }

                if (probeTargets.isNotEmpty()) {
                    _catalogModels.value = _catalogModels.value.map { item ->
                        if (probeTargets.any { it.stableKey == item.stableKey }) {
                            item.copy(status = ModelProbeStatus.PROBING)
                        } else item
                    }
                    val semaphore = Semaphore(3)
                    coroutineScope {
                        probeTargets.mapIndexed { idx, target ->
                            async {
                                semaphore.withPermit {
                                    _discoveryProgress.value = "Проверка ${idx + 1}/${probeTargets.size}: ${target.modelId}"
                                    val probed = llmRouter.probeCatalogItem(target, snapshot)
                                    _catalogModels.value = _catalogModels.value.map { cur ->
                                        if (cur.stableKey == probed.stableKey) probed else cur
                                    }
                                }
                            }
                        }.awaitAll()
                    }
                }
            }
            val onlineCount = _catalogModels.value.count { it.status == ModelProbeStatus.ONLINE }
            _discoveryProgress.value = "Найдено ${_catalogModels.value.size} моделей • Онлайн: $onlineCount"
            _isDiscoveringModels.value = false
        }
    }

    fun probeSingleModel(item: ModelCatalogItem) {
        viewModelScope.launch {
            val snapshot = localSettings.value
            _catalogModels.value = _catalogModels.value.map { cur ->
                if (cur.stableKey == item.stableKey) cur.copy(status = ModelProbeStatus.PROBING, statusNote = "") else cur
            }
            val updated = llmRouter.probeCatalogItem(item, snapshot)
            _catalogModels.value = _catalogModels.value.map { cur ->
                if (cur.stableKey == updated.stableKey) updated else cur
            }
        }
    }

    fun expandScenarioDraft(
        titleHint: String,
        subtitleHint: String = "",
        rpMode: RpMode = RpMode.CHARACTER_1ON1,
        characterHint: String = "",
        systemPromptHint: String = "",
        firstMessageHint: String = "",
        freeformIdea: String = "",
        onResult: (Result<ExpandedScenarioDraft>) -> Unit
    ) {
        if (_isExpandingDraft.value) return
        viewModelScope.launch {
            _isExpandingDraft.value = true
            val res = llmRouter.expandScenarioWithAi(
                titleHint = titleHint,
                subtitleHint = subtitleHint,
                rpMode = rpMode,
                characterHint = characterHint,
                systemPromptHint = systemPromptHint,
                firstMessageHint = firstMessageHint,
                freeformIdea = freeformIdea,
                settings = localSettings.value
            )
            _isExpandingDraft.value = false
            onResult(res)
        }
    }

    fun setDefaultModel(
        provider: LlmProvider,
        modelId: String,
        applyToExistingChats: Boolean = true
    ) {
        viewModelScope.launch {
            val resolvedModel = modelId.trim().ifEmpty {
                provider.suggestedModels.firstOrNull() ?: "openai"
            }
            preferencesRepository.setDefaultModel(provider, resolvedModel)
            if (applyToExistingChats) {
                repository.updateAllChatsProvider(currentUserId, provider, resolvedModel)
                    .onSuccess { updatedCount ->
                        val suffix = if (updatedCount > 0) " (обновлено чатов: $updatedCount)" else ""
                        _toastEvents.tryEmit("Модель $resolvedModel выбрана$suffix")
                    }
                    .onFailure {
                        _toastEvents.tryEmit("Модель по умолчанию: $resolvedModel")
                    }
            } else {
                _toastEvents.tryEmit("Модель по умолчанию: $resolvedModel")
            }
        }
    }

    fun saveDefaultParams(params: GenerationParams) {
        viewModelScope.launch {
            preferencesRepository.saveDefaultParams(params)
            _toastEvents.tryEmit("Параметры генерации сохранены")
        }
    }

    fun saveProviderConfig(config: ProviderEndpointConfig) {
        viewModelScope.launch {
            preferencesRepository.saveProviderConfig(config)
            _toastEvents.tryEmit("Настройки ${config.provider.displayName} сохранены локально")
        }
    }

    fun saveGithubPat(token: String) {
        viewModelScope.launch {
            preferencesRepository.saveGithubPat(token)
            _toastEvents.tryEmit(
                if (token.isBlank()) "Токен GitHub удалён" else "GitHub токен сохранён локально"
            )
        }
    }

    // --- Scenarios & Personas ---

    fun saveScenario(scenario: RpScenario, onSaved: (String) -> Unit = {}) {
        viewModelScope.launch {
            val res = repository.saveScenario(currentUserId, scenario)
            res.onSuccess { id ->
                _toastEvents.tryEmit(if (isOfflineMode) "Сценарий сохранён локально" else "Сценарий сохранён в облако")
                onSaved(id)
            }.onFailure { err ->
                _toastEvents.tryEmit(err.localizedMessage ?: "Ошибка сохранения сценария")
            }
        }
    }

    fun saveImportedScenario(
        scenario: RpScenario,
        launchImmediately: Boolean,
        onLaunchedChat: (String) -> Unit = {}
    ) {
        viewModelScope.launch {
            val res = repository.saveScenario(currentUserId, scenario)
            res.onSuccess { newId ->
                val savedScenario = scenario.copy(id = newId)
                if (launchImmediately) {
                    startChatFromScenario(savedScenario, onLaunchedChat)
                } else {
                    _toastEvents.tryEmit("Карточка «${scenario.title}» добавлена в сценарии")
                }
            }.onFailure { err ->
                _toastEvents.tryEmit(err.localizedMessage ?: "Ошибка сохранения карточки")
            }
        }
    }

    fun toggleScenarioPin(scenario: RpScenario) {
        viewModelScope.launch {
            repository.toggleScenarioPin(currentUserId, scenario.id, !scenario.pinned)
        }
    }

    fun deleteScenario(scenarioId: String) {
        viewModelScope.launch {
            repository.deleteScenario(currentUserId, scenarioId)
                .onSuccess { _toastEvents.tryEmit("Сценарий удалён") }
                .onFailure { _toastEvents.tryEmit(it.localizedMessage ?: "Ошибка удаления") }
        }
    }

    fun savePersona(persona: UserPersona) {
        viewModelScope.launch {
            repository.savePersona(currentUserId, persona)
                .onSuccess { _toastEvents.tryEmit("Персона «${persona.name}» сохранена") }
                .onFailure { _toastEvents.tryEmit(it.localizedMessage ?: "Ошибка сохранения персоны") }
        }
    }

    fun setDefaultPersona(personaId: String) {
        val currentList = (personasState.value as? UiState.Success)?.data ?: return
        viewModelScope.launch {
            repository.setDefaultPersona(currentUserId, personaId, currentList.map { it.id })
        }
    }

    fun deletePersona(personaId: String) {
        viewModelScope.launch {
            repository.deletePersona(currentUserId, personaId)
                .onSuccess { _toastEvents.tryEmit("Персона удалена") }
        }
    }

    // --- Chats Management & Tactical RPG HUD ---

    fun startChatFromScenario(scenario: RpScenario, onCreated: (String) -> Unit) {
        val settings = localSettings.value
        val personas = (personasState.value as? UiState.Success)?.data.orEmpty()
        val defaultPersona = personas.firstOrNull { it.activeDefault } ?: personas.firstOrNull()
        viewModelScope.launch {
            repository.createChatFromScenario(
                userId = currentUserId,
                scenario = scenario,
                userPersona = defaultPersona,
                overrideProvider = settings.defaultProvider,
                overrideModelId = settings.defaultModelId,
                overrideParams = settings.defaultParams
            ).onSuccess { chatId ->
                selectChat(chatId)
                onCreated(chatId)
            }.onFailure { err ->
                _toastEvents.tryEmit(err.localizedMessage ?: "Не удалось создать чат")
            }
        }
    }

    fun createQuickCustomChat(
        title: String,
        rpMode: RpMode,
        characterName: String,
        systemPrompt: String,
        firstMessage: String,
        freeformIdea: String = "",
        autoFillWithAi: Boolean = false,
        preExpandedDraft: ExpandedScenarioDraft? = null,
        agentSetup: AgentSetup? = null,
        onCreated: (String) -> Unit
    ) {
        val settings = localSettings.value
        val personas = (personasState.value as? UiState.Success)?.data.orEmpty()
        val defaultPersona = personas.firstOrNull { it.activeDefault } ?: personas.firstOrNull()

        viewModelScope.launch {
            val resolvedDraft = when {
                preExpandedDraft != null -> preExpandedDraft
                autoFillWithAi && (
                    title.isBlank() ||
                        characterName.isBlank() ||
                        firstMessage.isBlank() ||
                        systemPrompt.isBlank() ||
                        freeformIdea.isNotBlank()
                    ) -> {
                    _isExpandingDraft.value = true
                    val res = llmRouter.expandScenarioWithAi(
                        titleHint = title,
                        rpMode = rpMode,
                        characterHint = characterName,
                        systemPromptHint = systemPrompt,
                        firstMessageHint = firstMessage,
                        freeformIdea = freeformIdea,
                        settings = settings
                    )
                    _isExpandingDraft.value = false
                    res.onFailure {
                        _toastEvents.tryEmit("Автозаполнение AI недоступно (${it.localizedMessage ?: "оффлайн"}), создан базовый чат.")
                    }.getOrNull()
                }
                else -> null
            }

            val resolvedAgentSetup = agentSetup ?: AgentSetup(
                presetId = settings.defaultAgentPresetId,
                agents = AgentPipelinePresets.forPreset(settings.defaultAgentPresetId)
            )
            val resolvedSetupJson = AgentSetupCodec.encode(resolvedAgentSetup)

            val finalTitle = title.trim().ifBlank {
                resolvedDraft?.title?.ifBlank { null }
                    ?: characterName.trim().ifBlank { "Новый RP-чат" }
            }
            val finalChar = characterName.trim().ifBlank {
                resolvedDraft?.characterName?.ifBlank { null }
                    ?: finalTitle
            }
            val finalSysPrompt = buildString {
                val base = systemPrompt.trim().ifBlank {
                    resolvedDraft?.systemPrompt.orEmpty()
                }.ifBlank { "Ты — выразительный ролевой партнёр." }
                append(base)
                val bio = resolvedDraft?.characterBio.orEmpty().trim()
                if (bio.isNotBlank() && !base.contains(bio.take(24))) {
                    append("\n\n[Характер и предыстория $finalChar]\n")
                    append(bio)
                }
            }
            val finalFirstMsg = firstMessage.trim().ifBlank {
                resolvedDraft?.firstMessage.orEmpty()
            }
            val baseRpg = resolvedDraft?.rpgState ?: if (rpMode == RpMode.GAME_MASTER || resolvedAgentSetup.hasRpgPipeline()) {
                RpgState(enabled = true)
            } else {
                RpgState(enabled = false)
            }
            val finalRpg = if (resolvedAgentSetup.hasRpgPipeline()) baseRpg.copy(enabled = true) else baseRpg

            val scenario = RpScenario(
                id = "custom",
                title = finalTitle,
                subtitle = resolvedDraft?.subtitle.orEmpty(),
                rpMode = rpMode.name,
                systemPrompt = finalSysPrompt,
                characterBio = resolvedDraft?.characterBio.orEmpty(),
                firstMessage = finalFirstMsg,
                exampleDialogue = resolvedDraft?.exampleDialogue.orEmpty(),
                loreNotes = resolvedDraft?.loreNotes.orEmpty(),
                loreEntries = resolvedDraft?.loreEntries.orEmpty(),
                participants = resolvedDraft?.participants.orEmpty(),
                tags = resolvedDraft?.tags.orEmpty(),
                defaultRpgState = finalRpg,
                defaultAgentSetupJson = resolvedSetupJson,
                defaultProvider = settings.defaultProvider.name,
                defaultModelId = settings.defaultModelId,
                temperature = settings.defaultParams.temperature,
                topP = settings.defaultParams.topP,
                topK = settings.defaultParams.topK.toLong(),
                maxTokens = settings.defaultParams.maxTokens.toLong(),
                contextWindowMessages = settings.defaultParams.contextWindowMessages.toLong(),
                presencePenalty = settings.defaultParams.presencePenalty,
                frequencyPenalty = settings.defaultParams.frequencyPenalty,
                stopSequences = settings.defaultParams.stopSequences
            )
            val adjustedScenario = scenario
            repository.createChatFromScenario(
                userId = currentUserId,
                scenario = adjustedScenario,
                userPersona = defaultPersona,
                overrideProvider = settings.defaultProvider,
                overrideModelId = settings.defaultModelId,
                overrideParams = settings.defaultParams
            ).onSuccess { chatId ->
                if (finalChar.isNotBlank() && finalChar != adjustedScenario.title) {
                    repository.updateChatConfig(
                        userId = currentUserId,
                        chatId = chatId,
                        title = finalTitle,
                        rpMode = rpMode,
                        systemPrompt = finalSysPrompt,
                        characterName = finalChar,
                        userPersonaName = defaultPersona?.name?.ifBlank { "Странник" } ?: "Странник",
                        userPersonaDesc = defaultPersona?.description.orEmpty(),
                        authorsNote = "",
                        memorySummary = "",
                        loreNotes = adjustedScenario.loreNotes,
                        loreEntries = adjustedScenario.loreEntries,
                        participants = adjustedScenario.participants,
                        provider = settings.defaultProvider,
                        modelId = settings.defaultModelId,
                        params = settings.defaultParams,
                        pinned = false
                    )
                }
                selectChat(chatId)
                onCreated(chatId)
            }.onFailure { err ->
                _toastEvents.tryEmit(err.localizedMessage ?: "Не удалось создать чат")
            }
        }
    }

    fun updateActiveChatConfig(
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
        portraitUri: String = activeChat.value?.portraitUri.orEmpty(),
        backdropUri: String = activeChat.value?.backdropUri.orEmpty(),
        agentSetupJson: String? = null
    ) {
        val chat = activeChat.value ?: return
        viewModelScope.launch {
            repository.updateChatConfig(
                userId = currentUserId,
                chatId = chat.id,
                title = title,
                rpMode = rpMode,
                systemPrompt = systemPrompt,
                characterName = characterName,
                userPersonaName = userPersonaName,
                userPersonaDesc = userPersonaDesc,
                authorsNote = authorsNote,
                memorySummary = memorySummary,
                loreNotes = loreNotes,
                loreEntries = loreEntries,
                participants = participants,
                provider = provider,
                modelId = modelId,
                params = params,
                pinned = chat.pinned,
                portraitUri = portraitUri,
                backdropUri = backdropUri
            ).onSuccess {
                if (agentSetupJson != null) {
                    repository.updateChatAgentSetup(currentUserId, chat.id, agentSetupJson)
                }
                _toastEvents.tryEmit("Настройки чата обновлены")
            }.onFailure { err ->
                _toastEvents.tryEmit(err.localizedMessage ?: "Ошибка обновления чата")
            }
        }
    }

    fun updateRpgState(newState: RpgState) {
        val chat = activeChat.value ?: return
        viewModelScope.launch {
            repository.updateChatRpgState(currentUserId, chat.id, newState)
                .onFailure { err ->
                    _toastEvents.tryEmit(err.localizedMessage ?: "Не удалось обновить RPG-лист")
                }
        }
    }

    fun applyRpgUpdateDelta(delta: RpgUpdateDelta) {
        val chat = activeChat.value ?: return
        val updated = delta.applyTo(
            state = chat.rpgState,
            customMeters = chat.agentSetup().simulation.customMeters
        )
        viewModelScope.launch {
            repository.updateChatRpgState(currentUserId, chat.id, updated)
                .onSuccess {
                    val isWarning = delta.hpDelta < 0L || delta.meterDeltas.values.any { it < 0L }
                    _hapticEvents.tryEmit(if (isWarning) HapticEvent.DAMAGE_OR_WARN else HapticEvent.METER_CHANGE)
                    _toastEvents.tryEmit("Состояние: ${delta.summaryText()}")
                }
                .onFailure { _toastEvents.tryEmit(it.localizedMessage ?: "Ошибка обновления листа сцены") }
        }
    }

    fun moveToLocation(nodeId: String, notifyGm: Boolean = true) {
        val chat = activeChat.value ?: return
        val rpg = chat.rpgState
        val targetNode = rpg.locations.firstOrNull { it.id == nodeId } ?: return
        val updatedLocations = rpg.locations.map { loc ->
            if (loc.id == nodeId) loc.copy(visited = true) else loc
        }
        val updatedRpg = rpg.copy(
            enabled = true,
            currentLocationId = nodeId,
            locations = updatedLocations
        )
        viewModelScope.launch {
            repository.updateChatRpgState(currentUserId, chat.id, updatedRpg)
                .onSuccess {
                    _toastEvents.tryEmit("Переход: ${targetNode.name}")
                    if (notifyGm && !_generationState.value.isGenerating) {
                        sendMessage("*Перемещается в локацию «${targetNode.name}».*", requestAiReply = true)
                    }
                }
                .onFailure { _toastEvents.tryEmit(it.localizedMessage ?: "Ошибка смены локации") }
        }
    }

    fun updateCombatState(combat: CombatState) {
        val chat = activeChat.value ?: return
        updateRpgState(chat.rpgState.copy(enabled = true, combat = combat))
    }

    fun rollTacticalD20(statName: String, modifier: Long, purpose: String = "") {
        val chat = activeChat.value ?: return
        if (_generationState.value.isGenerating) return
        val raw = Random.nextInt(1, 21)
        val total = raw + modifier
        val modStr = if (modifier >= 0) "+$modifier" else "$modifier"
        val crit = when (raw) {
            20 -> " — Критический успех!"
            1 -> " — Критический провал!"
            else -> ""
        }
        val purposePart = if (purpose.isNotBlank()) " [$purpose]" else ""
        val text = "🎲 Проверка ($statName$purposePart): d20($raw) $modStr = $total$crit"
        _hapticEvents.tryEmit(
            when (raw) {
                20 -> HapticEvent.CRIT_SUCCESS
                1 -> HapticEvent.DAMAGE_OR_WARN
                else -> HapticEvent.DICE_ROLL
            }
        )
        val currentMsgs = (activeMessagesState.value as? UiState.Success)?.data.orEmpty()
        val nextOrder = (currentMsgs.maxOfOrNull { it.orderIndex } ?: -1L) + 1L

        viewModelScope.launch {
            val res = repository.addMessage(
                userId = currentUserId,
                chatId = chat.id,
                role = "user",
                speakerName = chat.userPersonaName,
                content = text,
                messageKind = MessageKind.DICE,
                modelUsed = "",
                orderIndex = nextOrder,
                newTotalCount = (currentMsgs.size + 1).toLong()
            )
            if (res.isSuccess) {
                val updatedHistory = currentMsgs + RpMessage(
                    id = res.getOrNull().orEmpty(),
                    userId = currentUserId,
                    chatId = chat.id,
                    role = "user",
                    speakerName = chat.userPersonaName,
                    content = text,
                    variants = listOf(text),
                    messageKind = MessageKind.DICE.name,
                    orderIndex = nextOrder
                )
                launchAiGeneration(
                    chat,
                    updatedHistory,
                    overrideInstruction = "Игрок выполнил проверку характеристики ($text). Опиши итог и последствия в сцене."
                )
            }
        }
    }

    fun toggleChatPin(chat: RpChat) {
        viewModelScope.launch {
            repository.toggleChatPin(currentUserId, chat.id, !chat.pinned)
        }
    }

    fun deleteChat(chatId: String, onDeleted: () -> Unit = {}) {
        viewModelScope.launch {
            if (_activeChatId.value == chatId) {
                selectChat(null)
            }
            repository.deleteChat(currentUserId, chatId)
                .onSuccess {
                    _toastEvents.tryEmit("Чат удалён")
                    onDeleted()
                }
                .onFailure { err ->
                    _toastEvents.tryEmit(err.localizedMessage ?: "Ошибка удаления чата")
                }
        }
    }

    // --- Roleplay Messaging, Swipes, Streaming & Free Fallback ---

    fun switchToFreeFallbackAndRetry() {
        val chat = activeChat.value ?: return
        if (_generationState.value.isGenerating) return
        val settingsSnapshot = localSettings.value
        val next = FreeAiPresets.nextSmartFallback(
            currentProvider = chat.providerEnum(),
            currentModel = chat.modelId,
            isKeyConfigured = { prov -> settingsSnapshot.configFor(prov).apiKey.isNotBlank() },
            lastError = _generationState.value.errorMessage
        )
        val updatedChat = chat.copy(
            provider = next.provider.name,
            modelId = next.modelId
        )
        viewModelScope.launch {
            repository.updateChatConfig(
                userId = currentUserId,
                chatId = chat.id,
                title = chat.title,
                rpMode = chat.rpModeEnum(),
                systemPrompt = chat.systemPrompt,
                characterName = chat.characterName,
                userPersonaName = chat.userPersonaName,
                userPersonaDesc = chat.userPersonaDesc,
                authorsNote = chat.authorsNote,
                memorySummary = chat.memorySummary,
                loreNotes = chat.loreNotes,
                loreEntries = chat.loreEntries,
                participants = chat.participants,
                provider = next.provider,
                modelId = next.modelId,
                params = chat.generationParams(),
                pinned = chat.pinned,
                portraitUri = chat.portraitUri,
                backdropUri = chat.backdropUri
            ).onSuccess {
                _generationState.value = GenerationUiState()
                _toastEvents.tryEmit("Переключено на ${next.labelRu}")
                val currentMsgs = (activeMessagesState.value as? UiState.Success)?.data.orEmpty()
                launchAiGeneration(updatedChat, currentMsgs, overrideInstruction = null)
            }.onFailure { err ->
                _toastEvents.tryEmit(err.localizedMessage ?: "Не удалось переключить модель")
            }
        }
    }

    fun sendMessage(rawText: String, requestAiReply: Boolean = true) {
        val chat = activeChat.value ?: return
        val trimmed = rawText.trim()
        if (trimmed.isEmpty() || _generationState.value.isGenerating) return

        val currentMsgs = (activeMessagesState.value as? UiState.Success)?.data.orEmpty()
        val isOoc = trimmed.startsWith("((") && trimmed.endsWith("))") ||
            trimmed.startsWith("/ooc", ignoreCase = true)
        val cleanText = if (trimmed.startsWith("/ooc", ignoreCase = true)) {
            trimmed.removePrefix("/ooc").removePrefix("/OOC").trim()
        } else trimmed
        val kind = if (isOoc) MessageKind.OOC else MessageKind.ROLEPLAY
        val nextOrder = (currentMsgs.maxOfOrNull { it.orderIndex } ?: -1L) + 1L

        viewModelScope.launch {
            val addRes = repository.addMessage(
                userId = currentUserId,
                chatId = chat.id,
                role = "user",
                speakerName = chat.userPersonaName,
                content = cleanText.ifBlank { trimmed },
                messageKind = kind,
                modelUsed = "",
                orderIndex = nextOrder,
                newTotalCount = (currentMsgs.size + 1).toLong()
            )
            addRes.onFailure { err ->
                _generationState.value = _generationState.value.copy(
                    errorMessage = err.localizedMessage ?: "Не удалось отправить сообщение."
                )
                return@launch
            }
            if (requestAiReply) {
                val contentStr = cleanText.ifBlank { trimmed }
                val updatedHistory = currentMsgs + RpMessage(
                    id = addRes.getOrNull().orEmpty(),
                    userId = currentUserId,
                    chatId = chat.id,
                    role = "user",
                    speakerName = chat.userPersonaName,
                    content = contentStr,
                    variants = listOf(contentStr),
                    messageKind = kind.name,
                    orderIndex = nextOrder
                )
                launchAiGeneration(chat, updatedHistory, overrideInstruction = null)
            }
        }
    }

    fun rollDice(sides: Int, requestAiReaction: Boolean = true) {
        val chat = activeChat.value ?: return
        if (_generationState.value.isGenerating) return
        val roll = Random.nextInt(1, sides + 1)
        val critLabel = when {
            sides == 20 && roll == 20 -> " — Критический успех!"
            sides == 20 && roll == 1 -> " — Критический провал!"
            else -> ""
        }
        val text = "🎲 Бросок d$sides: выпадает $roll$critLabel"
        _hapticEvents.tryEmit(
            when {
                sides == 20 && roll == 20 -> HapticEvent.CRIT_SUCCESS
                sides == 20 && roll == 1 -> HapticEvent.DAMAGE_OR_WARN
                else -> HapticEvent.DICE_ROLL
            }
        )
        val currentMsgs = (activeMessagesState.value as? UiState.Success)?.data.orEmpty()
        val nextOrder = (currentMsgs.maxOfOrNull { it.orderIndex } ?: -1L) + 1L

        viewModelScope.launch {
            val res = repository.addMessage(
                userId = currentUserId,
                chatId = chat.id,
                role = "user",
                speakerName = chat.userPersonaName,
                content = text,
                messageKind = MessageKind.DICE,
                modelUsed = "",
                orderIndex = nextOrder,
                newTotalCount = (currentMsgs.size + 1).toLong()
            )
            if (res.isSuccess && requestAiReaction) {
                val updatedHistory = currentMsgs + RpMessage(
                    id = res.getOrNull().orEmpty(),
                    userId = currentUserId,
                    chatId = chat.id,
                    role = "user",
                    speakerName = chat.userPersonaName,
                    content = text,
                    variants = listOf(text),
                    messageKind = MessageKind.DICE.name,
                    orderIndex = nextOrder
                )
                launchAiGeneration(
                    chat,
                    updatedHistory,
                    overrideInstruction = "Игрок совершил бросок кубика ($text). Опиши исход и последствия в рамках сцены."
                )
            }
        }
    }

    fun continueScene() {
        val chat = activeChat.value ?: return
        if (_generationState.value.isGenerating) return
        val currentMsgs = (activeMessagesState.value as? UiState.Success)?.data.orEmpty()
        launchAiGeneration(
            chat = chat,
            messages = currentMsgs,
            overrideInstruction = "Продолжи сцену и действие дальше естественно, без повторения предыдущих фраз."
        )
    }

    fun regenerateLastResponse() {
        val chat = activeChat.value ?: return
        if (_generationState.value.isGenerating) return
        val currentMsgs = (activeMessagesState.value as? UiState.Success)?.data.orEmpty()
        val last = currentMsgs.lastOrNull()
        if (last != null && last.role == "assistant") {
            generateSwipeVariant(last)
        } else {
            launchAiGeneration(chat, currentMsgs, overrideInstruction = null)
        }
    }

    fun selectSwipeVariant(message: RpMessage, targetIndex: Int) {
        val chat = activeChat.value ?: return
        if (_generationState.value.isGenerating) return
        val currentMsgs = (activeMessagesState.value as? UiState.Success)?.data.orEmpty()
        val isLast = currentMsgs.lastOrNull()?.id == message.id
        viewModelScope.launch {
            repository.selectMessageVariant(currentUserId, chat.id, message, targetIndex, isLast)
                .onFailure { _toastEvents.tryEmit(it.localizedMessage ?: "Не удалось переключить вариант") }
        }
    }

    fun generateSwipeVariant(targetMessage: RpMessage) {
        val chat = activeChat.value ?: return
        if (_generationState.value.isGenerating) return
        if (targetMessage.effectiveVariants().size >= 12) {
            _toastEvents.tryEmit("Достигнут лимит 12 вариантов на одном сообщении")
            return
        }
        val currentMsgs = (activeMessagesState.value as? UiState.Success)?.data.orEmpty()
        val idx = currentMsgs.indexOfFirst { it.id == targetMessage.id }
        if (idx < 0) return
        val contextHistory = currentMsgs.take(idx)
        val isLast = idx == currentMsgs.lastIndex

        activeGenerationJob?.cancel()
        activeGenerationJob = viewModelScope.launch {
            _generationState.value = GenerationUiState(
                isGenerating = true,
                streamingText = "",
                swipingMessageId = targetMessage.id,
                stageLabel = "Подбор нового варианта..."
            )
            val settings = localSettings.value
            val override = if (contextHistory.isEmpty()) {
                "Сгенерируй альтернативную открывающую сцену и приветствие для персонажа ${chat.characterName}, сохранив сеттинг и стиль."
            } else {
                null
            }
            try {
                var finalText = ""
                pipelineEngine.executeTurn(
                    chat = chat,
                    messages = contextHistory,
                    settings = settings,
                    overrideInstruction = override,
                    deviceContext = appContext
                ).collect { event ->
                    when (event) {
                        is AgentPipelineEvent.StageChanged -> {
                            _generationState.value = _generationState.value.copy(
                                stageLabel = event.stageLabel,
                                activeAgents = event.activeAgentNames
                            )
                        }
                        is AgentPipelineEvent.TextChunk -> {
                            _generationState.value = _generationState.value.copy(
                                streamingText = event.accumulatedText
                            )
                        }
                        is AgentPipelineEvent.Completed -> {
                            finalText = event.result.finalRawText.trim()
                        }
                    }
                }

                if (finalText.isNotEmpty()) {
                    val modelLabel = "${chat.providerEnum().displayName}: ${chat.modelId}"
                    repository.addMessageVariant(
                        userId = currentUserId,
                        chatId = chat.id,
                        message = targetMessage,
                        newVariantText = finalText,
                        modelUsed = modelLabel,
                        isLastMessage = isLast
                    ).onFailure { err ->
                        _generationState.value = GenerationUiState(
                            isGenerating = false,
                            errorMessage = err.localizedMessage ?: "Не удалось сохранить новый свайп-вариант"
                        )
                        return@launch
                    }
                }
                _generationState.value = GenerationUiState(isGenerating = false)
                refreshRamSnapshot()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                AppLogBuffer.e(TAG, "Swipe generation failed", e)
                _generationState.value = GenerationUiState(
                    isGenerating = false,
                    errorMessage = e.localizedMessage ?: "Ошибка генерации варианта ответа."
                )
            }
        }
    }

    private fun launchAiGeneration(
        chat: RpChat,
        messages: List<RpMessage>,
        overrideInstruction: String?,
        allowAutoFailover: Boolean = true
    ) {
        activeGenerationJob?.cancel()
        _suggestedReplies.value = emptyList()
        activeGenerationJob = viewModelScope.launch {
            val settings = localSettings.value
            var workingInputChat = chat
            val activeSetup = workingInputChat.agentSetup()

            // Pre-flight cooldown bypass when Auto-Failover is enabled
            if (allowAutoFailover && settings.autoFailoverEnabled) {
                val activeProv = workingInputChat.providerEnum()
                val remSec = llmRouter.remainingCooldownSeconds(activeProv)
                if (remSec > 0) {
                    val alt = selectBestFailoverCandidate(
                        currentProvider = activeProv,
                        currentModel = workingInputChat.modelId,
                        settings = settings,
                        lastError = "429 cooldown"
                    )
                    if (alt != null) {
                        workingInputChat = switchChatProviderSilently(workingInputChat, alt)
                        _toastEvents.tryEmit("⏳ ${activeProv.displayName} на лимите (${remSec}с) — авто-переход на ${alt.labelRu}")
                    }
                }
            }

            _generationState.value = GenerationUiState(
                isGenerating = true,
                streamingText = "",
                stageLabel = "Подготовка команды агентов..."
            )
            try {
                var completedResult: com.example.data.ai.PipelineExecutionResult? = null
                pipelineEngine.executeTurn(
                    chat = workingInputChat,
                    messages = messages,
                    settings = settings,
                    overrideInstruction = overrideInstruction,
                    deviceContext = appContext
                ).collect { event ->
                    when (event) {
                        is AgentPipelineEvent.StageChanged -> {
                            _generationState.value = _generationState.value.copy(
                                stageLabel = event.stageLabel,
                                activeAgents = event.activeAgentNames
                            )
                        }
                        is AgentPipelineEvent.TextChunk -> {
                            _generationState.value = _generationState.value.copy(
                                streamingText = event.accumulatedText
                            )
                        }
                        is AgentPipelineEvent.Completed -> {
                            completedResult = event.result
                        }
                    }
                }

                val result = completedResult
                val finalText = result?.finalRawText?.trim().orEmpty()
                if (finalText.isNotEmpty()) {
                    val nextOrder = (messages.maxOfOrNull { it.orderIndex } ?: -1L) + 1L
                    val speaker = if (workingInputChat.rpModeEnum() == RpMode.GROUP_SCENE) {
                        "Сцена"
                    } else {
                        workingInputChat.characterName
                    }
                    val msgKind = if (workingInputChat.rpModeEnum() == RpMode.NARRATIVE) {
                        MessageKind.NARRATOR
                    } else {
                        MessageKind.ROLEPLAY
                    }
                    val addRes = repository.addMessage(
                        userId = currentUserId,
                        chatId = workingInputChat.id,
                        role = "assistant",
                        speakerName = speaker,
                        content = finalText,
                        messageKind = msgKind,
                        modelUsed = "${workingInputChat.providerEnum().displayName}: ${workingInputChat.modelId}",
                        orderIndex = nextOrder,
                        newTotalCount = (messages.size + 1).toLong(),
                        agentTrace = result?.agentTrace.orEmpty()
                    )
                    addRes.onFailure { err ->
                        _generationState.value = GenerationUiState(
                            isGenerating = false,
                            errorMessage = err.localizedMessage ?: "Ошибка сохранения ответа."
                        )
                        return@launch
                    }

                    val newMsgId = addRes.getOrNull().orEmpty()
                    var workingChat = workingInputChat
                    val delta = result?.postRpgDelta
                    val hasMetersOrRpg = workingInputChat.rpgState.enabled ||
                        activeSetup.hasRpgPipeline() ||
                        activeSetup.simulation.customMeters.isNotEmpty() ||
                        (delta != null && (delta.meterDeltas.isNotEmpty() || delta.meterSetValues.isNotEmpty()))
                    if (delta != null && hasMetersOrRpg) {
                        val updatedRpg = delta.applyTo(
                            state = workingInputChat.rpgState,
                            currentTurn = nextOrder,
                            customMeters = activeSetup.simulation.customMeters
                        )
                        repository.updateChatRpgState(currentUserId, workingInputChat.id, updatedRpg)
                        workingChat = workingInputChat.copy(rpgState = updatedRpg)
                        if (newMsgId.isNotBlank()) {
                            _autoAppliedDeltaMessageIds.value = _autoAppliedDeltaMessageIds.value + newMsgId
                        }
                        val hasNegativeDrop = delta.hpDelta < 0L || delta.meterDeltas.values.any { it < 0L }
                        val hasPositiveBoost = delta.hpDelta > 0L ||
                            delta.levelDelta > 0L ||
                            delta.goldDelta > 0L ||
                            delta.meterDeltas.values.any { it > 0L } ||
                            delta.addItems.isNotEmpty()
                        if (hasNegativeDrop) {
                            _hapticEvents.tryEmit(HapticEvent.DAMAGE_OR_WARN)
                        } else if (hasPositiveBoost) {
                            _hapticEvents.tryEmit(HapticEvent.METER_CHANGE)
                        }
                    }
                    result?.auditNotes?.firstOrNull()?.let { note ->
                        _toastEvents.tryEmit(note)
                    }

                    val updatedHistory = messages + RpMessage(
                        id = newMsgId,
                        userId = currentUserId,
                        chatId = workingInputChat.id,
                        role = "assistant",
                        speakerName = speaker,
                        content = finalText,
                        variants = listOf(finalText),
                        messageKind = msgKind.name,
                        agentTrace = result?.agentTrace.orEmpty(),
                        orderIndex = nextOrder
                    )

                    // Periodic Auto-Chronicle & Memory compression every N assistant turns
                    val summarizeStep = settings.autoSummarizeEveryN
                    val assistantTurns = updatedHistory.count { it.role == "assistant" }
                    if (summarizeStep > 0 && assistantTurns > 0 && assistantTurns % summarizeStep == 0) {
                        autoCompressMemoryAndChronicle(workingChat, updatedHistory)
                    }

                    // Auto action suggestions (3 chips) when enabled & safe for device RAM
                    val isLocalLowMem = workingChat.providerEnum() == LlmProvider.LOCAL_LLM &&
                        LocalRamWatchdog.inspectRam(appContext, settings.localModelProfile.ramPreset).isLowMemory
                    if (settings.autoSuggestActions && !isLocalLowMem) {
                        refreshActionSuggestions(workingChat, updatedHistory)
                    }
                }
                _generationState.value = GenerationUiState(isGenerating = false)
                refreshCooldownTicker()
                refreshRamSnapshot()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                AppLogBuffer.e(TAG, "AI generation failed", e)
                refreshCooldownTicker()
                if (allowAutoFailover && settings.autoFailoverEnabled && isFailoverEligibleError(e)) {
                    val alt = selectBestFailoverCandidate(
                        currentProvider = workingInputChat.providerEnum(),
                        currentModel = workingInputChat.modelId,
                        settings = settings,
                        lastError = e.localizedMessage
                    )
                    if (alt != null) {
                        val switchedChat = switchChatProviderSilently(workingInputChat, alt)
                        _toastEvents.tryEmit("Авто-фолбэк: переход на ${alt.labelRu}")
                        launchAiGeneration(
                            chat = switchedChat,
                            messages = messages,
                            overrideInstruction = overrideInstruction,
                            allowAutoFailover = false
                        )
                        return@launch
                    }
                }
                _generationState.value = GenerationUiState(
                    isGenerating = false,
                    errorMessage = e.localizedMessage ?: "Ошибка генерации ответа."
                )
            }
        }
    }

    private fun isFailoverEligibleError(e: Throwable): Boolean {
        if (e is com.example.data.ai.LlmQuotaOrRateLimitException) return true
        val msg = e.localizedMessage.orEmpty().lowercase()
        return msg.contains("429") ||
            msg.contains("402") ||
            msg.contains("403") ||
            msg.contains("лимит") ||
            msg.contains("app check") ||
            msg.contains("firebase не инициализирован") ||
            msg.contains("api-ключ") ||
            msg.contains("подключиться") ||
            msg.contains("недоступен")
    }

    private fun selectBestFailoverCandidate(
        currentProvider: LlmProvider,
        currentModel: String,
        settings: AppLocalSettings,
        lastError: String?
    ): com.example.data.model.FreeFallbackChoice? {
        val candidates = FreeAiPresets.fallbackChain.filter { choice ->
            val isSame = choice.provider == currentProvider && choice.modelId.equals(currentModel, ignoreCase = true)
            val keyOk = !choice.provider.requiresApiKey || settings.configFor(choice.provider).apiKey.isNotBlank()
            val notCooledDown = llmRouter.remainingCooldownSeconds(choice.provider) == 0
            val localAllowed = choice.provider != LlmProvider.LOCAL_LLM || settings.localLlmEnabled
            !isSame && keyOk && notCooledDown && localAllowed
        }
        return candidates.firstOrNull() ?: FreeAiPresets.nextSmartFallback(
            currentProvider = currentProvider,
            currentModel = currentModel,
            isKeyConfigured = { prov -> settings.configFor(prov).apiKey.isNotBlank() },
            lastError = lastError
        ).takeIf { it.provider != currentProvider || !it.modelId.equals(currentModel, ignoreCase = true) }
    }

    private suspend fun switchChatProviderSilently(
        chat: RpChat,
        choice: com.example.data.model.FreeFallbackChoice
    ): RpChat {
        val resolvedModel = if (choice.provider == LlmProvider.LOCAL_LLM) {
            localSettings.value.configFor(LlmProvider.LOCAL_LLM).customModelId.ifBlank { choice.modelId }
        } else {
            choice.modelId
        }
        val updated = chat.copy(provider = choice.provider.name, modelId = resolvedModel)
        repository.updateChatConfig(
            userId = currentUserId,
            chatId = chat.id,
            title = chat.title,
            rpMode = chat.rpModeEnum(),
            systemPrompt = chat.systemPrompt,
            characterName = chat.characterName,
            userPersonaName = chat.userPersonaName,
            userPersonaDesc = chat.userPersonaDesc,
            authorsNote = chat.authorsNote,
            memorySummary = chat.memorySummary,
            loreNotes = chat.loreNotes,
            loreEntries = chat.loreEntries,
            participants = chat.participants,
            provider = choice.provider,
            modelId = resolvedModel,
            params = chat.generationParams(),
            pinned = chat.pinned,
            portraitUri = chat.portraitUri,
            backdropUri = chat.backdropUri
        )
        return updated
    }

    fun stopGeneration(savePartial: Boolean = true) {
        val stateSnapshot = _generationState.value
        val partial = stateSnapshot.streamingText.trim()
        val swipingTargetId = stateSnapshot.swipingMessageId
        activeGenerationJob?.cancel()
        activeGenerationJob = null
        _generationState.value = GenerationUiState(isGenerating = false)
        if (savePartial && partial.isNotEmpty()) {
            val chat = activeChat.value ?: return
            val currentMsgs = (activeMessagesState.value as? UiState.Success)?.data.orEmpty()
            val modelLabel = "${chat.providerEnum().displayName}: ${chat.modelId}"
            viewModelScope.launch {
                if (swipingTargetId != null) {
                    val target = currentMsgs.firstOrNull { it.id == swipingTargetId }
                    if (target != null) {
                        val isLast = currentMsgs.lastOrNull()?.id == target.id
                        repository.addMessageVariant(currentUserId, chat.id, target, partial, modelLabel, isLast)
                    }
                } else {
                    val nextOrder = (currentMsgs.maxOfOrNull { it.orderIndex } ?: -1L) + 1L
                    repository.addMessage(
                        userId = currentUserId,
                        chatId = chat.id,
                        role = "assistant",
                        speakerName = chat.characterName,
                        content = partial,
                        messageKind = MessageKind.ROLEPLAY,
                        modelUsed = modelLabel,
                        orderIndex = nextOrder,
                        newTotalCount = (currentMsgs.size + 1).toLong()
                    )
                }
            }
        }
    }

    fun editMessage(message: RpMessage, newContent: String) {
        val chat = activeChat.value ?: return
        val currentMsgs = (activeMessagesState.value as? UiState.Success)?.data.orEmpty()
        val isLast = currentMsgs.lastOrNull()?.id == message.id
        viewModelScope.launch {
            repository.updateMessageContent(currentUserId, chat.id, message, newContent, isLast)
                .onSuccess { _toastEvents.tryEmit("Сообщение обновлено") }
                .onFailure { _toastEvents.tryEmit(it.localizedMessage ?: "Ошибка редактирования") }
        }
    }

    fun rewindToMessage(targetMessage: RpMessage, inclusiveDelete: Boolean) {
        val chat = activeChat.value ?: return
        val currentMsgs = (activeMessagesState.value as? UiState.Success)?.data.orEmpty()
        val idx = currentMsgs.indexOfFirst { it.id == targetMessage.id }
        if (idx < 0) return
        val cutIndex = if (inclusiveDelete) idx else idx + 1
        if (cutIndex >= currentMsgs.size) return
        val toDelete = currentMsgs.drop(cutIndex).map { it.id }
        val remaining = currentMsgs.take(cutIndex)
        viewModelScope.launch {
            repository.deleteMessagesFrom(
                userId = currentUserId,
                chatId = chat.id,
                messageIdsToDelete = toDelete,
                remainingCount = remaining.size.toLong(),
                remainingLastPreview = remaining.lastOrNull()?.activeContent().orEmpty()
            ).onSuccess {
                _toastEvents.tryEmit("Удалено сообщений: ${toDelete.size}")
            }
        }
    }

    fun branchFromMessage(
        targetMessage: RpMessage,
        customBranchLabel: String = "",
        onBranched: (String) -> Unit
    ) {
        val chat = activeChat.value ?: return
        val currentMsgs = (activeMessagesState.value as? UiState.Success)?.data.orEmpty()
        val idx = currentMsgs.indexOfFirst { it.id == targetMessage.id }
        if (idx < 0) return
        val slice = currentMsgs.take(idx + 1)
        viewModelScope.launch {
            repository.branchChatFromMessage(currentUserId, chat, slice, customBranchLabel)
                .onSuccess { newChatId ->
                    _toastEvents.tryEmit("Создана новая ветка сюжета")
                    selectChat(newChatId)
                    onBranched(newChatId)
                }
                .onFailure { err ->
                    _toastEvents.tryEmit(err.localizedMessage ?: "Не удалось создать ветку")
                }
        }
    }

    fun compressSceneMemory() {
        val chat = activeChat.value ?: return
        val currentMsgs = (activeMessagesState.value as? UiState.Success)?.data.orEmpty()
        if (currentMsgs.isEmpty()) {
            _toastEvents.tryEmit("Недостаточно сообщений для сжатия памяти")
            return
        }
        viewModelScope.launch {
            _generationState.value = _generationState.value.copy(isCompressingMemory = true)
            llmRouter.generateChronicleAndMemorySummary(chat, currentMsgs, localSettings.value, appContext)
                .onSuccess { (summary, chronicleEntry) ->
                    repository.updateChatConfig(
                        userId = currentUserId,
                        chatId = chat.id,
                        title = chat.title,
                        rpMode = chat.rpModeEnum(),
                        systemPrompt = chat.systemPrompt,
                        characterName = chat.characterName,
                        userPersonaName = chat.userPersonaName,
                        userPersonaDesc = chat.userPersonaDesc,
                        authorsNote = chat.authorsNote,
                        memorySummary = summary,
                        loreNotes = chat.loreNotes,
                        loreEntries = chat.loreEntries,
                        participants = chat.participants,
                        provider = chat.providerEnum(),
                        modelId = chat.modelId,
                        params = chat.generationParams(),
                        pinned = chat.pinned,
                        portraitUri = chat.portraitUri,
                        backdropUri = chat.backdropUri
                    )
                    if (chronicleEntry != null) {
                        val updatedRpg = chat.rpgState.copy(
                            chronicle = (chat.rpgState.chronicle + chronicleEntry).takeLast(40)
                        )
                        repository.updateChatRpgState(currentUserId, chat.id, updatedRpg)
                    }
                    _toastEvents.tryEmit("Память сцены и Хроника обновлены")
                }
                .onFailure { err ->
                    _toastEvents.tryEmit(err.localizedMessage ?: "Не удалось сжать память")
                }
            _generationState.value = _generationState.value.copy(isCompressingMemory = false)
        }
    }

    private fun autoCompressMemoryAndChronicle(chat: RpChat, msgs: List<RpMessage>) {
        viewModelScope.launch {
            llmRouter.generateChronicleAndMemorySummary(chat, msgs, localSettings.value, appContext)
                .onSuccess { (summary, chronicleEntry) ->
                    repository.updateChatConfig(
                        userId = currentUserId,
                        chatId = chat.id,
                        title = chat.title,
                        rpMode = chat.rpModeEnum(),
                        systemPrompt = chat.systemPrompt,
                        characterName = chat.characterName,
                        userPersonaName = chat.userPersonaName,
                        userPersonaDesc = chat.userPersonaDesc,
                        authorsNote = chat.authorsNote,
                        memorySummary = summary,
                        loreNotes = chat.loreNotes,
                        loreEntries = chat.loreEntries,
                        participants = chat.participants,
                        provider = chat.providerEnum(),
                        modelId = chat.modelId,
                        params = chat.generationParams(),
                        pinned = chat.pinned,
                        portraitUri = chat.portraitUri,
                        backdropUri = chat.backdropUri
                    )
                    if (chronicleEntry != null) {
                        val latestChat = activeChat.value ?: chat
                        val updatedRpg = latestChat.rpgState.copy(
                            chronicle = (latestChat.rpgState.chronicle + chronicleEntry).takeLast(40)
                        )
                        repository.updateChatRpgState(currentUserId, chat.id, updatedRpg)
                    }
                    _toastEvents.tryEmit("Хроник автоматически зафиксировал веху (${msgs.size} ходов)")
                }
        }
    }

    fun refreshActionSuggestions(
        chatOverride: RpChat? = null,
        msgsOverride: List<RpMessage>? = null
    ) {
        val chat = chatOverride ?: activeChat.value ?: return
        val msgs = msgsOverride ?: (activeMessagesState.value as? UiState.Success)?.data.orEmpty()
        if (msgs.isEmpty() || _isGeneratingSuggestions.value) return
        viewModelScope.launch {
            _isGeneratingSuggestions.value = true
            llmRouter.generateActionSuggestions(chat, msgs, localSettings.value, count = 3, deviceContext = appContext)
                .onSuccess { list ->
                    _suggestedReplies.value = list
                }
                .onFailure {
                    if (chatOverride == null) {
                        _toastEvents.tryEmit("Не удалось подобрать варианты хода")
                    }
                }
            _isGeneratingSuggestions.value = false
        }
    }

    fun handleSemanticSpanClick(
        span: SemanticSegment.SemanticSpan,
        onOpenStudio: (tabIndex: Int, focusEntityId: String?) -> Unit
    ) {
        val chat = activeChat.value ?: return
        if (span.type == SemanticEntityType.CHECK) {
            if (_generationState.value.isGenerating) return
            val mod = chat.rpgState.statModifier(span.checkStat)
            val roll = MathEngine.rollCheck(
                statName = span.checkStat,
                modifier = mod,
                dc = span.checkDc,
                purpose = span.displayText
            )
            _hapticEvents.tryEmit(
                when {
                    roll.d20 == 20 -> HapticEvent.CRIT_SUCCESS
                    roll.d20 == 1 || !roll.success -> HapticEvent.DAMAGE_OR_WARN
                    else -> HapticEvent.DICE_ROLL
                }
            )
            val currentMsgs = (activeMessagesState.value as? UiState.Success)?.data.orEmpty()
            val nextOrder = (currentMsgs.maxOfOrNull { it.orderIndex } ?: -1L) + 1L
            viewModelScope.launch {
                val res = repository.addMessage(
                    userId = currentUserId,
                    chatId = chat.id,
                    role = "user",
                    speakerName = chat.userPersonaName,
                    content = roll.formattedSummary,
                    messageKind = MessageKind.DICE,
                    modelUsed = "",
                    orderIndex = nextOrder,
                    newTotalCount = (currentMsgs.size + 1).toLong()
                )
                if (res.isSuccess) {
                    val updatedHistory = currentMsgs + RpMessage(
                        id = res.getOrNull().orEmpty(),
                        userId = currentUserId,
                        chatId = chat.id,
                        role = "user",
                        speakerName = chat.userPersonaName,
                        content = roll.formattedSummary,
                        variants = listOf(roll.formattedSummary),
                        messageKind = MessageKind.DICE.name,
                        orderIndex = nextOrder
                    )
                    launchAiGeneration(
                        chat,
                        updatedHistory,
                        overrideInstruction = "Игрок прошёл проверку прямо из текста (${roll.formattedSummary}). Опиши исход и последствия в сцене."
                    )
                }
            }
            return
        }

        if (span.type == SemanticEntityType.THOUGHT) {
            _toastEvents.tryEmit("💭 ${span.displayText}")
            return
        }

        val (updatedState, resolvedId) = SemanticMarkupEngine.resolveOrRegisterEntity(
            state = chat.rpgState,
            type = span.type,
            entityId = span.entityId,
            displayText = span.displayText
        )
        if (updatedState != chat.rpgState) {
            viewModelScope.launch {
                repository.updateChatRpgState(currentUserId, chat.id, updatedState)
                _toastEvents.tryEmit("Открыта и сохранена новая карточка: ${span.displayText}")
            }
        }
        val tabIndex = when (span.type) {
            SemanticEntityType.ITEM -> 0
            SemanticEntityType.NPC -> 1
            SemanticEntityType.LOCATION -> 2
            SemanticEntityType.QUEST, SemanticEntityType.LORE -> 3
            else -> 0
        }
        onOpenStudio(tabIndex, resolvedId)
    }

    fun rollNpcPerception(npcId: String) {
        val chat = activeChat.value ?: return
        val npc = chat.rpgState.npcs.firstOrNull { it.id == npcId } ?: return
        val (updatedNpc, result) = MathEngine.resolveNpcPerceptionCheck(
            npc = npc,
            perceptionMod = chat.rpgState.perceptionModifier()
        )
        _hapticEvents.tryEmit(if (result.success) HapticEvent.DICE_ROLL else HapticEvent.DAMAGE_OR_WARN)
        val updatedNpcs = chat.rpgState.npcs.map { if (it.id == npcId) updatedNpc else it }
        val updatedRpg = chat.rpgState.copy(enabled = true, npcs = updatedNpcs)
        val currentMsgs = (activeMessagesState.value as? UiState.Success)?.data.orEmpty()
        val nextOrder = (currentMsgs.maxOfOrNull { it.orderIndex } ?: -1L) + 1L

        viewModelScope.launch {
            repository.updateChatRpgState(currentUserId, chat.id, updatedRpg)
            repository.addMessage(
                userId = currentUserId,
                chatId = chat.id,
                role = "user",
                speakerName = chat.userPersonaName,
                content = result.formattedSummary,
                messageKind = MessageKind.DICE,
                modelUsed = "",
                orderIndex = nextOrder,
                newTotalCount = (currentMsgs.size + 1).toLong()
            )
            _toastEvents.tryEmit(
                if (result.success) {
                    "Восприятие успешно (${result.total} vs DC ${result.dc}): досье ${npc.name} приоткрыто!"
                } else {
                    "Провал Восприятия (${result.total} vs DC ${result.dc}): ${npc.name} скрывает детали."
                }
            )
        }
    }

    fun buyItemFromNpc(npcId: String, itemName: String, priceGold: Long) {
        val chat = activeChat.value ?: return
        val rpg = chat.rpgState
        if (rpg.gold < priceGold) {
            _toastEvents.tryEmit("Недостаточно золота (нужно $priceGold g)")
            return
        }
        val updatedNpcs = rpg.npcs.map { npc ->
            if (npc.id == npcId) {
                val boosted = npc.copy(
                    disposition = (npc.disposition + 3L).coerceIn(-100L, 100L),
                    inventory = npc.inventory.filterNot { it.equals(itemName, ignoreCase = true) }
                )
                MathEngine.applyTrustAutoReveal(boosted)
            } else npc
        }
        val updatedRpg = rpg.copy(
            enabled = true,
            gold = (rpg.gold - priceGold).coerceAtLeast(0L),
            inventory = (rpg.inventory + itemName.take(120)).take(40),
            npcs = updatedNpcs
        )
        viewModelScope.launch {
            repository.updateChatRpgState(currentUserId, chat.id, updatedRpg)
                .onSuccess { _toastEvents.tryEmit("Куплено: $itemName (-${priceGold}g)") }
                .onFailure { _toastEvents.tryEmit(it.localizedMessage ?: "Ошибка сделки") }
        }
    }

    fun equipItemToSlot(slotName: String, itemName: String) {
        val chat = activeChat.value ?: return
        val slots = chat.rpgState.playerProfile.equipmentSlots.toMutableMap()
        slots[slotName] = itemName.trim().take(120)
        val updated = chat.rpgState.copy(
            enabled = true,
            playerProfile = chat.rpgState.playerProfile.copy(equipmentSlots = slots)
        )
        updateRpgState(updated)
    }

    fun deepenMessageWithDetailer(message: RpMessage) {
        val chat = activeChat.value ?: return
        if (_generationState.value.isGenerating || _deepeningMessageId.value != null) return
        val currentMsgs = (activeMessagesState.value as? UiState.Success)?.data.orEmpty()
        val isLast = currentMsgs.lastOrNull()?.id == message.id

        viewModelScope.launch {
            _deepeningMessageId.value = message.id
            pipelineEngine.runDetailerRewrite(
                chat = chat,
                targetText = message.activeContent(),
                settings = localSettings.value,
                deviceContext = appContext
            ).onSuccess { deepened ->
                repository.addMessageVariant(
                    userId = currentUserId,
                    chatId = chat.id,
                    message = message,
                    newVariantText = deepened,
                    modelUsed = "Агент детализации (${chat.modelId})",
                    isLastMessage = isLast
                )
                _toastEvents.tryEmit("Добавлен детализированный вариант сцены")
            }.onFailure { err ->
                _toastEvents.tryEmit(err.localizedMessage ?: "Не удалось детализировать сообщение")
            }
            _deepeningMessageId.value = null
        }
    }

    fun generateSceneArtForMessage(message: RpMessage) {
        val chat = activeChat.value ?: return
        val ctx = appContext
        if (ctx == null) {
            _toastEvents.tryEmit("Контекст устройства недоступен для сохранения арта")
            return
        }
        if (_generatingArtMessageId.value != null) return
        val locName = chat.rpgState.currentLocation()?.name.orEmpty()
        val prompt = llmRouter.buildVisualScenePrompt(
            messageText = message.activeContent(),
            locationName = locName,
            settingHint = chat.title
        )
        viewModelScope.launch {
            _generatingArtMessageId.value = message.id
            SceneArtManager.generateFromFreeOrCustomEndpoint(
                context = ctx,
                prompt = prompt,
                endpointTemplate = localSettings.value.imageEndpointTemplate,
                aspectHint = "backdrop"
            ).onSuccess { uri ->
                repository.updateMessageSceneArt(currentUserId, chat.id, message.id, uri)
                _toastEvents.tryEmit("Арт сцены прикреплён к сообщению")
            }.onFailure { err ->
                _toastEvents.tryEmit(err.localizedMessage ?: "Не удалось сгенерировать арт")
            }
            _generatingArtMessageId.value = null
        }
    }

    fun generateLocationBackdrop(locId: String) {
        val chat = activeChat.value ?: return
        val ctx = appContext ?: return
        val node = chat.rpgState.locations.firstOrNull { it.id == locId } ?: return
        val prompt = "${node.name}, ${node.description}, ${chat.title}, atmospheric RPG location backdrop art, no text"
        viewModelScope.launch {
            SceneArtManager.generateFromFreeOrCustomEndpoint(
                context = ctx,
                prompt = prompt,
                endpointTemplate = localSettings.value.imageEndpointTemplate,
                aspectHint = "backdrop"
            ).onSuccess { uri ->
                val updatedLocs = chat.rpgState.locations.map {
                    if (it.id == locId) it.copy(backdropUri = uri) else it
                }
                repository.updateChatRpgState(currentUserId, chat.id, chat.rpgState.copy(locations = updatedLocs))
                _toastEvents.tryEmit("Фон для «${node.name}» сгенерирован")
            }.onFailure { err ->
                _toastEvents.tryEmit(err.localizedMessage ?: "Ошибка генерации фона локации")
            }
        }
    }

    // --- JSON Export / Import & GitHub Gist ---

    fun buildActiveChatExport(): Pair<String, String>? {
        val chat = activeChat.value ?: return null
        val msgs = (activeMessagesState.value as? UiState.Success)?.data.orEmpty()
        val safeName = chat.title.replace(Regex("[^a-zA-Zа-яА-Я0-9_\\-]"), "_").take(30)
        val fileName = "lorecraft_chat_${safeName}.json"
        return fileName to JsonExportManager.chatToJsonString(chat, msgs)
    }

    fun exportFullWorkspaceJson(onReady: (fileName: String, json: String) -> Unit) {
        viewModelScope.launch {
            repository.fetchAllUserDataForExport(currentUserId)
                .onSuccess { bundle ->
                    val json = JsonExportManager.workspaceToJsonString(bundle)
                    onReady("lorecraft_backup.json", json)
                }
                .onFailure { err ->
                    _toastEvents.tryEmit(err.localizedMessage ?: "Ошибка экспорта данных")
                }
        }
    }

    fun importFromJson(jsonString: String, onDone: (Int) -> Unit = {}) {
        viewModelScope.launch {
            val chatParsed = JsonExportManager.parseImportedChats(jsonString)
            if (chatParsed.isSuccess) {
                val list = chatParsed.getOrNull().orEmpty()
                var importedChats = 0
                list.forEach { item ->
                    val res = repository.importChatWithMessages(currentUserId, item.chat, item.messages)
                    if (res.isSuccess) importedChats++
                }
                JsonExportManager.parseImportedScenarios(jsonString).onSuccess { scenarios ->
                    scenarios.forEach { sc ->
                        repository.saveScenario(currentUserId, sc.copy(id = ""))
                    }
                }
                _toastEvents.tryEmit("Импортировано чатов: $importedChats")
                onDone(importedChats)
                return@launch
            }

            val scenParsed = JsonExportManager.parseImportedScenarios(jsonString)
            if (scenParsed.isSuccess) {
                var count = 0
                scenParsed.getOrNull().orEmpty().forEach { sc ->
                    if (repository.saveScenario(currentUserId, sc.copy(id = "")).isSuccess) count++
                }
                _toastEvents.tryEmit("Импортировано сценариев: $count")
                onDone(count)
            } else {
                val err = chatParsed.exceptionOrNull() ?: scenParsed.exceptionOrNull()
                _toastEvents.tryEmit("Ошибка JSON: ${err?.localizedMessage ?: "неверный формат"}")
            }
        }
    }

    fun publishToGitHubGist(
        description: String,
        scenariosOnly: Boolean,
        isPublic: Boolean,
        onResult: (Result<PublishedGistResult>) -> Unit
    ) {
        val pat = localSettings.value.githubPat
        viewModelScope.launch {
            if (scenariosOnly) {
                val scenarios = (scenariosState.value as? UiState.Success)?.data.orEmpty()
                if (scenarios.isEmpty()) {
                    onResult(Result.failure(IllegalStateException("Нет сценариев для публикации.")))
                    return@launch
                }
                val json = JsonExportManager.scenariosToJsonString(scenarios)
                val res = GitHubGistService.publishGist(
                    token = pat,
                    description = description.ifBlank { "LoreCraft Scenarios Pack" },
                    fileName = "lorecraft_scenarios.json",
                    jsonContent = json,
                    isPublic = isPublic
                )
                onResult(res)
            } else {
                repository.fetchAllUserDataForExport(currentUserId)
                    .onSuccess { bundle ->
                        val json = JsonExportManager.workspaceToJsonString(bundle)
                        val res = GitHubGistService.publishGist(
                            token = pat,
                            description = description.ifBlank { "LoreCraft Full Backup" },
                            fileName = "lorecraft_backup.json",
                            jsonContent = json,
                            isPublic = isPublic
                        )
                        onResult(res)
                    }
                    .onFailure { err ->
                        onResult(Result.failure(err))
                    }
            }
        }
    }

    fun importCharacterCardFromUrl(
        url: String,
        onCardReady: (ParsedCharacterCard) -> Unit,
        onWorkspaceImported: (Int) -> Unit = {},
        onError: (String) -> Unit
    ) {
        val pat = localSettings.value.githubPat
        viewModelScope.launch {
            GitHubGistService.fetchTextFromGitHubOrUrl(url, pat)
                .onSuccess { text ->
                    val trimmed = text.trim()
                    val asWorkspace = try {
                        val obj = org.json.JSONObject(trimmed)
                        obj.has("chats") || obj.has("chat") || obj.has("scenarios")
                    } catch (_: Exception) {
                        false
                    }
                    if (asWorkspace) {
                        importFromJson(trimmed) { count -> onWorkspaceImported(count) }
                    } else {
                        SillyTavernCardParser.parseJsonString(trimmed, "GitHub / Gist")
                            .onSuccess(onCardReady)
                            .onFailure { onError(it.localizedMessage ?: "Не удалось распознать карточку") }
                    }
                }
                .onFailure { err ->
                    onError(err.localizedMessage ?: "Не удалось загрузить файл по ссылке")
                }
        }
    }

    fun notifyMessage(msg: String) {
        _toastEvents.tryEmit(msg)
    }
}
