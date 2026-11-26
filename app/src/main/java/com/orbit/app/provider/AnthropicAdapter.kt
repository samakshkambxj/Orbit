package com.orbit.app.provider

import com.orbit.app.data.model.ChatRequest
import com.orbit.app.data.model.ChatTurn
import com.orbit.app.data.model.ProviderConfig
import com.orbit.app.data.model.ToolCall
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.buffer
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import okhttp3.Call
import okhttp3.Callback
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response

class AnthropicAdapter(
    private val client: OkHttpClient,
    private val json: Json,
) : ProviderAdapter {

    override fun stream(config: ProviderConfig, request: ChatRequest): Flow<ProviderEvent> = callbackFlow {
        val httpRequest = try {
            buildRequest(config, request, stream = true)
        } catch (error: Throwable) {
            close(error as? ProviderException ?: ProviderErrors.fromIo(error as? IOException ?: IOException(error)))
            return@callbackFlow
        }
        val call = clientFor(config).newCall(httpRequest)
        call.enqueue(object : Callback {
            override fun onFailure(call: Call, error: IOException) {
                close(ProviderErrors.fromIo(error))
            }

            override fun onResponse(call: Call, response: Response) {
                response.use { resp ->
                    if (!resp.isSuccessful) {
                        val body = runCatching { resp.body?.string().orEmpty() }.getOrDefault("")
                        close(ProviderErrors.fromHttp(resp.code, body))
                        return
                    }
                    val contentType = resp.header("Content-Type").orEmpty()
                    if (!contentType.contains("text/event-stream", ignoreCase = true)) {
                        val body = runCatching { resp.body?.string().orEmpty() }.getOrDefault("")
                        try {
                            val text = parseComplete(body)
                            trySend(ProviderEvent.Text(text))
                            trySend(ProviderEvent.Finished(null))
                            close()
                        } catch (error: Throwable) {
                            close(error as? ProviderException ?: ProviderErrors.fromIo(IOException(error)))
                        }
                        return
                    }
                    val source = resp.body?.source()
                    if (source == null) {
                        close(ProviderErrors.emptyResponse())
                        return
                    }
                    val reader = SseReader()
                    val toolState = ToolState()
                    try {
                        while (true) {
                            val line = source.readUtf8Line() ?: break
                            val payload = reader.accept(line) ?: continue
                            parseDelta(payload, toolState).forEach { trySend(it) }
                        }
                        reader.flush()?.let { payload ->
                            parseDelta(payload, toolState).forEach { trySend(it) }
                        }
                        close()
                    } catch (error: Throwable) {
                        close(error as? ProviderException ?: ProviderErrors.fromIo(IOException(error)))
                    }
                }
            }
        })
        awaitClose { call.cancel() }
    }.buffer(Channel.UNLIMITED)

    override suspend fun complete(config: ProviderConfig, request: ChatRequest): String =
        withContext(Dispatchers.IO) {
            val httpRequest = buildRequest(config, request, stream = false)
            clientFor(config).newCall(httpRequest).execute().use { response ->
                val body = response.body?.string().orEmpty()
                if (!response.isSuccessful) throw ProviderErrors.fromHttp(response.code, body)
                parseComplete(body)
            }
        }

    private fun buildRequest(config: ProviderConfig, request: ChatRequest, stream: Boolean): Request {
        val system = request.turns
            .filter { it.role.equals("system", ignoreCase = true) }
            .joinToString("\n\n") { it.content }
            .trim()
        val messages = mergeMessages(request.turns)
        val body = buildJsonObject {
            put("model", request.model)
            put("max_tokens", request.maxTokens.coerceIn(1, 131_072))
            put("stream", stream)
            request.temperature?.let { put("temperature", it) }
            if (system.isNotEmpty()) put("system", system)
            putJsonArray("messages") {
                messages.forEach { message ->
                    add(
                        buildJsonObject {
                            put("role", message.role)
                            putJsonArray("content") {
                                message.blocks.forEach { block -> add(block) }
                            }
                        },
                    )
                }
            }
            if (request.tools.isNotEmpty()) {
                putJsonArray("tools") {
                    request.tools.forEach { tool ->
                        add(
                            buildJsonObject {
                                put("name", tool.name)
                                put("description", tool.description)
                                put("input_schema", tool.asJsonSchema())
                            },
                        )
                    }
                }
            }
        }
        val builder = Request.Builder()
            .url(providerEndpoint(config.baseUrl, "messages"))
            .post(body.toString().toRequestBody(JSON_MEDIA_TYPE))
            .header("Content-Type", "application/json")
            .header("Accept", if (stream) "text/event-stream" else "application/json")
            .header("anthropic-version", ANTHROPIC_VERSION)
        config.apiKey?.takeIf { it.isNotBlank() }?.let { key ->
            builder.header("x-api-key", key)
        }
        return builder.build()
    }

    private data class AnthropicMessage(
        val role: String,
        val blocks: List<JsonObject>,
    )

    private fun mergeMessages(turns: List<ChatTurn>): List<AnthropicMessage> {
        val merged = mutableListOf<AnthropicMessage>()
        turns.forEach { turn ->
            val message = when (turn.role.lowercase()) {
                "user" -> AnthropicMessage("user", userBlocks(turn))
                "assistant" -> AnthropicMessage("assistant", assistantBlocks(turn))
                "tool" -> AnthropicMessage(
                    role = "user",
                    blocks = listOf(
                        buildJsonObject {
                            put("type", "tool_result")
                            put("tool_use_id", turn.toolCallId)
                            put("content", turn.content)
                            if (turn.toolError) put("is_error", true)
                        },
                    ),
                )
                else -> return@forEach
            }
            if (message.blocks.isEmpty()) return@forEach
            val last = merged.lastOrNull()
            if (last != null && last.role == message.role) {
                merged[merged.lastIndex] = last.copy(blocks = last.blocks + message.blocks)
            } else {
                merged += message
            }
        }
        while (merged.isNotEmpty() && merged.first().role != "user") merged.removeAt(0)
        return merged
    }

    private fun userBlocks(turn: ChatTurn): List<JsonObject> = buildList {
        turn.images.forEach { image ->
            add(
                buildJsonObject {
                    put("type", "image")
                    putJsonObject("source") {
                        put("type", "base64")
                        put("media_type", image.mediaType)
                        put("data", image.data)
                    }
                },
            )
        }
        if (turn.content.isNotBlank()) {
            add(
                buildJsonObject {
                    put("type", "text")
                    put("text", turn.content)
                },
            )
        }
    }

    private fun assistantBlocks(turn: ChatTurn): List<JsonObject> = buildList {
        if (turn.content.isNotBlank()) {
            add(
                buildJsonObject {
                    put("type", "text")
                    put("text", turn.content)
                },
            )
        }
        turn.toolCalls.forEach { call ->
            add(
                buildJsonObject {
                    put("type", "tool_use")
                    put("id", call.id)
                    put("name", call.name)
                    put(
                        "input",
                        runCatching { json.parseToJsonElement(call.arguments) }
                            .getOrElse { buildJsonObject {} },
                    )
                },
            )
        }
    }

    private fun parseDelta(
        payload: String,
        toolState: ToolState,
    ): List<ProviderEvent> {
        val root = runCatching { json.parseToJsonElement(payload).jsonObject }.getOrNull()
            ?: return emptyList()
        val index = root["index"]?.let {
            runCatching { it.jsonPrimitive.content.toInt() }.getOrNull()
        } ?: 0
        return when (root["type"].textOrNull()) {
            "content_block_start" -> {
                val block = root["content_block"]?.let { runCatching { it.jsonObject }.getOrNull() }
                if (block != null && block["type"].textOrNull() == "tool_use") {
                    toolState.start(
                        index = index,
                        id = block["id"].textOrNull().orEmpty(),
                        name = block["name"].textOrNull().orEmpty(),
                    )
                }
                emptyList()
            }
            "content_block_delta" -> {
                val delta = root["delta"]?.let { runCatching { it.jsonObject }.getOrNull() }
                when (delta?.get("type").textOrNull()) {
                    "text_delta" -> delta?.get("text").textOrNull()
                        ?.takeIf { it.isNotEmpty() }
                        ?.let { listOf(ProviderEvent.Text(it)) }
                        .orEmpty()
                    "thinking_delta" -> delta?.get("thinking").textOrNull()
                        ?.takeIf { it.isNotEmpty() }
                        ?.let { listOf(ProviderEvent.Reasoning(it)) }
                        .orEmpty()
                    "input_json_delta" -> {
                        delta?.get("partial_json").textOrNull()?.let {
                            toolState.append(index, it)
                        }
                        emptyList()
                    }
                    else -> emptyList()
                }
            }
            "content_block_stop" -> toolState.stop(index)
                ?.let { listOf(ProviderEvent.ToolCallReady(it)) }
                .orEmpty()
            "message_delta" -> {
                val reason = root["delta"]?.let { runCatching { it.jsonObject }.getOrNull() }
                    ?.get("stop_reason").textOrNull()
                listOf(ProviderEvent.Finished(reason))
            }
            "message_stop" -> listOf(ProviderEvent.Finished(null))
            "error" -> {
                val detail = root["error"]?.let { runCatching { it.jsonObject }.getOrNull() }
                    ?.get("message").textOrNull()
                throw ProviderException(
                    ProviderErrorKind.UNKNOWN,
                    "Provider error: ${ProviderErrors.sanitize(detail ?: "unknown error")}",
                )
            }
            else -> emptyList()
        }
    }

    private class ToolState {
        private data class Block(
            val id: String,
            val name: String,
            val json: StringBuilder = StringBuilder(),
        )

        private val blocks = mutableMapOf<Int, Block>()

        fun start(index: Int, id: String, name: String) {
            if (name.isNotBlank()) blocks[index] = Block(id, name)
        }

        fun append(index: Int, partial: String) {
            blocks[index]?.json?.append(partial)
        }

        fun stop(index: Int): ToolCall? {
            val block = blocks.remove(index) ?: return null
            return ToolCall(
                id = block.id.ifBlank { "tool_${java.util.UUID.randomUUID()}" },
                name = block.name,
                arguments = block.json.toString().ifBlank { "{}" },
            )
        }
    }

    private fun parseComplete(raw: String): String {
        val root = runCatching { json.parseToJsonElement(raw).jsonObject }.getOrNull()
            ?: throw ProviderErrors.errorFromPayload(raw)
        if (root["type"].textOrNull() == "error" || root["error"] != null) {
            val detail = root["error"]?.let { runCatching { it.jsonObject }.getOrNull() }
                ?.get("message").textOrNull()
                ?: root["error"].textOrNull()
            throw ProviderErrors.errorFromPayload(detail ?: raw)
        }
        val blocks = root["content"]?.let { runCatching { it.jsonArray }.getOrNull() }.orEmpty()
        val text = buildString {
            blocks.forEach { item ->
                val block = runCatching { item.jsonObject }.getOrNull() ?: return@forEach
                if (block["type"].textOrNull() == "text") {
                    append(block["text"].textOrNull().orEmpty())
                }
            }
        }
        if (text.isBlank()) throw ProviderErrors.emptyResponse()
        return text
    }

    private fun clientFor(config: ProviderConfig): OkHttpClient =
        client.newBuilder()
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(config.timeoutMs.coerceIn(5_000, 600_000), TimeUnit.MILLISECONDS)
            .writeTimeout(config.timeoutMs.coerceIn(5_000, 600_000), TimeUnit.MILLISECONDS)
            .build()

    private fun JsonElement?.textOrNull(): String? = when (this) {
        is JsonPrimitive -> contentOrNull
        else -> null
    }

    private companion object {
        const val ANTHROPIC_VERSION = "2023-06-01"
        val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
    }
}
