package com.example.stocksignal.data.stooq.network

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.stocksignal.core.ExternalExecutionGate
import com.example.stocksignal.notifications.NotificationDiagnosticsRepository
import io.mockk.coEvery
import io.mockk.mockk
import java.net.SocketTimeoutException
import java.time.Duration
import java.util.Collections
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import okhttp3.tls.HandshakeCertificates
import okhttp3.tls.HeldCertificate
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class StooqBlockInterceptorIntegrationTest {

    private lateinit var server: MockWebServer
    private lateinit var clientCertificates: HandshakeCertificates

    @Before
    fun setUp() {
        val localhostCertificate = HeldCertificate.Builder()
            .addSubjectAlternativeName("localhost")
            .build()
        val serverCertificates = HandshakeCertificates.Builder()
            .heldCertificate(localhostCertificate)
            .build()
        clientCertificates = HandshakeCertificates.Builder()
            .addTrustedCertificate(localhostCertificate.certificate)
            .build()
        server = MockWebServer()
        server.useHttps(serverCertificates.sslSocketFactory(), false)
        server.start()
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun enforcesGapBetweenSequentialRequests() {
        val receivedAtMillis = synchronizedTimes()
        server.dispatcher = alwaysOkDispatcher(receivedAtMillis)

        val interceptor = createInterceptor(baseGapMs = FIXED_GAP_MS, jitterMs = 0L)
        val client = buildClient(interceptor)

        repeat(3) { idx ->
            val path = if (idx % 2 == 0) {
                "/q/a2/d/?s=tsla.us&i=10"
            } else {
                "/q/g/?s=tsla.us"
            }
            executeSuccess(client, path)
        }

        waitForRequests(receivedAtMillis, expectedSize = 3)
        assertMinGap(receivedAtMillis)
    }

    @Test
    fun enforcesGapWhenRequestsStartConcurrently() = runBlocking {
        val receivedAtMillis = synchronizedTimes()
        server.dispatcher = alwaysOkDispatcher(receivedAtMillis)

        val interceptor = createInterceptor(baseGapMs = FIXED_GAP_MS, jitterMs = 0L)
        val client = buildClient(interceptor)
        val startLatch = CountDownLatch(1)

        val jobs = listOf(
            async(Dispatchers.IO) {
                startLatch.await()
                executeSuccess(client, "/q/a2/d/?s=aaa.us&i=10")
            },
            async(Dispatchers.IO) {
                startLatch.await()
                executeSuccess(client, "/q/g/?s=bbb.us")
            },
            async(Dispatchers.IO) {
                startLatch.await()
                executeSuccess(client, "/q/a2/d/?s=ccc.us&i=10")
            }
        )

        startLatch.countDown()
        jobs.awaitAll()

        waitForRequests(receivedAtMillis, expectedSize = 3)
        assertMinGap(receivedAtMillis)
    }

    @Test
    fun enforcesGapAfterTimeoutBeforeNextAttempt() {
        val receivedAtMillis = synchronizedTimes()
        var requestCount = 0
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                receivedAtMillis.add(System.currentTimeMillis())
                return if (requestCount++ == 0) {
                    MockResponse()
                        .setResponseCode(200)
                        .setHeadersDelay(500, TimeUnit.MILLISECONDS)
                        .setBody("slow")
                } else {
                    MockResponse().setResponseCode(200).setBody("ok")
                }
            }
        }

        val interceptor = createInterceptor(baseGapMs = FIXED_GAP_MS, jitterMs = 0L)
        val client = buildClient(interceptor, readTimeoutMs = 150L)

        val first = Request.Builder()
            .url(server.url("/q/a2/d/?s=timeout.us&i=10"))
            .build()
        try {
            client.newCall(first).execute().use { response ->
                response.body?.string()
            }
            throw AssertionError("Expected SocketTimeoutException for the first request.")
        } catch (expected: SocketTimeoutException) {
            // expected
        }

        executeSuccess(client, "/q/a2/d/?s=recover.us&i=10")

        waitForRequests(receivedAtMillis, expectedSize = 2)
        assertMinGap(receivedAtMillis)
    }

    @Test
    fun blockedStateShortCircuitsWithoutNetworkCall() {
        val receivedAtMillis = synchronizedTimes()
        server.dispatcher = alwaysOkDispatcher(receivedAtMillis)

        val diagnostics = mockk<NotificationDiagnosticsRepository>(relaxed = true) {
            coEvery { getStooqBlockedInfo() } returns NotificationDiagnosticsRepository.StooqBlockedInfo(
                blockedAtMillis = null,
                blockedUntilMillis = null,
                message = null
            )
        }
        val blocker = StooqRequestBlocker(diagnostics)
        blocker.blockFor(Duration.ofMinutes(5), "manual block")
        val reporter = mockk<StooqBlockReporter>(relaxed = true)
        val interceptor = StooqBlockInterceptor(
            blocker = blocker,
            blockReporter = reporter,
            diagnosticsRepository = diagnostics,
            executionGate = ExternalExecutionGate()
        )
        interceptor.configurePacingForTest(baseRequestGapMs = FIXED_GAP_MS, jitterMs = 0L)
        val client = buildClient(interceptor)

        val request = Request.Builder()
            .url(server.url("/q/a2/d/?s=blocked.us&i=10"))
            .build()
        try {
            client.newCall(request).execute().use { response ->
                response.body?.string()
            }
            throw AssertionError("Expected StooqBlockedException when blocker is active.")
        } catch (expected: StooqBlockedException) {
            // expected
        }

        // No request should have reached the server while blocked.
        assertEquals(0, receivedAtMillis.size)
    }

    private fun createInterceptor(
        baseGapMs: Long,
        jitterMs: Long
    ): StooqBlockInterceptor {
        val diagnostics = mockk<NotificationDiagnosticsRepository>(relaxed = true) {
            coEvery { getStooqBlockedInfo() } returns NotificationDiagnosticsRepository.StooqBlockedInfo(
                blockedAtMillis = null,
                blockedUntilMillis = null,
                message = null
            )
        }
        val blocker = StooqRequestBlocker(diagnostics)
        val reporter = mockk<StooqBlockReporter>(relaxed = true)
        return StooqBlockInterceptor(
            blocker = blocker,
            blockReporter = reporter,
            diagnosticsRepository = diagnostics,
            executionGate = ExternalExecutionGate()
        ).also {
            it.configurePacingForTest(baseRequestGapMs = baseGapMs, jitterMs = jitterMs)
        }
    }

    private fun buildClient(
        interceptor: StooqBlockInterceptor,
        readTimeoutMs: Long = 5_000L
    ): OkHttpClient {
        return OkHttpClient.Builder()
            .connectTimeout(5_000L, TimeUnit.MILLISECONDS)
            .readTimeout(readTimeoutMs, TimeUnit.MILLISECONDS)
            .writeTimeout(5_000L, TimeUnit.MILLISECONDS)
            .sslSocketFactory(clientCertificates.sslSocketFactory(), clientCertificates.trustManager)
            .hostnameVerifier { _, _ -> true }
            .addInterceptor(interceptor)
            .build()
    }

    private fun executeSuccess(client: OkHttpClient, path: String) {
        val request = Request.Builder()
            .url(server.url(path))
            .build()
        client.newCall(request).execute().use { response ->
            assertEquals(200, response.code)
            response.body?.string()
        }
    }

    private fun alwaysOkDispatcher(
        receivedAtMillis: MutableList<Long>
    ): Dispatcher {
        return object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                receivedAtMillis.add(System.currentTimeMillis())
                return MockResponse().setResponseCode(200).setBody("ok")
            }
        }
    }

    private fun synchronizedTimes(): MutableList<Long> {
        return Collections.synchronizedList(mutableListOf())
    }

    private fun waitForRequests(
        receivedAtMillis: MutableList<Long>,
        expectedSize: Int
    ) {
        repeat(100) {
            if (receivedAtMillis.size >= expectedSize) return
            Thread.sleep(50)
        }
        throw AssertionError(
            "Timed out waiting for $expectedSize requests; got ${receivedAtMillis.size}"
        )
    }

    private fun assertMinGap(receivedAtMillis: MutableList<Long>) {
        val sorted = receivedAtMillis.toList().sorted()
        val gaps = sorted.zipWithNext { a, b -> b - a }
        gaps.forEach { gap ->
            assertTrue(
                "Expected request gap >= ${MIN_EXPECTED_GAP_MS}ms but got ${gap}ms",
                gap >= MIN_EXPECTED_GAP_MS
            )
        }
    }

    companion object {
        private const val FIXED_GAP_MS = 3_000L
        private const val MIN_EXPECTED_GAP_MS = 2_800L
    }
}
