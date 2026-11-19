package com.example.studyappandroid.data.ai.local

import android.app.ActivityManager
import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import javax.inject.Inject
import javax.inject.Singleton

data class DeviceMemoryBudget(
    val availableBytes: Long,
    val totalBytes: Long,
    val isLowMemory: Boolean,
    val recommendedContextTokens: Int,
    val recommendedThreads: Int,
)

@Singleton
class LocalModelManager @Inject constructor(
    @param:ApplicationContext private val context: Context,
) {
    companion object {
        const val ASSET_MODEL_PATH = "models/qwen2.5-0.5b-instruct-q4_k_m.gguf"
        const val MODEL_FILE_NAME = "qwen2.5-0.5b-instruct-q4_k_m.gguf"
        const val MODEL_DISPLAY_NAME = "Qwen2.5-0.5B-Instruct (Q4_K_M)"
        const val MODEL_LICENSE_NAME = "Apache License 2.0"

        private const val MIN_REQUIRED_AVAIL_RAM_BYTES = 320L * 1024L * 1024L
        private const val HIGH_RAM_BYTES = 1400L * 1024L * 1024L
        private const val COPY_BUFFER_SIZE = 256 * 1024

        private val extractMutex = Mutex()
    }

    fun getMemoryBudget(): DeviceMemoryBudget {
        val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
        val info = ActivityManager.MemoryInfo()
        activityManager?.getMemoryInfo(info)

        val avail = info.availMem
        val total = info.totalMem
        val low = info.lowMemory || (avail < MIN_REQUIRED_AVAIL_RAM_BYTES)

        val ctx = if (avail >= HIGH_RAM_BYTES && !info.lowMemory) 1536 else 1024

        val cores = Runtime.getRuntime().availableProcessors().coerceAtLeast(2)
        val threads = (cores - 2).coerceIn(2, 4)

        return DeviceMemoryBudget(
            availableBytes = avail,
            totalBytes = total,
            isLowMemory = low,
            recommendedContextTokens = ctx,
            recommendedThreads = threads,
        )
    }

    fun getNativeLibDir(): String = context.applicationInfo.nativeLibraryDir

    fun getLocalModelFile(): File {
        val dir = File(context.filesDir, "models").apply { mkdirs() }
        return File(dir, MODEL_FILE_NAME)
    }

    suspend fun ensureModelFileExtracted(
        onProgress: ((Float) -> Unit)? = null,
    ): Result<File> = extractMutex.withLock {
        withContext(Dispatchers.IO) {
            runCatching {
                val targetFile = getLocalModelFile()
                val expectedLength = runCatching {
                    context.assets.openFd(ASSET_MODEL_PATH).use { it.length }
                }.getOrDefault(-1L)

                if (targetFile.exists() && targetFile.length() > 10_000_000L &&
                    (expectedLength <= 0L || targetFile.length() == expectedLength)
                ) {
                    onProgress?.invoke(1f)
                    return@runCatching targetFile
                }

                val requiredBytes = if (expectedLength > 0L) expectedLength else 500L * 1024L * 1024L
                val usableSpace = targetFile.parentFile?.usableSpace ?: context.filesDir.usableSpace
                if (usableSpace < requiredBytes + 32L * 1024L * 1024L) {
                    error("Insufficient internal storage to unpack offline AI model (~${requiredBytes / (1024 * 1024)} MB required).")
                }

                val tempFile = File(targetFile.parentFile, "$MODEL_FILE_NAME.tmp")
                if (tempFile.exists()) tempFile.delete()

                context.assets.open(ASSET_MODEL_PATH).use { input ->
                    FileOutputStream(tempFile).use { output ->
                        val buffer = ByteArray(COPY_BUFFER_SIZE)
                        var copied = 0L
                        while (true) {
                            val read = input.read(buffer)
                            if (read <= 0) break
                            output.write(buffer, 0, read)
                            copied += read
                            if (expectedLength > 0L) {
                                onProgress?.invoke((copied.toFloat() / expectedLength.toFloat()).coerceIn(0f, 0.99f))
                            }
                        }
                        output.fd.sync()
                    }
                }

                if (targetFile.exists()) targetFile.delete()
                if (!tempFile.renameTo(targetFile)) {
                    tempFile.copyTo(targetFile, overwrite = true)
                    tempFile.delete()
                }
                if (!targetFile.exists() || targetFile.length() <= 10_000_000L) {
                    error("Failed to finalize offline AI model file.")
                }
                onProgress?.invoke(1f)
                targetFile
            }
        }
    }
}
