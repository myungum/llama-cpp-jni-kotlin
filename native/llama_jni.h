#ifndef LLAMA_JNI_H
#define LLAMA_JNI_H

#include <jni.h>

#ifdef __cplusplus
extern "C" {
#endif

// JNI function declarations for LlamaWrapper class
// Package: com.traycer.llama, Class: LlamaWrapper
// These signatures exactly match the Kotlin native method declarations

/**
 * Native method to load a GGUF model.
 * Matches Kotlin: nativeLoadModel(modelPath: String, contextSize: Int, threads: Int): Long
 * 
 * @param env JNI environment pointer
 * @param thiz Java object reference (LlamaWrapper instance)
 * @param modelPath Path to the GGUF model file
 * @param contextSize Context size for the model (default: 2048)
 * @param threads Number of threads to use (-1 for auto-detect)
 * @return Native handle (pointer) to the loaded model context, or 0 on failure
 */
JNIEXPORT jlong JNICALL
Java_com_traycer_llama_LlamaWrapper_nativeLoadModel(JNIEnv *env, jobject thiz, jstring modelPath, jint contextSize, jint threads);

/**
 * Native method to generate text.
 * Matches Kotlin: nativeGenerateText(handle: Long, prompt: String, maxTokens: Int, temperature: Float, topP: Float, topK: Int): String?
 * 
 * @param env JNI environment pointer
 * @param thiz Java object reference (LlamaWrapper instance)
 * @param handle Native handle to the model context
 * @param prompt Input text prompt
 * @param maxTokens Maximum number of tokens to generate
 * @param temperature Sampling temperature (0.0 to 2.0, higher = more random)
 * @param topP Top-p sampling parameter (0.0 to 1.0, nucleus sampling)
 * @param topK Top-k sampling parameter (limits vocabulary to top K tokens)
 * @return Generated text as String, or error message if generation fails
 */
JNIEXPORT jstring JNICALL
Java_com_traycer_llama_LlamaWrapper_nativeGenerateText(JNIEnv *env, jobject thiz, jlong handle, 
                                                       jstring prompt, jint maxTokens, jfloat temperature, jfloat topP, jint topK);

/**
 * Native method to get model information.
 * Matches Kotlin: nativeGetModelInfo(handle: Long): String?
 * 
 * @param env JNI environment pointer
 * @param thiz Java object reference (LlamaWrapper instance)
 * @param handle Native handle to the model context
 * @return Model information string, or error message if handle is invalid
 */
JNIEXPORT jstring JNICALL
Java_com_traycer_llama_LlamaWrapper_nativeGetModelInfo(JNIEnv *env, jobject thiz, jlong handle);

/**
 * Native method to clean up and free model resources.
 * Matches Kotlin: nativeCleanup(handle: Long)
 * 
 * @param env JNI environment pointer
 * @param thiz Java object reference (LlamaWrapper instance)
 * @param handle Native handle to the model context
 */
JNIEXPORT void JNICALL
Java_com_traycer_llama_LlamaWrapper_nativeCleanup(JNIEnv *env, jobject thiz, jlong handle);

// Handle management constants and types
#define LLAMA_JNI_INVALID_HANDLE 0L
#define LLAMA_JNI_SUCCESS 0
#define LLAMA_JNI_ERROR -1

// Error codes for comprehensive error handling
typedef enum {
    LLAMA_JNI_ERR_NONE = 0,
    LLAMA_JNI_ERR_INVALID_MODEL_PATH = 1,
    LLAMA_JNI_ERR_MODEL_LOAD_FAILED = 2,
    LLAMA_JNI_ERR_INVALID_HANDLE = 3,
    LLAMA_JNI_ERR_GENERATION_FAILED = 4,
    LLAMA_JNI_ERR_OUT_OF_MEMORY = 5,
    LLAMA_JNI_ERR_INVALID_PARAMS = 6,
    LLAMA_JNI_ERR_TOKENIZATION_FAILED = 7,
    LLAMA_JNI_ERR_CONTEXT_INIT_FAILED = 8
} llama_jni_error_t;

// Forward declaration for opaque handle structure
// The actual LlamaContext implementation is in the .cpp file
struct LlamaContext;

// Default parameter values (matching Kotlin defaults)
#define LLAMA_JNI_DEFAULT_CONTEXT_SIZE 2048
#define LLAMA_JNI_DEFAULT_MAX_TOKENS 256
#define LLAMA_JNI_DEFAULT_TEMPERATURE 0.8f
#define LLAMA_JNI_DEFAULT_TOP_P 0.9f
#define LLAMA_JNI_DEFAULT_TOP_K 40
#define LLAMA_JNI_AUTO_DETECT_THREADS -1

#ifdef __cplusplus
}
#endif

#endif // LLAMA_JNI_H
