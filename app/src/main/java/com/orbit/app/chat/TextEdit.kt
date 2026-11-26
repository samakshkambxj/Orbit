package com.orbit.app.chat

data class TextEditResult(
    val text: String,
    val replacements: Int,
)

/**
 * Applies a literal search-and-replace edit. When [replaceAll] is false the
 * old text must appear exactly once, so the model cannot silently change the
 * wrong section of a file.
 */
fun applyTextEdit(
    content: String,
    oldText: String,
    newText: String,
    replaceAll: Boolean = false,
): Result<TextEditResult> = runCatching {
    require(oldText.isNotEmpty()) { "old_text cannot be empty." }
    val occurrences = countOccurrences(content, oldText)
    require(occurrences > 0) { "old_text was not found in the file." }
    if (!replaceAll && occurrences > 1) {
        error(
            "old_text appears $occurrences times. Include more context or set replace_all to true.",
        )
    }
    val updated = if (replaceAll) {
        content.replace(oldText, newText)
    } else {
        content.replaceFirst(oldText, newText)
    }
    TextEditResult(text = updated, replacements = occurrences)
}

private fun countOccurrences(content: String, needle: String): Int {
    var count = 0
    var index = content.indexOf(needle)
    while (index >= 0) {
        count += 1
        index = content.indexOf(needle, index + needle.length)
    }
    return count
}
