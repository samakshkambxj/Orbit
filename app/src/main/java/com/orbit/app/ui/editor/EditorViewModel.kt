package com.orbit.app.ui.editor

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.orbit.app.core.AppContainer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class EditorState(
    val loading: Boolean = true,
    val content: String = "",
    val original: String = "",
    val error: String? = null,
    val saving: Boolean = false,
    val saved: Boolean = false,
) {
    val dirty: Boolean get() = content != original
}

class EditorViewModel(
    private val path: String,
    private val container: AppContainer,
) : ViewModel() {
    private val _state = MutableStateFlow(EditorState())
    val state = _state.asStateFlow()

    val fileName: String = path.substringAfterLast('/').ifBlank { "File" }

    init {
        viewModelScope.launch {
            container.workspace.read(path)
                .onSuccess { text ->
                    _state.update {
                        it.copy(
                            loading = false,
                            content = text,
                            original = text,
                            error = null,
                        )
                    }
                }
                .onFailure { error ->
                    _state.update {
                        it.copy(
                            loading = false,
                            error = error.message ?: "Could not open that file.",
                        )
                    }
                }
        }
    }

    fun update(value: String) {
        _state.update { it.copy(content = value, saved = false) }
    }

    fun save() {
        val current = _state.value
        if (current.saving || !current.dirty) return
        viewModelScope.launch {
            _state.update { it.copy(saving = true, error = null) }
            container.workspace.write(path, current.content)
                .onSuccess {
                    _state.update {
                        it.copy(saving = false, original = it.content, saved = true)
                    }
                }
                .onFailure { error ->
                    _state.update {
                        it.copy(
                            saving = false,
                            error = error.message ?: "Could not save that file.",
                        )
                    }
                }
        }
    }

    fun clearError() {
        _state.update { it.copy(error = null) }
    }
}
