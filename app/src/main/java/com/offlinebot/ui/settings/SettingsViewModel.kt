package com.offlinebot.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.offlinebot.ai.ModelCatalog
import com.offlinebot.data.database.dao.SystemPromptDao
import com.offlinebot.data.database.entities.SystemPromptEntity
import com.offlinebot.data.repository.PhoneContextRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class PromptUi(
    val id: Long,
    val name: String,
    val content: String,
    val isActive: Boolean
)

data class SettingsState(
    val models: List<ModelRow> = emptyList(),
    val phoneContextStatus: String = "Contacts and SMS are optional. Import only if you want local context.",
    val importingPhoneContext: Boolean = false,
    val prompts: List<PromptUi> = emptyList(),
    val editingPromptId: Long? = null,
    val editingPromptName: String = "",
    val editingPromptContent: String = "",
    val includeContactsInContext: Boolean = false,
    val includeMessagesInContext: Boolean = false,
    val mapsEnabled: Boolean = true,
    val contactCount: Int = 0,
    val messageCount: Int = 0,
    val editingActivePrompt: Boolean = false,
    val editingActivePromptContent: String = ""
)

data class ModelRow(
    val label: String,
    val installed: Boolean,
    val sizeLabel: String,
    val path: String,
    val required: Boolean
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    modelCatalog: ModelCatalog,
    private val phoneContextRepository: PhoneContextRepository,
    private val systemPromptDao: SystemPromptDao
) : ViewModel() {
    private val _state = MutableStateFlow(
        SettingsState(
            models = modelCatalog.bundledStatus().map {
                ModelRow(label = it.label, installed = it.installed, sizeLabel = it.sizeLabel,
                    path = it.file.absolutePath, required = it.required)
            }
        )
    )
    val state: StateFlow<SettingsState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            systemPromptDao.observeAll().collect { prompts ->
                _state.update {
                    it.copy(prompts = prompts.map { p ->
                        PromptUi(id = p.id, name = p.name, content = p.content, isActive = p.isActive)
                    })
                }
            }
        }
        viewModelScope.launch {
            systemPromptDao.observeActive().collect { prompt ->
                _state.update { it.copy(editingActivePromptContent = prompt?.content ?: "") }
            }
        }
    }

    fun toggleContactsContext() {
        _state.update { it.copy(includeContactsInContext = !it.includeContactsInContext) }
    }

    fun toggleMessagesContext() {
        _state.update { it.copy(includeMessagesInContext = !it.includeMessagesInContext) }
    }

    fun toggleMapsEnabled() {
        _state.update { it.copy(mapsEnabled = !it.mapsEnabled) }
    }

    fun startEditActivePrompt() {
        _state.update { it.copy(editingActivePrompt = true, editingActivePromptContent = it.editingActivePromptContent) }
    }

    fun updateActivePromptContent(content: String) {
        _state.update { it.copy(editingActivePromptContent = content) }
    }

    fun saveActivePrompt() {
        viewModelScope.launch {
            val content = _state.value.editingActivePromptContent.trim()
            // Update the single active prompt (id=1, created on first use)
            val existing = systemPromptDao.getActiveNow()
            if (existing != null) {
                systemPromptDao.update(existing.copy(content = content))
            } else {
                systemPromptDao.deactivateAll()
                systemPromptDao.insert(
                    com.offlinebot.data.database.entities.SystemPromptEntity(
                        name = "System Prompt", content = content, isActive = true
                    )
                )
            }
            _state.update { it.copy(editingActivePrompt = false) }
        }
    }

    fun cancelEditActivePrompt() {
        _state.update { it.copy(editingActivePrompt = false) }
    }

    fun importPhoneContext() {
        viewModelScope.launch {
            _state.update { it.copy(importingPhoneContext = true, phoneContextStatus = "Importing phone context locally...") }
            val result = phoneContextRepository.importAvailableContext()
            val missing = if (result.missingPermissions.isEmpty()) "" else " Missing: ${result.missingPermissions.joinToString()}"
            _state.update { it.copy(importingPhoneContext = false,
                phoneContextStatus = "Imported ${result.contactsImported} contacts and ${result.messagesImported} messages.$missing") }
        }
    }

    // System prompt CRUD
    fun startNewPrompt() {
        _state.update { it.copy(editingPromptId = 0L, editingPromptName = "", editingPromptContent = "") }
    }

    fun startEditPrompt(prompt: PromptUi) {
        _state.update { it.copy(editingPromptId = prompt.id, editingPromptName = prompt.name, editingPromptContent = prompt.content) }
    }

    fun cancelEdit() {
        _state.update { it.copy(editingPromptId = null, editingPromptName = "", editingPromptContent = "") }
    }

    fun updateEditName(name: String) { _state.update { it.copy(editingPromptName = name) } }
    fun updateEditContent(content: String) { _state.update { it.copy(editingPromptContent = content) } }

    fun savePrompt() {
        val name = _state.value.editingPromptName.trim()
        val content = _state.value.editingPromptContent.trim()
        if (name.isBlank() || content.isBlank()) return

        viewModelScope.launch {
            val id = _state.value.editingPromptId ?: return@launch
            if (id == 0L) {
                systemPromptDao.insert(SystemPromptEntity(name = name, content = content))
            } else {
                systemPromptDao.update(SystemPromptEntity(id = id, name = name, content = content))
            }
            _state.update { it.copy(editingPromptId = null, editingPromptName = "", editingPromptContent = "") }
        }
    }

    fun deletePrompt(prompt: PromptUi) {
        viewModelScope.launch { systemPromptDao.deleteById(prompt.id) }
    }

    fun setActivePrompt(prompt: PromptUi) {
        viewModelScope.launch {
            systemPromptDao.deactivateAll()
            systemPromptDao.setActive(prompt.id)
        }
    }
}
