package com.lingohub.android.cdn.core

import android.content.Context
import android.content.ContextWrapper
import android.content.res.Configuration
import android.content.res.Resources
import com.lingohub.android.cdn.utils.LingoHubLogger
import com.lingohub.android.cdn.utils.ResourcesUtil

/**
 * Serves LingoHub translations through [getResources]. Used from any thread:
 * Services and workers resolve strings off the main thread.
 */
internal class LingoHubContextWrapper(base: Context) : ContextWrapper(base) {

    @Volatile
    private var translated: TranslatedResources? = null

    override fun getResources(): Resources {
        val base = super.getResources()
        val current = translated
        if (current != null && current.isCopyOf(base)) return current.resources
        // Racing callers may each build a copy; any of them is valid.
        return TranslatedResources(this, base).also { translated = it }.resources
    }

    override fun createConfigurationContext(overrideConfiguration: Configuration): Context =
        LingoHubContextWrapper(super.createConfigurationContext(overrideConfiguration))
}

/**
 * A [ResourcesUtil] copy of [base] and what it was copied from. The framework
 * updates [base] in place on configuration and asset changes but not the copy,
 * so the wrapper rebuilds it once [isCopyOf] fails. Long-lived contexts depend
 * on this: after a language change Android derives the process default locale
 * from the application's resources.
 */
private class TranslatedResources(context: Context, private val base: Resources) {
    private val assets = base.assets
    private val configuration = Configuration(base.configuration)
    val resources = ResourcesUtil(context, assets, base.displayMetrics, configuration)

    init {
        LingoHubLogger.logger.onDebug("LingoHubContextWrapper, copied resources for ${configuration.locales}")
    }

    fun isCopyOf(current: Resources): Boolean =
        current === base && current.assets === assets && current.configuration == configuration
}
