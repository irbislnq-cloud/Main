package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.data.ai.LlmRouterService
import com.example.data.firebase.FirebaseHealthStatus
import com.example.data.firebase.authStateFlow
import com.example.data.firebase.configureAppCheck
import com.example.data.local.AppLocalSettings
import com.example.data.local.PreferencesRepository
import com.example.data.local.cache.OfflineLocalRpRepository
import com.example.data.local.cache.OfflineRpDatabase
import com.example.data.repository.FirestoreRpRepository
import com.example.data.repository.HybridRpRepository
import com.example.device.AmbientSoundEngine
import com.example.diagnostics.AppLogBuffer
import com.example.diagnostics.CrashActivity
import com.example.ui.screens.AuthScreen
import com.example.ui.screens.HomeHubScreen
import com.example.ui.theme.LoreCraftTheme
import com.example.ui.viewmodel.LoreCraftViewModel
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val safeMode = intent?.getBooleanExtra(CrashActivity.EXTRA_SAFE_MODE, false) ?: false
        AppLogBuffer.i("MainActivity", "onCreate (safeMode=$safeMode)")

        val firebaseHealth = configureAppCheck(this, intent)

        enableEdgeToEdge()

        setContent {
            LoreCraftRoot(
                firebaseHealth = firebaseHealth,
                launchedInSafeMode = safeMode
            )
        }
    }
}

@Composable
fun LoreCraftRoot(
    firebaseHealth: FirebaseHealthStatus,
    launchedInSafeMode: Boolean = false
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val preferencesRepository = remember(context) {
        PreferencesRepository(context.applicationContext)
    }
    val localSettings by preferencesRepository.settingsFlow
        .collectAsStateWithLifecycle(initialValue = AppLocalSettings())

    val currentUser by produceState<FirebaseUser?>(
        initialValue = if (firebaseHealth.isReady) runCatching { Firebase.auth.currentUser }.getOrNull() else null,
        key1 = firebaseHealth.isReady
    ) {
        if (!firebaseHealth.isReady) {
            value = null
            return@produceState
        }
        runCatching {
            Firebase.auth.authStateFlow().collect { user ->
                value = user
            }
        }.onFailure { err ->
            AppLogBuffer.e("MainActivity", "Auth flow observation failed", err)
            value = null
        }
    }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> AmbientSoundEngine.onAppForegrounded()
                Lifecycle.Event.ON_STOP -> AmbientSoundEngine.onAppBackgrounded()
                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            AmbientSoundEngine.release()
        }
    }

    var manualOfflineSession by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(launchedInSafeMode) {
        if (launchedInSafeMode) {
            manualOfflineSession = true
        }
    }

    val offlineRequested = manualOfflineSession ||
        localSettings.localModelProfile.offlineModePreferred ||
        !firebaseHealth.isReady

    LoreCraftTheme(themeMode = localSettings.themeMode) {
        val user = currentUser
        if (user == null && !offlineRequested) {
            AuthScreen(
                firebaseHealth = firebaseHealth,
                onAuthSuccess = {
                    scope.launch(kotlinx.coroutines.NonCancellable) {
                        preferencesRepository.setOfflineModePreferred(false)
                        manualOfflineSession = false
                    }
                },
                onEnterOfflineMode = {
                    manualOfflineSession = true
                    scope.launch { preferencesRepository.setOfflineModePreferred(true) }
                }
            )
        } else {
            val useOfflineRepo = user == null || !firebaseHealth.isReady || manualOfflineSession ||
                localSettings.localModelProfile.offlineModePreferred
            val effectiveUid = if (useOfflineRepo) {
                OfflineLocalRpRepository.LOCAL_OFFLINE_USER_ID
            } else {
                user!!.uid
            }

            key(effectiveUid, useOfflineRepo) {
                val vm: LoreCraftViewModel = viewModel(
                    key = "${effectiveUid}_$useOfflineRepo",
                    factory = viewModelFactory {
                        initializer {
                            val app = checkNotNull(this[APPLICATION_KEY]) {
                                "APPLICATION_KEY missing from CreationExtras"
                            }
                            val localDb = OfflineRpDatabase.getInstance(app)
                            val offlineRepo = OfflineLocalRpRepository(localDb.offlineRpDao())
                            val chosenRepo = if (useOfflineRepo) {
                                offlineRepo
                            } else {
                                val databaseId = app.getString(R.string.firestore_database_id)
                                val db = FirebaseFirestore.getInstance(databaseId)
                                HybridRpRepository(
                                    remote = FirestoreRpRepository(db),
                                    localCache = offlineRepo
                                )
                            }
                            LoreCraftViewModel(
                                repository = chosenRepo,
                                preferencesRepository = PreferencesRepository(app),
                                llmRouter = LlmRouterService(),
                                currentUserId = effectiveUid,
                                isOfflineMode = useOfflineRepo,
                                firebaseError = firebaseHealth.errorSummary,
                                appContext = app.applicationContext,
                                onSwitchToCloudRequested = {
                                    scope.launch(kotlinx.coroutines.NonCancellable) {
                                        preferencesRepository.setOfflineModePreferred(false)
                                        manualOfflineSession = false
                                    }
                                }
                            )
                        }
                    }
                )
                HomeHubScreen(viewModel = vm)
            }
        }
    }
}
