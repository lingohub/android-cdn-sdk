package com.lingohub.android.cdn.data

import com.lingohub.android.cdn.core.BaseContextTest
import com.lingohub.android.cdn.core.Lingohub
import com.lingohub.android.cdn.core.LingohubUpdateListener
import com.lingohub.android.cdn.data.model.BundleInfo
import com.lingohub.android.cdn.data.model.BundleMetadata
import com.lingohub.android.cdn.utils.configureLingohub
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import retrofit2.Response
import kotlin.coroutines.CoroutineContext

@OptIn(ExperimentalCoroutinesApi::class)
class UpdaterTest : BaseContextTest() {
    private val api: Api = mock()
    private val preferences: IPreferences = mock()
    private val fileHelper: IFileHelper = mock()

    private val testDispatcher = UnconfinedTestDispatcher()

    @BeforeEach
    override fun setup() {
        super.setup()
        configureLingohub(baseContext)
        Dispatchers.setMain(testDispatcher)
        Lingohub.api = api
        Lingohub.preferences = preferences
        Lingohub.fileHelper = fileHelper
        Lingohub.updater = Updater(BlockingCoroutineScope())
    }

    @Test
    fun `Download api call invoked upon receiving bundleInfo`() {
        val mockedBundle = getMockedBundleInfo()
        runTest {
            whenever(api.getBundleInfo()).thenReturn(mockedBundle)
            Lingohub.update()
            verify(api, times(1)).downloadBundle(mockedBundle.body()!!.filesUrl)
        }
    }

    @Test
    fun `Unzip called after bundle downloaded`() {
        val downloadResponse = "test".toResponseBody()
        val mockedBundle = getMockedBundleInfo()

        runTest {
            whenever(api.getBundleInfo()).thenReturn(mockedBundle)
            whenever(api.downloadBundle(any())).thenReturn(downloadResponse)
            Lingohub.updater.update()
            verify(fileHelper, times(1)).unzipBundle(any())
        }
    }

    @Test
    fun `204 response means up to date and is not reported as failure`() {
        val listener: LingohubUpdateListener = mock()
        Lingohub.addUpdateListener(listener)
        runTest {
            whenever(api.getBundleInfo()).thenReturn(Response.success(204, null as BundleInfo?))
            Lingohub.update()
            verify(api, never()).downloadBundle(any())
            verify(listener, never()).onFailure(any())
        }
        Lingohub.removeUpdateListener(listener)
    }

    @Test
    fun `Bundle not deleted when app not updated`() {
        whenever(preferences.getBundleMetadata()).thenReturn(BundleMetadata("identifier", "4"))
        Lingohub.appVersionCode = "4"
        runTest {
            Lingohub.checkIfUpdated()
            verify(fileHelper, never()).deleteBundle()
        }
    }

    @Test
    fun `Bundle deleted on app update`() {
        whenever(preferences.getBundleMetadata()).thenReturn(BundleMetadata("identifier", "19"))
        Lingohub.appVersionCode = "20"
        runTest {
            Lingohub.checkIfUpdated()
            verify(fileHelper, times(1)).deleteBundle()
        }
    }

    @AfterEach
    override fun tearDown() {
        Dispatchers.resetMain()
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

/**
 * Executes launched blocks synchronously so tests can verify side effects immediately.
 */
private class BlockingCoroutineScope : ICoroutineScope {
    override val coroutineContext: CoroutineContext = Dispatchers.Unconfined

    override fun launch(block: suspend CoroutineScope.() -> Unit): Job {
        return runBlocking {
            block()
            Job()
        }
    }
}
