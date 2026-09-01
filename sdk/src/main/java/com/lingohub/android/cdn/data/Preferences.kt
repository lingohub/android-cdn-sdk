package com.lingohub.android.cdn.data

import android.content.Context
import android.content.SharedPreferences
import com.lingohub.android.cdn.data.model.BundleMetadata
import androidx.core.content.edit

internal interface IPreferences {
    fun getBundleMetadata(): BundleMetadata?
    fun saveBundleMetadata(metadata: BundleMetadata)
    fun clearBundleMetadata()
    fun getClientId(): String?
    fun saveClientId(clientId: String)
}

internal class Preferences(context: Context) : IPreferences {
    companion object {
        const val BUNDLE_ID = "bundle_identifier"
        const val APP_VERSION = "app_version"
        const val CLIENT_ID = "client_id"
    }

    // Storage identifier, not brand surface: this name must stay "Lingohub" so apps
    // upgrading from older SDK versions keep their bundle metadata. Renaming it would
    // orphan the metadata while the downloaded bundle in files/lingohub survives,
    // which lets checkIfUpdated() skip the app-version-change cleanup and serve
    // stale translations until the next successful update.
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

    // Remove only the bundle keys: clear() would also wipe the per-install
    // client id, which must survive an app-version purge.
    override fun clearBundleMetadata() = prefs.edit() {
        remove(APP_VERSION)
            .remove(BUNDLE_ID)
    }

    override fun getClientId(): String? = prefs.getString(CLIENT_ID, null)

    override fun saveClientId(clientId: String) = prefs.edit() {
        putString(CLIENT_ID, clientId)
    }
}