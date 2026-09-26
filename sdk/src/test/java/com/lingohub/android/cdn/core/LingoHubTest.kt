package com.lingohub.android.cdn.core

import com.lingohub.android.cdn.data.Preferences
import com.lingohub.android.cdn.data.model.Environment
import com.lingohub.android.cdn.utils.awaitBundleTransitions
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import org.mockito.kotlin.*
import java.util.*
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread

class LingoHubTest: BaseContextTest() {
    private lateinit var mockLingoHubUpdateListener: LingoHubUpdateListener

    @BeforeEach
    override fun setup() {
        super.setup()
        mockLingoHubUpdateListener = mock()
        LingoHub.configure(baseContext, "test-api-key", Environment.PRODUCTION)
        awaitBundleTransitions()
        LingoHub.addUpdateListener( mockLingoHubUpdateListener)
    }

    @Test
    fun `test initialization with valid parameters`() {
        verify(baseContext).packageName
        verify(baseContext).packageManager
        verify(baseContext).filesDir
        verify(baseContext).getSharedPreferences("Lingohub", 0)

        assert(LingoHub.apiKey == "test-api-key")
        assert(LingoHub.environment == Environment.PRODUCTION)
    }

    @Test
    fun `client id is a generated UUID and persisted when none is stored`() {
        // configure() ran in setup() with no stored client id.
        UUID.fromString(LingoHub.clientId)
        verify(sharedPreferencesEditor).putString(Preferences.CLIENT_ID, LingoHub.clientId)
    }

    @Test
    fun `stored client id is reused instead of generating a new one`() {
        // setup() already configured once without a stored id and saved one.
        clearInvocations(sharedPreferencesEditor)
        whenever(sharedPreferences.getString(Preferences.CLIENT_ID, null)).thenReturn("stored-client-id")

        LingoHub.configure(baseContext, "test-api-key", Environment.PRODUCTION)
        awaitBundleTransitions()

        assert(LingoHub.clientId == "stored-client-id")
        verify(sharedPreferencesEditor, never()).putString(eq(Preferences.CLIENT_ID), any())
    }

    @Test
    fun `runIfConfigured runs only until the app configures the SDK again`() {
        val generation = LingoHub.configurationGeneration
        var runs = 0

        assertTrue(LingoHub.runIfConfigured(generation) { runs++ })
        LingoHub.configure(baseContext, "test-api-key", Environment.STAGING)
        awaitBundleTransitions()

        assertFalse(LingoHub.runIfConfigured(generation) { runs++ })
        assertEquals(1, runs)
    }

    @Test
    @Timeout(10, unit = TimeUnit.SECONDS)
    fun `configure() waits for what runIfConfigured runs`() {
        val generation = LingoHub.configurationGeneration
        val entered = CountDownLatch(1)
        val proceed = CountDownLatch(1)
        val activation = thread {
            LingoHub.runIfConfigured(generation) {
                entered.countDown()
                proceed.await()
            }
        }
        entered.await()

        val reconfiguration = thread { LingoHub.configure(baseContext, "test-api-key", Environment.STAGING) }
        reconfiguration.join(200)
        assertTrue(reconfiguration.isAlive, "configure() must wait")
        assertEquals(generation, LingoHub.configurationGeneration, "Nothing may change while the block runs")

        proceed.countDown()
        activation.join()
        reconfiguration.join()
        awaitBundleTransitions()
        assertEquals(generation + 1, LingoHub.configurationGeneration)
        assertEquals(Environment.STAGING, LingoHub.environment)
    }

    @Test
    fun `test setLocale updates current locale`() {
        val testLocale = Locale("de")
        LingoHub.setLocale(testLocale)

        assert(LocaleProvider.currentLocale == testLocale)
    }
}