package com.lingohub.android.cdn.utils

import com.lingohub.android.cdn.core.LingoHub
import com.lingohub.android.cdn.data.model.Bundle
import java.util.*

internal class BundleHelper {
    @Volatile
    private var bundles: List<Bundle>? = null

    /**
     * Re-reads the bundle from disk. Suspends until the new state is visible so
     * callers can order "bundle refreshed" strictly before listener
     * notification.
     */
    suspend fun refresh() {
        bundles = LingoHub.fileHelper.readBundle()
    }

    fun bundleForLocale(locale: Locale): Bundle? {
        return bundles?.find { it.iso == locale.language }
    }
}
