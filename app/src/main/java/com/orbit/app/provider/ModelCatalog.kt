package com.orbit.app.provider

import com.orbit.app.data.model.ApiFormat
import com.orbit.app.data.model.ProviderConfig
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import okhttp3.OkHttpClient
import okhttp3.Request

/** Reads the provider's model list so users do not have to type IDs by hand. */
class ModelCatalog(
    private val client: OkHttpClient,
    private val json: Json,
) {
    suspend fun fetch(config: ProviderConfig): List<String> = withContext(Dispatchers.IO) {
        val builder = Request.Builder()
            .url(providerEndpoint(config.baseUrl, "models"))
            .get()
            .header("Accept", "application/json")
        when (config.apiFormat) {
            ApiFormat.OPENAI -> config.apiKey?.takeIf { it.isNotBlank() }?.let { key ->
                builder.header("Authorization", "Bearer $key")
            }
            ApiFormat.ANTHROPIC -> {
                config.apiKey?.takeIf { it.isNotBlank() }?.let { key ->
                    builder.header("x-api-key", key)
                }
                builder.header("anthropic-version", "2023-06-01")
            }
        }

        try {
            client.newCall(builder.build()).execute().use { response ->
                val body = response.body?.string().orEmpty()
                if (!response.isSuccessful) throw ProviderErrors.fromHttp(response.code, body)
                val root = runCatching { json.parseToJsonElement(body).jsonObject }.getOrNull()
                    ?: throw ProviderErrors.errorFromPayload(body)
                root["error"]?.let { throw ProviderErrors.errorFromPayload(it.toString()) }
                val data = root["data"]
                    ?.let { runCatching { it.jsonArray }.getOrNull() }
                    .orEmpty()
                data.mapNotNull { item ->
                    val obj = runCatching { item.jsonObject }.getOrNull()
                    (obj?.get("id") as? JsonPrimitive)
                        ?.contentOrNull
                        ?.takeIf { it.isNotBlank() }
                }.distinct().sorted()
            }
        } catch (error: IOException) {
            throw ProviderErrors.fromIo(error)
        }
    }
}
