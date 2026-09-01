package com.lingohub.android.cdn.core

import com.lingohub.android.cdn.data.Preferences
import com.lingohub.android.cdn.data.Repository
import com.lingohub.android.cdn.data.model.Environment
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.*
import java.util.*

class LingoHubTest: BaseContextTest() {
    private lateinit var mockRepository: Repository
    private lateinit var mockLingoHubUpdateListener: LingoHubUpdateListener

    @BeforeEach
    override fun setup() {
        super.setup()
        mockRepository = mock()
        mockLingoHubUpdateListener = mock()
        LingoHub.configure(baseContext, "test-api-key", Environment.PRODUCTION)
        LingoHub.addRepository(Locale.ENGLISH,  mockRepository)
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

        assert(LingoHub.clientId == "stored-client-id")
        verify(sharedPreferencesEditor, never()).putString(eq(Preferences.CLIENT_ID), any())
    }

    @Test
    fun `test setLocale updates current locale`() {
        val testLocale = Locale("de")
        LingoHub.setLocale(testLocale)

        assert(LocaleProvider.currentLocale == testLocale)
    }
}