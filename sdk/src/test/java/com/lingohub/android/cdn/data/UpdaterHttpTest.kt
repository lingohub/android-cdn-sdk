package com.lingohub.android.cdn.data

import com.lingohub.android.cdn.core.BaseContextTest
import com.lingohub.android.cdn.core.LingoHub
import com.lingohub.android.cdn.utils.InMemorySharedPreferences
import com.lingohub.android.cdn.utils.RecordingListener
import com.lingohub.android.cdn.utils.configureLingoHub
import com.sun.net.httpserver.HttpServer
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import org.mockito.kotlin.any
import org.mockito.kotlin.whenever
import java.net.InetAddress
import java.net.InetSocketAddress
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.TimeUnit

/**
 * The update policy on the wire (lingohub/organization#2351): the real Retrofit/OkHttp client against a
 * local HTTP server, so requests OkHttp would send on its own are counted too.
 */
@Timeout(20, unit = TimeUnit.SECONDS)
class UpdaterHttpTest : BaseContextTest() {
    private data class Answer(val status: Int, val headers: Map<String, String> = emptyMap())

    private lateinit var server: HttpServer
    private val answers = ConcurrentLinkedQueue<Answer>()
    private val authorizations = ConcurrentLinkedQueue<String>()
    private val listener = RecordingListener()
    private val waits = mutableListOf<Long>()

    @BeforeEach
    override fun setup() {
        super.setup()
        whenever(baseContext.getSharedPreferences(any(), any())).thenReturn(InMemorySharedPreferences())
        configureLingoHub(baseContext)
        LingoHub.apiKey = "lh-cdn_test-key"

        server = HttpServer.create(InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0)
        server.createContext("/v1/distributions/check") { exchange ->
            exchange.requestBody.readBytes()
            authorizations += exchange.requestHeaders.getFirst("Authorization").orEmpty()
            val answer = answers.poll() ?: Answer(500)
            answer.headers.forEach { (name, value) -> exchange.responseHeaders.add(name, value) }
            val body = """{"type":"about:blank","status":${answer.status},"detail":"HTTP ${answer.status}","errors":[]}""".toByteArray()
            exchange.responseHeaders.add("Content-Type", "application/json")
            exchange.sendResponseHeaders(answer.status, body.size.toLong())
            exchange.responseBody.use { it.write(body) }
        }
        server.start()

        LingoHub.api = Api.build("http://${server.address.hostString}:${server.address.port}/")
        LingoHub.preferences = Preferences(baseContext)
        LingoHub.updater = Updater(BlockingCoroutineScope(), waitBeforeRetry = { waits += it })
        LingoHub.addUpdateListener(listener)
    }

    @AfterEach
    override fun tearDown() {
        LingoHub.removeUpdateListener(listener)
        server.stop(0)
        super.tearDown()
    }

    @Test
    fun `a 503 with Retry-After 0 is sent twice in total, not four times`() {
        repeat(4) { answers += Answer(503, mapOf("Retry-After" to "0")) }

        LingoHub.update()

        // OkHttp would follow each of these 503s up on its own, which the one-shot check body prevents
        assertEquals(2, authorizations.size)
        assertEquals(listOf(0L), waits)
        assertEquals(listOf(503), listener.failures.map { it.statusCode })
        assertEquals(listOf("Bearer lh-cdn_test-key", "Bearer lh-cdn_test-key"), authorizations.toList())
    }

    @Test
    fun `a 408 is sent once`() {
        repeat(2) { answers += Answer(408) }

        LingoHub.update()

        // OkHttp would repeat a 408 on its own
        assertEquals(1, authorizations.size)
        assertEquals(listOf(408), listener.failures.map { it.statusCode })
    }

    @Test
    fun `a 503 without Retry-After gets the one retry of the policy`() {
        repeat(3) { answers += Answer(503) }

        LingoHub.update()

        assertEquals(2, authorizations.size)
        assertTrue(waits.single() in 2_000L..5_000L, "$waits")
        assertEquals(listOf(503), listener.failures.map { it.statusCode })
    }
}
