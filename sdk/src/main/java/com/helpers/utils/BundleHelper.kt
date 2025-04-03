package com.helpers.utils

import com.helpers.core.Lingohub
import com.helpers.data.model.Bundle
import com.helpers.data.ICoroutineScope
import com.helpers.data.LingohubScope
import kotlinx.coroutines.launch
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