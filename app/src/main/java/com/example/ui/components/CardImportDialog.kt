package com.example.ui.components

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CloudDownload
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Save
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.export.ParsedCharacterCard
import com.example.data.export.SillyTavernCardParser
import com.example.data.model.RpScenario

@Composable
fun CardImportDialog(
    onDismiss: () -> Unit,
    onFetchFromUrl: (
        url: String,
        onCardParsed: (ParsedCharacterCard) -> Unit,
        onWorkspaceImported: (Int) -> Unit,
        onError: (String) -> Unit
    ) -> Unit,
    onSaveScenario: (scenario: RpScenario, launchImmediately: Boolean) -> Unit
) {
    val context = LocalContext.current
    var urlInput by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }
    var errorMsg by remember { mutableStateOf<String?>(null) }
    var previewCard by remember { mutableStateOf<ParsedCharacterCard?>(null) }

    val openCardLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            errorMsg = null
            SillyTavernCardParser.parseFromUri(context, uri)
                .onSuccess { card -> previewCard = card }
                .onFailure { err -> errorMsg = err.localizedMessage ?: "Не удалось прочитать карточку" }
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Импорт карточки RP") },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "Поддерживаются карточки SillyTavern PNG (с метаданными chara), Chub / Tavern v2 JSON и ссылки GitHub / Gist.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                OutlinedButton(
                    onClick = {
                        openCardLauncher.launch(arrayOf("image/png", "application/json", "text/*"))
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("pick_card_file_button")
                ) {
                    Icon(Icons.Outlined.Image, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Выбрать PNG или JSON файл")
                }

                HorizontalDivider()

                OutlinedTextField(
                    value = urlInput,
                    onValueChange = { urlInput = it },
                    label = { Text("Ссылка на GitHub / Gist / Raw JSON") },
                    placeholder = { Text("https://gist.github.com/... или raw...") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("card_url_input")
                )

                Button(
                    onClick = {
                        errorMsg = null
                        isLoading = true
                        onFetchFromUrl(
                            urlInput,
                            { card ->
                                isLoading = false
                                previewCard = card
                            },
                            { count ->
                                isLoading = false
                                if (count > 0) onDismiss()
                            },
                            { err ->
                                isLoading = false
                                errorMsg = err
                            }
                        )
                    },
                    enabled = urlInput.isNotBlank() && !isLoading,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("fetch_card_url_button")
                ) {
                    if (isLoading) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                    } else {
                        Icon(Icons.Outlined.CloudDownload, contentDescription = null, modifier = Modifier.size(18.dp))
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Загрузить по ссылке")
                }

                if (errorMsg != null) {
                    Surface(
                        shape = MaterialTheme.shapes.small,
                        color = MaterialTheme.colorScheme.errorContainer,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = errorMsg!!,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            modifier = Modifier.padding(10.dp)
                        )
                    }
                }

                val card = previewCard
                if (card != null) {
                    Surface(
                        shape = MaterialTheme.shapes.medium,
                        color = MaterialTheme.colorScheme.surfaceContainerHigh,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                text = "${card.sourceFormat}: ${card.scenario.title}",
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                            if (card.scenario.characterBio.isNotBlank()) {
                                Text(
                                    text = card.scenario.characterBio,
                                    style = MaterialTheme.typography.bodySmall,
                                    maxLines = 4,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            if (card.scenario.firstMessage.isNotBlank()) {
                                Text(
                                    text = "Приветствие: ${card.scenario.firstMessage}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 3,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            Text(
                                text = "Лорбук: ${card.scenario.loreEntries.size} зап. • Теги: ${card.scenario.tags.joinToString(", ")}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.tertiary
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            val card = previewCard
            if (card != null) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = {
                            onSaveScenario(card.scenario, false)
                            onDismiss()
                        },
                        modifier = Modifier.testTag("save_imported_card_button")
                    ) {
                        Icon(Icons.Outlined.Save, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("В библиотеку")
                    }
                    Button(
                        onClick = {
                            onSaveScenario(card.scenario, true)
                            onDismiss()
                        },
                        modifier = Modifier.testTag("launch_imported_card_button")
                    ) {
                        Icon(Icons.Outlined.PlayArrow, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Играть")
                    }
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Закрыть") }
        }
    )
}
