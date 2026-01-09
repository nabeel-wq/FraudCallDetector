package com.frauddetector.utils

import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Decodes compressed audio files (3GPP, AAC, etc.) to PCM float arrays for ML models.
 * Uses MediaExtractor and MediaCodec to decode audio data.
 */
object AudioDecoder {

    private const val TAG = "AudioDecoder"
    private const val TIMEOUT_US = 10000L

    /**
     * Decode audio file to float array for Whisper.cpp
     * Handles 3GPP (AMR-WB), AAC, and other MediaCodec-supported formats
     *
     * @param audioFile Input audio file
     * @return FloatArray of normalized audio samples (-1.0 to 1.0), or null if decoding fails
     */
    fun decodeToFloatArray(audioFile: File): FloatArray? {
        if (!audioFile.exists() || audioFile.length() == 0L) {
            AppLogger.e(TAG, "Audio file does not exist or is empty: ${audioFile.absolutePath}")
            return null
        }

        val extractor = MediaExtractor()
        var codec: MediaCodec? = null

        try {
            AppLogger.d(TAG, "Decoding audio file: ${audioFile.name} (${audioFile.length()} bytes)")

            // Set data source
            extractor.setDataSource(audioFile.absolutePath)

            // Find audio track
            var audioTrackIndex = -1
            var audioFormat: MediaFormat? = null

            for (i in 0 until extractor.trackCount) {
                val format = extractor.getTrackFormat(i)
                val mime = format.getString(MediaFormat.KEY_MIME)
                if (mime?.startsWith("audio/") == true) {
                    audioTrackIndex = i
                    audioFormat = format
                    AppLogger.d(TAG, "Found audio track: index=$i, mime=$mime")
                    break
                }
            }

            if (audioTrackIndex < 0 || audioFormat == null) {
                AppLogger.e(TAG, "No audio track found in file")
                return null
            }

            // Select audio track
            extractor.selectTrack(audioTrackIndex)

            // Get audio properties
            val mime = audioFormat.getString(MediaFormat.KEY_MIME) ?: ""
            val sampleRate = audioFormat.getInteger(MediaFormat.KEY_SAMPLE_RATE)
            val channelCount = audioFormat.getInteger(MediaFormat.KEY_CHANNEL_COUNT)

            AppLogger.d(TAG, "Audio format: mime=$mime, sampleRate=$sampleRate, channels=$channelCount")

            // Create decoder
            codec = MediaCodec.createDecoderByType(mime)
            codec.configure(audioFormat, null, null, 0)
            codec.start()

            // Decode audio
            val decodedSamples = mutableListOf<Short>()
            var inputDone = false
            var outputDone = false

            val bufferInfo = MediaCodec.BufferInfo()

            while (!outputDone) {
                // Feed input
                if (!inputDone) {
                    val inputBufferIndex = codec.dequeueInputBuffer(TIMEOUT_US)
                    if (inputBufferIndex >= 0) {
                        val inputBuffer = codec.getInputBuffer(inputBufferIndex)
                        if (inputBuffer != null) {
                            val sampleSize = extractor.readSampleData(inputBuffer, 0)
                            if (sampleSize < 0) {
                                // End of stream
                                codec.queueInputBuffer(inputBufferIndex, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                                inputDone = true
                                AppLogger.d(TAG, "Input done")
                            } else {
                                val presentationTimeUs = extractor.sampleTime
                                codec.queueInputBuffer(inputBufferIndex, 0, sampleSize, presentationTimeUs, 0)
                                extractor.advance()
                            }
                        }
                    }
                }

                // Get output
                val outputBufferIndex = codec.dequeueOutputBuffer(bufferInfo, TIMEOUT_US)
                when {
                    outputBufferIndex >= 0 -> {
                        val outputBuffer = codec.getOutputBuffer(outputBufferIndex)
                        if (outputBuffer != null && bufferInfo.size > 0) {
                            // Read PCM data
                            outputBuffer.position(bufferInfo.offset)
                            outputBuffer.limit(bufferInfo.offset + bufferInfo.size)

                            val pcmData = ByteArray(bufferInfo.size)
                            outputBuffer.get(pcmData)

                            // Convert bytes to shorts (16-bit PCM)
                            val shortBuffer = ByteBuffer.wrap(pcmData)
                                .order(ByteOrder.LITTLE_ENDIAN)
                                .asShortBuffer()

                            while (shortBuffer.hasRemaining()) {
                                decodedSamples.add(shortBuffer.get())
                            }
                        }

                        codec.releaseOutputBuffer(outputBufferIndex, false)

                        if ((bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                            outputDone = true
                            AppLogger.d(TAG, "Output done")
                        }
                    }
                    outputBufferIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                        val newFormat = codec.outputFormat
                        AppLogger.d(TAG, "Output format changed: $newFormat")
                    }
                }
            }

            AppLogger.d(TAG, "Decoded ${decodedSamples.size} PCM samples")

            // Convert to mono if stereo
            val monoSamples = if (channelCount > 1) {
                AppLogger.d(TAG, "Converting from $channelCount channels to mono")
                val mono = mutableListOf<Short>()
                for (i in 0 until decodedSamples.size step channelCount) {
                    // Average all channels
                    val avg = (0 until channelCount.coerceAtMost(decodedSamples.size - i))
                        .sumOf { decodedSamples[i + it].toInt() } / channelCount
                    mono.add(avg.toShort())
                }
                mono
            } else {
                decodedSamples
            }

            // Convert to float array normalized to -1.0 to 1.0
            val floatArray = FloatArray(monoSamples.size) { i ->
                (monoSamples[i] / 32768.0f).coerceIn(-1f, 1f)
            }

            AppLogger.d(TAG, "Converted to ${floatArray.size} float samples")
            return floatArray

        } catch (e: Exception) {
            AppLogger.e(TAG, "Failed to decode audio file", e)
            return null
        } finally {
            try {
                codec?.stop()
                codec?.release()
                extractor.release()
            } catch (e: Exception) {
                AppLogger.e(TAG, "Error releasing decoder resources", e)
            }
        }
    }

    /**
     * Decode raw PCM byte array to float array
     * Assumes 16-bit little-endian PCM mono audio
     *
     * @param pcmData Raw PCM bytes
     * @return FloatArray of normalized audio samples
     */
    fun pcmBytesToFloat(pcmData: ByteArray): FloatArray {
        val shortBuffer = ByteBuffer.wrap(pcmData)
            .order(ByteOrder.LITTLE_ENDIAN)
            .asShortBuffer()

        val shortArray = ShortArray(shortBuffer.remaining())
        shortBuffer.get(shortArray)

        return FloatArray(shortArray.size) { i ->
            (shortArray[i] / 32768.0f).coerceIn(-1f, 1f)
        }
    }
}
