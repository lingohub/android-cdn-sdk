package com.lingohub.android.cdn.core

import android.app.Notification
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.os.Build
import android.os.SystemClock
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SdkSuppress
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.rule.ServiceTestRule
import com.lingohub.android.cdn.test.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.util.Collections
import java.util.Locale
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread

@RunWith(AndroidJUnit4::class)
class StringsOutsideActivitiesTest {

    @get:Rule
    val serviceRule = ServiceTestRule()

    // The test package's own context: never wrapped, so it keeps the APK strings.
    private val context: Context = InstrumentationRegistry.getInstrumentation().targetContext

    @Before
    fun installTestRelease() {
        LingoHub.setLocale(Locale.ENGLISH)
        installRelease(
            "en",
            text("lh_test_greeting", RELEASE_GREETING),
            text("lh_test_welcome", "Welcome back, %1\$s"),
            plural("lh_test_messages", "one", "%d new message"),
            plural("lh_test_messages", "other", "%d new messages")
        )
    }

    @Test
    fun stringsReadBeforeConfigureComeFromTheApk() {
        val application = ApplicationProvider.getApplicationContext<TestApplication>()

        assertEquals(APK_GREETING, application.greetingBeforeConfigure)
    }

    @Test
    fun applicationContextServesTheRelease() {
        val applicationContext = context.applicationContext

        assertEquals(RELEASE_GREETING, applicationContext.getString(R.string.lh_test_greeting))
        assertEquals("Welcome back, Alex", applicationContext.getString(R.string.lh_test_welcome, "Alex"))
        assertEquals("3 new messages", applicationContext.resources.getQuantityString(R.plurals.lh_test_messages, 3, 3))
        assertEquals(APK_GREETING, context.getString(R.string.lh_test_greeting))
    }

    @Test
    fun notificationBuiltInAServiceShowsTheRelease() {
        val binder = serviceRule.bindService(Intent(context, TranslatedService::class.java))
        val service = (binder as TranslatedService.LocalBinder).service

        val notification = service.buildNotification(messageCount = 1)

        assertEquals(RELEASE_GREETING, notification.extras.getCharSequence(Notification.EXTRA_TITLE).toString())
        assertEquals("1 new message", notification.extras.getCharSequence(Notification.EXTRA_TEXT).toString())
    }

    @Test
    fun manifestReceiverIsDeliveredAndServesTheRelease() {
        TranslatedReceiver.received.clear()

        context.sendBroadcast(Intent(context, TranslatedReceiver::class.java))

        val strings = TranslatedReceiver.received.poll(10, TimeUnit.SECONDS)
        assertNotNull("the manifest receiver was not delivered", strings)
        // Without attribution tags (or a split), Android hands a manifest receiver
        // a context that delegates to the Application.
        assertEquals(RELEASE_GREETING, strings!!.receiverContext)
        assertEquals(RELEASE_GREETING, strings.applicationContext)
        assertEquals(RELEASE_GREETING, strings.wrapped)
    }

    @Test
    @SdkSuppress(minSdkVersion = Build.VERSION_CODES.S)
    fun receiverWithAttributionTagsServesTheReleaseThroughWrap() {
        TranslatedReceiver.received.clear()

        context.sendBroadcast(Intent(context, AttributedReceiver::class.java))

        val strings = TranslatedReceiver.received.poll(10, TimeUnit.SECONDS)
        assertNotNull("the attributed receiver was not delivered", strings)
        // Its context is an attribution context of its own, not backed by the Application.
        assertEquals("lh_test", strings!!.attributionTag)
        assertEquals(RELEASE_GREETING, strings.wrapped)
        assertEquals(RELEASE_GREETING, strings.applicationContext)
    }

    @Test
    fun textWithADefaultServesTheRelease() {
        val resources = context.applicationContext.resources

        assertEquals(RELEASE_GREETING, resources.getText(R.string.lh_test_greeting, "default"))
        // Like Android's: the default instead of an exception for no or a missing resource.
        assertEquals("default", resources.getText(0, "default"))
        assertEquals("default", resources.getText(MISSING_RESOURCE_ID, "default"))
    }

    @Test
    fun configurationContextOfAWrappedContextServesTheRelease() {
        val wrapped = LingoHub.wrap(context)

        val derived = wrapped.createConfigurationContext(Configuration(wrapped.resources.configuration))

        assertEquals(RELEASE_GREETING, derived.getString(R.string.lh_test_greeting))
    }

    @Test
    fun wrappingAWrappedContextReturnsItUnchanged() {
        val wrapped = LingoHub.wrap(context)

        assertSame(wrapped, LingoHub.wrap(wrapped))
    }

    @Test
    fun lookupsOnBackgroundThreadsPickUpANewRelease() {
        val applicationContext = context.applicationContext
        val readingTheRelease = CountDownLatch(READERS)
        val failures = Collections.synchronizedList(mutableListOf<Throwable>())
        // Each reader reads the installed release, keeps reading while the next
        // one is installed, and stops once it reads the next one.
        val readers = List(READERS) {
            thread {
                try {
                    var sawRelease = false
                    val deadline = SystemClock.uptimeMillis() + 10_000
                    while (SystemClock.uptimeMillis() < deadline) {
                        when (val text = applicationContext.getString(R.string.lh_test_greeting)) {
                            RELEASE_GREETING -> if (!sawRelease) {
                                sawRelease = true
                                readingTheRelease.countDown()
                            }
                            NEXT_RELEASE_GREETING -> if (sawRelease) return@thread
                            else -> throw AssertionError("unexpected string: $text")
                        }
                    }
                    throw AssertionError("did not read the release and then the next one")
                } catch (e: Throwable) {
                    failures += e
                }
            }
        }
        assertTrue("the readers did not start", readingTheRelease.await(10, TimeUnit.SECONDS))

        installRelease("en", text("lh_test_greeting", NEXT_RELEASE_GREETING))
        readers.forEach { it.join() }

        assertEquals(emptyList<Throwable>(), failures)
    }

    private companion object {
        const val APK_GREETING = "Hello from the APK"
        const val RELEASE_GREETING = "Hello from the release"
        const val NEXT_RELEASE_GREETING = "Hello from the next release"
        const val READERS = 4

        // Package 0x7f (the app), type 0xff: no such resource.
        const val MISSING_RESOURCE_ID = 0x7fff0000
    }
}
