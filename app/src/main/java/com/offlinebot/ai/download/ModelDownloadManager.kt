package com.offlinebot.ai.download

import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext

@Singleton
class ModelDownloadManager @Inject constructor() {
    private val _downloadStates = MutableStateFlow<Map<String, DownloadState>>(emptyMap())
    val downloadStates: StateFlow<Map<String, DownloadState>> = _downloadStates.asStateFlow()

    suspend fun download(
        modelId: String,
        url: String,
        destFile: File,
        expectedSha256: String? = null
    ): DownloadState = withContext(Dispatchers.IO) {
        try {
            _downloadStates.update(modelId, DownloadState.Downloading(0f, 0, 0))

            destFile.parentFile?.mkdirs()
            val tempFile = File(destFile.absolutePath + ".tmp")

            var connection: HttpURLConnection? = null
            try {
                // Manually follow redirects to handle HuggingFace → XetHub → AWS chain
                var currentUrl = url
                var redirectCount = 0
                val maxRedirects = 10

                while (redirectCount <= maxRedirects) {
                    connection = (URL(currentUrl).openConnection() as HttpURLConnection).apply {
                        connectTimeout = 30_000
                        readTimeout = 300_000
                        instanceFollowRedirects = false
                        setRequestProperty("User-Agent", "OfflineBot/1.0")
                    }

                    when (connection.responseCode) {
                        HttpURLConnection.HTTP_MOVED_PERM,
                        HttpURLConnection.HTTP_MOVED_TEMP,
                        HttpURLConnection.HTTP_SEE_OTHER,
                        307, 308 -> {
                            val newUrl = connection.getHeaderField("Location")
                            connection.disconnect()
                            if (newUrl == null || newUrl == currentUrl) {
                                val error = DownloadState.Error("Redirect loop detected")
                                _downloadStates.update(modelId, error)
                                return@withContext error
                            }
                            // Handle relative redirects
                            currentUrl = if (newUrl.startsWith("/")) {
                                val base = URL(url)
                                "${base.protocol}://${base.host}$newUrl"
                            } else {
                                newUrl
                            }
                            redirectCount++
                            continue
                        }
                        HttpURLConnection.HTTP_OK -> break
                        else -> {
                            val error = DownloadState.Error("Server returned ${connection.responseCode}")
                            _downloadStates.update(modelId, error)
                            connection.disconnect()
                            return@withContext error
                        }
                    }
                }

                if (redirectCount > maxRedirects) {
                    val error = DownloadState.Error("Too many redirects")
                    _downloadStates.update(modelId, error)
                    return@withContext error
                }

                val finalConn = connection ?: run {
                    val error = DownloadState.Error("Connection failed")
                    _downloadStates.update(modelId, error)
                    return@withContext error
                }

                val totalBytes = finalConn.contentLengthLong
                var bytesDownloaded = 0L

                finalConn.inputStream.use { input ->
                    FileOutputStream(tempFile).use { output ->
                        val buffer = ByteArray(8192)
                        var read = input.read(buffer)
                        while (read != -1) {
                            output.write(buffer, 0, read)
                            bytesDownloaded += read
                            val progress = if (totalBytes > 0) bytesDownloaded.toFloat() / totalBytes else -1f
                            _downloadStates.update(
                                modelId,
                                DownloadState.Downloading(progress, bytesDownloaded, totalBytes)
                            )
                            read = input.read(buffer)
                        }
                    }
                }
            } finally {
                try { connection?.disconnect() } catch (_: Exception) {}
            }

            // SHA-256 verification
            if (expectedSha256 != null) {
                _downloadStates.update(modelId, DownloadState.Verifying(0f))
                val actual = sha256(tempFile)
                if (!actual.equals(expectedSha256, ignoreCase = true)) {
                    tempFile.delete()
                    val error = DownloadState.Error("SHA-256 mismatch.\nExpected: ${expectedSha256.take(16)}...\nGot: ${actual.take(16)}...")
                    _downloadStates.update(modelId, error)
                    return@withContext error
                }
            }

            tempFile.renameTo(destFile)
            _downloadStates.update(modelId, DownloadState.Complete)
            DownloadState.Complete
        } catch (e: Exception) {
            val error = DownloadState.Error(e.message ?: "Download failed")
            _downloadStates.update(modelId, error)
            error
        }
    }

    fun getState(modelId: String): DownloadState =
        _downloadStates.value[modelId] ?: DownloadState.Idle

    private fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(8192)
            var read = input.read(buffer)
            while (read != -1) {
                digest.update(buffer, 0, read)
                read = input.read(buffer)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    private fun <K> MutableStateFlow<Map<K, DownloadState>>.update(key: K, state: DownloadState) {
        val current = this.value.toMutableMap()
        current[key] = state
        this.value = current
    }
}
