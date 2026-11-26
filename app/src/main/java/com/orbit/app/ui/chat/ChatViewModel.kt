package com.orbit.app.ui.chat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.orbit.app.core.AppContainer
import com.orbit.app.data.db.MessageEntity
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
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

    private val _input = MutableStateFlow("")
    val input = _input.asStateFlow()

    val isGenerating = container.chat.active
        .map { conversationId in it }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    fun setInput(value: String) {
        _input.value = value
    }

    fun send() {
        val text = _input.value.trim()
        if (text.isEmpty() || isGenerating.value) return
        _input.value = ""
        container.chat.send(conversationId, text)
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

    fun delete(onDeleted: () -> Unit) {
        viewModelScope.launch {
            container.chat.stop(conversationId)
            container.conversations.delete(conversationId)
            onDeleted()
        }
    }
}
