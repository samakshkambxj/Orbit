package com.orbit.app.ui.chats

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.orbit.app.core.AppContainer
import com.orbit.app.data.db.ConversationEntity
import com.orbit.app.data.db.ModelEntity
import com.orbit.app.data.db.ProviderEntity
import com.orbit.app.data.prefs.AppSettings
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class ChatListViewModel(private val container: AppContainer) : ViewModel() {
    val conversations = container.conversations.observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList<ConversationEntity>())

    val models = container.providers.observeAllModels()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList<ModelEntity>())

    val providers = container.providers.observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList<ProviderEntity>())

    private val settings = container.settings.settings
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AppSettings())

    fun createChat(onResult: (String?) -> Unit) {
        viewModelScope.launch {
            val current = settings.value
            val available = models.value
            val chosen = available.firstOrNull {
                it.providerId == current.defaultProviderId && it.modelId == current.defaultModelId
            } ?: available.firstOrNull { it.providerId == current.defaultProviderId }
                ?: available.firstOrNull()

            if (chosen == null) {
                onResult(null)
                return@launch
            }
            val conversation = container.conversations.create(
                providerId = chosen.providerId,
                modelId = chosen.modelId,
            )
            onResult(conversation.id)
        }
    }

    fun rename(id: String, title: String) {
        viewModelScope.launch { container.conversations.rename(id, title) }
    }

    fun delete(id: String) {
        viewModelScope.launch { container.conversations.delete(id) }
    }

    fun setPinned(id: String, pinned: Boolean) {
        viewModelScope.launch { container.conversations.setPinned(id, pinned) }
    }
}
