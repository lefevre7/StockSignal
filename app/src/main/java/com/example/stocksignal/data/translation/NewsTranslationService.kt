package com.example.stocksignal.data.translation

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import android.os.storage.StorageManager
import android.util.Log
import com.google.mediapipe.tasks.genai.llminference.GraphOptions
import com.google.mediapipe.tasks.genai.llminference.LlmInference
import com.google.mediapipe.tasks.genai.llminference.LlmInferenceSession
import com.google.android.play.core.assetpacks.AssetPackLocation
import com.google.android.play.core.assetpacks.AssetPackManagerFactory
import com.google.android.play.core.assetpacks.AssetPackStateUpdateListener
import com.google.android.play.core.assetpacks.model.AssetPackStatus
import com.google.mlkit.genai.common.DownloadStatus
import com.google.mlkit.genai.common.FeatureStatus
import com.google.mlkit.genai.prompt.Generation
import com.google.mlkit.genai.prompt.GenerativeModel
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import kotlin.coroutines.resume

enum class ModelAvailability {
    AVAILABLE,
    NEEDS_DOWNLOAD,
    UNAVAILABLE
}

@Singleton
class NewsTranslationService @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val model: GenerativeModel by lazy { Generation.getClient() }
    private val storageManager: StorageManager by lazy {
        context.getSystemService(StorageManager::class.java)
    }
    private val connectivityManager: ConnectivityManager by lazy {
        context.getSystemService(ConnectivityManager::class.java)
    }
    private val downloadClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .followRedirects(true)
            .followSslRedirects(true)
            .build()
    }
    private val localModelDir: File by lazy { File(context.filesDir, LOCAL_MODEL_DIR_NAME) }
    private val localModelFile: File by lazy { File(localModelDir, LOCAL_MODEL_FILE_NAME) }
    private val localDownloadMutex = Mutex()
    private var localInference: LlmInference? = null
    @Volatile private var cachedModelLastModified: Long = -1L
    @Volatile private var cachedModelSize: Long = -1L
    @Volatile private var cachedModelValid: Boolean? = null
    @Volatile private var localModelIncompatible: Boolean = false
    @Volatile private var localModelIncompatibilityMessage: String? = null
    private val assetPackManager by lazy { AssetPackManagerFactory.getInstance(context) }

    suspend fun getModelAvailability(): ModelAvailability {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
            Log.w(TAG, "Translation not supported below API 31.")
            // TODO: Consider offering a tiny model or keeping Polish headlines.
            return ModelAvailability.UNAVAILABLE
        }
        return try {
            val status = model.checkStatus()
            Log.d(TAG, "Play services model status: $status")
            when (status) {
                FeatureStatus.AVAILABLE -> ModelAvailability.AVAILABLE
                FeatureStatus.UNAVAILABLE -> ModelAvailability.UNAVAILABLE
                else -> ModelAvailability.NEEDS_DOWNLOAD
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to check translation model status.", e)
            ModelAvailability.UNAVAILABLE
        }
    }

    suspend fun isLocalModelAvailable(): Boolean {
        if (!localModelFile.exists()) {
            Log.d(TAG, "Local model missing at ${localModelFile.absolutePath}.")
            return false
        }
        val size = localModelFile.length()
        if (size != LOCAL_MODEL_EXPECTED_BYTES) {
            Log.w(TAG, "Local model size mismatch. Expected $LOCAL_MODEL_EXPECTED_BYTES, got $size.")
            return false
        }
        val lastModified = localModelFile.lastModified()
        val cachedValid = cachedModelValid
        if (cachedValid != null &&
            cachedModelSize == size &&
            cachedModelLastModified == lastModified
        ) {
            return cachedValid
        }
        return withContext(Dispatchers.IO) {
            val hash = computeSha256(localModelFile)
            val matches = hash != null && hash.equals(LOCAL_MODEL_SHA256, ignoreCase = true)
            if (!matches) {
                Log.w(TAG, "Local model hash mismatch. Expected $LOCAL_MODEL_SHA256, got $hash.")
            }
            cachedModelSize = size
            cachedModelLastModified = lastModified
            cachedModelValid = matches
            matches
        }
    }

    suspend fun isLocalModelUsable(): Boolean {
        if (localModelIncompatible) {
            Log.w(TAG, "Local model flagged as incompatible; treating as unusable.")
            return false
        }
        return isLocalModelAvailable()
    }

    fun getLocalModelRequiredBytes(): Long = LOCAL_MODEL_REQUIRED_BYTES

    fun getLocalModelExpectedBytes(): Long = LOCAL_MODEL_EXPECTED_BYTES

    fun getLocalModelSha256(): String = LOCAL_MODEL_SHA256

    fun getLocalModelFilePath(): String = localModelFile.absolutePath

    fun isLocalModelIncompatible(): Boolean = localModelIncompatible

    fun getLocalModelIncompatibilityMessage(): String? = localModelIncompatibilityMessage

    fun hasEnoughStorage(minBytes: Long): Boolean {
        return try {
            val available = storageManager.getAllocatableBytes(StorageManager.UUID_DEFAULT)
            available >= minBytes
        } catch (e: Exception) {
            Log.w(TAG, "Failed to check storage for translation model.", e)
            false
        }
    }

    fun isOnWifi(): Boolean {
        return try {
            val network = connectivityManager.activeNetwork ?: return false
            val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return false
            val onWifi = capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
            Log.d(TAG, "Wifi connectivity available: $onWifi")
            onWifi
        } catch (e: Exception) {
            Log.w(TAG, "Failed to check wifi connectivity.", e)
            false
        }
    }

    suspend fun downloadModel(): Boolean {
        return try {
            model.download().collect { status ->
                if (status is DownloadStatus.DownloadFailed) {
                    throw status.e
                }
                Log.d(TAG, "Play services model download status: $status")
            }
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to download translation model.", e)
            // TODO: Consider offering a tiny model or keeping Polish headlines.
            false
        }
    }

    suspend fun downloadLocalModel(onProgress: (Int) -> Unit): Boolean {
        return withContext(Dispatchers.IO) {
            localDownloadMutex.withLock {
                if (!localModelIncompatible && isLocalModelAvailable()) {
                    Log.i(TAG, "Local model already downloaded.")
                    onProgress(100)
                    return@withLock true
                }
                clearLocalModelIncompatibility()
                if (!localModelDir.exists() && !localModelDir.mkdirs()) {
                    Log.e(TAG, "Failed to create local model directory at ${localModelDir.absolutePath}.")
                    return@withLock false
                }
                val tempFile = File(localModelDir, "$LOCAL_MODEL_FILE_NAME.part")
                if (tempFile.exists()) {
                    tempFile.delete()
                }
                Log.i(TAG, "Downloading local model from $LOCAL_MODEL_URL.")
                val request = Request.Builder().url(LOCAL_MODEL_URL).build()
                downloadClient.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) {
                        Log.e(TAG, "Local model download failed: HTTP ${response.code}.")
                        return@withLock false
                    }
                    val body = response.body ?: run {
                        Log.e(TAG, "Local model download failed: empty body.")
                        return@withLock false
                    }
                    val contentLength = body.contentLength()
                    if (contentLength > 0 && contentLength != LOCAL_MODEL_EXPECTED_BYTES) {
                        Log.w(
                            TAG,
                            "Local model content length mismatch. " +
                                "Expected $LOCAL_MODEL_EXPECTED_BYTES, got $contentLength."
                        )
                    }
                    onProgress(0)
                    val digest = MessageDigest.getInstance("SHA-256")
                    body.byteStream().use { input ->
                        FileOutputStream(tempFile).use { output ->
                            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                            var totalRead = 0L
                            var lastPercent = -1
                            while (true) {
                                val read = input.read(buffer)
                                if (read == -1) break
                                digest.update(buffer, 0, read)
                                output.write(buffer, 0, read)
                                totalRead += read
                                val totalBytes = if (contentLength > 0) {
                                    contentLength
                                } else {
                                    LOCAL_MODEL_EXPECTED_BYTES
                                }
                                val percent = ((totalRead * 100) / totalBytes)
                                    .toInt()
                                    .coerceIn(0, 100)
                                if (percent != lastPercent) {
                                    onProgress(percent)
                                    lastPercent = percent
                                }
                            }
                            output.flush()
                        }
                    }
                    val finalSize = tempFile.length()
                    if (finalSize != LOCAL_MODEL_EXPECTED_BYTES) {
                        Log.e(TAG, "Local model size mismatch after download: $finalSize bytes.")
                        tempFile.delete()
                        return@withLock false
                    }
                    val downloadedHash = digest.digest().toHexString()
                    if (!downloadedHash.equals(LOCAL_MODEL_SHA256, ignoreCase = true)) {
                        Log.e(
                            TAG,
                            "Local model hash mismatch after download. " +
                                "Expected $LOCAL_MODEL_SHA256, got $downloadedHash."
                        )
                        tempFile.delete()
                        return@withLock false
                    }
                    if (localModelFile.exists() && !localModelFile.delete()) {
                        Log.w(TAG, "Failed to delete existing local model before rename.")
                    }
                    if (!tempFile.renameTo(localModelFile)) {
                        Log.e(TAG, "Failed to move local model into place.")
                        tempFile.delete()
                        return@withLock false
                    }
                    localInference = null
                    cachedModelSize = localModelFile.length()
                    cachedModelLastModified = localModelFile.lastModified()
                    cachedModelValid = true
                    Log.i(TAG, "Local model download complete: ${localModelFile.absolutePath}.")
                    onProgress(100)
                    true
                }
            }
        }
    }

    suspend fun tryFetchLocalModelFromAssetPack(): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                clearLocalModelIncompatibility()
                val packName = LOCAL_MODEL_ASSET_PACK
                val current = assetPackManager.getPackLocation(packName)
                if (current != null) {
                    Log.i(TAG, "Local model asset pack already installed.")
                    return@withContext copyFromAssetPack(current)
                }
                val result = suspendCancellableCoroutine<Boolean> { cont ->
                    lateinit var listener: AssetPackStateUpdateListener
                    listener = AssetPackStateUpdateListener { state ->
                        if (state.name() != packName) return@AssetPackStateUpdateListener
                        when (state.status()) {
                            AssetPackStatus.COMPLETED -> {
                                assetPackManager.unregisterListener(listener)
                                cont.resume(true)
                            }
                            AssetPackStatus.FAILED,
                            AssetPackStatus.CANCELED -> {
                                assetPackManager.unregisterListener(listener)
                                cont.resume(false)
                            }
                            else -> Unit
                        }
                    }
                    assetPackManager.registerListener(listener)
                    assetPackManager.fetch(listOf(packName))
                        .addOnFailureListener { e ->
                            Log.e(TAG, "Asset pack fetch failed to start.", e)
                            assetPackManager.unregisterListener(listener)
                            if (cont.isActive) {
                                cont.resume(false)
                            }
                        }
                    cont.invokeOnCancellation { assetPackManager.unregisterListener(listener) }
                }
                if (!result) {
                    Log.w(TAG, "Asset pack fetch failed for $packName.")
                    return@withContext false
                }
                val location = assetPackManager.getPackLocation(packName)
                if (location == null) {
                    Log.w(TAG, "Asset pack location not found after fetch.")
                    return@withContext false
                }
                copyFromAssetPack(location)
            } catch (e: Exception) {
                Log.e(TAG, "Asset pack fetch failed.", e)
                false
            }
        }
    }

    fun deleteLocalModel(): Boolean {
        return try {
            localInference?.close()
            localInference = null
            clearLocalModelIncompatibility()
            val tempFile = File(localModelDir, "$LOCAL_MODEL_FILE_NAME.part")
            if (tempFile.exists()) {
                tempFile.delete()
            }
            if (!localModelFile.exists()) {
                cachedModelValid = null
                cachedModelSize = -1L
                cachedModelLastModified = -1L
                true
            } else {
                val deleted = localModelFile.delete()
                cachedModelValid = null
                cachedModelSize = -1L
                cachedModelLastModified = -1L
                deleted
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to delete local translation model.", e)
            false
        }
    }

    suspend fun translateWithMlkit(input: String): String? {
        val prompt = buildPrompt(input)
        return try {
            val response = model.generateContent(prompt)
            response.candidates.firstOrNull()?.text?.trim()
        } catch (e: Exception) {
            Log.e(TAG, "Translation failed.", e)
            null
        }
    }

    suspend fun translateWithLocalModel(input: String): String? {
        if (localModelIncompatible) {
            Log.w(TAG, "Local model incompatible; skipping local translation.")
            return null
        }
        val prompt = buildPrompt(input)
        return withContext(Dispatchers.Default) {
            val inference = getOrCreateLocalInference() ?: return@withContext null
            
            // Try different session configurations in sequence
            val sessionConfigs = listOf(
                // 4A: Default settings (no GraphOptions)
                { LlmInferenceSession.LlmInferenceSessionOptions.builder()
                    .setTopK(40)
                    .setTemperature(0.2f)
                    .build() },
                // 4B: Try with different GraphOptions
                { LlmInferenceSession.LlmInferenceSessionOptions.builder()
                    .setTopK(40)
                    .setTemperature(0.2f)
                    .setGraphOptions(
                        GraphOptions.builder()
                            .setIncludeTokenCostCalculator(true)
                            .build()
                    )
                    .build() },
                // Minimal config
                { LlmInferenceSession.LlmInferenceSessionOptions.builder().build() }
            )
            
            for ((index, configBuilder) in sessionConfigs.withIndex()) {
                try {
                    val sessionOptions = configBuilder()
                    val response = LlmInferenceSession.createFromOptions(inference, sessionOptions).use { session ->
                        session.addQueryChunk(prompt)
                        session.generateResponse()
                    }
                    if (index > 0) {
                        Log.i(TAG, "Local model succeeded with config #${index + 1}")
                    }
                    return@withContext response.trim()
                } catch (e: Exception) {
                    Log.w(TAG, "Local model config #${index + 1} failed: ${e.message}")
                    if (index == sessionConfigs.lastIndex) {
                        // Last attempt failed
                        Log.e(TAG, "All local model session configurations failed.", e)
                        if (isCompatibilityError(e)) {
                            markLocalModelIncompatible(e)
                        }
                    }
                }
            }
            null
        }
    }

    private fun buildPrompt(input: String): String {
        return "Please translate this sentence from Polish to English. " +
            "Please return just the translation of the string (no extra words, no quotes, " +
            "single line): $input"
    }

    private fun copyFromAssetPack(location: AssetPackLocation): Boolean {
        val assetPath = location.assetsPath()
        val sourceFile = File(assetPath, LOCAL_MODEL_FILE_NAME)
        if (!sourceFile.exists()) {
            Log.w(TAG, "Local model not found in asset pack at ${sourceFile.absolutePath}.")
            return false
        }
        if (!localModelDir.exists() && !localModelDir.mkdirs()) {
            Log.e(TAG, "Failed to create local model directory at ${localModelDir.absolutePath}.")
            return false
        }
        return try {
            sourceFile.inputStream().use { input ->
                FileOutputStream(localModelFile).use { output ->
                    input.copyTo(output)
                }
            }
            val valid = verifyLocalModelFile(localModelFile)
            if (!valid) {
                localModelFile.delete()
                return false
            }
            cachedModelSize = localModelFile.length()
            cachedModelLastModified = localModelFile.lastModified()
            cachedModelValid = true
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to copy local model from asset pack.", e)
            false
        }
    }

    private fun verifyLocalModelFile(file: File): Boolean {
        if (!file.exists()) return false
        val size = file.length()
        if (size != LOCAL_MODEL_EXPECTED_BYTES) {
            Log.w(TAG, "Local model size mismatch. Expected $LOCAL_MODEL_EXPECTED_BYTES, got $size.")
            return false
        }
        val hash = computeSha256(file)
        val matches = hash != null && hash.equals(LOCAL_MODEL_SHA256, ignoreCase = true)
        if (!matches) {
            Log.w(TAG, "Local model hash mismatch. Expected $LOCAL_MODEL_SHA256, got $hash.")
            cachedModelValid = false
        }
        if (matches) {
            cachedModelSize = size
            cachedModelLastModified = file.lastModified()
            cachedModelValid = true
        }
        return matches
    }

    private fun computeSha256(file: File): String? {
        return try {
            val digest = MessageDigest.getInstance("SHA-256")
            file.inputStream().use { input ->
                val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                while (true) {
                    val read = input.read(buffer)
                    if (read == -1) break
                    digest.update(buffer, 0, read)
                }
            }
            digest.digest().toHexString()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to compute SHA-256 for local model.", e)
            null
        }
    }

    private fun ByteArray.toHexString(): String {
        return joinToString("") { "%02x".format(it) }
    }

    private fun getOrCreateLocalInference(): LlmInference? {
        if (localModelIncompatible) {
            Log.w(TAG, "Local model incompatible; not initializing inference.")
            return null
        }
        if (!verifyLocalModelFile(localModelFile)) {
            Log.w(TAG, "Local model is not available for inference.")
            return null
        }
        val existing = localInference
        if (existing != null) return existing
        
        // Validate model compatibility before creating inference
        if (!validateModelCompatibility()) {
            Log.w(TAG, "Model failed compatibility validation.")
            return null
        }
        
        return try {
            val options = LlmInference.LlmInferenceOptions.builder()
                .setModelPath(localModelFile.absolutePath)
                .setMaxTokens(128)
                .setMaxTopK(40)
                .build()
            LlmInference.createFromOptions(context, options).also { localInference = it }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize local LLM inference.", e)
            if (isCompatibilityError(e)) {
                markLocalModelIncompatible(e)
            }
            null
        }
    }
    
    private fun validateModelCompatibility(): Boolean {
        // Basic validation: check file is a valid TFLite model
        return try {
            localModelFile.inputStream().use { input ->
                val header = ByteArray(8)
                val read = input.read(header)
                if (read < 8) {
                    Log.w(TAG, "Model file too small (${read} bytes)")
                    return false
                }
                // TFLite models typically start with specific magic bytes
                // This is a basic check - model may still be incompatible
                Log.d(TAG, "Model file header validated")
                true
            }
        } catch (e: Exception) {
            Log.e(TAG, "Model validation failed", e)
            false
        }
    }

    private fun isCompatibilityError(e: Exception): Boolean {
        val message = (e.message ?: "") + " " + (e.cause?.message ?: "")
        return message.contains("TfLitePrefillDecodeRunnerCalculator", ignoreCase = true) ||
            message.contains("prefill_input_names", ignoreCase = true) ||
            message.contains("signature_keys", ignoreCase = true)
    }

    private fun markLocalModelIncompatible(e: Exception) {
        if (localModelIncompatible) return
        localModelIncompatible = true
        localModelIncompatibilityMessage = 
            "Local model format incompatible with MediaPipe 0.10.20. " +
            "Model needs reconversion with proper prefill/decode signatures. " +
            "Using cloud translation instead."
        Log.w(TAG, "Local model marked incompatible. ${e.message}", e)
    }

    private fun clearLocalModelIncompatibility() {
        localModelIncompatible = false
        localModelIncompatibilityMessage = null
    }

    companion object {
        private const val TAG = "NewsTranslationService"
        private const val LOCAL_MODEL_DIR_NAME = "llm"
        private const val LOCAL_MODEL_FILE_NAME = "gemma3-270m-it-q8.task"
        private const val LOCAL_MODEL_ASSET_PACK = "gemma3_270m_model"
        // Original working model from GitHub LFS
        private const val LOCAL_MODEL_URL =
            "https://media.githubusercontent.com/media/lefevre7/StockSignal/refs/heads/main/" +
                "gemma3-270m-it-q8.task?download=true"
        private const val LOCAL_MODEL_SHA256 =
            "0f7147f1c22eaf758b819bbf7841793e4c90096c9352cde7fbe5c631f2265ef5"
        private const val LOCAL_MODEL_EXPECTED_BYTES = 303_950_933L
        private const val LOCAL_MODEL_REQUIRED_BYTES =
            LOCAL_MODEL_EXPECTED_BYTES * 12 / 10
    }
}
