package com.lingohub.android.cdn.data

import android.content.Context
import com.lingohub.android.cdn.data.model.Environment
import com.lingohub.android.cdn.utils.InMemorySharedPreferences
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

/**
 * The retry and pause policy for update checks (lingohub/organization#2351), its persisted schedule,
 * and Retry-After parsing, without networking.
 */
class UpdatePolicyTest {
    private val minute = 60_000L
    private val hour = 60 * minute
    private val start = 1_800_000_000_000L
    private val scope = UpdateSchedule.Scope.of("1.0.0", Environment.PRODUCTION, "lh-cdn_key")

    // --- Retry after a 5xx ---

    @Test
    fun `retry delay without Retry-After is drawn from two to five seconds`() {
        assertEquals(2_000L, UpdatePolicy.serverErrorRetryDelayMs(null, randomFraction = 0.0))
        assertEquals(3_500L, UpdatePolicy.serverErrorRetryDelayMs(null, randomFraction = 0.5))
        assertEquals(5_000L, UpdatePolicy.serverErrorRetryDelayMs(null, randomFraction = 1.0))
        assertEquals(5_000L, UpdatePolicy.serverErrorRetryDelayMs(null, randomFraction = 7.0), "Out-of-range fractions are clamped")
        repeat(1000) {
            val delay = UpdatePolicy.serverErrorRetryDelayMs(null)
            assertTrue(delay != null && delay in 2_000L..5_000L, "$delay")
        }
    }

    @Test
    fun `retry delay follows Retry-After up to ten seconds`() {
        assertEquals(0L, UpdatePolicy.serverErrorRetryDelayMs(0))
        assertEquals(3_000L, UpdatePolicy.serverErrorRetryDelayMs(3_000))
        assertEquals(10_000L, UpdatePolicy.serverErrorRetryDelayMs(10_000))
        assertNull(UpdatePolicy.serverErrorRetryDelayMs(11_000), "A longer Retry-After skips the retry")
    }

    // --- Pauses ---

    @Test
    fun `server error pause doubles from five minutes up to an hour`() {
        val pauses = (1..7).map { UpdatePolicy.serverErrorCooldownMs(it, null) }
        assertEquals(listOf(5, 10, 20, 40, 60, 60, 60).map { it * minute }, pauses)
        assertEquals(hour, UpdatePolicy.serverErrorCooldownMs(Int.MAX_VALUE, null))
    }

    @Test
    fun `server error pause is never shorter than Retry-After`() {
        assertEquals(5 * minute, UpdatePolicy.serverErrorCooldownMs(1, 3_000))
        assertEquals(2 * hour, UpdatePolicy.serverErrorCooldownMs(1, 2 * hour))
        assertEquals(24 * hour, UpdatePolicy.serverErrorCooldownMs(1, 1_000 * hour))
    }

    @Test
    fun `usage limit pauses for an hour or a longer Retry-After`() {
        assertEquals(hour, UpdatePolicy.usageLimitCooldownMs(null))
        assertEquals(hour, UpdatePolicy.usageLimitCooldownMs(5 * minute))
        assertEquals(3 * hour, UpdatePolicy.usageLimitCooldownMs(3 * hour))
        assertEquals(24 * hour, UpdatePolicy.usageLimitCooldownMs(1_000 * hour))
    }

    // --- Schedule decisions ---

    @Test
    fun `minimum interval counts from the last successful update`() {
        val fresh = UpdateSchedule(scope)
        assertEquals(UpdateSchedule.Decision.Check, fresh.decide(start, 15 * minute))

        val schedule = fresh.recordSuccess(start)

        assertEquals(UpdateSchedule.Decision.Skip(start + 15 * minute), schedule.decide(start + 14 * minute, 15 * minute))
        assertEquals(UpdateSchedule.Decision.Check, schedule.decide(start + 15 * minute, 15 * minute))
        assertEquals(UpdateSchedule.Decision.Check, schedule.decide(start + 1, 0))
        assertEquals(UpdateSchedule.Decision.Check, schedule.decide(start + 1, -5))
        assertEquals(UpdateSchedule.Decision.Skip(Long.MAX_VALUE), schedule.decide(start + 1, Long.MAX_VALUE), "No overflow")
    }

    @Test
    fun `clock turned back does not hold updates`() {
        val updated = UpdateSchedule(scope).recordSuccess(start)
        assertEquals(UpdateSchedule.Decision.Check, updated.decide(start - hour, 15 * minute))

        val paused = UpdateSchedule(scope).recordUsageLimit(emptyList(), null, start)
        assertEquals(UpdateSchedule.Decision.Paused(paused.cooldown!!), paused.decide(start - 2 * hour, 0), "Within the longest pause the SDK sets")
        assertEquals(UpdateSchedule.Decision.Check, paused.decide(start - 24 * hour, 0), "Further out than any pause the SDK sets")
    }

    @Test
    fun `server errors pause with backoff until the CDN answers again`() {
        var schedule = UpdateSchedule(scope).recordServerError(503, emptyList(), 2_000, start)
        val first = UpdateSchedule.Cooldown(start + 5 * minute, 503, emptyList())
        assertEquals(first, schedule.cooldown)
        assertEquals(UpdateSchedule.Decision.Paused(first), schedule.decide(start + 5 * minute - 1, 0))
        assertEquals(UpdateSchedule.Decision.Check, schedule.decide(start + 5 * minute, 0))

        schedule = schedule.recordServerError(502, listOf("UPSTREAM_DOWN"), null, start + 5 * minute)
        assertEquals(UpdateSchedule.Cooldown(start + 15 * minute, 502, listOf("UPSTREAM_DOWN")), schedule.cooldown, "The pause keeps the codes it reports")

        // Any answer but a 5xx ends the series
        schedule = schedule.recordAnswer().recordServerError(503, emptyList(), null, start + hour)
        assertEquals(start + hour + 5 * minute, schedule.cooldown?.untilMs)

        schedule = schedule.recordSuccess(start + 2 * hour)
        assertNull(schedule.cooldown)
        assertEquals(0, schedule.consecutiveServerErrors)
        assertEquals(start + 2 * hour, schedule.lastSuccessfulUpdateMs)
    }

    @Test
    fun `usage limit pauses checks and ends a server error series`() {
        val schedule = UpdateSchedule(scope)
            .recordServerError(503, emptyList(), null, start)
            .recordUsageLimit(listOf("USAGE_LIMIT_EXCEEDED"), 3 * hour, start)

        assertEquals(UpdateSchedule.Cooldown(start + 3 * hour, 429, listOf("USAGE_LIMIT_EXCEEDED")), schedule.cooldown)
        assertEquals(0, schedule.consecutiveServerErrors)
    }

    @Test
    fun `pause is reported with the failure that caused it`() {
        val usageLimit = UpdateSchedule.Cooldown(start, 429, listOf("USAGE_LIMIT_EXCEEDED")).toError()
        assertEquals(429, usageLimit.statusCode)
        assertEquals(listOf("USAGE_LIMIT_EXCEEDED"), usageLimit.errorCodes)
        assertTrue(usageLimit.message!!.startsWith("LingoHub usage limit exceeded, translation updates are paused until"), usageLimit.message)

        val serverError = UpdateSchedule.Cooldown(start, 503, emptyList()).toError()
        assertEquals(503, serverError.statusCode)
        assertEquals(emptyList<String>(), serverError.errorCodes)
        assertTrue(serverError.message!!.startsWith("LingoHub CDN unavailable, translation updates are paused until"), serverError.message)
    }

    // --- Persistence ---

    @Test
    fun `schedule survives a relaunch for the same scope only`() {
        val storage = InMemorySharedPreferences()
        val schedule = UpdateSchedule(scope)
            .recordSuccess(start)
            .recordServerError(503, emptyList(), null, start)
            .recordUsageLimit(listOf("USAGE_LIMIT_EXCEEDED", "OTHER"), null, start)

        Preferences(contextWith(storage)).saveUpdateSchedule(schedule)
        val relaunched = Preferences(contextWith(storage))

        assertEquals(schedule, relaunched.getUpdateSchedule(scope))
        // Another app version, environment or CDN key checks right away
        for (other in listOf(
            UpdateSchedule.Scope.of("1.0.1", Environment.PRODUCTION, "lh-cdn_key"),
            UpdateSchedule.Scope.of("1.0.0", Environment.STAGING, "lh-cdn_key"),
            UpdateSchedule.Scope.of("1.0.0", Environment.PRODUCTION, "lh-cdn_other"),
        )) {
            assertEquals(UpdateSchedule(other), relaunched.getUpdateSchedule(other), "$other")
        }
    }

    @Test
    fun `scope stores only a digest of the CDN key`() {
        val storage = InMemorySharedPreferences()

        Preferences(contextWith(storage)).saveUpdateSchedule(UpdateSchedule(scope))

        assertFalse(storage.all.values.any { "$it".contains("lh-cdn_key") })
        assertEquals(64, scope.apiKeyDigest.length)
    }

    @Test
    fun `schedule without a pause or a successful update round-trips`() {
        val storage = InMemorySharedPreferences()
        val preferences = Preferences(contextWith(storage))
        preferences.saveUpdateSchedule(UpdateSchedule(scope).recordServerError(500, emptyList(), null, start))
        val schedule = UpdateSchedule(scope).recordAnswer()

        preferences.saveUpdateSchedule(schedule)

        assertEquals(schedule, Preferences(contextWith(storage)).getUpdateSchedule(scope))
        assertEquals(UpdateSchedule(scope), Preferences(contextWith(InMemorySharedPreferences())).getUpdateSchedule(scope))
    }

    // --- Retry-After ---

    @Test
    fun `Retry-After delay-seconds`() {
        assertEquals(120_000L, UpdatePolicy.retryAfterMs("120", start))
        assertEquals(3_000L, UpdatePolicy.retryAfterMs(" 3 ", start))
        assertEquals(0L, UpdatePolicy.retryAfterMs("0", start))
        assertEquals(24 * hour, UpdatePolicy.retryAfterMs("99999999999999999999", start), "Capped at the longest pause")
    }

    @Test
    fun `Retry-After HTTP-date`() {
        val now = 1_792_481_280_000L // Tue, 20 Oct 2026 07:28:00 GMT
        assertEquals(2 * hour, UpdatePolicy.retryAfterMs("Tue, 20 Oct 2026 09:28:00 GMT", now))
        assertEquals(0L, UpdatePolicy.retryAfterMs("Mon, 19 Oct 2026 07:28:00 GMT", now), "A date in the past means now")
    }

    @Test
    fun `malformed Retry-After is ignored`() {
        for (value in listOf(null, "", "   ", "-1", "1.5", "+3", "soon", "Wed, 21 Oct 2026")) {
            assertNull(UpdatePolicy.retryAfterMs(value, start), "$value")
        }
    }

    private fun contextWith(storage: InMemorySharedPreferences): Context = mock<Context>().also {
        whenever(it.getSharedPreferences(any(), any())).thenReturn(storage)
    }
}
