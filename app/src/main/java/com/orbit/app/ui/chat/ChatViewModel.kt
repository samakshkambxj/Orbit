package com.orbit.app.ui.chat

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.orbit.app.core.AppContainer
import com.orbit.app.data.db.MessageEntity
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class ChatViewModel(
    private val conversationId: String,
    private val container: AppContainer,
) : ViewModel() {
    val conversation = container.conversations.observe(conversationId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val messages = container.conversations.observeMessages(conversationId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList<MessageEntity>())

    val providers = container.providers.observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val models = container.providers.observeAllModels()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val workspace = container.workspace.workspace
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    private val _input = MutableStateFlow("")
    val input = _input.asStateFlow()

    private val _pendingImages = MutableStateFlow<List<String>>(emptyList())
    val pendingImages = _pendingImages.asStateFlow()

    private val _imageError = MutableStateFlow<String?>(null)
    val imageError = _imageError.asStateFlow()

    val isGenerating = container.chat.active
        .map { conversationId in it }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    fun setInput(value: String) {
        _input.value = value
    }

    fun addImage(uri: Uri) {
        if (_pendingImages.value.size >= MAX_IMAGES) {
            _imageError.value = "You can attach up to $MAX_IMAGES images."
            return
        }
        _imageError.value = null
        viewModelScope.launch {
            container.images.save(uri)
                .onSuccess { path -> _pendingImages.update { it + path } }
                .onFailure { error ->
                    _imageError.value = error.message ?: "Could not add that image."
                }
        }
    }

    fun removeImage(path: String) {
        _pendingImages.update { it - path }
        container.images.delete(path)
    }

    fun clearImageError() {
        _imageError.value = null
    }

    fun send() {
        val text = _input.value.trim()
        val images = _pendingImages.value
        if ((text.isEmpty() && images.isEmpty()) || isGenerating.value) return
        _input.value = ""
        _pendingImages.value = emptyList()
        container.chat.send(conversationId, text, images)
    }

    fun stop() {
        container.chat.stop(conversationId)
    }

    fun retry() {
        if (isGenerating.value) return
        container.chat.retry(conversationId)
    }

    fun selectModel(providerId: String, modelId: String) {
        viewModelScope.launch {
            container.conversations.setModel(conversationId, providerId, modelId)
        }
    }

    fun rename(title: String) {
        viewModelScope.launch { container.conversations.rename(conversationId, title) }
    }

    fun setSystemPrompt(prompt: String) {
        viewModelScope.launch {
            container.conversations.setSystemPrompt(conversationId, prompt)
        }
    }

    fun setAgentEnabled(enabled: Boolean) {
        viewModelScope.launch {
            container.conversations.setAgentEnabled(conversationId, enabled)
        }
    }

    fun approveToolCalls() {
        container.chat.approveToolCalls(conversationId)
    }

    fun denyToolCalls() {
        container.chat.denyToolCalls(conversationId)
    }

    fun delete(onDeleted: () -> Unit) {
        viewModelScope.launch {
            container.chat.stop(conversationId)
            val attachments = container.conversations
                .messages(conversationId, 10_000)
                .flatMap { it.attachments.lines() }
            container.conversations.delete(conversationId)
            attachments.filter { it.isNotBlank() }.forEach { container.images.delete(it) }
            onDeleted()
        }
    }

    private companion object {
        const val MAX_IMAGES = 4
    }
}
