package com.lingohub.android.cdn.core

import androidx.annotation.Keep

/**
 * Error delivered to [LingoHubUpdateListener.onFailure].
 *
 * @property statusCode the HTTP status of the failed CDN request, or null for
 *   local/network errors.
 * @property errorCodes machine-readable error codes from the CDN's problem
 *   response, e.g. "CDN_KEY_EXPIRED" or "USAGE_LIMIT_EXCEEDED". Empty when the
 *   server sent none.
 */
@Keep
class LingoHubSDKError @JvmOverloads constructor(
    message: String,
    val statusCode: Int? = null,
    val errorCodes: List<String> = emptyList()
) : IllegalStateException(message)