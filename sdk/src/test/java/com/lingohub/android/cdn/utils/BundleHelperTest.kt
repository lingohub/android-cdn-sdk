package com.lingohub.android.cdn.utils

import com.lingohub.android.cdn.data.model.Bundle
import com.lingohub.android.cdn.data.model.Item
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Test
import java.util.Collections
import java.util.Locale
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.concurrent.thread

class BundleHelperTest {
    // What the bundle directory on disk holds.
    @Volatile
    private var bundlesOnDisk: List<Bundle>? = null
    private val bundleHelper = BundleHelper { bundlesOnDisk }

    @Test
    fun `no release is served before the first refresh`() {
        assertNull(bundleHelper.repositoryForLocale(Locale.ENGLISH))
    }

    @Test
    fun `serves the refreshed release and reuses its repository`() = runBlocking {
        bundlesOnDisk = release("en", "v1")
        bundleHelper.refresh()

        val repository = bundleHelper.repositoryForLocale(Locale.US)
        assertEquals("Hello v1", repository?.getText(GREETING))
        assertSame(repository, bundleHelper.repositoryForLocale(Locale.ENGLISH))
    }

    @Test
    fun `a refresh replaces repositories built from the previous release`() = runBlocking {
        bundlesOnDisk = release("en", "v1")
        bundleHelper.refresh()
        bundleHelper.repositoryForLocale(Locale.ENGLISH)

        bundlesOnDisk = release("en", "v2")
        bundleHelper.refresh()

        assertEquals("Hello v2", bundleHelper.repositoryForLocale(Locale.ENGLISH)?.getText(GREETING))
    }

    @Test
    fun `a language without a bundle has no repository`() = runBlocking {
        bundlesOnDisk = release("de", "v1")
        bundleHelper.refresh()

        assertNull(bundleHelper.repositoryForLocale(Locale.ENGLISH))
    }

    @Test
    fun `lookups racing refreshes never outlive the release they were built from`() = runBlocking {
        val failures = Collections.synchronizedList(mutableListOf<Throwable>())
        val running = AtomicBoolean(true)
        val readers = List(4) {
            thread {
                try {
                    while (running.get()) bundleHelper.repositoryForLocale(Locale.ENGLISH)?.getText(GREETING)
                } catch (e: Throwable) {
                    failures += e
                }
            }
        }

        try {
            repeat(200) { version ->
                bundlesOnDisk = release("en", "v$version")
                bundleHelper.refresh()
                // Readers keep building repositories concurrently; none of them
                // may leak an older release into lookups after the refresh.
                assertEquals("Hello v$version", bundleHelper.repositoryForLocale(Locale.ENGLISH)?.getText(GREETING))
            }
        } finally {
            running.set(false)
            readers.forEach { it.join() }
        }
        assertEquals(emptyList<Throwable>(), failures)
    }

    private fun release(language: String, version: String) =
        listOf(Bundle(language, listOf(Item(key = GREETING, type = "TEXT", value = "Hello $version"))))

    private companion object {
        const val GREETING = "greeting"
    }
}
