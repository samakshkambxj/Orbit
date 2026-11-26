package com.orbit.app.ui.providers

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.orbit.app.core.AppContainer
import com.orbit.app.data.model.ApiFormat
import com.orbit.app.data.model.ChatRequest
import com.orbit.app.data.model.ChatTurn
import com.orbit.app.data.model.ProviderInput
import com.orbit.app.provider.ProviderPreset
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ProviderEditState(
    val loading: Boolean = true,
    val name: String = "",
    val baseUrl: String = "",
    val apiFormat: ApiFormat = ApiFormat.OPENAI,
    val apiKey: String = "",
    val modelId: String = "",
    val modelDisplayName: String = "",
    val hasStoredKey: Boolean = false,
    val isSaving: Boolean = false,
    val isTesting: Boolean = false,
    val error: String? = null,
    val testResult: String? = null,
    val testSucceeded: Boolean = false,
)

class ProviderEditViewModel(
    private val providerId: String?,
    private val container: AppContainer,
) : ViewModel() {
    private val _state = MutableStateFlow(ProviderEditState())
    val state = _state.asStateFlow()

    init {
        viewModelScope.launch {
            if (providerId == null) {
                _state.update { it.copy(loading = false) }
                return@launch
            }
            val provider = container.providers.get(providerId)
            val model = container.providers.modelsFor(providerId).firstOrNull()
            _state.update {
                it.copy(
                    loading = false,
                    name = provider?.name.orEmpty(),
                    baseUrl = provider?.baseUrl.orEmpty(),
                    apiFormat = runCatching { ApiFormat.valueOf(provider?.apiFormat.orEmpty()) }
                        .getOrDefault(ApiFormat.OPENAI),
                    modelId = model?.modelId.orEmpty(),
                    modelDisplayName = model?.displayName.orEmpty(),
                    hasStoredKey = provider?.hasKey == true,
                )
            }
        }
    }

    fun setName(value: String) = _state.update { it.copy(name = value, error = null) }
    fun setBaseUrl(value: String) = _state.update { it.copy(baseUrl = value, error = null) }
    fun setApiFormat(value: ApiFormat) = _state.update { it.copy(apiFormat = value, error = null) }
    fun setApiKey(value: String) = _state.update { it.copy(apiKey = value, error = null) }
    fun setModelId(value: String) = _state.update { it.copy(modelId = value, error = null) }
    fun setModelDisplayName(value: String) = _state.update { it.copy(modelDisplayName = value, error = null) }

    fun applyPreset(preset: ProviderPreset) {
        _state.update {
            it.copy(
                name = preset.providerName,
                baseUrl = preset.baseUrl,
                apiFormat = preset.format,
                modelId = preset.modelId,
                modelDisplayName = preset.modelDisplayName,
                error = null,
            )
        }
    }

    fun save(onSaved: () -> Unit) {
        if (_state.value.isSaving) return
        viewModelScope.launch {
            _state.update { it.copy(isSaving = true, error = null) }
            persist().onSuccess {
                _state.update { state -> state.copy(isSaving = false) }
                onSaved()
            }.onFailure { error ->
                _state.update {
                    it.copy(
                        isSaving = false,
                        error = error.message ?: "Could not save the provider.",
                    )
                }
            }
        }
    }

    fun testConnection() {
        if (_state.value.isTesting) return
        viewModelScope.launch {
            _state.update { it.copy(isTesting = true, error = null, testResult = null) }
            val saved = persist()
            saved.onFailure { error ->
                _state.update {
                    it.copy(
                        isTesting = false,
                        error = error.message ?: "Could not save the provider.",
                    )
                }
                return@launch
            }
            val id = saved.getOrNull() ?: return@launch
            val config = container.providers.configFor(id)
            if (config == null) {
                _state.update {
                    it.copy(isTesting = false, testResult = "Provider could not be loaded.")
                }
                return@launch
            }
            runCatching {
                container.providerEngine.complete(
                    config = config,
                    request = ChatRequest(
                        model = _state.value.modelId.trim(),
                        turns = listOf(ChatTurn("user", "Reply with the single word OK.")),
                        maxTokens = 32,
                        stream = false,
                    ),
                )
            }.onSuccess { reply ->
                _state.update {
                    it.copy(
                        isTesting = false,
                        testSucceeded = true,
                        testResult = "Connected. Model replied: ${reply.trim().take(160)}",
                    )
                }
            }.onFailure { error ->
                _state.update {
                    it.copy(
                        isTesting = false,
                        testSucceeded = false,
                        testResult = error.message?.take(300) ?: "Connection failed.",
                    )
                }
            }
        }
    }

    private suspend fun persist(): Result<String> = runCatching {
        val current = _state.value
        container.providers.save(
            ProviderInput(
                id = providerId,
                name = current.name,
                baseUrl = current.baseUrl,
                apiFormat = current.apiFormat,
                apiKey = current.apiKey.takeIf { it.isNotBlank() },
                modelId = current.modelId,
                modelDisplayName = current.modelDisplayName,
            ),
        ).getOrThrow()
    }
}
