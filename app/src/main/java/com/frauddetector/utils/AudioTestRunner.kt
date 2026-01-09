package com.frauddetector.utils

import android.content.Context
import android.media.MediaRecorder
import android.os.Build
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Test runner for audio recording and processing diagnostics
 */
class AudioTestRunner(private val context: Context) {

    private val TAG = "AudioTestRunner"

    /**
     * Run comprehensive audio diagnostics on all recording files
     */
    suspend fun runFullDiagnostics(): String = withContext(Dispatchers.IO) {
        val report: StringBuilder = StringBuilder()
        report.appendLine("====================================")
        report.appendLine("AUDIO DIAGNOSTICS REPORT")
        report.appendLine("====================================")
        report.appendLine()

        // 1. Check recordings directory
        report.appendLine("--- Recordings Directory ---")
        val recordingsDir: File = File(context.filesDir, "recordings")
        if (!recordingsDir.exists()) {
            report.appendLine("❌ Recordings directory does not exist!")
            report.appendLine("Creating directory...")
            recordingsDir.mkdirs()
        } else {
            report.appendLine("✓ Directory exists: ${recordingsDir.absolutePath}")
        }

        val filesArray: Array<File> = recordingsDir.listFiles() ?: emptyArray()
        val files: List<File> = filesArray.sortedByDescending { it.lastModified() }
        report.appendLine("Files found: ${files.size}")
        report.appendLine()

        // 2. Analyze recent files
        report.appendLine("--- Recent Files Analysis ---")
        val filesToAnalyze: List<File> = files.take(5)
        if (filesToAnalyze.isEmpty()) {
            report.appendLine("⚠️ No files to analyze. Record a call first.")
        } else {
            filesToAnalyze.forEachIndexed { index: Int, file: File ->
                report.appendLine("File ${index + 1}: ${file.name}")
                report.appendLine("  Size: ${file.length()} bytes")
                report.appendLine("  Age: ${(System.currentTimeMillis() - file.lastModified()) / 1000}s")
                report.appendLine("  Quick check: ${AudioDiagnostics.quickAudioCheck(file)}")
                report.appendLine()
            }
        }

        // 3. Detailed analysis of most recent file
        if (files.isNotEmpty()) {
            report.appendLine("--- Detailed Analysis (Most Recent) ---")
            val mostRecent: File = files.first()
            val analysis: AudioDiagnostics.AudioAnalysis = AudioDiagnostics.analyzeAudioFile(mostRecent)
            report.appendLine(analysis.toDetailedString())

            // 4. Sample pattern analysis
            report.appendLine("--- Sample Pattern Analysis ---")
            val pattern: String = AudioDiagnostics.analyzeSamplePattern(mostRecent, 50)
            report.appendLine(pattern)
        }

        // 5. Test decoder
        if (files.isNotEmpty()) {
            report.appendLine("--- AudioDecoder Test ---")
            val testFile: File = files.first()
            val decoderWorks: Boolean = AudioDiagnostics.testAudioDecoder(testFile)
            report.appendLine("AudioDecoder status: ${if (decoderWorks) "✓ WORKING" else "❌ FAILED"}")
            report.appendLine()
        }

        // 6. Permissions check
        report.appendLine("--- Permissions Status ---")
        report.appendLine("Record Audio: (check in app settings)")
        report.appendLine("Read Phone State: (check in app settings)")
        report.appendLine()

        report.appendLine("====================================")
        report.appendLine("END OF REPORT")
        report.appendLine("====================================")

        val fullReport: String = report.toString()
        AppLogger.d(TAG, fullReport)
        fullReport
    }

    /**
     * Test recording with a simple 3-second capture
     */
    suspend fun testBasicRecording(): String = withContext(Dispatchers.IO) {
        val report: StringBuilder = StringBuilder()
        report.appendLine("--- Basic Recording Test ---")

        val testDir: File = File(context.filesDir, "recordings/test")
        testDir.mkdirs()

        val testFile: File = File(testDir, "test_recording_${System.currentTimeMillis()}.3gp")

        var recorder: MediaRecorder? = null
        try {
            report.appendLine("Starting 3-second test recording...")

            recorder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                MediaRecorder(context)
            } else {
                @Suppress("DEPRECATION")
                MediaRecorder()
            }

            recorder.apply {
                setAudioSource(MediaRecorder.AudioSource.MIC)
                setOutputFormat(MediaRecorder.OutputFormat.THREE_GPP)
                setAudioEncoder(MediaRecorder.AudioEncoder.AMR_WB)
                setAudioSamplingRate(16000)
                setAudioChannels(1)
                setOutputFile(testFile.absolutePath)
                prepare()
                start()
            }

            // Record for 3 seconds
            delay(3000)

            recorder.stop()
            recorder.release()

            report.appendLine("✓ Recording completed: ${testFile.name}")
            report.appendLine("  File size: ${testFile.length()} bytes")

            // Analyze the test recording
            val quickCheck: String = AudioDiagnostics.quickAudioCheck(testFile)
            report.appendLine("  Quick check: $quickCheck")

            if (quickCheck.contains("SILENT")) {
                report.appendLine()
                report.appendLine("❌ TEST FAILED: Recording is silent!")
                report.appendLine("Possible issues:")
                report.appendLine("  1. Microphone permission not granted")
                report.appendLine("  2. Audio source is not working")
                report.appendLine("  3. Device microphone hardware issue")
            } else {
                report.appendLine("✓ TEST PASSED: Recording contains audio")
            }

            // Clean up
            testFile.delete()

        } catch (e: Exception) {
            report.appendLine("❌ Recording failed: ${e.message}")
            e.printStackTrace()
        } finally {
            try {
                recorder?.release()
            } catch (e: Exception) {
                // Ignore
            }
        }

        val testReport: String = report.toString()
        AppLogger.d(TAG, testReport)
        testReport
    }

    /**
     * Test during an actual call (must be called while in-call)
     */
    suspend fun testInCallRecording(): String = withContext(Dispatchers.IO) {
        val report: StringBuilder = StringBuilder()
        report.appendLine("--- In-Call Recording Test ---")

        val testDir: File = File(context.filesDir, "recordings/test")
        testDir.mkdirs()

        val testFile: File = File(testDir, "in_call_test_${System.currentTimeMillis()}.3gp")

        var recorder: MediaRecorder? = null
        try {
            report.appendLine("Testing VOICE_COMMUNICATION audio source...")

            recorder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                MediaRecorder(context)
            } else {
                @Suppress("DEPRECATION")
                MediaRecorder()
            }

            recorder.apply {
                try {
                    setAudioSource(MediaRecorder.AudioSource.VOICE_COMMUNICATION)
                    report.appendLine("✓ VOICE_COMMUNICATION available")
                } catch (e: Exception) {
                    report.appendLine("❌ VOICE_COMMUNICATION failed, using MIC")
                    reset()
                    setAudioSource(MediaRecorder.AudioSource.MIC)
                }

                setOutputFormat(MediaRecorder.OutputFormat.THREE_GPP)
                setAudioEncoder(MediaRecorder.AudioEncoder.AMR_WB)
                setAudioSamplingRate(16000)
                setAudioChannels(1)
                setOutputFile(testFile.absolutePath)
                prepare()
                start()
            }

            // Record for 5 seconds
            report.appendLine("Recording for 5 seconds...")
            delay(5000)

            recorder.stop()
            recorder.release()

            report.appendLine("✓ Recording completed: ${testFile.name}")
            report.appendLine("  File size: ${testFile.length()} bytes")

            // Analyze the test recording
            val analysis: AudioDiagnostics.AudioAnalysis = AudioDiagnostics.analyzeAudioFile(testFile)
            report.appendLine(analysis.toDetailedString())

            if (analysis.isSilent) {
                report.appendLine()
                report.appendLine("❌ CRITICAL ISSUE: In-call recording is SILENT!")
                report.appendLine()
                report.appendLine("This is the root cause of [BLANK_AUDIO] transcriptions.")
                report.appendLine()
                report.appendLine("Android 10+ restrictions:")
                report.appendLine("  - VOICE_COMMUNICATION may not capture remote audio")
                report.appendLine("  - Call recording requires special permissions")
                report.appendLine("  - Some devices block call recording entirely")
                report.appendLine()
                report.appendLine("Possible solutions:")
                report.appendLine("  1. Use Accessibility Service for audio capture")
                report.appendLine("  2. Use VOICE_DOWNLINK (requires root/system app)")
                report.appendLine("  3. Process only microphone audio (user side only)")
            } else {
                report.appendLine("✓ In-call recording working!")
            }

            // Keep file for inspection
            report.appendLine()
            report.appendLine("Test file saved at: ${testFile.absolutePath}")

        } catch (e: Exception) {
            report.appendLine("❌ In-call recording failed: ${e.message}")
            e.printStackTrace()
        } finally {
            try {
                recorder?.release()
            } catch (e: Exception) {
                // Ignore
            }
        }

        val testReport: String = report.toString()
        AppLogger.d(TAG, testReport)
        testReport
    }

    /**
     * Generate a summary diagnosis
     */
    fun generateDiagnosis(recordingsDir: File): String {
        val filesArray: Array<File> = recordingsDir.listFiles() ?: emptyArray()
        val files: List<File> = filesArray.sortedByDescending { it.lastModified() }

        if (files.isEmpty()) {
            return "No recordings found. Cannot diagnose."
        }

        val recentFiles: List<File> = files.take(3)
        val analyses: List<AudioDiagnostics.AudioAnalysis> = recentFiles.map { AudioDiagnostics.analyzeAudioFile(it) }

        val allSilent: Boolean = analyses.all { it.isSilent }
        val mostlySilent: Boolean = analyses.count { it.isSilent } > analyses.size / 2

        return when {
            allSilent -> {
                """
                ❌ DIAGNOSIS: AUDIO RECORDING NOT WORKING

                All analyzed files are silent (RMS < 0.01).

                ROOT CAUSE: Android is not capturing audio during calls.

                This is due to Android 10+ restrictions on call recording:
                - MediaRecorder.AudioSource.VOICE_COMMUNICATION only captures local mic
                - Remote audio (other party) cannot be recorded without special permissions
                - Many devices completely block call recording for privacy

                SOLUTION REQUIRED:
                1. Use only local microphone audio (analyze user speech only)
                2. Implement Accessibility Service for audio capture (requires user setup)
                3. Request CAPTURE_AUDIO_OUTPUT permission (system app only)
                """.trimIndent()
            }
            mostlySilent -> {
                """
                ⚠️ DIAGNOSIS: INTERMITTENT AUDIO RECORDING

                Some files are silent, others have audio.

                Possible causes:
                - Timing issues (recording starts before call audio begins)
                - Audio source switching issues
                - Device-specific quirks

                RECOMMENDATION: Add delays and retry logic.
                """.trimIndent()
            }
            else -> {
                """
                ✓ DIAGNOSIS: AUDIO RECORDING WORKING

                Audio files contain actual audio data.

                If transcriptions still show [BLANK_AUDIO]:
                - Check AudioDecoder implementation
                - Check Whisper model initialization
                - Check sample rate conversion
                """.trimIndent()
            }
        }
    }
}
