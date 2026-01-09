package com.frauddetector.services

import kotlinx.coroutines.flow.SharedFlow
import java.io.File

/**
 * Common interface for audio recording managers.
 * Both AudioRecordingManager and AudioRecordManager implement this.
 */
interface AudioManager {
    /**
     * Flow of audio chunk files.
     */
    val audioChunkFlow: SharedFlow<File>

    /**
     * Start recording audio.
     * @param callId Unique identifier for this call
     * @return true if recording started successfully
     */
    fun startRecording(callId: String): Boolean

    /**
     * Stop recording audio.
     * @return File containing the complete recording, or null if recording failed
     */
    fun stopRecording(): File?

    /**
     * Check if currently recording.
     */
    fun isRecording(): Boolean

    /**
     * Release all resources.
     */
    fun release()
}
