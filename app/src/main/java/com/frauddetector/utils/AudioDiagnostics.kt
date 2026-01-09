package com.frauddetector.utils

import android.media.MediaExtractor
import android.media.MediaFormat
import java.io.File
import kotlin.math.abs
import kotlin.math.sqrt

/**
 * Diagnostic utilities for analyzing audio files and debugging recording/decoding issues
 */
object AudioDiagnostics {

    private const val TAG = "AudioDiagnostics"

    data class AudioAnalysis(
        val fileSize: Long,
        val exists: Boolean,
        val isReadable: Boolean,
        val hasAudioTrack: Boolean,
        val audioFormat: String?,
        val sampleRate: Int?,
        val channels: Int?,
        val duration: Long?, // microseconds
        val bitrate: Int?,
        val decodedSamples: Int,
        val rmsLevel: Float,
        val peakLevel: Float,
        val isSilent: Boolean,
        val silenceThreshold: Float = 0.01f,
        val errorMessage: String? = null
    ) {
        fun toDetailedString(): String = buildString {
            appendLine("=== Audio File Analysis ===")
            appendLine("File exists: $exists")
            appendLine("File size: $fileSize bytes")
            appendLine("Readable: $isReadable")
            appendLine("Has audio track: $hasAudioTrack")
            appendLine("Format: $audioFormat")
            appendLine("Sample rate: $sampleRate Hz")
            appendLine("Channels: $channels")
            appendLine("Duration: ${duration?.let { it / 1000 }}ms")
            appendLine("Bitrate: ${bitrate?.let { it / 1000 }}kbps")
            appendLine("Decoded samples: $decodedSamples")
            appendLine("RMS level: $rmsLevel")
            appendLine("Peak level: $peakLevel")
            appendLine("Is silent: $isSilent (threshold: $silenceThreshold)")
            if (errorMessage != null) {
                appendLine("ERROR: $errorMessage")
            }
            appendLine("=========================")
        }
    }

    /**
     * Analyze an audio file comprehensively
     */
    fun analyzeAudioFile(file: File): AudioAnalysis {
        var exists = false
        var fileSize = 0L
        var isReadable = false
        var hasAudioTrack = false
        var audioFormat: String? = null
        var sampleRate: Int? = null
        var channels: Int? = null
        var duration: Long? = null
        var bitrate: Int? = null
        var decodedSamples = 0
        var rmsLevel = 0f
        var peakLevel = 0f
        var isSilent = true
        var errorMessage: String? = null

        try {
            exists = file.exists()
            fileSize = file.length()
            isReadable = file.canRead()

            AppLogger.d(TAG, "Analyzing: ${file.name} (${fileSize} bytes)")

            if (!exists || fileSize == 0L) {
                errorMessage = "File does not exist or is empty"
                return AudioAnalysis(
                    fileSize, exists, isReadable, hasAudioTrack,
                    audioFormat, sampleRate, channels, duration, bitrate,
                    decodedSamples, rmsLevel, peakLevel, isSilent,
                    errorMessage = errorMessage
                )
            }

            // Extract metadata
            val extractor = MediaExtractor()
            try {
                extractor.setDataSource(file.absolutePath)

                // Find audio track
                for (i in 0 until extractor.trackCount) {
                    val format = extractor.getTrackFormat(i)
                    val mime = format.getString(MediaFormat.KEY_MIME)

                    AppLogger.d(TAG, "Track $i: mime=$mime")

                    if (mime?.startsWith("audio/") == true) {
                        hasAudioTrack = true
                        audioFormat = mime
                        sampleRate = format.getInteger(MediaFormat.KEY_SAMPLE_RATE)
                        channels = format.getInteger(MediaFormat.KEY_CHANNEL_COUNT)

                        if (format.containsKey(MediaFormat.KEY_DURATION)) {
                            duration = format.getLong(MediaFormat.KEY_DURATION)
                        }
                        if (format.containsKey(MediaFormat.KEY_BIT_RATE)) {
                            bitrate = format.getInteger(MediaFormat.KEY_BIT_RATE)
                        }

                        AppLogger.d(TAG, "Audio track found: $audioFormat, ${sampleRate}Hz, $channels channels")
                        break
                    }
                }
            } finally {
                extractor.release()
            }

            if (!hasAudioTrack) {
                errorMessage = "No audio track found in file"
                return AudioAnalysis(
                    fileSize, exists, isReadable, hasAudioTrack,
                    audioFormat, sampleRate, channels, duration, bitrate,
                    decodedSamples, rmsLevel, peakLevel, isSilent,
                    errorMessage = errorMessage
                )
            }

            // Decode and analyze audio levels
            val floatArray = AudioDecoder.decodeToFloatArray(file)
            if (floatArray == null) {
                errorMessage = "Failed to decode audio"
                return AudioAnalysis(
                    fileSize, exists, isReadable, hasAudioTrack,
                    audioFormat, sampleRate, channels, duration, bitrate,
                    decodedSamples, rmsLevel, peakLevel, isSilent,
                    errorMessage = errorMessage
                )
            }

            decodedSamples = floatArray.size

            // Calculate RMS level
            var sumSquares = 0.0
            var peak = 0f

            for (sample in floatArray) {
                val absSample = abs(sample)
                sumSquares += (sample * sample).toDouble()
                if (absSample > peak) {
                    peak = absSample
                }
            }

            rmsLevel = sqrt(sumSquares / floatArray.size).toFloat()
            peakLevel = peak

            // Consider silent if RMS < 0.01 (1% of full scale)
            val silenceThreshold = 0.01f
            isSilent = rmsLevel < silenceThreshold

            AppLogger.d(TAG, "Decoded ${decodedSamples} samples: RMS=$rmsLevel, Peak=$peakLevel, Silent=$isSilent")

            return AudioAnalysis(
                fileSize, exists, isReadable, hasAudioTrack,
                audioFormat, sampleRate, channels, duration, bitrate,
                decodedSamples, rmsLevel, peakLevel, isSilent,
                silenceThreshold = silenceThreshold
            )

        } catch (e: Exception) {
            AppLogger.e(TAG, "Error analyzing audio file", e)
            errorMessage = e.message

            return AudioAnalysis(
                fileSize, exists, isReadable, hasAudioTrack,
                audioFormat, sampleRate, channels, duration, bitrate,
                decodedSamples, rmsLevel, peakLevel, isSilent,
                errorMessage = errorMessage
            )
        }
    }

    /**
     * Test if AudioDecoder is working correctly with a sample file
     */
    fun testAudioDecoder(file: File): Boolean {
        AppLogger.d(TAG, "Testing AudioDecoder with: ${file.name}")

        try {
            val floatArray = AudioDecoder.decodeToFloatArray(file)
            if (floatArray == null) {
                AppLogger.e(TAG, "AudioDecoder returned null")
                return false
            }

            if (floatArray.isEmpty()) {
                AppLogger.e(TAG, "AudioDecoder returned empty array")
                return false
            }

            AppLogger.d(TAG, "AudioDecoder successfully decoded ${floatArray.size} samples")

            // Check if samples are in valid range
            var validSamples = 0
            var invalidSamples = 0

            for (sample in floatArray) {
                if (sample in -1.0f..1.0f) {
                    validSamples++
                } else {
                    invalidSamples++
                }
            }

            val validPercent = (validSamples.toFloat() / floatArray.size) * 100
            AppLogger.d(TAG, "Valid samples: $validPercent% ($validSamples/${ floatArray.size})")

            return validSamples > 0 && validPercent > 99.0f

        } catch (e: Exception) {
            AppLogger.e(TAG, "AudioDecoder test failed", e)
            return false
        }
    }

    /**
     * Check if recorded audio files are actually being created with non-zero size
     */
    fun checkRecordingFiles(recordingsDir: File): List<String> {
        val diagnostics = mutableListOf<String>()

        try {
            if (!recordingsDir.exists()) {
                diagnostics.add("Recordings directory does not exist: ${recordingsDir.absolutePath}")
                return diagnostics
            }

            val files = recordingsDir.listFiles() ?: emptyArray()
            diagnostics.add("Found ${files.size} files in recordings directory")

            for (file in files.sortedByDescending { it.lastModified() }.take(5)) {
                val age = System.currentTimeMillis() - file.lastModified()
                diagnostics.add("  ${file.name}: ${file.length()} bytes, ${age / 1000}s ago")
            }

        } catch (e: Exception) {
            diagnostics.add("Error checking recording files: ${e.message}")
        }

        return diagnostics
    }

    /**
     * Analyze the first N samples to detect patterns
     */
    fun analyzeSamplePattern(file: File, numSamples: Int = 100): String {
        return try {
            val floatArray = AudioDecoder.decodeToFloatArray(file) ?: return "Failed to decode"

            val samplesToCheck = floatArray.take(numSamples.coerceAtMost(floatArray.size))

            buildString {
                appendLine("First $numSamples samples:")
                samplesToCheck.forEachIndexed { index, sample ->
                    if (index % 10 == 0) {
                        append(String.format("%.4f ", sample))
                    }
                }
                appendLine()

                val allZeros = samplesToCheck.all { it == 0f }
                val allSame = samplesToCheck.distinct().size == 1

                appendLine("All zeros: $allZeros")
                appendLine("All same value: $allSame")
                appendLine("Distinct values: ${samplesToCheck.distinct().size}")
            }
        } catch (e: Exception) {
            "Error: ${e.message}"
        }
    }

    /**
     * Quick check if file contains actual audio
     */
    fun quickAudioCheck(file: File): String {
        if (!file.exists()) return "File does not exist"
        if (file.length() == 0L) return "File is empty (0 bytes)"

        val floatArray = AudioDecoder.decodeToFloatArray(file) ?: return "Failed to decode"

        if (floatArray.isEmpty()) return "Decoded array is empty"

        val rms = sqrt(floatArray.map { it * it }.average()).toFloat()
        val peak = floatArray.maxOfOrNull { abs(it) } ?: 0f

        return when {
            rms < 0.001f -> "SILENT (RMS: $rms, Peak: $peak)"
            rms < 0.01f -> "VERY QUIET (RMS: $rms, Peak: $peak)"
            rms < 0.1f -> "QUIET (RMS: $rms, Peak: $peak)"
            else -> "HAS AUDIO (RMS: $rms, Peak: $peak)"
        }
    }
}
