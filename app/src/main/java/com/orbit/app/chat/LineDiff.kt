package com.orbit.app.chat

enum class DiffKind { SAME, ADDED, REMOVED }

data class DiffLine(
    val kind: DiffKind,
    val text: String,
)

/**
 * A small line diff used to preview agent edits before they are approved.
 * Very large files fall back to a cheap prefix/suffix diff so the UI stays
 * responsive.
 */
fun diffLines(old: String, new: String, maxLines: Int = 400): List<DiffLine> {
    val oldLines = old.lines()
    val newLines = new.lines()
    if (oldLines.size > maxLines || newLines.size > maxLines) {
        return prefixSuffixDiff(oldLines, newLines)
    }

    val lcs = Array(oldLines.size + 1) { IntArray(newLines.size + 1) }
    for (i in oldLines.lastIndex downTo 0) {
        for (j in newLines.lastIndex downTo 0) {
            lcs[i][j] = if (oldLines[i] == newLines[j]) {
                lcs[i + 1][j + 1] + 1
            } else {
                maxOf(lcs[i + 1][j], lcs[i][j + 1])
            }
        }
    }

    val result = mutableListOf<DiffLine>()
    var i = 0
    var j = 0
    while (i < oldLines.size && j < newLines.size) {
        when {
            oldLines[i] == newLines[j] -> {
                result += DiffLine(DiffKind.SAME, oldLines[i])
                i += 1
                j += 1
            }
            lcs[i + 1][j] >= lcs[i][j + 1] -> {
                result += DiffLine(DiffKind.REMOVED, oldLines[i])
                i += 1
            }
            else -> {
                result += DiffLine(DiffKind.ADDED, newLines[j])
                j += 1
            }
        }
    }
    while (i < oldLines.size) {
        result += DiffLine(DiffKind.REMOVED, oldLines[i])
        i += 1
    }
    while (j < newLines.size) {
        result += DiffLine(DiffKind.ADDED, newLines[j])
        j += 1
    }
    return result
}

private fun prefixSuffixDiff(oldLines: List<String>, newLines: List<String>): List<DiffLine> {
    var prefix = 0
    while (prefix < oldLines.size && prefix < newLines.size && oldLines[prefix] == newLines[prefix]) {
        prefix += 1
    }
    var suffix = 0
    while (
        suffix < oldLines.size - prefix &&
        suffix < newLines.size - prefix &&
        oldLines[oldLines.lastIndex - suffix] == newLines[newLines.lastIndex - suffix]
    ) {
        suffix += 1
    }

    val result = mutableListOf<DiffLine>()
    oldLines.take(prefix).forEach { result += DiffLine(DiffKind.SAME, it) }
    oldLines.subList(prefix, oldLines.size - suffix)
        .forEach { result += DiffLine(DiffKind.REMOVED, it) }
    newLines.subList(prefix, newLines.size - suffix)
        .forEach { result += DiffLine(DiffKind.ADDED, it) }
    if (suffix > 0) {
        oldLines.takeLast(suffix).forEach { result += DiffLine(DiffKind.SAME, it) }
    }
    return result
}
