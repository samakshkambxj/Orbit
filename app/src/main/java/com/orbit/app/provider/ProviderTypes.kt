package com.orbit.app.provider

import com.orbit.app.data.model.ToolCall
import com.orbit.app.data.model.ToolSpec
import java.io.IOException
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import java.net.SocketTimeoutException
import java.net.UnknownHostException

sealed interface ProviderEvent {
    data class Text(val value: String) : ProviderEvent
    data class ToolCallReady(val call: ToolCall) : ProviderEvent
    data class Finished(val reason: String?) : ProviderEvent
}

enum class ProviderErrorKind {
    AUTH,
    RATE_LIMIT,
    TIMEOUT,
    NETWORK,
    INVALID_REQUEST,
    SERVER,
    EMPTY_RESPONSE,
    INVALID_CONFIG,
    CANCELLED,
    UNKNOWN,
}

class ProviderException(
    val kind: ProviderErrorKind,
    override val message: String,
    val retryable: Boolean = false,
    cause: Throwable? = null,
) : Exception(message, cause)

object ProviderErrors {
    fun fromIo(error: IOException): ProviderException = when (error) {
        is SocketTimeoutException -> ProviderException(
            ProviderErrorKind.TIMEOUT,
            "The provider took too long to respond. Check the endpoint and try again.",
            retryable = true,
            cause = error,
        )
        is UnknownHostException -> ProviderException(
            ProviderErrorKind.NETWORK,
            "Could not reach the provider. Check your connection and base URL.",
            retryable = true,
            cause = error,
        )
        else -> ProviderException(
            ProviderErrorKind.NETWORK,
            error.message?.take(300)?.takeIf { it.isNotBlank() }
                ?: "Could not reach the provider.",
            retryable = true,
            cause = error,
        )
    }

    fun fromHttp(code: Int, body: String): ProviderException {
        val kind = when {
            code == 401 || code == 403 -> ProviderErrorKind.AUTH
            code == 429 -> ProviderErrorKind.RATE_LIMIT
            code in 400..499 -> ProviderErrorKind.INVALID_REQUEST
            code >= 500 -> ProviderErrorKind.SERVER
            else -> ProviderErrorKind.UNKNOWN
        }
        val detail = sanitize(body)
        val message = when {
            detail.isBlank() -> "Provider returned HTTP $code."
            else -> "Provider error ($code): $detail"
        }
        return ProviderException(kind, message, retryable = kind == ProviderErrorKind.RATE_LIMIT || kind == ProviderErrorKind.SERVER)
    }

    fun emptyResponse(): ProviderException = ProviderException(
        ProviderErrorKind.EMPTY_RESPONSE,
        "The model returned an empty response. Try again or switch models.",
        retryable = true,
    )

    fun invalidConfig(message: String): ProviderException =
        ProviderException(ProviderErrorKind.INVALID_CONFIG, message)

    fun sanitize(value: String): String =
        value.replace(Regex("\\s+"), " ").trim().take(600)

    fun errorFromPayload(raw: String): ProviderException {
        val detail = sanitize(raw)
        return ProviderException(
            ProviderErrorKind.INVALID_REQUEST,
            if (detail.isBlank()) "The provider returned an error." else "Provider error: $detail",
        )
    }
}

internal fun ToolSpec.asJsonSchema(): JsonObject = buildJsonObject {
    parameters.forEach { (key, value) -> put(key, value) }
    if (required.isNotEmpty()) {
        putJsonArray("required") { required.forEach { add(JsonPrimitive(it)) } }
    }
}

internal fun providerEndpoint(baseUrl: String, path: String): String {
    val base = baseUrl.trim().trimEnd('/')
    val alreadyVersioned = base.endsWith("/v1") ||
        base.endsWith("/openai") ||
        base.endsWith("/v1beta/openai") ||
        base.endsWith("/api")
    return if (alreadyVersioned) "$base/$path" else "$base/v1/$path"
}
