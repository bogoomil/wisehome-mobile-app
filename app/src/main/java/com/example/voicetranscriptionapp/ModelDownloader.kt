package com.example.voicetranscriptionapp

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL

/**
 * Download and manage Whisper models
 */
class ModelDownloader(private val context: Context) {
    
    companion object {
        private const val TAG = "ModelDownloader"
        
        // Whisper model URLs from HuggingFace
        private const val BASE_URL = "https://huggingface.co/ggerganov/whisper.cpp/resolve/main"
        
        enum class ModelType(
            val filename: String,
            val size: Long, // bytes
            val description: String
        ) {
            TINY("ggml-tiny.bin", 75 * 1024 * 1024L, "Fastest, lowest accuracy"),
            BASE("ggml-base.bin", 142 * 1024 * 1024L, "Good balance (recommended)"),
            SMALL("ggml-small.bin", 466 * 1024 * 1024L, "Better accuracy, slower"),
            MEDIUM("ggml-medium.bin", 1500 * 1024 * 1024L, "Best accuracy, very slow");
            
            val url: String get() = "$BASE_URL/$filename"
            val sizeInMB: Int get() = (size / (1024 * 1024)).toInt()
        }
    }
    
    /**
     * Download progress callback
     */
    interface DownloadProgressListener {
        fun onProgress(downloadedBytes: Long, totalBytes: Long, percentage: Int)
        fun onComplete(file: File)
        fun onError(error: Exception)
    }
    
    /**
     * Check if a model is already downloaded
     */
    fun isModelDownloaded(modelType: ModelType): Boolean {
        val modelFile = getModelFile(modelType)
        return modelFile.exists() && modelFile.length() > 0
    }
    
    /**
     * Get the model file path
     */
    fun getModelFile(modelType: ModelType): File {
        return File(context.filesDir, modelType.filename)
    }
    
    /**
     * Get downloaded model info
     */
    fun getModelInfo(modelType: ModelType): Map<String, Any> {
        val file = getModelFile(modelType)
        return mapOf(
            "exists" to file.exists(),
            "size" to file.length(),
            "sizeInMB" to (file.length() / (1024 * 1024)),
            "path" to file.absolutePath,
            "expectedSize" to modelType.size,
            "expectedSizeInMB" to modelType.sizeInMB,
            "complete" to (file.exists() && file.length() >= modelType.size * 0.95) // 95% threshold
        )
    }
    
    /**
     * Download a Whisper model
     */
    suspend fun downloadModel(
        modelType: ModelType,
        progressListener: DownloadProgressListener? = null
    ): Result<File> = withContext(Dispatchers.IO) {
        try {
            val outputFile = getModelFile(modelType)
            
            // Check if already exists
            if (outputFile.exists() && outputFile.length() >= modelType.size * 0.95) {
                Log.i(TAG, "Model already downloaded: ${outputFile.absolutePath}")
                progressListener?.onComplete(outputFile)
                return@withContext Result.success(outputFile)
            }
            
            Log.i(TAG, "Downloading model: ${modelType.name} from ${modelType.url}")
            Log.i(TAG, "Expected size: ${modelType.sizeInMB} MB")
            
            val url = URL(modelType.url)
            val connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = "GET"
            connection.connectTimeout = 30000
            connection.readTimeout = 30000
            connection.connect()
            
            if (connection.responseCode != HttpURLConnection.HTTP_OK) {
                throw Exception("Server returned HTTP ${connection.responseCode}: ${connection.responseMessage}")
            }
            
            val totalBytes = connection.contentLengthLong
            Log.i(TAG, "Content length: ${totalBytes / (1024 * 1024)} MB")
            
            var downloadedBytes = 0L
            val buffer = ByteArray(8192)
            
            connection.inputStream.use { input ->
                FileOutputStream(outputFile).use { output ->
                    var bytesRead: Int
                    var lastProgressUpdate = 0L
                    
                    while (input.read(buffer).also { bytesRead = it } != -1) {
                        output.write(buffer, 0, bytesRead)
                        downloadedBytes += bytesRead
                        
                        // Update progress every 1 MB
                        if (downloadedBytes - lastProgressUpdate >= 1024 * 1024) {
                            val percentage = ((downloadedBytes * 100) / totalBytes).toInt()
                            progressListener?.onProgress(downloadedBytes, totalBytes, percentage)
                            Log.d(TAG, "Progress: $percentage% (${downloadedBytes / (1024 * 1024)} MB)")
                            lastProgressUpdate = downloadedBytes
                        }
                    }
                }
            }
            
            connection.disconnect()
            
            Log.i(TAG, "Download complete: ${outputFile.absolutePath}")
            Log.i(TAG, "File size: ${outputFile.length() / (1024 * 1024)} MB")
            
            progressListener?.onComplete(outputFile)
            Result.success(outputFile)
            
        } catch (e: Exception) {
            Log.e(TAG, "Download failed", e)
            progressListener?.onError(e)
            Result.failure(e)
        }
    }
    
    /**
     * Delete a downloaded model
     */
    fun deleteModel(modelType: ModelType): Boolean {
        val file = getModelFile(modelType)
        return if (file.exists()) {
            val deleted = file.delete()
            Log.i(TAG, "Model deleted: ${modelType.name}, success: $deleted")
            deleted
        } else {
            false
        }
    }
    
    /**
     * Get available storage space
     */
    fun getAvailableStorageSpaceMB(): Long {
        return context.filesDir.usableSpace / (1024 * 1024)
    }
    
    /**
     * Check if there's enough space to download a model
     */
    fun hasEnoughSpaceFor(modelType: ModelType): Boolean {
        val availableSpace = getAvailableStorageSpaceMB()
        val requiredSpace = modelType.sizeInMB + 50 // 50 MB buffer
        return availableSpace >= requiredSpace
    }
    
    /**
     * Get list of all models with their download status
     */
    fun getAllModelsStatus(): List<Map<String, Any>> {
        return ModelType.values().map { model ->
            mapOf(
                "type" to model.name,
                "filename" to model.filename,
                "size" to model.sizeInMB,
                "description" to model.description,
                "downloaded" to isModelDownloaded(model),
                "info" to getModelInfo(model)
            )
        }
    }
}

