package com.lingohub.android.cdn.utils

import com.lingohub.android.cdn.core.Lingohub
import com.lingohub.android.cdn.data.model.Bundle
import com.lingohub.android.cdn.data.ICoroutineScope
import com.lingohub.android.cdn.data.LingohubScope
import java.util.*

internal class BundleHelper(private val scope: ICoroutineScope = LingohubScope()) {
    private var bundles: List<Bundle>? = null

    fun refresh() {
        scope.launch {
            bundles = Lingohub.fileHelper.readBundle()
        }
    }

    fun bundleForLocale(locale: Locale): Bundle? {
        return bundles?.find { it.iso == locale.language }
    }
}