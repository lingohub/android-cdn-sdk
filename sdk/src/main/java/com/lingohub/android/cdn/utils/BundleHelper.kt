package com.lingohub.android.cdn.utils

import com.lingohub.android.cdn.core.LingoHub
import com.lingohub.android.cdn.data.model.Bundle
import com.lingohub.android.cdn.data.ICoroutineScope
import com.lingohub.android.cdn.data.LingoHubScope
import java.util.*

internal class BundleHelper(private val scope: ICoroutineScope = LingoHubScope()) {
    private var bundles: List<Bundle>? = null

    fun refresh() {
        scope.launch {
            bundles = LingoHub.fileHelper.readBundle()
        }
    }

    fun bundleForLocale(locale: Locale): Bundle? {
        return bundles?.find { it.iso == locale.language }
    }
}