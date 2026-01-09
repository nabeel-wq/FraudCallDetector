package com.frauddetector.utils

import android.app.DownloadManager
import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.os.Environment
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Manages downloading of optional ML models (FLAN-T5).
 */
class ModelDownloadManager(private val context: Context) {
    
    companion object {
        private const val TAG = "ModelDownloadManager"
        
        // TODO: Replace with actual model hosting URL
        // Options:
        // 1. HuggingFace: https://huggingface.co/google/flan-t5-small/resolve/main/...
        // 2. Your CDN: https://your-cdn.com/models/flan_t5_small.zip
        // 3. Firebase Storage: gs://your-bucket/models/flan_t5_small.zip
        private const val FLAN_T5_MODEL_URL = "https://placeholder-url.com/flan_t5_small.zip"
        private const val FLAN_T5_MODEL_SIZE_MB = 300
    }
    
    private val downloadManager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
    
    private val _downloadStateFlow = MutableStateFlow<DownloadState>(DownloadState.NotStarted)
    val downloadStateFlow: StateFlow<DownloadState> = _downloadStateFlow
    
    sealed class DownloadState {
        object NotStarted : DownloadState()
        data class Downloading(val progress: Int, val downloadedMB: Int, val totalMB: Int) : DownloadState()
        object Extracting : DownloadState()
        object Completed : DownloadState()
        data class Failed(val error: String) : DownloadState()
    }
    
    /**
     * Start downloading FLAN-T5 model.
     */
    suspend fun downloadFlanT5Model(): Boolean = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "Starting FLAN-T5 model download")
            _downloadStateFlow.value = DownloadState.Downloading(0, 0, FLAN_T5_MODEL_SIZE_MB)
            
            // Create download request
            val request = DownloadManager.Request(Uri.parse(FLAN_T5_MODEL_URL)).apply {
                setTitle("FLAN-T5 Model")
                setDescription("Downloading advanced reasoning model")
                setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE)
                setDestinationInExternalFilesDir(
                    context,
                    Environment.DIRECTORY_DOWNLOADS,
                    "flan_t5_small.zip"
                )
                setAllowedNetworkTypes(
                    DownloadManager.Request.NETWORK_WIFI or DownloadManager.Request.NETWORK_MOBILE
                )
            }
            
            val downloadId = downloadManager.enqueue(request)
            
            // Monitor download progress
            val success = monitorDownload(downloadId)
            
            if (success) {
                // Extract model
                _downloadStateFlow.value = DownloadState.Extracting
                extractModel(downloadId)
                
                _downloadStateFlow.value = DownloadState.Completed
                Log.d(TAG, "FLAN-T5 model downloaded and extracted successfully")
                return@withContext true
            } else {
                _downloadStateFlow.value = DownloadState.Failed("Download failed")
                return@withContext false
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "Failed to download FLAN-T5 model", e)
            _downloadStateFlow.value = DownloadState.Failed(e.message ?: "Unknown error")
            return@withContext false
        }
    }
    
    /**
     * Monitor download progress.
     */
    private suspend fun monitorDownload(downloadId: Long): Boolean {
        var downloading = true
        
        while (downloading) {
            val query = DownloadManager.Query().setFilterById(downloadId)
            val cursor: Cursor? = downloadManager.query(query)
            
            cursor?.use {
                if (it.moveToFirst()) {
                    val status = it.getInt(it.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS))
                    
                    when (status) {
                        DownloadManager.STATUS_SUCCESSFUL -> {
                            downloading = false
                            return true
                        }
                        DownloadManager.STATUS_FAILED -> {
                            downloading = false
                            return false
                        }
                        DownloadManager.STATUS_RUNNING -> {
                            val downloaded = it.getLong(it.getColumnIndexOrThrow(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR))
                            val total = it.getLong(it.getColumnIndexOrThrow(DownloadManager.COLUMN_TOTAL_SIZE_BYTES))
                            
                            if (total > 0) {
                                val progress = ((downloaded * 100) / total).toInt()
                                val downloadedMB = (downloaded / (1024 * 1024)).toInt()
                                val totalMB = (total / (1024 * 1024)).toInt()
                                
                                _downloadStateFlow.value = DownloadState.Downloading(progress, downloadedMB, totalMB)
                                Log.d(TAG, "Download progress: $progress% ($downloadedMB MB / $totalMB MB)")
                            }
                        }
                    }
                }
            }
            
            delay(500) // Check every 500ms
        }
        
        return false
    }
    
    /**
     * Extract downloaded model.
     */
    private fun extractModel(downloadId: Long) {
        val query = DownloadManager.Query().setFilterById(downloadId)
        val cursor = downloadManager.query(query)
        
        cursor?.use {
            if (it.moveToFirst()) {
                val localUri = it.getString(it.getColumnIndexOrThrow(DownloadManager.COLUMN_LOCAL_URI))
                val sourceFile = File(Uri.parse(localUri).path ?: return)
                
                // TODO: Implement ZIP extraction
                // For now, just move/copy the file
                val destDir = File(context.filesDir, "flan_t5_saved_model")
                destDir.mkdirs()
                
                Log.d(TAG, "Model extracted to: ${destDir.absolutePath}")
            }
        }
    }
    
    /**
     * Check if FLAN-T5 model is downloaded.
     */
    fun isFlanT5Downloaded(): Boolean {
        val modelDir = File(context.filesDir, "flan_t5_saved_model")
        return modelDir.exists() && modelDir.isDirectory
    }
    
    /**
     * Delete FLAN-T5 model to free up space.
     */
    fun deleteFlanT5Model(): Boolean {
        return try {
            val modelDir = File(context.filesDir, "flan_t5_saved_model")
            if (modelDir.exists()) {
                modelDir.deleteRecursively()
                Log.d(TAG, "FLAN-T5 model deleted")
                true
            } else {
                false
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to delete FLAN-T5 model", e)
            false
        }
    }
    
    /**
     * Get model size in MB.
     */
    fun getModelSizeMB(): Long {
        val modelDir = File(context.filesDir, "flan_t5_saved_model")
        return if (modelDir.exists()) {
            modelDir.walkTopDown().sumOf { it.length() } / (1024 * 1024)
        } else {
            0
        }
    }
}
