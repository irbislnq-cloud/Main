package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.outlined.AccountTree
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.AutoStories
import androidx.compose.material.icons.outlined.Casino
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.DataObject
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Explore
import androidx.compose.material.icons.outlined.GraphicEq
import androidx.compose.material.icons.outlined.Groups
import androidx.compose.material.icons.outlined.Memory
import androidx.compose.material.icons.outlined.MenuBook
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material.icons.outlined.Stop
import androidx.compose.material.icons.outlined.SwapHoriz
import androidx.compose.material.icons.outlined.TextFields
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.example.R
import com.example.data.ai.ContextPromptBuilder
import com.example.data.ai.SemanticSegment
import com.example.data.export.JsonExportManager
import com.example.data.model.LlmProvider
import com.example.data.model.MessageKind
import com.example.data.model.RpMessage
import com.example.data.model.ThemeMode
import com.example.device.AmbientScenePreset
import com.example.device.AmbientSceneResolver
import com.example.device.AmbientSoundEngine
import com.example.device.HapticFeedbackEngine
import com.example.ui.components.AmbientControlSheet
import com.example.ui.components.ModelCatalogSheet
import com.example.ui.components.ReaderSettingsSheet
import com.example.ui.components.RpMessageBubble
import com.example.ui.components.VisualNovelStage
import com.example.ui.theme.ReaderFontStyle
import com.example.ui.viewmodel.LoreCraftViewModel
import com.example.ui.viewmodel.UiState
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    viewModel: LoreCraftViewModel,
    onBack: () -> Unit
) {
    BackHandler { onBack() }

    val context = LocalContext.current
    val view = LocalView.current
    val clipboard = LocalClipboardManager.current

    val chat by viewModel.activeChat.collectAsStateWithLifecycle()
    val allChatsState by viewModel.chatsState.collectAsStateWithLifecycle()
    val messagesState by viewModel.activeMessagesState.collectAsStateWithLifecycle()
    val genState by viewModel.generationState.collectAsStateWithLifecycle()
    val settings by viewModel.localSettings.collectAsStateWithLifecycle()
    val catalogModels by viewModel.catalogModelsState.collectAsStateWithLifecycle()
    val isDiscoveringModels by viewModel.isDiscoveringModels.collectAsStateWithLifecycle()
    val discoveryProgress by viewModel.discoveryProgress.collectAsStateWithLifecycle()
    val suggestedReplies by viewModel.suggestedReplies.collectAsStateWithLifecycle()
    val isGeneratingSuggestions by viewModel.isGeneratingSuggestions.collectAsStateWithLifecycle()
    val deepeningMessageId by viewModel.deepeningMessageId.collectAsStateWithLifecycle()
    val generatingArtMessageId by viewModel.generatingArtMessageId.collectAsStateWithLifecycle()
    val autoAppliedDeltaMessageIds by viewModel.autoAppliedDeltaMessageIds.collectAsStateWithLifecycle()
    val customAgentsLibrary by viewModel.customAgentsLibrary.collectAsStateWithLifecycle()
    val providerCooldowns by viewModel.providerCooldowns.collectAsStateWithLifecycle()
    val isProbingLocalGguf by viewModel.isProbingLocalGguf.collectAsStateWithLifecycle()
    val localGgufModels by viewModel.localGgufModels.collectAsStateWithLifecycle()
    val activeAmbientPreset by AmbientSoundEngine.activeResolvedPreset.collectAsStateWithLifecycle()

    val isDarkTheme = when (settings.themeMode) {
        ThemeMode.DARK -> true
        ThemeMode.LIGHT -> false
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
    }

    val readerFontStyle = settings.readerFontStyle
    val readerFontFamily = readerFontStyle.fontFamily()

    LaunchedEffect(viewModel, settings.hapticsEnabled) {
        viewModel.hapticEvents.collect { event ->
            HapticFeedbackEngine.trigger(view, settings.hapticsEnabled, event)
        }
    }

    LaunchedEffect(
        chat?.id,
        chat?.rpgState?.worldEnv?.weather,
        chat?.rpgState?.currentLocationId,
        chat?.rpgState?.combat?.active,
        settings.ambientEnabled,
        settings.ambientPreset,
        settings.ambientVolume
    ) {
        val currentLoc = chat?.rpgState?.currentLocation()
        val resolved = AmbientSceneResolver.resolve(
            requested = settings.ambientPreset,
            weather = chat?.rpgState?.worldEnv?.weather.orEmpty(),
            locationName = currentLoc?.name.orEmpty(),
            locationDesc = currentLoc?.description.orEmpty(),
            combatActive = chat?.rpgState?.combat?.active == true,
            tags = chat?.agentSetup()?.simulation?.ruleModules?.map { it.name }.orEmpty()
        )
        AmbientSoundEngine.updateState(
            enabled = settings.ambientEnabled,
            resolvedPreset = resolved,
            volume = settings.ambientVolume
        )
    }

    var inputText by remember { mutableStateOf("") }
    var showConfigSheet by remember { mutableStateOf(false) }
    var showModelCatalog by remember { mutableStateOf(false) }
    var showBranchTreeSheet by remember { mutableStateOf(false) }
    var showWorldStudio by remember { mutableStateOf(false) }
    var showReaderSettingsSheet by remember { mutableStateOf(false) }
    var showAmbientSheet by remember { mutableStateOf(false) }
    var studioInitialTab by remember { mutableIntStateOf(0) }
    var studioFocusId by remember { mutableStateOf<String?>(null) }
    var showDiceMenu by remember { mutableStateOf(false) }
    var showMoreMenu by remember { mutableStateOf(false) }
    var editingMessage by remember { mutableStateOf<RpMessage?>(null) }
    var deletingMessage by remember { mutableStateOf<RpMessage?>(null) }
    var showDeleteChatDialog by remember { mutableStateOf(false) }
    var pendingExportJson by remember { mutableStateOf<String?>(null) }

    fun openStudio(tab: Int, focusId: String? = null) {
        studioInitialTab = tab
        studioFocusId = focusId
        showWorldStudio = true
    }

    val createJsonDocLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        val json = pendingExportJson
        pendingExportJson = null
        if (uri != null && json != null) {
            JsonExportManager.writeJsonToUri(context, uri, json)
                .onSuccess { viewModel.notifyMessage("Чат сохранён в JSON") }
                .onFailure { viewModel.notifyMessage(it.localizedMessage ?: "Ошибка записи файла") }
        }
    }

    val listState = rememberLazyListState()
    val messages = (messagesState as? UiState.Success)?.data.orEmpty()
    val allChats = (allChatsState as? UiState.Success)?.data.orEmpty()

    val triggeredLoreCount = remember(chat, messages) {
        val c = chat ?: return@remember 0
        ContextPromptBuilder.findTriggeredLoreEntries(c, messages).size
    }

    val isNearBottom by remember {
        derivedStateOf {
            val info = listState.layoutInfo
            val total = info.totalItemsCount
            if (total == 0) return@derivedStateOf true
            val lastVisible = info.visibleItemsInfo.lastOrNull()?.index ?: 0
            lastVisible >= total - 2
        }
    }

    LaunchedEffect(messages.size) {
        if (genState.swipingMessageId == null && (messages.isNotEmpty() || genState.isGenerating)) {
            val targetIndex = if (genState.isGenerating) messages.size else (messages.size - 1).coerceAtLeast(0)
            listState.animateScrollToItem(targetIndex)
        }
    }

    LaunchedEffect(genState.isGenerating, genState.swipingMessageId) {
        if (genState.isGenerating && genState.swipingMessageId == null) {
            while (true) {
                if (isNearBottom) {
                    val lastIdx = (listState.layoutInfo.totalItemsCount - 1).coerceAtLeast(0)
                    runCatching {
                        listState.scrollToItem(lastIdx, 100_000)
                    }
                }
                kotlinx.coroutines.delay(140L)
            }
        }
    }

    Scaffold(
        contentWindowInsets = WindowInsets.safeDrawing,
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier.testTag("chat_back_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "К списку чатов"
                        )
                    }
                },
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        val portrait = chat?.portraitUri.orEmpty()
                        Box(
                            modifier = Modifier.clickable(enabled = chat != null) { showConfigSheet = true }
                        ) {
                            if (portrait.isNotBlank()) {
                                AsyncImage(
                                    model = portrait,
                                    contentDescription = "Портрет персонажа",
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier
                                        .size(36.dp)
                                        .clip(CircleShape)
                                )
                            } else {
                                Image(
                                    painter = painterResource(id = R.drawable.img_world_emblem),
                                    contentDescription = "Аватар мира",
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier
                                        .size(36.dp)
                                        .clip(CircleShape)
                                )
                            }
                        }
                        Column(modifier = Modifier.weight(1f, fill = false)) {
                            Text(
                                text = chat?.title ?: "Загрузка чата...",
                                style = MaterialTheme.typography.titleMedium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.clickable(enabled = chat != null) { showConfigSheet = true }
                            )
                            val c = chat
                            if (c != null) {
                                val cooldownSec = providerCooldowns[c.providerEnum()] ?: 0
                                Surface(
                                    shape = RoundedCornerShape(6.dp),
                                    color = if (cooldownSec > 0) {
                                        MaterialTheme.colorScheme.errorContainer
                                    } else {
                                        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.65f)
                                    },
                                    modifier = Modifier
                                        .padding(top = 2.dp)
                                        .clickable { showModelCatalog = true }
                                        .testTag("chat_header_model_badge")
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        Text(
                                            text = if (cooldownSec > 0) {
                                                "⏳ ${cooldownSec}с • ${c.modelId}"
                                            } else {
                                                "${c.modelId} • T=${String.format(Locale.US, "%.2f", c.temperature)}"
                                            },
                                            style = MaterialTheme.typography.labelSmall,
                                            color = if (cooldownSec > 0) {
                                                MaterialTheme.colorScheme.onErrorContainer
                                            } else {
                                                MaterialTheme.colorScheme.onPrimaryContainer
                                            },
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Icon(
                                            imageVector = Icons.Outlined.SwapHoriz,
                                            contentDescription = "Сменить модель",
                                            tint = if (cooldownSec > 0) {
                                                MaterialTheme.colorScheme.onErrorContainer
                                            } else {
                                                MaterialTheme.colorScheme.onPrimaryContainer
                                            },
                                            modifier = Modifier.size(12.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                },
                actions = {
                    IconButton(
                        onClick = { viewModel.setVisualNovelMode(!settings.visualNovelMode) },
                        enabled = chat != null,
                        modifier = Modifier.testTag("chat_vn_toggle_button")
                    ) {
                        Icon(
                            imageVector = if (settings.visualNovelMode) {
                                Icons.Outlined.ChatBubbleOutline
                            } else {
                                Icons.Outlined.AutoStories
                            },
                            contentDescription = if (settings.visualNovelMode) {
                                "Переключиться в режим чата"
                            } else {
                                "Режим визуальной новеллы"
                            },
                            tint = if (settings.visualNovelMode) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            }
                        )
                    }
                    IconButton(
                        onClick = { openStudio(0) },
                        enabled = chat != null,
                        modifier = Modifier.testTag("chat_rpg_hud_button")
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Shield,
                            contentDescription = "Студия мира, NPC, карты и героя",
                            tint = if (chat?.rpgState?.enabled == true) {
                                MaterialTheme.colorScheme.secondary
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            }
                        )
                    }
                    IconButton(
                        onClick = { showBranchTreeSheet = true },
                        enabled = chat != null,
                        modifier = Modifier.testTag("chat_branch_tree_button")
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.AccountTree,
                            contentDescription = "Дерево веток сюжета",
                            tint = MaterialTheme.colorScheme.tertiary
                        )
                    }
                    Box {
                        IconButton(
                            onClick = { showDiceMenu = true },
                            modifier = Modifier.testTag("chat_dice_menu_button")
                        ) {
                            Icon(Icons.Outlined.Casino, contentDescription = "Бросить кубик")
                        }
                        DropdownMenu(
                            expanded = showDiceMenu,
                            onDismissRequest = { showDiceMenu = false }
                        ) {
                            listOf(4, 6, 8, 10, 12, 20, 100).forEach { sides ->
                                DropdownMenuItem(
                                    text = { Text("Бросить d$sides") },
                                    leadingIcon = { Icon(Icons.Outlined.Casino, contentDescription = null) },
                                    onClick = {
                                        showDiceMenu = false
                                        viewModel.rollDice(sides)
                                    }
                                )
                            }
                        }
                    }
                    IconButton(
                        onClick = { showConfigSheet = true },
                        enabled = chat != null,
                        modifier = Modifier.testTag("chat_tuning_button")
                    ) {
                        Icon(Icons.Outlined.Tune, contentDescription = "Параметры и контекст")
                    }
                    Box {
                        IconButton(
                            onClick = { showMoreMenu = true },
                            modifier = Modifier.testTag("chat_more_menu_button")
                        ) {
                            Icon(Icons.Outlined.MoreVert, contentDescription = "Ещё")
                        }
                        DropdownMenu(
                            expanded = showMoreMenu,
                            onDismissRequest = { showMoreMenu = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text("Книжный режим и шрифты") },
                                leadingIcon = { Icon(Icons.Outlined.TextFields, contentDescription = null) },
                                onClick = {
                                    showMoreMenu = false
                                    showReaderSettingsSheet = true
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Эмбиент-аудио и Local GGUF") },
                                leadingIcon = { Icon(Icons.Outlined.GraphicEq, contentDescription = null) },
                                onClick = {
                                    showMoreMenu = false
                                    showAmbientSheet = true
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Сохранить JSON в файл") },
                                leadingIcon = { Icon(Icons.Outlined.DataObject, contentDescription = null) },
                                onClick = {
                                    showMoreMenu = false
                                    viewModel.buildActiveChatExport()?.let { (name, json) ->
                                        pendingExportJson = json
                                        createJsonDocLauncher.launch(name)
                                    }
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Поделиться JSON") },
                                leadingIcon = { Icon(Icons.Outlined.Share, contentDescription = null) },
                                onClick = {
                                    showMoreMenu = false
                                    viewModel.buildActiveChatExport()?.let { (name, json) ->
                                        val shareIntent = JsonExportManager.buildShareIntent(context, name, json)
                                        context.startActivity(
                                            android.content.Intent.createChooser(shareIntent, "Экспорт RP в JSON")
                                        )
                                    }
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Сжать память сцены (AI)") },
                                leadingIcon = { Icon(Icons.Outlined.AutoAwesome, contentDescription = null) },
                                onClick = {
                                    showMoreMenu = false
                                    viewModel.compressSceneMemory()
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Удалить чат") },
                                leadingIcon = { Icon(Icons.Outlined.DeleteOutline, contentDescription = null) },
                                onClick = {
                                    showMoreMenu = false
                                    showDeleteChatDialog = true
                                }
                            )
                        }
                    }
                }
            )
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // Optional scene atmospheric backdrop with readable scrim
            val backdrop = chat?.backdropUri.orEmpty()
            if (backdrop.isNotBlank()) {
                AsyncImage(
                    model = backdrop,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxSize()
                        .alpha(0.16f)
                )
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(
                                    MaterialTheme.colorScheme.background.copy(alpha = 0.85f),
                                    MaterialTheme.colorScheme.background.copy(alpha = 0.70f),
                                    MaterialTheme.colorScheme.background.copy(alpha = 0.94f)
                                )
                            )
                        )
                )
            }

            Column(
                modifier = Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Live RPG, Custom Simulation Meters, Atmosphere, Book Reader & Local GGUF HUD strip
                val activeRpg = chat?.rpgState
                val activeSetup = chat?.agentSetup()
                val activeAgentCount = activeSetup?.activeAgentCount() ?: 0
                val customMeters = activeSetup?.simulation?.customMeters.orEmpty().filter { it.showInHud }
                Row(
                    modifier = Modifier
                        .widthIn(max = 800.dp)
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = 12.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Quick Book Reader & Typography Pill
                    Surface(
                        shape = RoundedCornerShape(50),
                        color = if (settings.readerBookMode) {
                            MaterialTheme.colorScheme.primaryContainer
                        } else {
                            MaterialTheme.colorScheme.surfaceContainerHigh
                        },
                        modifier = Modifier
                            .clickable { showReaderSettingsSheet = true }
                            .testTag("chat_reader_settings_pill")
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(5.dp)
                        ) {
                            Icon(
                                Icons.Outlined.MenuBook,
                                contentDescription = "Книжный режим и шрифт",
                                tint = if (settings.readerBookMode) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(14.dp)
                            )
                            Text(
                                text = if (settings.readerBookMode) "Книга • ${settings.readerFontSizeSp.toInt()}sp" else "Чтение",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }

                    // Quick Ambient Audio Pill
                    Surface(
                        shape = RoundedCornerShape(50),
                        color = if (settings.ambientEnabled) {
                            MaterialTheme.colorScheme.secondaryContainer
                        } else {
                            MaterialTheme.colorScheme.surfaceContainerHigh
                        },
                        modifier = Modifier
                            .clickable { showAmbientSheet = true }
                            .testTag("chat_ambient_pill")
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(5.dp)
                        ) {
                            Icon(
                                Icons.Outlined.GraphicEq,
                                contentDescription = "Эмбиент сцены",
                                tint = if (settings.ambientEnabled) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(14.dp)
                            )
                            Text(
                                text = if (settings.ambientEnabled) "${activeAmbientPreset.badgeIcon} Фон" else "Звук",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }

                    // 1-Tap Local GGUF Check & Switch Pill
                    Surface(
                        shape = RoundedCornerShape(50),
                        color = if (chat?.providerEnum() == LlmProvider.LOCAL_LLM) {
                            MaterialTheme.colorScheme.tertiaryContainer
                        } else {
                            MaterialTheme.colorScheme.surfaceContainerHigh
                        },
                        modifier = Modifier
                            .clickable(enabled = !isProbingLocalGguf) { viewModel.quickCheckAndSwitchLocalGguf() }
                            .testTag("chat_local_gguf_pill")
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(5.dp)
                        ) {
                            if (isProbingLocalGguf) {
                                CircularProgressIndicator(modifier = Modifier.size(12.dp), strokeWidth = 2.dp)
                            } else {
                                Icon(
                                    Icons.Outlined.Memory,
                                    contentDescription = "Локальная GGUF",
                                    tint = if (chat?.providerEnum() == LlmProvider.LOCAL_LLM) {
                                        MaterialTheme.colorScheme.tertiary
                                    } else {
                                        MaterialTheme.colorScheme.onSurfaceVariant
                                    },
                                    modifier = Modifier.size(14.dp)
                                )
                            }
                            Text(
                                text = if (chat?.providerEnum() == LlmProvider.LOCAL_LLM) "GGUF ON" else "GGUF",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }

                    // Dynamic Custom Simulation Meters
                    customMeters.forEach { meter ->
                        val currentVal = activeRpg?.stats?.get(meter.label) ?: meter.initialValue
                        val isWarning = meter.warningThreshold?.let { threshold ->
                            if (meter.warnWhenBelow) currentVal <= threshold else currentVal >= threshold
                        } ?: false
                        Surface(
                            shape = RoundedCornerShape(50),
                            color = if (isWarning) {
                                MaterialTheme.colorScheme.errorContainer
                            } else {
                                MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.72f)
                            },
                            modifier = Modifier
                                .clickable { showConfigSheet = true }
                                .testTag("custom_meter_pill_${meter.id}")
                        ) {
                            Text(
                                text = "${meter.label}: $currentVal${meter.unit}",
                                style = MaterialTheme.typography.labelSmall,
                                color = if (isWarning) {
                                    MaterialTheme.colorScheme.onErrorContainer
                                } else {
                                    MaterialTheme.colorScheme.onTertiaryContainer
                                },
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                            )
                        }
                    }

                    if (activeAgentCount > 0) {
                        Surface(
                            shape = RoundedCornerShape(50),
                            color = MaterialTheme.colorScheme.surfaceContainerHigh,
                            modifier = Modifier
                                .clickable { showConfigSheet = true }
                                .testTag("live_agents_pill")
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(5.dp)
                            ) {
                                Icon(
                                    Icons.Outlined.Groups,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(14.dp)
                                )
                                Text(
                                    text = "$activeAgentCount аг.",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                    }
                    if (activeRpg != null && activeRpg.enabled) {
                        Surface(
                            shape = RoundedCornerShape(50),
                            color = MaterialTheme.colorScheme.secondaryContainer,
                            modifier = Modifier
                                .clickable { openStudio(0) }
                                .testTag("live_rpg_hud_pill")
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(
                                    Icons.Outlined.Shield,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSecondaryContainer,
                                    modifier = Modifier.size(14.dp)
                                )
                                Text(
                                    text = "Ур.${activeRpg.level} • HP ${activeRpg.hp}/${activeRpg.maxHp} • ${activeRpg.gold}g",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSecondaryContainer
                                )
                            }
                        }
                        val loc = activeRpg.currentLocation()
                        if (loc != null) {
                            Surface(
                                shape = RoundedCornerShape(50),
                                color = MaterialTheme.colorScheme.primaryContainer,
                                modifier = Modifier
                                    .clickable { openStudio(2, loc.id) }
                                    .testTag("live_location_pill")
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Icon(
                                        Icons.Outlined.Explore,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                        modifier = Modifier.size(14.dp)
                                    )
                                    Text(
                                        text = "${loc.name} • Д.${activeRpg.worldEnv.dayCount}, ${activeRpg.worldEnv.timeOfDay}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer
                                    )
                                }
                            }
                        }
                        if (activeRpg.npcs.isNotEmpty()) {
                            Surface(
                                shape = RoundedCornerShape(50),
                                color = MaterialTheme.colorScheme.surfaceContainerHighest,
                                modifier = Modifier
                                    .clickable { openStudio(1) }
                                    .testTag("live_npc_count_pill")
                            ) {
                                Text(
                                    text = "NPC: ${activeRpg.npcs.size}",
                                    style = MaterialTheme.typography.labelSmall,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                                )
                            }
                        }
                        if (activeRpg.combat.active) {
                            Surface(
                                shape = RoundedCornerShape(50),
                                color = MaterialTheme.colorScheme.errorContainer,
                                modifier = Modifier.clickable { openStudio(4) }
                            ) {
                                Text(
                                    text = "Бой • Раунд ${activeRpg.combat.round}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onErrorContainer,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                                )
                            }
                        }
                    }
                    if (triggeredLoreCount > 0) {
                        Surface(
                            shape = RoundedCornerShape(50),
                            color = MaterialTheme.colorScheme.tertiaryContainer,
                            modifier = Modifier
                                .clickable { openStudio(3) }
                                .testTag("live_lore_count_pill")
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(5.dp)
                            ) {
                                Icon(
                                    Icons.Outlined.AutoStories,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onTertiaryContainer,
                                    modifier = Modifier.size(14.dp)
                                )
                                Text(
                                    text = "Лор: $triggeredLoreCount",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onTertiaryContainer
                                )
                            }
                        }
                    }
                }

                // Multi-agent active stage banner during generation
                if (genState.isGenerating && genState.stageLabel.isNotBlank()) {
                    Surface(
                        shape = RoundedCornerShape(50),
                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.72f),
                        modifier = Modifier
                            .padding(horizontal = 12.dp, vertical = 2.dp)
                            .testTag("agent_pipeline_stage_pill")
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            CircularProgressIndicator(modifier = Modifier.size(13.dp), strokeWidth = 2.dp)
                            Text(
                                text = buildString {
                                    append(genState.stageLabel)
                                    if (genState.activeAgents.isNotEmpty()) {
                                        append(" • ")
                                        append(genState.activeAgents.joinToString(", "))
                                    }
                                },
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .widthIn(max = 800.dp)
                        .fillMaxWidth()
                ) {
                    when (val state = messagesState) {
                        is UiState.Loading -> {
                            CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                        }
                        is UiState.Error -> {
                            Text(
                                text = state.message,
                                color = MaterialTheme.colorScheme.error,
                                modifier = Modifier
                                    .align(Alignment.Center)
                                    .padding(24.dp)
                            )
                        }
                        is UiState.Success -> {
                            if (settings.visualNovelMode) {
                                VisualNovelStage(
                                    chat = chat,
                                    messages = state.data,
                                    streamingText = genState.streamingText,
                                    isGenerating = genState.isGenerating,
                                    autoAppliedDeltaIds = autoAppliedDeltaMessageIds,
                                    onSemanticSpanClick = { span ->
                                        viewModel.handleSemanticSpanClick(span) { tabIdx, focusId ->
                                            openStudio(tabIdx, focusId)
                                        }
                                    },
                                    onApplyRpgDelta = { delta -> viewModel.applyRpgUpdateDelta(delta) },
                                    onOpenNpcDossier = { npcId -> openStudio(1, npcId) },
                                    onOpenLocationMap = { openStudio(2) },
                                    onGenerateArtForMessage = { msg -> viewModel.generateSceneArtForMessage(msg) },
                                    modifier = Modifier.fillMaxSize()
                                )
                            } else {
                                val lastAssistantId = state.data.lastOrNull { it.role == "assistant" }?.id
                                LazyColumn(
                                    state = listState,
                                    contentPadding = PaddingValues(vertical = 12.dp),
                                    verticalArrangement = Arrangement.spacedBy(4.dp),
                                    modifier = Modifier.fillMaxSize()
                                ) {
                                    if (state.data.isEmpty() && !genState.isGenerating) {
                                        item {
                                            Surface(
                                                shape = MaterialTheme.shapes.large,
                                                color = MaterialTheme.colorScheme.surfaceContainerLow,
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .padding(16.dp)
                                            ) {
                                                Column(
                                                    modifier = Modifier.padding(18.dp),
                                                    verticalArrangement = Arrangement.spacedBy(6.dp)
                                                ) {
                                                    Text(
                                                        text = "Сцена готова к началу",
                                                        style = MaterialTheme.typography.titleMedium
                                                    )
                                                    Text(
                                                        text = "Напишите первое действие в *звёздочках*, реплику или нажмите «Продолжить», чтобы команда агентов открыла сцену.",
                                                        style = MaterialTheme.typography.bodyMedium,
                                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                                    )
                                                }
                                            }
                                        }
                                    }

                                    itemsIndexed(
                                        items = state.data,
                                        key = { _, msg -> msg.id.ifBlank { msg.orderIndex.toString() } }
                                    ) { _, msg ->
                                        val isSwipingThis = genState.isGenerating && genState.swipingMessageId == msg.id
                                        RpMessageBubble(
                                            message = msg,
                                            showModelBadge = settings.showModelBadges,
                                            isLastAssistant = msg.id == lastAssistantId,
                                            isGenerating = genState.isGenerating,
                                            isSwipingThisMessage = isSwipingThis,
                                            streamingOverrideText = if (isSwipingThis) genState.streamingText else null,
                                            isDarkTheme = isDarkTheme,
                                            stageLabel = if (isSwipingThis) genState.stageLabel else "",
                                            activeAgents = if (isSwipingThis) genState.activeAgents else emptyList(),
                                            isDeltaApplied = msg.id in autoAppliedDeltaMessageIds,
                                            isDeepening = deepeningMessageId == msg.id,
                                            isGeneratingArt = generatingArtMessageId == msg.id,
                                            bookMode = settings.readerBookMode,
                                            readerFontFamily = readerFontFamily,
                                            readerFontSizeSp = settings.readerFontSizeSp.toFloat(),
                                            readerLineHeightMultiplier = settings.readerLineHeight,
                                            hapticsEnabled = settings.hapticsEnabled,
                                            onSemanticSpanClick = { span ->
                                                viewModel.handleSemanticSpanClick(span) { tabIdx, focusId ->
                                                    openStudio(tabIdx, focusId)
                                                }
                                            },
                                            onDeepenMessage = { viewModel.deepenMessageWithDetailer(msg) },
                                            onGenerateSceneArt = { viewModel.generateSceneArtForMessage(msg) },
                                            onQuickRegisterSelection = { entityType, label ->
                                                viewModel.handleSemanticSpanClick(
                                                    SemanticSegment.SemanticSpan(
                                                        type = entityType,
                                                        entityId = "",
                                                        displayText = label
                                                    )
                                                ) { tabIdx, focusId ->
                                                    openStudio(tabIdx, focusId)
                                                }
                                            },
                                            onEdit = { editingMessage = msg },
                                            onRegenerate = { viewModel.regenerateLastResponse() },
                                            onBranch = {
                                                viewModel.branchFromMessage(msg) { }
                                            },
                                            onCopy = {
                                                clipboard.setText(AnnotatedString(msg.activeContent()))
                                                viewModel.notifyMessage("Текст скопирован")
                                            },
                                            onDeleteFromHere = { deletingMessage = msg },
                                            onSelectVariant = { idx -> viewModel.selectSwipeVariant(msg, idx) },
                                            onGenerateNewVariant = { viewModel.generateSwipeVariant(msg) },
                                            onApplyRpgDelta = { delta -> viewModel.applyRpgUpdateDelta(delta) }
                                        )
                                    }

                                    if (genState.isGenerating && genState.swipingMessageId == null) {
                                        item(key = "streaming_bubble") {
                                            RpMessageBubble(
                                                message = RpMessage(
                                                    id = "streaming",
                                                    role = "assistant",
                                                    speakerName = chat?.characterName ?: "Персонаж",
                                                    content = genState.streamingText.ifBlank { "Печатает ответ..." },
                                                    messageKind = MessageKind.ROLEPLAY.name,
                                                    modelUsed = chat?.modelId.orEmpty()
                                                ),
                                                showModelBadge = settings.showModelBadges,
                                                isLastAssistant = false,
                                                isGenerating = true,
                                                isDarkTheme = isDarkTheme,
                                                stageLabel = genState.stageLabel,
                                                activeAgents = genState.activeAgents,
                                                bookMode = settings.readerBookMode,
                                                readerFontFamily = readerFontFamily,
                                                readerFontSizeSp = settings.readerFontSizeSp.toFloat(),
                                                readerLineHeightMultiplier = settings.readerLineHeight,
                                                hapticsEnabled = settings.hapticsEnabled,
                                                onEdit = {},
                                                onRegenerate = {},
                                                onBranch = {},
                                                onCopy = {},
                                                onDeleteFromHere = {}
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // Error surface with instant Retry + 1-tap Free Fallback
                if (genState.errorMessage != null) {
                    Surface(
                        shape = MaterialTheme.shapes.medium,
                        color = MaterialTheme.colorScheme.errorContainer,
                        modifier = Modifier
                            .widthIn(max = 800.dp)
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 4.dp)
                    ) {
                        Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                            Text(
                                text = genState.errorMessage!!,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onErrorContainer
                            )
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.End
                            ) {
                                TextButton(
                                    onClick = { viewModel.switchToFreeFallbackAndRetry() },
                                    modifier = Modifier.testTag("free_fallback_retry_button")
                                ) {
                                    Icon(Icons.Outlined.SwapHoriz, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Бесплатный резерв")
                                }
                                TextButton(onClick = {
                                    viewModel.clearGenerationError()
                                    viewModel.regenerateLastResponse()
                                }) {
                                    Icon(Icons.Outlined.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Повтор")
                                }
                            }
                        }
                    }
                }

                // 3 AI Suggested Quick-Reply Chips
                if (!genState.isGenerating && messages.isNotEmpty() && settings.autoSuggestActions) {
                    Row(
                        modifier = Modifier
                            .widthIn(max = 800.dp)
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState())
                            .padding(horizontal = 12.dp, vertical = 2.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        AssistChip(
                            onClick = { viewModel.refreshActionSuggestions() },
                            enabled = !isGeneratingSuggestions,
                            leadingIcon = {
                                if (isGeneratingSuggestions) {
                                    CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp)
                                } else {
                                    Icon(Icons.Outlined.AutoAwesome, contentDescription = null, modifier = Modifier.size(16.dp))
                                }
                            },
                            label = { Text("Идеи") },
                            modifier = Modifier.testTag("refresh_suggestions_chip")
                        )
                        suggestedReplies.forEachIndexed { idx, suggestion ->
                            AssistChip(
                                onClick = {
                                    if (settings.autoSendSuggestedAction) {
                                        viewModel.sendMessage(suggestion)
                                    } else {
                                        inputText = suggestion
                                    }
                                },
                                label = {
                                    Text(
                                        text = suggestion,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                },
                                modifier = Modifier
                                    .widthIn(max = 260.dp)
                                    .testTag("suggested_reply_chip_$idx")
                            )
                        }
                    }
                }

                // RP Formatting & Quick Actions Bar
                Row(
                    modifier = Modifier
                        .widthIn(max = 800.dp)
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = 12.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    AssistChip(
                        onClick = { inputText = "${inputText.trimEnd()} *действие* ".trimStart() },
                        label = { Text("*действие*") }
                    )
                    AssistChip(
                        onClick = { inputText = "${inputText.trimEnd()} «речь» ".trimStart() },
                        label = { Text("«речь»") }
                    )
                    AssistChip(
                        onClick = { inputText = "((OOC: ${inputText.trim()}))" },
                        label = { Text("((OOC))") }
                    )
                    AssistChip(
                        onClick = { viewModel.rollDice(20) },
                        enabled = !genState.isGenerating,
                        leadingIcon = { Icon(Icons.Outlined.Casino, contentDescription = null, modifier = Modifier.size(16.dp)) },
                        label = { Text("d20") }
                    )
                    AssistChip(
                        onClick = { viewModel.continueScene() },
                        enabled = !genState.isGenerating,
                        leadingIcon = { Icon(Icons.Outlined.PlayArrow, contentDescription = null, modifier = Modifier.size(16.dp)) },
                        label = { Text("Продолжить") }
                    )
                }

                // Message Composer
                Surface(
                    tonalElevation = 3.dp,
                    color = MaterialTheme.colorScheme.surface,
                    modifier = Modifier
                        .widthIn(max = 800.dp)
                        .fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.Bottom,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedTextField(
                            value = inputText,
                            onValueChange = { inputText = it },
                            placeholder = { Text("Ваше действие или реплика...") },
                            maxLines = 5,
                            shape = RoundedCornerShape(22.dp),
                            modifier = Modifier
                                .weight(1f)
                                .testTag("chat_message_input")
                        )

                        if (genState.isGenerating) {
                            FilledIconButton(
                                onClick = { viewModel.stopGeneration(savePartial = true) },
                                colors = IconButtonDefaults.filledIconButtonColors(
                                    containerColor = MaterialTheme.colorScheme.errorContainer,
                                    contentColor = MaterialTheme.colorScheme.onErrorContainer
                                ),
                                modifier = Modifier
                                    .size(50.dp)
                                    .testTag("chat_stop_button")
                            ) {
                                Icon(Icons.Outlined.Stop, contentDescription = "Остановить генерацию")
                            }
                        } else {
                            FilledIconButton(
                                onClick = {
                                    val text = inputText
                                    if (text.isNotBlank()) {
                                        inputText = ""
                                        viewModel.sendMessage(text)
                                    }
                                },
                                enabled = inputText.isNotBlank() && chat != null,
                                modifier = Modifier
                                    .size(50.dp)
                                    .testTag("chat_send_button")
                            ) {
                                Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Отправить")
                            }
                        }
                    }
                }
            }
        }
    }

    val currentChat = chat
    if (showConfigSheet && currentChat != null) {
        ChatConfigSheet(
            chat = currentChat,
            isCompressingMemory = genState.isCompressingMemory,
            imageEndpointTemplate = settings.imageEndpointTemplate,
            freeOnlyFilter = settings.freeOnlyFilter,
            providerConfigs = settings.providerConfigs,
            safetyLevel = settings.safetyLevel,
            creativeRpFraming = settings.creativeRpFraming,
            onSetSafetyLevel = { viewModel.setSafetyLevel(it) },
            onSetCreativeRpFraming = { viewModel.setCreativeRpFraming(it) },
            catalogModels = catalogModels,
            isDiscoveringModels = isDiscoveringModels,
            discoveryProgress = discoveryProgress,
            onDiscoverFreeModels = { viewModel.refreshModelCatalog(probeFreeLive = true) },
            onProbeModel = { viewModel.probeSingleModel(it) },
            customAgentsLibrary = customAgentsLibrary,
            onSaveCustomAgent = { viewModel.saveCustomAgentToLibrary(it) },
            onDeleteCustomAgent = { viewModel.deleteCustomAgentFromLibrary(it) },
            localLlmEnabled = settings.localLlmEnabled,
            onNotify = { viewModel.notifyMessage(it) },
            onDismiss = { showConfigSheet = false },
            onCompressMemory = { viewModel.compressSceneMemory() },
            onSave = { title, mode, sysPrompt, charName, userName, userDesc, aNote, memSum, lore, entries, parts, prov, model, p, portrait, backdrop, agentSetupJson ->
                viewModel.updateActiveChatConfig(
                    title = title,
                    rpMode = mode,
                    systemPrompt = sysPrompt,
                    characterName = charName,
                    userPersonaName = userName,
                    userPersonaDesc = userDesc,
                    authorsNote = aNote,
                    memorySummary = memSum,
                    loreNotes = lore,
                    loreEntries = entries,
                    participants = parts,
                    provider = prov,
                    modelId = model,
                    params = p,
                    portraitUri = portrait,
                    backdropUri = backdrop,
                    agentSetupJson = agentSetupJson
                )
            }
        )
    }

    if (showModelCatalog && currentChat != null) {
        ModelCatalogSheet(
            models = catalogModels,
            currentProvider = currentChat.providerEnum(),
            currentModelId = currentChat.modelId,
            isDiscovering = isDiscoveringModels,
            discoveryProgress = discoveryProgress,
            onDiscoverFreeModels = { viewModel.refreshModelCatalog(probeFreeLive = true) },
            onProbeModel = { viewModel.probeSingleModel(it) },
            onSelectModel = { pickedProvider, pickedModelId ->
                viewModel.updateActiveChatConfig(
                    title = currentChat.title,
                    rpMode = currentChat.rpModeEnum(),
                    systemPrompt = currentChat.systemPrompt,
                    characterName = currentChat.characterName,
                    userPersonaName = currentChat.userPersonaName,
                    userPersonaDesc = currentChat.userPersonaDesc,
                    authorsNote = currentChat.authorsNote,
                    memorySummary = currentChat.memorySummary,
                    loreNotes = currentChat.loreNotes,
                    loreEntries = currentChat.loreEntries,
                    participants = currentChat.participants,
                    provider = pickedProvider,
                    modelId = pickedModelId,
                    params = currentChat.generationParams(),
                    portraitUri = currentChat.portraitUri,
                    backdropUri = currentChat.backdropUri
                )
                showModelCatalog = false
            },
            onDismiss = { showModelCatalog = false }
        )
    }

    if (showBranchTreeSheet && currentChat != null) {
        BranchTreeSheet(
            activeChat = currentChat,
            allChats = allChats,
            canCreateBranchHere = messages.isNotEmpty(),
            onDismiss = { showBranchTreeSheet = false },
            onSelectBranch = { targetChatId -> viewModel.selectChat(targetChatId) },
            onCreateBranchFromLatest = {
                messages.lastOrNull()?.let { last ->
                    viewModel.branchFromMessage(last) { }
                }
            }
        )
    }

    if (showWorldStudio && currentChat != null) {
        WorldAndCharacterStudio(
            initialState = currentChat.rpgState,
            loreEntries = currentChat.loreEntries,
            memorySummary = currentChat.memorySummary,
            initialTab = studioInitialTab,
            focusedEntityId = studioFocusId,
            onDismiss = { showWorldStudio = false },
            onSave = { newState -> viewModel.updateRpgState(newState) },
            onMoveToLocation = { nodeId -> viewModel.moveToLocation(nodeId) },
            onUpdateCombat = { combat -> viewModel.updateCombatState(combat) },
            onRollTacticalD20 = { stat, mod, purpose -> viewModel.rollTacticalD20(stat, mod, purpose) },
            onRollNpcPerception = { npcId -> viewModel.rollNpcPerception(npcId) },
            onBuyItemFromNpc = { npcId, itemName, price -> viewModel.buyItemFromNpc(npcId, itemName, price) },
            onGenerateLocationBackdrop = { locId -> viewModel.generateLocationBackdrop(locId) }
        )
    }

    val msgToEdit = editingMessage
    if (msgToEdit != null) {
        var editedText by remember(msgToEdit.id, msgToEdit.activeVariantIndex) {
            mutableStateOf(msgToEdit.activeContent())
        }
        AlertDialog(
            onDismissRequest = { editingMessage = null },
            title = { Text("Редактировать сообщение") },
            text = {
                OutlinedTextField(
                    value = editedText,
                    onValueChange = { editedText = it },
                    minLines = 4,
                    maxLines = 10,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(onClick = {
                    viewModel.editMessage(msgToEdit, editedText)
                    editingMessage = null
                }) {
                    Text("Сохранить")
                }
            },
            dismissButton = {
                TextButton(onClick = { editingMessage = null }) { Text("Отмена") }
            }
        )
    }

    val msgToDelete = deletingMessage
    if (msgToDelete != null) {
        AlertDialog(
            onDismissRequest = { deletingMessage = null },
            title = { Text("Удаление / откат сцены") },
            text = {
                Text("Удалить только это сообщение вместе со следующими за ним репликами (откат сюжета)?")
            },
            confirmButton = {
                Button(onClick = {
                    viewModel.rewindToMessage(msgToDelete, inclusiveDelete = true)
                    deletingMessage = null
                }) {
                    Text("Откатить отсюда")
                }
            },
            dismissButton = {
                TextButton(onClick = { deletingMessage = null }) { Text("Отмена") }
            }
        )
    }

    if (showReaderSettingsSheet) {
        ReaderSettingsSheet(
            settings = settings,
            onToggleBookMode = { viewModel.setReaderBookMode(it) },
            onSelectFontStyle = { viewModel.setReaderFontStyle(it) },
            onChangeFontSizeSp = { viewModel.setReaderFontSizeSp(it) },
            onChangeLineHeight = { viewModel.setReaderLineHeight(it) },
            onToggleHaptics = { viewModel.setHapticsEnabled(it) },
            onToggleAutoFailover = { viewModel.setAutoFailoverEnabled(it) },
            onDismiss = { showReaderSettingsSheet = false }
        )
    }

    if (showAmbientSheet) {
        AmbientControlSheet(
            settings = settings,
            resolvedActivePreset = activeAmbientPreset,
            isProbingLocalGguf = isProbingLocalGguf,
            localGgufServerUrl = settings.localModelProfile.serverUrl.ifBlank { LlmProvider.LOCAL_LLM.defaultBaseUrl },
            localGgufModels = localGgufModels,
            onToggleAmbient = { viewModel.setAmbientEnabled(it) },
            onSelectPreset = { preset ->
                viewModel.setAmbientPreset(preset)
                if (preset != AmbientScenePreset.SILENT && !settings.ambientEnabled) {
                    viewModel.setAmbientEnabled(true)
                }
            },
            onChangeVolume = { viewModel.setAmbientVolume(it) },
            onQuickSwitchLocalGguf = { viewModel.quickCheckAndSwitchLocalGguf() },
            onDismiss = { showAmbientSheet = false }
        )
    }

    if (showDeleteChatDialog && currentChat != null) {
        AlertDialog(
            onDismissRequest = { showDeleteChatDialog = false },
            title = { Text("Удалить чат?") },
            text = { Text("Чат «${currentChat.title}» и вся его история будут удалены.") },
            confirmButton = {
                Button(onClick = {
                    showDeleteChatDialog = false
                    viewModel.deleteChat(currentChat.id) { onBack() }
                }) {
                    Text("Удалить")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteChatDialog = false }) { Text("Отмена") }
            }
        )
    }
}
