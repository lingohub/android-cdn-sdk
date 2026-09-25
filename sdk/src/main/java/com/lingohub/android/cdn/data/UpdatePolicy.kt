package com.lingohub.android.cdn.data

import java.text.ParseException
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone
import kotlin.random.Random

/**
 * How often the SDK checks for updates and how it reacts to a failed check: the "Failures and
 * retries" policy in the README, shared with the iOS SDK (lingohub/organization#2351).
 *
 * The CDN answers from a single-primary database, so a crowd of clients retrying together can keep
 * it down. The policy is conservative for that reason: at most one retry per failure, then a pause
 * that survives app restarts (see [UpdateSchedule]).
 */
internal object UpdatePolicy {
    /** The minimum time between successful update checks, unless the app is debuggable. */
    const val RELEASE_MINIMUM_CHECK_INTERVAL_MS = 15 * 60 * 1000L

    /** Delay before the single retry after a 5xx without `Retry-After`, drawn per retry. */
    const val SERVER_ERROR_RETRY_DELAY_MIN_MS = 2_000L
    const val SERVER_ERROR_RETRY_DELAY_MAX_MS = 5_000L

    /** The longest `Retry-After` the SDK waits for before its single retry. A longer one skips the retry. */
    const val MAXIMUM_RETRY_WAIT_MS = 10_000L

    /** The pause after an update failed with a 5xx; it doubles with every further failure in a row. */
    const val INITIAL_SERVER_ERROR_COOLDOWN_MS = 5 * 60 * 1000L
    const val MAXIMUM_SERVER_ERROR_COOLDOWN_MS = 60 * 60 * 1000L

    /** The pause after a 429 (the CDN usage budget is exhausted). */
    const val USAGE_LIMIT_COOLDOWN_MS = 60 * 60 * 1000L

    /** No pause lasts longer, whatever `Retry-After` asks for, so a bogus header can never stop updates for good. */
    const val MAXIMUM_COOLDOWN_MS = 24 * 60 * 60 * 1000L

    /**
     * The wait before the single retry after a 5xx: the `Retry-After` delay, or 2–5 seconds without one,
     * placed within that range by [randomFraction] (0..1). Null when `Retry-After` asks for more than
     * [MAXIMUM_RETRY_WAIT_MS], which skips the retry.
     */
    fun serverErrorRetryDelayMs(retryAfterMs: Long?, randomFraction: Double = Random.nextDouble()): Long? {
        if (retryAfterMs == null) {
            val span = SERVER_ERROR_RETRY_DELAY_MAX_MS - SERVER_ERROR_RETRY_DELAY_MIN_MS
            return SERVER_ERROR_RETRY_DELAY_MIN_MS + (randomFraction.coerceIn(0.0, 1.0) * span).toLong()
        }
        return retryAfterMs.takeIf { it <= MAXIMUM_RETRY_WAIT_MS }
    }

    /**
     * The pause after an update failed with a 5xx: 5 minutes after the first failure, doubling with each
     * further one in a row up to an hour, and never shorter than `Retry-After`.
     *
     * @param consecutiveFailures failed updates in a row, this one included.
     */
    fun serverErrorCooldownMs(consecutiveFailures: Int, retryAfterMs: Long?): Long {
        // 5 · 2^12 minutes is far beyond the cap; limiting the exponent keeps the arithmetic in range.
        val doublings = (consecutiveFailures - 1).coerceIn(0, 12)
        val backoff = (INITIAL_SERVER_ERROR_COOLDOWN_MS shl doublings).coerceAtMost(MAXIMUM_SERVER_ERROR_COOLDOWN_MS)
        return maxOf(backoff, retryAfterMs ?: 0).coerceAtMost(MAXIMUM_COOLDOWN_MS)
    }

    /** The pause after a 429: an hour, or `Retry-After` when that is longer. */
    fun usageLimitCooldownMs(retryAfterMs: Long?): Long =
        maxOf(USAGE_LIMIT_COOLDOWN_MS, retryAfterMs ?: 0).coerceAtMost(MAXIMUM_COOLDOWN_MS)

    /**
     * The delay in milliseconds a `Retry-After` header value asks for (RFC 9110, section 10.2.3):
     * delay-seconds (`"120"`), or the time until an HTTP-date (`"Wed, 21 Oct 2026 07:28:00 GMT"`, 0 once it
     * has passed). Capped at [MAXIMUM_COOLDOWN_MS], which no pause exceeds anyway. Null when the value is
     * missing or malformed.
     */
    fun retryAfterMs(value: String?, nowMs: Long): Long? {
        val trimmed = value?.trim()
        if (trimmed.isNullOrEmpty()) return null
        val delayMs = if (trimmed.all { it in '0'..'9' }) {
            // Digits beyond a Long are far beyond the cap as well
            val seconds = trimmed.toLongOrNull() ?: Long.MAX_VALUE
            if (seconds > MAXIMUM_COOLDOWN_MS / 1000) MAXIMUM_COOLDOWN_MS else seconds * 1000
        } else {
            val date = httpDate(trimmed) ?: return null
            date - nowMs
        }
        return delayMs.coerceIn(0, MAXIMUM_COOLDOWN_MS)
    }

    /**
     * Only the IMF-fixdate form: RFC 9110 asks recipients to accept the two obsolete forms too, but no
     * server this SDK talks to sends them.
     */
    private fun httpDate(value: String): Long? {
        // Created per call: parsing runs only for a failed response that carries a date.
        val format = SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss 'GMT'", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("GMT")
            isLenient = false
        }
        return try {
            format.parse(value)?.time
        } catch (e: ParseException) {
            null
        }
    }
}
