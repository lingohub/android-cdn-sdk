package com.helpers.data.model

import androidx.annotation.Keep
import kotlinx.serialization.InternalSerializationApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerialName

@Keep
@Serializable
@OptIn(InternalSerializationApi::class)
internal data class BundleInfo(
    @SerialName("distributionReleaseId")
    val id: String,
    val name: String,
    val filesUrl: String,
    val createdAt: String
    )

@Keep
public enum class Environment {
    PRODUCTION, DEVELOPMENT, STAGING, TEST
}