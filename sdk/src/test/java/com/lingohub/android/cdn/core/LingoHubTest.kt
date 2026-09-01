package com.lingohub.android.cdn.core

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
        verify(baseContext).contentResolver
        verify(baseContext).packageName
        verify(baseContext).packageManager
        verify(baseContext).resources
        verify(baseContext).filesDir
        verify(baseContext).getSharedPreferences("Lingohub", 0)

        assert(LingoHub.apiKey == "test-api-key")
        assert(LingoHub.environment == Environment.PRODUCTION)
    }

    @Test
    fun `test setLocale updates current locale`() {
        val testLocale = Locale("de")
        LingoHub.setLocale(testLocale)

        assert(LocaleProvider.currentLocale == testLocale)
    }
}