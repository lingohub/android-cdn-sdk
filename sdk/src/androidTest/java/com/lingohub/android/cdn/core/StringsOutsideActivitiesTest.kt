package com.lingohub.android.cdn.core

import android.app.Notification
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
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
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
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
        // Android hands manifest receivers a context that delegates to the Application.
        assertEquals(RELEASE_GREETING, strings!!.receiverContext)
        assertEquals(RELEASE_GREETING, strings.applicationContext)
        assertEquals(RELEASE_GREETING, strings.wrapped)
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
        val seen = Collections.synchronizedSet(mutableSetOf<String>())
        val failures = Collections.synchronizedList(mutableListOf<Throwable>())
        val running = AtomicBoolean(true)
        val readers = List(4) {
            thread {
                try {
                    while (running.get()) seen += applicationContext.getString(R.string.lh_test_greeting)
                } catch (e: Throwable) {
                    failures += e
                }
            }
        }

        try {
            installRelease("en", text("lh_test_greeting", "Hello from the next release"))
        } finally {
            running.set(false)
            readers.forEach { it.join() }
        }

        assertEquals(emptyList<Throwable>(), failures)
        assertTrue("unexpected strings: $seen", seen.all { it == RELEASE_GREETING || it == "Hello from the next release" })
        assertEquals("Hello from the next release", applicationContext.getString(R.string.lh_test_greeting))
    }

    private companion object {
        const val APK_GREETING = "Hello from the APK"
        const val RELEASE_GREETING = "Hello from the release"
    }
}
