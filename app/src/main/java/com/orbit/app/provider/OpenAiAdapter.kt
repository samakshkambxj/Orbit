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

class OpenAiAdapter(
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
                    val toolCalls = ToolCallAccumulator()
                    try {
                        while (true) {
                            val line = source.readUtf8Line() ?: break
                            val payload = reader.accept(line) ?: continue
                            if (payload == "[DONE]") {
                                toolCalls.drain().forEach {
                                    trySend(ProviderEvent.ToolCallReady(it))
                                }
                                trySend(ProviderEvent.Finished(null))
                                close()
                                return
                            }
                            parseDelta(payload, toolCalls).forEach { trySend(it) }
                        }
                        reader.flush()?.let { payload ->
                            if (payload != "[DONE]") {
                                parseDelta(payload, toolCalls).forEach { trySend(it) }
                            }
                        }
                        toolCalls.drain().forEach { trySend(ProviderEvent.ToolCallReady(it)) }
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
        val body = buildJsonObject {
            put("model", request.model)
            put("stream", stream)
            put("max_tokens", request.maxTokens.coerceIn(1, 131_072))
            request.temperature?.let { put("temperature", it) }
            putJsonArray("messages") {
                request.turns.forEach { turn ->
                    add(
                        buildJsonObject {
                            val role = normalizeRole(turn.role)
                            put("role", role)
                            when {
                                role == "tool" -> {
                                    put("tool_call_id", turn.toolCallId)
                                    put("content", turn.content)
                                }
                                turn.toolCalls.isNotEmpty() -> {
                                    put("content", turn.content)
                                    putJsonArray("tool_calls") {
                                        turn.toolCalls.forEach { call ->
                                            add(
                                                buildJsonObject {
                                                    put("id", call.id)
                                                    put("type", "function")
                                                    putJsonObject("function") {
                                                        put("name", call.name)
                                                        put("arguments", call.arguments)
                                                    }
                                                },
                                            )
                                        }
                                    }
                                }
                                turn.images.isEmpty() -> put("content", turn.content)
                                else -> {
                                    putJsonArray("content") {
                                        if (turn.content.isNotBlank()) {
                                            add(
                                                buildJsonObject {
                                                    put("type", "text")
                                                    put("text", turn.content)
                                                },
                                            )
                                        }
                                        turn.images.forEach { image ->
                                            add(
                                                buildJsonObject {
                                                    put("type", "image_url")
                                                    putJsonObject("image_url") {
                                                        put(
                                                            "url",
                                                            "data:${image.mediaType};base64,${image.data}",
                                                        )
                                                    }
                                                },
                                            )
                                        }
                                    }
                                }
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
                                put("type", "function")
                                putJsonObject("function") {
                                    put("name", tool.name)
                                    put("description", tool.description)
                                    put("parameters", tool.asJsonSchema())
                                }
                            },
                        )
                    }
                }
            }
        }
        val builder = Request.Builder()
            .url(providerEndpoint(config.baseUrl, "chat/completions"))
            .post(body.toString().toRequestBody(JSON_MEDIA_TYPE))
            .header("Content-Type", "application/json")
            .header("Accept", if (stream) "text/event-stream" else "application/json")
        config.apiKey?.takeIf { it.isNotBlank() }?.let { key ->
            builder.header("Authorization", "Bearer $key")
        }
        return builder.build()
    }

    private fun parseDelta(
        payload: String,
        toolCalls: ToolCallAccumulator,
    ): List<ProviderEvent> {
        if (payload == "[DONE]") return listOf(ProviderEvent.Finished(null))
        val root = runCatching { json.parseToJsonElement(payload).jsonObject }.getOrNull()
            ?: return emptyList()
        root["error"]?.let { throw ProviderErrors.errorFromPayload(it.toString()) }
        val choice = root["choices"]?.let { element ->
            runCatching { element.jsonArray.firstOrNull()?.jsonObject }.getOrNull()
        } ?: return emptyList()
        val delta = choice["delta"]?.let { runCatching { it.jsonObject }.getOrNull() }
        delta?.let(toolCalls::add)
        val reasoning = delta?.get("reasoning_content").textOrNull()
            ?: delta?.get("reasoning").textOrNull()
        val text = contentText(delta?.get("content"))
        val finish = choice["finish_reason"].textOrNull()
        val events = mutableListOf<ProviderEvent>()
        if (!reasoning.isNullOrEmpty()) events += ProviderEvent.Reasoning(reasoning)
        if (text.isNotEmpty()) events += ProviderEvent.Text(text)
        if (!finish.isNullOrBlank()) {
            toolCalls.drain().forEach { events += ProviderEvent.ToolCallReady(it) }
            events += ProviderEvent.Finished(finish)
        }
        return events
    }

    private fun parseComplete(raw: String): String {
        val root = runCatching { json.parseToJsonElement(raw).jsonObject }.getOrNull()
            ?: throw ProviderErrors.errorFromPayload(raw)
        root["error"]?.let { throw ProviderErrors.errorFromPayload(it.toString()) }
        val choice = root["choices"]?.let { element ->
            runCatching { element.jsonArray.firstOrNull()?.jsonObject }.getOrNull()
        } ?: throw ProviderErrors.emptyResponse()
        val message = choice["message"]?.let { runCatching { it.jsonObject }.getOrNull() }
        val text = contentText(message?.get("content"))
        if (text.isBlank()) throw ProviderErrors.emptyResponse()
        return text
    }

    private fun contentText(element: JsonElement?): String {
        if (element == null) return ""
        (element as? JsonPrimitive)?.let { return it.contentOrNull.orEmpty() }
        val array = runCatching { element.jsonArray }.getOrNull() ?: return ""
        return array.joinToString("") { item ->
            val obj = runCatching { item.jsonObject }.getOrNull()
            obj?.get("text").textOrNull().orEmpty()
        }
    }

    private fun normalizeRole(role: String): String = when (role.lowercase()) {
        "system", "user", "assistant", "tool" -> role.lowercase()
        else -> "user"
    }

    private class ToolCallAccumulator {
        private data class Part(
            var id: String = "",
            var name: String = "",
            val arguments: StringBuilder = StringBuilder(),
        )

        private val parts = sortedMapOf<Int, Part>()

        fun add(delta: kotlinx.serialization.json.JsonObject) {
            val array = delta["tool_calls"]
                ?.let { runCatching { it.jsonArray }.getOrNull() }
                ?: return
            array.forEach { item ->
                val obj = runCatching { item.jsonObject }.getOrNull() ?: return@forEach
                val index = obj["index"]
                    ?.let { runCatching { it.jsonPrimitive.content.toInt() }.getOrNull() }
                    ?: 0
                val part = parts.getOrPut(index) { Part() }
                obj["id"]?.textOrNull()?.takeIf { it.isNotBlank() }?.let { part.id = it }
                val function = obj["function"]
                    ?.let { runCatching { it.jsonObject }.getOrNull() }
                function?.get("name")?.textOrNull()
                    ?.takeIf { it.isNotBlank() }
                    ?.let { part.name = it }
                function?.get("arguments")?.textOrNull()?.let { part.arguments.append(it) }
            }
        }

        fun drain(): List<ToolCall> {
            val calls = parts.values
                .filter { it.name.isNotBlank() }
                .map { part ->
                    ToolCall(
                        id = part.id.ifBlank { "call_${java.util.UUID.randomUUID()}" },
                        name = part.name,
                        arguments = part.arguments.toString().ifBlank { "{}" },
                    )
                }
            parts.clear()
            return calls
        }

        private fun JsonElement?.textOrNull(): String? = when (this) {
            is JsonPrimitive -> contentOrNull
            else -> null
        }
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
        val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
    }
}
