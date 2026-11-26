package com.orbit.app.chat

import android.util.Base64
import com.orbit.app.data.model.ChatImage
import com.orbit.app.data.model.ChatRequest
import com.orbit.app.data.model.ChatTurn
import com.orbit.app.data.model.MessageStatus
import com.orbit.app.data.model.ToolCall
import com.orbit.app.data.repo.ConversationRepository
import com.orbit.app.data.workspace.WorkspaceRepository
import com.orbit.app.provider.ProviderEngine
import com.orbit.app.provider.ProviderEvent
import com.orbit.app.provider.ProviderErrors
import com.orbit.app.provider.ProviderRepository
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * Runs chat generations in an application-scoped job so streaming survives
 * rotation. Partial output is persisted while streaming. When agent mode is on,
 * tool calls are executed against the workspace, with mutations gated by user
 * approval.
 */
class ChatService(
    private val appScope: CoroutineScope,
    private val conversations: ConversationRepository,
    private val providers: ProviderRepository,
    private val engine: ProviderEngine,
    private val workspace: WorkspaceRepository,
    private val json: Json,
) {
    private val agentTools = AgentTools(workspace, json)
    private val toolCallSerializer = ListSerializer(ToolCall.serializer())
    private val jobs = ConcurrentHashMap<String, Job>()
    private val lock = Any()
    private val _active = MutableStateFlow<Set<String>>(emptySet())
    val active: StateFlow<Set<String>> = _active.asStateFlow()

    fun isActive(conversationId: String): Boolean = jobs[conversationId]?.isActive == true

    fun send(conversationId: String, text: String, imagePaths: List<String> = emptyList()) {
        val clean = text.trim()
        val images = imagePaths.filter { it.isNotBlank() }.take(MAX_IMAGES)
        if ((clean.isEmpty() && images.isEmpty()) || clean.length > MAX_MESSAGE_CHARS) return
        if (isActive(conversationId)) return
        launchGeneration(conversationId) { generate(conversationId, clean, images) }
    }

    fun retry(conversationId: String) {
        if (isActive(conversationId)) return
        launchGeneration(conversationId) { retryGeneration(conversationId) }
    }

    fun approveToolCalls(conversationId: String) {
        if (isActive(conversationId)) return
        launchGeneration(conversationId) { resolveToolCalls(conversationId, approved = true) }
    }

    fun denyToolCalls(conversationId: String) {
        if (isActive(conversationId)) return
        launchGeneration(conversationId) { resolveToolCalls(conversationId, approved = false) }
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

    private suspend fun generate(
        conversationId: String,
        userText: String,
        imagePaths: List<String>,
    ) {
        val conversation = conversations.get(conversationId) ?: return
        conversations.appendMessage(
            conversationId = conversationId,
            role = "user",
            content = userText,
            attachments = imagePaths.joinToString("\n"),
        )
        if (conversation.title == "New chat") {
            val title = userText.lineSequence().firstOrNull().orEmpty().trim().take(64)
                .ifBlank { if (imagePaths.isEmpty()) "" else "Image" }
            if (title.isNotEmpty()) conversations.rename(conversationId, title)
        }
        respond(conversationId)
    }

    private suspend fun retryGeneration(conversationId: String) {
        val all = conversations.messages(conversationId, 10_000)
        val lastAssistant = all.indexOfLast { it.role == "assistant" }
        if (lastAssistant >= 0) {
            all.drop(lastAssistant).forEach { conversations.deleteMessage(it.id) }
        }
        respond(conversationId)
    }

    private suspend fun resolveToolCalls(conversationId: String, approved: Boolean) {
        val all = conversations.messages(conversationId, 200)
        val index = all.indexOfLast { it.status == MessageStatus.AWAITING_APPROVAL.name }
        if (index < 0) return
        val message = all[index]
        all.drop(index + 1).forEach { conversations.deleteMessage(it.id) }
        val calls = decodeCalls(message.toolCalls)
        conversations.upsertMessage(message.copy(status = MessageStatus.COMPLETE.name))
        executeCalls(conversationId, calls, approved)
        respond(conversationId)
    }

    private suspend fun respond(conversationId: String, depth: Int = 0) {
        val conversation = conversations.get(conversationId) ?: return
        if (depth > MAX_TOOL_STEPS) {
            appendError(
                conversationId,
                "The agent stopped after $MAX_TOOL_STEPS tool steps. Send a new message to continue.",
            )
            return
        }
        val pending = conversations.lastMessage(conversationId)
        if (pending != null && pending.status == MessageStatus.AWAITING_APPROVAL.name) return

        val config = providers.configFor(conversation.providerId)
        if (config == null) {
            appendError(conversationId, "This chat's provider no longer exists. Add a provider to continue.")
            return
        }
        if (conversation.modelId.isBlank()) {
            appendError(conversationId, "No model is selected for this chat.")
            return
        }

        val workspaceRef = workspace.workspace.first()
        val agentActive = conversation.agentEnabled && workspaceRef != null
        val tools = if (agentActive) agentTools.specs else emptyList()

        val assistant = conversations.appendMessage(
            conversationId = conversationId,
            role = "assistant",
            content = "",
            status = MessageStatus.STREAMING.name,
        )

        val history = conversations.messages(conversationId, MAX_HISTORY_MESSAGES)
            .filter { it.id != assistant.id }
            .filter { it.status != MessageStatus.ERROR.name }
            .filter {
                it.content.isNotBlank() ||
                    it.attachments.isNotBlank() ||
                    it.toolCalls.isNotBlank() ||
                    it.toolCallId.isNotBlank()
            }
            .map {
                ChatTurn(
                    role = it.role,
                    content = it.content,
                    images = loadImages(it.attachments),
                    toolCalls = decodeCalls(it.toolCalls),
                    toolCallId = it.toolCallId,
                    toolName = it.toolName,
                    toolError = it.toolError,
                )
            }

        val systemPrompt = buildString {
            append(conversation.systemPrompt.ifBlank { SYSTEM_PROMPT })
            if (agentActive) {
                append("\n\n")
                append(agentInstructions(workspaceRef?.name.orEmpty()))
            }
        }
        val turns = buildList {
            add(ChatTurn("system", systemPrompt))
            addAll(history)
        }

        val content = StringBuilder()
        val calls = mutableListOf<ToolCall>()
        var lastPersist = 0L
        try {
            engine.stream(
                config = config,
                request = ChatRequest(
                    model = conversation.modelId,
                    turns = turns,
                    maxTokens = 4096,
                    stream = true,
                    tools = tools,
                ),
            ).collect { event ->
                when (event) {
                    is ProviderEvent.Text -> content.append(event.value)
                    is ProviderEvent.ToolCallReady -> calls += event.call
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

            val text = content.toString().trimEnd()
            if (calls.isEmpty()) {
                if (text.isBlank()) throw ProviderErrors.emptyResponse()
                withContext(NonCancellable) {
                    conversations.upsertMessage(
                        assistant.copy(
                            content = text,
                            status = MessageStatus.COMPLETE.name,
                            error = null,
                        ),
                    )
                    conversations.touch(conversationId, text)
                }
                return
            }

            val needsApproval = calls.any { agentTools.isMutation(it.name) }
            withContext(NonCancellable) {
                conversations.upsertMessage(
                    assistant.copy(
                        content = text,
                        toolCalls = encodeCalls(calls),
                        status = if (needsApproval) {
                            MessageStatus.AWAITING_APPROVAL.name
                        } else {
                            MessageStatus.COMPLETE.name
                        },
                        error = null,
                    ),
                )
                conversations.touch(
                    conversationId,
                    text.ifBlank { calls.joinToString(", ") { it.name } },
                )
            }
            if (needsApproval) return

            executeCalls(conversationId, calls, approved = true)
            respond(conversationId, depth + 1)
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

    private suspend fun executeCalls(
        conversationId: String,
        calls: List<ToolCall>,
        approved: Boolean,
    ) {
        calls.forEach { call ->
            val outcome = if (approved) {
                agentTools.execute(call)
            } else {
                ToolOutcome(
                    content = "The user denied this tool call. Do not retry it without asking.",
                    isError = true,
                )
            }
            conversations.appendMessage(
                conversationId = conversationId,
                role = "tool",
                content = outcome.content.take(MAX_TOOL_RESULT_CHARS),
                toolCallId = call.id,
                toolName = call.name,
                toolError = outcome.isError,
            )
        }
    }

    private fun encodeCalls(calls: List<ToolCall>): String =
        runCatching { json.encodeToString(toolCallSerializer, calls) }.getOrDefault("")

    private fun decodeCalls(raw: String): List<ToolCall> =
        if (raw.isBlank()) {
            emptyList()
        } else {
            runCatching { json.decodeFromString(toolCallSerializer, raw) }.getOrDefault(emptyList())
        }

    private fun agentInstructions(workspaceName: String): String = buildString {
        appendLine("You are ORBIT's coding agent, working inside the user's workspace folder.")
        if (workspaceName.isNotBlank()) appendLine("Workspace name: $workspaceName")
        appendLine("Paths are relative to the workspace root. Never use absolute paths or '..'.")
        appendLine("Inspect files with list_files, read_file and search_files before editing.")
        appendLine(
            "write_file, create_file, create_folder, delete_path and rename_path require the " +
                "user's approval and may be denied.",
        )
        appendLine("Keep changes focused and explain what changed when you are done.")
    }.trim()

    private fun loadImages(attachments: String): List<ChatImage> {
        if (attachments.isBlank()) return emptyList()
        return attachments.lineSequence()
            .filter { it.isNotBlank() }
            .mapNotNull { path ->
                runCatching {
                    val file = File(path)
                    if (!file.isFile) return@runCatching null
                    ChatImage(
                        data = Base64.encodeToString(file.readBytes(), Base64.NO_WRAP),
                        mediaType = "image/jpeg",
                    )
                }.getOrNull()
            }
            .toList()
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
        const val MAX_IMAGES = 4
        const val MAX_TOOL_STEPS = 8
        const val MAX_TOOL_RESULT_CHARS = 30_000
        const val STREAM_PERSIST_INTERVAL_MS = 150L
        const val SYSTEM_PROMPT =
            "You are ORBIT, a concise and capable AI assistant. Use fenced code blocks with a language tag for code. " +
                "If you are unsure, say so instead of inventing details."
    }
}
