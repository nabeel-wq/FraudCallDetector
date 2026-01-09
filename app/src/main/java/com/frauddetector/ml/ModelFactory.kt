package com.frauddetector.ml

import android.content.Context
import android.util.Log
import java.io.File

/**
 * Factory for creating ASR and Classifier model instances.
 * Allows easy switching between different model implementations via configuration.
 */
class ModelFactory(private val context: Context) {
    
    companion object {
        private const val TAG = "ModelFactory"
        
        // Model type constants
        const val ASR_WHISPER_CPP = "whisper_cpp"
        const val ASR_INDIC_CONFORMER = "indic_conformer"
        // Add more ASR types as needed
        
        const val CLASSIFIER_MINILM = "minilm"  // Default, bundled
        const val CLASSIFIER_FLAN_T5 = "flan_t5"  // Advanced, optional download
        const val CLASSIFIER_BERT = "bert"
        const val CLASSIFIER_GEMMA = "gemma"
        // Add more classifier types as needed
    }
    
    /**
     * Creates an ASR (Automatic Speech Recognition) model instance.
     * 
     * @param modelType Type of ASR model to create (use constants above)
     * @return ASR model instance
     * @throws IllegalArgumentException if model type is not supported
     */
    fun createASRModel(modelType: String = ASR_WHISPER_CPP): ASRModel {
        Log.d(TAG, "Creating ASR model: $modelType")
        
        return when (modelType) {
            ASR_WHISPER_CPP -> {
                // Using Whisper.cpp via JNI
                WhisperASR(context)
            }
            ASR_INDIC_CONFORMER -> {
                // Placeholder for future implementation
                throw IllegalArgumentException("IndicConformer ASR not yet implemented. Use $ASR_WHISPER_CPP for now.")
            }
            else -> {
                throw IllegalArgumentException("Unsupported ASR model type: $modelType")
            }
        }
    }
    
    /**
     * Create classifier model instance based on type.
     * @param modelType Type of classifier model to create (use constants above)
     * @return Classifier model instance
     * @throws IllegalArgumentException if model type is not supported
     */
    fun createClassifierModel(modelType: String = CLASSIFIER_MINILM): ClassifierModel {
        Log.d(TAG, "Creating classifier model: $modelType")
        
        return when (modelType) {
            CLASSIFIER_MINILM -> {
                MiniLMClassifier(context)
            }
            CLASSIFIER_FLAN_T5 -> {
                // Check if FLAN-T5 model is downloaded
                if (!isFlanT5Downloaded()) {
                    throw IllegalStateException("FLAN-T5 model not downloaded. Please download from settings.")
                }
                FlanT5Classifier(context)
            }
            CLASSIFIER_BERT -> {
                // Placeholder for future implementation
                throw IllegalArgumentException("BERT classifier not yet implemented. Use $CLASSIFIER_MINILM for now.")
            }
            CLASSIFIER_GEMMA -> {
                // Placeholder for future implementation
                throw IllegalArgumentException("Gemma classifier not yet implemented. Use $CLASSIFIER_MINILM for now.")
            }
            else -> {
                throw IllegalArgumentException("Unsupported classifier model type: $modelType")
            }
        }
    }
    
    /**
     * Check if FLAN-T5 model is downloaded.
     */
    fun isFlanT5Downloaded(): Boolean {
        val modelFile = java.io.File(context.filesDir, "flan_t5_saved_model")
        return modelFile.exists() && modelFile.isDirectory
    }
    
    /**
     * Get list of available ASR models.
     */
    fun getAvailableASRModels(): List<String> {
        return listOf(
            ASR_WHISPER_CPP,
            // ASR_INDIC_CONFORMER // Uncomment when implemented
        )
    }
    
    /**
     * Get list of available classifier models.
     */
    fun getAvailableClassifierModels(): List<String> {
        val models = mutableListOf(CLASSIFIER_MINILM)
        
        // Add FLAN-T5 if downloaded
        if (isFlanT5Downloaded()) {
            models.add(CLASSIFIER_FLAN_T5)
        }
        
        return models
    }
}
