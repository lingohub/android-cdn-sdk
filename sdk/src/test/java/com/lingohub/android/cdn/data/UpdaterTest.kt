package com.lingohub

import com.lingohub.android.cdn.core.BaseContextTest
import com.lingohub.android.cdn.core.Lingohub
import com.lingohub.android.cdn.data.Api
import com.lingohub.android.cdn.data.ICoroutineScope
import com.lingohub.android.cdn.data.IFileHelper
import com.lingohub.android.cdn.data.IPreferences
import com.lingohub.android.cdn.data.Updater
import com.lingohub.android.cdn.data.model.BundleInfo
import com.lingohub.android.cdn.data.model.BundleMetadata
import com.lingohub.android.cdn.utils.configureLingohub
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.TestCoroutineDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runBlockingTest
import kotlinx.coroutines.test.setMain
import okhttp3.ResponseBody.Companion.toResponseBody
import org.amshove.kluent.*
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.never
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import retrofit2.Response
import kotlin.coroutines.CoroutineContext

@OptIn(ExperimentalCoroutinesApi::class)
class UpdaterTest : BaseContextTest() {
    private val api: Api = mock()
    private val preferences: IPreferences = mock()
    private val fileHelper: IFileHelper = mock()

    private val testDispatcher = TestCoroutineDispatcher()
    private val managedCoroutineScope = TestCoroutineScope(testDispatcher)

    @BeforeEach
    override fun setup() {
        super.setup()
        configureLingohub(baseContext)
        Dispatchers.setMain(testDispatcher)
        Lingohub.api = api
        Lingohub.preferences = preferences
        Lingohub.fileHelper = fileHelper
        Lingohub.updater = Updater(managedCoroutineScope)
    }

    @Test
    fun `Download api call invoked upon receiving bundleInfo`() {
        val mockedBundle = getMockedBundleInfo()
        runBlockingTest {
            When calling api.getBundleInfo() doReturn mockedBundle
            Lingohub.update()
            verify(api, times(1)).downloadBundle(mockedBundle.body()!!.filesUrl)
        }
    }

    @Test
    fun `Unzip called after bundle downloaded`() {
        val downloadResponse = "test".toResponseBody()
        val mockedBundle = getMockedBundleInfo()

        runBlockingTest {
            When calling api.getBundleInfo() doReturn mockedBundle
            When calling api.downloadBundle(any()) doReturn downloadResponse
            Lingohub.updater.update()
            verify(fileHelper, times(1)).unzipBundle(any())
        }
    }

    @Test
    fun `Bundle not deleted when app not updated`() {
        When calling preferences.getBundleMetadata() doReturn BundleMetadata("identifier", "4")
        Lingohub.appVersionCode = "4"
        runBlockingTest {
            Lingohub.checkIfUpdated()
            verify(fileHelper, never()).deleteBundle()
        }
    }

    @Test
    fun `Bundle deleted on app update`() {
        When calling preferences.getBundleMetadata() doReturn BundleMetadata("identifier", "19")
        Lingohub.appVersionCode = "20"
        runBlockingTest {
            Lingohub.checkIfUpdated()
            verify(fileHelper, times(1)).deleteBundle()
        }
    }

    @AfterEach
    override fun tearDown() {
        Dispatchers.resetMain()
        testDispatcher.cleanupTestCoroutines()
    }

    class TestCoroutineScope(private val dispatcher: TestCoroutineDispatcher) : ICoroutineScope {
        override val coroutineContext: CoroutineContext = dispatcher

        override fun launch(block: suspend CoroutineScope.() -> Unit): Job {
            return runBlocking(dispatcher) {
                block()
                Job()
            }
        }
    }

    private fun getMockedBundleInfo(): Response<BundleInfo> {
        return Response.success(BundleInfo(
            id = "123123",
            createdAt = "2022-01-01T00:00:00.000Z",
            name = "Version 1",
            filesUrl = "url",

        ))
    }
}