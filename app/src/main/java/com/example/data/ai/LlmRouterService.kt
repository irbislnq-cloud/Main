package com.example.data.ai

import android.content.Context
import com.example.data.local.AppLocalSettings
import com.example.data.model.ChronicleEntry
import com.example.data.model.ExpandedScenarioDraft
import com.example.data.model.FreeModelCatalog
import com.example.data.model.GenerationParams
import com.example.data.model.LlmProvider
import com.example.data.model.LoreEntry
import com.example.data.model.ModelCatalogItem
import com.example.data.model.ModelProbeStatus
import com.example.data.model.ModelTag
import com.example.data.model.RpChat
import com.example.data.model.RpMessage
import com.example.data.model.RpMode
import com.example.data.model.RpgState
import com.example.data.model.SafetyLevel
import com.example.data.model.WorldLocationNode
import com.example.diagnostics.AppLogBuffer
import com.google.firebase.Firebase
import com.google.firebase.FirebaseApp
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.Content
import com.google.firebase.ai.type.HarmBlockThreshold
import com.google.firebase.ai.type.HarmCategory
import com.google.firebase.ai.type.SafetySetting
import com.google.firebase.ai.type.content
import com.google.firebase.ai.type.generationConfig
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.ConnectException
import java.net.UnknownHostException
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

private const val TAG = "LlmRouterService"

class LlmQuotaOrRateLimitException(
    message: String,
    val retryAfterSeconds: Int = 30
) : IllegalStateException(message)

data class ProviderTestResult(
    val replyPreview: String,
    val latencyMs: Long,
    val modelUsed: String
)

class LlmRouterService {

    private val cooldownUntilMs = ConcurrentHashMap<LlmProvider, Long>()

    fun recordCooldown(provider: LlmProvider, seconds: Int) {
        val bounded = seconds.coerceIn(3, 600)
        cooldownUntilMs[provider] = System.currentTimeMillis() + bounded * 1000L
    }

    fun clearCooldown(provider: LlmProvider) {
        cooldownUntilMs.remove(provider)
    }

    fun remainingCooldownSeconds(provider: LlmProvider): Int {
        val until = cooldownUntilMs[provider] ?: return 0
        val diffMs = until - System.currentTimeMillis()
        if (diffMs <= 0L) {
            cooldownUntilMs.remove(provider)
            return 0
        }
        return ((diffMs + 999L) / 1000L).toInt().coerceAtLeast(1)
    }

    fun activeCooldownSnapshot(): Map<LlmProvider, Int> {
        val now = System.currentTimeMillis()
        val out = LinkedHashMap<LlmProvider, Int>()
        cooldownUntilMs.entries.forEach { (prov, until) ->
            val rem = until - now
            if (rem <= 0L) {
                cooldownUntilMs.remove(prov)
            } else {
                out[prov] = ((rem + 999L) / 1000L).toInt().coerceAtLeast(1)
            }
        }
        return out
    }

    private val httpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(25, TimeUnit.SECONDS)
            .readTimeout(90, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .build()
    }

    fun streamReply(
        chat: RpChat,
        messages: List<RpMessage>,
        settings: AppLocalSettings,
        overrideInstruction: String? = null,
        deviceContext: Context? = null
    ): Flow<String> {
        val context = ContextPromptBuilder.build(chat, messages, overrideInstruction, settings)
        val provider = chat.providerEnum()
        val modelId = chat.modelId.ifBlank {
            provider.suggestedModels.firstOrNull() ?: "openai"
        }
        val params = chat.generationParams()
        AppLogBuffer.i(TAG, "Start reply: provider=${provider.name}, model=$modelId, msgs=${messages.size}, safety=${settings.safetyLevel.name}")
        return streamBuiltContext(
            provider = provider,
            modelId = modelId,
            context = context,
            params = params,
            settings = settings,
            deviceContext = deviceContext
        )
    }

    fun streamBuiltContext(
        provider: LlmProvider,
        modelId: String,
        context: BuiltRpContext,
        params: GenerationParams,
        settings: AppLocalSettings,
        deviceContext: Context? = null
    ): Flow<String> {
        val resolvedModel = modelId.ifBlank {
            provider.suggestedModels.firstOrNull() ?: "openai"
        }
        return when (provider) {
            LlmProvider.GEMINI_AI_LOGIC -> streamGeminiAiLogic(
                modelId = resolvedModel,
                context = context,
                params = params,
                streaming = settings.streamingEnabled,
                safetyLevel = settings.safetyLevel
            )
            LlmProvider.ANTHROPIC -> streamAnthropic(
                baseUrl = settings.configFor(provider).baseUrl.ifBlank { provider.defaultBaseUrl },
                apiKey = sanitizeKey(settings.configFor(provider).apiKey),
                modelId = resolvedModel,
                context = context,
                params = params,
                streaming = settings.streamingEnabled
            )
            LlmProvider.POLLINATIONS,
            LlmProvider.GEMINI_DIRECT,
            LlmProvider.OPENAI,
            LlmProvider.OPENROUTER,
            LlmProvider.GROQ,
            LlmProvider.LOCAL_LLM,
            LlmProvider.DEEPSEEK,
            LlmProvider.CUSTOM -> {
                val cfg = settings.configFor(provider)
                val resolvedBase = if (provider == LlmProvider.LOCAL_LLM) {
                    settings.localModelProfile.serverUrl.ifBlank {
                        cfg.baseUrl.ifBlank { provider.defaultBaseUrl }
                    }
                } else {
                    cfg.baseUrl.ifBlank { provider.defaultBaseUrl }
                }
                streamOpenAiCompatible(
                    provider = provider,
                    baseUrl = resolvedBase,
                    apiKey = sanitizeKey(cfg.apiKey),
                    modelId = resolvedModel,
                    context = context,
                    params = params,
                    streaming = settings.streamingEnabled,
                    settings = settings,
                    deviceContext = deviceContext
                )
            }
        }
    }

    suspend fun testOpenAiServerModels(
        provider: LlmProvider = LlmProvider.CUSTOM,
        baseUrl: String,
        apiKey: String = ""
    ): Result<List<String>> = withContext(Dispatchers.IO) {
        runCatching {
            val resolvedInputUrl = baseUrl.trim().ifBlank { provider.defaultBaseUrl }
            val modelsUrl = resolveModelsEndpoint(provider, resolvedInputUrl)
            require(isPermittedEndpointUrl(modelsUrl)) {
                "HTTP разрешён только для локальных/LAN серверов (127.0.0.1, localhost, 192.168.x.x); внешние API требуют HTTPS."
            }
            val cleanKey = sanitizeKey(apiKey)
            val builder = Request.Builder()
                .url(modelsUrl)
                .header("Accept", "application/json")
                .get()
            if (cleanKey.isNotBlank()) {
                builder.header("Authorization", "Bearer $cleanKey")
            }
            httpClient.newCall(builder.build()).execute().use { resp ->
                val bodyStr = resp.body?.string().orEmpty()
                if (!resp.isSuccessful) {
                    throw IllegalStateException("HTTP ${resp.code}: ${bodyStr.take(220)}")
                }
                val found = parseModelsResponse(bodyStr)
                AppLogBuffer.i(TAG, "/models ping OK ($modelsUrl): ${found.size} models")
                found
            }
        }.onFailure {
            AppLogBuffer.w(TAG, "Server /models check failed for $baseUrl", it)
        }
    }

    suspend fun testChatCompletion(
        provider: LlmProvider,
        baseUrl: String,
        apiKey: String,
        modelId: String,
        settings: AppLocalSettings
    ): Result<ProviderTestResult> = withContext(Dispatchers.IO) {
        val started = System.currentTimeMillis()
        val resolvedModel = modelId.trim().ifBlank {
            provider.suggestedModels.firstOrNull() ?: "openai"
        }
        val cleanKey = sanitizeKey(apiKey)
        val probePrompt = "Ответь одним коротким предложением по-русски: связь работает."

        try {
            when (provider) {
                LlmProvider.GEMINI_AI_LOGIC -> {
                    val firebaseReady = runCatching { FirebaseApp.getInstance() }.isSuccess
                    if (!firebaseReady) {
                        return@withContext Result.failure(
                            IllegalStateException("Firebase не инициализирован. Переключитесь на Pollinations (без ключа).")
                        )
                    }
                    val safeModel = if (resolvedModel.startsWith("gemini-")) resolvedModel else "gemini-3.8-flash"
                    val model = Firebase.ai.generativeModel(
                        modelName = safeModel,
                        generationConfig = generationConfig {
                            temperature = 0.2f
                            maxOutputTokens = 96
                        },
                        safetySettings = buildGeminiSafetySettings(settings.safetyLevel)
                    )
                    val response = withTimeout(25_000L) {
                        model.generateContent(probePrompt)
                    }
                    val text = response.text?.trim().orEmpty()
                    if (text.isBlank()) {
                        return@withContext Result.failure(
                            IllegalStateException("Модель $safeModel вернула пустой ответ.")
                        )
                    }
                    val elapsed = (System.currentTimeMillis() - started).coerceAtLeast(1L)
                    Result.success(ProviderTestResult(text.take(240), elapsed, safeModel))
                }

                LlmProvider.ANTHROPIC -> {
                    if (cleanKey.isBlank()) {
                        return@withContext Result.failure(
                            IllegalStateException(missingKeyMessage(provider))
                        )
                    }
                    val normalizedBase = baseUrl.trim().ifBlank { provider.defaultBaseUrl }.trimEnd('/')
                    val endpoint = if (normalizedBase.endsWith("/messages")) normalizedBase else "$normalizedBase/messages"
                    val payload = JSONObject().apply {
                        put("model", resolvedModel)
                        put("messages", JSONArray().put(JSONObject().apply {
                            put("role", "user")
                            put("content", probePrompt)
                        }))
                        put("max_tokens", 128)
                        put("temperature", 0.2)
                        put("stream", false)
                    }
                    val req = Request.Builder()
                        .url(endpoint)
                        .post(payload.toString().toRequestBody("application/json; charset=utf-8".toMediaType()))
                        .header("Content-Type", "application/json")
                        .header("x-api-key", cleanKey)
                        .header("anthropic-version", "2023-06-01")
                        .build()
                    val text = withTimeout(30_000L) {
                        httpClient.newCall(req).execute().use { resp ->
                            val bodyStr = resp.body?.string().orEmpty()
                            if (!resp.isSuccessful) {
                                throw mapHttpError(provider, resp.code, bodyStr, resp.header("retry-after"), resolvedModel)
                            }
                            val arr = JSONObject(bodyStr).optJSONArray("content")
                            buildString {
                                if (arr != null) {
                                    for (i in 0 until arr.length()) {
                                        append(arr.optJSONObject(i)?.optString("text").orEmpty())
                                    }
                                }
                            }.trim()
                        }
                    }
                    if (text.isBlank()) {
                        return@withContext Result.failure(IllegalStateException("Модель $resolvedModel вернула пустой ответ."))
                    }
                    val elapsed = (System.currentTimeMillis() - started).coerceAtLeast(1L)
                    Result.success(ProviderTestResult(text.take(240), elapsed, resolvedModel))
                }

                else -> {
                    if (provider.requiresApiKey && cleanKey.isBlank()) {
                        return@withContext Result.failure(
                            IllegalStateException(missingKeyMessage(provider))
                        )
                    }
                    val effectiveBase = when {
                        baseUrl.isNotBlank() -> baseUrl.trim()
                        provider == LlmProvider.LOCAL_LLM -> settings.localModelProfile.serverUrl.ifBlank { provider.defaultBaseUrl }
                        else -> provider.defaultBaseUrl
                    }
                    val endpoint = resolveChatCompletionsEndpoint(provider, effectiveBase)
                    if (!isPermittedEndpointUrl(endpoint)) {
                        return@withContext Result.failure(
                            IllegalStateException("HTTP разрешён только для локальных/LAN адресов; для внешних API используйте HTTPS.")
                        )
                    }
                    val payload = JSONObject().apply {
                        put("model", resolvedModel)
                        put("messages", JSONArray().put(JSONObject().apply {
                            put("role", "user")
                            put("content", probePrompt)
                        }))
                        put("temperature", 0.2)
                        put("max_tokens", if (provider == LlmProvider.POLLINATIONS) 512 else 160)
                        put("stream", false)
                    }
                    val reqBuilder = Request.Builder()
                        .url(endpoint)
                        .post(payload.toString().toRequestBody("application/json; charset=utf-8".toMediaType()))
                        .header("Content-Type", "application/json")
                        .header("Accept", "application/json")
                    if (cleanKey.isNotBlank()) {
                        reqBuilder.header("Authorization", "Bearer $cleanKey")
                    }
                    if (provider == LlmProvider.OPENROUTER) {
                        reqBuilder.header("HTTP-Referer", "https://lorecraft.app")
                        reqBuilder.header("X-Title", "LoreCraft RP Studio")
                    }

                    val text = withTimeout(35_000L) {
                        httpClient.newCall(reqBuilder.build()).execute().use { resp ->
                            val bodyStr = resp.body?.string().orEmpty()
                            if (!resp.isSuccessful) {
                                throw mapHttpError(provider, resp.code, bodyStr, resp.header("retry-after"), resolvedModel)
                            }
                            extractOpenAiNonStreamingText(JSONObject(bodyStr))
                        }
                    }
                    if (text.isBlank()) {
                        return@withContext Result.failure(
                            IllegalStateException("Модель $resolvedModel вернула пустой ответ.")
                        )
                    }
                    val elapsed = (System.currentTimeMillis() - started).coerceAtLeast(1L)
                    val visible = RpgAndThinkParser.stripThinkingForPrompt(text).ifBlank { text }
                    AppLogBuffer.i(TAG, "Chat test OK ($provider / $resolvedModel) in ${elapsed}ms")
                    Result.success(ProviderTestResult(visible.take(240), elapsed, resolvedModel))
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: TimeoutCancellationException) {
            Result.failure(IllegalStateException("Таймаут проверки ${provider.displayName} (35 сек)."))
        } catch (e: ConnectException) {
            Result.failure(mapConnectionError(provider, baseUrl, e))
        } catch (e: UnknownHostException) {
            Result.failure(IllegalStateException("Нет интернета или неверный адрес (${provider.displayName})."))
        } catch (e: Exception) {
            val mapped = if (provider == LlmProvider.GEMINI_AI_LOGIC) mapGeminiAiLogicError(e) else e
            AppLogBuffer.w(TAG, "Chat test failed for $provider/$resolvedModel", mapped)
            Result.failure(mapped)
        }
    }

    private fun streamGeminiAiLogic(
        modelId: String,
        context: BuiltRpContext,
        params: GenerationParams,
        streaming: Boolean,
        safetyLevel: SafetyLevel
    ): Flow<String> = flow {
        val firebaseInitialized = runCatching { FirebaseApp.getInstance() }.isSuccess
        if (!firebaseInitialized) {
            throw IllegalStateException(
                "Firebase не инициализирован. Переключите чат на Pollinations (без ключа), OpenRouter (:free) или локальную GGUF-модель."
            )
        }

        val safeModel = if (modelId.startsWith("gemini-")) modelId else "gemini-3.8-flash"
        val config = generationConfig {
            temperature = params.temperature.toFloat()
            topP = params.topP.toFloat()
            topK = params.topK
            maxOutputTokens = params.maxTokens
            if (params.stopSequences.isNotEmpty()) {
                stopSequences = params.stopSequences
            }
        }
        val model = Firebase.ai.generativeModel(
            modelName = safeModel,
            generationConfig = config,
            safetySettings = buildGeminiSafetySettings(safetyLevel),
            systemInstruction = content { text(context.systemPrompt) }
        )

        val geminiHistory: List<Content> = context.history.map { msg ->
            val roleName = if (msg.role == "assistant") "model" else "user"
            content(roleName) { text(msg.content) }
        }
        val chatSession = model.startChat(history = geminiHistory)

        try {
            val accumulated = StringBuilder()
            withTimeout(65_000L) {
                if (streaming) {
                    chatSession.sendMessageStream(context.latestUserPrompt).collect { chunk ->
                        val part = chunk.text.orEmpty()
                        if (part.isNotEmpty()) {
                            accumulated.append(part)
                            emit(accumulated.toString())
                        }
                    }
                } else {
                    val response = chatSession.sendMessage(context.latestUserPrompt)
                    val full = response.text.orEmpty()
                    if (full.isNotEmpty()) {
                        accumulated.append(full)
                        emit(full)
                    }
                }
            }
            if (accumulated.isBlank()) {
                throw IllegalStateException("Модель Gemini вернула пустой ответ.")
            }
            clearCooldown(LlmProvider.GEMINI_AI_LOGIC)
            AppLogBuffer.d(TAG, "Gemini reply completed (${accumulated.length} chars)")
        } catch (e: TimeoutCancellationException) {
            AppLogBuffer.e(TAG, "Gemini generation timed out", e)
            throw IllegalStateException("Таймаут ожидания ответа Gemini (65 сек). Попробуйте повторить.")
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            AppLogBuffer.e(TAG, "Gemini generation error", e)
            val mapped = mapGeminiAiLogicError(e)
            if (mapped is LlmQuotaOrRateLimitException) {
                recordCooldown(LlmProvider.GEMINI_AI_LOGIC, mapped.retryAfterSeconds)
            }
            throw mapped
        }
    }.flowOn(Dispatchers.IO)

    private fun streamOpenAiCompatible(
        provider: LlmProvider,
        baseUrl: String,
        apiKey: String,
        modelId: String,
        context: BuiltRpContext,
        params: GenerationParams,
        streaming: Boolean,
        settings: AppLocalSettings = AppLocalSettings(),
        deviceContext: Context? = null
    ): Flow<String> = flow {
        val cleanKey = sanitizeKey(apiKey)
        if (provider.requiresApiKey && cleanKey.isBlank()) {
            throw IllegalStateException(missingKeyMessage(provider))
        }
        val normalizedBase = baseUrl.trim().trimEnd('/')
        if (normalizedBase.isBlank() || !isPermittedEndpointUrl(normalizedBase)) {
            throw IllegalStateException("Некорректный URL эндпоинта (внешние API требуют HTTPS, HTTP — только для локальной сети): $baseUrl")
        }
        val endpoint = resolveChatCompletionsEndpoint(provider, normalizedBase)

        val messagesJson = JSONArray().apply {
            put(JSONObject().apply {
                put("role", "system")
                put("content", context.systemPrompt)
            })
            context.history.forEach { msg ->
                put(JSONObject().apply {
                    put("role", msg.role)
                    put("content", msg.content)
                })
            }
            put(JSONObject().apply {
                put("role", "user")
                put("content", context.latestUserPrompt)
            })
        }

        val ramSnap = if (provider == LlmProvider.LOCAL_LLM) {
            LocalRamWatchdog.inspectRam(deviceContext, settings.localModelProfile.ramPreset)
        } else {
            null
        }
        val effectiveMaxTokens = when {
            provider == LlmProvider.POLLINATIONS -> maxOf(params.maxTokens, 1024)
            ramSnap != null -> minOf(params.maxTokens, ramSnap.safeLocalMaxTokens)
            else -> params.maxTokens
        }
        val omitPenalties = provider == LlmProvider.POLLINATIONS || provider == LlmProvider.GEMINI_DIRECT

        val payload = JSONObject().apply {
            put("model", modelId)
            put("messages", messagesJson)
            put("temperature", params.temperature)
            put("top_p", params.topP)
            put("max_tokens", effectiveMaxTokens)
            if (!omitPenalties && params.presencePenalty != 0.0) {
                put("presence_penalty", params.presencePenalty)
            }
            if (!omitPenalties && params.frequencyPenalty != 0.0) {
                put("frequency_penalty", params.frequencyPenalty)
            }
            if (params.stopSequences.isNotEmpty() && provider != LlmProvider.POLLINATIONS) {
                put("stop", JSONArray(params.stopSequences))
            }
            put("stream", streaming)
        }

        val reqBuilder = Request.Builder()
            .url(endpoint)
            .post(payload.toString().toRequestBody("application/json; charset=utf-8".toMediaType()))
            .header("Content-Type", "application/json")
            .header("Accept", if (streaming) "text/event-stream" else "application/json")

        if (cleanKey.isNotBlank()) {
            reqBuilder.header("Authorization", "Bearer $cleanKey")
        }
        if (provider == LlmProvider.OPENROUTER) {
            reqBuilder.header("HTTP-Referer", "https://lorecraft.app")
            reqBuilder.header("X-Title", "LoreCraft RP Studio")
        }

        suspend fun executeRequest(emitChunk: suspend (String) -> Unit) {
            val accumulated = StringBuilder()
            val reasoningOnly = StringBuilder()
            var thinkOpened = false
            var thinkClosed = false

            withTimeout(90_000L) {
                httpClient.newCall(reqBuilder.build()).execute().use { response ->
                    val body = response.body
                    if (!response.isSuccessful || body == null) {
                        val errText = body?.string().orEmpty()
                        AppLogBuffer.w(TAG, "HTTP ${response.code} from ${provider.displayName}: ${errText.take(350)}")
                        throw mapHttpError(
                            provider = provider,
                            code = response.code,
                            rawBody = errText,
                            retryAfterHeader = response.header("retry-after"),
                            modelId = modelId
                        )
                    }
                    if (!streaming) {
                        val json = JSONObject(body.string())
                        val text = extractOpenAiNonStreamingText(json)
                        if (text.isNotEmpty()) {
                            accumulated.append(text)
                            emitChunk(text)
                        }
                    } else {
                        BufferedReader(InputStreamReader(body.byteStream())).use { reader ->
                            var line: String?
                            while (reader.readLine().also { line = it } != null) {
                                val trimmed = line?.trim().orEmpty()
                                if (!trimmed.startsWith("data:")) continue
                                val data = trimmed.removePrefix("data:").trim()
                                if (data == "[DONE]") break
                                if (data.isEmpty()) continue
                                val chunkJson = try {
                                    JSONObject(data)
                                } catch (_: Exception) {
                                    null
                                } ?: continue

                                val deltaObj = chunkJson.optJSONArray("choices")
                                    ?.optJSONObject(0)
                                    ?.optJSONObject("delta")

                                val reasoningDelta = deltaObj?.let { obj ->
                                    listOf("reasoning", "reasoning_content")
                                        .firstNotNullOfOrNull { key ->
                                            obj.optString(key).takeIf { it.isNotEmpty() && it != "null" }
                                        }
                                }.orEmpty()

                                val contentDelta = deltaObj?.optString("content")
                                    ?.takeIf { it.isNotEmpty() && it != "null" }
                                    .orEmpty()

                                if (reasoningDelta.isNotEmpty()) {
                                    if (!thinkOpened) {
                                        accumulated.append("<think>")
                                        thinkOpened = true
                                    }
                                    reasoningOnly.append(reasoningDelta)
                                    accumulated.append(reasoningDelta)
                                    emitChunk(accumulated.toString())
                                }
                                if (contentDelta.isNotEmpty()) {
                                    if (thinkOpened && !thinkClosed) {
                                        accumulated.append("</think>\n\n")
                                        thinkClosed = true
                                    }
                                    accumulated.append(contentDelta)
                                    emitChunk(accumulated.toString())
                                }
                            }
                        }
                        if (thinkOpened && !thinkClosed) {
                            accumulated.append("</think>\n\n")
                            thinkClosed = true
                            if (accumulated.toString().substringAfter("</think>").isBlank() && reasoningOnly.isNotBlank()) {
                                accumulated.clear()
                                accumulated.append(reasoningOnly.toString().trim())
                            }
                            emitChunk(accumulated.toString())
                        }
                    }
                }
            }
            if (accumulated.isBlank()) {
                throw IllegalStateException("Модель $modelId вернула пустой ответ.")
            }
            clearCooldown(provider)
            AppLogBuffer.d(TAG, "${provider.displayName} reply completed (${accumulated.length} chars)")
        }

        try {
            if (provider == LlmProvider.LOCAL_LLM) {
                LocalRamWatchdog.localInferenceMutex.withLock {
                    executeRequest { emit(it) }
                }
            } else {
                executeRequest { emit(it) }
            }
        } catch (e: TimeoutCancellationException) {
            AppLogBuffer.e(TAG, "OpenAI-compatible timed out", e)
            throw IllegalStateException("Таймаут запроса к ${provider.displayName} (90 сек).")
        } catch (e: CancellationException) {
            throw e
        } catch (e: ConnectException) {
            AppLogBuffer.w(TAG, "Connection refused to $endpoint", e)
            throw mapConnectionError(provider, normalizedBase, e)
        } catch (e: UnknownHostException) {
            AppLogBuffer.w(TAG, "Host unreachable for $endpoint", e)
            throw IllegalStateException("Нет интернета или недоступен сервер ${provider.displayName}.")
        } catch (e: LlmQuotaOrRateLimitException) {
            throw e
        } catch (e: Exception) {
            AppLogBuffer.e(TAG, "OpenAI-compatible generation failed", e)
            throw IllegalStateException(e.localizedMessage ?: "Ошибка API ${provider.displayName}.")
        }
    }.flowOn(Dispatchers.IO)

    private fun streamAnthropic(
        baseUrl: String,
        apiKey: String,
        modelId: String,
        context: BuiltRpContext,
        params: GenerationParams,
        streaming: Boolean
    ): Flow<String> = flow {
        val cleanKey = sanitizeKey(apiKey)
        if (cleanKey.isBlank()) {
            throw IllegalStateException(missingKeyMessage(LlmProvider.ANTHROPIC))
        }
        val normalizedBase = baseUrl.trim().trimEnd('/')
        val endpoint = if (normalizedBase.endsWith("/messages")) {
            normalizedBase
        } else {
            "$normalizedBase/messages"
        }

        val messagesJson = JSONArray().apply {
            context.history.forEach { msg ->
                put(JSONObject().apply {
                    put("role", msg.role)
                    put("content", msg.content)
                })
            }
            put(JSONObject().apply {
                put("role", "user")
                put("content", context.latestUserPrompt)
            })
        }

        val payload = JSONObject().apply {
            put("model", modelId)
            put("system", context.systemPrompt)
            put("messages", messagesJson)
            put("max_tokens", params.maxTokens)
            put("temperature", params.temperature.coerceIn(0.0, 1.0))
            put("top_p", params.topP)
            put("top_k", params.topK)
            if (params.stopSequences.isNotEmpty()) {
                put("stop_sequences", JSONArray(params.stopSequences))
            }
            put("stream", streaming)
        }

        val request = Request.Builder()
            .url(endpoint)
            .post(payload.toString().toRequestBody("application/json; charset=utf-8".toMediaType()))
            .header("Content-Type", "application/json")
            .header("x-api-key", cleanKey)
            .header("anthropic-version", "2023-06-01")
            .build()

        try {
            val accumulated = StringBuilder()
            withTimeout(65_000L) {
                httpClient.newCall(request).execute().use { response ->
                    val body = response.body
                    if (!response.isSuccessful || body == null) {
                        val errText = body?.string().orEmpty()
                        throw mapHttpError(
                            provider = LlmProvider.ANTHROPIC,
                            code = response.code,
                            rawBody = errText,
                            retryAfterHeader = response.header("retry-after"),
                            modelId = modelId
                        )
                    }
                    if (!streaming) {
                        val json = JSONObject(body.string())
                        val contentArray = json.optJSONArray("content")
                        val text = buildString {
                            if (contentArray != null) {
                                for (i in 0 until contentArray.length()) {
                                    append(contentArray.optJSONObject(i)?.optString("text").orEmpty())
                                }
                            }
                        }
                        if (text.isNotEmpty()) {
                            accumulated.append(text)
                            emit(text)
                        }
                    } else {
                        BufferedReader(InputStreamReader(body.byteStream())).use { reader ->
                            var line: String?
                            while (reader.readLine().also { line = it } != null) {
                                val trimmed = line?.trim().orEmpty()
                                if (!trimmed.startsWith("data:")) continue
                                val data = trimmed.removePrefix("data:").trim()
                                if (data.isEmpty()) continue
                                val json = try {
                                    JSONObject(data)
                                } catch (_: Exception) {
                                    null
                                } ?: continue
                                when (json.optString("type")) {
                                    "content_block_delta" -> {
                                        val deltaText = json.optJSONObject("delta")
                                            ?.optString("text")
                                            .orEmpty()
                                        if (deltaText.isNotEmpty()) {
                                            accumulated.append(deltaText)
                                            emit(accumulated.toString())
                                        }
                                    }
                                    "message_stop" -> break
                                }
                            }
                        }
                    }
                }
            }
            if (accumulated.isBlank()) {
                throw IllegalStateException("Модель Claude вернула пустой ответ.")
            }
        } catch (e: TimeoutCancellationException) {
            AppLogBuffer.e(TAG, "Anthropic generation timed out", e)
            throw IllegalStateException("Таймаут запроса к Claude (65 сек).")
        } catch (e: CancellationException) {
            throw e
        } catch (e: LlmQuotaOrRateLimitException) {
            throw e
        } catch (e: Exception) {
            AppLogBuffer.e(TAG, "Anthropic generation failed", e)
            throw IllegalStateException(e.localizedMessage ?: "Ошибка API Anthropic Claude.")
        }
    }.flowOn(Dispatchers.IO)

    suspend fun compressSceneMemory(
        chat: RpChat,
        messages: List<RpMessage>,
        settings: AppLocalSettings = AppLocalSettings()
    ): Result<String> = withContext(Dispatchers.IO) {
        try {
            val transcript = messages.takeLast(40).joinToString("\n") { msg ->
                val who = msg.speakerName.ifBlank { if (msg.role == "assistant") chat.characterName else chat.userPersonaName }
                val clean = RpgAndThinkParser.stripThinkingForPrompt(msg.activeContent())
                "$who: $clean"
            }
            val prompt = """
                Сожми ключевые события, договорённости, состояние инвентаря/отношений и важные детали этой ролевой сцены в компактную сводку (до 8 предложений на русском языке).
                Не придумывай новые факты. Сохрани имена собственные.

                Предыдущая память:
                ${chat.memorySummary.ifBlank { "Отсутствует" }}

                Последние сообщения:
                $transcript
            """.trimIndent()

            val activeProvider = chat.providerEnum()
            val canTryFirebase = activeProvider == LlmProvider.GEMINI_AI_LOGIC &&
                runCatching { FirebaseApp.getInstance() }.isSuccess

            if (canTryFirebase) {
                val firebaseAttempt = runCatching {
                    val summarizer = Firebase.ai.generativeModel(
                        modelName = "gemini-3.5-flash-lite",
                        generationConfig = generationConfig {
                            temperature = 0.3f
                            topP = 0.85f
                            maxOutputTokens = 768
                        }
                    )
                    val response = withTimeout(45_000L) {
                        summarizer.generateContent(prompt)
                    }
                    response.text?.trim().orEmpty()
                }
                val text = firebaseAttempt.getOrNull().orEmpty()
                if (text.isNotBlank()) {
                    return@withContext Result.success(text)
                }
                AppLogBuffer.w(TAG, "Firebase memory compression failed; falling back to Pollinations", firebaseAttempt.exceptionOrNull())
            }

            // Use chat's active provider if non-Firebase (and non-Anthropic/keyed unless configured), otherwise keyless Pollinations
            val fallbackProvider = if (activeProvider != LlmProvider.GEMINI_AI_LOGIC &&
                activeProvider != LlmProvider.ANTHROPIC &&
                (!activeProvider.requiresApiKey || settings.configFor(activeProvider).apiKey.isNotBlank())
            ) {
                activeProvider
            } else {
                LlmProvider.POLLINATIONS
            }
            val cfg = settings.configFor(fallbackProvider)
            val modelId = if (fallbackProvider == activeProvider) {
                chat.modelId.ifBlank { fallbackProvider.suggestedModels.firstOrNull() ?: "openai" }
            } else {
                fallbackProvider.suggestedModels.firstOrNull() ?: "openai"
            }
            val base = if (fallbackProvider == LlmProvider.LOCAL_LLM) {
                settings.localModelProfile.serverUrl.ifBlank { fallbackProvider.defaultBaseUrl }
            } else {
                cfg.baseUrl.ifBlank { fallbackProvider.defaultBaseUrl }
            }
            val endpoint = resolveChatCompletionsEndpoint(fallbackProvider, base)
            val cleanKey = sanitizeKey(cfg.apiKey)
            val payload = JSONObject().apply {
                put("model", modelId)
                put("messages", JSONArray().put(JSONObject().apply {
                    put("role", "user")
                    put("content", prompt)
                }))
                put("temperature", 0.3)
                put("max_tokens", 1024)
                put("stream", false)
            }
            val reqBuilder = Request.Builder()
                .url(endpoint)
                .post(payload.toString().toRequestBody("application/json; charset=utf-8".toMediaType()))
                .header("Content-Type", "application/json")
                .header("Accept", "application/json")
            if (cleanKey.isNotBlank()) {
                reqBuilder.header("Authorization", "Bearer $cleanKey")
            }
            if (fallbackProvider == LlmProvider.OPENROUTER) {
                reqBuilder.header("HTTP-Referer", "https://lorecraft.app")
                reqBuilder.header("X-Title", "LoreCraft RP Studio")
            }
            val summary = withTimeout(45_000L) {
                httpClient.newCall(reqBuilder.build()).execute().use { resp ->
                    val bodyStr = resp.body?.string().orEmpty()
                    if (!resp.isSuccessful) {
                        throw mapHttpError(fallbackProvider, resp.code, bodyStr, resp.header("retry-after"), modelId)
                    }
                    val raw = extractOpenAiNonStreamingText(JSONObject(bodyStr))
                    RpgAndThinkParser.stripThinkingForPrompt(raw).ifBlank { raw }.trim()
                }
            }
            if (summary.isBlank()) {
                Result.failure(IllegalStateException("Не удалось сформировать сводку памяти."))
            } else {
                Result.success(summary)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            AppLogBuffer.e(TAG, "Memory compression failed", e)
            Result.failure(e)
        }
    }

    suspend fun discoverCatalogModels(settings: AppLocalSettings): List<ModelCatalogItem> = withContext(Dispatchers.IO) {
        val merged = LinkedHashMap<String, ModelCatalogItem>()
        FreeModelCatalog.curated().forEach { item ->
            val withKeyStatus = if (item.provider.requiresApiKey &&
                sanitizeKey(settings.configFor(item.provider).apiKey).isBlank()
            ) {
                item.copy(status = ModelProbeStatus.NEEDS_KEY)
            } else {
                item
            }
            merged[item.stableKey] = withKeyStatus
        }

        // 1. Live Pollinations models (keyless)
        runCatching {
            val req = Request.Builder()
                .url("https://text.pollinations.ai/models")
                .header("Accept", "application/json")
                .get()
                .build()
            withTimeout(10_000L) {
                httpClient.newCall(req).execute().use { resp ->
                    if (resp.isSuccessful) {
                        val ids = parseModelsResponse(resp.body?.string().orEmpty())
                        ids.take(20).forEach { id ->
                            val candidate = ModelCatalogItem(
                                provider = LlmProvider.POLLINATIONS,
                                modelId = id,
                                displayName = "Pollinations: $id",
                                descriptionRu = "Обнаружена в живом списке text.pollinations.ai (бесплатно, без ключа).",
                                tags = FreeModelCatalog.inferTags(LlmProvider.POLLINATIONS, id)
                            )
                            merged.putIfAbsent(candidate.stableKey, candidate)
                        }
                    }
                }
            }
        }.onFailure { AppLogBuffer.d(TAG, "Pollinations discovery skipped: ${it.message}") }

        // 2. Live OpenRouter public :free catalog (no auth needed to read /models)
        runCatching {
            val hasOpenRouterKey = sanitizeKey(settings.configFor(LlmProvider.OPENROUTER).apiKey).isNotBlank()
            val req = Request.Builder()
                .url("https://openrouter.ai/api/v1/models")
                .header("Accept", "application/json")
                .get()
                .build()
            withTimeout(12_000L) {
                httpClient.newCall(req).execute().use { resp ->
                    if (resp.isSuccessful) {
                        val root = JSONObject(resp.body?.string().orEmpty())
                        val arr = root.optJSONArray("data") ?: JSONArray()
                        var added = 0
                        for (i in 0 until arr.length()) {
                            if (added >= 24) break
                            val obj = arr.optJSONObject(i) ?: continue
                            val id = obj.optString("id").trim()
                            if (id.isBlank()) continue
                            val pricing = obj.optJSONObject("pricing")
                            val promptPrice = pricing?.optString("prompt")?.toDoubleOrNull()
                            val isFree = id.endsWith(":free") || id == "openrouter/free" || (promptPrice != null && promptPrice == 0.0)
                            if (!isFree) continue
                            val niceName = obj.optString("name").ifBlank { id }
                            val tags = FreeModelCatalog.inferTags(LlmProvider.OPENROUTER, id) + ModelTag.FREE
                            val item = ModelCatalogItem(
                                provider = LlmProvider.OPENROUTER,
                                modelId = id,
                                displayName = niceName,
                                descriptionRu = "Бесплатная модель OpenRouter (:free). Нужен бесплатный ключ с openrouter.ai/keys.",
                                tags = tags,
                                status = if (hasOpenRouterKey) ModelProbeStatus.UNKNOWN else ModelProbeStatus.NEEDS_KEY
                            )
                            if (!merged.containsKey(item.stableKey)) {
                                merged[item.stableKey] = item
                                added++
                            }
                        }
                    }
                }
            }
        }.onFailure { AppLogBuffer.d(TAG, "OpenRouter catalog discovery skipped: ${it.message}") }

        // 3. Groq /models if user configured Groq key
        val groqKey = sanitizeKey(settings.configFor(LlmProvider.GROQ).apiKey)
        if (groqKey.isNotBlank()) {
            testOpenAiServerModels(
                provider = LlmProvider.GROQ,
                baseUrl = settings.configFor(LlmProvider.GROQ).baseUrl.ifBlank { LlmProvider.GROQ.defaultBaseUrl },
                apiKey = groqKey
            ).onSuccess { list ->
                list.take(12).forEach { id ->
                    val item = ModelCatalogItem(
                        provider = LlmProvider.GROQ,
                        modelId = id,
                        displayName = "Groq: $id",
                        descriptionRu = "Модель из вашего Groq Free Tier аккаунта.",
                        tags = FreeModelCatalog.inferTags(LlmProvider.GROQ, id)
                    )
                    merged.putIfAbsent(item.stableKey, item)
                }
            }
        }

        // 4. Local GGUF server quick probe (127.0.0.1 / LAN)
        val localUrl = settings.localModelProfile.serverUrl.trim()
        if (localUrl.startsWith("http://") || localUrl.startsWith("https://")) {
            runCatching {
                withTimeout(2_500L) {
                    testOpenAiServerModels(
                        provider = LlmProvider.LOCAL_LLM,
                        baseUrl = localUrl,
                        apiKey = ""
                    ).onSuccess { list ->
                        list.take(10).forEach { id ->
                            val item = ModelCatalogItem(
                                provider = LlmProvider.LOCAL_LLM,
                                modelId = id,
                                displayName = "Локальная GGUF: $id",
                                descriptionRu = "Работает локально на $localUrl без облака и цензуры.",
                                tags = setOf(
                                    ModelTag.FREE,
                                    ModelTag.NO_KEY,
                                    ModelTag.LOCAL,
                                    ModelTag.UNCENSORED_FRIENDLY,
                                    ModelTag.RP_OPTIMIZED
                                ),
                                status = ModelProbeStatus.ONLINE,
                                latencyMs = 25L,
                                statusNote = "Найдена на локальном сервере"
                            )
                            merged[item.stableKey] = item
                        }
                    }
                }
            }
        }

        merged.values.toList()
    }

    suspend fun probeCatalogItem(
        item: ModelCatalogItem,
        settings: AppLocalSettings
    ): ModelCatalogItem = withContext(Dispatchers.IO) {
        val cfg = settings.configFor(item.provider)
        val cleanKey = sanitizeKey(cfg.apiKey)
        if (item.provider.requiresApiKey && cleanKey.isBlank()) {
            return@withContext item.copy(
                status = ModelProbeStatus.NEEDS_KEY,
                statusNote = missingKeyMessage(item.provider)
            )
        }
        val base = if (item.provider == LlmProvider.LOCAL_LLM) {
            settings.localModelProfile.serverUrl.ifBlank { cfg.baseUrl }
        } else {
            cfg.baseUrl
        }
        val res = testChatCompletion(
            provider = item.provider,
            baseUrl = base,
            apiKey = cleanKey,
            modelId = item.modelId,
            settings = settings
        )
        res.fold(
            onSuccess = { ok ->
                item.copy(
                    status = ModelProbeStatus.ONLINE,
                    latencyMs = ok.latencyMs,
                    statusNote = "Онлайн • ${ok.replyPreview.take(44)}"
                )
            },
            onFailure = { err ->
                val msg = err.localizedMessage.orEmpty()
                val lower = msg.lowercase()
                val st = when {
                    lower.contains("402") || lower.contains("429") || lower.contains("лимит") -> ModelProbeStatus.RATE_LIMITED
                    lower.contains("403") || lower.contains("app check") || lower.contains("appcheck") -> ModelProbeStatus.AUTH_ERROR
                    else -> ModelProbeStatus.OFFLINE
                }
                item.copy(
                    status = st,
                    latencyMs = null,
                    statusNote = msg.take(130)
                )
            }
        )
    }

    suspend fun expandScenarioWithAi(
        titleHint: String,
        subtitleHint: String = "",
        rpMode: RpMode = RpMode.CHARACTER_1ON1,
        characterHint: String = "",
        systemPromptHint: String = "",
        firstMessageHint: String = "",
        freeformIdea: String = "",
        settings: AppLocalSettings = AppLocalSettings()
    ): Result<ExpandedScenarioDraft> = withContext(Dispatchers.IO) {
        try {
            val seedInfo = buildString {
                if (freeformIdea.isNotBlank()) appendLine("Главная идея пользователя: ${freeformIdea.trim()}")
                if (titleHint.isNotBlank()) appendLine("Название / Мир: ${titleHint.trim()}")
                if (subtitleHint.isNotBlank()) appendLine("Жанр / Подзаголовок: ${subtitleHint.trim()}")
                appendLine("Формат RP: ${rpMode.labelRu} (${rpMode.descriptionRu})")
                if (characterHint.isNotBlank()) appendLine("Имя персонажа / Мастера: ${characterHint.trim()}")
                if (systemPromptHint.isNotBlank()) appendLine("Пожелания к системному промпту: ${systemPromptHint.trim()}")
                if (firstMessageHint.isNotBlank()) appendLine("Набросок первой сцены: ${firstMessageHint.trim()}")
            }.trim()

            val prompt = """
                Ты — профессиональный гейм-дизайнер и сценарист ролевых игр (LoreCraft).
                Пользователь дал краткий набросок или заполнил только часть полей. Продумай мир, конфликт, предысторию и детали НАПЕРЁД, чтобы сразу получилась богатая, живая RP-сессия на русском языке.

                Входные данные:
                $seedInfo

                Верни СТРОГО один JSON-объект (без пояснений до/после) со следующими ключами:
                {
                  "title": "Выразительное название мира или чата",
                  "subtitle": "Краткий жанр и зацепка в 1 строку",
                  "characterName": "Имя главного NPC или Мастера Игры",
                  "systemPrompt": "Подробная инструкция для нейросети: характер, мотивация, секреты персонажа, стиль отыгрыша (*действие* и «речь»), запрет писать за {{user}}, глубина мира (4-7 предложений)",
                  "characterBio": "Внешность, биография, скрытая цель, слабые места и привычки персонажа (4-6 предложений)",
                  "firstMessage": "Атмосферная открывающая сцена с действиями в *звёздочках* и живой репликой, адресованной {{user}} (2-3 абзаца)",
                  "exampleDialogue": "2-3 коротких примера характерных реплик персонажа",
                  "participants": ["Имя1", "Имя2"],
                  "tags": ["Жанр1", "Жанр2", "Жанр3"],
                  "loreNotes": ["Неизменный факт мира 1", "Тайна или закон мира 2", "Опасность 3"],
                  "loreEntries": [
                    {"title": "Ключевой объект/фракция", "keys": ["ключ1", "ключ2"], "content": "Описание для лорбука", "alwaysActive": false}
                  ],
                  "rpgEnabled": ${rpMode == RpMode.GAME_MASTER},
                  "activeQuest": "Стартовая цель / завязка квеста",
                  "inventory": ["Стартовый предмет 1", "Предмет 2", "Предмет 3"],
                  "stats": {"Сила": 12, "Ловкость": 14, "Интеллект": 13, "Харизма": 13},
                  "locations": [
                    {"id": "loc_1", "name": "Стартовая локация", "description": "Атмосферное описание", "connections": ["loc_2"]},
                    {"id": "loc_2", "name": "Соседняя опасная зона", "description": "Описание", "connections": ["loc_1", "loc_3"]},
                    {"id": "loc_3", "name": "Тайное убежище или цель", "description": "Описание", "connections": ["loc_2"]}
                  ]
                }
            """.trimIndent()

            // Try user's preferred provider first if usable, then seamless Pollinations fallback
            val candidates = buildList {
                val pref = settings.defaultProvider
                if (pref != LlmProvider.ANTHROPIC &&
                    (!pref.requiresApiKey || sanitizeKey(settings.configFor(pref).apiKey).isNotBlank())
                ) {
                    add(pref to settings.defaultModelId.ifBlank { pref.suggestedModels.firstOrNull() ?: "openai" })
                }
                add(LlmProvider.POLLINATIONS to "openai")
                add(LlmProvider.POLLINATIONS to "openai-fast")
                if (sanitizeKey(settings.configFor(LlmProvider.GEMINI_DIRECT).apiKey).isNotBlank()) {
                    add(LlmProvider.GEMINI_DIRECT to "gemini-2.5-flash")
                }
                if (sanitizeKey(settings.configFor(LlmProvider.OPENROUTER).apiKey).isNotBlank()) {
                    add(LlmProvider.OPENROUTER to "openrouter/free")
                }
                if (sanitizeKey(settings.configFor(LlmProvider.GROQ).apiKey).isNotBlank()) {
                    add(LlmProvider.GROQ to "llama-3.3-70b-versatile")
                }
            }.distinct()

            var rawResponse: String? = null
            var lastErr: Throwable? = null
            for ((prov, modelId) in candidates) {
                val attempt = runCatching {
                    when (prov) {
                        LlmProvider.GEMINI_AI_LOGIC -> {
                            check(runCatching { FirebaseApp.getInstance() }.isSuccess) { "Firebase not ready" }
                            val safeModel = if (modelId.startsWith("gemini-")) modelId else "gemini-3.8-flash"
                            val model = Firebase.ai.generativeModel(
                                modelName = safeModel,
                                generationConfig = generationConfig {
                                    temperature = 0.8f
                                    maxOutputTokens = 2048
                                },
                                safetySettings = buildGeminiSafetySettings(settings.safetyLevel)
                            )
                            withTimeout(35_000L) {
                                model.generateContent(prompt).text.orEmpty()
                            }
                        }
                        else -> {
                            val cfg = settings.configFor(prov)
                            val cleanKey = sanitizeKey(cfg.apiKey)
                            val base = if (prov == LlmProvider.LOCAL_LLM) {
                                settings.localModelProfile.serverUrl.ifBlank { prov.defaultBaseUrl }
                            } else {
                                cfg.baseUrl.ifBlank { prov.defaultBaseUrl }
                            }
                            val endpoint = resolveChatCompletionsEndpoint(prov, base)
                            val payload = JSONObject().apply {
                                put("model", modelId)
                                put("messages", JSONArray().put(JSONObject().apply {
                                    put("role", "user")
                                    put("content", prompt)
                                }))
                                put("temperature", 0.8)
                                put("max_tokens", 2048)
                                put("stream", false)
                            }
                            val reqBuilder = Request.Builder()
                                .url(endpoint)
                                .post(payload.toString().toRequestBody("application/json; charset=utf-8".toMediaType()))
                                .header("Content-Type", "application/json")
                                .header("Accept", "application/json")
                            if (cleanKey.isNotBlank()) {
                                reqBuilder.header("Authorization", "Bearer $cleanKey")
                            }
                            if (prov == LlmProvider.OPENROUTER) {
                                reqBuilder.header("HTTP-Referer", "https://lorecraft.app")
                                reqBuilder.header("X-Title", "LoreCraft RP Studio")
                            }
                            withTimeout(35_000L) {
                                httpClient.newCall(reqBuilder.build()).execute().use { resp ->
                                    val bodyStr = resp.body?.string().orEmpty()
                                    if (!resp.isSuccessful) {
                                        throw mapHttpError(prov, resp.code, bodyStr, resp.header("retry-after"), modelId)
                                    }
                                    extractOpenAiNonStreamingText(JSONObject(bodyStr))
                                }
                            }
                        }
                    }
                }
                val text = attempt.getOrNull()?.trim().orEmpty()
                if (text.isNotBlank() && text.contains("{")) {
                    rawResponse = text
                    break
                }
                lastErr = attempt.exceptionOrNull() ?: IllegalStateException("Пустой или не-JSON ответ")
            }

            if (rawResponse == null) {
                return@withContext Result.failure(
                    lastErr ?: IllegalStateException("Не удалось связаться с бесплатной моделью для авто-заполнения.")
                )
            }

            val draft = parseExpandedScenarioDraft(
                rawText = rawResponse,
                fallbackTitle = titleHint,
                fallbackSubtitle = subtitleHint,
                fallbackRpMode = rpMode,
                fallbackCharacter = characterHint,
                fallbackSystemPrompt = systemPromptHint,
                fallbackFirstMessage = firstMessageHint
            )
            Result.success(draft)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            AppLogBuffer.e(TAG, "Scenario AI expansion failed", e)
            Result.failure(e)
        }
    }

    suspend fun completeSinglePrompt(
        provider: LlmProvider,
        modelId: String,
        systemPrompt: String,
        userPrompt: String,
        settings: AppLocalSettings,
        maxTokens: Int = 512,
        temperature: Double = 0.65,
        deviceContext: Context? = null
    ): Result<String> = withContext(Dispatchers.IO) {
        val resolvedModel = modelId.trim().ifBlank {
            provider.suggestedModels.firstOrNull() ?: "openai"
        }
        try {
            when (provider) {
                LlmProvider.GEMINI_AI_LOGIC -> {
                    if (runCatching { FirebaseApp.getInstance() }.isFailure) {
                        return@withContext Result.failure(IllegalStateException("Firebase не инициализирован"))
                    }
                    val safeModel = if (resolvedModel.startsWith("gemini-")) resolvedModel else "gemini-3.8-flash"
                    val model = Firebase.ai.generativeModel(
                        modelName = safeModel,
                        generationConfig = generationConfig {
                            this.temperature = temperature.toFloat().coerceIn(0f, 2f)
                            this.maxOutputTokens = maxTokens.coerceIn(64, 4096)
                        },
                        safetySettings = buildGeminiSafetySettings(settings.safetyLevel),
                        systemInstruction = content { text(systemPrompt) }
                    )
                    val out = withTimeout(35_000L) { model.generateContent(userPrompt).text.orEmpty().trim() }
                    if (out.isBlank()) Result.failure(IllegalStateException("Пустой ответ от Gemini"))
                    else Result.success(out)
                }

                LlmProvider.ANTHROPIC -> {
                    val cleanKey = sanitizeKey(settings.configFor(provider).apiKey)
                    if (cleanKey.isBlank()) return@withContext Result.failure(IllegalStateException(missingKeyMessage(provider)))
                    val base = settings.configFor(provider).baseUrl.ifBlank { provider.defaultBaseUrl }.trimEnd('/')
                    val endpoint = if (base.endsWith("/messages")) base else "$base/messages"
                    val payload = JSONObject().apply {
                        put("model", resolvedModel)
                        put("system", systemPrompt)
                        put("messages", JSONArray().put(JSONObject().apply {
                            put("role", "user")
                            put("content", userPrompt)
                        }))
                        put("max_tokens", maxTokens.coerceIn(64, 4096))
                        put("temperature", temperature.coerceIn(0.0, 1.0))
                        put("stream", false)
                    }
                    val req = Request.Builder()
                        .url(endpoint)
                        .post(payload.toString().toRequestBody("application/json; charset=utf-8".toMediaType()))
                        .header("Content-Type", "application/json")
                        .header("x-api-key", cleanKey)
                        .header("anthropic-version", "2023-06-01")
                        .build()
                    val text = withTimeout(40_000L) {
                        httpClient.newCall(req).execute().use { resp ->
                            val bodyStr = resp.body?.string().orEmpty()
                            if (!resp.isSuccessful) {
                                throw mapHttpError(provider, resp.code, bodyStr, resp.header("retry-after"), resolvedModel)
                            }
                            val arr = JSONObject(bodyStr).optJSONArray("content")
                            buildString {
                                if (arr != null) {
                                    for (i in 0 until arr.length()) {
                                        append(arr.optJSONObject(i)?.optString("text").orEmpty())
                                    }
                                }
                            }.trim()
                        }
                    }
                    if (text.isBlank()) Result.failure(IllegalStateException("Пустой ответ от Claude"))
                    else Result.success(text)
                }

                else -> {
                    val cfg = settings.configFor(provider)
                    val cleanKey = sanitizeKey(cfg.apiKey)
                    if (provider.requiresApiKey && cleanKey.isBlank()) {
                        return@withContext Result.failure(IllegalStateException(missingKeyMessage(provider)))
                    }
                    val base = if (provider == LlmProvider.LOCAL_LLM) {
                        settings.localModelProfile.serverUrl.ifBlank { cfg.baseUrl.ifBlank { provider.defaultBaseUrl } }
                    } else {
                        cfg.baseUrl.ifBlank { provider.defaultBaseUrl }
                    }
                    val endpoint = resolveChatCompletionsEndpoint(provider, base)
                    val ramCap = if (provider == LlmProvider.LOCAL_LLM) {
                        LocalRamWatchdog.inspectRam(deviceContext, settings.localModelProfile.ramPreset).safeLocalMaxTokens
                    } else maxTokens
                    val payload = JSONObject().apply {
                        put("model", resolvedModel)
                        put("messages", JSONArray().apply {
                            if (systemPrompt.isNotBlank()) {
                                put(JSONObject().apply {
                                    put("role", "system")
                                    put("content", systemPrompt)
                                })
                            }
                            put(JSONObject().apply {
                                put("role", "user")
                                put("content", userPrompt)
                            })
                        })
                        put("temperature", temperature.coerceIn(0.0, 2.0))
                        put("max_tokens", minOf(maxTokens, ramCap).coerceIn(64, 4096))
                        put("stream", false)
                    }
                    val reqBuilder = Request.Builder()
                        .url(endpoint)
                        .post(payload.toString().toRequestBody("application/json; charset=utf-8".toMediaType()))
                        .header("Content-Type", "application/json")
                        .header("Accept", "application/json")
                    if (cleanKey.isNotBlank()) reqBuilder.header("Authorization", "Bearer $cleanKey")
                    if (provider == LlmProvider.OPENROUTER) {
                        reqBuilder.header("HTTP-Referer", "https://lorecraft.app")
                        reqBuilder.header("X-Title", "LoreCraft RP Studio")
                    }
                    suspend fun runReq(): String = withTimeout(45_000L) {
                        httpClient.newCall(reqBuilder.build()).execute().use { resp ->
                            val bodyStr = resp.body?.string().orEmpty()
                            if (!resp.isSuccessful) {
                                throw mapHttpError(provider, resp.code, bodyStr, resp.header("retry-after"), resolvedModel)
                            }
                            extractOpenAiNonStreamingText(JSONObject(bodyStr))
                        }
                    }
                    val text = if (provider == LlmProvider.LOCAL_LLM) {
                        LocalRamWatchdog.localInferenceMutex.withLock { runReq() }
                    } else {
                        runReq()
                    }
                    val visible = RpgAndThinkParser.stripThinkingForPrompt(text).ifBlank { text }.trim()
                    if (visible.isBlank()) Result.failure(IllegalStateException("Пустой ответ от ${provider.displayName}"))
                    else Result.success(visible)
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun generateActionSuggestions(
        chat: RpChat,
        messages: List<RpMessage>,
        settings: AppLocalSettings,
        count: Int = 3,
        deviceContext: Context? = null
    ): Result<List<String>> = withContext(Dispatchers.IO) {
        val recent = messages.takeLast(6).joinToString("\n") { m ->
            val who = m.speakerName.ifBlank { m.role }
            "$who: ${RpgAndThinkParser.stripThinkingForPrompt(m.activeContent()).take(280)}"
        }
        val sys = "Ты — помощник игрока в ролевой игре (RP). Предложи ровно $count разнообразных, выразительных вариантов следующего хода игрока (${chat.userPersonaName}) по-русски. Каждый вариант: 4–14 слов, можно с коротким *действием* или «репликой»."
        val usr = """
            Сцена: ${chat.title} (${chat.rpModeEnum().labelRu})
            Локация: ${chat.rpgState.currentLocation()?.name ?: "Не указана"}
            Последние реплики:
            $recent

            Верни СТРОГО JSON-массив из $count строк, например:
            ["*Осторожно проверяет замок* «Кто здесь?»", "*Шаг назад к выходу*", "Задать вопрос о карте"]
        """.trimIndent()

        val prov = chat.providerEnum()
        val model = chat.modelId.ifBlank { prov.suggestedModels.firstOrNull() ?: "openai" }
        val res = completeSinglePrompt(
            provider = prov,
            modelId = model,
            systemPrompt = sys,
            userPrompt = usr,
            settings = settings,
            maxTokens = 260,
            temperature = 0.85,
            deviceContext = deviceContext
        )
        res.map { raw ->
            val cleaned = raw.replace("```json", "", ignoreCase = true).replace("```", "").trim()
            val arrStart = cleaned.indexOf('[')
            val arrEnd = cleaned.lastIndexOf(']')
            val parsed = if (arrStart >= 0 && arrEnd > arrStart) {
                runCatching {
                    val arr = JSONArray(cleaned.substring(arrStart, arrEnd + 1))
                    (0 until arr.length()).mapNotNull { i ->
                        arr.optString(i).trim().takeIf { it.isNotBlank() }
                    }
                }.getOrNull()
            } else null

            val list = parsed ?: cleaned.lines()
                .map { it.trim().removePrefix("-").removePrefix("•").replace(Regex("^\\d+[.)]\\s*"), "").trim('"', ' ') }
                .filter { it.isNotBlank() && it.length >= 3 }
            list.distinct().take(count)
        }
    }

    fun buildVisualScenePrompt(
        messageText: String,
        locationName: String = "",
        settingHint: String = ""
    ): String {
        val plain = SemanticMarkupEngine.stripTagsToPlainText(
            RpgAndThinkParser.stripMachineTagsOnly(
                RpgAndThinkParser.stripThinkingForPrompt(messageText)
            )
        ).replace("*", " ").replace("\n", " ").trim()
        val snippet = plain.take(260)
        return listOf(
            settingHint.trim(),
            locationName.trim(),
            snippet,
            "cinematic dark fantasy RPG illustration, atmospheric volumetric lighting, detailed environment art, no text"
        ).filter { it.isNotBlank() }.joinToString(", ")
    }

    suspend fun generateChronicleAndMemorySummary(
        chat: RpChat,
        messages: List<RpMessage>,
        settings: AppLocalSettings,
        deviceContext: Context? = null
    ): Result<Pair<String, ChronicleEntry?>> = withContext(Dispatchers.IO) {
        val transcript = messages.takeLast(32).joinToString("\n") { msg ->
            val who = msg.speakerName.ifBlank { if (msg.role == "assistant") chat.characterName else chat.userPersonaName }
            "$who: ${RpgAndThinkParser.stripThinkingForPrompt(msg.activeContent()).take(400)}"
        }
        val sys = "Ты — Агент-Хроник ролевой игры. Веди точную память мира и летопись событий на русском языке."
        val usr = """
            Предыдущая память: ${chat.memorySummary.ifBlank { "Отсутствует" }}
            Текущая локация: ${chat.rpgState.currentLocation()?.name ?: "Не указана"}
            Последние события:
            $transcript

            Верни СТРОГО один JSON-объект:
            {
              "memorySummary": "Сводка ключевых фактов, состояний NPC, предметов и договорённостей (5-8 предложений).",
              "chronicleTitle": "Короткий заголовок текущей вехи (3-6 слов)",
              "chronicleSummary": "1-2 предложения для летописи о том, что только что произошло.",
              "tags": ["локация", "событие"]
            }
        """.trimIndent()

        val activeProv = chat.providerEnum()
        val targetProv = if (activeProv == LlmProvider.GEMINI_AI_LOGIC ||
            activeProv == LlmProvider.POLLINATIONS ||
            (!activeProv.requiresApiKey || sanitizeKey(settings.configFor(activeProv).apiKey).isNotBlank())
        ) activeProv else LlmProvider.POLLINATIONS
        val targetModel = if (targetProv == activeProv) {
            chat.modelId.ifBlank { targetProv.suggestedModels.firstOrNull() ?: "openai" }
        } else {
            targetProv.suggestedModels.firstOrNull() ?: "openai"
        }

        val res = completeSinglePrompt(
            provider = targetProv,
            modelId = targetModel,
            systemPrompt = sys,
            userPrompt = usr,
            settings = settings,
            maxTokens = 700,
            temperature = 0.35,
            deviceContext = deviceContext
        )
        res.map { raw ->
            val stripped = raw.replace("```json", "", ignoreCase = true).replace("```", "").trim()
            val s = stripped.indexOf('{')
            val e = stripped.lastIndexOf('}')
            val obj = if (s >= 0 && e > s) {
                runCatching { JSONObject(stripped.substring(s, e + 1)) }.getOrNull()
            } else null

            if (obj != null) {
                val mem = obj.optString("memorySummary").trim().ifBlank { stripped }
                val cTitle = obj.optString("chronicleTitle").trim()
                val cSum = obj.optString("chronicleSummary").trim()
                val tagsArr = obj.optJSONArray("tags")
                val tags = if (tagsArr != null) {
                    (0 until tagsArr.length()).mapNotNull { tagsArr.optString(it).takeIf { t -> t.isNotBlank() } }
                } else emptyList()
                val entry = if (cTitle.isNotBlank() || cSum.isNotBlank()) {
                    ChronicleEntry(
                        id = "chr_${java.util.UUID.randomUUID().toString().take(8)}",
                        turnNumber = messages.size.toLong(),
                        title = cTitle.ifBlank { "Глава ${chat.rpgState.chronicle.size + 1}" },
                        summary = cSum.ifBlank { cTitle },
                        tags = tags.take(6)
                    )
                } else null
                mem to entry
            } else {
                stripped to null
            }
        }
    }

    private fun extractOpenAiNonStreamingText(json: JSONObject): String {
        val messageObj = json.optJSONArray("choices")
            ?.optJSONObject(0)
            ?.optJSONObject("message")
        val content = messageObj?.optString("content")
            ?.takeIf { it.isNotBlank() && it != "null" }
            .orEmpty()
        val reasoning = messageObj?.let { obj ->
            listOf("reasoning", "reasoning_content")
                .firstNotNullOfOrNull { k -> obj.optString(k).takeIf { it.isNotBlank() && it != "null" } }
        }.orEmpty()
        return when {
            content.isNotBlank() && reasoning.isNotBlank() -> "<think>$reasoning</think>\n\n$content"
            content.isNotBlank() -> content
            reasoning.isNotBlank() -> reasoning
            else -> ""
        }
    }

    private fun missingKeyMessage(provider: LlmProvider): String {
        val hint = when (provider) {
            LlmProvider.GEMINI_DIRECT -> "Получить бесплатный ключ можно на aistudio.google.com/apikey."
            LlmProvider.OPENROUTER -> "Бесплатный ключ создаётся на openrouter.ai/keys."
            LlmProvider.GROQ -> "Бесплатный ключ создаётся на console.groq.com/keys."
            else -> "Укажите ключ во вкладке «Модели»."
        }
        return "Не указан API-ключ для ${provider.displayName}. $hint Либо переключитесь на Pollinations (работает сразу без ключа)."
    }

    private fun mapConnectionError(provider: LlmProvider, baseUrl: String, e: ConnectException): IllegalStateException {
        val clean = baseUrl.trim()
        return if (provider == LlmProvider.LOCAL_LLM || clean.contains("127.0.0.1") || clean.contains("localhost")) {
            IllegalStateException(
                "Локальный сервер GGUF ($clean) не запущен. Запустите llama-server / PocketPal / Termux или переключитесь на Pollinations (без ключа)."
            )
        } else {
            IllegalStateException("Не удалось подключиться к ${provider.displayName}: ${e.localizedMessage}")
        }
    }

    private fun mapHttpError(
        provider: LlmProvider,
        code: Int,
        rawBody: String,
        retryAfterHeader: String?,
        modelId: String
    ): Exception {
        val preview = rawBody.take(260)
        return when (code) {
            402 -> {
                val waitSec = retryAfterHeader?.toIntOrNull()
                    ?: Regex("Retry after (\\d+)", RegexOption.IGNORE_CASE).find(rawBody)?.groupValues?.getOrNull(1)?.toIntOrNull()
                    ?: 45
                recordCooldown(provider, waitSec)
                LlmQuotaOrRateLimitException(
                    message = "Pollinations: анонимный лимит (1 запрос / ${waitSec} сек). Подождите ~${waitSec} сек, повторите или добавьте бесплатный ключ с enter.pollinations.ai.",
                    retryAfterSeconds = waitSec
                )
            }
            429 -> {
                val waitSec = retryAfterHeader?.toIntOrNull() ?: 25
                recordCooldown(provider, waitSec)
                LlmQuotaOrRateLimitException(
                    message = "Лимит запросов HTTP 429 (${provider.displayName}). Подождите ~${waitSec} сек или нажмите «Бесплатный резерв».",
                    retryAfterSeconds = waitSec
                )
            }
            401 -> IllegalStateException(
                "HTTP 401 (${provider.displayName}): неверный или устаревший API-ключ. Проверьте ключ во вкладке «Модели»."
            )
            403 -> IllegalStateException(
                "HTTP 403 (${provider.displayName}): доступ отклонён (регион, ключ или политика провайдера). Попробуйте Pollinations (без ключа)."
            )
            404 -> IllegalStateException(
                "HTTP 404 (${provider.displayName}): модель «$modelId» или путь не найдены. Проверьте ID модели."
            )
            else -> IllegalStateException("HTTP $code (${provider.displayName}): $preview")
        }
    }

    companion object {
        internal fun sanitizeKey(raw: String): String =
            raw.trim().replace("\n", "").replace("\r", "")

        internal fun isPermittedEndpointUrl(url: String): Boolean {
            val trimmed = url.trim()
            if (trimmed.startsWith("https://", ignoreCase = true)) return true
            if (!trimmed.startsWith("http://", ignoreCase = true)) return false
            val host = runCatching { java.net.URI(trimmed).host.orEmpty().lowercase().trim('[', ']') }
                .getOrDefault("")
            if (host.isBlank()) return false
            if (host == "localhost" || host == "127.0.0.1" || host == "0.0.0.0" || host == "::1" || host == "10.0.2.2") {
                return true
            }
            if (host.endsWith(".local") || host.endsWith(".lan")) return true
            if (host.startsWith("127.") || host.startsWith("10.") || host.startsWith("192.168.")) {
                return true
            }
            val match172 = Regex("^172\\.(\\d+)\\.").find(host)
            val secondOctet = match172?.groupValues?.getOrNull(1)?.toIntOrNull()
            return secondOctet != null && secondOctet in 16..31
        }

        internal fun resolveChatCompletionsEndpoint(provider: LlmProvider, baseUrl: String): String {
            val normalizedBase = baseUrl.trim().ifBlank { provider.defaultBaseUrl }.trimEnd('/')
            return if (normalizedBase.endsWith("/chat/completions")) {
                normalizedBase
            } else {
                "$normalizedBase/chat/completions"
            }
        }

        internal fun resolveModelsEndpoint(provider: LlmProvider, baseUrl: String): String {
            val cleanBase = baseUrl.trim().ifBlank { provider.defaultBaseUrl }.trimEnd('/')
            if (provider == LlmProvider.POLLINATIONS && cleanBase.contains("text.pollinations.ai", ignoreCase = true)) {
                return "https://text.pollinations.ai/models"
            }
            if (cleanBase.endsWith("/chat/completions")) {
                return cleanBase.removeSuffix("/chat/completions") + "/models"
            }
            return if (cleanBase.endsWith("/models")) cleanBase else "$cleanBase/models"
        }

        internal fun parseModelsResponse(bodyStr: String): List<String> {
            val trimmed = bodyStr.trim()
            if (trimmed.isEmpty()) return emptyList()
            val out = mutableListOf<String>()
            if (trimmed.startsWith("[")) {
                val arr = JSONArray(trimmed)
                for (i in 0 until arr.length()) {
                    when (val item = arr.opt(i)) {
                        is JSONObject -> {
                            val id = item.optString("id").ifBlank { item.optString("name") }.trim()
                            if (id.isNotEmpty()) out.add(id)
                        }
                        is String -> {
                            if (item.isNotBlank()) out.add(item.trim())
                        }
                    }
                }
            } else {
                val root = JSONObject(trimmed)
                val arr = root.optJSONArray("data") ?: root.optJSONArray("models") ?: JSONArray()
                for (i in 0 until arr.length()) {
                    val obj = arr.optJSONObject(i)
                    val id = obj?.optString("id")?.takeIf { it.isNotBlank() }
                        ?: obj?.optString("name")?.takeIf { it.isNotBlank() }
                    if (!id.isNullOrBlank()) out.add(id.trim())
                }
            }
            return out.distinct()
        }

        internal fun mapGeminiAiLogicError(e: Throwable): Exception {
            val combined = generateSequence(e) { it.cause }
                .mapNotNull { it.localizedMessage ?: it.message }
                .joinToString(" | ")
            val lower = combined.lowercase()
            return when {
                lower.contains("403") ||
                    lower.contains("app check") ||
                    lower.contains("appcheck") ||
                    lower.contains("attestation") -> {
                    IllegalStateException(
                        "Firebase App Check отклонил запрос (HTTP 403): APK из GitHub без зарегистрированного Debug-токена. " +
                            "Нажмите «Бесплатный резерв» для перехода на Pollinations (без ключа) или добавьте Debug Token из вкладки «Модели» в Firebase Console."
                    )
                }
                lower.contains("429") ||
                    lower.contains("quota") ||
                    lower.contains("resource_exhausted") -> {
                    LlmQuotaOrRateLimitException(
                        "Лимит бесплатных запросов Gemini исчерпан. Переключитесь на Pollinations или другую бесплатную модель."
                    )
                }
                else -> IllegalStateException(
                    combined.ifBlank { "Ошибка генерации Firebase Gemini." }
                )
            }
        }

        internal fun buildGeminiSafetySettings(level: SafetyLevel): List<SafetySetting> {
            val threshold = when (level) {
                SafetyLevel.STANDARD -> HarmBlockThreshold.MEDIUM_AND_ABOVE
                SafetyLevel.RELAXED -> HarmBlockThreshold.ONLY_HIGH
                SafetyLevel.MINIMAL -> HarmBlockThreshold.NONE
            }
            return listOf(
                SafetySetting(HarmCategory.HARASSMENT, threshold),
                SafetySetting(HarmCategory.HATE_SPEECH, threshold),
                SafetySetting(HarmCategory.SEXUALLY_EXPLICIT, threshold),
                SafetySetting(HarmCategory.DANGEROUS_CONTENT, threshold)
            )
        }

        internal fun parseExpandedScenarioDraft(
            rawText: String,
            fallbackTitle: String = "",
            fallbackSubtitle: String = "",
            fallbackRpMode: RpMode = RpMode.CHARACTER_1ON1,
            fallbackCharacter: String = "",
            fallbackSystemPrompt: String = "",
            fallbackFirstMessage: String = ""
        ): ExpandedScenarioDraft {
            val stripped = RpgAndThinkParser.stripThinkingForPrompt(rawText)
                .replace("```json", "", ignoreCase = true)
                .replace("```", "")
                .trim()
            val startIdx = stripped.indexOf('{')
            val endIdx = stripped.lastIndexOf('}')
            val jsonSlice = if (startIdx >= 0 && endIdx > startIdx) {
                stripped.substring(startIdx, endIdx + 1)
            } else {
                "{}"
            }
            val obj = runCatching { JSONObject(jsonSlice) }.getOrElse { JSONObject() }

            fun optNonBlank(key: String, fallback: String): String =
                obj.optString(key).takeIf { it.isNotBlank() && it != "null" }?.trim() ?: fallback

            fun optStringList(key: String): List<String> {
                val arr = obj.optJSONArray(key) ?: return emptyList()
                return (0 until arr.length())
                    .mapNotNull { arr.optString(it)?.takeIf { s -> s.isNotBlank() && s != "null" }?.trim() }
            }

            val loreEntries = mutableListOf<LoreEntry>()
            val leArr = obj.optJSONArray("loreEntries")
            if (leArr != null) {
                for (i in 0 until leArr.length()) {
                    val leObj = leArr.optJSONObject(i) ?: continue
                    val title = leObj.optString("title").trim()
                    val content = leObj.optString("content").trim()
                    if (content.isBlank()) continue
                    val keysArr = leObj.optJSONArray("keys")
                    val keys = if (keysArr != null) {
                        (0 until keysArr.length()).mapNotNull {
                            keysArr.optString(it)?.takeIf { k -> k.isNotBlank() }?.trim()
                        }
                    } else emptyList()
                    loreEntries += LoreEntry(
                        id = "ai_lore_${i + 1}",
                        title = title.ifBlank { "Факт ${i + 1}" },
                        keys = keys.ifEmpty { listOf(title.lowercase()) },
                        content = content,
                        alwaysActive = leObj.optBoolean("alwaysActive", false),
                        priority = (15 - i).toLong().coerceAtLeast(1L),
                        enabled = true
                    )
                }
            }

            val locations = mutableListOf<WorldLocationNode>()
            val locArr = obj.optJSONArray("locations")
            if (locArr != null) {
                for (i in 0 until locArr.length()) {
                    val lObj = locArr.optJSONObject(i) ?: continue
                    val id = lObj.optString("id").ifBlank { "loc_${i + 1}" }.trim()
                    val name = lObj.optString("name").trim()
                    if (name.isBlank()) continue
                    val connArr = lObj.optJSONArray("connections")
                    val conns = if (connArr != null) {
                        (0 until connArr.length()).mapNotNull {
                            connArr.optString(it)?.takeIf { c -> c.isNotBlank() }?.trim()
                        }
                    } else emptyList()
                    locations += WorldLocationNode(
                        id = id,
                        name = name,
                        description = lObj.optString("description").trim(),
                        connections = conns,
                        visited = i == 0
                    )
                }
            }

            val statsObj = obj.optJSONObject("stats")
            val statsMap = if (statsObj != null && statsObj.length() > 0) {
                val m = LinkedHashMap<String, Long>()
                statsObj.keys().forEach { k ->
                    m[k] = statsObj.optLong(k, 12L).coerceIn(1L, 30L)
                }
                m
            } else {
                RpgState().stats
            }

            val rpgEnabled = obj.optBoolean("rpgEnabled", fallbackRpMode == RpMode.GAME_MASTER) ||
                locations.isNotEmpty()
            val rpgState = RpgState(
                enabled = rpgEnabled,
                stats = statsMap,
                inventory = optStringList("inventory"),
                activeQuest = obj.optString("activeQuest").takeIf { it != "null" }.orEmpty().trim(),
                currentLocationId = locations.firstOrNull()?.id.orEmpty(),
                locations = locations
            )

            val resolvedChar = optNonBlank("characterName", fallbackCharacter).ifBlank { "Мастер" }
            val resolvedTitle = optNonBlank("title", fallbackTitle).ifBlank { resolvedChar }

            return ExpandedScenarioDraft(
                title = resolvedTitle,
                subtitle = optNonBlank("subtitle", fallbackSubtitle),
                rpMode = fallbackRpMode,
                characterName = resolvedChar,
                systemPrompt = optNonBlank("systemPrompt", fallbackSystemPrompt)
                    .ifBlank { "Ты ведёшь живую ролевую сцену. Используй *действие* и «речь», помни детали и не пиши за {{user}}." },
                characterBio = optNonBlank("characterBio", ""),
                firstMessage = optNonBlank("firstMessage", fallbackFirstMessage),
                exampleDialogue = optNonBlank("exampleDialogue", ""),
                participants = optStringList("participants"),
                tags = optStringList("tags"),
                loreNotes = optStringList("loreNotes"),
                loreEntries = loreEntries,
                rpgState = rpgState
            )
        }
    }
}
