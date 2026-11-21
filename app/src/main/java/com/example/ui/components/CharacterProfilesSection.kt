package com.example.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Badge
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.local.cache.CharacterProfileEntity
import com.example.ui.viewmodel.CharacterProfileViewModel

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CharacterProfilesSection(
    profileViewModel: CharacterProfileViewModel,
    onApplyToPersona: (CharacterProfileEntity) -> Unit,
    modifier: Modifier = Modifier
) {
    val profiles by profileViewModel.profiles.collectAsStateWithLifecycle()
    val searchQuery by profileViewModel.searchQuery.collectAsStateWithLifecycle()
    val isEditorOpen by profileViewModel.isEditorOpen.collectAsStateWithLifecycle()
    val editingProfile by profileViewModel.editingProfile.collectAsStateWithLifecycle()
    val errorMessage by profileViewModel.errorMessage.collectAsStateWithLifecycle()

    Surface(
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainer,
        tonalElevation = 1.dp,
        modifier = modifier.fillMaxWidth()
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
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Badge,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(18.dp)
                        )
                        Text(
                            text = "Профили персонажей (Room)",
                            style = MaterialTheme.typography.titleMedium
                        )
                    }
                    Text(
                        text = "Локальная картотека имён, биографий и черт характера",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                OutlinedButton(
                    onClick = { profileViewModel.startCreating() },
                    modifier = Modifier.testTag("add_character_profile_button")
                ) {
                    Icon(Icons.Outlined.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Профиль")
                }
            }

            if (profiles.isNotEmpty() || searchQuery.isNotBlank()) {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { profileViewModel.onSearchQueryChange(it) },
                    label = { Text("Поиск по имени, биографии или чертам") },
                    leadingIcon = {
                        Icon(Icons.Outlined.Search, contentDescription = null, modifier = Modifier.size(18.dp))
                    },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("character_profile_search_input")
                )
            }

            if (profiles.isEmpty()) {
                Text(
                    text = if (searchQuery.isBlank()) {
                        "Профили персонажей пока не добавлены. Нажмите «Профиль», чтобы сохранить героя с биографией и чертами характера."
                    } else {
                        "По запросу «$searchQuery» профили не найдены."
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                profiles.forEach { profile ->
                    Surface(
                        shape = MaterialTheme.shapes.medium,
                        color = MaterialTheme.colorScheme.surfaceContainerLow,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("character_profile_card_${profile.id}")
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = profile.name,
                                        style = MaterialTheme.typography.titleSmall,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    if (profile.bio.isNotBlank()) {
                                        Text(
                                            text = profile.bio,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            maxLines = 2,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }
                                TextButton(
                                    onClick = { onApplyToPersona(profile) },
                                    modifier = Modifier.testTag("character_profile_use_${profile.id}")
                                ) {
                                    Text("В персону")
                                }
                                IconButton(
                                    onClick = { profileViewModel.startEditing(profile) },
                                    modifier = Modifier.testTag("character_profile_edit_${profile.id}")
                                ) {
                                    Icon(
                                        imageVector = Icons.Outlined.Edit,
                                        contentDescription = "Редактировать профиль"
                                    )
                                }
                                IconButton(
                                    onClick = { profileViewModel.deleteProfile(profile.id) },
                                    modifier = Modifier.testTag("character_profile_delete_${profile.id}")
                                ) {
                                    Icon(
                                        imageVector = Icons.Outlined.DeleteOutline,
                                        contentDescription = "Удалить профиль"
                                    )
                                }
                            }

                            val traits = profile.traitList()
                            if (traits.isNotEmpty()) {
                                FlowRow(
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    verticalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    traits.take(8).forEach { trait ->
                                        Surface(
                                            shape = RoundedCornerShape(8.dp),
                                            color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.7f)
                                        ) {
                                            Text(
                                                text = trait,
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSecondaryContainer,
                                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (isEditorOpen) {
        var name by remember(editingProfile?.id) { mutableStateOf(editingProfile?.name.orEmpty()) }
        var bio by remember(editingProfile?.id) { mutableStateOf(editingProfile?.bio.orEmpty()) }
        var traits by remember(editingProfile?.id) { mutableStateOf(editingProfile?.personalityTraits.orEmpty()) }

        AlertDialog(
            onDismissRequest = { profileViewModel.dismissEditor() },
            title = {
                Text(if (editingProfile == null) "Новый профиль персонажа" else "Редактировать профиль")
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = name,
                        onValueChange = {
                            name = it
                            profileViewModel.clearError()
                        },
                        label = { Text("Имя персонажа *") },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("character_profile_name_input")
                    )
                    OutlinedTextField(
                        value = bio,
                        onValueChange = { bio = it },
                        label = { Text("Биография и предыстория") },
                        minLines = 3,
                        maxLines = 5,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("character_profile_bio_input")
                    )
                    OutlinedTextField(
                        value = traits,
                        onValueChange = { traits = it },
                        label = { Text("Черты характера (через запятую)") },
                        placeholder = { Text("Сдержанный, ироничный, верный") },
                        minLines = 2,
                        maxLines = 3,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("character_profile_traits_input")
                    )
                    if (!errorMessage.isNullOrBlank()) {
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = errorMessage!!,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = { profileViewModel.saveProfile(name, bio, traits) },
                    modifier = Modifier.testTag("character_profile_save_button")
                ) {
                    Text("Сохранить")
                }
            },
            dismissButton = {
                TextButton(onClick = { profileViewModel.dismissEditor() }) {
                    Text("Отмена")
                }
            }
        )
    }
}
