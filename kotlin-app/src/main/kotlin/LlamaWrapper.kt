package com.traycer.llama

import java.io.File

/**
 * Kotlin wrapper for llama.cpp native library integration via JNI.
 * Provides a high-level API for loading GGUF models and generating text.
 */
class LlamaWrapper {
    
    companion object {
        private var isLibraryLoaded = false
        
        /**
         * Load the native library. This should be called before using any other methods.
         */
        fun loadLibrary() {
            if (!isLibraryLoaded) {
                try {
                    System.loadLibrary("llama_jni")
                    isLibraryLoaded = true
                } catch (e: UnsatisfiedLinkError) {
                    throw RuntimeException("Failed to load native library 'llama_jni'. Make sure the library is in the java.library.path.", e)
                }
            }
        }
        
        init {
            loadLibrary()
        }
    }
    
    // Native handle to the llama context (pointer stored as long)
    private var nativeHandle: Long = 0
    private var isModelLoaded = false
    
    /**
     * Load a GGUF model from the specified file path.
     * 
     * @param modelPath Path to the GGUF model file
     * @param contextSize Context size for the model (default: 2048)
     * @param threads Number of threads to use (default: -1 for auto-detect)
     * @throws IllegalArgumentException if the model path is invalid
     * @throws RuntimeException if model loading fails
     */
    fun loadModel(modelPath: String, contextSize: Int = 2048, threads: Int = -1) {
        if (isModelLoaded) {
            cleanup()
        }
        
        val modelFile = File(modelPath)
        if (!modelFile.exists()) {
            throw IllegalArgumentException("Model file does not exist: $modelPath")
        }
        if (!modelFile.canRead()) {
            throw IllegalArgumentException("Cannot read model file: $modelPath")
        }
        
        try {
            nativeHandle = nativeLoadModel(modelPath, contextSize, threads)
            if (nativeHandle == 0L) {
                throw RuntimeException("Failed to load model: $modelPath")
            }
            isModelLoaded = true
        } catch (e: Exception) {
            throw RuntimeException("Error loading model: ${e.message}", e)
        }
    }
    
    /**
     * Generate text based on the given prompt.
     * 
     * @param prompt The input prompt for text generation
     * @param maxTokens Maximum number of tokens to generate (default: 256)
     * @param temperature Temperature for sampling (default: 0.8)
     * @param topP Top-p sampling parameter (default: 0.9)
     * @param topK Top-k sampling parameter (default: 40)
     * @return Generated text as a string
     * @throws IllegalStateException if no model is loaded
     * @throws RuntimeException if text generation fails
     */
    fun generateText(
        prompt: String, 
        maxTokens: Int = 256, 
        temperature: Float = 0.8f,
        topP: Float = 0.9f,
        topK: Int = 40
    ): String {
        if (!isModelLoaded || nativeHandle == 0L) {
            throw IllegalStateException("No model loaded. Call loadModel() first.")
        }
        
        if (prompt.isBlank()) {
            throw IllegalArgumentException("Prompt cannot be empty or blank")
        }
        
        try {
            val result = nativeGenerateText(nativeHandle, prompt, maxTokens, temperature, topP, topK)
            if (result == null) {
                throw RuntimeException("Text generation returned null result")
            }
            return result
        } catch (e: Exception) {
            throw RuntimeException("Error during text generation: ${e.message}", e)
        }
    }
    
    /**
     * Get information about the loaded model.
     * 
     * @return Model information as a string, or null if no model is loaded
     */
    fun getModelInfo(): String? {
        if (!isModelLoaded || nativeHandle == 0L) {
            return null
        }
        
        return try {
            nativeGetModelInfo(nativeHandle)
        } catch (e: Exception) {
            null
        }
    }
    
    /**
     * Check if a model is currently loaded.
     * 
     * @return true if a model is loaded, false otherwise
     */
    fun isModelLoaded(): Boolean {
        return isModelLoaded && nativeHandle != 0L
    }
    
    /**
     * Clean up resources and free the loaded model.
     * This should be called when done using the model to prevent memory leaks.
     */
    fun cleanup() {
        if (nativeHandle != 0L) {
            try {
                nativeCleanup(nativeHandle)
            } catch (e: Exception) {
                // Log error but don't throw during cleanup
                System.err.println("Warning: Error during cleanup: ${e.message}")
            } finally {
                nativeHandle = 0L
                isModelLoaded = false
            }
        }
    }
    
    /**
     * Finalize method to ensure cleanup when object is garbage collected.
     */
    protected fun finalize() {
        cleanup()
    }
    
    // Native method declarations - these correspond to the JNI implementation
    
    /**
     * Native method to load a GGUF model.
     * 
     * @param modelPath Path to the model file
     * @param contextSize Context size for the model
     * @param threads Number of threads to use
     * @return Native handle (pointer) to the loaded model context
     */
    private external fun nativeLoadModel(modelPath: String, contextSize: Int, threads: Int): Long
    
    /**
     * Native method to generate text.
     * 
     * @param handle Native handle to the model context
     * @param prompt Input prompt
     * @param maxTokens Maximum tokens to generate
     * @param temperature Sampling temperature
     * @param topP Top-p sampling parameter
     * @param topK Top-k sampling parameter
     * @return Generated text
     */
    private external fun nativeGenerateText(
        handle: Long, 
        prompt: String, 
        maxTokens: Int, 
        temperature: Float,
        topP: Float,
        topK: Int
    ): String?
    
    /**
     * Native method to get model information.
     * 
     * @param handle Native handle to the model context
     * @return Model information string
     */
    private external fun nativeGetModelInfo(handle: Long): String?
    
    /**
     * Native method to clean up and free model resources.
     * 
     * @param handle Native handle to the model context
     */
    private external fun nativeCleanup(handle: Long)
}