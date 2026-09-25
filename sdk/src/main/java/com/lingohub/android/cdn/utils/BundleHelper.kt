package com.lingohub.android.cdn.utils

import com.lingohub.android.cdn.core.LingoHub
import com.lingohub.android.cdn.data.IRepository
import com.lingohub.android.cdn.data.Repository
import com.lingohub.android.cdn.data.model.Bundle
import java.util.*
import java.util.concurrent.ConcurrentHashMap

internal class BundleHelper(
    private val readBundle: suspend () -> List<Bundle>? = { LingoHub.fileHelper.readBundle() }
) {
    // The bundles and the repositories built from them are replaced as one
    // unit: a lookup racing a refresh resolves against a single release and
    // can never cache a repository of the old release after the new one is live.
    @Volatile
    private var release: Release? = null

    /**
     * Re-reads the bundle from disk. Suspends until the new state is visible so
     * callers can order "bundle refreshed" strictly before listener
     * notification.
     */
    suspend fun refresh() {
        release = readBundle()?.let(::Release)
    }

    /** Null until a release is loaded or when it has no bundle for the language. */
    fun repositoryForLocale(locale: Locale): IRepository? = release?.repositoryFor(locale.language)

    private class Release(private val bundles: List<Bundle>) {
        private val repositories = ConcurrentHashMap<String, IRepository>()

        fun repositoryFor(language: String): IRepository? =
            repositories[language] ?: bundles.find { it.iso == language }?.let { bundle ->
                repositories.getOrPut(language) { Repository(bundle) }
            }
    }
}
