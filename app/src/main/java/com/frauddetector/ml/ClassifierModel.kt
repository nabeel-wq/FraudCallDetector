package com.frauddetector.ml

/**
 * Data class representing the result of scam classification.
 */
data class ClassificationResult(
    val isScam: Boolean,
    val confidence: Float, // 0.0 to 1.0
    val category: String, // e.g., "Lottery Scam", "Legitimate", "Phishing"
    val reasoning: String, // Explanation of why it was classified as scam/legitimate
    val suggestedAction: Action
) {
    enum class Action {
        AUTO_REJECT,    // High confidence scam - auto reject
        WARN_USER,      // Likely scam - warn user but let them decide
        ALLOW,          // Legitimate call - allow through
        UNCERTAIN       // Not enough confidence - let user decide
    }
}

/**
 * Interface for scam classification models.
 * Allows easy swapping between different classifier implementations (FLAN-T5, BERT, Gemma, etc.)
 */
interface ClassifierModel {
    /**
     * Initialize the classifier model. Should be called once before classification.
     * @return true if initialization successful, false otherwise
     */
    suspend fun initialize(): Boolean
    
    /**
     * Classify text as scam or legitimate.
     * @param text Transcribed call text to classify
     * @param context Optional context (caller ID, time of day, etc.)
     * @return Classification result with confidence and reasoning
     */
    suspend fun classify(text: String, context: Map<String, String> = emptyMap()): ClassificationResult
    
    /**
     * Classify text with streaming/incremental updates.
     * Useful for real-time classification as transcription progresses.
     * @param text Current accumulated transcription
     * @param isComplete Whether this is the final text or still being transcribed
     * @return Classification result (may be preliminary if isComplete=false)
     */
    suspend fun classifyIncremental(text: String, isComplete: Boolean = false): ClassificationResult
    
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
     * Get confidence threshold for auto-rejection.
     * Calls with scam confidence above this threshold will be auto-rejected.
     */
    fun getAutoRejectThreshold(): Float
    
    /**
     * Set confidence threshold for auto-rejection.
     */
    fun setAutoRejectThreshold(threshold: Float)
}
