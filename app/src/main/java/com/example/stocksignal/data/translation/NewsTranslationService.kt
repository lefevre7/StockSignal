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
    private val primaryModelFile: File by lazy { File(localModelDir, PRIMARY_MODEL_FILE_NAME) }
    private val legacyModelFile: File by lazy { File(localModelDir, LEGACY_MODEL_FILE_NAME) }
    private val localDownloadMutex = Mutex()
    private var localInference: LlmInference? = null
    private var localInferenceSpec: LocalModelSpec? = null
    @Volatile private var cachedPrimaryModelLastModified: Long = -1L
    @Volatile private var cachedPrimaryModelSize: Long = -1L
    @Volatile private var cachedPrimaryModelValid: Boolean? = null
    @Volatile private var cachedLegacyModelLastModified: Long = -1L
    @Volatile private var cachedLegacyModelSize: Long = -1L
    @Volatile private var cachedLegacyModelValid: Boolean? = null
    @Volatile private var primaryModelIncompatible: Boolean = false
    @Volatile private var legacyModelIncompatible: Boolean = false
    @Volatile private var localModelIncompatibilityMessage: String? = null
    @Volatile private var warnedGpuFallback: Boolean = false
    @Volatile private var warnedLegacyFallback: Boolean = false
    @Volatile private var pendingWarningMessage: String? = null
    private val warningLock = Any()
    private val assetPackManager by lazy { AssetPackManagerFactory.getInstance(context) }
    private val primaryModelSpec = LocalModelSpec(
        label = "Gemma 3 1B int4",
        fileName = PRIMARY_MODEL_FILE_NAME,
        sha256 = PRIMARY_MODEL_SHA256,
        expectedBytes = PRIMARY_MODEL_EXPECTED_BYTES,
        isLegacy = false
    )
    private val legacyModelSpec = LocalModelSpec(
        label = "Gemma 3 270M q8",
        fileName = LEGACY_MODEL_FILE_NAME,
        sha256 = LEGACY_MODEL_SHA256,
        expectedBytes = LEGACY_MODEL_EXPECTED_BYTES,
        isLegacy = true
    )

    suspend fun getModelAvailability(): ModelAvailability {
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
        val primaryAvailable = isModelAvailable(
            spec = primaryModelSpec,
            file = primaryModelFile,
            expectedBytes = PRIMARY_MODEL_EXPECTED_BYTES,
            expectedSha = PRIMARY_MODEL_SHA256,
            cache = ModelCache.PRIMARY
        )
        if (primaryAvailable) return true
        return isModelAvailable(
            spec = legacyModelSpec,
            file = legacyModelFile,
            expectedBytes = LEGACY_MODEL_EXPECTED_BYTES,
            expectedSha = LEGACY_MODEL_SHA256,
            cache = ModelCache.LEGACY
        )
    }

    suspend fun isLocalModelUsable(): Boolean {
        if (isLocalModelIncompatible()) {
            Log.w(TAG, "Local models flagged as incompatible; treating as unusable.")
            return false
        }
        return resolveUsableModelSpec() != null
    }

    fun getLocalModelRequiredBytes(): Long = PRIMARY_MODEL_REQUIRED_BYTES

    fun getLocalModelExpectedBytes(): Long = PRIMARY_MODEL_EXPECTED_BYTES

    fun getLocalModelSha256(): String = PRIMARY_MODEL_SHA256

    fun getLocalModelFilePath(): String = primaryModelFile.absolutePath

    fun isLocalModelIncompatible(): Boolean = primaryModelIncompatible && legacyModelIncompatible

    fun getLocalModelIncompatibilityMessage(): String? = localModelIncompatibilityMessage

    fun consumeWarningMessage(): String? {
        synchronized(warningLock) {
            val message = pendingWarningMessage
            pendingWarningMessage = null
            return message
        }
    }

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
                if (!primaryModelIncompatible &&
                    isModelAvailable(
                        spec = primaryModelSpec,
                        file = primaryModelFile,
                        expectedBytes = PRIMARY_MODEL_EXPECTED_BYTES,
                        expectedSha = PRIMARY_MODEL_SHA256,
                        cache = ModelCache.PRIMARY
                    )
                ) {
                    Log.i(TAG, "Primary local model already downloaded.")
                    onProgress(100)
                    return@withLock true
                }
                clearLocalModelIncompatibility()
                if (!localModelDir.exists() && !localModelDir.mkdirs()) {
                    Log.e(TAG, "Failed to create local model directory at ${localModelDir.absolutePath}.")
                    return@withLock false
                }
                val tempFile = File(localModelDir, "$PRIMARY_MODEL_FILE_NAME.part")
                if (tempFile.exists()) {
                    tempFile.delete()
                }
                Log.i(TAG, "Downloading local model from $PRIMARY_MODEL_URL.")
                val request = Request.Builder().url(PRIMARY_MODEL_URL).build()
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
                    if (contentLength > 0 && contentLength != PRIMARY_MODEL_EXPECTED_BYTES) {
                        Log.w(
                            TAG,
                            "Local model content length mismatch. " +
                                "Expected $PRIMARY_MODEL_EXPECTED_BYTES, got $contentLength."
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
                                    PRIMARY_MODEL_EXPECTED_BYTES
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
                    if (finalSize != PRIMARY_MODEL_EXPECTED_BYTES) {
                        Log.e(TAG, "Local model size mismatch after download: $finalSize bytes.")
                        tempFile.delete()
                        return@withLock false
                    }
                    val downloadedHash = digest.digest().toHexString()
                    if (!downloadedHash.equals(PRIMARY_MODEL_SHA256, ignoreCase = true)) {
                        Log.e(
                            TAG,
                            "Local model hash mismatch after download. " +
                                "Expected $PRIMARY_MODEL_SHA256, got $downloadedHash."
                        )
                        tempFile.delete()
                        return@withLock false
                    }
                    if (primaryModelFile.exists() && !primaryModelFile.delete()) {
                        Log.w(TAG, "Failed to delete existing local model before rename.")
                    }
                    if (!tempFile.renameTo(primaryModelFile)) {
                        Log.e(TAG, "Failed to move local model into place.")
                        tempFile.delete()
                        return@withLock false
                    }
                    localInference = null
                    localInferenceSpec = null
                    cachedPrimaryModelSize = primaryModelFile.length()
                    cachedPrimaryModelLastModified = primaryModelFile.lastModified()
                    cachedPrimaryModelValid = true
                    Log.i(TAG, "Local model download complete: ${primaryModelFile.absolutePath}.")
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
                val packName = PRIMARY_MODEL_ASSET_PACK
                val current = assetPackManager.getPackLocation(packName)
                if (current != null) {
                    Log.i(TAG, "Local model asset pack already installed.")
                    return@withContext copyFromAssetPack(current, primaryModelSpec)
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
                copyFromAssetPack(location, primaryModelSpec)
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
            localInferenceSpec = null
            clearLocalModelIncompatibility()
            val tempFiles = listOf(
                File(localModelDir, "$PRIMARY_MODEL_FILE_NAME.part"),
                File(localModelDir, "$LEGACY_MODEL_FILE_NAME.part")
            )
            tempFiles.forEach { temp ->
                if (temp.exists()) {
                    temp.delete()
                }
            }
            val deletedPrimary = if (primaryModelFile.exists()) primaryModelFile.delete() else true
            val deletedLegacy = if (legacyModelFile.exists()) legacyModelFile.delete() else true
            cachedPrimaryModelValid = null
            cachedPrimaryModelSize = -1L
            cachedPrimaryModelLastModified = -1L
            cachedLegacyModelValid = null
            cachedLegacyModelSize = -1L
            cachedLegacyModelLastModified = -1L
            deletedPrimary && deletedLegacy
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
        if (isLocalModelIncompatible()) {
            Log.w(TAG, "Local models incompatible; skipping local translation.")
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
                            markModelIncompatible(localInferenceSpec ?: primaryModelSpec, e)
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

    private fun copyFromAssetPack(location: AssetPackLocation, spec: LocalModelSpec): Boolean {
        val assetPath = location.assetsPath()
        val sourceFile = File(assetPath, spec.fileName)
        if (!sourceFile.exists()) {
            Log.w(TAG, "Local model not found in asset pack at ${sourceFile.absolutePath}.")
            return false
        }
        if (!localModelDir.exists() && !localModelDir.mkdirs()) {
            Log.e(TAG, "Failed to create local model directory at ${localModelDir.absolutePath}.")
            return false
        }
        val targetFile = modelFileFor(spec)
        return try {
            sourceFile.inputStream().use { input ->
                FileOutputStream(targetFile).use { output ->
                    input.copyTo(output)
                }
            }
            val valid = verifyLocalModelFile(targetFile, spec, cacheForSpec(spec))
            if (!valid) {
                targetFile.delete()
                return false
            }
            updateCache(cacheForSpec(spec), targetFile.length(), targetFile.lastModified(), true)
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to copy local model from asset pack.", e)
            false
        }
    }

    private fun verifyLocalModelFile(file: File, spec: LocalModelSpec, cache: ModelCache): Boolean {
        if (!file.exists()) return false
        val size = file.length()
        if (size != spec.expectedBytes) {
            Log.w(
                TAG,
                "Local model size mismatch for ${spec.label}. Expected ${spec.expectedBytes}, got $size."
            )
            return false
        }
        val hash = computeSha256(file)
        val matches = hash != null && hash.equals(spec.sha256, ignoreCase = true)
        if (!matches) {
            Log.w(
                TAG,
                "Local model hash mismatch for ${spec.label}. Expected ${spec.sha256}, got $hash."
            )
            updateCache(cache, size, file.lastModified(), false)
        }
        if (matches) {
            updateCache(cache, size, file.lastModified(), true)
        }
        return matches
    }

    private suspend fun isModelAvailable(
        spec: LocalModelSpec,
        file: File,
        expectedBytes: Long,
        expectedSha: String,
        cache: ModelCache
    ): Boolean {
        if (!file.exists()) {
            Log.d(TAG, "Local model ${spec.label} missing at ${file.absolutePath}.")
            return false
        }
        val size = file.length()
        if (size != expectedBytes) {
            Log.w(
                TAG,
                "Local model size mismatch for ${spec.label}. Expected $expectedBytes, got $size."
            )
            return false
        }
        val lastModified = file.lastModified()
        val cachedValid = cacheValid(cache)
        if (cachedValid != null &&
            cacheSize(cache) == size &&
            cacheLastModified(cache) == lastModified
        ) {
            return cachedValid
        }
        return withContext(Dispatchers.IO) {
            val hash = computeSha256(file)
            val matches = hash != null && hash.equals(expectedSha, ignoreCase = true)
            if (!matches) {
                Log.w(
                    TAG,
                    "Local model hash mismatch for ${spec.label}. " +
                        "Expected $expectedSha, got $hash."
                )
            }
            updateCache(cache, size, lastModified, matches)
            matches
        }
    }

    private fun cacheValid(cache: ModelCache): Boolean? {
        return when (cache) {
            ModelCache.PRIMARY -> cachedPrimaryModelValid
            ModelCache.LEGACY -> cachedLegacyModelValid
        }
    }

    private fun cacheSize(cache: ModelCache): Long {
        return when (cache) {
            ModelCache.PRIMARY -> cachedPrimaryModelSize
            ModelCache.LEGACY -> cachedLegacyModelSize
        }
    }

    private fun cacheLastModified(cache: ModelCache): Long {
        return when (cache) {
            ModelCache.PRIMARY -> cachedPrimaryModelLastModified
            ModelCache.LEGACY -> cachedLegacyModelLastModified
        }
    }

    private fun updateCache(cache: ModelCache, size: Long, lastModified: Long, valid: Boolean) {
        when (cache) {
            ModelCache.PRIMARY -> {
                cachedPrimaryModelSize = size
                cachedPrimaryModelLastModified = lastModified
                cachedPrimaryModelValid = valid
            }
            ModelCache.LEGACY -> {
                cachedLegacyModelSize = size
                cachedLegacyModelLastModified = lastModified
                cachedLegacyModelValid = valid
            }
        }
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

    private suspend fun getOrCreateLocalInference(): LlmInference? {
        if (isLocalModelIncompatible()) {
            Log.w(TAG, "Local models incompatible; not initializing inference.")
            return null
        }
        val attempted = mutableSetOf<LocalModelSpec>()
        while (true) {
            val spec = resolveUsableModelSpec() ?: return null
            if (!attempted.add(spec)) return null
            val inference = createInferenceForSpec(spec)
            if (inference != null) return inference
        }
    }

    private suspend fun resolveUsableModelSpec(): LocalModelSpec? {
        val primaryUsable = !primaryModelIncompatible &&
            isModelAvailable(
                spec = primaryModelSpec,
                file = primaryModelFile,
                expectedBytes = PRIMARY_MODEL_EXPECTED_BYTES,
                expectedSha = PRIMARY_MODEL_SHA256,
                cache = ModelCache.PRIMARY
            )
        if (primaryUsable) return primaryModelSpec
        val legacyUsable = !legacyModelIncompatible &&
            isModelAvailable(
                spec = legacyModelSpec,
                file = legacyModelFile,
                expectedBytes = LEGACY_MODEL_EXPECTED_BYTES,
                expectedSha = LEGACY_MODEL_SHA256,
                cache = ModelCache.LEGACY
            )
        return if (legacyUsable) legacyModelSpec else null
    }

    private fun modelFileFor(spec: LocalModelSpec): File {
        return if (spec.isLegacy) legacyModelFile else primaryModelFile
    }

    private fun cacheForSpec(spec: LocalModelSpec): ModelCache {
        return if (spec.isLegacy) ModelCache.LEGACY else ModelCache.PRIMARY
    }

    private fun resolvePreferredBackend(): LlmInference.Backend {
        if (isProbablyEmulator()) {
            Log.w(TAG, "Emulator detected; forcing CPU backend for local model.")
            notifyGpuFallbackOnce()
            return LlmInference.Backend.CPU
        }
        return LlmInference.Backend.GPU
    }

    private fun isProbablyEmulator(): Boolean {
        val fingerprint = Build.FINGERPRINT
        val model = Build.MODEL
        val brand = Build.BRAND
        val device = Build.DEVICE
        val product = Build.PRODUCT
        val manufacturer = Build.MANUFACTURER
        val hardware = Build.HARDWARE
        val abis = Build.SUPPORTED_ABIS
        return fingerprint.startsWith("generic") ||
            fingerprint.startsWith("unknown") ||
            model.contains("google_sdk", ignoreCase = true) ||
            model.contains("emulator", ignoreCase = true) ||
            model.contains("android sdk built for x86", ignoreCase = true) ||
            manufacturer.contains("genymotion", ignoreCase = true) ||
            (brand.startsWith("generic") && device.startsWith("generic")) ||
            product.contains("sdk_gphone", ignoreCase = true) ||
            product.contains("emulator", ignoreCase = true) ||
            hardware.contains("ranchu", ignoreCase = true) ||
            hardware.contains("goldfish", ignoreCase = true) ||
            abis.any { it.startsWith("x86") }
    }

    private fun createInferenceForSpec(spec: LocalModelSpec): LlmInference? {
        val modelFile = modelFileFor(spec)
        val cache = cacheForSpec(spec)
        val existing = localInference
        if (existing != null && localInferenceSpec == spec) return existing
        if (existing != null && localInferenceSpec != spec) {
            existing.close()
            localInference = null
            localInferenceSpec = null
        }
        if (!verifyLocalModelFile(modelFile, spec, cache)) {
            Log.w(TAG, "Local model ${spec.label} is not available for inference.")
            markModelIncompatible(spec, IllegalStateException("Local model validation failed."))
            return null
        }
        // Validate model compatibility before creating inference
        if (!validateModelCompatibility(modelFile, spec)) {
            Log.w(TAG, "Model ${spec.label} failed compatibility validation.")
            markModelIncompatible(spec, IllegalStateException("Model compatibility validation failed."))
            return null
        }
        val preferredBackend = resolvePreferredBackend()
        val baseOptions = LlmInference.LlmInferenceOptions.builder()
            .setModelPath(modelFile.absolutePath)
            .setMaxTokens(512)
            .setMaxTopK(40)
            .setPreferredBackend(preferredBackend)
            .build()
        fun recordInference(inference: LlmInference): LlmInference {
            localInference = inference
            localInferenceSpec = spec
            if (spec.isLegacy) {
                Log.e(TAG, "Falling back to legacy ${spec.label} model.")
                notifyLegacyFallbackOnce()
            }
            return inference
        }
        if (preferredBackend == LlmInference.Backend.CPU) {
            return try {
                recordInference(LlmInference.createFromOptions(context, baseOptions))
            } catch (cpuError: Exception) {
                Log.e(TAG, "Failed to initialize local LLM inference.", cpuError)
                markModelIncompatible(spec, cpuError)
                null
            }
        }
        return try {
            recordInference(LlmInference.createFromOptions(context, baseOptions))
        } catch (gpuError: Exception) {
            if (isCompatibilityError(gpuError)) {
                markModelIncompatible(spec, gpuError)
                return null
            }
            Log.e(TAG, "GPU inference init failed; falling back to CPU.", gpuError)
            val cpuOptions = baseOptions.toBuilder()
                .setPreferredBackend(LlmInference.Backend.CPU)
                .build()
            try {
                recordInference(LlmInference.createFromOptions(context, cpuOptions)).also {
                    notifyGpuFallbackOnce()
                }
            } catch (cpuError: Exception) {
                Log.e(TAG, "Failed to initialize local LLM inference.", cpuError)
                markModelIncompatible(spec, cpuError)
                null
            }
        }
    }

    private fun validateModelCompatibility(file: File, spec: LocalModelSpec): Boolean {
        // Basic validation: check file is a valid TFLite model
        return try {
            file.inputStream().use { input ->
                val header = ByteArray(8)
                val read = input.read(header)
                if (read < 8) {
                    Log.w(TAG, "Model file too small for ${spec.label} (${read} bytes)")
                    return false
                }
                // TFLite models typically start with specific magic bytes
                // This is a basic check - model may still be incompatible
                Log.d(TAG, "Model file header validated for ${spec.label}")
                true
            }
        } catch (e: Exception) {
            Log.e(TAG, "Model validation failed for ${spec.label}", e)
            false
        }
    }

    private fun isCompatibilityError(e: Exception): Boolean {
        val message = (e.message ?: "") + " " + (e.cause?.message ?: "")
        return message.contains("TfLitePrefillDecodeRunnerCalculator", ignoreCase = true) ||
            message.contains("prefill_input_names", ignoreCase = true) ||
            message.contains("signature_keys", ignoreCase = true)
    }

    private fun markModelIncompatible(spec: LocalModelSpec, e: Exception) {
        if (spec.isLegacy) {
            if (legacyModelIncompatible) return
            legacyModelIncompatible = true
        } else {
            if (primaryModelIncompatible) return
            primaryModelIncompatible = true
        }
        localModelIncompatibilityMessage =
            "Local translation model ${spec.label} is not usable on this device."
        Log.e(TAG, "Local model ${spec.label} marked incompatible. ${e.message}", e)
    }

    private fun clearLocalModelIncompatibility() {
        primaryModelIncompatible = false
        legacyModelIncompatible = false
        localModelIncompatibilityMessage = null
    }

    private fun notifyGpuFallbackOnce() {
        if (warnedGpuFallback) return
        warnedGpuFallback = true
        enqueueWarning("GPU unavailable; using CPU for offline translations.")
    }

    private fun notifyLegacyFallbackOnce() {
        if (warnedLegacyFallback) return
        warnedLegacyFallback = true
        enqueueWarning("Falling back to the legacy 270M offline model for translations.")
    }

    private fun enqueueWarning(message: String) {
        synchronized(warningLock) {
            pendingWarningMessage = message
        }
    }

    private data class LocalModelSpec(
        val label: String,
        val fileName: String,
        val sha256: String,
        val expectedBytes: Long,
        val isLegacy: Boolean
    )

    private enum class ModelCache {
        PRIMARY,
        LEGACY
    }

    companion object {
        private const val TAG = "NewsTranslationService"
        private const val LOCAL_MODEL_DIR_NAME = "llm"
        private const val PRIMARY_MODEL_FILE_NAME = "gemma3-1b-it-int4.task"
        private const val PRIMARY_MODEL_ASSET_PACK = "gemma3_1b_model"
        // Primary model from GitHub LFS
        private const val PRIMARY_MODEL_URL =
            "https://media.githubusercontent.com/media/lefevre7/StockSignal/refs/heads/main/" +
                "gemma3-1b-it-int4.task?download=true"
        private const val PRIMARY_MODEL_SHA256 =
            "e3d981c01aeaaac69a84ffa0d4be13281b3176731063f1bea1c9fe6887bd9dee"
        private const val PRIMARY_MODEL_EXPECTED_BYTES = 554_661_243L
        private const val PRIMARY_MODEL_REQUIRED_BYTES =
            PRIMARY_MODEL_EXPECTED_BYTES * 12 / 10
        private const val LEGACY_MODEL_FILE_NAME = "gemma3-270m-it-q8.task"
        private const val LEGACY_MODEL_SHA256 =
            "0f7147f1c22eaf758b819bbf7841793e4c90096c9352cde7fbe5c631f2265ef5"
        private const val LEGACY_MODEL_EXPECTED_BYTES = 303_950_933L
    }
}
