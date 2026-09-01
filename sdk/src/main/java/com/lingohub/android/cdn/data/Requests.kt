package com.lingohub.android.cdn.data

import androidx.annotation.Keep
import com.lingohub.android.cdn.core.Lingohub
import com.lingohub.android.cdn.core.LocaleProvider
import com.lingohub.android.cdn.BuildConfig
import kotlinx.serialization.Serializable
import kotlinx.serialization.InternalSerializationApi

@Keep
@OptIn(InternalSerializationApi::class)
@Serializable
data class PackageRequest(
    val distributionEnvironment: String = Lingohub.environment.name,
    val distributionType: String = "MOBILE_SDK_ANDROID",
    val clientVersion: String = Lingohub.appVersionCode,
    // Read at request time so a later Lingohub.setLocale() is reflected.
    val clientLanguageCode: String = LocaleProvider.currentLocale.language,
    val clientUser: String = Lingohub.deviceId,
    val clientAgent: String = "Lingohub-Android-SDK/" + BuildConfig.SDK_VERSION_NAME,
    val clientRelease: String? = Lingohub.preferences.getBundleMetadata()?.bundleIdentifier
)
