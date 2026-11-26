package com.orbit.app.chat

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LineDiffTest {
    @Test
    fun identicalTextHasNoChanges() {
        val diff = diffLines("a\nb\nc", "a\nb\nc")
        assertTrue(diff.all { it.kind == DiffKind.SAME })
        assertEquals(listOf("a", "b", "c"), diff.map { it.text })
    }

    @Test
    fun addedLineIsMarked() {
        val diff = diffLines("a\nc", "a\nb\nc")
        assertEquals(
            listOf(
                DiffKind.SAME to "a",
                DiffKind.ADDED to "b",
                DiffKind.SAME to "c",
            ),
            diff.map { it.kind to it.text },
        )
    }

    @Test
    fun removedLineIsMarked() {
        val diff = diffLines("a\nb\nc", "a\nc")
        assertEquals(
            listOf(
                DiffKind.SAME to "a",
                DiffKind.REMOVED to "b",
                DiffKind.SAME to "c",
            ),
            diff.map { it.kind to it.text },
        )
    }

    @Test
    fun replacementKeepsBothSides() {
        val diff = diffLines("val a = 1", "val a = 2")
        assertEquals(
            listOf(
                DiffKind.REMOVED to "val a = 1",
                DiffKind.ADDED to "val a = 2",
            ),
            diff.map { it.kind to it.text },
        )
    }

    @Test
    fun largeInputFallsBackButKeepsEdges() {
        val old = (1..1_000).joinToString("\n")
        val new = old + "\nappended"
        val diff = diffLines(old, new, maxLines = 50)
        assertEquals(DiffKind.SAME, diff.first().kind)
        assertEquals("appended", diff.last().text)
        assertEquals(DiffKind.ADDED, diff.last().kind)
    }
}
