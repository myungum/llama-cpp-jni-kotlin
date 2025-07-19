package com.traycer.llama

/**
 * Model configuration constants shared across the application.
 * 
 * This object centralizes all model-related configuration to ensure consistency
 * and make it easy to change the model in one place.
 */
object ModelConfig {
    
    /**
     * The filename of the GGUF model to use.
     * Change this constant to use a different model.
     */
    const val MODEL_FILENAME = "Qwen3-0.6B-Q8_0.gguf"
    
    /**
     * Primary model path (relative to project root)
     */
    const val MODEL_PATH = "../models/$MODEL_FILENAME"
    
    /**
     * Alternative model path (relative to kotlin-app directory)
     */
    const val ALTERNATIVE_MODEL_PATH = "./models/$MODEL_FILENAME"
    
    /**
     * Model display name for user interfaces
     */
    const val MODEL_DISPLAY_NAME = "Qwen3-0.6B (Q8_0)"
    
    /**
     * Default model parameters
     */
    object DefaultParams {
        const val CONTEXT_SIZE = 2048
        const val MAX_TOKENS = 256
        const val TEMPERATURE = 0.8f
        const val TOP_P = 0.9f
        const val TOP_K = 40
        const val THREADS = -1  // Auto-detect
    }
}