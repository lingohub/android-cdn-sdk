package com.helpers.data

import androidx.annotation.Keep
import com.helpers.core.Lingohub
import com.lingohub.BuildConfig
import kotlinx.serialization.Serializable
import kotlinx.serialization.InternalSerializationApi

@Keep
@OptIn(InternalSerializationApi::class)
@Serializable
data class PackageRequest(
    val distributionEnvironment: String = Lingohub.environment.name,
    val distributionType: String = "MOBILE_SDK_ANDROID",
    val clientVersion: String = Lingohub.appVersionCode,
    val clientLanguageCode: String = Lingohub.appLanguage,
    val clientUser: String = Lingohub.deviceId,
    val clientAgent: String = "Lingohub-Android-SDK/" + BuildConfig.SDK_VERSION_NAME,
    val clientRelease: String? = Lingohub.preferences.getBundleMetadata()?.bundleIdentifier
)
