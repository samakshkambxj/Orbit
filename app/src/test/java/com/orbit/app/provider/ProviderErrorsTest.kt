package com.orbit.app.provider

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ProviderEndpointTest {
    @Test
    fun `adds the version prefix to a bare host`() {
        assertEquals(
            "https://api.openai.com/v1/chat/completions",
            providerEndpoint("https://api.openai.com", "chat/completions"),
        )
    }

    @Test
    fun `keeps an existing version prefix`() {
        assertEquals(
            "https://example.com/v1/messages",
            providerEndpoint("https://example.com/v1/", "messages"),
        )
    }

    @Test
    fun `keeps an openai compatible prefix`() {
        assertEquals(
            "https://example.com/openai/chat/completions",
            providerEndpoint("https://example.com/openai", "chat/completions"),
        )
    }

    @Test
    fun `keeps an api prefix`() {
        assertEquals(
            "https://example.com/api/chat/completions",
            providerEndpoint("https://example.com/api", "chat/completions"),
        )
    }
}

class ProviderErrorsTest {
    @Test
    fun `maps authentication failures`() {
        assertEquals(ProviderErrorKind.AUTH, ProviderErrors.fromHttp(401, "bad key").kind)
        assertEquals(ProviderErrorKind.AUTH, ProviderErrors.fromHttp(403, "forbidden").kind)
    }

    @Test
    fun `maps rate limits and server errors as retryable`() {
        val rateLimit = ProviderErrors.fromHttp(429, "slow down")
        assertEquals(ProviderErrorKind.RATE_LIMIT, rateLimit.kind)
        assertTrue(rateLimit.retryable)

        val server = ProviderErrors.fromHttp(503, "unavailable")
        assertEquals(ProviderErrorKind.SERVER, server.kind)
        assertTrue(server.retryable)
    }

    @Test
    fun `maps other client errors`() {
        assertEquals(ProviderErrorKind.INVALID_REQUEST, ProviderErrors.fromHttp(400, "bad body").kind)
    }

    @Test
    fun `sanitizes and truncates error bodies`() {
        val detail = ProviderErrors.sanitize("  line one\n\nline two  ")

        assertEquals("line one line two", detail)
        assertTrue(ProviderErrors.sanitize("x".repeat(2_000)).length <= 600)
    }
}
