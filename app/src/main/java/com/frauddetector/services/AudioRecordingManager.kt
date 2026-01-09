package com.frauddetector.services
import android.content.Context
import android.media.MediaRecorder
import android.os.Build
import android.util.Log
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import java.io.File
import java.io.IOException

/**
 * Manages audio recording during phone calls.
 * Records audio in chunks for real-time processing.
 */
class AudioRecordingManager(private val context: Context) : AudioManager {

    companion object {
        private const val TAG = "AudioRecordingManager"
        private const val SAMPLE_RATE = 16000 // Required by Whisper
        private const val CHUNK_DURATION_MS = 5000 // 5 seconds per chunk

        // Samsung-specific workarounds
        private val isSamsungDevice: Boolean by lazy {
            Build.MANUFACTURER.equals("samsung", ignoreCase = true)
        }

        // Audio source priority order (different for Samsung)
        private val audioSourcePriority: List<Int> by lazy {
            if (isSamsungDevice) {
                // Samsung devices have issues with VOICE_COMMUNICATION
                // Try MIC first, then VOICE_COMMUNICATION, then UNPROCESSED if available
                listOfNotNull(
                    MediaRecorder.AudioSource.MIC,
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N)
                        MediaRecorder.AudioSource.UNPROCESSED
                    else null,
                    MediaRecorder.AudioSource.VOICE_COMMUNICATION,
                    MediaRecorder.AudioSource.VOICE_RECOGNITION
                )
            } else {
                // Other devices: try VOICE_COMMUNICATION first
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
    
    private var mediaRecorder: MediaRecorder? = null
    private var isRecording = false
    private var currentOutputFile: File? = null
    private val recordingScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var currentAudioSource: Int? = null
    private val systemAudioManager: android.media.AudioManager by lazy {
        context.getSystemService(Context.AUDIO_SERVICE) as android.media.AudioManager
    }

    // Flow for audio chunks
    private val _audioChunkFlow = MutableSharedFlow<File>(replay = 0)
    override val audioChunkFlow: SharedFlow<File> = _audioChunkFlow
    
    /**
     * Helper method to get audio source name for logging.
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
     * Request audio focus before recording.
     */
    private fun requestAudioFocus(): Boolean {
        return try {
            val result = systemAudioManager.requestAudioFocus(
                null,
                android.media.AudioManager.STREAM_VOICE_CALL,
                android.media.AudioManager.AUDIOFOCUS_GAIN_TRANSIENT
            )
            val success = result == android.media.AudioManager.AUDIOFOCUS_REQUEST_GRANTED
            Log.d(TAG, "Audio focus request: ${if (success) "GRANTED" else "DENIED"}")
            success
        } catch (e: Exception) {
            Log.w(TAG, "Failed to request audio focus", e)
            true // Continue anyway
        }
    }

    /**
     * Configure MediaRecorder with priority-based audio source selection.
     * Returns true if configuration succeeded, false otherwise.
     */
    private fun configureMediaRecorder(recorder: MediaRecorder, outputFile: File): Boolean {
        Log.d(TAG, "Device: ${Build.MANUFACTURER} ${Build.MODEL} (Samsung: $isSamsungDevice)")
        Log.d(TAG, "Trying audio sources in priority order: ${audioSourcePriority.map { getAudioSourceName(it) }}")

        for (audioSource in audioSourcePriority) {
            try {
                Log.d(TAG, "Attempting audio source: ${getAudioSourceName(audioSource)}")

                recorder.setAudioSource(audioSource)
                recorder.setOutputFormat(MediaRecorder.OutputFormat.THREE_GPP)
                recorder.setAudioEncoder(MediaRecorder.AudioEncoder.AMR_WB)
                recorder.setAudioSamplingRate(SAMPLE_RATE)
                recorder.setAudioChannels(1) // Mono
                recorder.setOutputFile(outputFile.absolutePath)

                recorder.prepare()

                currentAudioSource = audioSource
                Log.i(TAG, "✓ Successfully configured with audio source: ${getAudioSourceName(audioSource)}")
                return true

            } catch (e: Exception) {
                Log.w(TAG, "✗ Failed with ${getAudioSourceName(audioSource)}: ${e.message}")
                try {
                    recorder.reset()
                } catch (resetError: Exception) {
                    Log.e(TAG, "Error resetting recorder", resetError)
                }
            }
        }

        Log.e(TAG, "All audio sources failed!")
        return false
    }

    /**
     * Start recording audio.
     * @param callId Unique identifier for this call
     * @return true if recording started successfully
     */
    override fun startRecording(callId: String): Boolean {
        if (isRecording) {
            Log.w(TAG, "Already recording")
            return false
        }

        try {
            Log.d(TAG, "========================================")
            Log.d(TAG, "Starting audio recording for call: $callId")
            Log.d(TAG, "========================================")

            // Request audio focus
            requestAudioFocus()

            // Create recordings directory
            val recordingsDir = File(context.filesDir, "recordings")
            if (!recordingsDir.exists()) {
                recordingsDir.mkdirs()
            }

            // Create output file for this call
            currentOutputFile = File(recordingsDir, "call_${callId}_${System.currentTimeMillis()}.wav")

            // Initialize MediaRecorder
            mediaRecorder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                MediaRecorder(context)
            } else {
                @Suppress("DEPRECATION")
                MediaRecorder()
            }

            // Configure with priority-based audio source selection
            val configured = mediaRecorder?.let { recorder ->
                currentOutputFile?.let { file ->
                    configureMediaRecorder(recorder, file)
                } ?: false
            } ?: false

            if (!configured) {
                Log.e(TAG, "Failed to configure MediaRecorder")
                cleanup()
                return false
            }

            // Start recording
            mediaRecorder?.start()
            isRecording = true

            // Start chunk recording job
            startChunkRecording(callId)

            Log.i(TAG, "✓✓✓ Recording started successfully with ${currentAudioSource?.let { getAudioSourceName(it) }} ✓✓✓")
            return true

        } catch (e: Exception) {
            Log.e(TAG, "Failed to start recording", e)
            cleanup()
            return false
        }
    }
    
    /**
     * Start recording in chunks for real-time processing.
     */
    private fun startChunkRecording(callId: String) {
        recordingScope.launch {
            var chunkIndex = 0
            val recordingsDir = File(context.filesDir, "recordings")

            while (isRecording) {
                delay(CHUNK_DURATION_MS.toLong())

                if (!isRecording) break

                try {
                    // Stop current recorder
                    mediaRecorder?.stop()
                    mediaRecorder?.release()

                    // Emit the completed chunk
                    currentOutputFile?.let { file ->
                        if (file.exists() && file.length() > 0) {
                            Log.d(TAG, "Audio chunk ready: ${file.name}, size: ${file.length()} bytes (source: ${currentAudioSource?.let { getAudioSourceName(it) }})")
                            _audioChunkFlow.emit(file)
                        } else {
                            Log.w(TAG, "Chunk file empty or missing: ${file.name}")
                        }
                    }

                    if (!isRecording) break

                    // Start new chunk
                    chunkIndex++
                    currentOutputFile = File(recordingsDir, "call_${callId}_chunk_${chunkIndex}_${System.currentTimeMillis()}.wav")

                    // Initialize new MediaRecorder for next chunk
                    mediaRecorder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        MediaRecorder(context)
                    } else {
                        @Suppress("DEPRECATION")
                        MediaRecorder()
                    }

                    // Configure with same audio source that worked initially
                    val configured = mediaRecorder?.let { recorder ->
                        currentOutputFile?.let { file ->
                            // Try the audio source that worked first
                            currentAudioSource?.let { workingSource ->
                                try {
                                    Log.d(TAG, "Chunk $chunkIndex: Using working audio source: ${getAudioSourceName(workingSource)}")
                                    recorder.setAudioSource(workingSource)
                                    recorder.setOutputFormat(MediaRecorder.OutputFormat.THREE_GPP)
                                    recorder.setAudioEncoder(MediaRecorder.AudioEncoder.AMR_WB)
                                    recorder.setAudioSamplingRate(SAMPLE_RATE)
                                    recorder.setAudioChannels(1)
                                    recorder.setOutputFile(file.absolutePath)
                                    recorder.prepare()
                                    true
                                } catch (e: Exception) {
                                    Log.w(TAG, "Working source failed, trying all sources: ${e.message}")
                                    recorder.reset()
                                    configureMediaRecorder(recorder, file)
                                }
                            } ?: configureMediaRecorder(recorder, file)
                        } ?: false
                    } ?: false

                    if (!configured) {
                        Log.e(TAG, "Failed to configure recorder for chunk $chunkIndex")
                        break
                    }

                    mediaRecorder?.start()

                } catch (e: Exception) {
                    Log.e(TAG, "Error during chunk recording", e)
                    break
                }
            }
        }
    }
    
    /**
     * Stop recording audio.
     * @return File containing the complete recording, or null if recording failed
     */
    override fun stopRecording(): File? {
        if (!isRecording) {
            Log.w(TAG, "Not currently recording")
            return null
        }
        
        Log.d(TAG, "Stopping audio recording")
        isRecording = false
        
        return try {
            mediaRecorder?.stop()
            val finalFile = currentOutputFile
            cleanup()
            
            if (finalFile?.exists() == true && finalFile.length() > 0) {
                Log.d(TAG, "Recording stopped successfully: ${finalFile.absolutePath}")
                finalFile
            } else {
                Log.w(TAG, "Recording file is empty or doesn't exist")
                null
            }
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
     * Release audio focus after recording.
     */
    private fun releaseAudioFocus() {
        try {
            systemAudioManager.abandonAudioFocus(null)
            Log.d(TAG, "Audio focus released")
        } catch (e: Exception) {
            Log.w(TAG, "Failed to release audio focus", e)
        }
    }

    /**
     * Cleanup resources.
     */
    private fun cleanup() {
        try {
            mediaRecorder?.release()
            mediaRecorder = null
            currentAudioSource = null
        } catch (e: Exception) {
            Log.e(TAG, "Error releasing MediaRecorder", e)
        }
        releaseAudioFocus()
    }

    /**
     * Release all resources.
     */
    override fun release() {
        stopRecording()
        recordingScope.cancel()
    }
}
