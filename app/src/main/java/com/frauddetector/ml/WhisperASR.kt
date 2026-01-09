package com.frauddetector.ml

import android.content.Context
import com.frauddetector.utils.AppLogger
import com.frauddetector.utils.AudioDecoder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Whisper.cpp implementation of ASR model for on-device speech recognition.
 * Uses whisper.cpp via JNI for fast, accurate transcription.
 * Model: Whisper Tiny (lightweight, ~39MB)
 */
class WhisperASR(private val context: Context) : ASRModel {

    companion object {
        private const val TAG = "WhisperASR"
        private const val MODEL_ASSET_PATH = "models/ggml-tiny.bin"
        private const val SAMPLE_RATE = 16000
    }

    private var contextPtr: Long = 0L
    private var initialized = false

    override suspend fun initialize(): Boolean = withContext(Dispatchers.IO) {
        if (initialized) {
            AppLogger.d(TAG, "Model already initialized")
            return@withContext true
        }

        try {
            AppLogger.d(TAG, "Initializing Whisper ASR model...")
            AppLogger.d(TAG, "System info: ${WhisperLib.getSystemInfo()}")

            // Load model from assets
            contextPtr = WhisperLib.initContextFromAsset(
                context.assets,
                MODEL_ASSET_PATH
            )

            if (contextPtr == 0L) {
                AppLogger.e(TAG, "Failed to load Whisper model from assets")
                return@withContext false
            }

            initialized = true
            AppLogger.d(TAG, "Whisper ASR model initialized successfully (context: $contextPtr)")
            return@withContext true

        } catch (e: Exception) {
            AppLogger.e(TAG, "Failed to initialize Whisper model", e)
            return@withContext false
        }
    }

    override suspend fun transcribe(audioFile: File, languageCode: String): String? =
        withContext(Dispatchers.IO) {
            if (!initialized || contextPtr == 0L) {
                AppLogger.w(TAG, "Model not initialized, cannot transcribe")
                return@withContext null
            }

            try {
                AppLogger.d(TAG, "Transcribing file: ${audioFile.name} (${audioFile.length()} bytes)")

                // Decode audio file to float array using MediaCodec
                val audioData = AudioDecoder.decodeToFloatArray(audioFile)
                if (audioData == null) {
                    AppLogger.e(TAG, "Failed to decode audio file")
                    return@withContext null
                }
                AppLogger.d(TAG, "Audio decoded to ${audioData.size} float samples")

                // Run transcription
                val numThreads = Runtime.getRuntime().availableProcessors().coerceIn(2, 4)
                WhisperLib.fullTranscribe(contextPtr, numThreads, audioData)

                // Collect all segments
                val segmentCount = WhisperLib.getTextSegmentCount(contextPtr)
                val transcript = buildString {
                    for (i in 0 until segmentCount) {
                        append(WhisperLib.getTextSegment(contextPtr, i))
                    }
                }

                AppLogger.d(TAG, "Transcribed ($segmentCount segments): $transcript")
                return@withContext transcript.trim()

            } catch (e: Exception) {
                AppLogger.e(TAG, "Transcription failed", e)
                return@withContext null
            }
        }

    override suspend fun transcribeChunk(audioData: ByteArray, languageCode: String): String? =
        withContext(Dispatchers.IO) {
            if (!initialized || contextPtr == 0L) {
                AppLogger.w(TAG, "Model not initialized, cannot transcribe chunk")
                return@withContext null
            }

            try {
                // Convert PCM bytes to float array
                val floatData = AudioDecoder.pcmBytesToFloat(audioData)

                // Run transcription
                val numThreads = Runtime.getRuntime().availableProcessors().coerceIn(2, 4)
                WhisperLib.fullTranscribe(contextPtr, numThreads, floatData)

                // Collect all segments
                val segmentCount = WhisperLib.getTextSegmentCount(contextPtr)
                val transcript = buildString {
                    for (i in 0 until segmentCount) {
                        append(WhisperLib.getTextSegment(contextPtr, i))
                    }
                }

                if (transcript.isNotEmpty()) {
                    AppLogger.d(TAG, "Chunk transcribed: $transcript")
                }

                return@withContext transcript.trim()

            } catch (e: Exception) {
                AppLogger.e(TAG, "Chunk transcription failed", e)
                return@withContext null
            }
        }

    override fun isInitialized(): Boolean = initialized

    override fun release() {
        try {
            if (contextPtr != 0L) {
                WhisperLib.freeContext(contextPtr)
                contextPtr = 0L
            }
            initialized = false
            AppLogger.d(TAG, "Whisper model released")
        } catch (e: Exception) {
            AppLogger.e(TAG, "Error releasing model", e)
        }
    }

    override fun getModelName(): String = "Whisper Tiny (ggml)"

    override fun getSupportedLanguages(): List<String> = listOf(
        "en", "es", "fr", "de", "it", "pt", "ru", "zh", "ja", "ko", "ar", "hi"
    )
}
