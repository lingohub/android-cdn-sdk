package com.lingohub.android.cdn.utils

import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/**
 * String lookups log through debug {} and warn {} on every call, so their
 * messages must only be built when logging is on.
 */
class LingoHubLoggerTest {

    @AfterEach
    fun tearDown() {
        LingoHubLogger.init(LingoHubLogLevel.NONE)
    }

    @Test
    fun `messages are not built at NONE`() {
        LingoHubLogger.init(LingoHubLogLevel.NONE)
        var built = 0

        LingoHubLogger.debug { built++; "debug" }
        LingoHubLogger.warn(null) { built++; "warn" }

        assertEquals(0, built)
    }

    @Test
    fun `messages are built at FULL`() {
        LingoHubLogger.init(LingoHubLogLevel.FULL)
        var built = 0

        LingoHubLogger.debug { built++; "debug" }
        LingoHubLogger.warn(null) { built++; "warn" }

        assertEquals(2, built)
    }
}
