package com.frauddetector.utils

import kotlin.math.pow

/**
 * Tracks confidence scores over time and provides aggregated confidence.
 * Uses weighted averaging to give more importance to recent scores.
 */
class ConfidenceTracker(
    private val weightingStrategy: WeightingStrategy = WeightingStrategy.LINEAR
) {
    private val scores = mutableListOf<Float>()
    
    enum class WeightingStrategy {
        /**
         * All scores have equal weight.
         */
        UNIFORM,
        
        /**
         * Recent scores have linearly increasing weight.
         * Weight = index + 1
         */
        LINEAR,
        
        /**
         * Recent scores have exponentially increasing weight.
         * Weight = 2^index
         */
        EXPONENTIAL,
        
        /**
         * Only the most recent score matters.
         */
        LATEST_ONLY
    }
    
    /**
     * Add a new confidence score.
     */
    fun addScore(score: Float) {
        require(score in 0f..1f) { "Score must be between 0 and 1" }
        scores.add(score)
    }
    
    /**
     * Get aggregated confidence based on weighting strategy.
     */
    fun getAggregatedConfidence(): Float {
        if (scores.isEmpty()) return 0f
        
        return when (weightingStrategy) {
            WeightingStrategy.UNIFORM -> scores.average().toFloat()
            
            WeightingStrategy.LINEAR -> {
                val weightedSum = scores.mapIndexed { index, score ->
                    score * (index + 1)
                }.sum()
                val totalWeight = (1..scores.size).sum()
                weightedSum / totalWeight
            }
            
            WeightingStrategy.EXPONENTIAL -> {
                val weightedSum = scores.mapIndexed { index, score ->
                    score * 2f.pow(index)
                }.sum()
                val totalWeight = (0 until scores.size).sumOf { 2f.pow(it).toDouble() }.toFloat()
                weightedSum / totalWeight
            }
            
            WeightingStrategy.LATEST_ONLY -> scores.last()
        }
    }
    
    /**
     * Get the trend of confidence scores.
     * Returns positive if confidence is increasing, negative if decreasing.
     */
    fun getTrend(): Float {
        if (scores.size < 2) return 0f
        
        val recent = scores.takeLast(3)
        if (recent.size < 2) return 0f
        
        // Simple linear regression slope
        val n = recent.size
        val sumX = (0 until n).sum()
        val sumY = recent.sum()
        val sumXY = recent.mapIndexed { index, score -> index * score }.sum()
        val sumX2 = (0 until n).sumOf { it * it }
        
        val slope = (n * sumXY - sumX * sumY) / (n * sumX2 - sumX * sumX)
        return slope
    }
    
    /**
     * Check if confidence is consistently high (above threshold).
     */
    fun isConsistentlyHigh(threshold: Float = 0.8f, minSamples: Int = 2): Boolean {
        if (scores.size < minSamples) return false
        return scores.takeLast(minSamples).all { it >= threshold }
    }
    
    /**
     * Check if confidence is consistently low (below threshold).
     */
    fun isConsistentlyLow(threshold: Float = 0.3f, minSamples: Int = 2): Boolean {
        if (scores.size < minSamples) return false
        return scores.takeLast(minSamples).all { it <= threshold }
    }
    
    /**
     * Get the number of scores tracked.
     */
    fun getScoreCount(): Int = scores.size
    
    /**
     * Get all scores.
     */
    fun getAllScores(): List<Float> = scores.toList()
    
    /**
     * Clear all scores.
     */
    fun clear() {
        scores.clear()
    }
    
    /**
     * Get statistics about the scores.
     */
    fun getStatistics(): Statistics {
        if (scores.isEmpty()) {
            return Statistics(0f, 0f, 0f, 0f, 0)
        }
        
        return Statistics(
            mean = scores.average().toFloat(),
            min = scores.minOrNull() ?: 0f,
            max = scores.maxOrNull() ?: 0f,
            stdDev = calculateStdDev(),
            count = scores.size
        )
    }
    
    private fun calculateStdDev(): Float {
        if (scores.size < 2) return 0f
        
        val mean = scores.average()
        val variance = scores.map { (it - mean).pow(2) }.average()
        return kotlin.math.sqrt(variance).toFloat()
    }
    
    data class Statistics(
        val mean: Float,
        val min: Float,
        val max: Float,
        val stdDev: Float,
        val count: Int
    )
}
