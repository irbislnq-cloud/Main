package com.example.ui.components

import android.content.Intent
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.BugReport
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.DeleteSweep
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import com.example.data.firebase.EXPECTED_FIREBASE_SHA1
import com.example.data.firebase.readApkSigningSha1
import com.example.data.firebase.readAppCheckDebugToken
import com.example.diagnostics.AppLogBuffer
import com.example.diagnostics.CrashLogStore
import com.google.firebase.Firebase
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.auth

@Composable
fun DiagnosticsDialog(
    statusSummary: String = "",
    onDismiss: () -> Unit,
    onToast: (String) -> Unit = {}
) {
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current

    var levelFilter by remember { mutableStateOf("ALL") }
    var refreshTick by remember { mutableStateOf(0) }

    val lastCrash = remember(refreshTick) { CrashLogStore.readLastCrash(context) }
    val deviceHeader = remember(context) { CrashLogStore.buildDeviceDiagnosticsText(context) }
    val appCheckToken = remember(context) { readAppCheckDebugToken(context) }
    val apkSha1 = remember(context) { readApkSigningSha1(context) }
    val authUid = remember(context) {
        runCatching {
            if (FirebaseApp.getApps(context).isNotEmpty()) Firebase.auth.currentUser?.uid else null
        }.getOrNull()
    }
    val logs = remember(refreshTick, levelFilter) {
        val all = AppLogBuffer.snapshot().asReversed()
        when (levelFilter) {
            "ERR" -> all.filter { it.level == "E" || it.level == "W" }
            "INFO" -> all.filter { it.level == "I" || it.level == "D" }
            else -> all
        }
    }

    val shaNote = remember(apkSha1) {
        when {
            apkSha1 == null -> "APK SHA-1: не определён"
            apkSha1.equals(EXPECTED_FIREBASE_SHA1, ignoreCase = true) ->
                "APK SHA-1: $apkSha1 (совпадает с Firebase)"
            else ->
                "APK SHA-1: $apkSha1 (НЕСОВПАДЕНИЕ! В Firebase зарегистрирован $EXPECTED_FIREBASE_SHA1)"
        }
    }

    val fullTextReport = remember(refreshTick, statusSummary, appCheckToken, apkSha1, authUid) {
        buildString {
            append(deviceHeader)
            if (statusSummary.isNotBlank()) {
                appendLine("Состояние: $statusSummary")
            }
            appendLine(shaNote)
            appendLine("Auth UID: ${authUid ?: "не авторизован (локальный/оффлайн)"}")
            if (!appCheckToken.isNullOrBlank()) {
                appendLine("AppCheck Debug Token: $appCheckToken")
            }
            if (lastCrash != null) {
                appendLine()
                appendLine("=== Последний сохранённый сбой ===")
                appendLine(lastCrash.second)
            }
            appendLine()
            appendLine("=== Журнал событий (последние 200) ===")
            appendLine(AppLogBuffer.snapshotFormatted(200))
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(Icons.Outlined.BugReport, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Text("Диагностика и логи")
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 460.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Surface(
                    shape = MaterialTheme.shapes.medium,
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    SelectionContainer {
                        Text(
                            text = buildString {
                                append(deviceHeader.trim())
                                if (statusSummary.isNotBlank()) {
                                    append("\nСостояние: $statusSummary")
                                }
                                append("\n$shaNote")
                                append("\nAuth UID: ${authUid ?: "нет"}")
                                if (!appCheckToken.isNullOrBlank()) {
                                    append("\nAppCheck Debug Token: $appCheckToken")
                                }
                            },
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(10.dp)
                        )
                    }
                }

                if (lastCrash != null) {
                    Surface(
                        shape = MaterialTheme.shapes.medium,
                        color = MaterialTheme.colorScheme.errorContainer,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(10.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                text = "Последний сбой: ${lastCrash.first}",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onErrorContainer
                            )
                            SelectionContainer {
                                Text(
                                    text = lastCrash.second.take(1500),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onErrorContainer
                                )
                            }
                            TextButton(
                                onClick = {
                                    CrashLogStore.clearLastCrash(context)
                                    refreshTick++
                                    onToast("Отчёт о сбое очищен")
                                }
                            ) {
                                Text("Удалить сохранённый отчёт о сбое")
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
                    FilterChip(
                        selected = levelFilter == "ALL",
                        onClick = { levelFilter = "ALL" },
                        label = { Text("Все (${AppLogBuffer.snapshot().size})") }
                    )
                    FilterChip(
                        selected = levelFilter == "ERR",
                        onClick = { levelFilter = "ERR" },
                        label = { Text("Ошибки / Warn") }
                    )
                    FilterChip(
                        selected = levelFilter == "INFO",
                        onClick = { levelFilter = "INFO" },
                        label = { Text("События") }
                    )
                }

                if (logs.isEmpty()) {
                    Text(
                        text = "Записей по выбранному фильтру нет.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    Surface(
                        shape = MaterialTheme.shapes.small,
                        color = MaterialTheme.colorScheme.surfaceContainerLowest,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        SelectionContainer {
                            Column(
                                modifier = Modifier.padding(8.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                logs.take(80).forEach { entry ->
                                    val color = when (entry.level) {
                                        "E" -> MaterialTheme.colorScheme.error
                                        "W" -> MaterialTheme.colorScheme.secondary
                                        else -> MaterialTheme.colorScheme.onSurfaceVariant
                                    }
                                    Text(
                                        text = entry.formatted(),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = color
                                    )
                                }
                            }
                        }
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = {
                            val sendIntent = Intent(Intent.ACTION_SEND).apply {
                                type = "text/plain"
                                putExtra(Intent.EXTRA_SUBJECT, "LoreCraft Diagnostics")
                                putExtra(Intent.EXTRA_TEXT, fullTextReport)
                            }
                            context.startActivity(Intent.createChooser(sendIntent, "Поделиться логами"))
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Outlined.Share, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Отправить")
                    }
                    OutlinedButton(
                        onClick = {
                            AppLogBuffer.clear()
                            refreshTick++
                            onToast("Буфер логов очищен")
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Outlined.DeleteSweep, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Очистить")
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    clipboard.setText(AnnotatedString(fullTextReport))
                    onToast("Полный диагностический отчёт скопирован")
                }
            ) {
                Icon(Icons.Outlined.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Копировать")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Закрыть")
            }
        }
    )
}
