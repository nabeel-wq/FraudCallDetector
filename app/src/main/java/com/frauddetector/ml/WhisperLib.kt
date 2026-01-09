package com.frauddetector.ml

import android.content.res.AssetManager

/**
 * Low-level JNI bindings for Whisper.cpp
 * All native methods are accessed through the Companion object
 */
class WhisperLib {
    companion object {
        init {
            System.loadLibrary("whisper-jni")
        }

        /**
         * Initialize Whisper context from an Android asset
         * @param assetManager Android AssetManager
         * @param assetPath Path to model file in assets (e.g., "models/ggml-tiny.bin")
         * @return Context pointer (0 if failed)
         */
        external fun initContextFromAsset(assetManager: AssetManager, assetPath: String): Long

        /**
         * Initialize Whisper context from a file path
         * @param modelPath Absolute path to model file
         * @return Context pointer (0 if failed)
         */
        external fun initContext(modelPath: String): Long

        /**
         * Free Whisper context and release resources
         * @param contextPtr Context pointer from init functions
         */
        external fun freeContext(contextPtr: Long)

        /**
         * Transcribe audio data
         * @param contextPtr Context pointer
         * @param numThreads Number of threads to use (typically 2-4)
         * @param audioData Float array of audio samples (16kHz mono, normalized to -1.0 to 1.0)
         */
        external fun fullTranscribe(contextPtr: Long, numThreads: Int, audioData: FloatArray)

        /**
         * Get number of transcribed text segments after transcription
         * @param contextPtr Context pointer
         * @return Number of segments
         */
        external fun getTextSegmentCount(contextPtr: Long): Int

        /**
         * Get text segment by index
         * @param contextPtr Context pointer
         * @param index Segment index (0-based)
         * @return Transcribed text for that segment
         */
        external fun getTextSegment(contextPtr: Long, index: Int): String

        /**
         * Get system information (CPU features, SIMD support, etc.)
         * @return System info string
         */
        external fun getSystemInfo(): String
    }
}
