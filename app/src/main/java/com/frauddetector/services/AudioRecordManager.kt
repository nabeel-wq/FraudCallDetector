package com.frauddetector.services

import android.content.Context
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Build
import android.util.Log
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import java.io.File
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Alternative audio recording using AudioRecord instead of MediaRecorder.
 * This may work better on Samsung devices where MediaRecorder is blocked.
 */
class AudioRecordManager(private val context: Context) : AudioManager {

    companion object {
        private const val TAG = "AudioRecordManager"
        private const val SAMPLE_RATE = 16000 // Required by Whisper
        private const val CHANNEL_CONFIG = AudioFormat.CHANNEL_IN_MONO
        private const val AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT
        private const val CHUNK_DURATION_MS = 5000 // 5 seconds per chunk

        // Calculate buffer size
        private val BUFFER_SIZE = AudioRecord.getMinBufferSize(
            SAMPLE_RATE,
            CHANNEL_CONFIG,
            AUDIO_FORMAT
        ).let { minSize ->
            // Use 2x minimum buffer size for reliability
            (minSize * 2).coerceAtLeast(SAMPLE_RATE * 2 * 2) // 2 seconds minimum
        }

        // Samsung-specific workarounds
        private val isSamsungDevice: Boolean by lazy {
            Build.MANUFACTURER.equals("samsung", ignoreCase = true)
        }

        // Audio source priority (different for Samsung)
        private val audioSourcePriority: List<Int> by lazy {
            if (isSamsungDevice) {
                listOfNotNull(
                    MediaRecorder.AudioSource.MIC,
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N)
                        MediaRecorder.AudioSource.UNPROCESSED
                    else null,
                    MediaRecorder.AudioSource.VOICE_RECOGNITION,
                    MediaRecorder.AudioSource.VOICE_COMMUNICATION
                )
            } else {
                listOfNotNull(
                    MediaRecorder.AudioSource.VOICE_COMMUNICATION,
                    MediaRecorder.AudioSource.MIC,
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N)
                        MediaRecorder.AudioSource.UNPROCESSED
                    else null,
                    MediaRecorder.AudioSource.VOICE_RECOGNITION
                )
            }
        }
    }

    private var audioRecord: AudioRecord? = null
    private var isRecording = false
    private var currentAudioSource: Int? = null
    private val recordingScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var recordingJob: Job? = null

    // Flow for audio chunks
    private val _audioChunkFlow = MutableSharedFlow<File>(replay = 0)
    override val audioChunkFlow: SharedFlow<File> = _audioChunkFlow

    /**
     * Get audio source name for logging.
     */
    private fun getAudioSourceName(source: Int): String {
        return when (source) {
            MediaRecorder.AudioSource.MIC -> "MIC"
            MediaRecorder.AudioSource.VOICE_COMMUNICATION -> "VOICE_COMMUNICATION"
            MediaRecorder.AudioSource.VOICE_RECOGNITION -> "VOICE_RECOGNITION"
            7 -> "UNPROCESSED" // MediaRecorder.AudioSource.UNPROCESSED (API 24+)
            else -> "UNKNOWN($source)"
        }
    }

    /**
     * Try to initialize AudioRecord with priority-based audio source selection.
     */
    private fun initializeAudioRecord(): AudioRecord? {
        Log.d(TAG, "========================================")
        Log.d(TAG, "Device: ${Build.MANUFACTURER} ${Build.MODEL} (Samsung: $isSamsungDevice)")
        Log.d(TAG, "Buffer size: $BUFFER_SIZE bytes")
        Log.d(TAG, "Trying audio sources: ${audioSourcePriority.map { getAudioSourceName(it) }}")

        for (audioSource in audioSourcePriority) {
            try {
                Log.d(TAG, "Attempting audio source: ${getAudioSourceName(audioSource)}")

                val recorder = AudioRecord(
                    audioSource,
                    SAMPLE_RATE,
                    CHANNEL_CONFIG,
                    AUDIO_FORMAT,
                    BUFFER_SIZE
                )

                if (recorder.state == AudioRecord.STATE_INITIALIZED) {
                    currentAudioSource = audioSource
                    Log.i(TAG, "✓ Successfully initialized with audio source: ${getAudioSourceName(audioSource)}")
                    return recorder
                } else {
                    Log.w(TAG, "✗ AudioRecord not initialized for ${getAudioSourceName(audioSource)}")
                    recorder.release()
                }

            } catch (e: Exception) {
                Log.w(TAG, "✗ Failed with ${getAudioSourceName(audioSource)}: ${e.message}")
            }
        }

        Log.e(TAG, "All audio sources failed!")
        return null
    }

    /**
     * Start recording audio.
     */
    override fun startRecording(callId: String): Boolean {
        if (isRecording) {
            Log.w(TAG, "Already recording")
            return false
        }

        try {
            Log.d(TAG, "Starting audio recording for call: $callId")

            // Initialize AudioRecord
            audioRecord = initializeAudioRecord()
            if (audioRecord == null) {
                Log.e(TAG, "Failed to initialize AudioRecord")
                return false
            }

            // Create recordings directory
            val recordingsDir = File(context.filesDir, "recordings")
            if (!recordingsDir.exists()) {
                recordingsDir.mkdirs()
            }

            // Start recording
            audioRecord?.startRecording()
            isRecording = true

            // Start chunk recording job
            recordingJob = recordingScope.launch {
                recordAudioChunks(callId, recordingsDir)
            }

            Log.i(TAG, "✓✓✓ Recording started successfully with ${currentAudioSource?.let { getAudioSourceName(it) }} ✓✓✓")
            Log.i(TAG, "✓✓✓ Using AudioRecord API (not MediaRecorder) ✓✓✓")
            return true

        } catch (e: Exception) {
            Log.e(TAG, "Failed to start recording", e)
            cleanup()
            return false
        }
    }

    /**
     * Record audio in chunks and save as WAV files.
     */
    private suspend fun recordAudioChunks(callId: String, recordingsDir: File) {
        var chunkIndex = 0
        val buffer = ByteArray(BUFFER_SIZE)
        val chunkSize = (SAMPLE_RATE * 2 * CHUNK_DURATION_MS) / 1000 // 16-bit PCM = 2 bytes per sample
        var currentChunkData = mutableListOf<Byte>()

        Log.d(TAG, "Starting chunk recording loop (chunk size: $chunkSize bytes)")

        try {
            while (isRecording && audioRecord != null) {
                val bytesRead = audioRecord!!.read(buffer, 0, buffer.size)

                if (bytesRead > 0) {
                    // Accumulate audio data
                    currentChunkData.addAll(buffer.take(bytesRead))

                    // Check if we have enough data for a chunk
                    if (currentChunkData.size >= chunkSize) {
                        // Save chunk as WAV file
                        val chunkFile = File(
                            recordingsDir,
                            "call_${callId}_chunk_${chunkIndex}_${System.currentTimeMillis()}.wav"
                        )

                        val chunkBytes = currentChunkData.toByteArray()
                        saveAsWav(chunkFile, chunkBytes, SAMPLE_RATE, 1)

                        Log.d(TAG, "Audio chunk ready: ${chunkFile.name}, size: ${chunkFile.length()} bytes (source: ${currentAudioSource?.let { getAudioSourceName(it) }})")

                        // Emit chunk
                        _audioChunkFlow.emit(chunkFile)

                        // Reset for next chunk
                        currentChunkData.clear()
                        chunkIndex++
                    }
                } else if (bytesRead < 0) {
                    Log.e(TAG, "Error reading audio data: $bytesRead")
                    break
                }

                // Small delay to prevent tight loop
                delay(10)
            }

            // Save any remaining data as final chunk
            if (currentChunkData.isNotEmpty()) {
                val finalFile = File(
                    recordingsDir,
                    "call_${callId}_chunk_${chunkIndex}_${System.currentTimeMillis()}.wav"
                )
                saveAsWav(finalFile, currentChunkData.toByteArray(), SAMPLE_RATE, 1)
                _audioChunkFlow.emit(finalFile)
                Log.d(TAG, "Final chunk saved: ${finalFile.name}")
            }

        } catch (e: Exception) {
            Log.e(TAG, "Error during chunk recording", e)
        }
    }

    /**
     * Save PCM data as WAV file with proper headers.
     */
    private fun saveAsWav(file: File, pcmData: ByteArray, sampleRate: Int, channels: Int) {
        FileOutputStream(file).use { fos ->
            // Write WAV header
            val totalDataLen = pcmData.size + 36
            val byteRate = sampleRate * channels * 2 // 16-bit = 2 bytes per sample

            // RIFF header
            fos.write("RIFF".toByteArray())
            fos.write(intToByteArray(totalDataLen), 0, 4)
            fos.write("WAVE".toByteArray())

            // fmt chunk
            fos.write("fmt ".toByteArray())
            fos.write(intToByteArray(16), 0, 4) // fmt chunk size
            fos.write(shortToByteArray(1), 0, 2) // audio format (1 = PCM)
            fos.write(shortToByteArray(channels.toShort()), 0, 2) // number of channels
            fos.write(intToByteArray(sampleRate), 0, 4) // sample rate
            fos.write(intToByteArray(byteRate), 0, 4) // byte rate
            fos.write(shortToByteArray((channels * 2).toShort()), 0, 2) // block align
            fos.write(shortToByteArray(16), 0, 2) // bits per sample

            // data chunk
            fos.write("data".toByteArray())
            fos.write(intToByteArray(pcmData.size), 0, 4)
            fos.write(pcmData)
        }
    }

    /**
     * Convert int to byte array (little-endian).
     */
    private fun intToByteArray(value: Int): ByteArray {
        return ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putInt(value).array()
    }

    /**
     * Convert short to byte array (little-endian).
     */
    private fun shortToByteArray(value: Short): ByteArray {
        return ByteBuffer.allocate(2).order(ByteOrder.LITTLE_ENDIAN).putShort(value).array()
    }

    /**
     * Stop recording.
     */
    override fun stopRecording(): File? {
        if (!isRecording) {
            Log.w(TAG, "Not currently recording")
            return null
        }

        Log.d(TAG, "Stopping audio recording")
        isRecording = false

        return try {
            // Wait for recording job to complete
            runBlocking {
                recordingJob?.join()
            }

            cleanup()
            null // Return null since we're using chunks

        } catch (e: Exception) {
            Log.e(TAG, "Error stopping recording", e)
            cleanup()
            null
        }
    }

    /**
     * Check if currently recording.
     */
    override fun isRecording(): Boolean = isRecording

    /**
     * Cleanup resources.
     */
    private fun cleanup() {
        try {
            audioRecord?.stop()
            audioRecord?.release()
            audioRecord = null
            currentAudioSource = null
        } catch (e: Exception) {
            Log.e(TAG, "Error releasing AudioRecord", e)
        }
    }

    /**
     * Release all resources.
     */
    override fun release() {
        stopRecording()
        recordingScope.cancel()
    }
}
