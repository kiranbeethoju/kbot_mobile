package com.offlinebot.ui.models

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.offlinebot.ai.ModelCatalog
import com.offlinebot.ai.ModelStatus
import com.offlinebot.ai.download.DownloadState
import com.offlinebot.ai.download.ModelDownloadManager
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ModelsState(
    val models: List<ModelRow> = emptyList(),
    val downloadStates: Map<String, DownloadState> = emptyMap()
)

data class ModelRow(
    val id: String,
    val label: String,
    val description: String,
    val installed: Boolean,
    val sizeLabel: String,
    val path: String,
    val required: Boolean,
    val canDownload: Boolean,
    val downloadState: DownloadState = DownloadState.Idle
)

@HiltViewModel
class ModelsViewModel @Inject constructor(
    private val modelCatalog: ModelCatalog,
    private val downloadManager: ModelDownloadManager
) : ViewModel() {
    private val _state = MutableStateFlow(ModelsState())
    val state: StateFlow<ModelsState> = _state.asStateFlow()

    init {
        refreshModels()
        viewModelScope.launch {
            downloadManager.downloadStates.collect { states ->
                _state.update { it.copy(downloadStates = states) }
                refreshModels()
            }
        }
    }

    private fun refreshModels() {
        val states = _state.value.downloadStates
        _state.update {
            it.copy(
                models = modelCatalog.statuses().map { status ->
                    status.toRow(states[status.id])
                }
            )
        }
    }

    fun downloadModel(modelId: String) {
        val status = modelCatalog.statuses().find { it.id == modelId } ?: return
        val url = status.downloadUrl ?: return

        viewModelScope.launch {
            downloadManager.download(
                modelId = status.id,
                url = url,
                destFile = status.file,
                expectedSha256 = status.sha256
            )
        }
    }

    private fun ModelStatus.toRow(state: DownloadState?): ModelRow = ModelRow(
        id = id,
        label = label,
        description = description,
        installed = installed,
        sizeLabel = sizeLabel,
        path = file.absolutePath,
        required = required,
        canDownload = downloadUrl != null,
        downloadState = state ?: DownloadState.Idle
    )
}
