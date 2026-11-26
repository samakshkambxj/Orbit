package com.orbit.app.chat

import com.orbit.app.data.model.ChatRequest
import com.orbit.app.data.model.ChatTurn
import com.orbit.app.data.model.MessageStatus
import com.orbit.app.data.repo.ConversationRepository
import com.orbit.app.provider.ProviderEngine
import com.orbit.app.provider.ProviderEvent
import com.orbit.app.provider.ProviderErrors
import com.orbit.app.provider.ProviderRepository
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Runs chat generations in an application-scoped job so streaming survives
 * rotation. Partial output is persisted while streaming.
 */
class ChatService(
    private val appScope: CoroutineScope,
    private val conversations: ConversationRepository,
    private val providers: ProviderRepository,
    private val engine: ProviderEngine,
) {
    private val jobs = ConcurrentHashMap<String, Job>()
    private val lock = Any()
    private val _active = MutableStateFlow<Set<String>>(emptySet())
    val active: StateFlow<Set<String>> = _active.asStateFlow()

    fun isActive(conversationId: String): Boolean = jobs[conversationId]?.isActive == true

    fun send(conversationId: String, text: String) {
        val clean = text.trim()
        if (clean.isEmpty() || clean.length > MAX_MESSAGE_CHARS) return
        if (isActive(conversationId)) return
        launchGeneration(conversationId) { generate(conversationId, clean) }
    }

    fun stop(conversationId: String) {
        jobs[conversationId]?.cancel(CancellationException("Stopped by user"))
    }

    fun stopAll() {
        jobs.values.forEach { it.cancel() }
    }

    private fun launchGeneration(conversationId: String, block: suspend () -> Unit) {
        val job = appScope.launch(start = CoroutineStart.LAZY) {
            try {
                block()
            } finally {
                synchronized(lock) {
                    jobs.remove(conversationId)
                    _active.value = jobs.keys.toSet()
                }
            }
        }
        synchronized(lock) {
            if (jobs[conversationId]?.isActive == true) {
                job.cancel()
                return
            }
            jobs[conversationId] = job
            _active.value = jobs.keys.toSet()
        }
        job.start()
    }

    private suspend fun generate(conversationId: String, userText: String) {
        val conversation = conversations.get(conversationId) ?: return
        conversations.appendMessage(
            conversationId = conversationId,
            role = "user",
            content = userText,
        )
        if (conversation.title == "New chat") {
            val title = userText.lineSequence().firstOrNull().orEmpty().trim().take(64)
            if (title.isNotEmpty()) conversations.rename(conversationId, title)
        }

        val config = providers.configFor(conversation.providerId)
        if (config == null) {
            appendError(conversationId, "This chat's provider no longer exists. Add a provider to continue.")
            return
        }
        if (conversation.modelId.isBlank()) {
            appendError(conversationId, "No model is selected for this chat.")
            return
        }

        val assistant = conversations.appendMessage(
            conversationId = conversationId,
            role = "assistant",
            content = "",
            status = MessageStatus.STREAMING.name,
        )

        val history = conversations.messages(conversationId, MAX_HISTORY_MESSAGES)
            .filter { it.id != assistant.id }
            .filter { it.status != MessageStatus.ERROR.name && it.content.isNotBlank() }
            .map { ChatTurn(it.role, it.content) }
        val turns = buildList {
            add(ChatTurn("system", SYSTEM_PROMPT))
            addAll(history)
        }

        val content = StringBuilder()
        var lastPersist = 0L
        try {
            engine.stream(
                config = config,
                request = ChatRequest(
                    model = conversation.modelId,
                    turns = turns,
                    maxTokens = 4096,
                    stream = true,
                ),
            ).collect { event ->
                when (event) {
                    is ProviderEvent.Text -> content.append(event.value)
                    is ProviderEvent.Finished -> Unit
                }
                val now = System.currentTimeMillis()
                if (now - lastPersist >= STREAM_PERSIST_INTERVAL_MS) {
                    lastPersist = now
                    runCatching {
                        conversations.upsertMessage(
                            assistant.copy(
                                content = content.toString(),
                                status = MessageStatus.STREAMING.name,
                            ),
                        )
                    }
                }
            }
            if (content.isBlank()) throw ProviderErrors.emptyResponse()
            withContext(NonCancellable) {
                conversations.upsertMessage(
                    assistant.copy(
                        content = content.toString().trimEnd(),
                        status = MessageStatus.COMPLETE.name,
                        error = null,
                    ),
                )
                conversations.touch(conversationId, content.toString())
            }
        } catch (cancelled: CancellationException) {
            withContext(NonCancellable) {
                runCatching {
                    conversations.upsertMessage(
                        assistant.copy(
                            content = content.toString(),
                            status = MessageStatus.CANCELLED.name,
                            error = "Generation stopped.",
                        ),
                    )
                }
            }
            throw cancelled
        } catch (error: Throwable) {
            val message = error.message?.takeIf { it.isNotBlank() }
                ?: "Generation failed. Check the provider and try again."
            withContext(NonCancellable) {
                runCatching {
                    conversations.upsertMessage(
                        assistant.copy(
                            content = content.toString(),
                            status = MessageStatus.ERROR.name,
                            error = message,
                        ),
                    )
                }
            }
        }
    }

    private suspend fun appendError(conversationId: String, message: String) {
        withContext(NonCancellable) {
            runCatching {
                conversations.appendMessage(
                    conversationId = conversationId,
                    role = "assistant",
                    content = "",
                    status = MessageStatus.ERROR.name,
                    error = message,
                )
            }
        }
    }

    private companion object {
        const val MAX_MESSAGE_CHARS = 120_000
        const val MAX_HISTORY_MESSAGES = 60
        const val STREAM_PERSIST_INTERVAL_MS = 150L
        const val SYSTEM_PROMPT =
            "You are ORBIT, a concise and capable AI assistant. Use fenced code blocks with a language tag for code. " +
                "If you are unsure, say so instead of inventing details."
    }
}
