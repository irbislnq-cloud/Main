package com.example.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.PrimaryScrollableTabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.R
import com.example.data.local.SceneArtManager
import com.example.data.model.AgentConfig
import com.example.data.model.AgentSetupCodec
import com.example.data.model.GenerationParams
import com.example.data.model.LlmProvider
import com.example.data.model.LoreEntry
import com.example.data.model.ModelCatalogItem
import com.example.data.model.ProviderEndpointConfig
import com.example.data.model.RpChat
import com.example.data.model.RpMode
import com.example.data.model.SafetyLevel
import com.example.ui.components.AgentPipelineBuilder
import com.example.ui.components.GenerationTuningControls
import com.example.ui.components.LorebookEditorSection
import com.example.ui.components.ModelCatalogSheet
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatConfigSheet(
    chat: RpChat,
    isCompressingMemory: Boolean,
    imageEndpointTemplate: String = "https://image.pollinations.ai/prompt/{prompt}?nologo=true",
    freeOnlyFilter: Boolean = true,
    providerConfigs: Map<LlmProvider, ProviderEndpointConfig> = emptyMap(),
    safetyLevel: SafetyLevel = SafetyLevel.RELAXED,
    creativeRpFraming: Boolean = true,
    onSetSafetyLevel: (SafetyLevel) -> Unit = {},
    onSetCreativeRpFraming: (Boolean) -> Unit = {},
    catalogModels: List<ModelCatalogItem> = emptyList(),
    isDiscoveringModels: Boolean = false,
    discoveryProgress: String = "",
    onDiscoverFreeModels: () -> Unit = {},
    onProbeModel: (ModelCatalogItem) -> Unit = {},
    customAgentsLibrary: List<AgentConfig> = emptyList(),
    onSaveCustomAgent: (AgentConfig) -> Unit = {},
    onDeleteCustomAgent: (String) -> Unit = {},
    localLlmEnabled: Boolean = false,
    onNotify: (String) -> Unit = {},
    onDismiss: () -> Unit,
    onCompressMemory: () -> Unit,
    onSave: (
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
        portraitUri: String,
        backdropUri: String,
        agentSetupJson: String
    ) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var selectedTab by remember { mutableIntStateOf(0) }
    var showModelCatalog by remember { mutableStateOf(false) }

    var title by remember(chat.id) { mutableStateOf(chat.title) }
    var rpMode by remember(chat.id) { mutableStateOf(chat.rpModeEnum()) }
    var systemPrompt by remember(chat.id) { mutableStateOf(chat.systemPrompt) }
    var characterName by remember(chat.id) { mutableStateOf(chat.characterName) }
    var userPersonaName by remember(chat.id) { mutableStateOf(chat.userPersonaName) }
    var userPersonaDesc by remember(chat.id) { mutableStateOf(chat.userPersonaDesc) }
    var authorsNote by remember(chat.id) { mutableStateOf(chat.authorsNote) }
    var memorySummary by remember(chat.id, chat.memorySummary) { mutableStateOf(chat.memorySummary) }
    var participantsRaw by remember(chat.id) { mutableStateOf(chat.participants.joinToString(", ")) }
    val loreNotes = remember(chat.id) {
        mutableStateListOf<String>().apply { addAll(chat.loreNotes) }
    }
    var loreEntries by remember(chat.id) {
        mutableStateOf(chat.loreEntries)
    }
    var newLore by remember { mutableStateOf("") }

    var portraitUri by remember(chat.id) { mutableStateOf(chat.portraitUri) }
    var backdropUri by remember(chat.id) { mutableStateOf(chat.backdropUri) }
    var isGenPortrait by remember { mutableStateOf(false) }
    var isGenBackdrop by remember { mutableStateOf(false) }

    val pickPortraitLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            scope.launch {
                SceneArtManager.importImageFromUri(context, uri, "portrait")
                    .onSuccess {
                        portraitUri = it
                        onNotify("Портрет выбран")
                    }
                    .onFailure { onNotify(it.localizedMessage ?: "Ошибка чтения фото") }
            }
        }
    }

    val pickBackdropLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            scope.launch {
                SceneArtManager.importImageFromUri(context, uri, "backdrop")
                    .onSuccess {
                        backdropUri = it
                        onNotify("Фон сцены выбран")
                    }
                    .onFailure { onNotify(it.localizedMessage ?: "Ошибка чтения фото") }
            }
        }
    }

    var provider by remember(chat.id) { mutableStateOf(chat.providerEnum()) }
    var modelId by remember(chat.id) { mutableStateOf(chat.modelId) }
    var params by remember(chat.id) { mutableStateOf(chat.generationParams()) }
    var agentSetupDraft by remember(chat.id, chat.agentSetupJson) { mutableStateOf(chat.agentSetup()) }

    val visibleProviders = remember(freeOnlyFilter, localLlmEnabled, provider) {
        val base = if (freeOnlyFilter) {
            (LlmProvider.freeProviders() + provider).distinct()
        } else {
            LlmProvider.entries
        }
        base.filter { localLlmEnabled || it != LlmProvider.LOCAL_LLM || it == provider }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .imePadding()
                .navigationBarsPadding()
                .heightIn(max = 640.dp)
                .padding(horizontal = 16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Студия контекста и LLM",
                    style = MaterialTheme.typography.headlineMedium
                )
                Button(
                    onClick = {
                        val participants = participantsRaw.split(",")
                            .map { it.trim() }.filter { it.isNotEmpty() }.take(12)
                        onSave(
                            title,
                            rpMode,
                            systemPrompt,
                            characterName,
                            userPersonaName,
                            userPersonaDesc,
                            authorsNote,
                            memorySummary,
                            loreNotes.toList(),
                            loreEntries,
                            participants,
                            provider,
                            modelId.trim().ifEmpty {
                                provider.suggestedModels.firstOrNull() ?: "openai"
                            },
                            params.clamped(),
                            portraitUri,
                            backdropUri,
                            AgentSetupCodec.encode(agentSetupDraft)
                        )
                        onDismiss()
                    },
                    modifier = Modifier.testTag("chat_config_save_button")
                ) {
                    Icon(Icons.Outlined.Check, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Применить")
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            PrimaryScrollableTabRow(selectedTabIndex = selectedTab, edgePadding = 0.dp) {
                Tab(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    text = { Text("Промпт и Лор") }
                )
                Tab(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    text = { Text("Агенты (${agentSetupDraft.activeAgentCount()})") }
                )
                Tab(
                    selected = selectedTab == 2,
                    onClick = { selectedTab = 2 },
                    text = { Text("Temp / Top-P") }
                )
                Tab(
                    selected = selectedTab == 3,
                    onClick = { selectedTab = 3 },
                    text = { Text("Модель LLM") }
                )
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f, fill = false)
                    .verticalScroll(rememberScrollState())
                    .padding(vertical = 14.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                when (selectedTab) {
                    0 -> {
                        OutlinedTextField(
                            value = title,
                            onValueChange = { title = it },
                            label = { Text("Название чата") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )

                        // Visual Portrait & Scene Backdrop Section
                        Surface(
                            shape = MaterialTheme.shapes.medium,
                            color = MaterialTheme.colorScheme.surfaceContainerLow,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier.padding(12.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text("Портрет и фон сцены", style = MaterialTheme.typography.titleSmall)
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(56.dp)
                                            .clip(RoundedCornerShape(12.dp))
                                    ) {
                                        if (portraitUri.isNotBlank()) {
                                            AsyncImage(
                                                model = portraitUri,
                                                contentDescription = "Портрет персонажа",
                                                contentScale = ContentScale.Crop,
                                                modifier = Modifier.fillMaxSize()
                                            )
                                        } else {
                                            Image(
                                                painter = painterResource(id = R.drawable.img_world_emblem),
                                                contentDescription = "Эмблема",
                                                contentScale = ContentScale.Crop,
                                                modifier = Modifier.fillMaxSize()
                                            )
                                        }
                                    }
                                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                        OutlinedButton(onClick = {
                                            pickPortraitLauncher.launch(
                                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                            )
                                        }) {
                                            Icon(Icons.Outlined.Image, contentDescription = null, modifier = Modifier.size(16.dp))
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text("Портрет")
                                        }
                                        OutlinedButton(
                                            onClick = {
                                                if (isGenPortrait) return@OutlinedButton
                                                isGenPortrait = true
                                                val p = "$characterName, $title, atmospheric RPG portrait"
                                                scope.launch {
                                                    SceneArtManager.generateFromFreeOrCustomEndpoint(
                                                        context, p, imageEndpointTemplate, "portrait"
                                                    ).onSuccess {
                                                        portraitUri = it
                                                        onNotify("Портрет обновлён")
                                                    }.onFailure {
                                                        onNotify(it.localizedMessage ?: "Ошибка генерации арта")
                                                    }
                                                    isGenPortrait = false
                                                }
                                            },
                                            enabled = !isGenPortrait
                                        ) {
                                            if (isGenPortrait) {
                                                CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp)
                                            } else {
                                                Icon(Icons.Outlined.AutoAwesome, contentDescription = null, modifier = Modifier.size(16.dp))
                                            }
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text("Арт")
                                        }
                                        if (portraitUri.isNotBlank()) {
                                            IconButton(onClick = { portraitUri = "" }) {
                                                Icon(Icons.Outlined.Close, contentDescription = "Убрать портрет")
                                            }
                                        }
                                    }
                                }

                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    if (backdropUri.isNotBlank()) {
                                        AsyncImage(
                                            model = backdropUri,
                                            contentDescription = "Фон чата",
                                            contentScale = ContentScale.Crop,
                                            modifier = Modifier
                                                .width(72.dp)
                                                .height(42.dp)
                                                .clip(RoundedCornerShape(8.dp))
                                        )
                                    }
                                    OutlinedButton(onClick = {
                                        pickBackdropLauncher.launch(
                                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                        )
                                    }) {
                                        Text("Фон из фото")
                                    }
                                    OutlinedButton(
                                        onClick = {
                                            if (isGenBackdrop) return@OutlinedButton
                                            isGenBackdrop = true
                                            val bgPrompt = "$title atmospheric dark fantasy environment backdrop"
                                            scope.launch {
                                                SceneArtManager.generateFromFreeOrCustomEndpoint(
                                                    context, bgPrompt, imageEndpointTemplate, "backdrop"
                                                ).onSuccess {
                                                    backdropUri = it
                                                    onNotify("Фон сцены обновлён")
                                                }.onFailure {
                                                    onNotify(it.localizedMessage ?: "Ошибка генерации фона")
                                                }
                                                isGenBackdrop = false
                                            }
                                        },
                                        enabled = !isGenBackdrop
                                    ) {
                                        if (isGenBackdrop) {
                                            CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp)
                                        } else {
                                            Text("Арт фона")
                                        }
                                    }
                                    if (backdropUri.isNotBlank()) {
                                        TextButton(onClick = { backdropUri = "" }) { Text("Убрать") }
                                    }
                                }
                            }
                        }

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            RpMode.entries.forEach { mode ->
                                FilterChip(
                                    selected = rpMode == mode,
                                    onClick = { rpMode = mode },
                                    label = { Text(mode.shortRu) }
                                )
                            }
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedTextField(
                                value = characterName,
                                onValueChange = { characterName = it },
                                label = { Text("Персонаж / DM") },
                                singleLine = true,
                                modifier = Modifier.weight(1f)
                            )
                            OutlinedTextField(
                                value = userPersonaName,
                                onValueChange = { userPersonaName = it },
                                label = { Text("Ваше имя (User)") },
                                singleLine = true,
                                modifier = Modifier.weight(1f)
                            )
                        }

                        OutlinedTextField(
                            value = userPersonaDesc,
                            onValueChange = { userPersonaDesc = it },
                            label = { Text("Описание вашей персоны") },
                            minLines = 2,
                            maxLines = 4,
                            modifier = Modifier.fillMaxWidth()
                        )

                        OutlinedTextField(
                            value = systemPrompt,
                            onValueChange = { systemPrompt = it },
                            label = { Text("Системный промпт") },
                            minLines = 4,
                            maxLines = 10,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("chat_config_system_prompt")
                        )

                        OutlinedTextField(
                            value = authorsNote,
                            onValueChange = { authorsNote = it },
                            label = { Text("Author's Note (режиссёрская заметка)") },
                            placeholder = { Text("Например: Усиль напряжение, персонаж скрывает рану") },
                            minLines = 2,
                            maxLines = 4,
                            modifier = Modifier.fillMaxWidth()
                        )

                        if (rpMode == RpMode.GROUP_SCENE || rpMode == RpMode.GAME_MASTER) {
                            OutlinedTextField(
                                value = participantsRaw,
                                onValueChange = { participantsRaw = it },
                                label = { Text("Участники сцены (через запятую)") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Сжатая память диалога", style = MaterialTheme.typography.titleSmall)
                            OutlinedButton(
                                onClick = onCompressMemory,
                                enabled = !isCompressingMemory,
                                modifier = Modifier.testTag("compress_memory_button")
                            ) {
                                if (isCompressingMemory) {
                                    CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                                } else {
                                    Icon(Icons.Outlined.AutoAwesome, contentDescription = null, modifier = Modifier.size(16.dp))
                                }
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Сжать AI")
                            }
                        }
                        OutlinedTextField(
                            value = memorySummary,
                            onValueChange = { memorySummary = it },
                            label = { Text("Ключевые события прошлых сцен") },
                            minLines = 2,
                            maxLines = 6,
                            modifier = Modifier.fillMaxWidth()
                        )

                        HorizontalDivider()

                        LorebookEditorSection(
                            entries = loreEntries,
                            onEntriesChange = { loreEntries = it }
                        )

                        HorizontalDivider()

                        Text("Быстрые закреплённые заметки лора", style = MaterialTheme.typography.titleSmall)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            OutlinedTextField(
                                value = newLore,
                                onValueChange = { newLore = it },
                                label = { Text("Новый факт лора") },
                                singleLine = true,
                                modifier = Modifier.weight(1f)
                            )
                            OutlinedButton(
                                onClick = {
                                    val fact = newLore.trim()
                                    if (fact.isNotEmpty() && loreNotes.size < 30) {
                                        loreNotes.add(fact)
                                        newLore = ""
                                    }
                                },
                                modifier = Modifier.height(54.dp)
                            ) {
                                Icon(Icons.Outlined.Add, contentDescription = "Добавить факт")
                            }
                        }
                        loreNotes.forEachIndexed { idx, fact ->
                            Surface(
                                shape = MaterialTheme.shapes.small,
                                color = MaterialTheme.colorScheme.surfaceContainerLow,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "• $fact",
                                        style = MaterialTheme.typography.bodySmall,
                                        modifier = Modifier.weight(1f)
                                    )
                                    IconButton(onClick = { loreNotes.removeAt(idx) }) {
                                        Icon(Icons.Outlined.Close, contentDescription = "Удалить")
                                    }
                                }
                            }
                        }
                    }

                    1 -> {
                        AgentPipelineBuilder(
                            setup = agentSetupDraft,
                            onSetupChange = { agentSetupDraft = it },
                            customLibrary = customAgentsLibrary,
                            onSaveCustomAgent = onSaveCustomAgent,
                            onDeleteCustomAgent = onDeleteCustomAgent,
                            localLlmEnabled = localLlmEnabled,
                            freeOnlyFilter = freeOnlyFilter
                        )
                    }

                    2 -> {
                        GenerationTuningControls(
                            params = params,
                            onParamsChange = { params = it },
                            safetyLevel = safetyLevel,
                            onSafetyLevelChange = onSetSafetyLevel,
                            creativeRpFraming = creativeRpFraming,
                            onCreativeRpFramingChange = onSetCreativeRpFraming
                        )
                    }

                    3 -> {
                        OutlinedButton(
                            onClick = { showModelCatalog = true },
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("chat_open_catalog_button")
                        ) {
                            Icon(Icons.Outlined.AutoAwesome, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Каталог и автопоиск моделей (${catalogModels.size})")
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Провайдер LLM", style = MaterialTheme.typography.titleSmall)
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = if (provider.freeFriendly) {
                                    MaterialTheme.colorScheme.tertiaryContainer
                                } else {
                                    MaterialTheme.colorScheme.surfaceContainerHighest
                                }
                            ) {
                                Text(
                                    text = provider.freeBadgeRu,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (provider.freeFriendly) {
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
                            visibleProviders.forEach { p ->
                                FilterChip(
                                    selected = provider == p,
                                    onClick = {
                                        provider = p
                                        modelId = p.suggestedModels.firstOrNull().orEmpty()
                                    },
                                    label = { Text(p.displayName) },
                                    modifier = Modifier.testTag("chat_provider_${p.name}")
                                )
                            }
                        }
                        OutlinedTextField(
                            value = modelId,
                            onValueChange = { modelId = it },
                            label = { Text("Модель") },
                            singleLine = true,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("chat_model_id_input")
                        )
                        Row(
                            modifier = Modifier.horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            provider.suggestedModels.forEach { m ->
                                AssistChip(
                                    onClick = { modelId = m },
                                    label = { Text(m) }
                                )
                            }
                        }
                        val hasConfiguredKey = providerConfigs[provider]?.apiKey?.isNotBlank() == true
                        val missingRequiredKey = provider.requiresApiKey && !hasConfiguredKey
                        Surface(
                            shape = MaterialTheme.shapes.medium,
                            color = if (missingRequiredKey) {
                                MaterialTheme.colorScheme.errorContainer
                            } else {
                                MaterialTheme.colorScheme.surfaceContainerLow
                            }
                        ) {
                            Text(
                                text = when {
                                    missingRequiredKey ->
                                        "Для ${provider.displayName} не указан API-ключ. Добавьте бесплатный ключ во вкладке «Модели» или выберите Pollinations (без ключа)."
                                    provider == LlmProvider.POLLINATIONS ->
                                        "Pollinations работает сразу без ключа и регистрации (1 бесплатный ответ / 45 сек; с опциональным ключом — без паузы)."
                                    provider == LlmProvider.GEMINI_AI_LOGIC ->
                                        "Встроенный Firebase AI Logic. При ошибке App Check (HTTP 403) в Debug APK используйте Pollinations."
                                    provider == LlmProvider.LOCAL_LLM ->
                                        "Локальный сервер на устройстве (http://127.0.0.1:8080/v1 или адрес из вкладки «Модели»)."
                                    else ->
                                        "API-ключ для этого провайдера сохранён во вкладке «Модели»."
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = if (missingRequiredKey) {
                                    MaterialTheme.colorScheme.onErrorContainer
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                },
                                modifier = Modifier.padding(12.dp)
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }

    if (showModelCatalog) {
        ModelCatalogSheet(
            models = catalogModels,
            currentProvider = provider,
            currentModelId = modelId,
            isDiscovering = isDiscoveringModels,
            discoveryProgress = discoveryProgress,
            onDiscoverFreeModels = onDiscoverFreeModels,
            onProbeModel = onProbeModel,
            onSelectModel = { pickedProvider, pickedModelId ->
                provider = pickedProvider
                modelId = pickedModelId
            },
            onDismiss = { showModelCatalog = false }
        )
    }
}
