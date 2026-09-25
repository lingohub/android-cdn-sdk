package com.lingohub.android.cdn.data

import com.lingohub.android.cdn.core.LingoHubSDKError
import java.util.Date

/**
 * The persisted pacing state of update checks: when the last update succeeded, and whether a failure
 * paused checks (see [UpdatePolicy]). Only valid for the app version it was recorded for: a new app
 * version checks right away.
 *
 * @property lastSuccessfulUpdateMs when the last update succeeded; the minimum interval counts from here.
 * @property consecutiveServerErrors updates in a row that failed with a 5xx; the pause doubles with each.
 */
internal data class UpdateSchedule(
    val appVersion: String,
    val lastSuccessfulUpdateMs: Long? = null,
    val consecutiveServerErrors: Int = 0,
    val cooldown: Cooldown? = null,
) {
    /** Update checks are paused until [untilMs] after the failure described by [statusCode] and [errorCodes]. */
    data class Cooldown(val untilMs: Long, val statusCode: Int, val errorCodes: List<String>) {
        /** What `update()` reports while this cooldown lasts. */
        fun toError(): LingoHubSDKError {
            val details = errorCodes.takeIf { it.isNotEmpty() }?.joinToString()?.let { ", $it" }.orEmpty()
            val message = if (statusCode == 429) {
                "LingoHub usage limit exceeded, translation updates are paused until ${Date(untilMs)} (HTTP 429$details)"
            } else {
                "LingoHub CDN unavailable, translation updates are paused until ${Date(untilMs)} (HTTP $statusCode$details)"
            }
            return LingoHubSDKError(message, statusCode = statusCode, errorCodes = errorCodes)
        }
    }

    /** What an `update()` call does at a given time. */
    sealed interface Decision {
        /** Contact the CDN. */
        data object Check : Decision

        /** Skip: the last successful update is less than the minimum interval ago. */
        data class Skip(val untilMs: Long) : Decision

        /** Fail without contacting the CDN: a failure paused checks. */
        data class Paused(val cooldown: Cooldown) : Decision
    }

    fun decide(nowMs: Long, minimumIntervalMs: Long): Decision {
        // A cooldown ending further out than any the SDK sets means the clock was turned back; it must
        // not hold updates for longer than intended.
        if (cooldown != null && nowMs < cooldown.untilMs && cooldown.untilMs - nowMs <= UpdatePolicy.MAXIMUM_COOLDOWN_MS) {
            return Decision.Paused(cooldown)
        }
        // Likewise, a last update "in the future" does not delay the next one.
        if (lastSuccessfulUpdateMs != null && nowMs >= lastSuccessfulUpdateMs) {
            val elapsedMs = nowMs - lastSuccessfulUpdateMs
            if (elapsedMs < minimumIntervalMs) {
                // Saturates instead of overflowing for an interval as long as Long.MAX_VALUE
                return Decision.Skip(nowMs + (minimumIntervalMs - elapsedMs).coerceAtMost(Long.MAX_VALUE - nowMs))
            }
        }
        return Decision.Check
    }

    /** The update succeeded: the CDN answered and any release it offered is installed. */
    fun recordSuccess(nowMs: Long) = copy(lastSuccessfulUpdateMs = nowMs, consecutiveServerErrors = 0, cooldown = null)

    /** The CDN answered with anything but a 5xx, which ends a series of server errors. */
    fun recordAnswer() = copy(consecutiveServerErrors = 0)

    /** An update failed with a 5xx: pause checks, backing off with every failure in a row. */
    fun recordServerError(statusCode: Int, retryAfterMs: Long?, nowMs: Long): UpdateSchedule {
        val failures = consecutiveServerErrors + 1
        val durationMs = UpdatePolicy.serverErrorCooldownMs(failures, retryAfterMs)
        return copy(consecutiveServerErrors = failures, cooldown = Cooldown(nowMs + durationMs, statusCode, emptyList()))
    }

    /** The CDN answered 429: pause checks for an hour, or for `Retry-After` when longer. */
    fun recordUsageLimit(errorCodes: List<String>, retryAfterMs: Long?, nowMs: Long): UpdateSchedule {
        val durationMs = UpdatePolicy.usageLimitCooldownMs(retryAfterMs)
        return copy(consecutiveServerErrors = 0, cooldown = Cooldown(nowMs + durationMs, 429, errorCodes))
    }
}
