package com.protoprojects.agrix.ai

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.FlowCollector
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

sealed class DownloadState {
    data class Progress(val bytesRead: Long, val totalBytes: Long) : DownloadState()
    data class Done(val path: String) : DownloadState()
    data class Error(val message: String) : DownloadState()
}

/**
 * Gemma model files are gated behind Google's license (Kaggle Models /
 * Hugging Face "litert-community" org) and are hundreds of MB to a few GB,
 * so they can't be bundled inside the APK or downloaded anonymously.
 *
 * We support two acquisition paths, both one-time and both fully offline
 * after they complete:
 *
 *  1. importLocalFile()  — user downloads the .task file once in their
 *     browser (after accepting the Gemma license) and picks it with the
 *     system file picker. No server involved on our side at all. This is
 *     the recommended path for Play Store distribution.
 *
 *  2. downloadFromUrl()  — cookie-authenticated download of the file
 *     Hugging Face serves after sign-in (see HFSignInScreen.kt).
 */
class ModelDownloadManager(private val context: Context) {

    fun modelsDir(): File = File(context.filesDir, "models").apply { mkdirs() }

    /** Copies a user-picked .task file (via ACTION_OPEN_DOCUMENT) into app-private storage. */
    fun importLocalFile(uri: Uri, fileName: String = HFModelSource.MODEL_FILE_NAME): Flow<DownloadState> = flow {
        val target = File(modelsDir(), fileName)
        val resolver = context.contentResolver
        val input = resolver.openInputStream(uri) ?: run {
            emit(DownloadState.Error("Could not open selected file"))
            return@flow
        }
        val totalBytes = resolver.openFileDescriptor(uri, "r")?.statSize ?: -1L
        input.use { inStream ->
            target.outputStream().use { outStream ->
                val buffer = ByteArray(1 shl 20) // 1MB chunks
                var read: Int
                var copied = 0L
                while (inStream.read(buffer).also { read = it } != -1) {
                    outStream.write(buffer, 0, read)
                    copied += read
                    emit(DownloadState.Progress(copied, totalBytes))
                }
            }
        }
        validateAndEmit(target)
    }.flowOn(Dispatchers.IO)

    /**
     * Streams a .task file from a URL, following redirects manually.
     *
     * This does NOT use `HttpURLConnection.instanceFollowRedirects = true`.
     * That flag only auto-follows same-host redirects reliably; more
     * importantly, when a redirect DOES cross hosts, `HttpURLConnection`
     * silently drops custom request headers (including our `Cookie` header)
     * on the follow-up request. Hugging Face serves large model files via
     * Git LFS, which almost always means `huggingface.co` responds to the
     * `resolve/main/...` URL with a redirect to a *separate* signed-URL CDN
     * host (e.g. `cdn-lfs.huggingface.co`). With auto-follow, the Cookie
     * header — which only huggingface.co understands anyway — gets lost on
     * that hop, the CDN request comes back as an auth error page, and that
     * HTML/JSON error page gets written to disk as if it were the model
     * file. MediaPipe then crashes trying to parse it as a model.
     *
     * Fixed by following redirects by hand (up to 5 hops), forwarding the
     * Cookie header only to same-host redirect targets — exactly what a
     * browser does — and validating what actually landed on disk before
     * ever reporting success.
     */
    fun downloadFromUrl(url: String, fileName: String = HFModelSource.MODEL_FILE_NAME, cookieHeader: String? = null): Flow<DownloadState> = flow {
        val target = File(modelsDir(), fileName)
        val tmp = File(modelsDir(), "$fileName.part")
        var connection: HttpURLConnection? = null
        try {
            var currentUrl = URL(url)
            var redirects = 0

            while (true) {
                connection = (currentUrl.openConnection() as HttpURLConnection).apply {
                    requestMethod = "GET"
                    connectTimeout = 15_000
                    readTimeout = 30_000
                    instanceFollowRedirects = false
                    if (!cookieHeader.isNullOrBlank() && currentUrl.host.endsWith("huggingface.co")) {
                        setRequestProperty("Cookie", cookieHeader)
                    }
                }
                connection.connect()

                val code = connection.responseCode
                if (code in 300..399) {
                    val location = connection.getHeaderField("Location")
                    connection.disconnect()
                    if (location.isNullOrBlank() || redirects >= 5) {
                        emit(DownloadState.Error("Too many redirects while downloading the model. The download link may have changed — see HFModelSource.kt."))
                        return@flow
                    }
                    currentUrl = URL(currentUrl, location)
                    redirects++
                    continue
                }
                break
            }

            val finalConnection = connection ?: run {
                emit(DownloadState.Error("Could not connect to download the model"))
                return@flow
            }

            if (finalConnection.responseCode !in 200..299) {
                emit(DownloadState.Error("Server returned HTTP ${finalConnection.responseCode}. The model file location may have changed, or sign-in may have expired — try signing in again."))
                return@flow
            }

            val totalBytes = finalConnection.contentLengthLong
            finalConnection.inputStream.use { inStream ->
                tmp.outputStream().use { outStream ->
                    val buffer = ByteArray(1 shl 20)
                    var read: Int
                    var copied = 0L
                    while (inStream.read(buffer).also { read = it } != -1) {
                        outStream.write(buffer, 0, read)
                        copied += read
                        emit(DownloadState.Progress(copied, totalBytes))
                    }
                }
            }

            if (!looksLikeModelFile(tmp)) {
                tmp.delete()
                emit(DownloadState.Error(
                    "The download didn't return a valid model file (likely an expired sign-in or an auth " +
                        "page instead of the actual file). Try signing in again."
                ))
                return@flow
            }

            tmp.renameTo(target)
            validateAndEmit(target)
        } catch (e: Exception) {
            emit(DownloadState.Error(e.message ?: "Download failed"))
        } finally {
            connection?.disconnect()
        }
    }.flowOn(Dispatchers.IO)

    /**
     * Sanity-checks a file before letting anything try to load it as a
     * model: big enough to plausibly be a quantized LLM (rules out a
     * kilobyte-sized HTML/JSON error page), and doesn't start with text
     * that looks like HTML/JSON (rules out an error page that happens to
     * be padded/truncated to a larger size). This is what stands between a
     * bad download and a native crash inside MediaPipe.
     */
    private fun looksLikeModelFile(file: File): Boolean {
        if (!file.exists() || file.length() < MIN_PLAUSIBLE_MODEL_BYTES) return false
        val head = ByteArray(32)
        val read = file.inputStream().use { it.read(head) }
        if (read <= 0) return false
        val prefix = String(head, 0, read, Charsets.US_ASCII).trimStart()
        return !(prefix.startsWith("<") || prefix.startsWith("{") || prefix.startsWith("HTTP/"))
    }

    private fun verifySha256(file: File, expectedHash: String): Boolean {
        return try {
            val digest = java.security.MessageDigest.getInstance("SHA-256")
            file.inputStream().use { input ->
                val buffer = ByteArray(64 * 1024)
                var read: Int
                while (input.read(buffer).also { read = it } != -1) {
                    digest.update(buffer, 0, read)
                }
            }
            val actual = digest.digest().joinToString("") { "%02x".format(it) }
            actual.equals(expectedHash, ignoreCase = true)
        } catch (e: Exception) {
            false
        }
    }

    private suspend fun FlowCollector<DownloadState>.validateAndEmit(file: File) {
        if (!looksLikeModelFile(file)) {
            file.delete()
            emit(DownloadState.Error("The downloaded file doesn't look like a valid model — try again."))
            return
        }
        val expectedSha = HFModelSource.MODEL_SHA256
        if (expectedSha.isNotBlank() && expectedSha != "REPLACE_WITH_ACTUAL_SHA256_AFTER_DOWNLOAD") {
            if (!verifySha256(file, expectedSha)) {
                file.delete()
                emit(DownloadState.Error("Model integrity verification failed (SHA-256 mismatch). File may be corrupted or truncated."))
                return
            }
        }
        emit(DownloadState.Done(file.absolutePath))
    }

    fun existingModelPath(fileName: String = HFModelSource.MODEL_FILE_NAME): String? {
        val f = File(modelsDir(), fileName)
        return if (f.exists() && f.length() >= MIN_PLAUSIBLE_MODEL_BYTES) f.absolutePath else null
    }

    fun deleteModel(fileName: String = HFModelSource.MODEL_FILE_NAME) {
        File(modelsDir(), fileName).delete()
    }

    companion object {
        /** Smallest plausible size for a real quantized Gemma .task file (int4 builds start around a few hundred MB). */
        private const val MIN_PLAUSIBLE_MODEL_BYTES = 50L * 1024 * 1024
    }
}
