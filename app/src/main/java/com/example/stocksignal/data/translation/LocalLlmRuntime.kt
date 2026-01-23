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
        Log.i(
            TAG,
            "Initializing LiteRT-LM engine (backend=${config.backend}, maxTokens=${config.maxTokens})."
        )
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
        Log.i(TAG, "LiteRT-LM engine initialized for ${config.modelPath}.")
    }

    override suspend fun generate(prompt: String, sampling: LlmSamplingConfig): String {
        return lock.withLock {
            Log.d(
                TAG,
                "Starting generation (promptChars=${prompt.length}, topK=${sampling.topK}, " +
                    "topP=${sampling.topP}, temp=${sampling.temperature}, backend=${config.backend})."
            )
            val startNs = System.nanoTime()
            val activeConversation = resetConversation(sampling)
            val output = StringBuilder()
            var lastChunk = ""
            var messageCount = 0
            suspendCancellableCoroutine { cont ->
                val completed = AtomicBoolean(false)
                cont.invokeOnCancellation {
                    try {
                        activeConversation.cancelProcess()
                    } catch (e: Exception) {
                        Log.w(TAG, "Failed to cancel LiteRT-LM conversation.", e)
                    }
                }
                activeConversation.sendMessageAsync(
                    Message.of(prompt),
                    object : MessageCallback {
                        override fun onMessage(message: Message) {
                            messageCount += 1
                            val chunk = extractText(message)
                            if (chunk.isBlank()) {
                                Log.d(
                                    TAG,
                                    "LiteRT-LM message #$messageCount had no text content."
                                )
                                return
                            }
                            val appended = if (chunk.startsWith(lastChunk) && chunk.length > lastChunk.length) {
                                chunk.substring(lastChunk.length)
                            } else {
                                chunk
                            }
                            lastChunk = chunk
                            synchronized(output) {
                                output.append(appended)
                            }
                            if (Log.isLoggable(TAG, Log.VERBOSE)) {
                                Log.v(
                                    TAG,
                                    "LiteRT-LM chunk #$messageCount " +
                                        "(chunkChars=${chunk.length}, appendedChars=${appended.length})"
                                )
                            }
                        }

                        override fun onDone() {
                            if (completed.compareAndSet(false, true)) {
                                val elapsedMs = (System.nanoTime() - startNs) / 1_000_000
                                Log.d(
                                    TAG,
                                    "LiteRT-LM generation done (messages=$messageCount, " +
                                        "outputChars=${output.length}, durationMs=$elapsedMs)."
                                )
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
        }.let { response ->
            if (response.isBlank()) {
                Log.w(TAG, "Streaming response empty; retrying with synchronous sendMessage.")
                return@let runCatching {
                    val fallbackConversation = resetConversation(sampling)
                    val message = fallbackConversation.sendMessage(Message.of(prompt))
                    val fallback = extractText(message)
                    if (fallback.isBlank()) {
                        Log.w(TAG, "Synchronous LiteRT-LM response was empty.")
                    } else {
                        Log.d(TAG, "Synchronous LiteRT-LM response chars=${fallback.length}.")
                    }
                    fallback
                }.getOrElse { error ->
                    Log.e(TAG, "Synchronous LiteRT-LM fallback failed.", error)
                    response
                }
            }
            response
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

    private fun extractText(message: Message): String {
        val contents = message.contents
        if (contents.isEmpty()) return ""
        val text = StringBuilder()
        contents.forEach { content ->
            when (content) {
                is Content.Text -> text.append(content.text)
                else -> {
                    if (Log.isLoggable(TAG, Log.DEBUG)) {
                        Log.d(TAG, "Ignoring non-text content: ${content::class.java.simpleName}")
                    }
                }
            }
        }
        return text.toString()
    }

    companion object {
        private const val TAG = "LiteRtLlmRuntime"
    }
}
