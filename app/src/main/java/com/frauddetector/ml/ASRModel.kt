package com.frauddetector.ml

import java.io.File

/**
 * Interface for Automatic Speech Recognition (ASR) models.
 * Allows easy swapping between different ASR implementations (WhisperCPP, IndicConformer, etc.)
 */
interface ASRModel {
    /**
     * Initialize the ASR model. Should be called once before transcription.
     * @return true if initialization successful, false otherwise
     */
    suspend fun initialize(): Boolean
    
    /**
     * Transcribe audio file to text.
     * @param audioFile Path to audio file (should be 16kHz WAV format)
     * @param languageCode Optional language code (e.g., "en", "hi", "ta")
     * @return Transcribed text or null if transcription failed
     */
    suspend fun transcribe(audioFile: File, languageCode: String = "en"): String?
    
    /**
     * Transcribe audio chunk for real-time processing.
     * @param audioData Raw audio data (16kHz, mono, PCM)
     * @param languageCode Optional language code
     * @return Transcribed text or null if transcription failed
     */
    suspend fun transcribeChunk(audioData: ByteArray, languageCode: String = "en"): String?
    
    /**
     * Check if model is initialized and ready for use.
     */
    fun isInitialized(): Boolean
    
    /**
     * Release model resources. Should be called when model is no longer needed.
     */
    fun release()
    
    /**
     * Get model name for logging/debugging.
     */
    fun getModelName(): String
    
    /**
     * Get supported languages.
     * @return List of supported language codes
     */
    fun getSupportedLanguages(): List<String>
}
