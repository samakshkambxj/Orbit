package com.orbit.app.ui.assistants

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.orbit.app.core.AppContainer
import com.orbit.app.data.db.AssistantEntity
import com.orbit.app.data.db.ModelEntity
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class AssistantsViewModel(private val container: AppContainer) : ViewModel() {
    val assistants = container.assistants.observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList<AssistantEntity>())

    val models = container.providers.observeAllModels()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList<ModelEntity>())

    fun startChat(assistant: AssistantEntity, onResult: (String?) -> Unit) {
        viewModelScope.launch {
            val provider = container.providers.get(assistant.providerId)
            if (provider == null) {
                onResult(null)
                return@launch
            }
            val conversation = container.conversations.create(
                providerId = assistant.providerId,
                modelId = assistant.modelId,
                systemPrompt = assistant.systemPrompt,
            )
            onResult(conversation.id)
        }
    }

    fun delete(id: String) {
        viewModelScope.launch { container.assistants.delete(id) }
    }
}
