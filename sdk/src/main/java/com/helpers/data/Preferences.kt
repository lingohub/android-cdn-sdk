package com.helpers.data

import android.content.Context
import android.content.SharedPreferences
import com.helpers.data.model.BundleMetadata
import androidx.core.content.edit

internal interface IPreferences {
    fun getBundleMetadata(): BundleMetadata?
    fun saveBundleMetadata(metadata: BundleMetadata)
    fun clearBundleMetadata()
}

internal class Preferences(context: Context) : IPreferences {
    companion object {
        const val BUNDLE_ID = "bundle_identifier"
        const val APP_VERSION = "app_version"
    }

    private val prefs: SharedPreferences = context.getSharedPreferences("Lingohub", Context.MODE_PRIVATE)

    override fun getBundleMetadata(): BundleMetadata? {
        val bundleId = prefs.getString(BUNDLE_ID, null) ?: return null
        val appVersion = prefs.getString(APP_VERSION, null) ?: return null
        return BundleMetadata(bundleId, appVersion)
    }

    override fun saveBundleMetadata(metadata: BundleMetadata) = prefs.edit() {
        putString(APP_VERSION, metadata.appVersion)
            .putString(BUNDLE_ID, metadata.bundleIdentifier)
    }

    override fun clearBundleMetadata() = prefs.edit() { clear() }
}