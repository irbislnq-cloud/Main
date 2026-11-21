package com.example.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Login
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.BugReport
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material.icons.outlined.CloudSync
import androidx.compose.material.icons.outlined.DataObject
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.credentials.CredentialManager
import com.example.R
import com.example.data.firebase.FirebaseHealthStatus
import com.example.data.firebase.attemptAutoSignIn
import com.example.data.firebase.onGoogleSignInClicked
import com.example.ui.components.DiagnosticsDialog

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AuthScreen(
    firebaseHealth: FirebaseHealthStatus = FirebaseHealthStatus(isReady = true),
    onAuthSuccess: () -> Unit,
    onEnterOfflineMode: () -> Unit = {}
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val credentialManager = remember(context) {
        runCatching { CredentialManager.create(context) }.getOrNull()
    }

    var isLoading by remember { mutableStateOf(false) }
    var isCheckingAutoSignIn by remember { mutableStateOf(firebaseHealth.isReady) }
    var errorMessage by remember(firebaseHealth.errorSummary) {
        mutableStateOf(firebaseHealth.errorSummary)
    }
    var showDiagnostics by remember { mutableStateOf(false) }

    LaunchedEffect(firebaseHealth.isReady) {
        if (!firebaseHealth.isReady) {
            isCheckingAutoSignIn = false
            return@LaunchedEffect
        }
        attemptAutoSignIn(
            context = context,
            credentialManager = credentialManager,
            onAuthSuccess = {
                isCheckingAutoSignIn = false
                onAuthSuccess()
            },
            onUnauthenticated = {
                isCheckingAutoSignIn = false
            },
            scope = scope
        )
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .windowInsetsPadding(WindowInsets.safeDrawing),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = 520.dp)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Spacer(modifier = Modifier.height(8.dp))

            // Editorial portal artwork card
            Surface(
                shape = MaterialTheme.shapes.extraLarge,
                tonalElevation = 3.dp,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(220.dp)
            ) {
                Box(modifier = Modifier.fillMaxSize()) {
                    Image(
                        painter = painterResource(id = R.drawable.img_auth_portal),
                        contentDescription = "Иллюстрация портала миров LoreCraft",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                Brush.verticalGradient(
                                    colors = listOf(
                                        Color.Transparent,
                                        MaterialTheme.colorScheme.background.copy(alpha = 0.88f)
                                    )
                                )
                            )
                    )
                    Column(
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .padding(20.dp)
                    ) {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.9f)
                        ) {
                            Text(
                                text = "ROLEPLAY AI STUDIO",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                            )
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "LoreCraft",
                            style = MaterialTheme.typography.displayLarge,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                    }
                }
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "Бесплатные AI-источники и живой ролевой чат",
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.onBackground,
                    textAlign = TextAlign.Center
                )
                Text(
                    text = "Бесплатный Gemini, OpenRouter (:free), Groq, локальные GGUF-модели на устройстве, тактическая RPG-карта с d20, арты сцен и оффлайн-кэш в Room.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )

                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FeaturePill(Icons.Outlined.AutoAwesome, "Free AI + GGUF")
                    FeaturePill(Icons.Outlined.Tune, "Temp & Top-P")
                    FeaturePill(Icons.Outlined.CloudSync, "Облако / Оффлайн")
                    FeaturePill(Icons.Outlined.DataObject, "Экспорт JSON")
                }
            }

            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                if (errorMessage != null) {
                    Surface(
                        shape = MaterialTheme.shapes.medium,
                        color = MaterialTheme.colorScheme.errorContainer,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = errorMessage!!,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            modifier = Modifier.padding(12.dp)
                        )
                    }
                }

                if (firebaseHealth.isReady) {
                    Button(
                        onClick = {
                            errorMessage = null
                            isLoading = true
                            onGoogleSignInClicked(
                                context = context,
                                credentialManager = credentialManager,
                                onAuthSuccess = {
                                    isLoading = false
                                    onAuthSuccess()
                                },
                                onAuthError = { msg ->
                                    isLoading = false
                                    errorMessage = msg
                                },
                                scope = scope,
                                onAuthCancelled = { hint ->
                                    isLoading = false
                                    if (!hint.isNullOrBlank()) {
                                        errorMessage = hint
                                    }
                                }
                            )
                        },
                        enabled = !isLoading,
                        shape = MaterialTheme.shapes.large,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp)
                            .testTag("google_sign_in_button")
                    ) {
                        if (isLoading) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                color = MaterialTheme.colorScheme.onPrimary,
                                strokeWidth = 2.dp
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Text("Вход через Google...")
                        } else {
                            Icon(
                                imageVector = Icons.AutoMirrored.Outlined.Login,
                                contentDescription = null,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = "Войти / Регистрация через Google",
                                style = MaterialTheme.typography.labelLarge
                            )
                        }
                    }
                }

                OutlinedButton(
                    onClick = onEnterOfflineMode,
                    shape = MaterialTheme.shapes.large,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .testTag("offline_mode_button")
                ) {
                    Icon(
                        imageVector = Icons.Outlined.CloudOff,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Автономный режим (Оффлайн / Локальные LLM)")
                }

                TextButton(
                    onClick = { showDiagnostics = true },
                    modifier = Modifier.testTag("auth_diagnostics_button")
                ) {
                    Icon(
                        imageVector = Icons.Outlined.BugReport,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Логи и диагностика")
                }

                Text(
                    text = "Регистрация не требуется отдельно: новый облачный профиль создаётся автоматически при первом входе через Google. Все чаты также доступны оффлайн в Room.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
            }
        }
    }

    if (showDiagnostics) {
        DiagnosticsDialog(
            statusSummary = if (firebaseHealth.isReady) "Firebase готов к работе" else "Firebase недоступен: ${firebaseHealth.errorSummary}",
            onDismiss = { showDiagnostics = false }
        )
    }
}

@Composable
private fun FeaturePill(icon: ImageVector, label: String) {
    Surface(
        shape = RoundedCornerShape(50),
        color = MaterialTheme.colorScheme.surfaceContainer,
        tonalElevation = 1.dp
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(16.dp)
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}
