package com.frauddetector.ml

import android.content.Context
import android.util.Log
import java.io.BufferedReader
import java.io.InputStreamReader

/**
 * Simple WordPiece tokenizer for BERT-based models.
 * Implements basic tokenization for MiniLM-L6-v2.
 */
class SimpleTokenizer(private val context: Context) {
    
    companion object {
        private const val TAG = "SimpleTokenizer"
        private const val VOCAB_FILE = "vocab.txt"
        private const val MAX_INPUT_CHARS_PER_WORD = 100
        
        // Special tokens
        const val CLS_TOKEN = "[CLS]"
        const val SEP_TOKEN = "[SEP]"
        const val PAD_TOKEN = "[PAD]"
        const val UNK_TOKEN = "[UNK]"
    }
    
    private val vocab = mutableMapOf<String, Int>()
    private val idsToTokens = mutableMapOf<Int, String>()
    
    var clsTokenId: Int = 0
        private set
    var sepTokenId: Int = 0
        private set
    var padTokenId: Int = 0
        private set
    var unkTokenId: Int = 0
        private set
    
    fun loadVocabulary(): Boolean {
        try {
            Log.d(TAG, "Loading vocabulary from assets...")
            
            context.assets.open("models/$VOCAB_FILE").use { inputStream ->
                BufferedReader(InputStreamReader(inputStream)).use { reader ->
                    var index = 0
                    reader.forEachLine { token ->
                        vocab[token] = index
                        idsToTokens[index] = token
                        
                        // Store special token IDs
                        when (token) {
                            CLS_TOKEN -> clsTokenId = index
                            SEP_TOKEN -> sepTokenId = index
                            PAD_TOKEN -> padTokenId = index
                            UNK_TOKEN -> unkTokenId = index
                        }
                        
                        index++
                    }
                }
            }
            
            Log.d(TAG, "Vocabulary loaded: ${vocab.size} tokens")
            Log.d(TAG, "Special tokens - CLS: $clsTokenId, SEP: $sepTokenId, PAD: $padTokenId, UNK: $unkTokenId")
            
            return true
            
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load vocabulary", e)
            return false
        }
    }
    
    /**
     * Tokenize text and convert to input IDs.
     * Returns pair of (input_ids, attention_mask)
     */
    fun encode(text: String, maxLength: Int = 128): Pair<LongArray, LongArray> {
        // Basic whitespace tokenization
        val tokens = basicTokenize(text.lowercase())
        
        // WordPiece tokenization
        val wordpieceTokens = mutableListOf<String>()
        for (token in tokens) {
            wordpieceTokens.addAll(wordpieceTokenize(token))
        }
        
        // Add special tokens: [CLS] + tokens + [SEP]
        val finalTokens = mutableListOf(CLS_TOKEN)
        finalTokens.addAll(wordpieceTokens.take(maxLength - 2)) // Leave room for CLS and SEP
        finalTokens.add(SEP_TOKEN)
        
        // Convert to IDs
        val inputIds = LongArray(maxLength) { padTokenId.toLong() }
        val attentionMask = LongArray(maxLength) { 0L }
        
        for (i in finalTokens.indices) {
            inputIds[i] = (vocab[finalTokens[i]] ?: unkTokenId).toLong()
            attentionMask[i] = 1L
        }
        
        return Pair(inputIds, attentionMask)
    }
    
    /**
     * Basic tokenization: whitespace splitting and punctuation handling
     */
    private fun basicTokenize(text: String): List<String> {
        // Simple whitespace tokenization
        // In production, you'd want more sophisticated handling
        return text.trim()
            .split(Regex("\\s+"))
            .filter { it.isNotEmpty() }
    }
    
    /**
     * WordPiece tokenization
     */
    private fun wordpieceTokenize(word: String): List<String> {
        if (word.length > MAX_INPUT_CHARS_PER_WORD) {
            return listOf(UNK_TOKEN)
        }
        
        val tokens = mutableListOf<String>()
        var start = 0
        
        while (start < word.length) {
            var end = word.length
            var foundSubtoken: String? = null
            
            // Greedy longest-match-first
            while (start < end) {
                var substr = word.substring(start, end)
                
                // Add ## prefix for non-first subwords
                if (start > 0) {
                    substr = "##$substr"
                }
                
                if (vocab.containsKey(substr)) {
                    foundSubtoken = substr
                    break
                }
                
                end--
            }
            
            if (foundSubtoken == null) {
                // Unknown token
                tokens.add(UNK_TOKEN)
                break
            }
            
            tokens.add(foundSubtoken)
            start = end
        }
        
        return tokens
    }
    
    fun getVocabSize(): Int = vocab.size
}
