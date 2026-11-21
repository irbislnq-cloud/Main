package com.example.ui.screens

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoStories
import androidx.compose.material.icons.filled.ChatBubble
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.AutoStories
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.DarkMode
import androidx.compose.material.icons.outlined.LightMode
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.model.RpChat
import com.example.data.model.RpScenario
import com.example.data.model.ThemeMode
import com.example.ui.components.CardImportDialog
import com.example.ui.components.QuickCreateChatDialog
import com.example.ui.viewmodel.LoreCraftViewModel

private sealed interface ActiveRoute {
    data object Hub : ActiveRoute
    data class Chat(val chatId: String) : ActiveRoute
    data class ScenarioEditor(val scenario: RpScenario?) : ActiveRoute
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeHubScreen(viewModel: LoreCraftViewModel) {
    val scenariosState by viewModel.scenariosState.collectAsStateWithLifecycle()
    val chatsState by viewModel.chatsState.collectAsStateWithLifecycle()
    val personasState by viewModel.personasState.collectAsStateWithLifecycle()
    val settings by viewModel.localSettings.collectAsStateWithLifecycle()
    val activeVmChatId by viewModel.activeChatId.collectAsStateWithLifecycle()
    val catalogModels by viewModel.catalogModelsState.collectAsStateWithLifecycle()
    val isDiscoveringModels by viewModel.isDiscoveringModels.collectAsStateWithLifecycle()
    val discoveryProgress by viewModel.discoveryProgress.collectAsStateWithLifecycle()
    val isExpandingDraft by viewModel.isExpandingDraft.collectAsStateWithLifecycle()
    val deviceRamSnapshot by viewModel.deviceRamSnapshot.collectAsStateWithLifecycle()
    val customAgentsLibrary by viewModel.customAgentsLibrary.collectAsStateWithLifecycle()

    val snackbarHostState = remember { SnackbarHostState() }
    LaunchedEffect(viewModel) {
        viewModel.toastEvents.collect { message ->
            snackbarHostState.showSnackbar(message)
        }
    }

    var route by remember { mutableStateOf<ActiveRoute>(ActiveRoute.Hub) }
    var selectedTab by rememberSaveable { mutableIntStateOf(0) }
    var showQuickChatDialog by remember { mutableStateOf(false) }
    var showCardImportDialog by remember { mutableStateOf(false) }
    var chatToDelete by remember { mutableStateOf<RpChat?>(null) }
    var scenarioToDelete by remember { mutableStateOf<RpScenario?>(null) }

    when (val currentRoute = route) {
        is ActiveRoute.Chat -> {
            LaunchedEffect(currentRoute.chatId) {
                viewModel.selectChat(currentRoute.chatId)
            }
            LaunchedEffect(activeVmChatId) {
                if (activeVmChatId != null && activeVmChatId != currentRoute.chatId) {
                    route = ActiveRoute.Chat(activeVmChatId!!)
                }
            }
            ChatScreen(
                viewModel = viewModel,
                onBack = {
                    viewModel.selectChat(null)
                    route = ActiveRoute.Hub
                }
            )
        }

        is ActiveRoute.ScenarioEditor -> {
            ScenarioEditorScreen(
                initialScenario = currentRoute.scenario,
                defaultProvider = settings.defaultProvider,
                defaultModelId = settings.defaultModelId,
                imageEndpointTemplate = settings.imageEndpointTemplate,
                freeOnlyFilter = settings.freeOnlyFilter,
                isExpandingWithAi = isExpandingDraft,
                customAgentsLibrary = customAgentsLibrary,
                onSaveCustomAgent = { viewModel.saveCustomAgentToLibrary(it) },
                onDeleteCustomAgent = { viewModel.deleteCustomAgentFromLibrary(it) },
                localLlmEnabled = settings.localLlmEnabled,
                onExpandWithAi = { title, sub, mode, sys, bio, first, idea, cb ->
                    val combinedIdea = listOf(idea, bio).filter { it.isNotBlank() }.joinToString("\n")
                    viewModel.expandScenarioDraft(
                        titleHint = title,
                        subtitleHint = sub,
                        rpMode = mode,
                        systemPromptHint = sys,
                        firstMessageHint = first,
                        freeformIdea = combinedIdea,
                        onResult = cb
                    )
                },
                catalogModels = catalogModels,
                isDiscoveringModels = isDiscoveringModels,
                discoveryProgress = discoveryProgress,
                onDiscoverFreeModels = { viewModel.refreshModelCatalog(probeFreeLive = true) },
                onProbeModel = { viewModel.probeSingleModel(it) },
                onNotify = { viewModel.notifyMessage(it) },
                onBack = { route = ActiveRoute.Hub },
                onSave = { scenario ->
                    viewModel.saveScenario(scenario) {
                        route = ActiveRoute.Hub
                    }
                }
            )
        }

        ActiveRoute.Hub -> {
            BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                val useRail = maxWidth >= 600.dp

                Scaffold(
                    contentWindowInsets = WindowInsets.safeDrawing,
                    snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
                    topBar = {
                        TopAppBar(
                            title = {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp)
                                ) {
                                    Text(
                                        text = "LoreCraft",
                                        style = MaterialTheme.typography.headlineMedium
                                    )
                                    if (viewModel.isOfflineMode) {
                                        androidx.compose.material3.Surface(
                                            shape = androidx.compose.foundation.shape.RoundedCornerShape(8.dp),
                                            color = MaterialTheme.colorScheme.secondaryContainer,
                                            modifier = Modifier.testTag("hub_offline_badge")
                                        ) {
                                            Text(
                                                text = "Оффлайн",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSecondaryContainer,
                                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                            )
                                        }
                                    }
                                }
                            },
                            actions = {
                                IconButton(
                                    onClick = {
                                        val nextTheme = when (settings.themeMode) {
                                            ThemeMode.DARK -> ThemeMode.LIGHT
                                            ThemeMode.LIGHT -> ThemeMode.DARK
                                            ThemeMode.SYSTEM -> ThemeMode.DARK
                                        }
                                        viewModel.setThemeMode(nextTheme)
                                    },
                                    modifier = Modifier.testTag("quick_theme_toggle")
                                ) {
                                    Icon(
                                        imageVector = if (settings.themeMode == ThemeMode.DARK) {
                                            Icons.Outlined.LightMode
                                        } else {
                                            Icons.Outlined.DarkMode
                                        },
                                        contentDescription = "Переключить тему"
                                    )
                                }
                            }
                        )
                    },
                    floatingActionButton = {
                        if (selectedTab == 0 || selectedTab == 1) {
                            FloatingActionButton(
                                onClick = {
                                    if (selectedTab == 0) {
                                        showQuickChatDialog = true
                                    } else {
                                        route = ActiveRoute.ScenarioEditor(null)
                                    }
                                },
                                containerColor = MaterialTheme.colorScheme.primary,
                                contentColor = MaterialTheme.colorScheme.onPrimary,
                                modifier = Modifier.testTag("hub_fab")
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.Add,
                                    contentDescription = if (selectedTab == 0) "Новый RP-чат" else "Новый сценарий"
                                )
                            }
                        }
                    },
                    bottomBar = {
                        if (!useRail) {
                            NavigationBar {
                                HubNavItems().forEachIndexed { idx, item ->
                                    NavigationBarItem(
                                        selected = selectedTab == idx,
                                        onClick = { selectedTab = idx },
                                        icon = {
                                            Icon(
                                                imageVector = if (selectedTab == idx) item.selectedIcon else item.unselectedIcon,
                                                contentDescription = item.label
                                            )
                                        },
                                        label = { Text(item.label) },
                                        modifier = Modifier.testTag("nav_tab_$idx")
                                    )
                                }
                            }
                        }
                    }
                ) { innerPadding ->
                    Row(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(innerPadding)
                    ) {
                        if (useRail) {
                            NavigationRail {
                                HubNavItems().forEachIndexed { idx, item ->
                                    NavigationRailItem(
                                        selected = selectedTab == idx,
                                        onClick = { selectedTab = idx },
                                        icon = {
                                            Icon(
                                                imageVector = if (selectedTab == idx) item.selectedIcon else item.unselectedIcon,
                                                contentDescription = item.label
                                            )
                                        },
                                        label = { Text(item.label) },
                                        modifier = Modifier.testTag("rail_tab_$idx")
                                    )
                                }
                            }
                        }

                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.TopCenter
                        ) {
                            Box(
                                modifier = Modifier
                                    .widthIn(max = 860.dp)
                                    .fillMaxSize()
                            ) {
                                when (selectedTab) {
                                    0 -> ChatsTab(
                                        chatsState = chatsState,
                                        settings = settings,
                                        onOpenChat = { chatId ->
                                            route = ActiveRoute.Chat(chatId)
                                        },
                                        onQuickCreateChat = { showQuickChatDialog = true },
                                        onGoToScenarios = { selectedTab = 1 },
                                        onTogglePin = { viewModel.toggleChatPin(it) },
                                        onDeleteChat = { chatToDelete = it }
                                    )
                                    1 -> ScenariosTab(
                                        scenariosState = scenariosState,
                                        onCreateNewScenario = {
                                            route = ActiveRoute.ScenarioEditor(null)
                                        },
                                        onImportCard = { showCardImportDialog = true },
                                        onEditScenario = { scenario ->
                                            route = ActiveRoute.ScenarioEditor(scenario)
                                        },
                                        onLaunchScenario = { scenario ->
                                            viewModel.startChatFromScenario(scenario) { chatId ->
                                                route = ActiveRoute.Chat(chatId)
                                            }
                                        },
                                        onTogglePin = { viewModel.toggleScenarioPin(it) },
                                        onDeleteScenario = { scenarioToDelete = it }
                                    )
                                    2 -> ModelsAndTuningTab(
                                        settings = settings,
                                        onSetDefaultModel = { p, m, applyAll -> viewModel.setDefaultModel(p, m, applyAll) },
                                        onSaveProviderConfig = { viewModel.saveProviderConfig(it) },
                                        onSaveDefaultParams = { viewModel.saveDefaultParams(it) },
                                        onSetStreaming = { viewModel.setStreamingEnabled(it) },
                                        onSetShowBadges = { viewModel.setShowModelBadges(it) },
                                        onSetFreeOnlyFilter = { viewModel.setFreeOnlyFilter(it) },
                                        onSetSafetyLevel = { viewModel.setSafetyLevel(it) },
                                        onSetCreativeRpFraming = { viewModel.setCreativeRpFraming(it) },
                                        deviceRamSnapshot = deviceRamSnapshot,
                                        onRefreshRam = { viewModel.refreshRamSnapshot() },
                                        onSetLocalLlmEnabled = { viewModel.setLocalLlmEnabled(it) },
                                        onSetDefaultAgentPreset = { viewModel.setDefaultAgentPresetId(it) },
                                        onSetAutoSuggestActions = { viewModel.setAutoSuggestActions(it) },
                                        onSetAutoSendSuggestedAction = { viewModel.setAutoSendSuggestedAction(it) },
                                        onSetAutoSummarizeEveryN = { viewModel.setAutoSummarizeEveryN(it) },
                                        catalogModels = catalogModels,
                                        isDiscoveringModels = isDiscoveringModels,
                                        discoveryProgress = discoveryProgress,
                                        onDiscoverFreeModels = { viewModel.refreshModelCatalog(probeFreeLive = true) },
                                        onProbeModel = { viewModel.probeSingleModel(it) },
                                        onSaveLocalModelProfile = { viewModel.saveLocalModelProfile(it) },
                                        onSaveImageEndpoint = { viewModel.saveImageEndpointTemplate(it) },
                                        onTestServer = { prov, url, key, cb ->
                                            viewModel.testLocalServer(prov, url, key, cb)
                                        },
                                        onTestChatCompletion = { prov, url, key, model, cb ->
                                            viewModel.testProviderConnection(prov, url, key, model, cb)
                                        },
                                        onToast = { viewModel.notifyMessage(it) }
                                    )
                                    3 -> ProfileAndExportTab(
                                        viewModel = viewModel,
                                        settings = settings,
                                        personasState = personasState
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showQuickChatDialog) {
        QuickCreateChatDialog(
            initialAutoFillWithAi = settings.autoFillNewChatWithAi,
            isExpandingWithAi = isExpandingDraft,
            defaultAgentPresetId = settings.defaultAgentPresetId,
            customAgentsLibrary = customAgentsLibrary,
            onSaveCustomAgent = { viewModel.saveCustomAgentToLibrary(it) },
            onDeleteCustomAgent = { viewModel.deleteCustomAgentFromLibrary(it) },
            localLlmEnabled = settings.localLlmEnabled,
            freeOnlyFilter = settings.freeOnlyFilter,
            onExpandWithAi = { title, mode, charName, sysPrompt, firstMsg, idea, cb ->
                viewModel.expandScenarioDraft(
                    titleHint = title,
                    rpMode = mode,
                    characterHint = charName,
                    systemPromptHint = sysPrompt,
                    firstMessageHint = firstMsg,
                    freeformIdea = idea,
                    onResult = cb
                )
            },
            onAutoFillPreferenceChange = { viewModel.setAutoFillNewChatWithAi(it) },
            onDismiss = { showQuickChatDialog = false },
            onCreate = { title, mode, charName, sysPrompt, firstMsg, idea, autoFill, preExpanded, agentSetup ->
                if (!autoFill || preExpanded != null) {
                    showQuickChatDialog = false
                }
                viewModel.createQuickCustomChat(
                    title = title,
                    rpMode = mode,
                    characterName = charName,
                    systemPrompt = sysPrompt,
                    firstMessage = firstMsg,
                    freeformIdea = idea,
                    autoFillWithAi = autoFill,
                    preExpandedDraft = preExpanded,
                    agentSetup = agentSetup
                ) { chatId ->
                    showQuickChatDialog = false
                    route = ActiveRoute.Chat(chatId)
                }
            }
        )
    }

    if (showCardImportDialog) {
        CardImportDialog(
            onDismiss = { showCardImportDialog = false },
            onFetchFromUrl = { url, onCard, onWorkspace, onErr ->
                viewModel.importCharacterCardFromUrl(
                    url = url,
                    onCardReady = onCard,
                    onWorkspaceImported = onWorkspace,
                    onError = onErr
                )
            },
            onSaveScenario = { scenario, launchImmediately ->
                viewModel.saveImportedScenario(scenario, launchImmediately) { chatId ->
                    route = ActiveRoute.Chat(chatId)
                }
            }
        )
    }

    val pendingDelChat = chatToDelete
    if (pendingDelChat != null) {
        AlertDialog(
            onDismissRequest = { chatToDelete = null },
            title = { Text("Удалить чат?") },
            text = { Text("Чат «${pendingDelChat.title}» и все сообщения будут удалены.") },
            confirmButton = {
                Button(onClick = {
                    viewModel.deleteChat(pendingDelChat.id)
                    chatToDelete = null
                }) {
                    Text("Удалить")
                }
            },
            dismissButton = {
                TextButton(onClick = { chatToDelete = null }) { Text("Отмена") }
            }
        )
    }

    val pendingDelScenario = scenarioToDelete
    if (pendingDelScenario != null) {
        AlertDialog(
            onDismissRequest = { scenarioToDelete = null },
            title = { Text("Удалить сценарий?") },
            text = { Text("Сценарий «${pendingDelScenario.title}» будет удалён из вашей библиотеки.") },
            confirmButton = {
                Button(onClick = {
                    viewModel.deleteScenario(pendingDelScenario.id)
                    scenarioToDelete = null
                }) {
                    Text("Удалить")
                }
            },
            dismissButton = {
                TextButton(onClick = { scenarioToDelete = null }) { Text("Отмена") }
            }
        )
    }
}

private data class HubNavItem(
    val label: String,
    val selectedIcon: androidx.compose.ui.graphics.vector.ImageVector,
    val unselectedIcon: androidx.compose.ui.graphics.vector.ImageVector
)

private fun HubNavItems(): List<HubNavItem> = listOf(
    HubNavItem("Чаты", Icons.Filled.ChatBubble, Icons.Outlined.ChatBubbleOutline),
    HubNavItem("Сценарии", Icons.Filled.AutoStories, Icons.Outlined.AutoStories),
    HubNavItem("Модели", Icons.Filled.Tune, Icons.Outlined.Tune),
    HubNavItem("Профиль", Icons.Filled.Person, Icons.Outlined.Person)
)
