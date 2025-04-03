package com.helpers.data.model

import androidx.annotation.Keep

@Keep
internal data class BundleMetadata(
    val bundleIdentifier: String,
    val appVersion: String
)
