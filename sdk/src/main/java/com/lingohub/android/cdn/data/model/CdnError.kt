package com.lingohub.android.cdn.data.model

import androidx.annotation.Keep
import kotlinx.serialization.Serializable

/**
 * RFC 7807 problem body returned by the CDN check endpoint on non-2xx
 * responses. Codes are kept as strings so unknown future values pass through.
 */
@Keep
@Serializable
internal data class CdnErrorResponse(
    val type: String? = null,
    val status: Int? = null,
    val detail: String? = null,
    val errors: List<CdnErrorDetail> = emptyList()
) {
    fun codes(): List<String> = errors.flatMap { it.infos }
}

@Keep
@Serializable
internal data class CdnErrorDetail(
    val field: String? = null,
    val infos: List<String> = emptyList()
)
