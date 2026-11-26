package com.orbit.app.provider

import com.orbit.app.data.model.ApiFormat
import com.orbit.app.data.model.ChatRequest
import com.orbit.app.data.model.ProviderConfig
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient

class ProviderEngine(
    private val client: OkHttpClient,
    private val json: Json,
) {
    private val openAi = OpenAiAdapter(client, json)
    private val anthropic = AnthropicAdapter(client, json)

    fun stream(config: ProviderConfig, request: ChatRequest): Flow<ProviderEvent> =
        adapter(config).stream(config, request)

    suspend fun complete(config: ProviderConfig, request: ChatRequest): String =
        adapter(config).complete(config, request)

    private fun adapter(config: ProviderConfig): ProviderAdapter = when (config.apiFormat) {
        ApiFormat.OPENAI -> openAi
        ApiFormat.ANTHROPIC -> anthropic
    }
}

interface ProviderAdapter {
    fun stream(config: ProviderConfig, request: ChatRequest): Flow<ProviderEvent>
    suspend fun complete(config: ProviderConfig, request: ChatRequest): String
}
