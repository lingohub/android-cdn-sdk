package com.lingohub.android.cdn.utils

import com.lingohub.android.cdn.data.Repository
import com.lingohub.android.cdn.data.model.Bundle
import com.lingohub.android.cdn.data.model.Item
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import java.util.Collections
import java.util.Locale
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import kotlin.concurrent.thread

class BundleHelperTest {
    // What the bundle directory on disk holds.
    @Volatile
    private var bundlesOnDisk: List<Bundle>? = null
    private val bundleHelper = BundleHelper(readBundle = { bundlesOnDisk })

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
    @Timeout(10, unit = TimeUnit.SECONDS)
    fun `a repository still being built from the previous release is not served after a refresh`() = runBlocking {
        val building = CountDownLatch(1)
        val finishBuilding = CountDownLatch(1)
        val helper = BundleHelper(readBundle = { bundlesOnDisk }) { bundle ->
            if (bundle.items.single().value == "Hello v1") {
                building.countDown()
                finishBuilding.await(5, TimeUnit.SECONDS)
            }
            Repository(bundle)
        }
        bundlesOnDisk = release("en", "v1")
        helper.refresh()

        // A lookup builds its repository from v1 and completes only after v2 is live.
        val slowLookup = thread { helper.repositoryForLocale(Locale.ENGLISH) }
        building.await()
        bundlesOnDisk = release("en", "v2")
        helper.refresh()
        finishBuilding.countDown()
        slowLookup.join()

        assertEquals("Hello v2", helper.repositoryForLocale(Locale.ENGLISH)?.getText(GREETING))
    }

    @Test
    fun `lookups racing refreshes never outlive the release they were built from`() = runBlocking {
        val failures = Collections.synchronizedList(mutableListOf<Throwable>())
        val readersStarted = CountDownLatch(READERS)
        val reads = AtomicInteger()
        val running = AtomicBoolean(true)
        val readers = List(READERS) {
            thread {
                readersStarted.countDown()
                try {
                    while (running.get()) {
                        bundleHelper.repositoryForLocale(Locale.ENGLISH)?.getText(GREETING)
                        reads.incrementAndGet()
                    }
                } catch (e: Throwable) {
                    failures += e
                }
            }
        }
        readersStarted.await()
        val readsBeforeRefreshes = reads.get()

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
        assertTrue(reads.get() > readsBeforeRefreshes, "the readers looked up nothing while releases changed")
    }

    private fun release(language: String, version: String) =
        listOf(Bundle(language, listOf(Item(key = GREETING, type = "TEXT", value = "Hello $version"))))

    private companion object {
        const val GREETING = "greeting"
        const val READERS = 4
    }
}
