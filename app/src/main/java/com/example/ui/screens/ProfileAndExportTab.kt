package com.example.ui.screens

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Login
import androidx.compose.material.icons.automirrored.outlined.Logout
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.BugReport
import androidx.compose.material.icons.outlined.CloudDone
import androidx.compose.material.icons.outlined.CloudDownload
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material.icons.outlined.CloudUpload
import androidx.compose.material.icons.outlined.Code
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.DarkMode
import androidx.compose.material.icons.outlined.DataObject
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.FileUpload
import androidx.compose.material.icons.outlined.LightMode
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.SettingsBrightness
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
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
import androidx.credentials.CredentialManager
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.data.export.JsonExportManager
import com.example.data.firebase.authStateFlow
import com.example.data.firebase.onGoogleSignInClicked
import com.example.data.firebase.signOut
import com.example.data.local.AppLocalSettings
import com.example.data.model.ThemeMode
import com.example.data.model.UserPersona
import com.example.diagnostics.AppLogBuffer
import com.example.diagnostics.CrashLogStore
import com.example.ui.components.CharacterProfilesSection
import com.example.ui.components.DiagnosticsDialog
import com.example.ui.viewmodel.CharacterProfileViewModel
import com.example.ui.viewmodel.LoreCraftViewModel
import com.example.ui.viewmodel.UiState
import com.google.firebase.Firebase
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.auth

@Composable
fun ProfileAndExportTab(
    viewModel: LoreCraftViewModel,
    settings: AppLocalSettings,
    personasState: UiState<List<UserPersona>>
) {
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val scope = rememberCoroutineScope()
    val characterProfileViewModel: CharacterProfileViewModel = viewModel(
        factory = CharacterProfileViewModel.factory(context)
    )
    val credentialManager = remember(context) {
        runCatching { CredentialManager.create(context) }.getOrNull()
    }
    val firebaseInitialized = remember(context) {
        runCatching { FirebaseApp.getApps(context).isNotEmpty() }.getOrDefault(false)
    }
    val user by produceState<FirebaseUser?>(
        initialValue = if (firebaseInitialized) runCatching { Firebase.auth.currentUser }.getOrNull() else null,
        key1 = firebaseInitialized
    ) {
        if (!firebaseInitialized) {
            value = null
            return@produceState
        }
        runCatching {
            Firebase.auth.authStateFlow().collect { u -> value = u }
        }.onFailure {
            value = null
        }
    }

    var isSigningInCloud by remember { mutableStateOf(false) }
    var cloudAuthError by remember { mutableStateOf<String?>(null) }

    var pendingExportJson by remember { mutableStateOf<String?>(null) }
    var showPersonaDialog by remember { mutableStateOf(false) }
    var showDiagnosticsDialog by remember { mutableStateOf(false) }

    var patInput by remember(settings.githubPat) { mutableStateOf(settings.githubPat) }
    var showPat by remember { mutableStateOf(false) }
    var gistPublic by remember { mutableStateOf(false) }
    var isPublishingGist by remember { mutableStateOf(false) }
    var lastGistUrl by remember { mutableStateOf<String?>(null) }
    var gistImportUrl by remember { mutableStateOf("") }
    var isImportingUrl by remember { mutableStateOf(false) }

    val hasSavedCrash = remember { CrashLogStore.readLastCrash(context) != null }
    val logCount = remember { AppLogBuffer.snapshot().size }

    val saveBackupLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        val json = pendingExportJson
        pendingExportJson = null
        if (uri != null && json != null) {
            JsonExportManager.writeJsonToUri(context, uri, json)
                .onSuccess { viewModel.notifyMessage("Полный бэкап сохранён в JSON") }
                .onFailure { viewModel.notifyMessage(it.localizedMessage ?: "Ошибка записи файла") }
        }
    }

    val importJsonLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            JsonExportManager.readTextFromUri(context, uri)
                .onSuccess { jsonText ->
                    viewModel.importFromJson(jsonText)
                }
                .onFailure { viewModel.notifyMessage(it.localizedMessage ?: "Не удалось прочитать JSON") }
        }
    }

    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
        modifier = Modifier.fillMaxSize()
    ) {
        item {
            Text("Профиль, тема и диагностика", style = MaterialTheme.typography.headlineMedium)
        }

        // Cloud Account / Offline Guest Mode Card
        item {
            Surface(
                shape = MaterialTheme.shapes.large,
                color = MaterialTheme.colorScheme.surfaceContainer,
                tonalElevation = 2.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    val isOffline = viewModel.isOfflineMode || user == null
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Surface(
                            shape = MaterialTheme.shapes.medium,
                            color = MaterialTheme.colorScheme.primaryContainer,
                            modifier = Modifier.size(48.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Person,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.padding(10.dp)
                            )
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = if (isOffline) {
                                    "Автономный профиль (Локальная БД)"
                                } else {
                                    user?.displayName?.takeIf { it.isNotBlank() } ?: "Google Пользователь"
                                },
                                style = MaterialTheme.typography.titleMedium
                            )
                            Text(
                                text = if (isOffline) {
                                    "Чаты и сценарии хранятся в Room на телефоне"
                                } else {
                                    user?.email ?: viewModel.currentUserId
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Surface(
                            shape = MaterialTheme.shapes.small,
                            color = if (isOffline) {
                                MaterialTheme.colorScheme.secondaryContainer
                            } else {
                                MaterialTheme.colorScheme.tertiaryContainer
                            }
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Icon(
                                    imageVector = if (isOffline) Icons.Outlined.CloudOff else Icons.Outlined.CloudDone,
                                    contentDescription = null,
                                    tint = if (isOffline) {
                                        MaterialTheme.colorScheme.onSecondaryContainer
                                    } else {
                                        MaterialTheme.colorScheme.onTertiaryContainer
                                    },
                                    modifier = Modifier.size(14.dp)
                                )
                                Text(
                                    text = if (isOffline) "Оффлайн" else "Синхр.",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (isOffline) {
                                        MaterialTheme.colorScheme.onSecondaryContainer
                                    } else {
                                        MaterialTheme.colorScheme.onTertiaryContainer
                                    }
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
                            Text("Запускать сразу оффлайн", style = MaterialTheme.typography.bodyMedium)
                            Text(
                                "Открывать студию без экрана входа Google",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = settings.localModelProfile.offlineModePreferred,
                            onCheckedChange = { viewModel.setOfflineModePreferred(it) },
                            modifier = Modifier.testTag("offline_preferred_switch")
                        )
                    }

                    if (!viewModel.firebaseError.isNullOrBlank()) {
                        Surface(
                            shape = MaterialTheme.shapes.small,
                            color = MaterialTheme.colorScheme.errorContainer
                        ) {
                            Text(
                                text = viewModel.firebaseError,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onErrorContainer,
                                modifier = Modifier.padding(10.dp)
                            )
                        }
                    }

                    if (!cloudAuthError.isNullOrBlank()) {
                        Surface(
                            shape = MaterialTheme.shapes.small,
                            color = MaterialTheme.colorScheme.errorContainer,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier.padding(10.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Text(
                                    text = cloudAuthError!!,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onErrorContainer
                                )
                                TextButton(onClick = { showDiagnosticsDialog = true }) {
                                    Text("Проверить SHA-1 и логи")
                                }
                            }
                        }
                    }

                    if (isOffline) {
                        Button(
                            onClick = {
                                cloudAuthError = null
                                if (user != null) {
                                    viewModel.requestCloudSignIn()
                                } else {
                                    isSigningInCloud = true
                                    onGoogleSignInClicked(
                                        context = context,
                                        credentialManager = credentialManager,
                                        onAuthSuccess = {
                                            isSigningInCloud = false
                                            viewModel.notifyMessage("Вход в облако Google выполнен")
                                            viewModel.requestCloudSignIn()
                                        },
                                        onAuthError = { err ->
                                            isSigningInCloud = false
                                            cloudAuthError = err
                                        },
                                        scope = scope,
                                        onAuthCancelled = { hint ->
                                            isSigningInCloud = false
                                            cloudAuthError = hint ?: "Вход через Google отменён."
                                        }
                                    )
                                }
                            },
                            enabled = !isSigningInCloud && firebaseInitialized,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("switch_to_cloud_button")
                        ) {
                            if (isSigningInCloud) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(18.dp),
                                    color = MaterialTheme.colorScheme.onPrimary,
                                    strokeWidth = 2.dp
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Открытие окна Google...")
                            } else {
                                Icon(Icons.AutoMirrored.Outlined.Login, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    if (user != null) {
                                        "Включить облачную синхронизацию"
                                    } else {
                                        "Войти / Регистрация в Google Cloud"
                                    }
                                )
                            }
                        }
                        Text(
                            text = "Отдельная регистрация не нужна — профиль создаётся автоматически при входе через Google-аккаунт на устройстве.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        OutlinedButton(
                            onClick = {
                                signOut(credentialManager, scope) {
                                    viewModel.notifyMessage("Вы вышли из облачного аккаунта")
                                }
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("sign_out_button")
                        ) {
                            Icon(Icons.AutoMirrored.Outlined.Logout, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Выйти из аккаунта")
                        }
                    }
                }
            }
        }

        // Diagnostics & Crash Logs Card
        item {
            Surface(
                shape = MaterialTheme.shapes.large,
                color = MaterialTheme.colorScheme.surfaceContainer,
                tonalElevation = 1.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.BugReport,
                            contentDescription = null,
                            tint = if (hasSavedCrash) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                        )
                        Text("Диагностика и журнал сбоев", style = MaterialTheme.typography.titleMedium)
                    }
                    Text(
                        text = if (hasSavedCrash) {
                            "Обнаружен сохранённый отчёт о предыдущем сбое. Событий в буфере: $logCount."
                        } else {
                            "Журнал запуска, Firebase, сети и локальных моделей (событий: $logCount)."
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    OutlinedButton(
                        onClick = { showDiagnosticsDialog = true },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("open_diagnostics_button")
                    ) {
                        Icon(Icons.Outlined.BugReport, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Открыть логи и отчёт устройства")
                    }
                }
            }
        }

        // Theme Mode Card
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
                    Text("Оформление интерфейса", style = MaterialTheme.typography.titleMedium)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        ThemeMode.entries.forEach { mode ->
                            val icon = when (mode) {
                                ThemeMode.SYSTEM -> Icons.Outlined.SettingsBrightness
                                ThemeMode.DARK -> Icons.Outlined.DarkMode
                                ThemeMode.LIGHT -> Icons.Outlined.LightMode
                            }
                            FilterChip(
                                selected = settings.themeMode == mode,
                                onClick = { viewModel.setThemeMode(mode) },
                                leadingIcon = {
                                    Icon(icon, contentDescription = null, modifier = Modifier.size(16.dp))
                                },
                                label = { Text(mode.labelRu) },
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("theme_chip_${mode.name}")
                            )
                        }
                    }
                }
            }
        }

        // GitHub & Gist Portability Card
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
                        Icon(Icons.Outlined.Code, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Text("GitHub & Gist портативность", style = MaterialTheme.typography.titleMedium)
                    }
                    Text(
                        text = "Публикуйте пакеты сценариев и бэкапы в GitHub Gist или загружайте сценарии по Raw/Gist ссылке.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedTextField(
                            value = patInput,
                            onValueChange = { patInput = it },
                            label = { Text("GitHub Token (scope: gist)") },
                            placeholder = { Text("ghp_...") },
                            singleLine = true,
                            visualTransformation = if (showPat) VisualTransformation.None else PasswordVisualTransformation(),
                            trailingIcon = {
                                IconButton(onClick = { showPat = !showPat }) {
                                    Icon(
                                        imageVector = if (showPat) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility,
                                        contentDescription = "Показать или скрыть токен"
                                    )
                                }
                            },
                            modifier = Modifier
                                .weight(1f)
                                .testTag("github_pat_input")
                        )
                        OutlinedButton(
                            onClick = { viewModel.saveGithubPat(patInput) },
                            modifier = Modifier
                                .height(54.dp)
                                .testTag("save_github_pat_button")
                        ) {
                            Text("Ок")
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Публичный Gist (иначе приватный)", style = MaterialTheme.typography.bodySmall)
                        Switch(checked = gistPublic, onCheckedChange = { gistPublic = it })
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = {
                                isPublishingGist = true
                                viewModel.publishToGitHubGist(
                                    description = "LoreCraft Scenarios Pack",
                                    scenariosOnly = true,
                                    isPublic = gistPublic
                                ) { res ->
                                    isPublishingGist = false
                                    res.onSuccess { published ->
                                        lastGistUrl = published.htmlUrl
                                        viewModel.notifyMessage("Пакет сценариев опубликован в Gist")
                                    }.onFailure { err ->
                                        viewModel.notifyMessage(err.localizedMessage ?: "Ошибка Gist")
                                    }
                                }
                            },
                            enabled = !isPublishingGist,
                            modifier = Modifier
                                .weight(1f)
                                .testTag("publish_scenarios_gist_button")
                        ) {
                            if (isPublishingGist) {
                                CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                            } else {
                                Icon(Icons.Outlined.CloudUpload, contentDescription = null, modifier = Modifier.size(18.dp))
                            }
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Gist: Сценарии")
                        }

                        OutlinedButton(
                            onClick = {
                                isPublishingGist = true
                                viewModel.publishToGitHubGist(
                                    description = "LoreCraft Full Workspace Backup",
                                    scenariosOnly = false,
                                    isPublic = gistPublic
                                ) { res ->
                                    isPublishingGist = false
                                    res.onSuccess { published ->
                                        lastGistUrl = published.htmlUrl
                                        viewModel.notifyMessage("Полный бэкап опубликован в Gist")
                                    }.onFailure { err ->
                                        viewModel.notifyMessage(err.localizedMessage ?: "Ошибка Gist")
                                    }
                                }
                            },
                            enabled = !isPublishingGist,
                            modifier = Modifier
                                .weight(1f)
                                .testTag("publish_backup_gist_button")
                        ) {
                            Icon(Icons.Outlined.CloudUpload, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Gist: Бэкап")
                        }
                    }

                    if (lastGistUrl != null) {
                        Surface(
                            shape = MaterialTheme.shapes.small,
                            color = MaterialTheme.colorScheme.tertiaryContainer,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = lastGistUrl!!,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onTertiaryContainer,
                                    modifier = Modifier.weight(1f)
                                )
                                IconButton(onClick = {
                                    clipboard.setText(AnnotatedString(lastGistUrl!!))
                                    viewModel.notifyMessage("Ссылка на Gist скопирована")
                                }) {
                                    Icon(Icons.Outlined.ContentCopy, contentDescription = "Копировать ссылку")
                                }
                            }
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedTextField(
                            value = gistImportUrl,
                            onValueChange = { gistImportUrl = it },
                            label = { Text("Импорт по Gist / GitHub URL") },
                            placeholder = { Text("https://gist.github.com/...") },
                            singleLine = true,
                            modifier = Modifier
                                .weight(1f)
                                .testTag("gist_import_url_input")
                        )
                        OutlinedButton(
                            onClick = {
                                isImportingUrl = true
                                viewModel.importCharacterCardFromUrl(
                                    url = gistImportUrl,
                                    onCardReady = { parsed ->
                                        isImportingUrl = false
                                        viewModel.saveImportedScenario(parsed.scenario, launchImmediately = false)
                                        gistImportUrl = ""
                                    },
                                    onWorkspaceImported = {
                                        isImportingUrl = false
                                        gistImportUrl = ""
                                    },
                                    onError = { err ->
                                        isImportingUrl = false
                                        viewModel.notifyMessage(err)
                                    }
                                )
                            },
                            enabled = gistImportUrl.isNotBlank() && !isImportingUrl,
                            modifier = Modifier
                                .height(54.dp)
                                .testTag("gist_import_button")
                        ) {
                            Icon(Icons.Outlined.CloudDownload, contentDescription = "Импортировать по URL")
                        }
                    }
                }
            }
        }

        // JSON Export / Import Card
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
                    Text("Экспорт и импорт данных (JSON)", style = MaterialTheme.typography.titleMedium)
                    Text(
                        text = "Сохраните все сценарии, персоны, историю чатов, свайпы и RPG-листы в переносимый JSON-файл.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Button(
                        onClick = {
                            viewModel.exportFullWorkspaceJson { fileName, json ->
                                pendingExportJson = json
                                saveBackupLauncher.launch(fileName)
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("export_all_json_button")
                    ) {
                        Icon(Icons.Outlined.DataObject, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Сохранить всё в JSON-файл")
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = {
                                viewModel.exportFullWorkspaceJson { fileName, json ->
                                    val shareIntent = JsonExportManager.buildShareIntent(context, fileName, json)
                                    context.startActivity(Intent.createChooser(shareIntent, "Поделиться JSON бэкапом"))
                                }
                            },
                            modifier = Modifier
                                .weight(1f)
                                .testTag("share_all_json_button")
                        ) {
                            Icon(Icons.Outlined.Share, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Поделиться")
                        }
                        OutlinedButton(
                            onClick = { importJsonLauncher.launch(arrayOf("application/json", "text/*")) },
                            modifier = Modifier
                                .weight(1f)
                                .testTag("import_json_button")
                        ) {
                            Icon(Icons.Outlined.FileUpload, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Импорт JSON")
                        }
                    }
                }
            }
        }

        // Room Character Profiles Card (name, bio, personality traits)
        item {
            CharacterProfilesSection(
                profileViewModel = characterProfileViewModel,
                onApplyToPersona = { profile ->
                    val combinedDesc = buildString {
                        if (profile.bio.isNotBlank()) append(profile.bio.trim())
                        if (profile.personalityTraits.isNotBlank()) {
                            if (isNotEmpty()) append("\n")
                            append("Черты характера: ")
                            append(profile.personalityTraits.trim())
                        }
                    }
                    viewModel.savePersona(
                        UserPersona(
                            name = profile.name,
                            description = combinedDesc,
                            preferredStyle = profile.personalityTraits.take(120),
                            activeDefault = false
                        )
                    )
                    viewModel.notifyMessage("Профиль «${profile.name}» добавлен в RP-персоны")
                }
            )
        }

        // User Personas Section
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
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Ваши RP-персоны", style = MaterialTheme.typography.titleMedium)
                            Text(
                                text = "Подставляются как {{user}} при старте чатов",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        OutlinedButton(
                            onClick = { showPersonaDialog = true },
                            modifier = Modifier.testTag("add_persona_button")
                        ) {
                            Icon(Icons.Outlined.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Персона")
                        }
                    }

                    val list = (personasState as? UiState.Success)?.data.orEmpty()
                    if (list.isEmpty()) {
                        Text(
                            text = "Нет сохранённых персон. По умолчанию используется «Странник».",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        list.forEach { persona ->
                            Surface(
                                shape = MaterialTheme.shapes.medium,
                                color = if (persona.activeDefault) {
                                    MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)
                                } else {
                                    MaterialTheme.colorScheme.surfaceContainerLow
                                },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = persona.name,
                                            style = MaterialTheme.typography.titleSmall
                                        )
                                        if (persona.description.isNotBlank()) {
                                            Text(
                                                text = persona.description,
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                maxLines = 2
                                            )
                                        }
                                    }
                                    IconButton(onClick = { viewModel.setDefaultPersona(persona.id) }) {
                                        Icon(
                                            imageVector = Icons.Outlined.Star,
                                            contentDescription = "Сделать основной",
                                            tint = if (persona.activeDefault) {
                                                MaterialTheme.colorScheme.secondary
                                            } else {
                                                MaterialTheme.colorScheme.onSurfaceVariant
                                            }
                                        )
                                    }
                                    IconButton(onClick = { viewModel.deletePersona(persona.id) }) {
                                        Icon(
                                            imageVector = Icons.Outlined.DeleteOutline,
                                            contentDescription = "Удалить персону"
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        item { Spacer(modifier = Modifier.height(72.dp)) }
    }

    if (showDiagnosticsDialog) {
        DiagnosticsDialog(onDismiss = { showDiagnosticsDialog = false })
    }

    if (showPersonaDialog) {
        var name by remember { mutableStateOf("") }
        var desc by remember { mutableStateOf("") }
        var style by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showPersonaDialog = false },
            title = { Text("Новая персона игрока") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        label = { Text("Имя персоны *") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = desc,
                        onValueChange = { desc = it },
                        label = { Text("Внешность, способности, предыстория") },
                        minLines = 3,
                        maxLines = 5,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = style,
                        onValueChange = { style = it },
                        label = { Text("Предпочитаемый стиль речи/лица") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.savePersona(
                            UserPersona(
                                name = name.trim().ifEmpty { "Странник" },
                                description = desc.trim(),
                                preferredStyle = style.trim(),
                                activeDefault = false
                            )
                        )
                        showPersonaDialog = false
                    }
                ) {
                    Text("Сохранить")
                }
            },
            dismissButton = {
                TextButton(onClick = { showPersonaDialog = false }) { Text("Отмена") }
            }
        )
    }
}
