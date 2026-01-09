package com.frauddetector.ml

/**
 * Configuration for the real-time processing pipeline.
 * Allows easy experimentation with different processing strategies.
 */
data class ProcessingConfig(
    /**
     * Duration of each audio chunk in milliseconds.
     * Default: 5000ms (5 seconds)
     * 
     * Shorter chunks = faster detection, more processing overhead
     * Longer chunks = better context, slower detection
     */
    val chunkDurationMs: Long = 5000,
    
    /**
     * Number of chunks to keep in the sliding context window.
     * Default: 3 chunks (15 seconds of context)
     * 
     * More chunks = better context understanding
     * Fewer chunks = faster processing, less memory
     */
    val contextWindowChunks: Int = 3,
    
    /**
     * How often to run classification (in chunks).
     * Default: 1 (classify every chunk)
     * 
     * 1 = classify after every chunk
     * 2 = classify after every 2 chunks
     */
    val classificationIntervalChunks: Int = 1,
    
    /**
     * Confidence threshold for early exit.
     * If aggregated confidence exceeds this, stop processing and make decision.
     * Default: 0.95 (95% confidence)
     */
    val earlyExitThreshold: Float = 0.95f,
    
    /**
     * Enable adaptive chunk duration based on speech activity.
     * Default: false
     */
    val adaptiveChunkDuration: Boolean = false,
    
    /**
     * Minimum chunk duration when adaptive mode is enabled.
     * Default: 3000ms (3 seconds)
     */
    val minChunkDurationMs: Long = 3000,
    
    /**
     * Maximum chunk duration when adaptive mode is enabled.
     * Default: 7000ms (7 seconds)
     */
    val maxChunkDurationMs: Long = 7000
) {
    companion object {
        /**
         * Fast mode: Shorter chunks, smaller context, frequent classification.
         * Good for quick scam detection, higher battery usage.
         */
        val FAST_MODE = ProcessingConfig(
            chunkDurationMs = 3000,
            contextWindowChunks = 2,
            classificationIntervalChunks = 1,
            earlyExitThreshold = 0.90f
        )
        
        /**
         * Balanced mode: Default settings.
         * Good balance between accuracy and performance.
         */
        val BALANCED_MODE = ProcessingConfig(
            chunkDurationMs = 5000,
            contextWindowChunks = 3,
            classificationIntervalChunks = 1,
            earlyExitThreshold = 0.95f
        )
        
        /**
         * Thorough mode: Longer chunks, larger context, less frequent classification.
         * Best accuracy, lower battery usage, slower detection.
         */
        val THOROUGH_MODE = ProcessingConfig(
            chunkDurationMs = 10000,
            contextWindowChunks = 5,
            classificationIntervalChunks = 2,
            earlyExitThreshold = 0.98f
        )
        
        /**
         * Adaptive mode: Adjusts chunk duration based on speech activity.
         * Experimental feature.
         */
        val ADAPTIVE_MODE = ProcessingConfig(
            chunkDurationMs = 5000,
            contextWindowChunks = 3,
            classificationIntervalChunks = 1,
            earlyExitThreshold = 0.95f,
            adaptiveChunkDuration = true
        )
    }
    
    /**
     * Get total context duration in milliseconds.
     */
    fun getTotalContextDurationMs(): Long {
        return chunkDurationMs * contextWindowChunks
    }
    
    /**
     * Get total context duration in seconds.
     */
    fun getTotalContextDurationSeconds(): Int {
        return (getTotalContextDurationMs() / 1000).toInt()
    }
}
