package com.orbit.app.data.model

import kotlinx.serialization.Serializable

enum class ApiFormat {
    OPENAI,
    ANTHROPIC;

    val label: String
        get() = when (this) {
            OPENAI -> "OpenAI-compatible"
            ANTHROPIC -> "Anthropic Messages"
        }
}

enum class ThemeMode { SYSTEM, LIGHT, DARK }

enum class MessageStatus { COMPLETE, STREAMING, AWAITING_APPROVAL, ERROR, CANCELLED }

data class ProviderConfig(
    val id: String,
    val name: String,
    val baseUrl: String,
    val apiFormat: ApiFormat,
    val apiKey: String?,
    val timeoutMs: Long = 120_000,
) {
    override fun toString(): String =
        "ProviderConfig(id=$id, name=$name, baseUrl=$baseUrl, apiFormat=$apiFormat, hasKey=${!apiKey.isNullOrBlank()})"
}

data class ProviderInput(
    val id: String? = null,
    val name: String,
    val baseUrl: String,
    val apiFormat: ApiFormat,
    val apiKey: String? = null,
    val modelId: String = "",
    val modelDisplayName: String = "",
)

data class ModelInfo(
    val providerId: String,
    val providerName: String,
    val modelId: String,
    val displayName: String,
)

data class ChatImage(
    val data: String,
    val mediaType: String = "image/jpeg",
)

@Serializable
data class ToolCall(
    val id: String,
    val name: String,
    val arguments: String = "{}",
)

data class ToolSpec(
    val name: String,
    val description: String,
    val parameters: kotlinx.serialization.json.JsonObject,
    val required: List<String> = emptyList(),
)

data class ChatTurn(
    val role: String,
    val content: String,
    val images: List<ChatImage> = emptyList(),
    val toolCalls: List<ToolCall> = emptyList(),
    val toolCallId: String = "",
    val toolName: String = "",
    val toolError: Boolean = false,
)

data class ChatRequest(
    val model: String,
    val turns: List<ChatTurn>,
    val maxTokens: Int = 4096,
    val temperature: Double? = null,
    val stream: Boolean = true,
    val tools: List<ToolSpec> = emptyList(),
)
