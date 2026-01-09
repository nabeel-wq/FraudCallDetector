package com.frauddetector.services

import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.os.Build
import android.telecom.Call
import android.telecom.InCallService
import android.telecom.VideoProfile
import com.frauddetector.utils.AppLogger
import com.frauddetector.utils.AudioTestRunner
import com.frauddetector.ml.ClassificationResult
import com.frauddetector.ml.ModelFactory
import com.frauddetector.ui.InCallActivity
import kotlinx.coroutines.*
import java.io.File

/**
 * Core InCallService implementation for managing phone calls.
 * Handles auto-answering, audio recording, and real-time scam detection.
 */
class FraudDetectorInCallService : InCallService() {
    
    companion object {
        private const val TAG = "FraudDetectorInCallService"
        private const val AUTO_ANSWER_DELAY_MS = 1000L // Wait 1 second before auto-answering
        private const val ANALYSIS_DURATION_MS = 15000L // Analyze for 15 seconds
    }
    
    private val serviceScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private val activeCalls = mutableMapOf<String, CallHandler>()
    
    override fun onCallAdded(call: Call) {
        super.onCallAdded(call)
        
        val callId = System.currentTimeMillis().toString() // telecomCallId requires API 34+
        AppLogger.d(TAG, "Call added: $callId, state: ${call.state}")
        
        // Create call handler
        val handler = CallHandler(call, callId)
        activeCalls[callId] = handler
        
        // Register callback
        call.registerCallback(handler.callback)
        
        // Handle incoming calls
        if (call.state == Call.STATE_RINGING) {
            handler.handleIncomingCall()
        }
    }
    
    override fun onCallRemoved(call: Call) {
        super.onCallRemoved(call)
        
        val callId = activeCalls.entries.find { it.value.call == call }?.key ?: ""
        AppLogger.d(TAG, "Call removed: $callId")
        
        // Cleanup call handler
        activeCalls[callId]?.let { handler ->
            handler.cleanup()
            activeCalls.remove(callId)
        }
    }
    
    override fun onDestroy() {
        super.onDestroy()
        AppLogger.d(TAG, "Service destroyed")
        
        // Cleanup all active calls
        activeCalls.values.forEach { it.cleanup() }
        activeCalls.clear()
        serviceScope.cancel()
    }
    
    /**
     * Handles a single call instance.
     */
    private inner class CallHandler(
        internal val call: Call,
        private val callId: String
    ) {
        // Use AudioRecordManager instead of AudioRecordingManager for Samsung compatibility
        private val audioRecordingManager = AudioRecordManager(this@FraudDetectorInCallService)
        private var realtimeAnalysisService: RealtimeAnalysisService? = null
        private var analysisJob: Job? = null
        
        val callback = object : Call.Callback() {
            override fun onStateChanged(call: Call, state: Int) {
                AppLogger.d(TAG, "Call state changed: $callId, state: $state")
                
                when (state) {
                    Call.STATE_ACTIVE -> handleCallActive()
                    Call.STATE_DISCONNECTED -> handleCallDisconnected()
                }
            }
        }
        
        /**
         * Handle incoming call - auto-answer after delay.
         */
        fun handleIncomingCall() {
            AppLogger.d(TAG, "Handling incoming call: $callId")
            
            // Get caller info
            val callerNumber = call.details.handle?.schemeSpecificPart ?: "Unknown"
            AppLogger.d(TAG, "Caller: $callerNumber")
            
            // Auto-answer after delay
            serviceScope.launch {
                delay(AUTO_ANSWER_DELAY_MS)
                
                if (call.state == Call.STATE_RINGING) {
                    AppLogger.d(TAG, "Auto-answering call: $callId")
                    call.answer(VideoProfile.STATE_AUDIO_ONLY)
                    
                    // Enable speakerphone for better audio recording
                    // Note: setAudioRoute requires API 28+, AUDIO_ROUTE_SPEAKER not available
                    // Audio routing handled by system for API 29
                }
            }
        }
        
        /**
         * Handle call becoming active - start recording and analysis.
         */
        private fun handleCallActive() {
            AppLogger.d(TAG, "Call active: $callId")

            // Show in-call UI
            showInCallUI()

            // SAMSUNG WORKAROUND: Enable speakerphone to allow audio capture
            // Samsung blocks microphone during calls unless speaker is on
            enableSpeakerphone()

            // Start audio recording
            val recordingStarted = audioRecordingManager.startRecording(callId)
            if (!recordingStarted) {
                AppLogger.e(TAG, "Failed to start recording for call: $callId")
                return
            }

            // Run audio diagnostics after recording starts
            serviceScope.launch(Dispatchers.IO) {
                delay(6000) // Wait for first chunk to be recorded
                try {
                    val testRunner = AudioTestRunner(this@FraudDetectorInCallService)
                    AppLogger.d(TAG, "=== RUNNING AUDIO DIAGNOSTICS ===")
                    val diagnosticReport = testRunner.runFullDiagnostics()
                    AppLogger.d(TAG, diagnosticReport)

                    // Generate diagnosis
                    val recordingsDir = File(filesDir, "recordings")
                    val diagnosis = testRunner.generateDiagnosis(recordingsDir)
                    AppLogger.w(TAG, "DIAGNOSIS:\n$diagnosis")
                } catch (e: Exception) {
                    AppLogger.e(TAG, "Error running diagnostics", e)
                }
            }

            // Initialize ML models and start analysis
            analysisJob = serviceScope.launch {
                try {
                    AppLogger.d(TAG, "Starting model initialization for call: $callId")
                    
                    // Initialize models
                    val modelFactory = ModelFactory(this@FraudDetectorInCallService)
                    val asrModel = modelFactory.createASRModel()
                    val classifierModel = modelFactory.createClassifierModel()
                    
                    val asrInit = async { 
                        AppLogger.d(TAG, "Initializing ASR model...")
                        val result = asrModel.initialize()
                        AppLogger.d(TAG, "ASR initialization result: $result")
                        if (!result) {
                            AppLogger.e(TAG, "ASR initialization FAILED - check if model file exists in assets/models/ggml-tiny.bin")
                        }
                        result
                    }
                    val classifierInit = async { 
                        AppLogger.d(TAG, "Initializing classifier model...")
                        val result = classifierModel.initialize()
                        AppLogger.d(TAG, "Classifier initialization result: $result")
                        result
                    }
                    
                    val (asrReady, classifierReady) = awaitAll(asrInit, classifierInit)
                    
                    if (!asrReady || !classifierReady) {
                        AppLogger.e(TAG, "Model initialization failed - ASR: $asrReady, Classifier: $classifierReady")
                        // Continue without models - at least record the call
                    } else {
                        AppLogger.d(TAG, "Models initialized successfully for call: $callId")
                        
                        // Create analysis service
                        realtimeAnalysisService = RealtimeAnalysisService(
                            this@FraudDetectorInCallService,
                            asrModel,
                            classifierModel
                        )
                        
                        // Start real-time analysis
                        realtimeAnalysisService?.startAnalysis(audioRecordingManager)
                        
                        // Monitor classification results
                        launch {
                            realtimeAnalysisService?.classificationFlow?.collect { result ->
                                result?.let { handleClassificationResult(it) }
                            }
                        }
                        
                        // Wait for analysis duration, then complete
                        delay(ANALYSIS_DURATION_MS)
                        realtimeAnalysisService?.completeAnalysis()
                    }
                    
                } catch (e: Exception) {
                    AppLogger.e(TAG, "Error during analysis", e)
                }
            }
        }
        
        /**
         * Handle classification result - decide whether to reject call.
         */
        private fun handleClassificationResult(result: ClassificationResult) {
            AppLogger.d(TAG, "Classification result: isScam=${result.isScam}, confidence=${result.confidence}, action=${result.suggestedAction}")
            
            when (result.suggestedAction) {
                ClassificationResult.Action.AUTO_REJECT -> {
                    AppLogger.w(TAG, "Auto-rejecting scam call: ${result.category} (confidence: ${result.confidence})")
                    // TODO: Show notification to user about rejected scam call
                    call.disconnect()
                }
                ClassificationResult.Action.WARN_USER -> {
                    AppLogger.w(TAG, "Warning user about potential scam: ${result.category}")
                    // TODO: Show warning notification
                }
                ClassificationResult.Action.ALLOW -> {
                    AppLogger.d(TAG, "Call appears legitimate, allowing")
                    // TODO: Notify user that call is safe
                }
                ClassificationResult.Action.UNCERTAIN -> {
                    AppLogger.d(TAG, "Uncertain classification, letting user decide")
                }
            }
        }
        
        /**
         * Handle call disconnected - stop recording and cleanup.
         */
        private fun handleCallDisconnected() {
            AppLogger.d(TAG, "Call disconnected: $callId")
            cleanup()
        }
        
        /**
         * Show in-call UI activity.
         */
        private fun showInCallUI() {
            val intent = Intent(this@FraudDetectorInCallService, InCallActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
                putExtra("CALL_ID", callId)
            }
            startActivity(intent)
        }

        /**
         * Enable speakerphone to allow audio recording on Samsung devices.
         * Samsung blocks microphone during calls unless speaker mode is enabled.
         */
        private fun enableSpeakerphone() {
            try {
                val audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
                if (!audioManager.isSpeakerphoneOn) {
                    audioManager.isSpeakerphoneOn = true
                    AppLogger.i(TAG, "✓ Speakerphone enabled for audio recording (Samsung workaround)")
                } else {
                    AppLogger.d(TAG, "Speakerphone already enabled")
                }
            } catch (e: Exception) {
                AppLogger.e(TAG, "Failed to enable speakerphone", e)
            }
        }

        /**
         * Disable speakerphone after call ends.
         */
        private fun disableSpeakerphone() {
            try {
                val audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
                if (audioManager.isSpeakerphoneOn) {
                    audioManager.isSpeakerphoneOn = false
                    AppLogger.d(TAG, "Speakerphone disabled")
                }
            } catch (e: Exception) {
                AppLogger.e(TAG, "Failed to disable speakerphone", e)
            }
        }

        /**
         * Cleanup resources for this call.
         */
        fun cleanup() {
            AppLogger.d(TAG, "Cleaning up call handler: $callId")

            // Disable speakerphone
            disableSpeakerphone()

            // Stop recording
            audioRecordingManager.stopRecording()
            audioRecordingManager.release()

            // Cancel analysis
            analysisJob?.cancel()
            realtimeAnalysisService?.release()

            // Unregister callback
            call.unregisterCallback(callback)
        }
    }
}
