package com.orbit.app.chat

import com.orbit.app.data.model.ToolCall
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

private val toolJson = Json { ignoreUnknownKeys = true }

fun decodeStoredToolCalls(raw: String): List<ToolCall> =
    if (raw.isBlank()) {
        emptyList()
    } else {
        runCatching {
            toolJson.decodeFromString(ListSerializer(ToolCall.serializer()), raw)
        }.getOrDefault(emptyList())
    }

fun describeToolCall(call: ToolCall): String {
    val args = runCatching { toolJson.parseToJsonElement(call.arguments).jsonObject }
        .getOrNull()
        ?: return ""
    val path = args["path"]?.jsonPrimitive?.contentOrNull.orEmpty()
    val query = args["query"]?.jsonPrimitive?.contentOrNull.orEmpty()
    val name = args["new_name"]?.jsonPrimitive?.contentOrNull.orEmpty()
    return when {
        query.isNotBlank() && path.isNotBlank() -> "“$query” in $path"
        query.isNotBlank() -> "“$query”"
        path.isNotBlank() && name.isNotBlank() -> "$path → $name"
        path.isNotBlank() -> path
        else -> ""
    }
}
