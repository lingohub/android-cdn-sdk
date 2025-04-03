package com.helpers.core

import com.helpers.BaseContextTest
import com.helpers.data.Repository
import com.helpers.data.model.Environment
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.*
import java.util.*

class LingohubTest: BaseContextTest() {
    private lateinit var mockRepository: Repository
    private lateinit var mockLingohubUpdateListener: LingohubUpdateListener

    @BeforeEach
    override fun setup() {
        super.setup()
        mockRepository = mock()
        mockLingohubUpdateListener = mock()
        Lingohub.configure(baseContext, "test-api-key", Environment.PRODUCTION)
        Lingohub.addRepository(Locale.ENGLISH,  mockRepository)
        Lingohub.addUpdateListener( mockLingohubUpdateListener)
    }

    @Test
    fun `test initialization with valid parameters`() {
        verify(baseContext).contentResolver
        verify(baseContext).packageName
        verify(baseContext).packageManager
        verify(baseContext).resources
        verify(baseContext).filesDir
        verify(baseContext).getSharedPreferences("Lingohub", 0)

        assert(Lingohub.apiKey == "test-api-key")
        assert(Lingohub.environment == Environment.PRODUCTION)
    }

    @Test
    fun `test setLocale updates current locale`() {
        val testLocale = Locale("de")
        Lingohub.setLocale(testLocale)

        assert(LocaleProvider.currentLocale == testLocale)
    }
}