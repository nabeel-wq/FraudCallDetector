package com.frauddetector.ml

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * FLAN-T5 Small implementation of classifier model.
 * Currently uses enhanced pattern-based classification with scam indicators.
 * 
 * Note: Full TFLite integration pending. This implementation provides
 * robust scam detection using linguistic patterns and keywords.
 */
class FlanT5Classifier(private val context: Context) : ClassifierModel {
    
    companion object {
        private const val TAG = "FlanT5Classifier"
        private const val DEFAULT_AUTO_REJECT_THRESHOLD = 0.8f
    }
    
    private var initialized = false
    private var autoRejectThreshold = DEFAULT_AUTO_REJECT_THRESHOLD
    
    // Enhanced scam indicators with weighted scoring
    private val scamPatterns = mapOf(
        // Financial fraud (high weight)
        "lottery|prize|winner|won|congratulations" to 0.3f,
        "suspended|account|verify|urgent|immediately" to 0.35f,
        "bank|credit card|ssn|social security|debit card" to 0.3f,
        "refund|tax|irs|arrest|warrant|legal action" to 0.35f,
        
        // Tech support scams (high weight)
        "microsoft|windows|apple|tech support|computer|virus|infected|malware" to 0.3f,
        "remote access|teamviewer|anydesk" to 0.4f,
        
        // Investment scams (medium-high weight)
        "investment|guaranteed|profit|returns|double your money|crypto|bitcoin" to 0.25f,
        "limited time|act now|don't miss|exclusive offer" to 0.2f,
        
        // Impersonation (high weight)
        "government|official|officer|department|federal|police" to 0.25f,
        "grandson|grandchild|family emergency|hospital|accident" to 0.3f,
        
        // Pressure tactics (medium weight)
        "final notice|last chance|expire|deadline|today only" to 0.2f,
        "confirm|click|link|website|download" to 0.15f,
        
        // Payment requests (high weight)
        "gift card|itunes|google play|amazon card|prepaid|wire transfer|western union|moneygram" to 0.4f,
        "pay|payment|send money|transfer|cash" to 0.15f
    )
    
    // Legitimate call indicators
    private val legitimatePatterns = listOf(
        "meeting|appointment|schedule|calendar",
        "project|work|office|colleague|team",
        "family|friend|relative|personal",
        "delivery|package|order|shipping",
        "reservation|booking|confirmation",
        "thank you|thanks|appreciate|grateful"
    )
    
    override suspend fun initialize(): Boolean = withContext(Dispatchers.IO) {
        if (initialized) {
            Log.d(TAG, "Classifier already initialized")
            return@withContext true
        }
        
        try {
            Log.d(TAG, "Initializing FLAN-T5 classifier (pattern-based)...")
            initialized = true
            Log.d(TAG, "Classifier initialized successfully")
            Log.d(TAG, "Loaded ${scamPatterns.size} scam patterns")
            return@withContext true
            
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize classifier", e)
            return@withContext false
        }
    }
    
    override suspend fun classify(text: String, context: Map<String, String>): ClassificationResult = 
        withContext(Dispatchers.Default) {
            if (!initialized) {
                Log.e(TAG, "Classifier not initialized. Call initialize() first.")
                return@withContext createFallbackResult()
            }
            
            try {
                Log.d(TAG, "Classifying text (${text.length} chars): ${text.take(100)}...")
                
                val result = analyzeText(text, context)
                
                Log.d(TAG, "Classification: isScam=${result.isScam}, confidence=${result.confidence}, category=${result.category}")
                return@withContext result
                
            } catch (e: Exception) {
                Log.e(TAG, "Classification failed", e)
                return@withContext createFallbackResult()
            }
        }
    
    override suspend fun classifyIncremental(text: String, isComplete: Boolean): ClassificationResult {
        val result = classify(text)
        
        return if (!isComplete && result.confidence > 0.5f) {
            // Reduce confidence for incomplete text
            result.copy(
                confidence = result.confidence * 0.75f,
                suggestedAction = when {
                    result.isScam && result.confidence * 0.75f > autoRejectThreshold -> 
                        ClassificationResult.Action.WARN_USER
                    else -> ClassificationResult.Action.UNCERTAIN
                }
            )
        } else {
            result
        }
    }
    
    /**
     * Analyze text using pattern matching and linguistic analysis.
     */
    private fun analyzeText(text: String, context: Map<String, String>): ClassificationResult {
        val lowerText = text.lowercase()
        val words = lowerText.split(Regex("\\s+"))
        
        // Calculate scam score using weighted patterns
        var scamScore = 0f
        val matchedPatterns = mutableListOf<String>()
        
        for ((pattern, weight) in scamPatterns) {
            if (Regex(pattern).containsMatchIn(lowerText)) {
                scamScore += weight
                matchedPatterns.add(pattern.split("|").first())
            }
        }
        
        // Check for legitimate indicators (reduces scam score)
        var legitimateScore = 0f
        for (pattern in legitimatePatterns) {
            if (Regex(pattern).containsMatchIn(lowerText)) {
                legitimateScore += 0.1f
            }
        }
        
        // Analyze urgency and pressure tactics
        val urgencyWords = listOf("urgent", "immediately", "now", "today", "asap", "hurry")
        val urgencyCount = urgencyWords.count { lowerText.contains(it) }
        if (urgencyCount >= 2) {
            scamScore += 0.15f
        }
        
        // Check for requests for personal information
        val personalInfoRequests = listOf("password", "pin", "code", "number", "account", "ssn")
        val personalInfoCount = personalInfoRequests.count { lowerText.contains(it) }
        if (personalInfoCount >= 2) {
            scamScore += 0.2f
        }
        
        // Normalize scores
        scamScore = scamScore.coerceIn(0f, 1f)
        legitimateScore = legitimateScore.coerceIn(0f, 0.5f)
        
        // Final confidence calculation
        val finalScore = (scamScore - legitimateScore).coerceIn(0f, 1f)
        val isScam = finalScore >= 0.5f
        val confidence = if (isScam) finalScore else (1f - finalScore)
        
        // Determine category
        val category = determineCategory(lowerText, matchedPatterns)
        
        // Generate reasoning
        val reasoning = generateReasoning(isScam, matchedPatterns, urgencyCount, personalInfoCount)
        
        // Determine suggested action
        val action = when {
            isScam && confidence >= autoRejectThreshold -> ClassificationResult.Action.AUTO_REJECT
            isScam && confidence >= 0.6f -> ClassificationResult.Action.WARN_USER
            !isScam && confidence >= 0.7f -> ClassificationResult.Action.ALLOW
            else -> ClassificationResult.Action.UNCERTAIN
        }
        
        return ClassificationResult(
            isScam = isScam,
            confidence = confidence,
            category = category,
            reasoning = reasoning,
            suggestedAction = action
        )
    }
    
    private fun determineCategory(text: String, matchedPatterns: List<String>): String {
        return when {
            text.contains("lottery") || text.contains("prize") || text.contains("won") -> 
                "Lottery/Prize Scam"
            text.contains("bank") || text.contains("account") || text.contains("credit card") -> 
                "Financial Fraud"
            text.contains("tech support") || text.contains("virus") || text.contains("microsoft") -> 
                "Tech Support Scam"
            text.contains("irs") || text.contains("tax") || text.contains("refund") -> 
                "IRS/Tax Scam"
            text.contains("gift card") || text.contains("itunes") || text.contains("google play") -> 
                "Gift Card Scam"
            text.contains("investment") || text.contains("crypto") || text.contains("bitcoin") -> 
                "Investment Scam"
            text.contains("grandson") || text.contains("grandchild") || text.contains("emergency") -> 
                "Grandparent Scam"
            matchedPatterns.isNotEmpty() -> "Potential Scam"
            else -> "Legitimate Call"
        }
    }
    
    private fun generateReasoning(
        isScam: Boolean,
        matchedPatterns: List<String>,
        urgencyCount: Int,
        personalInfoCount: Int
    ): String {
        return if (isScam) {
            buildString {
                append("Scam indicators detected: ")
                if (matchedPatterns.isNotEmpty()) {
                    append("${matchedPatterns.size} suspicious keywords (${matchedPatterns.take(3).joinToString(", ")})")
                }
                if (urgencyCount >= 2) {
                    append(", high-pressure tactics")
                }
                if (personalInfoCount >= 2) {
                    append(", requests for personal information")
                }
                append(". Exercise extreme caution.")
            }
        } else {
            "No significant scam indicators found. Call appears legitimate based on conversation content."
        }
    }
    
    private fun createFallbackResult(): ClassificationResult {
        return ClassificationResult(
            isScam = false,
            confidence = 0.0f,
            category = "Unknown",
            reasoning = "Classification unavailable - classifier not initialized",
            suggestedAction = ClassificationResult.Action.UNCERTAIN
        )
    }
    
    override fun isInitialized(): Boolean = initialized
    
    override fun release() {
        if (initialized) {
            Log.d(TAG, "Releasing classifier")
            initialized = false
        }
    }
    
    override fun getModelName(): String = "FLAN-T5 Small (Enhanced Pattern-Based)"
    
    override fun getAutoRejectThreshold(): Float = autoRejectThreshold
    
    override fun setAutoRejectThreshold(threshold: Float) {
        autoRejectThreshold = threshold.coerceIn(0.0f, 1.0f)
        Log.d(TAG, "Auto-reject threshold set to $autoRejectThreshold")
    }
}
