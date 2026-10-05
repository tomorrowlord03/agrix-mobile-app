package com.protoprojects.agrix.ai

import android.content.Context
import android.net.Uri
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.security.MessageDigest

sealed class TransferState {
    object Idle : TransferState()
    data class Progress(val bytesTransferred: Long, val totalBytes: Long, val percent: Int) : TransferState()
    data class Verifying(val message: String = "Verifying model integrity (SHA-256)...") : TransferState()
    data class Success(val destinationPath: String) : TransferState()
    data class Error(val error: String) : TransferState()
}

/**
 * Handles offline peer-to-peer and local file transfer of the Gemma model weights:
 * - Direct import from SD Card, USB-OTG drive, or SAF file picker
 * - Peer-to-peer export for sharing model with neighboring farmers without internet
 * - SHA-256 integrity verification before finalizing installed model
 */
class ModelDistributionManager(private val context: Context) {

    private val _transferState = MutableStateFlow<TransferState>(TransferState.Idle)
    val transferState: StateFlow<TransferState> = _transferState.asStateFlow()

    /**
     * Imports a model file from a content URI (e.g. from Storage Access Framework / SD card)
     * into the app's internal models directory, verifying its SHA-256 hash.
     */
    suspend fun importModelFromUri(
        sourceUri: Uri,
        expectedSha256: String? = null
    ): Result<String> = withContext(Dispatchers.IO) {
        try {
            val contentResolver = context.contentResolver
            val modelsDir = File(context.filesDir, "models").apply { mkdirs() }
            val destFile = File(modelsDir, HFModelSource.MODEL_FILE_NAME)
            val tempFile = File(modelsDir, "${HFModelSource.MODEL_FILE_NAME}.tmp")

            val totalBytes = contentResolver.openAssetFileDescriptor(sourceUri, "r")?.use {
                it.length
            } ?: -1L

            _transferState.value = TransferState.Progress(0, totalBytes, 0)

            contentResolver.openInputStream(sourceUri)?.use { input ->
                FileOutputStream(tempFile).use { output ->
                    val buffer = ByteArray(64 * 1024)
                    var bytesCopied = 0L
                    var read: Int
                    while (input.read(buffer).also { read = it } != -1) {
                        output.write(buffer, 0, read)
                        bytesCopied += read
                        val percent = if (totalBytes > 0) ((bytesCopied * 100) / totalBytes).toInt() else -1
                        _transferState.value = TransferState.Progress(bytesCopied, totalBytes, percent)
                    }
                    output.flush()
                }
            } ?: throw IllegalStateException("Could not open input stream for $sourceUri")

            // SHA-256 Verification
            if (!expectedSha256.isNullOrBlank() && expectedSha256 != "REPLACE_WITH_ACTUAL_SHA256_AFTER_DOWNLOAD") {
                _transferState.value = TransferState.Verifying()
                val actualHash = computeSha256(tempFile)
                if (!actualHash.equals(expectedSha256, ignoreCase = true)) {
                    tempFile.delete()
                    val err = "Model verification failed! Checksum mismatch: expected $expectedSha256, got $actualHash"
                    _transferState.value = TransferState.Error(err)
                    return@withContext Result.failure(IllegalStateException(err))
                }
            }

            if (destFile.exists()) destFile.delete()
            if (!tempFile.renameTo(destFile)) {
                throw IllegalStateException("Failed to move imported model file to destination")
            }

            _transferState.value = TransferState.Success(destFile.absolutePath)
            Log.i(TAG, "Model successfully imported to ${destFile.absolutePath}")
            Result.success(destFile.absolutePath)
        } catch (e: Exception) {
            Log.e(TAG, "Error importing model file", e)
            val errMsg = e.message ?: "Failed to import model file"
            _transferState.value = TransferState.Error(errMsg)
            Result.failure(e)
        }
    }

    /**
     * Exports installed model to a destination file / SD card directory.
     */
    suspend fun exportModelToFile(destDir: File): Result<File> = withContext(Dispatchers.IO) {
        try {
            val installedFile = File(File(context.filesDir, "models"), HFModelSource.MODEL_FILE_NAME)
            if (!installedFile.exists()) {
                val err = "No model is installed to export"
                _transferState.value = TransferState.Error(err)
                return@withContext Result.failure(IllegalStateException(err))
            }

            destDir.mkdirs()
            val targetFile = File(destDir, HFModelSource.MODEL_FILE_NAME)
            val totalBytes = installedFile.length()
            _transferState.value = TransferState.Progress(0, totalBytes, 0)

            FileInputStream(installedFile).use { input ->
                FileOutputStream(targetFile).use { output ->
                    val buffer = ByteArray(64 * 1024)
                    var bytesCopied = 0L
                    var read: Int
                    while (input.read(buffer).also { read = it } != -1) {
                        output.write(buffer, 0, read)
                        bytesCopied += read
                        val percent = ((bytesCopied * 100) / totalBytes).toInt()
                        _transferState.value = TransferState.Progress(bytesCopied, totalBytes, percent)
                    }
                    output.flush()
                }
            }

            _transferState.value = TransferState.Success(targetFile.absolutePath)
            Result.success(targetFile)
        } catch (e: Exception) {
            Log.e(TAG, "Error exporting model file", e)
            val errMsg = e.message ?: "Failed to export model"
            _transferState.value = TransferState.Error(errMsg)
            Result.failure(e)
        }
    }

    private fun computeSha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        FileInputStream(file).use { input ->
            val buffer = ByteArray(64 * 1024)
            var read: Int
            while (input.read(buffer).also { read = it } != -1) {
                digest.update(buffer, 0, read)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    fun resetState() {
        _transferState.value = TransferState.Idle
    }

    companion object {
        private const val TAG = "ModelDistributionMgr"
    }
}
