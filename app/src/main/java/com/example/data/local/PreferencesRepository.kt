package com.example.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.data.model.GenerationParams
import com.example.data.model.LlmProvider
import com.example.data.model.LocalModelProfile
import com.example.data.model.LocalRamPreset
import com.example.data.model.ProviderEndpointConfig
import com.example.data.model.SafetyLevel
import com.example.data.model.ThemeMode
import com.example.device.AmbientScenePreset
import com.example.ui.theme.ReaderFontStyle
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.loreCraftDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "lorecraft_preferences"
)

data class AppLocalSettings(
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val streamingEnabled: Boolean = true,
    val showModelBadges: Boolean = true,
    val freeOnlyFilter: Boolean = true,
    val safetyLevel: SafetyLevel = SafetyLevel.RELAXED,
    val creativeRpFraming: Boolean = true,
    val autoFillNewChatWithAi: Boolean = true,
    val visualNovelMode: Boolean = false,
    val autoSuggestActions: Boolean = true,
    val autoSendSuggestedAction: Boolean = false,
    val autoSummarizeEveryN: Int = 16,
    val localLlmEnabled: Boolean = false,
    val customAgentsLibraryJson: String = "[]",
    val defaultAgentPresetId: String = "full_rpg_squad",
    val defaultProvider: LlmProvider = LlmProvider.POLLINATIONS,
    val defaultModelId: String = "openai",
    val defaultParams: GenerationParams = GenerationParams(),
    val providerConfigs: Map<LlmProvider, ProviderEndpointConfig> = emptyMap(),
    val githubPat: String = "",
    val localModelProfile: LocalModelProfile = LocalModelProfile(),
    val imageEndpointTemplate: String = "https://image.pollinations.ai/prompt/{prompt}?nologo=true",
    val readerBookMode: Boolean = false,
    val readerFontStyle: ReaderFontStyle = ReaderFontStyle.SANS_CLEAN,
    val readerFontSizeSp: Int = 16,
    val readerLineHeight: Float = 1.5f,
    val hapticsEnabled: Boolean = true,
    val ambientEnabled: Boolean = false,
    val ambientPreset: AmbientScenePreset = AmbientScenePreset.AUTO,
    val ambientVolume: Float = 0.35f,
    val autoFailoverEnabled: Boolean = true
) {
    fun configFor(provider: LlmProvider): ProviderEndpointConfig =
        providerConfigs[provider] ?: ProviderEndpointConfig(provider = provider)
}

class PreferencesRepository(private val context: Context) {

    private object Keys {
        val THEME_MODE = stringPreferencesKey("theme_mode")
        val STREAMING_ENABLED = booleanPreferencesKey("streaming_enabled")
        val SHOW_MODEL_BADGES = booleanPreferencesKey("show_model_badges")
        val FREE_ONLY_FILTER = booleanPreferencesKey("free_only_filter")
        val SAFETY_LEVEL = stringPreferencesKey("safety_level")
        val CREATIVE_RP_FRAMING = booleanPreferencesKey("creative_rp_framing")
        val AUTO_FILL_NEW_CHAT_AI = booleanPreferencesKey("auto_fill_new_chat_ai")
        val VISUAL_NOVEL_MODE = booleanPreferencesKey("visual_novel_mode")
        val AUTO_SUGGEST_ACTIONS = booleanPreferencesKey("auto_suggest_actions")
        val AUTO_SEND_SUGGESTED = booleanPreferencesKey("auto_send_suggested")
        val AUTO_SUMMARIZE_EVERY_N = intPreferencesKey("auto_summarize_every_n")
        val LOCAL_LLM_ENABLED = booleanPreferencesKey("local_llm_enabled")
        val CUSTOM_AGENTS_LIB_JSON = stringPreferencesKey("custom_agents_library_json")
        val DEFAULT_AGENT_PRESET = stringPreferencesKey("default_agent_preset_id")
        val DEFAULT_PROVIDER = stringPreferencesKey("default_provider")
        val DEFAULT_MODEL_ID = stringPreferencesKey("default_model_id")
        val GITHUB_PAT = stringPreferencesKey("github_pat_local_only")
        val IMAGE_ENDPOINT = stringPreferencesKey("image_endpoint_template")

        val LOCAL_SERVER_URL = stringPreferencesKey("local_model_server_url")
        val LOCAL_GGUF_URI = stringPreferencesKey("local_model_gguf_uri")
        val LOCAL_GGUF_NAME = stringPreferencesKey("local_model_gguf_name")
        val LOCAL_RAM_PRESET = stringPreferencesKey("local_model_ram_preset")
        val OFFLINE_MODE_PREFERRED = booleanPreferencesKey("offline_mode_preferred")

        val TEMP = doublePreferencesKey("default_temperature")
        val TOP_P = doublePreferencesKey("default_top_p")
        val TOP_K = intPreferencesKey("default_top_k")
        val MAX_TOKENS = intPreferencesKey("default_max_tokens")
        val CONTEXT_WINDOW = intPreferencesKey("default_context_window")
        val PRESENCE_PENALTY = doublePreferencesKey("default_presence_penalty")
        val FREQUENCY_PENALTY = doublePreferencesKey("default_frequency_penalty")
        val STOP_SEQUENCES = stringPreferencesKey("default_stop_sequences")

        val READER_BOOK_MODE = booleanPreferencesKey("reader_book_mode")
        val READER_FONT_STYLE = stringPreferencesKey("reader_font_style")
        val READER_FONT_SIZE_SP = intPreferencesKey("reader_font_size_sp")
        val READER_LINE_HEIGHT = doublePreferencesKey("reader_line_height")
        val HAPTICS_ENABLED = booleanPreferencesKey("haptics_enabled")
        val AMBIENT_ENABLED = booleanPreferencesKey("ambient_enabled")
        val AMBIENT_PRESET = stringPreferencesKey("ambient_preset")
        val AMBIENT_VOLUME = doublePreferencesKey("ambient_volume")
        val AUTO_FAILOVER_ENABLED = booleanPreferencesKey("auto_failover_enabled")

        fun baseUrlKey(p: LlmProvider) = stringPreferencesKey("provider_${p.name}_base_url")
        fun apiKeyKey(p: LlmProvider) = stringPreferencesKey("provider_${p.name}_api_key")
        fun modelKey(p: LlmProvider) = stringPreferencesKey("provider_${p.name}_model_id")
    }

    val settingsFlow: Flow<AppLocalSettings> = context.loreCraftDataStore.data.map { prefs ->
        val providerConfigs = LlmProvider.entries.associateWith { provider ->
            ProviderEndpointConfig(
                provider = provider,
                baseUrl = prefs[Keys.baseUrlKey(provider)]?.takeIf { it.isNotBlank() }
                    ?: provider.defaultBaseUrl,
                apiKey = prefs[Keys.apiKeyKey(provider)].orEmpty(),
                customModelId = prefs[Keys.modelKey(provider)]?.takeIf { it.isNotBlank() }
                    ?: provider.suggestedModels.firstOrNull().orEmpty()
            )
        }
        val stopsRaw = prefs[Keys.STOP_SEQUENCES].orEmpty()
        val stops = stopsRaw.split("\n").map { it.trim() }.filter { it.isNotEmpty() }.take(10)

        AppLocalSettings(
            themeMode = ThemeMode.fromString(prefs[Keys.THEME_MODE]),
            streamingEnabled = prefs[Keys.STREAMING_ENABLED] ?: true,
            showModelBadges = prefs[Keys.SHOW_MODEL_BADGES] ?: true,
            freeOnlyFilter = prefs[Keys.FREE_ONLY_FILTER] ?: true,
            safetyLevel = SafetyLevel.fromString(prefs[Keys.SAFETY_LEVEL]),
            creativeRpFraming = prefs[Keys.CREATIVE_RP_FRAMING] ?: true,
            autoFillNewChatWithAi = prefs[Keys.AUTO_FILL_NEW_CHAT_AI] ?: true,
            visualNovelMode = prefs[Keys.VISUAL_NOVEL_MODE] ?: false,
            autoSuggestActions = prefs[Keys.AUTO_SUGGEST_ACTIONS] ?: true,
            autoSendSuggestedAction = prefs[Keys.AUTO_SEND_SUGGESTED] ?: false,
            autoSummarizeEveryN = prefs[Keys.AUTO_SUMMARIZE_EVERY_N] ?: 16,
            localLlmEnabled = prefs[Keys.LOCAL_LLM_ENABLED] ?: false,
            customAgentsLibraryJson = prefs[Keys.CUSTOM_AGENTS_LIB_JSON]?.takeIf { it.isNotBlank() } ?: "[]",
            defaultAgentPresetId = prefs[Keys.DEFAULT_AGENT_PRESET]?.takeIf { it.isNotBlank() } ?: "full_rpg_squad",
            defaultProvider = LlmProvider.fromString(prefs[Keys.DEFAULT_PROVIDER]),
            defaultModelId = prefs[Keys.DEFAULT_MODEL_ID]?.takeIf { it.isNotBlank() }
                ?: "openai",
            defaultParams = GenerationParams(
                temperature = prefs[Keys.TEMP] ?: 0.85,
                topP = prefs[Keys.TOP_P] ?: 0.95,
                topK = prefs[Keys.TOP_K] ?: 40,
                maxTokens = prefs[Keys.MAX_TOKENS] ?: 2048,
                contextWindowMessages = prefs[Keys.CONTEXT_WINDOW] ?: 24,
                presencePenalty = prefs[Keys.PRESENCE_PENALTY] ?: 0.0,
                frequencyPenalty = prefs[Keys.FREQUENCY_PENALTY] ?: 0.0,
                stopSequences = stops
            ).clamped(),
            providerConfigs = providerConfigs,
            githubPat = prefs[Keys.GITHUB_PAT].orEmpty(),
            localModelProfile = LocalModelProfile(
                serverUrl = prefs[Keys.LOCAL_SERVER_URL]?.takeIf { it.isNotBlank() }
                    ?: "http://127.0.0.1:8080/v1",
                ggufUri = prefs[Keys.LOCAL_GGUF_URI].orEmpty(),
                ggufDisplayName = prefs[Keys.LOCAL_GGUF_NAME].orEmpty(),
                ramPreset = LocalRamPreset.fromString(prefs[Keys.LOCAL_RAM_PRESET]),
                offlineModePreferred = prefs[Keys.OFFLINE_MODE_PREFERRED] ?: false
            ),
            imageEndpointTemplate = prefs[Keys.IMAGE_ENDPOINT]?.takeIf { it.isNotBlank() }
                ?: "https://image.pollinations.ai/prompt/{prompt}?nologo=true",
            readerBookMode = prefs[Keys.READER_BOOK_MODE] ?: false,
            readerFontStyle = ReaderFontStyle.fromString(prefs[Keys.READER_FONT_STYLE]),
            readerFontSizeSp = (prefs[Keys.READER_FONT_SIZE_SP] ?: 16).coerceIn(13, 22),
            readerLineHeight = (prefs[Keys.READER_LINE_HEIGHT] ?: 1.5).toFloat().coerceIn(1.25f, 1.85f),
            hapticsEnabled = prefs[Keys.HAPTICS_ENABLED] ?: true,
            ambientEnabled = prefs[Keys.AMBIENT_ENABLED] ?: false,
            ambientPreset = AmbientScenePreset.fromString(prefs[Keys.AMBIENT_PRESET]),
            ambientVolume = (prefs[Keys.AMBIENT_VOLUME] ?: 0.35).toFloat().coerceIn(0.05f, 1.0f),
            autoFailoverEnabled = prefs[Keys.AUTO_FAILOVER_ENABLED] ?: true
        )
    }

    suspend fun setThemeMode(mode: ThemeMode) {
        context.loreCraftDataStore.edit { it[Keys.THEME_MODE] = mode.name }
    }

    suspend fun setStreamingEnabled(enabled: Boolean) {
        context.loreCraftDataStore.edit { it[Keys.STREAMING_ENABLED] = enabled }
    }

    suspend fun setShowModelBadges(show: Boolean) {
        context.loreCraftDataStore.edit { it[Keys.SHOW_MODEL_BADGES] = show }
    }

    suspend fun setFreeOnlyFilter(freeOnly: Boolean) {
        context.loreCraftDataStore.edit { it[Keys.FREE_ONLY_FILTER] = freeOnly }
    }

    suspend fun setSafetyLevel(level: SafetyLevel) {
        context.loreCraftDataStore.edit { it[Keys.SAFETY_LEVEL] = level.name }
    }

    suspend fun setCreativeRpFraming(enabled: Boolean) {
        context.loreCraftDataStore.edit { it[Keys.CREATIVE_RP_FRAMING] = enabled }
    }

    suspend fun setAutoFillNewChatWithAi(enabled: Boolean) {
        context.loreCraftDataStore.edit { it[Keys.AUTO_FILL_NEW_CHAT_AI] = enabled }
    }

    suspend fun setVisualNovelMode(enabled: Boolean) {
        context.loreCraftDataStore.edit { it[Keys.VISUAL_NOVEL_MODE] = enabled }
    }

    suspend fun setAutoSuggestActions(enabled: Boolean) {
        context.loreCraftDataStore.edit { it[Keys.AUTO_SUGGEST_ACTIONS] = enabled }
    }

    suspend fun setAutoSendSuggestedAction(enabled: Boolean) {
        context.loreCraftDataStore.edit { it[Keys.AUTO_SEND_SUGGESTED] = enabled }
    }

    suspend fun setAutoSummarizeEveryN(everyN: Int) {
        context.loreCraftDataStore.edit { it[Keys.AUTO_SUMMARIZE_EVERY_N] = everyN.coerceIn(0, 60) }
    }

    suspend fun setLocalLlmEnabled(enabled: Boolean) {
        context.loreCraftDataStore.edit { it[Keys.LOCAL_LLM_ENABLED] = enabled }
    }

    suspend fun saveCustomAgentsLibraryJson(json: String) {
        context.loreCraftDataStore.edit { it[Keys.CUSTOM_AGENTS_LIB_JSON] = json }
    }

    suspend fun setDefaultAgentPresetId(presetId: String) {
        context.loreCraftDataStore.edit { it[Keys.DEFAULT_AGENT_PRESET] = presetId }
    }

    suspend fun setOfflineModePreferred(offline: Boolean) {
        context.loreCraftDataStore.edit { it[Keys.OFFLINE_MODE_PREFERRED] = offline }
    }

    suspend fun saveLocalModelProfile(profile: LocalModelProfile) {
        context.loreCraftDataStore.edit { prefs ->
            prefs[Keys.LOCAL_SERVER_URL] = profile.serverUrl.trim().ifEmpty { "http://127.0.0.1:8080/v1" }
            prefs[Keys.LOCAL_GGUF_URI] = profile.ggufUri.trim()
            prefs[Keys.LOCAL_GGUF_NAME] = profile.ggufDisplayName.trim()
            prefs[Keys.LOCAL_RAM_PRESET] = profile.ramPreset.name
            prefs[Keys.OFFLINE_MODE_PREFERRED] = profile.offlineModePreferred
            prefs[Keys.baseUrlKey(LlmProvider.LOCAL_LLM)] = profile.serverUrl.trim().ifEmpty { "http://127.0.0.1:8080/v1" }
        }
    }

    suspend fun saveImageEndpointTemplate(template: String) {
        context.loreCraftDataStore.edit { prefs ->
            prefs[Keys.IMAGE_ENDPOINT] = template.trim()
        }
    }

    suspend fun setDefaultModel(provider: LlmProvider, modelId: String) {
        context.loreCraftDataStore.edit { prefs ->
            prefs[Keys.DEFAULT_PROVIDER] = provider.name
            prefs[Keys.DEFAULT_MODEL_ID] = modelId.trim().ifEmpty {
                provider.suggestedModels.firstOrNull() ?: "openai"
            }
        }
    }

    suspend fun saveDefaultParams(params: GenerationParams) {
        val clamped = params.clamped()
        context.loreCraftDataStore.edit { prefs ->
            prefs[Keys.TEMP] = clamped.temperature
            prefs[Keys.TOP_P] = clamped.topP
            prefs[Keys.TOP_K] = clamped.topK
            prefs[Keys.MAX_TOKENS] = clamped.maxTokens
            prefs[Keys.CONTEXT_WINDOW] = clamped.contextWindowMessages
            prefs[Keys.PRESENCE_PENALTY] = clamped.presencePenalty
            prefs[Keys.FREQUENCY_PENALTY] = clamped.frequencyPenalty
            prefs[Keys.STOP_SEQUENCES] = clamped.stopSequences.joinToString("\n")
        }
    }

    suspend fun saveProviderConfig(config: ProviderEndpointConfig) {
        val cleanKey = config.apiKey.trim().replace("\n", "").replace("\r", "")
        val cleanUrl = config.baseUrl.trim()
        context.loreCraftDataStore.edit { prefs ->
            prefs[Keys.baseUrlKey(config.provider)] = cleanUrl
            prefs[Keys.apiKeyKey(config.provider)] = cleanKey
            prefs[Keys.modelKey(config.provider)] = config.customModelId.trim()
            if (config.provider == LlmProvider.LOCAL_LLM) {
                prefs[Keys.LOCAL_SERVER_URL] = cleanUrl
            }
        }
    }

    suspend fun saveGithubPat(token: String) {
        context.loreCraftDataStore.edit { prefs ->
            prefs[Keys.GITHUB_PAT] = token.trim()
        }
    }

    suspend fun setReaderBookMode(enabled: Boolean) {
        context.loreCraftDataStore.edit { it[Keys.READER_BOOK_MODE] = enabled }
    }

    suspend fun setReaderFontStyle(style: ReaderFontStyle) {
        context.loreCraftDataStore.edit { it[Keys.READER_FONT_STYLE] = style.name }
    }

    suspend fun setReaderFontSizeSp(sizeSp: Int) {
        context.loreCraftDataStore.edit { it[Keys.READER_FONT_SIZE_SP] = sizeSp.coerceIn(13, 22) }
    }

    suspend fun setReaderLineHeight(multiplier: Float) {
        context.loreCraftDataStore.edit {
            it[Keys.READER_LINE_HEIGHT] = multiplier.coerceIn(1.25f, 1.85f).toDouble()
        }
    }

    suspend fun setHapticsEnabled(enabled: Boolean) {
        context.loreCraftDataStore.edit { it[Keys.HAPTICS_ENABLED] = enabled }
    }

    suspend fun setAmbientEnabled(enabled: Boolean) {
        context.loreCraftDataStore.edit { it[Keys.AMBIENT_ENABLED] = enabled }
    }

    suspend fun setAmbientPreset(preset: AmbientScenePreset) {
        context.loreCraftDataStore.edit { it[Keys.AMBIENT_PRESET] = preset.name }
    }

    suspend fun setAmbientVolume(volume: Float) {
        context.loreCraftDataStore.edit { it[Keys.AMBIENT_VOLUME] = volume.coerceIn(0.05f, 1.0f).toDouble() }
    }

    suspend fun setAutoFailoverEnabled(enabled: Boolean) {
        context.loreCraftDataStore.edit { it[Keys.AUTO_FAILOVER_ENABLED] = enabled }
    }
}
