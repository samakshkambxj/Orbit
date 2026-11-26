package com.orbit.app.ui.assistants

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.orbit.app.core.AppContainer
import com.orbit.app.data.db.ModelEntity
import com.orbit.app.data.db.ProviderEntity
import com.orbit.app.data.repo.AssistantInput
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class AssistantEditState(
    val loading: Boolean = true,
    val name: String = "",
    val emoji: String = "✦",
    val systemPrompt: String = "",
    val providerId: String = "",
    val modelId: String = "",
    val isSaving: Boolean = false,
    val error: String? = null,
)

class AssistantEditViewModel(
    private val assistantId: String?,
    private val container: AppContainer,
) : ViewModel() {
    private val _state = MutableStateFlow(AssistantEditState())
    val state = _state.asStateFlow()

    val models = container.providers.observeAllModels()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList<ModelEntity>())

    val providers = container.providers.observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList<ProviderEntity>())

    init {
        viewModelScope.launch {
            val existing = assistantId?.let { container.assistants.get(it) }
            if (existing != null) {
                _state.update {
                    it.copy(
                        loading = false,
                        name = existing.name,
                        emoji = existing.emoji,
                        systemPrompt = existing.systemPrompt,
                        providerId = existing.providerId,
                        modelId = existing.modelId,
                    )
                }
                return@launch
            }
            val firstModel = container.providers.observeAllModels().first().firstOrNull()
            _state.update {
                it.copy(
                    loading = false,
                    providerId = firstModel?.providerId.orEmpty(),
                    modelId = firstModel?.modelId.orEmpty(),
                )
            }
        }
    }

    fun setName(value: String) = _state.update { it.copy(name = value, error = null) }
    fun setEmoji(value: String) = _state.update { it.copy(emoji = value, error = null) }
    fun setSystemPrompt(value: String) = _state.update { it.copy(systemPrompt = value, error = null) }

    fun setModel(providerId: String, modelId: String) {
        _state.update { it.copy(providerId = providerId, modelId = modelId, error = null) }
    }

    fun save(onSaved: () -> Unit) {
        if (_state.value.isSaving) return
        viewModelScope.launch {
            _state.update { it.copy(isSaving = true, error = null) }
            val current = _state.value
            container.assistants.save(
                AssistantInput(
                    id = assistantId,
                    name = current.name,
                    emoji = current.emoji,
                    systemPrompt = current.systemPrompt,
                    providerId = current.providerId,
                    modelId = current.modelId,
                ),
            ).onSuccess {
                _state.update { state -> state.copy(isSaving = false) }
                onSaved()
            }.onFailure { error ->
                _state.update {
                    it.copy(
                        isSaving = false,
                        error = error.message ?: "Could not save the assistant.",
                    )
                }
            }
        }
    }

    fun delete(onDeleted: () -> Unit) {
        val id = assistantId ?: return
        viewModelScope.launch {
            container.assistants.delete(id)
            onDeleted()
        }
    }
}
