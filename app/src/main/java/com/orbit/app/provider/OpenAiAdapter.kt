package com.orbit.app.provider

import com.orbit.app.data.model.ChatRequest
import com.orbit.app.data.model.ProviderConfig
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
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
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
                    try {
                        while (true) {
                            val line = source.readUtf8Line() ?: break
                            val payload = reader.accept(line) ?: continue
                            if (payload == "[DONE]") {
                                trySend(ProviderEvent.Finished(null))
                                close()
                                return
                            }
                            parseDelta(payload).forEach { trySend(it) }
                        }
                        reader.flush()?.let { payload ->
                            if (payload != "[DONE]") parseDelta(payload).forEach { trySend(it) }
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
        val body = buildJsonObject {
            put("model", request.model)
            put("stream", stream)
            put("max_tokens", request.maxTokens.coerceIn(1, 131_072))
            request.temperature?.let { put("temperature", it) }
            putJsonArray("messages") {
                request.turns.forEach { turn ->
                    add(
                        buildJsonObject {
                            put("role", normalizeRole(turn.role))
                            put("content", turn.content)
                        },
                    )
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

    private fun parseDelta(payload: String): List<ProviderEvent> {
        if (payload == "[DONE]") return listOf(ProviderEvent.Finished(null))
        val root = runCatching { json.parseToJsonElement(payload).jsonObject }.getOrNull()
            ?: return emptyList()
        root["error"]?.let { throw ProviderErrors.errorFromPayload(it.toString()) }
        val choice = root["choices"]?.let { element ->
            runCatching { element.jsonArray.firstOrNull()?.jsonObject }.getOrNull()
        } ?: return emptyList()
        val delta = choice["delta"]?.let { runCatching { it.jsonObject }.getOrNull() }
        val text = contentText(delta?.get("content"))
        val finish = choice["finish_reason"].textOrNull()
        val events = mutableListOf<ProviderEvent>()
        if (text.isNotEmpty()) events += ProviderEvent.Text(text)
        if (!finish.isNullOrBlank()) events += ProviderEvent.Finished(finish)
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
        "system", "user", "assistant" -> role.lowercase()
        else -> "user"
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
