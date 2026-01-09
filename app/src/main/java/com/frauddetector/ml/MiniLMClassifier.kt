package com.frauddetector.ml

import android.content.Context
import android.util.Log
import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.sqrt

/**
 * MiniLM-L6-v2 implementation with ONNX Runtime for semantic similarity classification.
 * Uses actual model inference for accurate scam detection.
 * 
 * Model: sentence-transformers/all-MiniLM-L6-v2
 * Embedding dimension: 384
 * Runtime: ONNX Runtime Mobile
 */
class MiniLMClassifier(private val context: Context) : ClassifierModel {
    
    companion object {
        private const val TAG = "MiniLMClassifier"
        private const val EMBEDDING_DIM = 384
        private const val DEFAULT_AUTO_REJECT_THRESHOLD = 0.8f
        private const val MAX_SEQUENCE_LENGTH = 128
        
        // Model files
        private const val MODEL_FILE = "minilm_l6_v2.onnx"
        private const val SCAM_EMBEDDINGS_FILE = "scam_embeddings.npy"
        private const val LEGITIMATE_EMBEDDINGS_FILE = "legitimate_embeddings.npy"
    }
    
    private var initialized = false
    private var autoRejectThreshold = DEFAULT_AUTO_REJECT_THRESHOLD
    
    // ONNX Runtime
    private var ortEnvironment: OrtEnvironment? = null
    private var ortSession: OrtSession? = null
    
    // Tokenizer
    private lateinit var tokenizer: SimpleTokenizer
    
    // Pre-computed embeddings cache
    private lateinit var scamEmbeddings: Array<FloatArray>
    private lateinit var legitimateEmbeddings: Array<FloatArray>
    
    override suspend fun initialize(): Boolean = withContext(Dispatchers.IO) {
        if (initialized) {
            Log.d(TAG, "Classifier already initialized")
            return@withContext true
        }
        
        try {
            Log.d(TAG, "Initializing MiniLM classifier with ONNX Runtime...")
            
            // Initialize tokenizer
            tokenizer = SimpleTokenizer(context)
            if (!tokenizer.loadVocabulary()) {
                Log.e(TAG, "Failed to load tokenizer vocabulary")
                return@withContext false
            }
            
            // Initialize ONNX Runtime
            ortEnvironment = OrtEnvironment.getEnvironment()
            
            // Load ONNX model
            val modelFile = File(context.filesDir, MODEL_FILE)
            if (!modelFile.exists()) {
                Log.d(TAG, "Copying ONNX model from assets...")
                context.assets.open("models/$MODEL_FILE").use { input ->
                    modelFile.outputStream().use { output ->
                        input.copyTo(output)
                    }
                }
            }
            
            // Create ONNX session
            val sessionOptions = OrtSession.SessionOptions()
            sessionOptions.setIntraOpNumThreads(4)
            sessionOptions.setOptimizationLevel(OrtSession.SessionOptions.OptLevel.ALL_OPT)
            
            ortSession = ortEnvironment!!.createSession(
                modelFile.absolutePath,
                sessionOptions
            )
            
            Log.d(TAG, "ONNX model loaded successfully")
            Log.d(TAG, "  Input names: ${ortSession!!.inputNames}")
            Log.d(TAG, "  Output names: ${ortSession!!.outputNames}")
            
            // Load pre-computed embeddings
            scamEmbeddings = loadEmbeddings(SCAM_EMBEDDINGS_FILE)
            legitimateEmbeddings = loadEmbeddings(LEGITIMATE_EMBEDDINGS_FILE)
            
            Log.d(TAG, "Loaded ${scamEmbeddings.size} scam pattern embeddings")
            Log.d(TAG, "Loaded ${legitimateEmbeddings.size} legitimate pattern embeddings")
            
            initialized = true
            Log.d(TAG, "MiniLM classifier initialized successfully")
            return@withContext true
            
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize MiniLM classifier", e)
            return@withContext false
        }
    }
    
    /**
     * Load embeddings from .npy file (NumPy format).
     */
    private fun loadEmbeddings(filename: String): Array<FloatArray> {
        try {
            val file = File(context.filesDir, filename)
            if (!file.exists()) {
                // Try loading from assets
                context.assets.open("models/$filename").use { input ->
                    file.outputStream().use { output ->
                        input.copyTo(output)
                    }
                }
            }
            
            // Parse .npy file (simplified - assumes float32 array)
            val bytes = file.readBytes()
            
            // Skip .npy header (typically 128 bytes for simple arrays)
            val headerSize = 128
            val dataBytes = bytes.sliceArray(headerSize until bytes.size)
            
            // Convert to float array
            val buffer = ByteBuffer.wrap(dataBytes).order(ByteOrder.LITTLE_ENDIAN)
            val numEmbeddings = dataBytes.size / (EMBEDDING_DIM * 4) // 4 bytes per float
            
            val embeddings = Array(numEmbeddings) { FloatArray(EMBEDDING_DIM) }
            for (i in 0 until numEmbeddings) {
                for (j in 0 until EMBEDDING_DIM) {
                    embeddings[i][j] = buffer.float
                }
            }
            
            return embeddings
            
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load embeddings from $filename", e)
            // Return empty array as fallback
            return emptyArray()
        }
    }
    
    /**
     * Encode text to embedding vector using ONNX Runtime.
     */
    private suspend fun encode(text: String): FloatArray = withContext(Dispatchers.Default) {
        try {
            // Tokenize input
            val (inputIds, attentionMask) = tokenizer.encode(text, MAX_SEQUENCE_LENGTH)
            
            // Create ONNX tensors
            val env = ortEnvironment ?: throw IllegalStateException("ONNX environment not initialized")
            
            val inputIdsTensor = OnnxTensor.createTensor(
                env,
                arrayOf(inputIds)
            )
            
            val attentionMaskTensor = OnnxTensor.createTensor(
                env,
                arrayOf(attentionMask)
            )
            
            // Run inference
            val inputs = mapOf(
                "input_ids" to inputIdsTensor,
                "attention_mask" to attentionMaskTensor
            )
            
            val outputs = ortSession!!.run(inputs)
            
            // Get last hidden state
            val lastHiddenState = outputs[0].value as Array<Array<FloatArray>>
            
            // Mean pooling
            val embedding = meanPooling(
                lastHiddenState[0],
                attentionMask.map { it.toFloat() }.toFloatArray()
            )
            
            // Normalize
            val normalized = normalize(embedding)
            
            // Note: ONNX Runtime Android 1.17.0 handles tensor cleanup automatically
            // No manual close() needed for tensors
            
            return@withContext normalized
            
        } catch (e: Exception) {
            Log.e(TAG, "Encoding failed", e)
            // Return zero vector as fallback
            return@withContext FloatArray(EMBEDDING_DIM) { 0f }
        }
    }
    
    /**
     * Mean pooling over token embeddings with attention mask.
     */
    private fun meanPooling(tokenEmbeddings: Array<FloatArray>, attentionMask: FloatArray): FloatArray {
        val embedding = FloatArray(EMBEDDING_DIM) { 0f }
        var sumMask = 0f
        
        for (i in tokenEmbeddings.indices) {
            val mask = attentionMask[i]
            sumMask += mask
            
            for (j in 0 until EMBEDDING_DIM) {
                embedding[j] += tokenEmbeddings[i][j] * mask
            }
        }
        
        // Average
        if (sumMask > 0) {
            for (j in 0 until EMBEDDING_DIM) {
                embedding[j] /= sumMask
            }
        }
        
        return embedding
    }
    
    /**
     * L2 normalization.
     */
    private fun normalize(embedding: FloatArray): FloatArray {
        val norm = sqrt(embedding.sumOf { (it * it).toDouble() }).toFloat()
        
        return if (norm > 0) {
            FloatArray(embedding.size) { i -> embedding[i] / norm }
        } else {
            embedding
        }
    }
    
    override suspend fun classify(text: String, context: Map<String, String>): ClassificationResult =
        withContext(Dispatchers.Default) {
            if (!initialized) {
                Log.e(TAG, "Classifier not initialized")
                return@withContext createFallbackResult()
            }
            
            try {
                Log.d(TAG, "Classifying text (${text.length} chars): ${text.take(100)}...")
                
                // Encode input text using ONNX
                val textEmbedding = encode(text)
                
                // Calculate similarities with scam patterns
                val scamSimilarities = scamEmbeddings.map { embedding ->
                    cosineSimilarity(textEmbedding, embedding)
                }
                val maxScamSimilarity = scamSimilarities.maxOrNull() ?: 0f
                val avgScamSimilarity = scamSimilarities.average().toFloat()
                
                // Calculate similarities with legitimate patterns
                val legitSimilarities = legitimateEmbeddings.map { embedding ->
                    cosineSimilarity(textEmbedding, embedding)
                }
                val maxLegitSimilarity = legitSimilarities.maxOrNull() ?: 0f
                val avgLegitSimilarity = legitSimilarities.average().toFloat()
                
                // Determine classification
                // Use both max and average for robustness
                val scamScore = (maxScamSimilarity * 0.7f + avgScamSimilarity * 0.3f)
                val legitScore = (maxLegitSimilarity * 0.7f + avgLegitSimilarity * 0.3f)
                
                val isScam = scamScore > legitScore
                val confidence = if (isScam) {
                    scamScore / (scamScore + legitScore).coerceAtLeast(0.01f)
                } else {
                    legitScore / (scamScore + legitScore).coerceAtLeast(0.01f)
                }
                
                // Determine category based on which scam pattern matched best
                val category = if (isScam) {
                    determineScamCategory(scamSimilarities)
                } else {
                    "Legitimate Call"
                }
                
                val reasoning = generateReasoning(isScam, maxScamSimilarity, maxLegitSimilarity)
                
                val action = when {
                    isScam && confidence >= autoRejectThreshold -> ClassificationResult.Action.AUTO_REJECT
                    isScam && confidence >= 0.6f -> ClassificationResult.Action.WARN_USER
                    !isScam && confidence >= 0.7f -> ClassificationResult.Action.ALLOW
                    else -> ClassificationResult.Action.UNCERTAIN
                }
                
                Log.d(TAG, "Classification: isScam=$isScam, confidence=$confidence, category=$category")
                Log.d(TAG, "  Scam similarity: max=$maxScamSimilarity, avg=$avgScamSimilarity")
                Log.d(TAG, "  Legit similarity: max=$maxLegitSimilarity, avg=$avgLegitSimilarity")
                
                return@withContext ClassificationResult(
                    isScam = isScam,
                    confidence = confidence,
                    category = category,
                    reasoning = reasoning,
                    suggestedAction = action
                )
                
            } catch (e: Exception) {
                Log.e(TAG, "Classification failed", e)
                return@withContext createFallbackResult()
            }
        }
    
    override suspend fun classifyIncremental(text: String, isComplete: Boolean): ClassificationResult {
        val result = classify(text)
        
        return if (!isComplete && result.confidence > 0.5f) {
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
     * Calculate cosine similarity between two vectors.
     */
    private fun cosineSimilarity(a: FloatArray, b: FloatArray): Float {
        require(a.size == b.size) { "Vectors must have same dimension" }
        
        var dotProduct = 0f
        var normA = 0f
        var normB = 0f
        
        for (i in a.indices) {
            dotProduct += a[i] * b[i]
            normA += a[i] * a[i]
            normB += b[i] * b[i]
        }
        
        val denominator = sqrt(normA * normB)
        return if (denominator > 0) dotProduct / denominator else 0f
    }
    
    private fun determineScamCategory(similarities: List<Float>): String {
        val maxIndex = similarities.indices.maxByOrNull { similarities[it] } ?: 0
        
        // Map index to category (based on order in conversion script)
        return when (maxIndex) {
            in 0..2 -> "Lottery/Prize Scam"
            in 3..5 -> "Financial Fraud"
            in 6..8 -> "IRS/Tax Scam"
            in 9..11 -> "Tech Support Scam"
            in 12..14 -> "Gift Card Scam"
            in 15..17 -> "Investment Scam"
            in 18..20 -> "Grandparent Scam"
            in 21..23 -> "Impersonation"
            else -> "Potential Scam"
        }
    }
    
    private fun generateReasoning(isScam: Boolean, scamSim: Float, legitSim: Float): String {
        return if (isScam) {
            "Semantic analysis detected scam indicators (similarity: ${(scamSim * 100).toInt()}%). " +
            "The conversation pattern matches known fraudulent calls. Exercise extreme caution."
        } else {
            "Conversation appears legitimate (similarity: ${(legitSim * 100).toInt()}%). " +
            "No significant scam indicators detected in the semantic analysis."
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
            Log.d(TAG, "Releasing MiniLM classifier")
            
            try {
                ortSession?.close()
                ortEnvironment?.close()
            } catch (e: Exception) {
                Log.e(TAG, "Error releasing ONNX resources", e)
            }
            
            ortSession = null
            ortEnvironment = null
            initialized = false
        }
    }
    
    override fun getModelName(): String = "MiniLM-L6-v2 (ONNX Runtime)"
    
    override fun getAutoRejectThreshold(): Float = autoRejectThreshold
    
    override fun setAutoRejectThreshold(threshold: Float) {
        autoRejectThreshold = threshold.coerceIn(0.0f, 1.0f)
        Log.d(TAG, "Auto-reject threshold set to $autoRejectThreshold")
    }
}
