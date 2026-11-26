package com.orbit.app.provider

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SseReaderTest {
    @Test
    fun `joins multi line data until a blank line`() {
        val reader = SseReader()

        assertNull(reader.accept("data: hello"))
        assertNull(reader.accept("data: world"))
        assertEquals("hello\nworld", reader.accept(""))
    }

    @Test
    fun `ignores comments and non data fields`() {
        val reader = SseReader()

        assertNull(reader.accept(": keep-alive"))
        assertNull(reader.accept("event: message"))
        assertNull(reader.accept("id: 42"))
        assertNull(reader.accept("data: payload"))
        assertEquals("payload", reader.accept(""))
    }

    @Test
    fun `keeps a single payload across blank lines`() {
        val reader = SseReader()

        assertNull(reader.accept("data: first"))
        assertEquals("first", reader.accept(""))
        assertNull(reader.accept(""))
    }

    @Test
    fun `flush returns the pending payload`() {
        val reader = SseReader()

        assertNull(reader.accept("data: trailing"))
        assertEquals("trailing", reader.flush())
        assertNull(reader.flush())
    }

    @Test
    fun `flush returns null when there is nothing buffered`() {
        val reader = SseReader()

        assertNull(reader.accept(": comment"))
        assertNull(reader.flush())
    }
}
