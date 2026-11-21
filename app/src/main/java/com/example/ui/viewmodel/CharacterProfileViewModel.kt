package com.example.ui.viewmodel

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.data.local.cache.CharacterProfileEntity
import com.example.data.local.cache.CharacterProfileRepository
import com.example.data.local.cache.OfflineRpDatabase
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@OptIn(ExperimentalCoroutinesApi::class)
class CharacterProfileViewModel(
    private val repository: CharacterProfileRepository
) : ViewModel() {

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    val profiles: StateFlow<List<CharacterProfileEntity>> = _searchQuery
        .flatMapLatest { query -> repository.searchProfiles(query) }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000L),
            initialValue = emptyList()
        )

    private val _editingProfile = MutableStateFlow<CharacterProfileEntity?>(null)
    val editingProfile: StateFlow<CharacterProfileEntity?> = _editingProfile.asStateFlow()

    private val _isEditorOpen = MutableStateFlow(false)
    val isEditorOpen: StateFlow<Boolean> = _isEditorOpen.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    fun onSearchQueryChange(query: String) {
        _searchQuery.value = query
    }

    fun startCreating() {
        _errorMessage.value = null
        _editingProfile.value = null
        _isEditorOpen.value = true
    }

    fun startEditing(profile: CharacterProfileEntity) {
        _errorMessage.value = null
        _editingProfile.value = profile
        _isEditorOpen.value = true
    }

    fun dismissEditor() {
        _isEditorOpen.value = false
        _editingProfile.value = null
        _errorMessage.value = null
    }

    fun saveProfile(
        name: String,
        bio: String,
        personalityTraits: String,
        onSaved: (Long) -> Unit = {}
    ) {
        val cleanName = name.trim()
        if (cleanName.isEmpty()) {
            _errorMessage.value = "Введите имя персонажа"
            return
        }
        val targetId = _editingProfile.value?.id ?: 0L
        viewModelScope.launch {
            runCatching {
                repository.saveProfile(
                    id = targetId,
                    name = cleanName,
                    bio = bio,
                    personalityTraits = personalityTraits
                )
            }.onSuccess { savedId ->
                _isEditorOpen.value = false
                _editingProfile.value = null
                _errorMessage.value = null
                onSaved(savedId)
            }.onFailure { err ->
                _errorMessage.value = err.localizedMessage ?: "Не удалось сохранить профиль персонажа"
            }
        }
    }

    fun deleteProfile(id: Long) {
        viewModelScope.launch {
            runCatching {
                repository.deleteProfile(id)
            }.onFailure { err ->
                _errorMessage.value = err.localizedMessage ?: "Не удалось удалить профиль"
            }
        }
    }

    fun clearError() {
        _errorMessage.value = null
    }

    companion object {
        fun factory(context: Context): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val db = OfflineRpDatabase.getInstance(context.applicationContext)
                CharacterProfileViewModel(CharacterProfileRepository(db.characterProfileDao()))
            }
        }
    }
}
