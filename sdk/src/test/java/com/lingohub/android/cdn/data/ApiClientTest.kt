package com.lingohub.android.cdn.data

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ApiClientTest {

    @Test
    fun `client never follows cross-scheme redirects`() {
        val client = Api.buildHttpClient()

        // An HTTPS download URL must stay HTTPS across redirects; same-scheme
        // redirects (e.g. object-storage 307s) remain allowed.
        assertFalse(client.followSslRedirects)
        assertTrue(client.followRedirects)
    }

    @Test
    fun `download request line has its query string redacted`() {
        val line = "--> GET https://cdn.example.com/bundle.zip?X-Amz-Signature=SENTINEL_SECRET&X-Amz-Expires=300 http/1.1"

        val sanitized = sanitizeLogLine(line)

        assertFalse(sanitized.contains("SENTINEL_SECRET"))
        assertEquals("--> GET https://cdn.example.com/bundle.zip?<redacted> http/1.1", sanitized)
    }

    @Test
    fun `check response body has presigned url query redacted`() {
        val body = """{"distributionReleaseId":"1","filesUrl":"https://s3.example.com/b.zip?X-Amz-Signature=SENTINEL_SECRET"}"""

        val sanitized = sanitizeLogLine(body)

        assertFalse(sanitized.contains("SENTINEL_SECRET"))
        assertEquals("""{"distributionReleaseId":"1","filesUrl":"https://s3.example.com/b.zip?<redacted>"}""", sanitized)
    }

    @Test
    fun `log lines without a query string are unchanged`() {
        val line = "<-- 200 https://cdn.lingohub.com/v1/distributions/check (123ms)"
        assertEquals(line, sanitizeLogLine(line))
    }
}
