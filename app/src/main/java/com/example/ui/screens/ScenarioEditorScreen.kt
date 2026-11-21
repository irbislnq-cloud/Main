package com.example.ui.screens

import androidx.activity.compose.BackHandler
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
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
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
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
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
import com.example.data.model.AgentPipelinePresets
import com.example.data.model.AgentSetupCodec
import com.example.data.model.ExpandedScenarioDraft
import com.example.data.model.GenerationParams
import com.example.data.model.LlmProvider
import com.example.data.model.ModelCatalogItem
import com.example.data.model.RpMode
import com.example.data.model.RpScenario
import com.example.data.model.RpgState
import com.example.ui.components.AgentPipelineBuilder
import com.example.ui.components.GenerationTuningControls
import com.example.ui.components.LorebookEditorSection
import com.example.ui.components.ModelCatalogSheet
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScenarioEditorScreen(
    initialScenario: RpScenario?,
    defaultProvider: LlmProvider,
    defaultModelId: String,
    imageEndpointTemplate: String = "https://image.pollinations.ai/prompt/{prompt}?nologo=true",
    freeOnlyFilter: Boolean = true,
    isExpandingWithAi: Boolean = false,
    customAgentsLibrary: List<AgentConfig> = emptyList(),
    onSaveCustomAgent: (AgentConfig) -> Unit = {},
    onDeleteCustomAgent: (String) -> Unit = {},
    localLlmEnabled: Boolean = false,
    onExpandWithAi: (
        title: String,
        subtitle: String,
        rpMode: RpMode,
        systemPrompt: String,
        characterBio: String,
        firstMessage: String,
        freeformIdea: String,
        onDone: (Result<ExpandedScenarioDraft>) -> Unit
    ) -> Unit = { _, _, _, _, _, _, _, cb -> cb(Result.failure(IllegalStateException("AI недоступен"))) },
    catalogModels: List<ModelCatalogItem> = emptyList(),
    isDiscoveringModels: Boolean = false,
    discoveryProgress: String = "",
    onDiscoverFreeModels: () -> Unit = {},
    onProbeModel: (ModelCatalogItem) -> Unit = {},
    onNotify: (String) -> Unit = {},
    onBack: () -> Unit,
    onSave: (RpScenario) -> Unit
) {
    BackHandler { onBack() }

    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val isEditing = initialScenario != null && initialScenario.id.isNotBlank()

    val defaultSystemTemplate = remember {
        """
            Ты играешь роль персонажа/мира в глубокой ролевой игре.
            - Описывай действия в *звёздочках*, а реплики в «кавычках».
            - Не пиши действия и слова за {{user}}.
            - Учитывай лор, характер и детали прошлых сообщений.
        """.trimIndent()
    }

    var title by remember { mutableStateOf(initialScenario?.title.orEmpty()) }
    var subtitle by remember { mutableStateOf(initialScenario?.subtitle.orEmpty()) }
    var rpMode by remember { mutableStateOf(initialScenario?.rpModeEnum() ?: RpMode.CHARACTER_1ON1) }
    var systemPrompt by remember {
        mutableStateOf(initialScenario?.systemPrompt ?: defaultSystemTemplate)
    }
    var characterBio by remember { mutableStateOf(initialScenario?.characterBio.orEmpty()) }
    var firstMessage by remember { mutableStateOf(initialScenario?.firstMessage.orEmpty()) }
    var exampleDialogue by remember { mutableStateOf(initialScenario?.exampleDialogue.orEmpty()) }
    var participantsRaw by remember {
        mutableStateOf(initialScenario?.participants?.joinToString(", ").orEmpty())
    }
    var tagsRaw by remember {
        mutableStateOf(initialScenario?.tags?.joinToString(", ").orEmpty())
    }

    var portraitUri by remember { mutableStateOf(initialScenario?.portraitUri.orEmpty()) }
    var backdropUri by remember { mutableStateOf(initialScenario?.backdropUri.orEmpty()) }
    var isGeneratingPortrait by remember { mutableStateOf(false) }
    var isGeneratingBackdrop by remember { mutableStateOf(false) }

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
                    .onFailure { onNotify(it.localizedMessage ?: "Не удалось загрузить портрет") }
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
                    .onFailure { onNotify(it.localizedMessage ?: "Не удалось загрузить фон") }
            }
        }
    }

    val loreNotes = remember {
        mutableStateListOf<String>().apply {
            addAll(initialScenario?.loreNotes.orEmpty())
        }
    }
    var loreEntries by remember {
        mutableStateOf(initialScenario?.loreEntries.orEmpty())
    }
    var rpgStateDraft by remember {
        mutableStateOf(initialScenario?.defaultRpgState ?: RpgState())
    }
    var enableRpgHud by remember {
        mutableStateOf(initialScenario?.defaultRpgState?.enabled ?: (rpMode == RpMode.GAME_MASTER))
    }
    var newLoreFact by remember { mutableStateOf("") }
    var aiIdeaInput by remember { mutableStateOf("") }
    var aiExpandError by remember { mutableStateOf<String?>(null) }
    var showModelCatalog by remember { mutableStateOf(false) }

    var selectedProvider by remember {
        mutableStateOf(initialScenario?.providerEnum() ?: defaultProvider)
    }
    var modelId by remember {
        mutableStateOf(initialScenario?.defaultModelId?.ifBlank { defaultModelId } ?: defaultModelId)
    }
    var params by remember {
        mutableStateOf(initialScenario?.generationParams() ?: GenerationParams())
    }
    var agentSetupDraft by remember {
        mutableStateOf(
            initialScenario?.agentSetup()
                ?: AgentPipelinePresets.setupForMode(initialScenario?.rpModeEnum() ?: RpMode.CHARACTER_1ON1)
        )
    }

    val visibleProviders = remember(freeOnlyFilter, localLlmEnabled, selectedProvider) {
        val base = if (freeOnlyFilter) {
            (LlmProvider.freeProviders() + selectedProvider).distinct()
        } else {
            LlmProvider.entries
        }
        base.filter { localLlmEnabled || it != LlmProvider.LOCAL_LLM || it == selectedProvider }
    }

    Scaffold(
        contentWindowInsets = WindowInsets.safeDrawing,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = if (isEditing) "Редактор сценария" else "Новый RP-сценарий",
                        style = MaterialTheme.typography.titleLarge
                    )
                },
                navigationIcon = {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier.testTag("scenario_editor_back")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Назад"
                        )
                    }
                },
                actions = {
                    Button(
                        onClick = {
                            val participants = participantsRaw.split(",")
                                .map { it.trim() }.filter { it.isNotEmpty() }.take(12)
                            val tags = tagsRaw.split(",")
                                .map { it.trim() }.filter { it.isNotEmpty() }.take(10)
                            val clamped = params.clamped()
                            val scenario = (initialScenario ?: RpScenario()).copy(
                                title = title.trim().ifEmpty { "Новый сценарий" },
                                subtitle = subtitle.trim(),
                                rpMode = rpMode.name,
                                systemPrompt = systemPrompt.trim().ifEmpty { "Ты — выразительный ролевой партнёр." },
                                characterBio = characterBio.trim(),
                                firstMessage = firstMessage.trim(),
                                exampleDialogue = exampleDialogue.trim(),
                                loreNotes = loreNotes.toList(),
                                loreEntries = loreEntries,
                                defaultRpgState = rpgStateDraft.copy(enabled = enableRpgHud || agentSetupDraft.hasRpgPipeline()),
                                defaultAgentSetupJson = AgentSetupCodec.encode(agentSetupDraft),
                                participants = participants,
                                tags = tags,
                                defaultProvider = selectedProvider.name,
                                defaultModelId = modelId.trim().ifEmpty {
                                    selectedProvider.suggestedModels.firstOrNull() ?: "gemini-3.8-flash"
                                },
                                temperature = clamped.temperature,
                                topP = clamped.topP,
                                topK = clamped.topK.toLong(),
                                maxTokens = clamped.maxTokens.toLong(),
                                contextWindowMessages = clamped.contextWindowMessages.toLong(),
                                presencePenalty = clamped.presencePenalty,
                                frequencyPenalty = clamped.frequencyPenalty,
                                stopSequences = clamped.stopSequences,
                                portraitUri = portraitUri,
                                backdropUri = backdropUri
                            )
                            onSave(scenario)
                        },
                        modifier = Modifier
                            .padding(end = 8.dp)
                            .testTag("save_scenario_button")
                    ) {
                        Icon(Icons.Outlined.Check, contentDescription = null)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Сохранить")
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Column(
                modifier = Modifier
                    .widthIn(max = 720.dp)
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Surface(
                    shape = MaterialTheme.shapes.medium,
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.42f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "AI-Мастер мира: автодополнение",
                            style = MaterialTheme.typography.titleSmall
                        )
                        Text(
                            text = "Опишите завязку в 1–2 фразах или оставьте часть полей пустыми — AI развернёт мир, промпт, приветствие, лорбук, локации и RPG-статы.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        OutlinedTextField(
                            value = aiIdeaInput,
                            onValueChange = { aiIdeaInput = it },
                            label = { Text("Краткая идея мира или сюжета") },
                            placeholder = { Text("Например: Тёмное фэнтези, город на костях дракона и культ алхимиков") },
                            minLines = 2,
                            maxLines = 4,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("editor_ai_idea_input")
                        )
                        OutlinedButton(
                            onClick = {
                                aiExpandError = null
                                val effectiveSys = if (systemPrompt.trim() == defaultSystemTemplate.trim()) "" else systemPrompt
                                onExpandWithAi(
                                    title,
                                    subtitle,
                                    rpMode,
                                    effectiveSys,
                                    characterBio,
                                    firstMessage,
                                    aiIdeaInput
                                ) { res ->
                                    res.onSuccess { draft ->
                                        if (title.isBlank()) title = draft.title
                                        if (subtitle.isBlank()) subtitle = draft.subtitle
                                        if (systemPrompt.isBlank() || systemPrompt.trim() == defaultSystemTemplate.trim()) {
                                            systemPrompt = draft.systemPrompt
                                        }
                                        if (characterBio.isBlank()) characterBio = draft.characterBio
                                        if (firstMessage.isBlank()) firstMessage = draft.firstMessage
                                        if (exampleDialogue.isBlank() && draft.exampleDialogue.isNotBlank()) {
                                            exampleDialogue = draft.exampleDialogue
                                        }
                                        if (participantsRaw.isBlank() && draft.participants.isNotEmpty()) {
                                            participantsRaw = draft.participants.joinToString(", ")
                                        }
                                        if (tagsRaw.isBlank() && draft.tags.isNotEmpty()) {
                                            tagsRaw = draft.tags.joinToString(", ")
                                        }
                                        draft.loreNotes.forEach { note ->
                                            if (note.isNotBlank() && !loreNotes.contains(note) && loreNotes.size < 30) {
                                                loreNotes.add(note)
                                            }
                                        }
                                        if (draft.loreEntries.isNotEmpty()) {
                                            val existingTitles = loreEntries.map { it.title.lowercase() }.toSet()
                                            val added = draft.loreEntries.filter { it.title.lowercase() !in existingTitles }
                                            loreEntries = loreEntries + added
                                        }
                                        if (draft.rpgState.locations.isNotEmpty() ||
                                            draft.rpgState.inventory.isNotEmpty() ||
                                            draft.rpgState.activeQuest.isNotBlank()
                                        ) {
                                            rpgStateDraft = draft.rpgState
                                            if (draft.rpgState.enabled) enableRpgHud = true
                                        }
                                        onNotify("AI дополнил пустые поля, лорбук (${draft.loreEntries.size}) и локации (${draft.rpgState.locations.size})")
                                    }.onFailure { err ->
                                        aiExpandError = err.localizedMessage ?: "Не удалось сгенерировать сценарий"
                                    }
                                }
                            },
                            enabled = !isExpandingWithAi && (
                                aiIdeaInput.isNotBlank() ||
                                    title.isNotBlank() ||
                                    subtitle.isNotBlank() ||
                                    characterBio.isNotBlank()
                                ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("editor_ai_autofill_button")
                        ) {
                            if (isExpandingWithAi) {
                                CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("AI создаёт мир, лор и карту...")
                            } else {
                                Icon(Icons.Outlined.AutoAwesome, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Дозаполнить AI пустые поля")
                            }
                        }
                        if (aiExpandError != null) {
                            Text(
                                text = aiExpandError!!,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error
                            )
                        }
                        if (rpgStateDraft.locations.isNotEmpty()) {
                            Text(
                                text = "Карта: ${rpgStateDraft.locations.joinToString(" → ") { it.name }}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.tertiary
                            )
                        }
                    }
                }

                Text("Формат ролевой игры", style = MaterialTheme.typography.titleMedium)
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
                            label = { Text(mode.labelRu) },
                            modifier = Modifier.testTag("editor_mode_${mode.name}")
                        )
                    }
                }
                Text(
                    text = rpMode.descriptionRu,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                // Visual Portrait & Scene Backdrop Card
                Surface(
                    shape = MaterialTheme.shapes.medium,
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text("Визуал сцены (Портрет и Фон)", style = MaterialTheme.typography.titleSmall)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(72.dp)
                                    .clip(RoundedCornerShape(14.dp))
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
                                        contentDescription = "Портрет по умолчанию",
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier.fillMaxSize()
                                    )
                                }
                            }
                            Column(
                                modifier = Modifier.weight(1f),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    OutlinedButton(
                                        onClick = {
                                            pickPortraitLauncher.launch(
                                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                            )
                                        },
                                        modifier = Modifier.testTag("editor_pick_portrait_button")
                                    ) {
                                        Icon(Icons.Outlined.Image, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("Галерея")
                                    }
                                    OutlinedButton(
                                        onClick = {
                                            if (isGeneratingPortrait) return@OutlinedButton
                                            isGeneratingPortrait = true
                                            val artPrompt = listOf(title, subtitle, characterBio.take(180))
                                                .filter { it.isNotBlank() }
                                                .joinToString(", ") + ", fantasy character portrait, atmospheric lighting"
                                            scope.launch {
                                                SceneArtManager.generateFromFreeOrCustomEndpoint(
                                                    context = context,
                                                    prompt = artPrompt,
                                                    endpointTemplate = imageEndpointTemplate,
                                                    aspectHint = "portrait"
                                                ).onSuccess {
                                                    portraitUri = it
                                                    onNotify("Портрет сгенерирован")
                                                }.onFailure {
                                                    onNotify(it.localizedMessage ?: "Не удалось сгенерировать портрет")
                                                }
                                                isGeneratingPortrait = false
                                            }
                                        },
                                        enabled = !isGeneratingPortrait,
                                        modifier = Modifier.testTag("editor_gen_portrait_button")
                                    ) {
                                        if (isGeneratingPortrait) {
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
                                Text(
                                    text = if (portraitUri.isNotBlank()) "Портрет привязан" else "Портрет персонажа / карточки",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        HorizontalDivider()

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            if (backdropUri.isNotBlank()) {
                                AsyncImage(
                                    model = backdropUri,
                                    contentDescription = "Фон сцены",
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier
                                        .width(96.dp)
                                        .height(54.dp)
                                        .clip(RoundedCornerShape(10.dp))
                                )
                            }
                            Column(modifier = Modifier.weight(1f)) {
                                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    OutlinedButton(
                                        onClick = {
                                            pickBackdropLauncher.launch(
                                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                            )
                                        },
                                        modifier = Modifier.testTag("editor_pick_backdrop_button")
                                    ) {
                                        Text("Фон из фото")
                                    }
                                    OutlinedButton(
                                        onClick = {
                                            if (isGeneratingBackdrop) return@OutlinedButton
                                            isGeneratingBackdrop = true
                                            val bgPrompt = listOf(title, subtitle)
                                                .filter { it.isNotBlank() }
                                                .joinToString(", ") + ", dark moody environment scenery, wide backdrop"
                                            scope.launch {
                                                SceneArtManager.generateFromFreeOrCustomEndpoint(
                                                    context = context,
                                                    prompt = bgPrompt,
                                                    endpointTemplate = imageEndpointTemplate,
                                                    aspectHint = "backdrop"
                                                ).onSuccess {
                                                    backdropUri = it
                                                    onNotify("Фон сцены сгенерирован")
                                                }.onFailure {
                                                    onNotify(it.localizedMessage ?: "Не удалось сгенерировать фон")
                                                }
                                                isGeneratingBackdrop = false
                                            }
                                        },
                                        enabled = !isGeneratingBackdrop,
                                        modifier = Modifier.testTag("editor_gen_backdrop_button")
                                    ) {
                                        if (isGeneratingBackdrop) {
                                            CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp)
                                        } else {
                                            Text("Сгенерировать фон")
                                        }
                                    }
                                    if (backdropUri.isNotBlank()) {
                                        TextButton(onClick = { backdropUri = "" }) { Text("Сброс") }
                                    }
                                }
                            }
                        }
                    }
                }

                Surface(
                    shape = MaterialTheme.shapes.medium,
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Включать RPG-панель (HP, статы, карта, бои)", style = MaterialTheme.typography.titleSmall)
                            Text(
                                text = "Автоматически активирует лист персонажа при запуске чата",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = enableRpgHud,
                            onCheckedChange = { enableRpgHud = it }
                        )
                    }
                }

                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Название персонажа или мира *") },
                    placeholder = { Text("Например: Архонт Нокс / Хроники Теней") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("editor_title_input")
                )

                OutlinedTextField(
                    value = subtitle,
                    onValueChange = { subtitle = it },
                    label = { Text("Краткое описание / жанр") },
                    placeholder = { Text("Например: Нуарный детектив • Сверхъестественное") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Системный промпт", style = MaterialTheme.typography.titleMedium)
                    Row(
                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        listOf("{{char}}", "{{user}}", "*действие*", "((OOC))").forEach { token ->
                            AssistChip(
                                onClick = { systemPrompt = "$systemPrompt $token" },
                                label = { Text("+ $token") }
                            )
                        }
                    }
                    OutlinedTextField(
                        value = systemPrompt,
                        onValueChange = { systemPrompt = it },
                        label = { Text("Инструкции поведения, стиля и границ RP") },
                        minLines = 5,
                        maxLines = 12,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("editor_system_prompt_input")
                    )
                }

                OutlinedTextField(
                    value = characterBio,
                    onValueChange = { characterBio = it },
                    label = { Text("Характер, внешность или правила мира") },
                    minLines = 3,
                    maxLines = 8,
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = firstMessage,
                    onValueChange = { firstMessage = it },
                    label = { Text("Приветствие / открывающая сцена") },
                    placeholder = { Text("*Сцена начинается...* «Приветствую, {{user}}.»") },
                    minLines = 3,
                    maxLines = 8,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("editor_first_message_input")
                )

                OutlinedTextField(
                    value = exampleDialogue,
                    onValueChange = { exampleDialogue = it },
                    label = { Text("Примеры реплик (Few-shot, необязательно)") },
                    minLines = 2,
                    maxLines = 6,
                    modifier = Modifier.fillMaxWidth()
                )

                if (rpMode == RpMode.GROUP_SCENE || rpMode == RpMode.GAME_MASTER) {
                    OutlinedTextField(
                        value = participantsRaw,
                        onValueChange = { participantsRaw = it },
                        label = { Text("Участники сцены (через запятую)") },
                        placeholder = { Text("Рин, Такеши, Вектор") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                OutlinedTextField(
                    value = tagsRaw,
                    onValueChange = { tagsRaw = it },
                    label = { Text("Теги жанра (через запятую)") },
                    placeholder = { Text("Фэнтези, Киберпанк, Романтика, RPG") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                HorizontalDivider()

                LorebookEditorSection(
                    entries = loreEntries,
                    onEntriesChange = { loreEntries = it }
                )

                HorizontalDivider()

                Text("Базовые факты лора (всегда в промпте)", style = MaterialTheme.typography.titleMedium)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = newLoreFact,
                        onValueChange = { newLoreFact = it },
                        label = { Text("Добавить факт мира") },
                        placeholder = { Text("Например: Клинок героя боится солнечного света") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedButton(
                        onClick = {
                            val fact = newLoreFact.trim()
                            if (fact.isNotEmpty() && loreNotes.size < 30) {
                                loreNotes.add(fact)
                                newLoreFact = ""
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
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "${idx + 1}. $fact",
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.weight(1f)
                            )
                            IconButton(onClick = { loreNotes.removeAt(idx) }) {
                                Icon(Icons.Outlined.Close, contentDescription = "Удалить факт")
                            }
                        }
                    }
                }

                HorizontalDivider()

                Text("Состав мультиагентной команды", style = MaterialTheme.typography.titleMedium)
                AgentPipelineBuilder(
                    setup = agentSetupDraft,
                    onSetupChange = { agentSetupDraft = it },
                    customLibrary = customAgentsLibrary,
                    onSaveCustomAgent = onSaveCustomAgent,
                    onDeleteCustomAgent = onDeleteCustomAgent,
                    localLlmEnabled = localLlmEnabled,
                    freeOnlyFilter = freeOnlyFilter
                )

                HorizontalDivider()

                Text("Модель и параметры по умолчанию", style = MaterialTheme.typography.titleMedium)
                OutlinedButton(
                    onClick = { showModelCatalog = true },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("editor_open_catalog_button")
                ) {
                    Icon(Icons.Outlined.AutoAwesome, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Каталог и автопоиск моделей (${selectedProvider.displayName}: $modelId)")
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
                                modelId = prov.suggestedModels.firstOrNull().orEmpty()
                            },
                            label = { Text(prov.displayName) }
                        )
                    }
                }
                OutlinedTextField(
                    value = modelId,
                    onValueChange = { modelId = it },
                    label = { Text("ID модели LLM") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    selectedProvider.suggestedModels.forEach { suggested ->
                        AssistChip(
                            onClick = { modelId = suggested },
                            label = { Text(suggested) }
                        )
                    }
                }

                GenerationTuningControls(
                    params = params,
                    onParamsChange = { params = it }
                )

                Spacer(modifier = Modifier.height(32.dp))
            }
        }
    }

    if (showModelCatalog) {
        ModelCatalogSheet(
            models = catalogModels,
            currentProvider = selectedProvider,
            currentModelId = modelId,
            isDiscovering = isDiscoveringModels,
            discoveryProgress = discoveryProgress,
            onDiscoverFreeModels = onDiscoverFreeModels,
            onProbeModel = onProbeModel,
            onSelectModel = { prov, id ->
                selectedProvider = prov
                modelId = id
                showModelCatalog = false
            },
            onDismiss = { showModelCatalog = false }
        )
    }
}
