package com.lingohub.android.cdn.data

import android.content.pm.ApplicationInfo
import com.lingohub.android.cdn.core.BaseContextTest
import com.lingohub.android.cdn.core.LingoHub
import com.lingohub.android.cdn.core.LingoHubSDKError
import com.lingohub.android.cdn.data.model.BundleInfo
import com.lingohub.android.cdn.data.model.Environment
import com.lingohub.android.cdn.utils.InMemorySharedPreferences
import com.lingohub.android.cdn.utils.RecordingListener
import com.lingohub.android.cdn.utils.configureLingoHub
import kotlinx.coroutines.test.runTest
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.inOrder
import org.mockito.kotlin.mock
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import retrofit2.HttpException
import retrofit2.Response
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * The "Failures and retries" policy end to end through [LingoHub.update] (lingohub/organization#2351):
 * minimum interval, the single retry after a 5xx, pauses that survive relaunches, and the fresh check
 * after a failed download.
 */
@Timeout(10, unit = TimeUnit.SECONDS)
class UpdaterSchedulingTest : BaseContextTest() {
    private val api: Api = mock()
    private val fileHelper: IFileHelper = mock()
    private val listener = RecordingListener()
    private val storage = InMemorySharedPreferences()

    private val minute = 60_000L
    private var now = 1_800_000_000_000L
    private val waits = mutableListOf<Long>()

    @BeforeEach
    override fun setup() {
        super.setup()
        // The real Preferences over storage that survives a "relaunch"
        whenever(baseContext.getSharedPreferences(any(), any())).thenReturn(storage)
        configureLingoHub(baseContext)
        LingoHub.api = api
        LingoHub.fileHelper = fileHelper
        LingoHub.appVersionName = APP_VERSION
        relaunch()
        LingoHub.addUpdateListener(listener)
    }

    @AfterEach
    override fun tearDown() {
        LingoHub.removeUpdateListener(listener)
        LingoHub.minimumCheckIntervalOverrideMs = null
        super.tearDown()
    }

    /** The next app launch: fresh SDK objects over the same stored preferences. */
    private fun relaunch() {
        LingoHub.preferences = Preferences(baseContext)
        LingoHub.updater = Updater(BlockingCoroutineScope(), clock = { now }, waitBeforeRetry = { waits += it })
    }

    /** The schedule stored for the configured app version, environment and key. */
    private val storedSchedule: UpdateSchedule
        get() = Preferences(baseContext).getUpdateSchedule(
            UpdateSchedule.Scope.of(LingoHub.appVersionName, LingoHub.environment, LingoHub.apiKey.orEmpty())
        )

    // --- Minimum interval ---

    @Test
    fun `minimum interval skips checks after a successful update, across relaunches`() = runTest {
        whenever(api.getBundleInfo(any(), any())).thenReturn(noContent(), noContent())

        LingoHub.update()
        relaunch()
        now += 15 * minute - 1
        LingoHub.update()
        verify(api, times(1)).getBundleInfo(any(), any())

        now += 1
        LingoHub.update()
        verify(api, times(2)).getBundleInfo(any(), any())
        assertEquals(emptyList<LingoHubSDKError>(), listener.failures)
    }

    @Test
    fun `installed release and nothing published start the minimum interval`() = runTest {
        whenever(api.getBundleInfo(any(), any())).thenReturn(release(), problem(404, "DISTRIBUTION_NOT_FOUND"))
        whenever(api.downloadBundle(any())).thenReturn("bundle".toResponseBody())

        LingoHub.update()
        verify(fileHelper).installBundle(any())
        LingoHub.update()
        verify(api, times(1)).getBundleInfo(any(), any())

        now += 15 * minute
        LingoHub.update()
        LingoHub.update()
        verify(api, times(2)).getBundleInfo(any(), any())
        assertEquals(emptyList<LingoHubSDKError>(), listener.failures)
    }

    @Test
    fun `interval defaults to 15 minutes, to none in debuggable apps, and follows setMinimumCheckInterval`() {
        assertEquals(15 * minute, LingoHub.minimumCheckIntervalMs)

        whenever(baseContext.applicationInfo).thenReturn(ApplicationInfo().apply { flags = ApplicationInfo.FLAG_DEBUGGABLE })
        configureLingoHub(baseContext)
        assertEquals(0L, LingoHub.minimumCheckIntervalMs)

        LingoHub.setMinimumCheckInterval(1, TimeUnit.HOURS)
        assertEquals(60 * minute, LingoHub.minimumCheckIntervalMs)
        LingoHub.setMinimumCheckInterval(-5, TimeUnit.SECONDS)
        assertEquals(0L, LingoHub.minimumCheckIntervalMs)
    }

    // --- Transport errors ---

    @Test
    fun `transport error is not retried and the next call checks again`() = runTest {
        whenever(api.getBundleInfo(any(), any())).thenAnswer { throw IOException("offline") }.thenReturn(noContent())

        LingoHub.update()
        verify(api, times(1)).getBundleInfo(any(), any())
        assertNull(listener.failures.single().statusCode)
        assertEquals(emptyList<Long>(), waits)

        LingoHub.update()
        verify(api, times(2)).getBundleInfo(any(), any())
    }

    // --- 5xx ---

    @Test
    fun `5xx is retried once after two to five seconds`() = runTest {
        whenever(api.getBundleInfo(any(), any())).thenReturn(problem(503), noContent())

        LingoHub.update()

        verify(api, times(2)).getBundleInfo(any(), any())
        assertTrue(waits.single() in 2_000L..5_000L, "$waits")
        assertEquals(emptyList<LingoHubSDKError>(), listener.failures)
        assertNull(storedSchedule.cooldown)
    }

    @Test
    fun `5xx retry follows Retry-After`() = runTest {
        whenever(api.getBundleInfo(any(), any())).thenReturn(problem(503, retryAfter = "4"), noContent())

        LingoHub.update()

        verify(api, times(2)).getBundleInfo(any(), any())
        assertEquals(listOf(4_000L), waits)
    }

    @Test
    fun `persistent 5xx pauses checks with backoff across relaunches`() = runTest {
        whenever(api.getBundleInfo(any(), any())).thenReturn(problem(503), problem(503), problem(502), problem(502), noContent())

        LingoHub.update()
        verify(api, times(2)).getBundleInfo(any(), any())
        assertEquals(now + 5 * minute, storedSchedule.cooldown?.untilMs)

        // Paused, also after a relaunch: the pause is reported without a request
        relaunch()
        now += 5 * minute - 1
        LingoHub.update()
        verify(api, times(2)).getBundleInfo(any(), any())

        // The second failed update in a row pauses for 10 minutes
        now += 1
        LingoHub.update()
        verify(api, times(4)).getBundleInfo(any(), any())
        assertEquals(now + 10 * minute, storedSchedule.cooldown?.untilMs)
        now += 10 * minute - 1
        LingoHub.update()
        verify(api, times(4)).getBundleInfo(any(), any())

        // An answer ends the series
        now += 1
        LingoHub.update()
        verify(api, times(5)).getBundleInfo(any(), any())
        assertEquals(listOf(503, 503, 502, 502), listener.failures.map { it.statusCode })
        assertEquals(0, storedSchedule.consecutiveServerErrors)
        assertNull(storedSchedule.cooldown)
    }

    @Test
    fun `Retry-After over ten seconds skips the retry and pauses checks`() = runTest {
        val start = now
        whenever(api.getBundleInfo(any(), any())).thenReturn(problem(503, retryAfter = "1800"))

        LingoHub.update()

        verify(api, times(1)).getBundleInfo(any(), any())
        assertEquals(emptyList<Long>(), waits)
        assertEquals(start + 30 * minute, storedSchedule.cooldown?.untilMs)
    }

    // --- 429 ---

    @Test
    fun `429 pauses checks for an hour across relaunches`() = runTest {
        whenever(api.getBundleInfo(any(), any())).thenReturn(problem(429, "USAGE_LIMIT_EXCEEDED"), noContent())

        LingoHub.update()
        verify(api, times(1)).getBundleInfo(any(), any())
        assertEquals(emptyList<Long>(), waits, "A 429 is not retried")

        relaunch()
        now += 60 * minute - 1
        LingoHub.update()
        verify(api, times(1)).getBundleInfo(any(), any())
        val failures = listener.failures
        assertEquals(listOf(429, 429), failures.map { it.statusCode })
        assertEquals(listOf("USAGE_LIMIT_EXCEEDED"), failures.last().errorCodes)

        now += 1
        LingoHub.update()
        verify(api, times(2)).getBundleInfo(any(), any())
    }

    @Test
    fun `429 pause follows a longer Retry-After`() = runTest {
        val start = now
        whenever(api.getBundleInfo(any(), any())).thenReturn(problem(429, "USAGE_LIMIT_EXCEEDED", retryAfter = "10800"))

        LingoHub.update()

        assertEquals(start + 180 * minute, storedSchedule.cooldown?.untilMs)
    }

    @Test
    fun `new app version checks despite a pause`() = runTest {
        whenever(api.getBundleInfo(any(), any())).thenReturn(problem(429, "USAGE_LIMIT_EXCEEDED"), noContent())
        LingoHub.update()

        LingoHub.appVersionName = "2.0.0"
        relaunch()
        LingoHub.update()

        verify(api, times(2)).getBundleInfo(any(), any())
    }

    @Test
    fun `another environment or CDN key checks despite the interval and a pause`() = runTest {
        whenever(api.getBundleInfo(any(), any())).thenReturn(noContent(), problem(429, "USAGE_LIMIT_EXCEEDED"), noContent(), noContent())

        // A successful check starts the minimum interval
        LingoHub.update()

        // Staging is checked all the same, and its 429 pauses staging
        LingoHub.environment = Environment.STAGING
        LingoHub.update()
        verify(api, times(2)).getBundleInfo(any(), any())
        assertEquals(listOf(429), listener.failures.map { it.statusCode })

        // Another CDN key is checked despite that pause
        LingoHub.apiKey = "lh-cdn_another-key"
        LingoHub.update()
        verify(api, times(3)).getBundleInfo(any(), any())

        // As is the next environment switch, while the interval of the last one runs
        LingoHub.environment = Environment.TEST
        LingoHub.update()
        verify(api, times(4)).getBundleInfo(any(), any())
        assertEquals(1, listener.failures.size)
    }

    @Test
    fun `reconfiguring during the retry wait keeps the cycle on its configuration`() = runTest {
        LingoHub.apiKey = "lh-cdn_test-key"
        whenever(api.getBundleInfo(any(), any())).thenReturn(problem(503), problem(429, "USAGE_LIMIT_EXCEEDED"))
        val stagingScope = UpdateSchedule.Scope.of(APP_VERSION, Environment.STAGING, "lh-cdn_staging-key")
        val stagingPause = UpdateSchedule(stagingScope).recordUsageLimit(listOf("USAGE_LIMIT_EXCEEDED"), null, now)
        LingoHub.updater = Updater(BlockingCoroutineScope(), clock = { now }, waitBeforeRetry = {
            // The app configures staging while the TEST cycle waits for its retry, and a staging update
            // records a pause of its own meanwhile
            LingoHub.environment = Environment.STAGING
            LingoHub.apiKey = "lh-cdn_staging-key"
            LingoHub.preferences.saveUpdateSchedule(stagingPause)
        })

        LingoHub.update()

        val authorizations = argumentCaptor<String>()
        val bodies = argumentCaptor<PackageRequest>()
        verify(api, times(2)).getBundleInfo(authorizations.capture(), bodies.capture())
        assertEquals(listOf("Bearer lh-cdn_test-key", "Bearer lh-cdn_test-key"), authorizations.allValues, "The retry belongs to the cycle it retries")
        assertEquals(listOf("TEST", "TEST"), bodies.allValues.map { it.distributionEnvironment })
        assertEquals(listOf(429), listener.failures.map { it.statusCode })
        assertEquals(stagingPause, Preferences(baseContext).getUpdateSchedule(stagingScope), "A replaced cycle must not overwrite the current schedule")
    }

    // --- Client errors ---

    @Test
    fun `client errors are neither retried nor paused`() = runTest {
        whenever(api.getBundleInfo(any(), any())).thenReturn(problem(401, "CDN_KEY_NOT_FOUND"), problem(401, "CDN_KEY_NOT_FOUND"))

        LingoHub.update()
        verify(api, times(1)).getBundleInfo(any(), any())
        assertEquals(emptyList<Long>(), waits)
        assertNull(storedSchedule.cooldown)

        LingoHub.update()
        verify(api, times(2)).getBundleInfo(any(), any())
        assertEquals(listOf(401, 401), listener.failures.map { it.statusCode })
    }

    // --- Downloads ---

    @Test
    fun `failed download gets one fresh check for a new URL`() = runTest {
        whenever(api.getBundleInfo(any(), any())).thenReturn(release("$FILES_URL?expired"), release("$FILES_URL?fresh"))
        whenever(api.downloadBundle(any())).thenThrow(httpException(403)).thenReturn("bundle".toResponseBody())

        LingoHub.update()

        verify(api, times(2)).getBundleInfo(any(), any())
        val downloads = inOrder(api)
        downloads.verify(api).downloadBundle("$FILES_URL?expired")
        downloads.verify(api).downloadBundle("$FILES_URL?fresh")
        verify(fileHelper).installBundle(any())
        assertEquals(emptyList<LingoHubSDKError>(), listener.failures)
    }

    @Test
    fun `download that fails again gives up until the next call`() = runTest {
        whenever(api.getBundleInfo(any(), any())).thenReturn(release(), release(), noContent())
        whenever(api.downloadBundle(any())).thenThrow(httpException(403), httpException(503))

        LingoHub.update()
        verify(api, times(2)).getBundleInfo(any(), any())
        verify(api, times(2)).downloadBundle(any())
        assertEquals(503, listener.failures.single().statusCode)
        assertNull(storedSchedule.cooldown, "A storage failure does not pause checks")

        LingoHub.update()
        verify(api, times(3)).getBundleInfo(any(), any())
    }

    @Test
    fun `fresh check after a failed download is not retried`() = runTest {
        whenever(api.getBundleInfo(any(), any())).thenReturn(release(), problem(503))
        whenever(api.downloadBundle(any())).thenThrow(httpException(403))

        LingoHub.update()

        verify(api, times(2)).getBundleInfo(any(), any())
        assertEquals(emptyList<Long>(), waits)
        assertEquals(503, storedSchedule.cooldown?.statusCode)
    }

    @Test
    fun `transport error during download is not retried`() = runTest {
        whenever(api.getBundleInfo(any(), any())).thenReturn(release())
        whenever(api.downloadBundle(any())).thenAnswer { throw IOException("connection lost") }

        LingoHub.update()

        verify(api, times(1)).getBundleInfo(any(), any())
        verify(api, times(1)).downloadBundle(any())
        assertNull(listener.failures.single().statusCode)
    }

    private companion object {
        const val APP_VERSION = "1.0.0"
        const val FILES_URL = "https://cdn.lingohub.com/bundles/test.zip"

        fun release(filesUrl: String = FILES_URL): Response<BundleInfo> =
            Response.success(BundleInfo(id = "123123", name = "Version 1", filesUrl = filesUrl, createdAt = "2022-01-01T00:00:00.000Z"))

        fun noContent(): Response<BundleInfo> = Response.success(204, null as BundleInfo?)

        /** An error answer of the check endpoint: a problem body with [info], and optionally Retry-After. */
        fun problem(code: Int, info: String? = null, retryAfter: String? = null): Response<BundleInfo> {
            val errors = info?.let { """[{"field":"X","infos":["$it"]}]""" } ?: "[]"
            val body = """{"type":"about:blank","status":$code,"detail":"HTTP $code","errors":$errors}"""
            val raw = okhttp3.Response.Builder()
                .code(code)
                .message("HTTP $code")
                .protocol(Protocol.HTTP_1_1)
                .request(Request.Builder().url("https://cdn.lingohub.com/v1/distributions/check").build())
                .apply { retryAfter?.let { header("Retry-After", it) } }
                .build()
            return Response.error(body.toResponseBody("application/json".toMediaType()), raw)
        }

        fun httpException(code: Int) = HttpException(Response.error<Any>(code, "".toResponseBody()))
    }
}
