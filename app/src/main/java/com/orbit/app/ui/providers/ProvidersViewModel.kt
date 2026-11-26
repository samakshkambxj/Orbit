package com.orbit.app.ui.providers

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.orbit.app.core.AppContainer
import com.orbit.app.data.db.ModelEntity
import com.orbit.app.data.db.ProviderEntity
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class ProvidersViewModel(private val container: AppContainer) : ViewModel() {
    val providers = container.providers.observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList<ProviderEntity>())

    val models = container.providers.observeAllModels()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList<ModelEntity>())

    fun delete(id: String) {
        viewModelScope.launch { container.providers.delete(id) }
    }
}
