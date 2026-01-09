package com.frauddetector.services

import android.content.Context
import com.frauddetector.utils.AppLogger
import com.frauddetector.utils.AudioDiagnostics
import com.frauddetector.ml.ASRModel
import com.frauddetector.ml.ClassificationResult
import com.frauddetector.ml.ClassifierModel
import com.frauddetector.ml.ProcessingConfig
import com.frauddetector.utils.CircularBuffer
import com.frauddetector.utils.ConfidenceTracker
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.io.File

/**
 * Modular real-time analysis service with configurable processing pipeline.
 * Orchestrates ASR transcription and scam classification with sliding context window.
 */
class RealtimeAnalysisService(
    private val context: Context,
    private val asrModel: ASRModel,
    private val classifierModel: ClassifierModel,
    private val advancedModel: ClassifierModel? = null,  // Optional FLAN-T5
    private val config: ProcessingConfig = ProcessingConfig.BALANCED_MODE
) {
    
    companion object {
        private const val TAG = "RealtimeAnalysisService"
    }
    
    private val analysisScope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    
    // Sliding context window for transcripts
    private val contextWindow = CircularBuffer<String>(config.contextWindowChunks)
    
    // Confidence tracking with weighted averaging
    private val confidenceTracker = ConfidenceTracker(ConfidenceTracker.WeightingStrategy.LINEAR)
    
    // Chunk counter for classification interval
    private var chunkCount = 0
    
    // State flows for UI updates
    private val _transcriptFlow = MutableStateFlow("")
    val transcriptFlow: StateFlow<String> = _transcriptFlow
    
    private val _classificationFlow = MutableStateFlow<ClassificationResult?>(null)
    val classificationFlow: StateFlow<ClassificationResult?> = _classificationFlow
    
    private val _analysisStateFlow = MutableStateFlow(AnalysisState.IDLE)
    val analysisStateFlow: StateFlow<AnalysisState> = _analysisStateFlow
    
    private val _contextInfoFlow = MutableStateFlow(ContextInfo(0, 0, 0f))
    val contextInfoFlow: StateFlow<ContextInfo> = _contextInfoFlow
    
    enum class AnalysisState {
        IDLE,
        TRANSCRIBING,
        CLASSIFYING,
        COMPLETED,
        EARLY_EXIT,
        ERROR
    }
    
    data class ContextInfo(
        val chunksProcessed: Int,
        val contextDurationSeconds: Int,
        val aggregatedConfidence: Float
    )
    
    /**
     * Start real-time analysis.
     * Supports both AudioRecordingManager and AudioRecordManager via AudioManager interface.
     */
    fun startAnalysis(audioManager: AudioManager) {
        AppLogger.d(TAG, "Starting real-time analysis with config: $config")
        _analysisStateFlow.value = AnalysisState.TRANSCRIBING
        reset()

        // Collect audio chunks and process them
        analysisScope.launch {
            audioManager.audioChunkFlow.collect { audioFile ->
                processAudioChunk(audioFile)
            }
        }
    }
    
    /**
     * Process a single audio chunk through the pipeline.
     */
    private suspend fun processAudioChunk(audioFile: File) {
        AppLogger.d(TAG, "Processing audio chunk: ${audioFile.name}")

        // DIAGNOSTICS: Analyze audio file before processing
        withContext(Dispatchers.IO) {
            val quickCheck = AudioDiagnostics.quickAudioCheck(audioFile)
            AppLogger.d(TAG, "Audio quick check: $quickCheck")

            // Full analysis for first chunk and any suspicious chunks
            if (chunkCount < 2 || quickCheck.contains("SILENT")) {
                val analysis = AudioDiagnostics.analyzeAudioFile(audioFile)
                AppLogger.d(TAG, analysis.toDetailedString())

                if (analysis.isSilent) {
                    AppLogger.w(TAG, "⚠️ DETECTED SILENT AUDIO! This indicates a recording issue.")
                }
            }
        }

        try {
            // Step 1: Transcribe audio chunk
            _analysisStateFlow.value = AnalysisState.TRANSCRIBING
            val chunkTranscript = asrModel.transcribe(audioFile, "en")

            if (chunkTranscript.isNullOrBlank()) {
                AppLogger.w(TAG, "Empty transcription for chunk: ${audioFile.name}")
                return
            }
            
            // Step 2: Add to sliding context window
            contextWindow.add(chunkTranscript.trim())
            chunkCount++
            
            // Step 3: Update accumulated transcript
            val currentTranscript = contextWindow.joinToString(" ")
            _transcriptFlow.value = currentTranscript
            

            
            AppLogger.d(TAG, "Context window: ${contextWindow.size} chunks, ${currentTranscript.length} chars")
            AppLogger.d(TAG, "Latest chunk: ${chunkTranscript.take(100)}...")
            
            // Step 4: Should we classify now?
            if (shouldClassify()) {
                classifyContext(currentTranscript)
            }
            
            // Step 5: Update context info
            updateContextInfo()
            
        } catch (e: Exception) {
            AppLogger.e(TAG, "Error processing audio chunk", e)
            _analysisStateFlow.value = AnalysisState.ERROR
        }
    }
    
    /**
     * Determine if we should run classification now.
     */
    private fun shouldClassify(): Boolean {
        // Must have at least one chunk
        if (contextWindow.isEmpty()) return false
        
        // Check classification interval
        return chunkCount % config.classificationIntervalChunks == 0
    }
    
    /**
     * Classify the current context.
     */
    private suspend fun classifyContext(transcript: String) {
        AppLogger.d(TAG, "Classifying context (${transcript.length} chars)")
        _analysisStateFlow.value = AnalysisState.CLASSIFYING
        
        try {
            // Use MiniLM classifier (always runs)
            val result = classifierModel.classifyIncremental(
                text = transcript,
                isComplete = false
            )
            
            _classificationFlow.value = result
            confidenceTracker.addScore(result.confidence)
            
            AppLogger.d(TAG, "Classification: isScam=${result.isScam}, confidence=${result.confidence}, action=${result.suggestedAction}")
            
            // Check for early exit
            val aggregatedConfidence = confidenceTracker.getAggregatedConfidence()
            if (aggregatedConfidence >= config.earlyExitThreshold) {
                AppLogger.d(TAG, "Early exit triggered: aggregated confidence = $aggregatedConfidence")
                _analysisStateFlow.value = AnalysisState.EARLY_EXIT
                
                // Update with final result
                _classificationFlow.value = result.copy(
                    confidence = aggregatedConfidence,
                    reasoning = result.reasoning + " (High confidence after ${chunkCount} chunks)"
                )
                return
            }
            
            // Use advanced model if available and confidence is uncertain
            if (advancedModel != null && result.confidence < 0.85f) {
                AppLogger.d(TAG, "Using advanced model for uncertain case")
                val advancedResult = advancedModel.classify(transcript)
                _classificationFlow.value = advancedResult
                confidenceTracker.addScore(advancedResult.confidence)
            }
            
            _analysisStateFlow.value = AnalysisState.TRANSCRIBING
            
        } catch (e: Exception) {
            AppLogger.e(TAG, "Error during classification", e)
            _analysisStateFlow.value = AnalysisState.ERROR
        }
    }
    
    /**
     * Complete analysis with final classification.
     */
    suspend fun completeAnalysis() {
        AppLogger.d(TAG, "Completing analysis")
        
        val finalTranscript = contextWindow.joinToString(" ")
        if (finalTranscript.length >= 20) {
            _analysisStateFlow.value = AnalysisState.CLASSIFYING
            
            val finalResult = classifierModel.classify(finalTranscript)
            val aggregatedConfidence = confidenceTracker.getAggregatedConfidence()
            
            // Use aggregated confidence for final result
            _classificationFlow.value = finalResult.copy(
                confidence = aggregatedConfidence,
                reasoning = finalResult.reasoning + " (Final analysis after ${chunkCount} chunks)"
            )
            
            _analysisStateFlow.value = AnalysisState.COMPLETED
        } else {
            AppLogger.w(TAG, "Transcript too short for classification: ${finalTranscript.length} chars")
            _analysisStateFlow.value = AnalysisState.COMPLETED
        }
    }
    
    /**
     * Update context information for UI.
     */
    private fun updateContextInfo() {
        _contextInfoFlow.value = ContextInfo(
            chunksProcessed = chunkCount,
            contextDurationSeconds = (chunkCount * config.chunkDurationMs / 1000).toInt(),
            aggregatedConfidence = confidenceTracker.getAggregatedConfidence()
        )
    }
    
    /**
     * Get current accumulated transcript.
     */
    fun getCurrentTranscript(): String = contextWindow.joinToString(" ")
    
    /**
     * Get current classification result.
     */
    fun getCurrentClassification(): ClassificationResult? = _classificationFlow.value
    
    /**
     * Get confidence statistics.
     */
    fun getConfidenceStatistics(): ConfidenceTracker.Statistics {
        return confidenceTracker.getStatistics()
    }
    
    /**
     * Get processing configuration.
     */
    fun getConfig(): ProcessingConfig = config
    
    /**
     * Reset analysis state.
     */
    fun reset() {
        AppLogger.d(TAG, "Resetting analysis state")
        contextWindow.clear()
        confidenceTracker.clear()
        chunkCount = 0
        _transcriptFlow.value = ""
        _classificationFlow.value = null
        _analysisStateFlow.value = AnalysisState.IDLE
        _contextInfoFlow.value = ContextInfo(0, 0, 0f)
    }
    
    /**
     * Release resources.
     */
    fun release() {
        analysisScope.cancel()
    }
}
