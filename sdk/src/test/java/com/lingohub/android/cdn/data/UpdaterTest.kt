package com.lingohub.android.cdn.data

import com.lingohub.android.cdn.core.BaseContextTest
import com.lingohub.android.cdn.core.LingoHub
import com.lingohub.android.cdn.core.LingoHubSDKError
import com.lingohub.android.cdn.core.LingoHubUpdateListener
import com.lingohub.android.cdn.data.model.BundleInfo
import com.lingohub.android.cdn.data.model.BundleMetadata
import com.lingohub.android.cdn.utils.configureLingoHub
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.inOrder
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
        configureLingoHub(baseContext)
        Dispatchers.setMain(testDispatcher)
        LingoHub.api = api
        LingoHub.preferences = preferences
        LingoHub.fileHelper = fileHelper
        LingoHub.updater = Updater(BlockingCoroutineScope())
    }

    @Test
    fun `Download api call invoked upon receiving bundleInfo`() {
        val mockedBundle = getMockedBundleInfo()
        runTest {
            whenever(api.getBundleInfo()).thenReturn(mockedBundle)
            whenever(api.downloadBundle(any())).thenReturn("test".toResponseBody())
            LingoHub.update()
            verify(api, times(1)).downloadBundle(mockedBundle.body()!!.filesUrl)
        }
    }

    @Test
    fun `Bundle installed after download`() {
        val downloadResponse = "test".toResponseBody()
        val mockedBundle = getMockedBundleInfo()

        runTest {
            whenever(api.getBundleInfo()).thenReturn(mockedBundle)
            whenever(api.downloadBundle(any())).thenReturn(downloadResponse)
            LingoHub.updater.update()
            verify(fileHelper, times(1)).installBundle(any())
        }
    }

    @Test
    fun `Listeners are notified only after the refreshed bundle was read from disk`() {
        val listener: LingoHubUpdateListener = mock()
        LingoHub.addUpdateListener(listener)
        runTest {
            whenever(api.getBundleInfo()).thenReturn(getMockedBundleInfo())
            whenever(api.downloadBundle(any())).thenReturn("test".toResponseBody())
            LingoHub.update()

            val order = inOrder(fileHelper, listener)
            order.verify(fileHelper).installBundle(any())
            order.verify(fileHelper).readBundle()
            order.verify(listener).onUpdate()
        }
        LingoHub.removeUpdateListener(listener)
    }

    @Test
    fun `Concurrent update calls are single-flight`() = runTest {
        whenever(api.getBundleInfo()).thenReturn(getMockedBundleInfo())
        whenever(api.downloadBundle(any())).thenReturn("test".toResponseBody())
        val updater = Updater(QueueingCoroutineScope(this))

        updater.update()
        updater.update()
        advanceUntilIdle()

        verify(api, times(1)).getBundleInfo()

        // Once the first update finished, the guard is released again.
        updater.update()
        advanceUntilIdle()
        verify(api, times(2)).getBundleInfo()
    }

    @Test
    fun `Non-HTTPS bundle url is rejected without downloading`() {
        val listener: LingoHubUpdateListener = mock()
        LingoHub.addUpdateListener(listener)
        runTest {
            whenever(api.getBundleInfo()).thenReturn(
                Response.success(getBundleInfo(filesUrl = "http://cdn.lingohub.com/bundles/test.zip"))
            )
            LingoHub.update()
            verify(api, never()).downloadBundle(any())
            val captor = argumentCaptor<Throwable>()
            verify(listener).onFailure(captor.capture())
            assertTrue(captor.firstValue.message!!.contains("non-HTTPS"))
        }
        LingoHub.removeUpdateListener(listener)
    }

    @Test
    fun `204 response means up to date and is not reported as failure`() {
        val listener: LingoHubUpdateListener = mock()
        LingoHub.addUpdateListener(listener)
        runTest {
            whenever(api.getBundleInfo()).thenReturn(Response.success(204, null as BundleInfo?))
            LingoHub.update()
            verify(api, never()).downloadBundle(any())
            verify(listener, never()).onFailure(any())
        }
        LingoHub.removeUpdateListener(listener)
    }

    @Test
    fun `404 distribution not found is treated as no update, not failure`() {
        val listener: LingoHubUpdateListener = mock()
        LingoHub.addUpdateListener(listener)
        runTest {
            val body =
                """{"type":"about:blank","status":404,"detail":"Not Found","errors":[{"field":"DISTRIBUTION","infos":["DISTRIBUTION_NOT_FOUND"]}]}"""
                    .toResponseBody("application/json".toMediaType())
            whenever(api.getBundleInfo()).thenReturn(Response.error(404, body))
            LingoHub.update()
            verify(api, never()).downloadBundle(any())
            verify(listener, never()).onFailure(any())
        }
        LingoHub.removeUpdateListener(listener)
    }

    @Test
    fun `429 usage limit exceeded notifies a specific failure`() {
        val listener: LingoHubUpdateListener = mock()
        LingoHub.addUpdateListener(listener)
        runTest {
            val body =
                """{"type":"about:blank","status":429,"detail":"Too Many Requests","errors":[{"field":"USAGE","infos":["USAGE_LIMIT_EXCEEDED"]}]}"""
                    .toResponseBody("application/json".toMediaType())
            whenever(api.getBundleInfo()).thenReturn(Response.error(429, body))
            LingoHub.update()
            verify(api, never()).downloadBundle(any())
            val captor = argumentCaptor<Throwable>()
            verify(listener).onFailure(captor.capture())
            val error = captor.firstValue as LingoHubSDKError
            assertTrue(error.message!!.contains("usage limit", ignoreCase = true))
            assertEquals(429, error.statusCode)
            assertTrue("USAGE_LIMIT_EXCEEDED" in error.errorCodes)
        }
        LingoHub.removeUpdateListener(listener)
    }

    @Test
    fun `Bundle not deleted when app not updated`() {
        whenever(preferences.getBundleMetadata()).thenReturn(BundleMetadata("identifier", "4"))
        LingoHub.appVersionName = "4"
        runTest {
            LingoHub.checkIfUpdated()
            verify(fileHelper, never()).deleteBundle()
        }
    }

    @Test
    fun `Bundle deleted on app update`() {
        whenever(preferences.getBundleMetadata()).thenReturn(BundleMetadata("identifier", "19"))
        LingoHub.appVersionName = "20"
        runTest {
            LingoHub.checkIfUpdated()
            verify(fileHelper, times(1)).deleteBundle()
        }
    }

    @AfterEach
    override fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun getMockedBundleInfo(): Response<BundleInfo> {
        return Response.success(getBundleInfo())
    }

    private fun getBundleInfo(filesUrl: String = "https://cdn.lingohub.com/bundles/test.zip"): BundleInfo {
        return BundleInfo(
            id = "123123",
            createdAt = "2022-01-01T00:00:00.000Z",
            name = "Version 1",
            filesUrl = filesUrl,
        )
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

/**
 * Launches into the given scope (e.g. runTest's TestScope with a
 * StandardTestDispatcher) so launched work queues until the test advances the
 * dispatcher - required to observe two update() calls overlapping.
 */
private class QueueingCoroutineScope(private val delegate: CoroutineScope) : ICoroutineScope {
    override val coroutineContext: CoroutineContext = delegate.coroutineContext

    override fun launch(block: suspend CoroutineScope.() -> Unit): Job {
        return delegate.launch { block() }
    }
}
