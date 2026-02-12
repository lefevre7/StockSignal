package com.example.stocksignal.data.translation

import android.content.Context
import android.util.Log
import com.example.stocksignal.core.ExternalExecutionGate
import io.mockk.every
import io.mockk.mockkStatic
import java.io.File
import java.security.MessageDigest
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

@RunWith(RobolectricTestRunner::class)
class NewsTranslationServiceTest {

    @Before
    fun setup() {
        mockkStatic(Log::class)
        every { Log.d(any(), any()) } returns 0
        every { Log.w(any(), any<String>()) } returns 0
        every { Log.w(any(), any<String>(), any()) } returns 0
        every { Log.e(any(), any<String>()) } returns 0
        every { Log.e(any(), any<String>(), any()) } returns 0
    }

    @Test
    fun `local model file path uses litertlm extension`() {
        val context: Context = RuntimeEnvironment.getApplication()
        val service = NewsTranslationService(context, FakeRuntimeFactory(), ExternalExecutionGate())

        assertTrue(service.getLocalModelFilePath().endsWith(".litertlm"))
    }

    @Test
    fun `primary model url uses litertlm download`() {
        assertTrue(NewsTranslationService.PRIMARY_MODEL_URL.endsWith(".litertlm?download=true"))
    }

    @Test
    fun `local model validation caches size and sha`() = runTest {
        val context: Context = RuntimeEnvironment.getApplication()
        val service = NewsTranslationService(context, FakeRuntimeFactory(), ExternalExecutionGate())
        val modelDir = File(context.filesDir, "llm")
        modelDir.mkdirs()
        val modelFile = File(modelDir, "gemma3-1b-it-int4.litertlm")
        val payload = "test-model"
        modelFile.writeText(payload)

        val available = service.isLocalModelAvailable()
        assertTrue(available)
        assertEquals(modelFile.length(), service.getLocalModelExpectedBytes())
        assertEquals(sha256(payload), service.getLocalModelSha256())
    }

    private fun sha256(value: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val bytes = digest.digest(value.toByteArray())
        return bytes.joinToString("") { "%02x".format(it) }
    }

    private class FakeRuntimeFactory : LocalLlmRuntimeFactory {
        override fun create(config: LocalLlmRuntimeConfig): LocalLlmRuntime {
            return FakeRuntime()
        }
    }

    private class FakeRuntime : LocalLlmRuntime {
        override suspend fun generate(prompt: String, sampling: LlmSamplingConfig): String {
            return "ok"
        }

        override fun close() = Unit
    }
}
