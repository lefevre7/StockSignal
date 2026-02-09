package com.example.stocksignal.data.translation

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import android.os.storage.StorageManager
import android.util.Log
import com.google.android.play.core.assetpacks.AssetPackLocation
import com.google.android.play.core.assetpacks.AssetPackManagerFactory
import com.google.android.play.core.assetpacks.AssetPackStateUpdateListener
import com.google.android.play.core.assetpacks.model.AssetPackStatus
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import kotlin.coroutines.resume

@Singleton
class NewsTranslationService @Inject constructor(
    @ApplicationContext private val context: Context,
    private val runtimeFactory: LocalLlmRuntimeFactory
) {
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
    private val localRuntimeLock = Mutex()
    private val localInferenceMutex = Mutex()
    private var localRuntime: LocalLlmRuntime? = null
    private var localRuntimeSpec: LocalModelSpec? = null
    @Volatile private var cachedPrimaryModelLastModified: Long = -1L
    @Volatile private var cachedPrimaryModelSize: Long = -1L
    @Volatile private var cachedPrimaryModelValid: Boolean? = null
    @Volatile private var cachedPrimaryModelHash: String? = null
    @Volatile private var cachedLegacyModelLastModified: Long = -1L
    @Volatile private var cachedLegacyModelSize: Long = -1L
    @Volatile private var cachedLegacyModelValid: Boolean? = null
    @Volatile private var cachedLegacyModelHash: String? = null
    @Volatile private var primaryModelIncompatible: Boolean = false
    @Volatile private var legacyModelIncompatible: Boolean = false
    @Volatile private var localModelIncompatibilityMessage: String? = null
    @Volatile private var cachedOpenClAvailable: Boolean? = null
    @Volatile private var warnedGpuFallback: Boolean = false
    @Volatile private var warnedLegacyFallback: Boolean = false
    @Volatile private var pendingWarningMessage: String? = null
    @Volatile private var loggedModelPresence: Boolean = false
    private val warningLock = Any()
    private val modelPresenceLock = Any()
    private val assetPackManager by lazy { AssetPackManagerFactory.getInstance(context) }
    private val primaryModelSpec = LocalModelSpec(
        label = "Gemma 3 1B int4",
        fileName = PRIMARY_MODEL_FILE_NAME,
        isLegacy = false
    )
    private val legacyModelSpec = LocalModelSpec(
        label = "Gemma 3 270M q8",
        fileName = LEGACY_MODEL_FILE_NAME,
        isLegacy = true
    )

    suspend fun isLocalModelAvailable(): Boolean {
        val primaryAvailable = isModelAvailable(
            spec = primaryModelSpec,
            file = primaryModelFile,
            cache = ModelCache.PRIMARY
        )
        if (primaryAvailable) return true
        return isModelAvailable(
            spec = legacyModelSpec,
            file = legacyModelFile,
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

    fun getLocalModelRequiredBytes(): Long {
        val expected = getLocalModelExpectedBytes()
        return (expected * 12 / 10).coerceAtLeast(0L)
    }

    fun getLocalModelExpectedBytes(): Long {
        val cached = cacheSize(ModelCache.PRIMARY)
        if (cached > 0) return cached
        val size = primaryModelFile.takeIf { it.exists() }?.length() ?: -1L
        return if (size > 0) size else PRIMARY_MODEL_ESTIMATED_BYTES
    }

    fun getLocalModelSha256(): String {
        cacheHash(ModelCache.PRIMARY)?.let { return it }
        if (!primaryModelFile.exists()) return ""
        val hash = computeSha256(primaryModelFile) ?: return ""
        updateCache(
            cache = ModelCache.PRIMARY,
            size = primaryModelFile.length(),
            lastModified = primaryModelFile.lastModified(),
            valid = true,
            sha256 = hash
        )
        return hash
    }

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

    suspend fun downloadLocalModel(onProgress: (Int) -> Unit): Boolean {
        return withContext(Dispatchers.IO) {
            localDownloadMutex.withLock {
                if (!primaryModelIncompatible &&
                    isModelAvailable(
                        spec = primaryModelSpec,
                        file = primaryModelFile,
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
                val expectedBytes = primaryModelSpec.expectedBytes
                val expectedSha256 = primaryModelSpec.expectedSha256
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
                    if (contentLength > 0 && expectedBytes != null && contentLength != expectedBytes) {
                        Log.w(
                            TAG,
                            "Local model content length mismatch. " +
                                "Expected $expectedBytes, got $contentLength."
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
                                val totalBytes = when {
                                    contentLength > 0 -> contentLength
                                    expectedBytes != null -> expectedBytes
                                    else -> PRIMARY_MODEL_ESTIMATED_BYTES
                                }
                                val percent = if (totalBytes > 0) {
                                    ((totalRead * 100) / totalBytes).toInt().coerceIn(0, 100)
                                } else {
                                    0
                                }
                                if (percent != lastPercent) {
                                    onProgress(percent)
                                    lastPercent = percent
                                }
                            }
                            output.flush()
                        }
                    }
                    val finalSize = tempFile.length()
                    if (contentLength > 0 && finalSize != contentLength) {
                        Log.e(
                            TAG,
                            "Local model size mismatch after download. Expected $contentLength, got $finalSize."
                        )
                        tempFile.delete()
                        return@withLock false
                    }
                    val downloadedHash = digest.digest().toHexString()
                    if (expectedBytes != null && finalSize != expectedBytes) {
                        Log.e(
                            TAG,
                            "Local model size mismatch after download. Expected $expectedBytes, got $finalSize."
                        )
                        tempFile.delete()
                        return@withLock false
                    }
                    if (expectedSha256 != null &&
                        !downloadedHash.equals(expectedSha256, ignoreCase = true)
                    ) {
                        Log.e(
                            TAG,
                            "Local model hash mismatch after download. " +
                                "Expected $expectedSha256, got $downloadedHash."
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
                    localRuntime?.close()
                    localRuntime = null
                    localRuntimeSpec = null
                    updateCache(
                        cache = ModelCache.PRIMARY,
                        size = primaryModelFile.length(),
                        lastModified = primaryModelFile.lastModified(),
                        valid = true,
                        sha256 = downloadedHash
                    )
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
            localRuntime?.close()
            localRuntime = null
            localRuntimeSpec = null
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
            cachedPrimaryModelHash = null
            cachedLegacyModelValid = null
            cachedLegacyModelSize = -1L
            cachedLegacyModelLastModified = -1L
            cachedLegacyModelHash = null
            deletedPrimary && deletedLegacy
        } catch (e: Exception) {
            Log.e(TAG, "Failed to delete local translation model.", e)
            false
        }
    }

    suspend fun translateWithLocalModel(input: String): String? {
        if (isLocalModelIncompatible()) {
            Log.w(TAG, "Local models incompatible; skipping local translation.")
            return null
        }
        val prompt = buildTranslationPrompt(input)
        val response = generateLocalResponse(
            prompt = prompt,
            temperature = 0.2f,
            topK = 40,
            topP = DEFAULT_TOP_P
        )
        val parsed = response?.let { parseTranslationJson(it) }
        if (parsed != null) return parsed
        if (response.isNullOrBlank()) return null
        Log.w(TAG, "Translation response did not match JSON schema; retrying once.")
        val retryPrompt = buildTranslationRetryPrompt(input)
        val retryResponse = generateLocalResponse(
            prompt = retryPrompt,
            temperature = 0.2f,
            topK = 40,
            topP = DEFAULT_TOP_P
        )
        val retryParsed = retryResponse?.let { parseTranslationJson(it) }
        return retryParsed ?: retryResponse?.let { stripTurnTags(it).trim() }
    }

    suspend fun generateLocalResponse(
        prompt: String,
        temperature: Float,
        topK: Int,
        topP: Float = DEFAULT_TOP_P
    ): String? {
        if (isLocalModelIncompatible()) {
            Log.w(TAG, "Local models incompatible; skipping local generation.")
            return null
        }
        Log.d(
            TAG,
            "Local generation requested (promptChars=${prompt.length}, temp=$temperature, " +
                "topK=$topK, topP=$topP)."
        )
        
        // Token estimation based on observed data: ~1.8 chars per token for stock market descriptions.
        // Max tokens is 1024 total (input + output). Reserve 150 tokens for output.
        // Max input: 874 tokens * 1.8 chars/token = ~1573 chars. Use 1500 for safety.
        val maxInputChars = 1500
        val truncatedPrompt = if (prompt.length > maxInputChars) {
            Log.w(TAG, "Input too long (${prompt.length} chars), truncating to $maxInputChars chars.")
            prompt.take(maxInputChars - 50) + "... [truncated]"
        } else {
            prompt
        }
        
        return withContext(Dispatchers.Default) {
            logModelPresenceOnce()
            val runtime = getOrCreateLocalRuntime() ?: return@withContext null
            Log.d(TAG, "Local model runtime ready (model=${localRuntimeSpec?.label ?: "unknown"}).")
            val normalizedTopK = topK.coerceAtLeast(1).coerceAtMost(MAX_TOP_K)
            val normalizedTopP = topP.coerceIn(0.0f, 1.0f)
            val sampling = LlmSamplingConfig(
                topK = normalizedTopK,
                topP = normalizedTopP.toDouble(),
                temperature = temperature.toDouble()
            )
            localInferenceMutex.withLock {
                try {
                    val response = runtime.generate(truncatedPrompt, sampling)
                    if (response.isBlank()) {
                        Log.w(TAG, "Local model returned an empty response.")
                    } else {
                        Log.d(TAG, "Local model response chars=${response.length}.")
                        if (Log.isLoggable(TAG, Log.VERBOSE)) {
                            Log.v(TAG, "Local model response preview: ${response.take(200)}")
                        }
                    }
                    response.trim()
                } catch (e: IllegalArgumentException) {
                    Log.w(TAG, "Local model input validation failed: ${e.message}")
                    if (isCompatibilityError(e)) {
                        markModelIncompatible(localRuntimeSpec ?: primaryModelSpec, e)
                    }
                    null
                } catch (e: Exception) {
                    Log.e(TAG, "Local model generation failed.", e)
                    if (isCompatibilityError(e)) {
                        markModelIncompatible(localRuntimeSpec ?: primaryModelSpec, e)
                    }
                    null
                }
            }
        }
    }

    private fun buildTranslationPrompt(input: String): String {
        return buildString {
            appendLine("<user_turn>")
            appendLine("Translate the Polish text to English.")
            appendLine("Reply ONLY with one-line JSON exactly like:")
            appendLine("{\"englishTranslation\":\"<english>\"}")
            appendLine("No extra words.")
            appendLine("End with </model_turn>.")
            appendLine("Polish: $input")
            appendLine("</user_turn>")
            append("<model_turn>")
        }
    }

    private fun buildTranslationRetryPrompt(input: String): String {
        return buildString {
            appendLine("<user_turn>")
            appendLine("Your previous response was invalid JSON.")
            appendLine("Reply ONLY with one-line JSON exactly like:")
            appendLine("{\"englishTranslation\":\"<english>\"}")
            appendLine("No extra words.")
            appendLine("End with </model_turn>.")
            appendLine("Polish: $input")
            appendLine("</user_turn>")
            append("<model_turn>")
        }
    }

    private fun parseTranslationJson(raw: String): String? {
        val normalized = normalizeTranslationJson(raw) ?: return null
        return try {
            val json = JSONObject(normalized)
            val translation = json.optString("englishTranslation").trim()
            translation.ifBlank { null }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to parse translation JSON: ${e.message}")
            null
        }
    }

    private fun normalizeTranslationJson(raw: String): String? {
        val stripped = stripTurnTags(raw)
            .replace("```json", "", ignoreCase = true)
            .replace("```", "")
            .trim()
        val start = stripped.indexOf('{')
        val end = stripped.lastIndexOf('}')
        if (start == -1 || end <= start) return null
        return stripped.substring(start, end + 1)
            .replace("\u201C", "\"")
            .replace("\u201D", "\"")
            .replace("\u2019", "'")
    }

    private fun stripTurnTags(raw: String): String {
        return raw
            .replace("<user_turn>", "", ignoreCase = true)
            .replace("</user_turn>", "", ignoreCase = true)
            .replace("<model_turn>", "", ignoreCase = true)
            .replace("</model_turn>", "", ignoreCase = true)
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
            localRuntime?.close()
            localRuntime = null
            localRuntimeSpec = null
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to copy local model from asset pack.", e)
            false
        }
    }

    private fun verifyLocalModelFile(file: File, spec: LocalModelSpec, cache: ModelCache): Boolean {
        if (!file.exists()) return false
        val size = file.length()
        if (size <= 0L) {
            Log.w(TAG, "Local model ${spec.label} is empty (${file.absolutePath}).")
            updateCache(cache, size, file.lastModified(), false, null)
            return false
        }
        val expectedBytes = spec.expectedBytes
        if (expectedBytes != null && size != expectedBytes) {
            Log.w(
                TAG,
                "Local model size mismatch for ${spec.label}. Expected $expectedBytes, got $size."
            )
            updateCache(cache, size, file.lastModified(), false, null)
            return false
        }
        val hash = computeSha256(file)
        val expectedSha = spec.expectedSha256
        val matches = hash != null && (expectedSha?.let { hash.equals(it, ignoreCase = true) } ?: true)
        if (hash == null) {
            Log.w(TAG, "Local model hash unavailable for ${spec.label}.")
        }
        if (!matches && expectedSha != null) {
            Log.w(
                TAG,
                "Local model hash mismatch for ${spec.label}. Expected $expectedSha, got $hash."
            )
        }
        updateCache(cache, size, file.lastModified(), matches, hash)
        return matches
    }

    private suspend fun isModelAvailable(
        spec: LocalModelSpec,
        file: File,
        cache: ModelCache
    ): Boolean {
        if (!file.exists()) {
            Log.d(TAG, "Local model ${spec.label} missing at ${file.absolutePath}.")
            return false
        }
        val size = file.length()
        if (size <= 0L) {
            Log.w(TAG, "Local model ${spec.label} is empty (${file.absolutePath}).")
            return false
        }
        val expectedBytes = spec.expectedBytes
        if (expectedBytes != null && size != expectedBytes) {
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
            val expectedSha = spec.expectedSha256
            val matches = hash != null && (expectedSha?.let { hash.equals(it, ignoreCase = true) } ?: true)
            if (hash == null) {
                Log.w(TAG, "Local model hash unavailable for ${spec.label}.")
            }
            if (!matches && expectedSha != null) {
                Log.w(
                    TAG,
                    "Local model hash mismatch for ${spec.label}. " +
                        "Expected $expectedSha, got $hash."
                )
            }
            updateCache(cache, size, lastModified, matches, hash)
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

    private fun cacheHash(cache: ModelCache): String? {
        return when (cache) {
            ModelCache.PRIMARY -> cachedPrimaryModelHash
            ModelCache.LEGACY -> cachedLegacyModelHash
        }
    }

    private fun updateCache(
        cache: ModelCache,
        size: Long,
        lastModified: Long,
        valid: Boolean,
        sha256: String?
    ) {
        when (cache) {
            ModelCache.PRIMARY -> {
                cachedPrimaryModelSize = size
                cachedPrimaryModelLastModified = lastModified
                cachedPrimaryModelValid = valid
                cachedPrimaryModelHash = sha256
            }
            ModelCache.LEGACY -> {
                cachedLegacyModelSize = size
                cachedLegacyModelLastModified = lastModified
                cachedLegacyModelValid = valid
                cachedLegacyModelHash = sha256
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

    private suspend fun getOrCreateLocalRuntime(): LocalLlmRuntime? {
        if (isLocalModelIncompatible()) {
            Log.w(TAG, "Local models incompatible; not initializing runtime.")
            return null
        }
        return localRuntimeLock.withLock {
            val attempted = mutableSetOf<LocalModelSpec>()
            while (true) {
                val spec = resolveUsableModelSpec() ?: return@withLock null
                if (!attempted.add(spec)) return@withLock null
                val runtime = createRuntimeForSpec(spec)
                if (runtime != null) return@withLock runtime
            }
            null
        }
    }

    private suspend fun resolveUsableModelSpec(): LocalModelSpec? {
        val primaryUsable = !primaryModelIncompatible &&
            isModelAvailable(
                spec = primaryModelSpec,
                file = primaryModelFile,
                cache = ModelCache.PRIMARY
            )
        if (primaryUsable) return primaryModelSpec
        val legacyUsable = !legacyModelIncompatible &&
            isModelAvailable(
                spec = legacyModelSpec,
                file = legacyModelFile,
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

    private fun resolvePreferredBackend(): LlmBackend {
        if (isProbablyEmulator()) {
            Log.w(TAG, "Emulator detected; forcing CPU backend for local model.")
            notifyGpuFallbackOnce()
            return LlmBackend.CPU
        }
        if (!isOpenClAvailable()) {
            Log.w(TAG, "OpenCL not available; forcing CPU backend for local model.")
            notifyGpuFallbackOnce()
            return LlmBackend.CPU
        }
        return LlmBackend.GPU
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

    private fun isOpenClAvailable(): Boolean {
        cachedOpenClAvailable?.let { return it }
        val candidates = listOf(
            "/system/lib64/libOpenCL.so",
            "/system/lib/libOpenCL.so",
            "/vendor/lib64/libOpenCL.so",
            "/vendor/lib/libOpenCL.so"
        )
        val exists = candidates.any { path -> File(path).exists() }
        val loaded = if (exists) {
            runCatching {
                System.loadLibrary("OpenCL")
                true
            }.getOrElse { false }
        } else {
            false
        }
        cachedOpenClAvailable = loaded
        return loaded
    }

    private fun createRuntimeForSpec(spec: LocalModelSpec): LocalLlmRuntime? {
        val modelFile = modelFileFor(spec)
        val cache = cacheForSpec(spec)
        val existing = localRuntime
        if (existing != null && localRuntimeSpec == spec) return existing
        if (existing != null && localRuntimeSpec != spec) {
            existing.close()
            localRuntime = null
            localRuntimeSpec = null
        }
        if (!verifyLocalModelFile(modelFile, spec, cache)) {
            Log.w(TAG, "Local model ${spec.label} is not available for runtime.")
            markModelIncompatible(spec, IllegalStateException("Local model validation failed."))
            return null
        }
        val preferredBackend = resolvePreferredBackend()
        val baseConfig = LocalLlmRuntimeConfig(
            modelPath = modelFile.absolutePath,
            backend = preferredBackend,
            maxTokens = MAX_TOKENS,
            cacheDir = modelFile.absolutePath
                .takeIf { it.startsWith("/data/local/tmp") }
                ?.let { context.getExternalFilesDir(null)?.absolutePath }
        )
        fun recordRuntime(runtime: LocalLlmRuntime): LocalLlmRuntime {
            localRuntime = runtime
            localRuntimeSpec = spec
            if (spec.isLegacy) {
                Log.e(TAG, "Falling back to legacy ${spec.label} model.")
                notifyLegacyFallbackOnce()
            }
            return runtime
        }
        if (preferredBackend == LlmBackend.CPU) {
            return try {
                recordRuntime(runtimeFactory.create(baseConfig))
            } catch (cpuError: Exception) {
                Log.e(TAG, "Failed to initialize LiteRT-LM runtime.", cpuError)
                markModelIncompatible(spec, cpuError)
                null
            }
        }
        return try {
            recordRuntime(runtimeFactory.create(baseConfig))
        } catch (gpuError: Exception) {
            if (isCompatibilityError(gpuError)) {
                markModelIncompatible(spec, gpuError)
                return null
            }
            Log.e(TAG, "GPU runtime init failed; falling back to CPU.", gpuError)
            val cpuConfig = baseConfig.copy(backend = LlmBackend.CPU)
            try {
                recordRuntime(runtimeFactory.create(cpuConfig)).also {
                    notifyGpuFallbackOnce()
                }
            } catch (cpuError: Exception) {
                Log.e(TAG, "Failed to initialize LiteRT-LM runtime.", cpuError)
                markModelIncompatible(spec, cpuError)
                null
            }
        }
    }

    private fun isCompatibilityError(e: Exception): Boolean {
        val message = (e.message ?: "") + " " + (e.cause?.message ?: "")
        return message.contains("LiteRT", ignoreCase = true) ||
            message.contains("litertlm", ignoreCase = true) ||
            message.contains("model", ignoreCase = true) ||
            message.contains("signature", ignoreCase = true)
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
        localRuntime?.close()
        localRuntime = null
        localRuntimeSpec = null
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

    private fun logModelPresenceOnce() {
        if (loggedModelPresence) return
        synchronized(modelPresenceLock) {
            if (loggedModelPresence) return
            loggedModelPresence = true
            val primaryExists = primaryModelFile.exists()
            val legacyExists = legacyModelFile.exists()
            val primarySize = primaryModelFile.takeIf { primaryExists }?.length() ?: 0L
            val legacySize = legacyModelFile.takeIf { legacyExists }?.length() ?: 0L
            Log.i(
                TAG,
                "Local model presence: primaryExists=$primaryExists primaryBytes=$primarySize, " +
                    "legacyExists=$legacyExists legacyBytes=$legacySize."
            )
        }
    }

    private data class LocalModelSpec(
        val label: String,
        val fileName: String,
        val expectedSha256: String? = null,
        val expectedBytes: Long? = null,
        val isLegacy: Boolean
    )

    private enum class ModelCache {
        PRIMARY,
        LEGACY
    }

    companion object {
        private const val TAG = "NewsTranslationService"
        private const val LOCAL_MODEL_DIR_NAME = "llm"
        private const val PRIMARY_MODEL_FILE_NAME = "gemma3-1b-it-int4.litertlm"
        private const val PRIMARY_MODEL_ASSET_PACK = "gemma3_1b_model"
        // Primary model from GitHub LFS
        internal const val PRIMARY_MODEL_URL =
            "https://media.githubusercontent.com/media/lefevre7/StockSignal/refs/heads/main/" +
                "gemma3-1b-it-int4.litertlm?download=true"
        private const val PRIMARY_MODEL_ESTIMATED_BYTES = 600_000_000L
        private const val LEGACY_MODEL_FILE_NAME = "gemma3-270m-it-q8.litertlm"
        private const val MAX_TOKENS = 1024
        private const val MAX_TOP_K = 40
        private const val DEFAULT_TOP_P = 0.95f
    }
}
