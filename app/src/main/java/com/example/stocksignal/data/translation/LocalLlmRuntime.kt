package com.example.stocksignal.data.translation

import android.util.Log
import com.google.ai.edge.litertlm.Backend
import com.google.ai.edge.litertlm.Content
import com.google.ai.edge.litertlm.Conversation
import com.google.ai.edge.litertlm.ConversationConfig
import com.google.ai.edge.litertlm.Engine
import com.google.ai.edge.litertlm.EngineConfig
import com.google.ai.edge.litertlm.Message
import com.google.ai.edge.litertlm.MessageCallback
import com.google.ai.edge.litertlm.SamplerConfig
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

enum class LlmBackend {
    CPU,
    GPU
}

data class LocalLlmRuntimeConfig(
    val modelPath: String,
    val backend: LlmBackend,
    val maxTokens: Int,
    val cacheDir: String? = null
)

data class LlmSamplingConfig(
    val topK: Int,
    val topP: Double,
    val temperature: Double
)

interface LocalLlmRuntime {
    suspend fun generate(prompt: String, sampling: LlmSamplingConfig): String
    fun close()
}

interface LocalLlmRuntimeFactory {
    fun create(config: LocalLlmRuntimeConfig): LocalLlmRuntime
}

class LiteRtLlmRuntimeFactory @Inject constructor() : LocalLlmRuntimeFactory {
    override fun create(config: LocalLlmRuntimeConfig): LocalLlmRuntime {
        return LiteRtLlmRuntime(config)
    }
}

private class LiteRtLlmRuntime(
    private val config: LocalLlmRuntimeConfig
) : LocalLlmRuntime {
    private val engine: Engine
    private var conversation: Conversation? = null
    private val lock = Mutex()

    init {
        val engineConfig = EngineConfig(
            modelPath = config.modelPath,
            backend = when (config.backend) {
                LlmBackend.CPU -> Backend.CPU
                LlmBackend.GPU -> Backend.GPU
            },
            maxNumTokens = config.maxTokens,
            cacheDir = config.cacheDir
        )
        engine = Engine(engineConfig)
        engine.initialize()
    }

    override suspend fun generate(prompt: String, sampling: LlmSamplingConfig): String {
        return lock.withLock {
            val activeConversation = resetConversation(sampling)
            val output = StringBuilder()
            suspendCancellableCoroutine { cont ->
                val completed = AtomicBoolean(false)
                activeConversation.sendMessageAsync(
                    Message.of(listOf(Content.Text(prompt))),
                    object : MessageCallback {
                        override fun onMessage(message: Message) {
                            synchronized(output) {
                                output.append(message.toString())
                            }
                        }

                        override fun onDone() {
                            if (completed.compareAndSet(false, true)) {
                                cont.resume(output.toString())
                            }
                        }

                        override fun onError(throwable: Throwable) {
                            if (completed.compareAndSet(false, true)) {
                                Log.e(TAG, "LiteRT-LM inference error", throwable)
                                cont.resumeWithException(throwable)
                            }
                        }
                    }
                )
            }
        }
    }

    override fun close() {
        try {
            conversation?.close()
        } catch (e: Exception) {
            Log.w(TAG, "Failed to close LiteRT-LM conversation.", e)
        } finally {
            conversation = null
        }
        try {
            engine.close()
        } catch (e: Exception) {
            Log.w(TAG, "Failed to close LiteRT-LM engine.", e)
        }
    }

    private fun resetConversation(sampling: LlmSamplingConfig): Conversation {
        conversation?.close()
        val samplerConfig = SamplerConfig(
            topK = sampling.topK,
            topP = sampling.topP,
            temperature = sampling.temperature
        )
        conversation = engine.createConversation(ConversationConfig(samplerConfig = samplerConfig))
        return conversation!!
    }

    companion object {
        private const val TAG = "LiteRtLlmRuntime"
    }
}
