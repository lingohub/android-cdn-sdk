package com.lingohub.android.cdn.data

import androidx.annotation.Keep
import com.lingohub.android.cdn.core.LingoHub
import com.lingohub.android.cdn.core.LocaleProvider
import com.lingohub.android.cdn.BuildConfig
import kotlinx.serialization.Serializable

@Keep
@Serializable
internal data class PackageRequest(
    val distributionEnvironment: String = LingoHub.environment.name,
    val distributionType: String = "MOBILE_SDK_ANDROID",
    val clientVersion: String = LingoHub.appVersionName,
    // Read at request time so a later LingoHub.setLocale() is reflected.
    val clientLanguageCode: String = LocaleProvider.currentLocale.language,
    val clientUser: String = LingoHub.deviceId,
    val clientAgent: String = "LingoHub-Android-SDK/" + BuildConfig.SDK_VERSION_NAME,
    val clientRelease: String? = LingoHub.preferences.getBundleMetadata()?.bundleIdentifier
)
