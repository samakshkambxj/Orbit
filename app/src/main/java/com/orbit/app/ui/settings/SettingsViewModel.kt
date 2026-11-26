package com.orbit.app.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.orbit.app.core.AppContainer
import com.orbit.app.data.db.ConversationEntity
import com.orbit.app.data.db.ModelEntity
import com.orbit.app.data.db.ProviderEntity
import com.orbit.app.data.model.ThemeMode
import com.orbit.app.data.prefs.AppSettings
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class SettingsViewModel(private val container: AppContainer) : ViewModel() {
    val settings = container.settings.settings
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AppSettings())

    val providers = container.providers.observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList<ProviderEntity>())

    val models = container.providers.observeAllModels()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList<ModelEntity>())

    val conversations = container.conversations.observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList<ConversationEntity>())

    fun setThemeMode(mode: ThemeMode) {
        viewModelScope.launch { container.settings.setThemeMode(mode) }
    }

    fun setDynamicColor(enabled: Boolean) {
        viewModelScope.launch { container.settings.setDynamicColor(enabled) }
    }

    fun setDefaultModel(providerId: String, modelId: String) {
        viewModelScope.launch {
            container.settings.setDefaultProvider(providerId)
            container.settings.setDefaultModel(modelId)
        }
    }

    fun deleteAllChats() {
        viewModelScope.launch {
            container.conversations.observeAll().first().forEach { conversation ->
                container.conversations.delete(conversation.id)
            }
        }
    }
}
