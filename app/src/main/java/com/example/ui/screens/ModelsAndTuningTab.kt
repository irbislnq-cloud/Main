package com.example.ui.screens

import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material.icons.outlined.Key
import androidx.compose.material.icons.outlined.Memory
import androidx.compose.material.icons.outlined.NetworkCheck
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.example.data.ai.DeviceRamSnapshot
import com.example.data.ai.ProviderTestResult
import com.example.data.firebase.readAppCheckDebugToken
import com.example.data.local.AppLocalSettings
import com.example.data.model.AgentPipelinePresets
import com.example.data.model.GenerationParams
import com.example.data.model.LlmProvider
import com.example.data.model.LocalModelProfile
import com.example.data.model.LocalRamPreset
import com.example.data.model.ModelCatalogItem
import com.example.data.model.ModelProbeStatus
import com.example.data.model.ProviderEndpointConfig
import com.example.data.model.SafetyLevel
import com.example.ui.components.GenerationTuningControls
import com.example.ui.components.ModelCatalogSheet

@Composable
fun ModelsAndTuningTab(
    settings: AppLocalSettings,
    onSetDefaultModel: (LlmProvider, String, Boolean) -> Unit,
    onSaveProviderConfig: (ProviderEndpointConfig) -> Unit,
    onSaveDefaultParams: (GenerationParams) -> Unit,
    onSetStreaming: (Boolean) -> Unit,
    onSetShowBadges: (Boolean) -> Unit,
    onSetFreeOnlyFilter: (Boolean) -> Unit,
    onSetSafetyLevel: (SafetyLevel) -> Unit = {},
    onSetCreativeRpFraming: (Boolean) -> Unit = {},
    deviceRamSnapshot: DeviceRamSnapshot? = null,
    onRefreshRam: () -> Unit = {},
    onSetLocalLlmEnabled: (Boolean) -> Unit = {},
    onSetDefaultAgentPreset: (String) -> Unit = {},
    onSetAutoSuggestActions: (Boolean) -> Unit = {},
    onSetAutoSendSuggestedAction: (Boolean) -> Unit = {},
    onSetAutoSummarizeEveryN: (Int) -> Unit = {},
    catalogModels: List<ModelCatalogItem> = emptyList(),
    isDiscoveringModels: Boolean = false,
    discoveryProgress: String = "",
    onDiscoverFreeModels: () -> Unit = {},
    onProbeModel: (ModelCatalogItem) -> Unit = {},
    onSaveLocalModelProfile: (LocalModelProfile) -> Unit,
    onSaveImageEndpoint: (String) -> Unit,
    onTestServer: (LlmProvider, String, String, (Result<List<String>>) -> Unit) -> Unit,
    onTestChatCompletion: (LlmProvider, String, String, String, (Result<ProviderTestResult>) -> Unit) -> Unit,
    onToast: (String) -> Unit = {}
) {
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current

    var initializedFromStore by remember { mutableStateOf(false) }
    var selectedProvider by remember { mutableStateOf(settings.defaultProvider) }
    var activeModelId by remember { mutableStateOf(settings.defaultModelId) }
    var baseUrl by remember {
        mutableStateOf(
            settings.configFor(selectedProvider).baseUrl.ifBlank { selectedProvider.defaultBaseUrl }
        )
    }
    var apiKey by remember { mutableStateOf(settings.configFor(selectedProvider).apiKey) }
    var showKey by remember { mutableStateOf(false) }
    var applyToExistingChats by remember { mutableStateOf(true) }
    var showModelCatalog by remember { mutableStateOf(false) }

    LaunchedEffect(settings.providerConfigs.isNotEmpty(), settings.defaultProvider, settings.defaultModelId) {
        if (!initializedFromStore && settings.providerConfigs.isNotEmpty()) {
            initializedFromStore = true
            selectedProvider = settings.defaultProvider
            activeModelId = settings.defaultModelId
            val cfg = settings.configFor(settings.defaultProvider)
            baseUrl = cfg.baseUrl.ifBlank { settings.defaultProvider.defaultBaseUrl }
            apiKey = cfg.apiKey
        }
    }

    var draftParams by remember(settings.defaultParams) {
        mutableStateOf(settings.defaultParams)
    }
    LaunchedEffect(settings.defaultParams) {
        draftParams = settings.defaultParams
    }

    // Server /models discovery state
    var isTestingServer by remember { mutableStateOf(false) }
    var serverTestStatus by remember { mutableStateOf<String?>(null) }
    var discoveredModels by remember { mutableStateOf<List<String>>(emptyList()) }

    // Live chat completion probe state
    var isTestingChat by remember { mutableStateOf(false) }
    var chatTestSuccess by remember { mutableStateOf<ProviderTestResult?>(null) }
    var chatTestError by remember { mutableStateOf<String?>(null) }

    val debugToken = remember(context, selectedProvider) {
        if (selectedProvider == LlmProvider.GEMINI_AI_LOGIC) {
            readAppCheckDebugToken(context)
        } else null
    }

    // Local GGUF profile state (Poco X6 Pro optimization)
    var localServerUrl by remember(settings.localModelProfile.serverUrl) {
        mutableStateOf(settings.localModelProfile.serverUrl)
    }
    var ggufUri by remember(settings.localModelProfile.ggufUri) {
        mutableStateOf(settings.localModelProfile.ggufUri)
    }
    var ggufDisplayName by remember(settings.localModelProfile.ggufDisplayName) {
        mutableStateOf(settings.localModelProfile.ggufDisplayName)
    }
    var ramPreset by remember(settings.localModelProfile.ramPreset) {
        mutableStateOf(settings.localModelProfile.ramPreset)
    }

    var imageEndpoint by remember(settings.imageEndpointTemplate) {
        mutableStateOf(settings.imageEndpointTemplate)
    }

    val pickGgufLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            runCatching {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            }
            val displayName = queryUriDisplayName(context, uri)
            ggufUri = uri.toString()
            ggufDisplayName = displayName
            onSaveLocalModelProfile(
                settings.localModelProfile.copy(
                    serverUrl = localServerUrl,
                    ggufUri = ggufUri,
                    ggufDisplayName = displayName,
                    ramPreset = ramPreset
                )
            )
        }
    }

    val visibleProviders = remember(settings.freeOnlyFilter, settings.localLlmEnabled, selectedProvider) {
        val base = if (settings.freeOnlyFilter) {
            (LlmProvider.freeProviders() + selectedProvider).distinct()
        } else {
            LlmProvider.entries
        }
        base.filter { settings.localLlmEnabled || it != LlmProvider.LOCAL_LLM || it == selectedProvider }
    }

    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
        modifier = Modifier.fillMaxSize()
    ) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("Бесплатные LLM и локальный движок", style = MaterialTheme.typography.headlineMedium)
                Text(
                    text = "Pollinations работает сразу без ключа и регистрации. Также доступны Gemini AI Studio, OpenRouter (:free), Groq и локальные GGUF.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        // Provider & Model card
        item {
            Surface(
                shape = MaterialTheme.shapes.large,
                color = MaterialTheme.colorScheme.surfaceContainer,
                tonalElevation = 2.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Только бесплатные источники", style = MaterialTheme.typography.titleSmall)
                            Text(
                                "Скрывает платные API без free-tier",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = settings.freeOnlyFilter,
                            onCheckedChange = onSetFreeOnlyFilter,
                            modifier = Modifier.testTag("free_only_filter_switch")
                        )
                    }

                    val onlineCount = remember(catalogModels) {
                        catalogModels.count { it.status == ModelProbeStatus.ONLINE }
                    }
                    Button(
                        onClick = { showModelCatalog = true },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("open_model_catalog_button")
                    ) {
                        Icon(Icons.Outlined.AutoAwesome, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            if (onlineCount > 0) {
                                "Каталог и автопоиск (${catalogModels.size} • онлайн $onlineCount)"
                            } else {
                                "Каталог и автопоиск бесплатных моделей (${catalogModels.size})"
                            }
                        )
                    }

                    HorizontalDivider()

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Провайдер и модель", style = MaterialTheme.typography.titleMedium)
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = if (selectedProvider.freeFriendly) {
                                MaterialTheme.colorScheme.tertiaryContainer
                            } else {
                                MaterialTheme.colorScheme.surfaceContainerHighest
                            }
                        ) {
                            Text(
                                text = selectedProvider.freeBadgeRu,
                                style = MaterialTheme.typography.labelSmall,
                                color = if (selectedProvider.freeFriendly) {
                                    MaterialTheme.colorScheme.onTertiaryContainer
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                },
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                            )
                        }
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        visibleProviders.forEach { prov ->
                            FilterChip(
                                selected = selectedProvider == prov,
                                onClick = {
                                    selectedProvider = prov
                                    val cfg = settings.configFor(prov)
                                    activeModelId = if (prov == settings.defaultProvider && settings.defaultModelId.isNotBlank()) {
                                        settings.defaultModelId
                                    } else {
                                        cfg.customModelId.ifBlank {
                                            prov.suggestedModels.firstOrNull().orEmpty()
                                        }
                                    }
                                    baseUrl = cfg.baseUrl.ifBlank { prov.defaultBaseUrl }
                                    apiKey = cfg.apiKey
                                    serverTestStatus = null
                                    discoveredModels = emptyList()
                                    chatTestSuccess = null
                                    chatTestError = null
                                },
                                label = { Text(prov.displayName) },
                                modifier = Modifier.testTag("global_provider_${prov.name}")
                            )
                        }
                    }

                    OutlinedTextField(
                        value = activeModelId,
                        onValueChange = { activeModelId = it },
                        label = { Text("ID модели") },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("global_model_input")
                    )

                    Row(
                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        selectedProvider.suggestedModels.forEach { suggested ->
                            AssistChip(
                                onClick = { activeModelId = suggested },
                                label = { Text(suggested) }
                            )
                        }
                    }

                    // Contextual Russian hints per provider
                    when (selectedProvider) {
                        LlmProvider.POLLINATIONS -> {
                            Surface(
                                shape = MaterialTheme.shapes.medium,
                                color = MaterialTheme.colorScheme.tertiaryContainer,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    text = "Работает сразу без ключа и регистрации (1 бесплатный ответ раз в 45 сек анонимно). Если указать опциональный бесплатный ключ с enter.pollinations.ai — ограничение снимается.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onTertiaryContainer,
                                    modifier = Modifier.padding(12.dp)
                                )
                            }
                        }

                        LlmProvider.GEMINI_AI_LOGIC -> {
                            Surface(
                                shape = MaterialTheme.shapes.medium,
                                color = MaterialTheme.colorScheme.secondaryContainer,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(
                                    modifier = Modifier.padding(12.dp),
                                    verticalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Text(
                                        text = "Firebase AI Logic защищён App Check: в Debug APK из GitHub возможна ошибка HTTP 403 без регистрации токена устройства в Firebase Console. Для мгновенного старта без настроек выберите Pollinations.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSecondaryContainer
                                    )
                                    if (debugToken != null) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                                        ) {
                                            SelectionContainer(modifier = Modifier.weight(1f)) {
                                                Text(
                                                    text = "App Check Debug Token:\n$debugToken",
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = MaterialTheme.colorScheme.onSecondaryContainer
                                                )
                                            }
                                            IconButton(onClick = {
                                                clipboard.setText(AnnotatedString(debugToken))
                                                onToast("Debug Token скопирован")
                                            }) {
                                                Icon(
                                                    Icons.Outlined.ContentCopy,
                                                    contentDescription = "Копировать Debug Token"
                                                )
                                            }
                                        }
                                    }
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        OutlinedButton(
                                            onClick = {
                                                val p = LlmProvider.POLLINATIONS
                                                val cfg = settings.configFor(p)
                                                selectedProvider = p
                                                activeModelId = cfg.customModelId.ifBlank { p.suggestedModels.firstOrNull() ?: "openai" }
                                                baseUrl = cfg.baseUrl.ifBlank { p.defaultBaseUrl }
                                                apiKey = cfg.apiKey
                                                chatTestSuccess = null
                                                chatTestError = null
                                                onSetDefaultModel(p, activeModelId, applyToExistingChats)
                                            },
                                            modifier = Modifier
                                                .weight(1f)
                                                .testTag("gemini_quick_switch_pollinations")
                                        ) {
                                            Text("Pollinations (0 ₽)", maxLines = 1)
                                        }
                                        OutlinedButton(
                                            onClick = {
                                                val p = LlmProvider.GEMINI_DIRECT
                                                val cfg = settings.configFor(p)
                                                selectedProvider = p
                                                activeModelId = cfg.customModelId.ifBlank { p.suggestedModels.firstOrNull() ?: "gemini-2.5-flash" }
                                                baseUrl = cfg.baseUrl.ifBlank { p.defaultBaseUrl }
                                                apiKey = cfg.apiKey
                                                chatTestSuccess = null
                                                chatTestError = null
                                            },
                                            modifier = Modifier
                                                .weight(1f)
                                                .testTag("gemini_quick_switch_direct")
                                        ) {
                                            Text("AI Studio (ключ)", maxLines = 1)
                                        }
                                    }
                                }
                            }
                        }

                        LlmProvider.GEMINI_DIRECT -> {
                            Surface(
                                shape = MaterialTheme.shapes.medium,
                                color = MaterialTheme.colorScheme.surfaceContainerLow,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    text = "Бесплатный Free Tier Google AI Studio (без карты): ключ создаётся на aistudio.google.com/apikey.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(12.dp)
                                )
                            }
                        }

                        LlmProvider.OPENROUTER -> {
                            Surface(
                                shape = MaterialTheme.shapes.medium,
                                color = MaterialTheme.colorScheme.surfaceContainerLow,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    text = "Модели с :free и роутер openrouter/free бесплатны. Ключ создаётся на openrouter.ai/keys.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(12.dp)
                                )
                            }
                        }

                        LlmProvider.GROQ -> {
                            Surface(
                                shape = MaterialTheme.shapes.medium,
                                color = MaterialTheme.colorScheme.surfaceContainerLow,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    text = "Быстрый облачный Free Tier для Llama 3.3 70B и Qwen 3. Ключ создаётся на console.groq.com/keys.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(12.dp)
                                )
                            }
                        }

                        else -> Unit
                    }

                    if (selectedProvider != LlmProvider.GEMINI_AI_LOGIC) {
                        OutlinedTextField(
                            value = baseUrl,
                            onValueChange = { baseUrl = it },
                            label = { Text("Base URL API") },
                            placeholder = { Text(selectedProvider.defaultBaseUrl) },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        OutlinedTextField(
                            value = apiKey,
                            onValueChange = { apiKey = it },
                            label = {
                                Text(
                                    when {
                                        selectedProvider == LlmProvider.POLLINATIONS ->
                                            "API-ключ (не обязателен; с enter.pollinations.ai убирает паузу)"
                                        selectedProvider.requiresApiKey ->
                                            "API-ключ (хранится только на устройстве)"
                                        else ->
                                            "API-ключ (для локального сервера обычно пустой)"
                                    }
                                )
                            },
                            leadingIcon = { Icon(Icons.Outlined.Key, contentDescription = null) },
                            trailingIcon = {
                                IconButton(onClick = { showKey = !showKey }) {
                                    Icon(
                                        imageVector = if (showKey) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility,
                                        contentDescription = "Показать или скрыть ключ"
                                    )
                                }
                            },
                            visualTransformation = if (showKey) VisualTransformation.None else PasswordVisualTransformation(),
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }

                    // Verification row: /models discovery + live chat completion test
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        if (selectedProvider != LlmProvider.GEMINI_AI_LOGIC &&
                            selectedProvider != LlmProvider.ANTHROPIC &&
                            selectedProvider != LlmProvider.GEMINI_DIRECT
                        ) {
                            OutlinedButton(
                                onClick = {
                                    isTestingServer = true
                                    serverTestStatus = null
                                    onTestServer(selectedProvider, baseUrl, apiKey) { res ->
                                        isTestingServer = false
                                        res.onSuccess { list ->
                                            discoveredModels = list
                                            serverTestStatus = if (list.isEmpty()) {
                                                "Сервер ответил, но список /models пуст."
                                            } else {
                                                "Доступно моделей: ${list.size}. Нажмите на модель ниже для выбора."
                                            }
                                            if (list.isNotEmpty() && activeModelId.isBlank()) {
                                                activeModelId = list.first()
                                            }
                                        }.onFailure { err ->
                                            discoveredModels = emptyList()
                                            serverTestStatus = "Ошибка /models: ${err.localizedMessage ?: "нет ответа"}"
                                        }
                                    }
                                },
                                enabled = !isTestingServer && !isTestingChat,
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("test_server_models_button")
                            ) {
                                if (isTestingServer) {
                                    CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                                } else {
                                    Icon(Icons.Outlined.NetworkCheck, contentDescription = null, modifier = Modifier.size(18.dp))
                                }
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Модели (/models)", maxLines = 1)
                            }
                        }

                        OutlinedButton(
                            onClick = {
                                isTestingChat = true
                                chatTestSuccess = null
                                chatTestError = null
                                onTestChatCompletion(
                                    selectedProvider,
                                    baseUrl,
                                    apiKey,
                                    activeModelId
                                ) { res ->
                                    isTestingChat = false
                                    res.onSuccess { ok ->
                                        chatTestSuccess = ok
                                    }.onFailure { err ->
                                        chatTestError = err.localizedMessage ?: "Не удалось получить ответ."
                                    }
                                }
                            },
                            enabled = !isTestingChat && !isTestingServer,
                            modifier = Modifier
                                .weight(1f)
                                .testTag("test_chat_api_button")
                        ) {
                            if (isTestingChat) {
                                CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                            } else {
                                Icon(Icons.Outlined.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                            }
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Проверить чат", maxLines = 1)
                        }
                    }

                    if (chatTestSuccess != null) {
                        val ok = chatTestSuccess!!
                        Surface(
                            shape = MaterialTheme.shapes.medium,
                            color = MaterialTheme.colorScheme.tertiaryContainer,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = "Связь работает за ${ok.latencyMs} мс (${ok.modelUsed}): «${ok.replyPreview}»",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onTertiaryContainer,
                                modifier = Modifier.padding(10.dp)
                            )
                        }
                    }
                    if (chatTestError != null) {
                        Surface(
                            shape = MaterialTheme.shapes.medium,
                            color = MaterialTheme.colorScheme.errorContainer,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = chatTestError!!,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onErrorContainer,
                                modifier = Modifier.padding(10.dp)
                            )
                        }
                    }

                    if (serverTestStatus != null) {
                        Text(
                            text = serverTestStatus!!,
                            style = MaterialTheme.typography.bodySmall,
                            color = if (discoveredModels.isNotEmpty()) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.error
                            }
                        )
                    }
                    if (discoveredModels.isNotEmpty()) {
                        Row(
                            modifier = Modifier.horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            discoveredModels.forEach { m ->
                                AssistChip(
                                    onClick = { activeModelId = m },
                                    label = { Text(m) }
                                )
                            }
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Применить ко всем чатам", style = MaterialTheme.typography.titleSmall)
                            Text(
                                "Обновляет провайдер и модель в уже созданных чатах",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = applyToExistingChats,
                            onCheckedChange = { applyToExistingChats = it },
                            modifier = Modifier.testTag("apply_to_all_chats_switch")
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        if (selectedProvider != LlmProvider.GEMINI_AI_LOGIC) {
                            OutlinedButton(
                                onClick = {
                                    onSaveProviderConfig(
                                        ProviderEndpointConfig(
                                            provider = selectedProvider,
                                            baseUrl = baseUrl,
                                            apiKey = apiKey,
                                            customModelId = activeModelId
                                        )
                                    )
                                },
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("Сохранить API")
                            }
                        }
                        Button(
                            onClick = {
                                if (selectedProvider != LlmProvider.GEMINI_AI_LOGIC) {
                                    onSaveProviderConfig(
                                        ProviderEndpointConfig(
                                            provider = selectedProvider,
                                            baseUrl = baseUrl,
                                            apiKey = apiKey,
                                            customModelId = activeModelId
                                        )
                                    )
                                }
                                onSetDefaultModel(selectedProvider, activeModelId, applyToExistingChats)
                            },
                            modifier = Modifier
                                .weight(1f)
                                .testTag("apply_default_model_button")
                        ) {
                            Icon(Icons.Outlined.Check, contentDescription = null)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Выбрать модель")
                        }
                    }
                }
            }
        }

        // Local GGUF & Poco X6 Pro Offline Server Profile (Opt-In + RAM Watchdog)
        item {
            Surface(
                shape = MaterialTheme.shapes.large,
                color = MaterialTheme.colorScheme.surfaceContainer,
                tonalElevation = 1.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Outlined.Memory, contentDescription = null, tint = MaterialTheme.colorScheme.secondary)
                            Column {
                                Text("Локальный движок (GGUF / 127.0.0.1)", style = MaterialTheme.typography.titleMedium)
                                Text(
                                    text = if (settings.localLlmEnabled) {
                                        "Включён с защитой от вылетов по ОЗУ"
                                    } else {
                                        "Выключен по умолчанию — работают бесплатные облачные LLM"
                                    },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        Switch(
                            checked = settings.localLlmEnabled,
                            onCheckedChange = {
                                onSetLocalLlmEnabled(it)
                                onRefreshRam()
                            },
                            modifier = Modifier.testTag("local_llm_opt_in_switch")
                        )
                    }

                    if (deviceRamSnapshot != null) {
                        Surface(
                            shape = MaterialTheme.shapes.medium,
                            color = if (deviceRamSnapshot.isLowMemory) {
                                MaterialTheme.colorScheme.errorContainer
                            } else {
                                MaterialTheme.colorScheme.surfaceContainerLow
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier.padding(10.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "ОЗУ свободно: ${deviceRamSnapshot.availableMb} / ${deviceRamSnapshot.totalMb} МБ • Лимит ctx: ${deviceRamSnapshot.safeLocalContextBudget}",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = if (deviceRamSnapshot.isLowMemory) {
                                            MaterialTheme.colorScheme.onErrorContainer
                                        } else {
                                            MaterialTheme.colorScheme.onSurface
                                        },
                                        modifier = Modifier.weight(1f)
                                    )
                                    IconButton(
                                        onClick = onRefreshRam,
                                        modifier = Modifier.size(32.dp)
                                    ) {
                                        Icon(
                                            Icons.Outlined.Refresh,
                                            contentDescription = "Обновить ОЗУ",
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                }
                                if (deviceRamSnapshot.warningText != null) {
                                    Text(
                                        text = deviceRamSnapshot.warningText,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onErrorContainer
                                    )
                                }
                            }
                        }
                    }

                    Text(
                        text = "Для оффлайн-сервера на телефоне (Termux llama.cpp, PocketPal, Maid, Ollama). При нехватке ОЗУ контекст сжимается автоматически.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Text("Пресет ОЗУ и потоков:", style = MaterialTheme.typography.labelMedium)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        LocalRamPreset.entries.forEach { preset ->
                            FilterChip(
                                selected = ramPreset == preset,
                                onClick = { ramPreset = preset },
                                label = { Text(preset.labelRu) },
                                modifier = Modifier.testTag("ram_preset_${preset.name}")
                            )
                        }
                    }
                    Text(
                        text = "Рекомендация: ${ramPreset.recommendedQuant} • ctx ${ramPreset.contextSize} • ${ramPreset.threads} потоков",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary
                    )

                    OutlinedTextField(
                        value = localServerUrl,
                        onValueChange = { localServerUrl = it },
                        label = { Text("Локальный сервер OpenAI-URL") },
                        placeholder = { Text("http://127.0.0.1:8080/v1") },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("local_server_url_input")
                    )

                    Row(
                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        AssistChip(
                            onClick = { localServerUrl = "http://127.0.0.1:8080/v1" },
                            label = { Text("llama.cpp :8080") }
                        )
                        AssistChip(
                            onClick = { localServerUrl = "http://127.0.0.1:11434/v1" },
                            label = { Text("Ollama :11434") }
                        )
                        AssistChip(
                            onClick = { localServerUrl = "http://127.0.0.1:5001/v1" },
                            label = { Text("KoboldCpp :5001") }
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedButton(
                            onClick = { pickGgufLauncher.launch(arrayOf("*/*")) },
                            modifier = Modifier
                                .weight(1f)
                                .testTag("pick_gguf_file_button")
                        ) {
                            Icon(Icons.Outlined.FolderOpen, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = if (ggufDisplayName.isNotBlank()) "GGUF: $ggufDisplayName" else "Выбрать файл .gguf",
                                maxLines = 1
                            )
                        }
                        if (ggufUri.isNotBlank()) {
                            IconButton(onClick = {
                                ggufUri = ""
                                ggufDisplayName = ""
                            }) {
                                Icon(Icons.Outlined.Close, contentDescription = "Очистить GGUF")
                            }
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = {
                                onSaveLocalModelProfile(
                                    settings.localModelProfile.copy(
                                        serverUrl = localServerUrl,
                                        ggufUri = ggufUri,
                                        ggufDisplayName = ggufDisplayName,
                                        ramPreset = ramPreset
                                    )
                                )
                            },
                            modifier = Modifier
                                .weight(1f)
                                .testTag("save_local_profile_button")
                        ) {
                            Text("Сохранить профиль")
                        }
                        Button(
                            onClick = {
                                onSetLocalLlmEnabled(true)
                                onSaveLocalModelProfile(
                                    settings.localModelProfile.copy(
                                        serverUrl = localServerUrl,
                                        ggufUri = ggufUri,
                                        ggufDisplayName = ggufDisplayName,
                                        ramPreset = ramPreset
                                    )
                                )
                                selectedProvider = LlmProvider.LOCAL_LLM
                                baseUrl = localServerUrl
                                activeModelId = ggufDisplayName.ifBlank { "local-gguf-q4" }
                                onSetDefaultModel(
                                    LlmProvider.LOCAL_LLM,
                                    activeModelId,
                                    applyToExistingChats
                                )
                            },
                            modifier = Modifier
                                .weight(1f)
                                .testTag("activate_local_llm_button")
                        ) {
                            Text("Включить локальную")
                        }
                    }
                }
            }
        }

        // Multi-Agent Presets, Quick Replies & Auto-Summarize Card
        item {
            Surface(
                shape = MaterialTheme.shapes.large,
                color = MaterialTheme.colorScheme.surfaceContainer,
                tonalElevation = 1.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text("Агенты, хроника и подсказки хода", style = MaterialTheme.typography.titleMedium)

                    Text("Отряд агентов по умолчанию для новых чатов:", style = MaterialTheme.typography.labelMedium)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        AgentPipelinePresets.presets.forEach { preset ->
                            FilterChip(
                                selected = settings.defaultAgentPresetId == preset.id,
                                onClick = { onSetDefaultAgentPreset(preset.id) },
                                label = { Text(preset.titleRu) },
                                modifier = Modifier.testTag("default_preset_${preset.id}")
                            )
                        }
                    }

                    HorizontalDivider()

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("3 быстрых варианта хода от AI", style = MaterialTheme.typography.titleSmall)
                            Text(
                                "Показывает 3 кнопки идей ответа над строкой ввода",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = settings.autoSuggestActions,
                            onCheckedChange = onSetAutoSuggestActions,
                            modifier = Modifier.testTag("auto_suggest_switch")
                        )
                    }

                    if (settings.autoSuggestActions) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Отправлять вариант сразу в 1 тап", style = MaterialTheme.typography.titleSmall)
                                Text(
                                    "Если выключено — вариант подставляется в поле ввода для правки",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Switch(
                                checked = settings.autoSendSuggestedAction,
                                onCheckedChange = onSetAutoSendSuggestedAction
                            )
                        }
                    }

                    HorizontalDivider()

                    Text("Авто-сжатие хроники и памяти:", style = MaterialTheme.typography.labelMedium)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        listOf(0 to "Выкл", 8 to "Каждые 8", 12 to "Каждые 12", 16 to "Каждые 16", 24 to "Каждые 24").forEach { (every, label) ->
                            FilterChip(
                                selected = settings.autoSummarizeEveryN == every,
                                onClick = { onSetAutoSummarizeEveryN(every) },
                                label = { Text(label) }
                            )
                        }
                    }
                }
            }
        }

        // Scene Art Endpoint Card
        item {
            Surface(
                shape = MaterialTheme.shapes.large,
                color = MaterialTheme.colorScheme.surfaceContainer,
                tonalElevation = 1.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(Icons.Outlined.AutoAwesome, contentDescription = null, tint = MaterialTheme.colorScheme.tertiary)
                        Text("Генерация портретов и фонов", style = MaterialTheme.typography.titleMedium)
                    }
                    Text(
                        text = "Шаблон URL с {prompt} для бесплатной генерации артов сцен и персонажей (или локальный Stable Diffusion / ComfyUI).",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    OutlinedTextField(
                        value = imageEndpoint,
                        onValueChange = { imageEndpoint = it },
                        label = { Text("Endpoint шаблон изображений") },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("image_endpoint_input")
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        TextButton(onClick = {
                            imageEndpoint = "https://image.pollinations.ai/prompt/{prompt}?nologo=true"
                        }) {
                            Text("Сброс (Pollinations)")
                        }
                        OutlinedButton(
                            onClick = { onSaveImageEndpoint(imageEndpoint) },
                            modifier = Modifier.testTag("save_image_endpoint_button")
                        ) {
                            Text("Сохранить endpoint")
                        }
                    }
                }
            }
        }

        // Streaming & Badges switches
        item {
            Surface(
                shape = MaterialTheme.shapes.large,
                color = MaterialTheme.colorScheme.surfaceContainer,
                tonalElevation = 1.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Потоковая генерация (Streaming)", style = MaterialTheme.typography.titleSmall)
                            Text(
                                "Текст появляется по мере печати нейросети",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = settings.streamingEnabled,
                            onCheckedChange = onSetStreaming
                        )
                    }
                    HorizontalDivider(modifier = Modifier.padding(vertical = 10.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Показывать модель в сообщениях", style = MaterialTheme.typography.titleSmall)
                            Text(
                                "Отображает название LLM на ответах персонажа",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = settings.showModelBadges,
                            onCheckedChange = onSetShowBadges
                        )
                    }
                }
            }
        }

        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Параметры генерации (по умолчанию)", style = MaterialTheme.typography.titleMedium)
                Button(
                    onClick = { onSaveDefaultParams(draftParams) },
                    modifier = Modifier.testTag("save_global_params_button")
                ) {
                    Text("Сохранить")
                }
            }
        }

        item {
            GenerationTuningControls(
                params = draftParams,
                onParamsChange = { draftParams = it },
                safetyLevel = settings.safetyLevel,
                onSafetyLevelChange = onSetSafetyLevel,
                creativeRpFraming = settings.creativeRpFraming,
                onCreativeRpFramingChange = onSetCreativeRpFraming
            )
        }

        item { Spacer(modifier = Modifier.height(72.dp)) }
    }

    if (showModelCatalog) {
        ModelCatalogSheet(
            models = catalogModels,
            currentProvider = selectedProvider,
            currentModelId = activeModelId,
            isDiscovering = isDiscoveringModels,
            discoveryProgress = discoveryProgress,
            onDiscoverFreeModels = onDiscoverFreeModels,
            onProbeModel = onProbeModel,
            onSelectModel = { pickedProvider, pickedModelId ->
                selectedProvider = pickedProvider
                activeModelId = pickedModelId
                val cfg = settings.configFor(pickedProvider)
                baseUrl = cfg.baseUrl.ifBlank { pickedProvider.defaultBaseUrl }
                apiKey = cfg.apiKey
                onSetDefaultModel(pickedProvider, pickedModelId, applyToExistingChats)
            },
            onDismiss = { showModelCatalog = false }
        )
    }
}

private fun queryUriDisplayName(context: android.content.Context, uri: Uri): String {
    runCatching {
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
            ?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (idx >= 0) {
                        val name = cursor.getString(idx)
                        if (!name.isNullOrBlank()) return name
                    }
                }
            }
    }
    return uri.lastPathSegment?.substringAfterLast('/')?.takeIf { it.isNotBlank() } ?: "model.gguf"
}
