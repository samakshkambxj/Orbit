package com.orbit.app.provider

/** Joins SSE `data:` lines until a blank line, as required by the SSE format. */
class SseReader {
    private val data = StringBuilder()

    fun accept(line: String): String? {
        if (line.isEmpty()) return flush()
        if (line.startsWith(":")) return null
        if (line.startsWith("data:")) {
            if (data.isNotEmpty()) data.append('\n')
            data.append(line.removePrefix("data:").trimStart())
        }
        return null
    }

    fun flush(): String? {
        if (data.isEmpty()) return null
        val payload = data.toString()
        data.clear()
        return payload
    }
}
