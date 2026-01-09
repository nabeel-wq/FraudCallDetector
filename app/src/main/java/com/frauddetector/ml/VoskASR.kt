package com.frauddetector.ml

import android.content.Context
import com.frauddetector.utils.AppLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.vosk.Model
import org.vosk.Recognizer
import org.vosk.android.StorageService
import java.io.File

/**
 * Vosk implementation of ASR model for on-device speech recognition.
 * Uses Vosk Android library with proven stability and working bindings.
 * Model: Small English model (lightweight, ~40MB)
 */
class VoskASR(private val context: Context) : ASRModel {
    
    companion object {
        private const val TAG = "VoskASR"
        private const val MODEL_NAME = "vosk-model-small-en-us-0.15"
        private const val SAMPLE_RATE = 16000f
    }
    
    private var model: Model? = null
    private var recognizer: Recognizer? = null
    private var initialized = false
    
    override suspend fun initialize(): Boolean = withContext(Dispatchers.IO) {
        if (initialized) {
            AppLogger.d(TAG, "Model already initialized")
            return@withContext true
        }
        
        try {
            AppLogger.d(TAG, "Initializing Vosk ASR model...")
            
            // Unpack model from assets to cache directory
            val modelPath = File(context.cacheDir, MODEL_NAME)
            if (!modelPath.exists()) {
                AppLogger.d(TAG, "Unpacking model from assets...")
                StorageService.unpack(context, MODEL_NAME, MODEL_NAME, { model ->
                    this@VoskASR.model = model
                    AppLogger.d(TAG, "Model unpacked successfully")
                }, { exception ->
                    AppLogger.e(TAG, "Failed to unpack model", exception)
                })
                
                // Wait a bit for unpacking to complete
                kotlinx.coroutines.delay(2000)
            } else {
                AppLogger.d(TAG, "Model already unpacked, loading...")
                model = Model(modelPath.absolutePath)
            }
            
            if (model == null) {
                AppLogger.e(TAG, "Failed to load Vosk model")
                return@withContext false
            }
            
            // Create recognizer
            recognizer = Recognizer(model, SAMPLE_RATE)
            
            initialized = true
            AppLogger.d(TAG, "Vosk ASR model initialized successfully")
            return@withContext true
            
        } catch (e: Exception) {
            AppLogger.e(TAG, "Failed to initialize Vosk model", e)
            return@withContext false
        }
    }
    
    override suspend fun transcribe(audioFile: File, languageCode: String): String? = withContext(Dispatchers.IO) {
        if (!initialized || recognizer == null) {
            AppLogger.w(TAG, "Model not initialized, cannot transcribe")
            return@withContext null
        }
        
        try {
            // Read audio file and process
            val audioData = audioFile.readBytes()
            recognizer?.acceptWaveForm(audioData, audioData.size)
            val result = recognizer?.finalResult
            
            // Parse JSON result to extract text
            val transcript = parseVoskResult(result)
            AppLogger.d(TAG, "Transcribed: $transcript")
            
            return@withContext transcript
            
        } catch (e: Exception) {
            AppLogger.e(TAG, "Transcription failed", e)
            return@withContext null
        }
    }
    
    override suspend fun transcribeChunk(audioData: ByteArray, languageCode: String): String? = withContext(Dispatchers.IO) {
        if (!initialized || recognizer == null) {
            AppLogger.w(TAG, "Model not initialized, cannot transcribe chunk")
            return@withContext null
        }
        
        try {
            // Process audio chunk
            if (recognizer?.acceptWaveForm(audioData, audioData.size) == true) {
                val result = recognizer?.result
                val transcript = parseVoskResult(result)
                
                if (transcript.isNotEmpty()) {
                    AppLogger.d(TAG, "Chunk transcribed: $transcript")
                }
                
                return@withContext transcript
            }
            
            return@withContext ""
            
        } catch (e: Exception) {
            AppLogger.e(TAG, "Chunk transcription failed", e)
            return@withContext null
        }
    }
    
    override fun isInitialized(): Boolean = initialized
    
    override fun release() {
        try {
            recognizer?.close()
            model?.close()
            recognizer = null
            model = null
            initialized = false
            AppLogger.d(TAG, "Vosk model released")
        } catch (e: Exception) {
            AppLogger.e(TAG, "Error releasing model", e)
        }
    }
    
    override fun getModelName(): String = "Vosk Small English"
    
    override fun getSupportedLanguages(): List<String> = listOf("en")
    
    /**
     * Parse Vosk JSON result to extract text.
     * Vosk returns results in JSON format: {"text": "transcribed text"}
     */
    private fun parseVoskResult(result: String?): String {
        if (result == null) return ""
        
        try {
            // Simple JSON parsing (extract text field)
            val textStart = result.indexOf("\"text\"")
            if (textStart == -1) return ""
            
            val colonIndex = result.indexOf(":", textStart)
            val quoteStart = result.indexOf("\"", colonIndex)
            val quoteEnd = result.indexOf("\"", quoteStart + 1)
            
            if (quoteStart != -1 && quoteEnd != -1) {
                return result.substring(quoteStart + 1, quoteEnd).trim()
            }
        } catch (e: Exception) {
            AppLogger.e(TAG, "Failed to parse Vosk result", e)
        }
        
        return ""
    }
}
