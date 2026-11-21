package com.example.data.model

import androidx.annotation.Keep
import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.ServerTimestamp

enum class ThemeMode(val labelRu: String) {
    SYSTEM("Системная"),
    DARK("Тёмная"),
    LIGHT("Светлая");

    companion object {
        fun fromString(raw: String?): ThemeMode =
            entries.firstOrNull { it.name == raw } ?: SYSTEM
    }
}

enum class RpMode(val labelRu: String, val shortRu: String, val descriptionRu: String) {
    CHARACTER_1ON1(
        labelRu = "Персонаж 1-на-1",
        shortRu = "Персонаж",
        descriptionRu = "Классический ролевой диалог с живым характером, памятью и стилем речи"
    ),
    GAME_MASTER(
        labelRu = "Мастер RPG / Мир",
        shortRu = "Мастер RPG",
        descriptionRu = "Ведущий настольной или текстовой RPG с картой, боями d20, статами и миром"
    ),
    GROUP_SCENE(
        labelRu = "Групповая сцена",
        shortRu = "Группа",
        descriptionRu = "Несколько персонажей в одной сцене с указанием говорящего и динамикой группы"
    ),
    NARRATIVE(
        labelRu = "Совместная проза",
        shortRu = "Проза",
        descriptionRu = "Литературное соавторство от 1-го или 3-го лица с режиссёрскими заметками"
    );

    companion object {
        fun fromString(raw: String?): RpMode =
            entries.firstOrNull { it.name == raw } ?: CHARACTER_1ON1
    }
}

enum class LlmProvider(
    val displayName: String,
    val defaultBaseUrl: String,
    val requiresApiKey: Boolean,
    val freeFriendly: Boolean,
    val freeBadgeRu: String,
    val suggestedModels: List<String>
) {
    POLLINATIONS(
        displayName = "Pollinations (Без ключа)",
        defaultBaseUrl = "https://text.pollinations.ai/openai",
        requiresApiKey = false,
        freeFriendly = true,
        freeBadgeRu = "0 ₽ • Без ключа",
        suggestedModels = listOf(
            "openai",
            "openai-fast",
            "gpt-oss"
        )
    ),
    GEMINI_AI_LOGIC(
        displayName = "Gemini (Firebase)",
        defaultBaseUrl = "",
        requiresApiKey = false,
        freeFriendly = true,
        freeBadgeRu = "Бесплатно • Firebase",
        suggestedModels = listOf(
            "gemini-3.8-flash",
            "gemini-3.5-flash-lite",
            "gemini-4-argon"
        )
    ),
    GEMINI_DIRECT(
        displayName = "Gemini AI Studio (ключ)",
        defaultBaseUrl = "https://generativelanguage.googleapis.com/v1beta/openai",
        requiresApiKey = true,
        freeFriendly = true,
        freeBadgeRu = "Free Tier • AI Studio",
        suggestedModels = listOf(
            "gemini-2.5-flash",
            "gemini-2.5-flash-lite",
            "gemini-2.0-flash"
        )
    ),
    OPENROUTER(
        displayName = "OpenRouter (:free)",
        defaultBaseUrl = "https://openrouter.ai/api/v1",
        requiresApiKey = true,
        freeFriendly = true,
        freeBadgeRu = "Есть :free модели",
        suggestedModels = listOf(
            "openrouter/free",
            "google/gemma-4-31b-it:free",
            "google/gemma-4-26b-a4b-it:free",
            "nvidia/nemotron-3-ultra-550b-a55b:free",
            "deepseek/deepseek-chat-v3-0324",
            "anthropic/claude-sonnet-4"
        )
    ),
    GROQ(
        displayName = "Groq (Free Tier)",
        defaultBaseUrl = "https://api.groq.com/openai/v1",
        requiresApiKey = true,
        freeFriendly = true,
        freeBadgeRu = "Быстрый Free Tier",
        suggestedModels = listOf(
            "llama-3.3-70b-versatile",
            "llama-3.1-8b-instant",
            "meta-llama/llama-4-scout-17b-16e-instruct",
            "qwen/qwen3-32b"
        )
    ),
    LOCAL_LLM(
        displayName = "Локальная GGUF / LAN",
        defaultBaseUrl = "http://127.0.0.1:8080/v1",
        requiresApiKey = false,
        freeFriendly = true,
        freeBadgeRu = "100% Оффлайн • 0 ₽",
        suggestedModels = listOf(
            "local-gguf-q4",
            "qwen2.5-3b-instruct-q4",
            "llama-3.2-3b-instruct-q4",
            "gemma-3-4b-it-q4"
        )
    ),
    DEEPSEEK(
        displayName = "DeepSeek API",
        defaultBaseUrl = "https://api.deepseek.com/v1",
        requiresApiKey = true,
        freeFriendly = false,
        freeBadgeRu = "API-ключ",
        suggestedModels = listOf(
            "deepseek-chat",
            "deepseek-reasoner"
        )
    ),
    OPENAI(
        displayName = "OpenAI",
        defaultBaseUrl = "https://api.openai.com/v1",
        requiresApiKey = true,
        freeFriendly = false,
        freeBadgeRu = "API-ключ",
        suggestedModels = listOf(
            "gpt-4o-mini",
            "gpt-4o",
            "gpt-4.1",
            "o3-mini"
        )
    ),
    ANTHROPIC(
        displayName = "Anthropic Claude",
        defaultBaseUrl = "https://api.anthropic.com/v1",
        requiresApiKey = true,
        freeFriendly = false,
        freeBadgeRu = "API-ключ",
        suggestedModels = listOf(
            "claude-sonnet-4-20250514",
            "claude-3-7-sonnet-latest",
            "claude-3-5-haiku-latest"
        )
    ),
    CUSTOM(
        displayName = "Свой OpenAI-API",
        defaultBaseUrl = "http://127.0.0.1:11434/v1",
        requiresApiKey = false,
        freeFriendly = true,
        freeBadgeRu = "Свой / HuggingFace",
        suggestedModels = listOf(
            "local-model",
            "qwen2.5:3b",
            "llama-3.3-70b-versatile"
        )
    );

    companion object {
        fun fromString(raw: String?): LlmProvider =
            entries.firstOrNull { it.name == raw } ?: POLLINATIONS

        fun freeProviders(): List<LlmProvider> =
            entries.filter { it.freeFriendly }
    }
}

data class FreeFallbackChoice(
    val provider: LlmProvider,
    val modelId: String,
    val labelRu: String
)

object FreeAiPresets {
    val fallbackChain: List<FreeFallbackChoice> = listOf(
        FreeFallbackChoice(LlmProvider.POLLINATIONS, "openai", "Pollinations OpenAI (Без ключа)"),
        FreeFallbackChoice(LlmProvider.POLLINATIONS, "openai-fast", "Pollinations Fast (Без ключа)"),
        FreeFallbackChoice(LlmProvider.GEMINI_AI_LOGIC, "gemini-3.8-flash", "Gemini 3.8 Flash (Firebase)"),
        FreeFallbackChoice(LlmProvider.GEMINI_AI_LOGIC, "gemini-3.5-flash-lite", "Gemini 3.5 Flash Lite (Firebase)"),
        FreeFallbackChoice(LlmProvider.GEMINI_DIRECT, "gemini-2.5-flash", "Gemini AI Studio (Свой ключ)"),
        FreeFallbackChoice(LlmProvider.OPENROUTER, "openrouter/free", "OpenRouter Auto (:free)"),
        FreeFallbackChoice(LlmProvider.GROQ, "llama-3.3-70b-versatile", "Groq Llama 3.3 70B"),
        FreeFallbackChoice(LlmProvider.LOCAL_LLM, "local-gguf-q4", "Локальная модель (127.0.0.1)")
    )

    fun nextFallback(currentProvider: LlmProvider, currentModel: String): FreeFallbackChoice {
        val currentIdx = fallbackChain.indexOfFirst {
            it.provider == currentProvider && it.modelId.equals(currentModel, ignoreCase = true)
        }
        return if (currentIdx >= 0 && currentIdx + 1 < fallbackChain.size) {
            fallbackChain[currentIdx + 1]
        } else if (currentProvider == LlmProvider.POLLINATIONS && currentModel != "openai-fast") {
            fallbackChain[1]
        } else {
            fallbackChain.first()
        }
    }

    fun nextSmartFallback(
        currentProvider: LlmProvider,
        currentModel: String,
        isKeyConfigured: (LlmProvider) -> Boolean,
        lastError: String? = null
    ): FreeFallbackChoice {
        val errLower = lastError.orEmpty().lowercase()
        val firebaseBlocked = errLower.contains("403") ||
            errLower.contains("app check") ||
            errLower.contains("appcheck") ||
            errLower.contains("firebase не инициализирован")

        if (currentProvider == LlmProvider.GEMINI_AI_LOGIC && firebaseBlocked) {
            return fallbackChain.firstOrNull { !it.provider.requiresApiKey && it.provider == LlmProvider.POLLINATIONS }
                ?: fallbackChain.first()
        }

        val candidates = fallbackChain.filter { choice ->
            !choice.provider.requiresApiKey || isKeyConfigured(choice.provider)
        }.ifEmpty { fallbackChain }

        val currentIdx = candidates.indexOfFirst {
            it.provider == currentProvider && it.modelId.equals(currentModel, ignoreCase = true)
        }
        return if (currentIdx >= 0 && currentIdx + 1 < candidates.size) {
            candidates[currentIdx + 1]
        } else {
            candidates.firstOrNull { it.provider != currentProvider } ?: candidates.first()
        }
    }
}

enum class SafetyLevel(
    val labelRu: String,
    val subtitleRu: String
) {
    STANDARD(
        labelRu = "Стандарт",
        subtitleRu = "Базовая модерация провайдера по умолчанию"
    ),
    RELAXED(
        labelRu = "Свободный RP",
        subtitleRu = "Блокировать только крайние риски (BLOCK_ONLY_HIGH)"
    ),
    MINIMAL(
        labelRu = "Минимум цензуры",
        subtitleRu = "Отключает фильтры безопасности где возможно + Creative RP"
    );

    companion object {
        fun fromString(raw: String?): SafetyLevel =
            entries.firstOrNull { it.name == raw } ?: RELAXED
    }
}

enum class ModelTag(val labelRu: String) {
    FREE("0 ₽"),
    NO_KEY("Без ключа"),
    UNCENSORED_FRIENDLY("Меньше цензуры"),
    RP_OPTIMIZED("Для RP"),
    FAST("Быстрая"),
    LOCAL("Локальная GGUF")
}

enum class ModelProbeStatus(val labelRu: String) {
    UNKNOWN("Не проверена"),
    PROBING("Проверка..."),
    ONLINE("Онлайн"),
    RATE_LIMITED("Лимит 429/402"),
    NEEDS_KEY("Нужен ключ"),
    AUTH_ERROR("Ошибка App Check / 403"),
    OFFLINE("Недоступна")
}

data class ModelCatalogItem(
    val provider: LlmProvider,
    val modelId: String,
    val displayName: String,
    val descriptionRu: String,
    val tags: Set<ModelTag>,
    val status: ModelProbeStatus = ModelProbeStatus.UNKNOWN,
    val latencyMs: Long? = null,
    val statusNote: String = ""
) {
    val stableKey: String get() = "${provider.name}::$modelId"
}

object FreeModelCatalog {
    fun curated(): List<ModelCatalogItem> = listOf(
        ModelCatalogItem(
            provider = LlmProvider.POLLINATIONS,
            modelId = "openai",
            displayName = "Pollinations GPT-4o / OpenAI",
            descriptionRu = "Работает сразу без ключа и регистрации, стабильный русский RP.",
            tags = setOf(ModelTag.FREE, ModelTag.NO_KEY, ModelTag.RP_OPTIMIZED)
        ),
        ModelCatalogItem(
            provider = LlmProvider.POLLINATIONS,
            modelId = "openai-fast",
            displayName = "Pollinations OpenAI Fast",
            descriptionRu = "Молниеносный отклик без ключа для быстрых сцен и автозаполнения.",
            tags = setOf(ModelTag.FREE, ModelTag.NO_KEY, ModelTag.FAST)
        ),
        ModelCatalogItem(
            provider = LlmProvider.POLLINATIONS,
            modelId = "gpt-oss",
            displayName = "Pollinations GPT-OSS 20B",
            descriptionRu = "Без ключа, 20B-модель с рассуждениями, работает в анонимном тарифе.",
            tags = setOf(ModelTag.FREE, ModelTag.NO_KEY, ModelTag.RP_OPTIMIZED, ModelTag.FAST)
        ),
        ModelCatalogItem(
            provider = LlmProvider.OPENROUTER,
            modelId = "openrouter/free",
            displayName = "OpenRouter Auto (:free)",
            descriptionRu = "Автоматически выбирает живую бесплатную модель OpenRouter.",
            tags = setOf(ModelTag.FREE, ModelTag.RP_OPTIMIZED)
        ),
        ModelCatalogItem(
            provider = LlmProvider.OPENROUTER,
            modelId = "cognitivecomputations/dolphin-mistral-24b-venice-edition:free",
            displayName = "Venice Dolphin Mistral 24B (:free)",
            descriptionRu = "Ролевая модель без лишней цензуры (нужен бесплатный ключ OpenRouter).",
            tags = setOf(ModelTag.FREE, ModelTag.RP_OPTIMIZED, ModelTag.UNCENSORED_FRIENDLY)
        ),
        ModelCatalogItem(
            provider = LlmProvider.OPENROUTER,
            modelId = "mistralai/mistral-small-3.1-24b-instruct:free",
            displayName = "Mistral Small 3.1 24B (:free)",
            descriptionRu = "Свободный художественный RP на русском и английском.",
            tags = setOf(ModelTag.FREE, ModelTag.RP_OPTIMIZED, ModelTag.UNCENSORED_FRIENDLY)
        ),
        ModelCatalogItem(
            provider = LlmProvider.OPENROUTER,
            modelId = "google/gemma-4-31b-it:free",
            displayName = "Gemma 4 31B IT (:free)",
            descriptionRu = "Мощная бесплатная модель с отличным русским языком.",
            tags = setOf(ModelTag.FREE, ModelTag.RP_OPTIMIZED)
        ),
        ModelCatalogItem(
            provider = LlmProvider.OPENROUTER,
            modelId = "deepseek/deepseek-r1:free",
            displayName = "DeepSeek R1 (:free)",
            descriptionRu = "Думающая модель для сложных интриг и детективных сюжетов.",
            tags = setOf(ModelTag.FREE, ModelTag.RP_OPTIMIZED)
        ),
        ModelCatalogItem(
            provider = LlmProvider.GROQ,
            modelId = "llama-3.3-70b-versatile",
            displayName = "Groq Llama 3.3 70B",
            descriptionRu = "Сверхбыстрый Free Tier (до ~300 ток/сек), отличная память деталей.",
            tags = setOf(ModelTag.FREE, ModelTag.FAST, ModelTag.RP_OPTIMIZED)
        ),
        ModelCatalogItem(
            provider = LlmProvider.GROQ,
            modelId = "qwen/qwen3-32b",
            displayName = "Groq Qwen 3 32B",
            descriptionRu = "Быстрый Free Tier Groq с глубоким отыгрышем и рассуждением.",
            tags = setOf(ModelTag.FREE, ModelTag.FAST, ModelTag.UNCENSORED_FRIENDLY)
        ),
        ModelCatalogItem(
            provider = LlmProvider.GEMINI_DIRECT,
            modelId = "gemini-2.5-flash",
            displayName = "Gemini 2.5 Flash (AI Studio ключ)",
            descriptionRu = "Бесплатный Free Tier по личному ключу AI Studio — работает без App Check.",
            tags = setOf(ModelTag.FREE, ModelTag.FAST, ModelTag.RP_OPTIMIZED)
        ),
        ModelCatalogItem(
            provider = LlmProvider.GEMINI_AI_LOGIC,
            modelId = "gemini-3.8-flash",
            displayName = "Gemini 3.8 Flash (Firebase)",
            descriptionRu = "Встроенный Firebase AI Logic (требует валидный App Check токен).",
            tags = setOf(ModelTag.FREE, ModelTag.NO_KEY, ModelTag.FAST)
        ),
        ModelCatalogItem(
            provider = LlmProvider.GEMINI_AI_LOGIC,
            modelId = "gemini-3.5-flash-lite",
            displayName = "Gemini 3.5 Flash Lite (Firebase)",
            descriptionRu = "Облегчённая быстрая модель через Firebase AI Logic.",
            tags = setOf(ModelTag.FREE, ModelTag.NO_KEY, ModelTag.FAST)
        ),
        ModelCatalogItem(
            provider = LlmProvider.LOCAL_LLM,
            modelId = "local-gguf-q4",
            displayName = "Локальная GGUF-модель (llama.cpp)",
            descriptionRu = "100% оффлайн и полная свобода без какой-либо облачной цензуры.",
            tags = setOf(ModelTag.FREE, ModelTag.NO_KEY, ModelTag.LOCAL, ModelTag.UNCENSORED_FRIENDLY, ModelTag.RP_OPTIMIZED)
        )
    )

    fun inferTags(provider: LlmProvider, modelId: String): Set<ModelTag> {
        val lower = modelId.lowercase()
        val tags = mutableSetOf<ModelTag>()
        if (provider.freeFriendly || lower.endsWith(":free") || lower == "openrouter/free") {
            tags += ModelTag.FREE
        }
        if (!provider.requiresApiKey) {
            tags += ModelTag.NO_KEY
        }
        if (provider == LlmProvider.LOCAL_LLM) {
            tags += ModelTag.LOCAL
            tags += ModelTag.UNCENSORED_FRIENDLY
        }
        if (lower.contains("dolphin") || lower.contains("hermes") || lower.contains("mistral") ||
            lower.contains("venice") || lower.contains("mythomax") || lower.contains("uncensored") ||
            lower.contains("abliterated") || lower.contains("lumimaid") || lower.contains("rocinante")
        ) {
            tags += ModelTag.UNCENSORED_FRIENDLY
            tags += ModelTag.RP_OPTIMIZED
        }
        if (lower.contains("fast") || lower.contains("flash") || lower.contains("8b") || lower.contains("instant")) {
            tags += ModelTag.FAST
        }
        if (lower.contains("llama") || lower.contains("gemma") || lower.contains("deepseek") || lower.contains("qwen") || lower == "openai") {
            tags += ModelTag.RP_OPTIMIZED
        }
        return tags
    }
}

data class ExpandedScenarioDraft(
    val title: String,
    val subtitle: String = "",
    val rpMode: RpMode = RpMode.CHARACTER_1ON1,
    val characterName: String = "",
    val systemPrompt: String = "",
    val characterBio: String = "",
    val firstMessage: String = "",
    val exampleDialogue: String = "",
    val participants: List<String> = emptyList(),
    val tags: List<String> = emptyList(),
    val loreNotes: List<String> = emptyList(),
    val loreEntries: List<LoreEntry> = emptyList(),
    val rpgState: RpgState = RpgState()
)

enum class MessageKind(val labelRu: String) {
    ROLEPLAY("РП"),
    OOC("OOC"),
    DICE("Кубик"),
    NARRATOR("Рассказчик");

    companion object {
        fun fromString(raw: String?): MessageKind =
            entries.firstOrNull { it.name == raw } ?: ROLEPLAY
    }
}

data class GenerationParams(
    val temperature: Double = 0.85,
    val topP: Double = 0.95,
    val topK: Int = 40,
    val maxTokens: Int = 2048,
    val contextWindowMessages: Int = 24,
    val presencePenalty: Double = 0.0,
    val frequencyPenalty: Double = 0.0,
    val stopSequences: List<String> = emptyList()
) {
    fun clamped(): GenerationParams = copy(
        temperature = temperature.coerceIn(0.0, 2.0),
        topP = topP.coerceIn(0.05, 1.0),
        topK = topK.coerceIn(1, 200),
        maxTokens = maxTokens.coerceIn(64, 16384),
        contextWindowMessages = contextWindowMessages.coerceIn(2, 200),
        presencePenalty = presencePenalty.coerceIn(-2.0, 2.0),
        frequencyPenalty = frequencyPenalty.coerceIn(-2.0, 2.0),
        stopSequences = stopSequences.map { it.trim().take(60) }.filter { it.isNotEmpty() }.take(10)
    )
}

data class GenerationPreset(
    val id: String,
    val nameRu: String,
    val subtitleRu: String,
    val params: GenerationParams
)

object GenerationPresets {
    val all = listOf(
        GenerationPreset(
            id = "balanced_rp",
            nameRu = "Живой диалог",
            subtitleRu = "Баланс эмоций и логики сюжета",
            params = GenerationParams(
                temperature = 0.85,
                topP = 0.95,
                topK = 40,
                maxTokens = 2048,
                contextWindowMessages = 24,
                presencePenalty = 0.15,
                frequencyPenalty = 0.10
            )
        ),
        GenerationPreset(
            id = "strict_lore",
            nameRu = "Точный лор",
            subtitleRu = "Строгое следование правилам и фактам",
            params = GenerationParams(
                temperature = 0.35,
                topP = 0.85,
                topK = 25,
                maxTokens = 2048,
                contextWindowMessages = 32,
                presencePenalty = 0.0,
                frequencyPenalty = 0.05
            )
        ),
        GenerationPreset(
            id = "rpg_master",
            nameRu = "Мастер подземелий",
            subtitleRu = "Чёткая симуляция мира и боёвки",
            params = GenerationParams(
                temperature = 0.70,
                topP = 0.90,
                topK = 35,
                maxTokens = 2560,
                contextWindowMessages = 30,
                presencePenalty = 0.10,
                frequencyPenalty = 0.10
            )
        ),
        GenerationPreset(
            id = "creative_chaos",
            nameRu = "Творческий шторм",
            subtitleRu = "Неожиданные повороты и яркие метафоры",
            params = GenerationParams(
                temperature = 1.25,
                topP = 0.98,
                topK = 80,
                maxTokens = 3072,
                contextWindowMessages = 20,
                presencePenalty = 0.35,
                frequencyPenalty = 0.25
            )
        ),
        GenerationPreset(
            id = "creative_rp_unfiltered",
            nameRu = "Creative RP (свободный)",
            subtitleRu = "Минимум отказов, фокус на драму, нуар и глубокий отыгрыш",
            params = GenerationParams(
                temperature = 1.05,
                topP = 0.96,
                topK = 60,
                maxTokens = 3072,
                contextWindowMessages = 28,
                presencePenalty = 0.20,
                frequencyPenalty = 0.15
            )
        )
    )
}

data class ProviderEndpointConfig(
    val provider: LlmProvider,
    val baseUrl: String = provider.defaultBaseUrl,
    val apiKey: String = "",
    val customModelId: String = provider.suggestedModels.firstOrNull().orEmpty()
)

enum class LocalRamPreset(
    val labelRu: String,
    val recommendedQuant: String,
    val contextSize: Int,
    val threads: Int
) {
    POCO_8GB(
        labelRu = "Poco X6 Pro 8 ГБ (≤3B Q4)",
        recommendedQuant = "Q4_K_M • до 3B параметров",
        contextSize = 4096,
        threads = 6
    ),
    POCO_12GB(
        labelRu = "Poco X6 Pro 12 ГБ (≤7B Q4)",
        recommendedQuant = "Q4_K_M • до 7B параметров",
        contextSize = 4096,
        threads = 6
    ),
    ECO_LITE(
        labelRu = "Экономичный (≤1.5B Q4)",
        recommendedQuant = "Q4_0 • минимальный расход ОЗУ",
        contextSize = 2048,
        threads = 4
    );

    companion object {
        fun fromString(raw: String?): LocalRamPreset =
            entries.firstOrNull { it.name == raw } ?: POCO_12GB
    }
}

data class LocalModelProfile(
    val serverUrl: String = "http://127.0.0.1:8080/v1",
    val ggufUri: String = "",
    val ggufDisplayName: String = "",
    val ramPreset: LocalRamPreset = LocalRamPreset.POCO_12GB,
    val offlineModePreferred: Boolean = false
)

@Keep
@IgnoreExtraProperties
data class LoreEntry(
    val id: String = "",
    val title: String = "",
    val keys: List<String> = emptyList(),
    val content: String = "",
    val alwaysActive: Boolean = false,
    val useRegex: Boolean = false,
    val priority: Long = 10L,
    val enabled: Boolean = true
)

@Keep
@IgnoreExtraProperties
data class WorldLocationNode(
    val id: String = "",
    val name: String = "",
    val description: String = "",
    val connections: List<String> = emptyList(),
    val visited: Boolean = false,
    val backdropUri: String = "",
    val posX: Double = -1.0,
    val posY: Double = -1.0,
    val dangerLevel: String = "",
    val shopItems: List<String> = emptyList()
)

@Keep
@IgnoreExtraProperties
data class Combatant(
    val id: String = "",
    val name: String = "",
    val hp: Long = 12L,
    val maxHp: Long = 12L,
    val initiative: Long = 10L,
    val enemy: Boolean = true,
    val notes: String = ""
)

@Keep
@IgnoreExtraProperties
data class CombatState(
    val active: Boolean = false,
    val round: Long = 1L,
    val turnIndex: Long = 0L,
    val combatants: List<Combatant> = emptyList()
)

@Keep
@IgnoreExtraProperties
data class RpgState(
    val enabled: Boolean = false,
    val level: Long = 1L,
    val hp: Long = 20L,
    val maxHp: Long = 20L,
    val mp: Long = 10L,
    val maxMp: Long = 10L,
    val gold: Long = 50L,
    val stats: Map<String, Long> = mapOf(
        "Сила" to 12L,
        "Ловкость" to 14L,
        "Интеллект" to 13L,
        "Восприятие" to 13L,
        "Харизма" to 12L
    ),
    val inventory: List<String> = emptyList(),
    val statuses: List<String> = emptyList(),
    val activeQuest: String = "",
    val currentLocationId: String = "",
    val locations: List<WorldLocationNode> = emptyList(),
    val combat: CombatState = CombatState(),
    val playerProfile: PlayerProfile = PlayerProfile(),
    val worldEnv: WorldEnvironment = WorldEnvironment(),
    val npcs: List<NpcDossier> = emptyList(),
    val quests: List<QuestItem> = emptyList(),
    val chronicle: List<ChronicleEntry> = emptyList()
) {
    fun currentLocation(): WorldLocationNode? =
        locations.firstOrNull { it.id == currentLocationId } ?: locations.firstOrNull()

    fun statModifier(statName: String): Long {
        val raw = stats[statName] ?: 10L
        return kotlin.math.floor((raw - 10.0) / 2.0).toLong()
    }

    fun perceptionModifier(): Long {
        val raw = stats["Восприятие"]
            ?: stats["Мудрость"]
            ?: stats["Интеллект"]
            ?: 10L
        return kotlin.math.floor((raw - 10.0) / 2.0).toLong()
    }
}

@Keep
@IgnoreExtraProperties
data class RpScenario(
    @DocumentId val id: String = "",
    val userId: String = "",
    val title: String = "",
    val subtitle: String = "",
    val rpMode: String = RpMode.CHARACTER_1ON1.name,
    val systemPrompt: String = "",
    val characterBio: String = "",
    val firstMessage: String = "",
    val exampleDialogue: String = "",
    val loreNotes: List<String> = emptyList(),
    val loreEntries: List<LoreEntry> = emptyList(),
    val defaultRpgState: RpgState = RpgState(),
    val defaultAgentSetupJson: String = "",
    val participants: List<String> = emptyList(),
    val tags: List<String> = emptyList(),
    val defaultProvider: String = LlmProvider.POLLINATIONS.name,
    val defaultModelId: String = "openai",
    val temperature: Double = 0.85,
    val topP: Double = 0.95,
    val topK: Long = 40L,
    val maxTokens: Long = 2048L,
    val contextWindowMessages: Long = 24L,
    val presencePenalty: Double = 0.0,
    val frequencyPenalty: Double = 0.0,
    val stopSequences: List<String> = emptyList(),
    val avatarIconKey: String = "chronicler",
    val accentColorHex: String = "#A78BFA",
    val portraitUri: String = "",
    val backdropUri: String = "",
    val pinned: Boolean = false,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    fun rpModeEnum(): RpMode = RpMode.fromString(rpMode)
    fun providerEnum(): LlmProvider = LlmProvider.fromString(defaultProvider)
    fun agentSetup(): AgentSetup = AgentSetupCodec.decode(defaultAgentSetupJson, rpModeEnum())
    fun generationParams(): GenerationParams = GenerationParams(
        temperature = temperature,
        topP = topP,
        topK = topK.toInt(),
        maxTokens = maxTokens.toInt(),
        contextWindowMessages = contextWindowMessages.toInt(),
        presencePenalty = presencePenalty,
        frequencyPenalty = frequencyPenalty,
        stopSequences = stopSequences
    ).clamped()
}

@Keep
@IgnoreExtraProperties
data class RpChat(
    @DocumentId val id: String = "",
    val userId: String = "",
    val scenarioId: String = "custom",
    val rootChatId: String = "",
    val parentChatId: String = "",
    val branchLabel: String = "",
    val branchPointOrder: Long = 0L,
    val title: String = "",
    val rpMode: String = RpMode.CHARACTER_1ON1.name,
    val systemPrompt: String = "",
    val characterName: String = "",
    val userPersonaName: String = "Странник",
    val userPersonaDesc: String = "",
    val authorsNote: String = "",
    val memorySummary: String = "",
    val loreNotes: List<String> = emptyList(),
    val loreEntries: List<LoreEntry> = emptyList(),
    val rpgState: RpgState = RpgState(),
    val agentSetupJson: String = "",
    val participants: List<String> = emptyList(),
    val provider: String = LlmProvider.POLLINATIONS.name,
    val modelId: String = "openai",
    val temperature: Double = 0.85,
    val topP: Double = 0.95,
    val topK: Long = 40L,
    val maxTokens: Long = 2048L,
    val contextWindowMessages: Long = 24L,
    val presencePenalty: Double = 0.0,
    val frequencyPenalty: Double = 0.0,
    val stopSequences: List<String> = emptyList(),
    val portraitUri: String = "",
    val backdropUri: String = "",
    val lastMessagePreview: String = "",
    val messageCount: Long = 0L,
    val pinned: Boolean = false,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    fun rpModeEnum(): RpMode = RpMode.fromString(rpMode)
    fun providerEnum(): LlmProvider = LlmProvider.fromString(provider)
    fun agentSetup(): AgentSetup = AgentSetupCodec.decode(agentSetupJson, rpModeEnum())
    fun generationParams(): GenerationParams = GenerationParams(
        temperature = temperature,
        topP = topP,
        topK = topK.toInt(),
        maxTokens = maxTokens.toInt(),
        contextWindowMessages = contextWindowMessages.toInt(),
        presencePenalty = presencePenalty,
        frequencyPenalty = frequencyPenalty,
        stopSequences = stopSequences
    ).clamped()
}

@Keep
@IgnoreExtraProperties
data class RpMessage(
    @DocumentId val id: String = "",
    val userId: String = "",
    val chatId: String = "",
    val role: String = "user",
    val speakerName: String = "",
    val content: String = "",
    val variants: List<String> = emptyList(),
    val variantModels: List<String> = emptyList(),
    val activeVariantIndex: Long = 0L,
    val messageKind: String = MessageKind.ROLEPLAY.name,
    val modelUsed: String = "",
    val sceneArtUri: String = "",
    val agentTrace: List<String> = emptyList(),
    val orderIndex: Long = 0L,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    fun kindEnum(): MessageKind = MessageKind.fromString(messageKind)

    fun effectiveVariants(): List<String> =
        if (variants.isNotEmpty()) variants else listOf(content)

    fun effectiveActiveIndex(): Int {
        val list = effectiveVariants()
        return activeVariantIndex.toInt().coerceIn(0, (list.size - 1).coerceAtLeast(0))
    }

    fun activeContent(): String =
        effectiveVariants().getOrElse(effectiveActiveIndex()) { content }

    fun activeModel(): String =
        variantModels.getOrElse(effectiveActiveIndex()) { modelUsed }
}

@Keep
@IgnoreExtraProperties
data class UserPersona(
    @DocumentId val id: String = "",
    val userId: String = "",
    val name: String = "",
    val description: String = "",
    val preferredStyle: String = "",
    val activeDefault: Boolean = false,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)
