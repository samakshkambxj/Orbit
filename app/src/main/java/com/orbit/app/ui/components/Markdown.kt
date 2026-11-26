package com.orbit.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

private sealed interface MarkdownBlock {
    data class TextBlock(val text: String) : MarkdownBlock
    data class CodeBlock(val language: String, val code: String) : MarkdownBlock
}

@Composable
fun MarkdownText(text: String, modifier: Modifier = Modifier) {
    val blocks = remember(text) { parseBlocks(text) }
    val codeBackground = MaterialTheme.colorScheme.surfaceVariant
    val inlineCode = MaterialTheme.colorScheme.surfaceVariant
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        blocks.forEach { block ->
            when (block) {
                is MarkdownBlock.TextBlock -> SelectionContainer {
                    Text(
                        text = annotate(block.text, inlineCode),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }
                is MarkdownBlock.CodeBlock -> CodeBlock(block.language, block.code, codeBackground)
            }
        }
    }
}

@Composable
private fun CodeBlock(language: String, code: String, background: Color) {
    val clipboard = LocalClipboardManager.current
    var copied by remember { mutableStateOf(false) }

    LaunchedEffect(copied) {
        if (copied) {
            delay(1500)
            copied = false
        }
    }

    Surface(
        color = background,
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 14.dp, end = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = language.ifBlank { "code" }.lowercase(),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
                IconButton(
                    onClick = {
                        clipboard.setText(AnnotatedString(code))
                        copied = true
                    },
                ) {
                    Icon(
                        imageVector = Icons.Outlined.ContentCopy,
                        contentDescription = if (copied) "Copied" else "Copy code",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Text(
                text = code,
                style = MaterialTheme.typography.bodyMedium,
                fontFamily = FontFamily.Monospace,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier
                    .horizontalScroll(rememberScrollState())
                    .padding(start = 14.dp, end = 14.dp, bottom = 14.dp),
            )
        }
    }
}

private fun parseBlocks(source: String): List<MarkdownBlock> {
    val blocks = mutableListOf<MarkdownBlock>()
    val buffer = StringBuilder()
    val lines = source.lines()
    var index = 0

    fun flushText() {
        val value = buffer.toString().trim('\n')
        if (value.isNotBlank()) blocks += MarkdownBlock.TextBlock(value)
        buffer.clear()
    }

    while (index < lines.size) {
        val line = lines[index]
        if (line.trimStart().startsWith("```")) {
            flushText()
            val language = line.trimStart().removePrefix("```").trim()
            val code = StringBuilder()
            index++
            while (index < lines.size && !lines[index].trimStart().startsWith("```")) {
                code.appendLine(lines[index])
                index++
            }
            blocks += MarkdownBlock.CodeBlock(language, code.toString().trimEnd('\n'))
            index++
        } else {
            buffer.appendLine(line)
            index++
        }
    }
    flushText()
    return blocks
}

private fun annotate(source: String, codeBackground: Color): AnnotatedString = buildAnnotatedString {
    var index = 0
    while (index < source.length) {
        when {
            source.startsWith("**", index) -> {
                val end = source.indexOf("**", index + 2)
                if (end > index + 2) {
                    withStyle(SpanStyle(fontWeight = FontWeight.SemiBold)) {
                        append(source.substring(index + 2, end))
                    }
                    index = end + 2
                } else {
                    append(source[index])
                    index++
                }
            }
            source[index] == '`' -> {
                val end = source.indexOf('`', index + 1)
                if (end > index + 1) {
                    withStyle(
                        SpanStyle(
                            fontFamily = FontFamily.Monospace,
                            background = codeBackground,
                            fontSize = 14.sp,
                        ),
                    ) {
                        append(source.substring(index + 1, end))
                    }
                    index = end + 1
                } else {
                    append(source[index])
                    index++
                }
            }
            source[index] == '*' -> {
                val end = source.indexOf('*', index + 1)
                if (end > index + 1) {
                    withStyle(SpanStyle(fontStyle = FontStyle.Italic)) {
                        append(source.substring(index + 1, end))
                    }
                    index = end + 1
                } else {
                    append(source[index])
                    index++
                }
            }
            else -> {
                append(source[index])
                index++
            }
        }
    }
}
