package com.orbit.app.ui.workspace

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.orbit.app.core.AppContainer
import com.orbit.app.data.prefs.WorkspaceRef
import com.orbit.app.data.workspace.WorkspaceEntry
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class WorkspaceState(
    val loading: Boolean = true,
    val workspace: WorkspaceRef? = null,
    val currentPath: String = "",
    val entries: List<WorkspaceEntry> = emptyList(),
    val error: String? = null,
    val busy: Boolean = false,
)

class WorkspaceViewModel(private val container: AppContainer) : ViewModel() {
    private val _state = MutableStateFlow(WorkspaceState())
    val state = _state.asStateFlow()

    init {
        viewModelScope.launch {
            container.workspace.workspace.collect { ref ->
                _state.update { it.copy(workspace = ref, currentPath = "", loading = ref != null) }
                if (ref == null) {
                    _state.update { it.copy(entries = emptyList(), loading = false) }
                } else {
                    refresh()
                }
            }
        }
    }

    fun adopt(uri: Uri) {
        viewModelScope.launch {
            _state.update { it.copy(busy = true, error = null) }
            container.workspace.adopt(uri)
                .onSuccess {
                    _state.update { it.copy(busy = false, currentPath = "") }
                }
                .onFailure { error ->
                    _state.update {
                        it.copy(busy = false, error = error.message ?: "Could not open that folder.")
                    }
                }
        }
    }

    fun forget() {
        viewModelScope.launch {
            container.workspace.forget()
            _state.update {
                it.copy(currentPath = "", entries = emptyList(), error = null)
            }
        }
    }

    fun refresh() {
        val path = _state.value.currentPath
        viewModelScope.launch {
            _state.update { it.copy(loading = true, error = null) }
            container.workspace.list(path)
                .onSuccess { entries ->
                    _state.update { it.copy(entries = entries, loading = false) }
                }
                .onFailure { error ->
                    _state.update {
                        it.copy(
                            loading = false,
                            error = error.message ?: "Could not read that folder.",
                        )
                    }
                }
        }
    }

    fun open(path: String) {
        _state.update { it.copy(currentPath = path) }
        refresh()
    }

    fun up() {
        val path = _state.value.currentPath
        val parent = path.split('/').filter { it.isNotBlank() }.dropLast(1).joinToString("/")
        open(parent)
    }

    fun createFile(name: String) {
        val path = _state.value.currentPath
        viewModelScope.launch {
            container.workspace.createFile(path, name)
                .onSuccess { refresh() }
                .onFailure { error ->
                    _state.update {
                        it.copy(error = error.message ?: "Could not create that file.")
                    }
                }
        }
    }

    fun createFolder(name: String) {
        val path = _state.value.currentPath
        viewModelScope.launch {
            container.workspace.createFolder(path, name)
                .onSuccess { refresh() }
                .onFailure { error ->
                    _state.update {
                        it.copy(error = error.message ?: "Could not create that folder.")
                    }
                }
        }
    }

    fun delete(entry: WorkspaceEntry) {
        viewModelScope.launch {
            container.workspace.delete(entry.path)
                .onSuccess { refresh() }
                .onFailure { error ->
                    _state.update {
                        it.copy(error = error.message ?: "Could not delete that item.")
                    }
                }
        }
    }

    fun rename(entry: WorkspaceEntry, newName: String) {
        viewModelScope.launch {
            container.workspace.rename(entry.path, newName)
                .onSuccess { refresh() }
                .onFailure { error ->
                    _state.update {
                        it.copy(error = error.message ?: "Could not rename that item.")
                    }
                }
        }
    }

    fun clearError() {
        _state.update { it.copy(error = null) }
    }
}
