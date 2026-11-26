package com.orbit.app.chat

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TextEditTest {
    @Test
    fun replacesSingleOccurrence() {
        val result = applyTextEdit("val a = 1\nval b = 2", "val a = 1", "val a = 10")
            .getOrThrow()
        assertEquals("val a = 10\nval b = 2", result.text)
        assertEquals(1, result.replacements)
    }

    @Test
    fun rejectsAmbiguousEdit() {
        val result = applyTextEdit("a\na", "a", "b")
        assertTrue(result.isFailure)
    }

    @Test
    fun replacesEveryOccurrenceWhenAsked() {
        val result = applyTextEdit("a\na", "a", "b", replaceAll = true).getOrThrow()
        assertEquals("b\nb", result.text)
        assertEquals(2, result.replacements)
    }

    @Test
    fun rejectsMissingText() {
        assertTrue(applyTextEdit("hello", "goodbye", "hi").isFailure)
    }

    @Test
    fun rejectsEmptySearchText() {
        assertTrue(applyTextEdit("hello", "", "hi").isFailure)
    }

    @Test
    fun countsNonOverlappingMatches() {
        val result = applyTextEdit("aaaa", "aa", "b", replaceAll = true).getOrThrow()
        assertEquals("bb", result.text)
        assertEquals(2, result.replacements)
    }
}
