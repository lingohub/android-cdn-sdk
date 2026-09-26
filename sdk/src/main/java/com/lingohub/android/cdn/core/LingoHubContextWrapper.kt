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

    private val lock = Any()

    @Volatile
    private var translated: TranslatedResources? = null

    override fun getResources(): Resources {
        val base = super.getResources()
        translated?.let { if (it.isCurrentFor(base)) return it.resources }
        synchronized(lock) {
            val current = translated
            if (current != null && current.follows(base)) {
                if (!current.isCurrentFor(base)) current.update()
                return current.resources
            }
            return TranslatedResources(this, base).also { translated = it }.resources
        }
    }

    override fun createConfigurationContext(overrideConfiguration: Configuration): Context =
        LingoHubContextWrapper(super.createConfigurationContext(overrideConfiguration))
}

/**
 * A [ResourcesUtil] copy of [base]. Android updates [base] in place on a
 * configuration change but not the copy, so [update] applies the change to the
 * copy, in place: resources handed out earlier, such as application.resources
 * kept by a long-lived helper, stay current too. Android asks the application
 * for its resources during every configuration change, which runs [update] for
 * it right away, and derives the process default locale from them. New assets
 * (overlays, split APKs) need a new copy.
 */
private class TranslatedResources(context: Context, private val base: Resources) {
    private val assets = base.assets

    @Volatile
    private var configuration = Configuration(base.configuration)

    val resources = ResourcesUtil(context, base, assets, base.displayMetrics, configuration)

    init {
        LingoHubLogger.debug { "LingoHubContextWrapper, copied resources for ${configuration.locales}" }
    }

    /** Whether this copies [current] with its current assets, so [update] can bring it up to date. */
    fun follows(current: Resources): Boolean = current === base && current.assets === assets

    fun isCurrentFor(current: Resources): Boolean = follows(current) && current.configuration == configuration

    // Deprecated for apps, but it is how Android itself updates the base.
    @Suppress("DEPRECATION")
    fun update() {
        val latest = Configuration(base.configuration)
        resources.updateConfiguration(latest, base.displayMetrics)
        configuration = latest
        LingoHubLogger.debug { "LingoHubContextWrapper, updated resources to ${latest.locales}" }
    }
}
