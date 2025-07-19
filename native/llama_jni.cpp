#include <jni.h>
#include <string>
#include <vector>
#include <memory>
#include <cstring>
#include <unordered_map>
#include <mutex>
#include <random>
#include <algorithm>
#include <thread>
#include <iostream>
#include "llama.h"

// Structure to hold llama context and associated data
struct LlamaContext {
    llama_model* model;
    llama_context* context;
    std::vector<llama_token> tokens;
    std::mt19937 rng;
    
    LlamaContext() : model(nullptr), context(nullptr), rng(std::random_device{}()) {}
    
    ~LlamaContext() {
        cleanup();
    }
    
    void cleanup() {
        if (context != nullptr) {
            llama_free(context);
            context = nullptr;
        }
        if (model != nullptr) {
            llama_model_free(model);
            model = nullptr;
        }
        tokens.clear();
    }
};

// Global handle management with thread safety
static std::unordered_map<jlong, std::unique_ptr<LlamaContext>> g_contexts;
static std::mutex g_contexts_mutex;
static jlong g_next_handle = 1;
static bool g_backend_initialized = false;
static std::mutex g_init_mutex;

// Helper function to convert jstring to std::string with proper error handling
std::string jstring_to_string(JNIEnv* env, jstring jstr) {
    if (jstr == nullptr) return "";
    
    const char* chars = env->GetStringUTFChars(jstr, nullptr);
    if (chars == nullptr) return "";
    
    std::string result(chars);
    env->ReleaseStringUTFChars(jstr, chars);
    return result;
}

// Helper function to convert std::string to jstring with error handling
jstring string_to_jstring(JNIEnv* env, const std::string& str) {
    if (env == nullptr) return nullptr;
    return env->NewStringUTF(str.c_str());
}

// Helper function to get context by handle with thread safety
LlamaContext* get_context(jlong handle) {
    std::lock_guard<std::mutex> lock(g_contexts_mutex);
    auto it = g_contexts.find(handle);
    return (it != g_contexts.end()) ? it->second.get() : nullptr;
}

// Simplified sampling function using greedy approach
llama_token sample_token_greedy(LlamaContext* ctx, float* logits) {
    if (ctx == nullptr || logits == nullptr || ctx->model == nullptr || ctx->context == nullptr) {
        return 0;
    }
    
    const auto* vocab = llama_model_get_vocab(ctx->model);
    const int n_vocab = llama_vocab_n_tokens(vocab);
    
    // Find token with highest logit (greedy sampling)
    int best_token = 0;
    float best_logit = logits[0];
    
    for (int i = 1; i < n_vocab; i++) {
        if (logits[i] > best_logit) {
            best_logit = logits[i];
            best_token = i;
        }
    }
    
    return best_token;
}

extern "C" {

// Load model from file path - matches exactly: nativeLoadModel(modelPath: String, contextSize: Int, threads: Int): Long
JNIEXPORT jlong JNICALL
Java_com_traycer_llama_LlamaWrapper_nativeLoadModel(JNIEnv* env, jobject thiz, jstring modelPath, jint contextSize, jint threads) {
    try {
        // Initialize backend if not already done (thread-safe)
        {
            std::lock_guard<std::mutex> init_lock(g_init_mutex);
            if (!g_backend_initialized) {
                llama_backend_init();
                g_backend_initialized = true;
            }
        }
        
        // Validate input parameters
        if (env == nullptr || modelPath == nullptr) {
            return 0;
        }
        
        std::string path = jstring_to_string(env, modelPath);
        if (path.empty()) {
            return 0;
        }
        
        // Validate context size
        if (contextSize <= 0) {
            contextSize = 2048;  // Default value
        }
        
        // Create new context
        auto ctx = std::make_unique<LlamaContext>();
        
        // Set up model parameters
        llama_model_params model_params = llama_model_default_params();
        model_params.use_mmap = true;
        model_params.use_mlock = false;
        
        // Load model using new API
        ctx->model = llama_model_load_from_file(path.c_str(), model_params);
        if (ctx->model == nullptr) {
            return 0;
        }
        
        // Set up context parameters
        llama_context_params ctx_params = llama_context_default_params();
        ctx_params.n_ctx = contextSize;
        ctx_params.n_batch = std::min(512, contextSize / 4);
        
        // Handle thread count parameter
        if (threads > 0) {
            ctx_params.n_threads = threads;
            ctx_params.n_threads_batch = threads;
        } else if (threads == -1) {
            // Auto-detect threads
            unsigned int hw_threads = std::thread::hardware_concurrency();
            ctx_params.n_threads = (hw_threads > 0) ? hw_threads : 4;
            ctx_params.n_threads_batch = ctx_params.n_threads;
        } else {
            // Default to 4 threads if invalid value
            ctx_params.n_threads = 4;
            ctx_params.n_threads_batch = 4;
        }
        
        // Create context using new API
        ctx->context = llama_init_from_model(ctx->model, ctx_params);
        if (ctx->context == nullptr) {
            return 0;
        }
        
        // Reserve space for tokens
        ctx->tokens.reserve(ctx_params.n_ctx);
        
        // Store context and return handle (thread-safe)
        {
            std::lock_guard<std::mutex> lock(g_contexts_mutex);
            jlong handle = g_next_handle++;
            g_contexts[handle] = std::move(ctx);
            return handle;
        }
        
    } catch (const std::exception& e) {
        std::cerr << "Exception in nativeLoadModel: " << e.what() << std::endl;
        return 0;
    } catch (...) {
        std::cerr << "Unknown exception in nativeLoadModel" << std::endl;
        return 0;
    }
}

// Generate text - matches exactly: nativeGenerateText(handle: Long, prompt: String, maxTokens: Int, temperature: Float, topP: Float, topK: Int): String?
JNIEXPORT jstring JNICALL
Java_com_traycer_llama_LlamaWrapper_nativeGenerateText(JNIEnv* env, jobject thiz, jlong handle, jstring prompt, jint maxTokens, jfloat temperature, jfloat topP, jint topK) {
    // Validate input parameters
    if (env == nullptr || prompt == nullptr || handle == 0) {
        return string_to_jstring(env, "Error: Invalid parameters");
    }
    
    LlamaContext* ctx = get_context(handle);
    if (ctx == nullptr || ctx->model == nullptr || ctx->context == nullptr) {
        return string_to_jstring(env, "Error: Invalid handle or model not loaded");
    }
    
    try {
        std::string input = jstring_to_string(env, prompt);
        if (input.empty()) {
            return string_to_jstring(env, "Error: Empty prompt");
        }
        
        // Validate generation parameters
        if (maxTokens <= 0) {
            maxTokens = 256;  // Default
        }
        
        // Clear previous tokens and KV cache
        ctx->tokens.clear();
        llama_memory_t memory = llama_get_memory(ctx->context);
        llama_memory_clear(memory, true);
        
        // Get vocab for tokenization
        const auto* vocab = llama_model_get_vocab(ctx->model);
        
        // Tokenize input
        const int max_input_tokens = llama_n_ctx(ctx->context) / 2;
        ctx->tokens.resize(max_input_tokens);
        
        int n_tokens = llama_tokenize(
            vocab,
            input.c_str(),
            input.length(),
            ctx->tokens.data(),
            max_input_tokens,
            true,  // add_bos (beginning of sequence)
            false  // special tokens
        );
        
        if (n_tokens < 0) {
            return string_to_jstring(env, "Error: Tokenization failed");
        }
        
        ctx->tokens.resize(n_tokens);
        
        // Process the prompt
        llama_batch batch = llama_batch_init(n_tokens, 0, 1);
        
        // Add all tokens to the batch
        for (int i = 0; i < n_tokens; i++) {
            batch.token[i] = ctx->tokens[i];
            batch.pos[i] = i;
            batch.n_seq_id[i] = 1;
            batch.seq_id[i][0] = 0;
            batch.logits[i] = false;
        }
        
        batch.n_tokens = n_tokens;
        
        // Mark the last token for logits computation
        if (batch.n_tokens > 0) {
            batch.logits[batch.n_tokens - 1] = true;
        }
        
        // Process the prompt
        if (llama_decode(ctx->context, batch) != 0) {
            llama_batch_free(batch);
            return string_to_jstring(env, "Error: Failed to decode prompt");
        }
        
        std::string result;
        
        // Calculate maximum generation tokens
        int max_gen_tokens = std::min(static_cast<int>(maxTokens), static_cast<int>(llama_n_ctx(ctx->context)) - n_tokens);
        
        // Generate tokens one by one using simplified greedy sampling
        for (int i = 0; i < max_gen_tokens; i++) {
            // Get logits for the last token
            float* logits = llama_get_logits_ith(ctx->context, batch.n_tokens - 1);
            if (logits == nullptr) {
                break;
            }
            
            // Sample next token using greedy approach (ignore advanced parameters for now)
            llama_token new_token = sample_token_greedy(ctx, logits);
            
            // Check for end of sequence
            if (llama_vocab_is_eog(vocab, new_token)) {
                break;
            }
            
            // Convert token to text
            char piece[256];
            int piece_len = llama_token_to_piece(vocab, new_token, piece, sizeof(piece), 0, false);
            
            if (piece_len > 0) {
                result.append(piece, piece_len);
            }
            
            // Prepare for next iteration
            batch.n_tokens = 1;
            batch.token[0] = new_token;
            batch.pos[0] = ctx->tokens.size();
            batch.n_seq_id[0] = 1;
            batch.seq_id[0][0] = 0;
            batch.logits[0] = true;
            
            ctx->tokens.push_back(new_token);
            
            // Decode the new token
            if (llama_decode(ctx->context, batch) != 0) {
                break;
            }
        }
        
        llama_batch_free(batch);
        return string_to_jstring(env, result);
        
    } catch (const std::exception& e) {
        std::cerr << "Exception in nativeGenerateText: " << e.what() << std::endl;
        return string_to_jstring(env, "Error: Exception during text generation");
    } catch (...) {
        std::cerr << "Unknown exception in nativeGenerateText" << std::endl;
        return string_to_jstring(env, "Error: Unknown exception during text generation");
    }
}

// Get model information - matches exactly: nativeGetModelInfo(handle: Long): String?
JNIEXPORT jstring JNICALL
Java_com_traycer_llama_LlamaWrapper_nativeGetModelInfo(JNIEnv* env, jobject thiz, jlong handle) {
    if (env == nullptr || handle == 0) {
        return string_to_jstring(env, "Error: Invalid parameters");
    }
    
    LlamaContext* ctx = get_context(handle);
    if (ctx == nullptr || ctx->model == nullptr || ctx->context == nullptr) {
        return string_to_jstring(env, "Error: No model loaded for this handle");
    }
    
    try {
        const auto* vocab = llama_model_get_vocab(ctx->model);
        
        char model_desc[256];
        llama_model_desc(ctx->model, model_desc, sizeof(model_desc));
        
        std::string info = "Model Information:\n";
        info += "Handle: " + std::to_string(handle) + "\n";
        info += "Vocabulary size: " + std::to_string(llama_vocab_n_tokens(vocab)) + "\n";
        info += "Context size: " + std::to_string(llama_n_ctx(ctx->context)) + "\n";
        info += "Embedding size: " + std::to_string(llama_model_n_embd(ctx->model)) + "\n";
        info += "Model type: " + std::string(model_desc) + "\n";
        info += "Status: Loaded and ready";
        
        return string_to_jstring(env, info);
    } catch (const std::exception& e) {
        std::cerr << "Exception in nativeGetModelInfo: " << e.what() << std::endl;
        return string_to_jstring(env, "Error: Exception getting model info");
    } catch (...) {
        std::cerr << "Unknown exception in nativeGetModelInfo" << std::endl;
        return string_to_jstring(env, "Error: Unknown exception getting model info");
    }
}

// Cleanup resources - matches exactly: nativeCleanup(handle: Long)
JNIEXPORT void JNICALL
Java_com_traycer_llama_LlamaWrapper_nativeCleanup(JNIEnv* env, jobject thiz, jlong handle) {
    if (handle == 0) {
        return;  // Invalid handle, nothing to clean up
    }
    
    try {
        std::lock_guard<std::mutex> lock(g_contexts_mutex);
        auto it = g_contexts.find(handle);
        if (it != g_contexts.end()) {
            // The unique_ptr destructor will automatically call LlamaContext destructor
            // which will properly clean up the model and context
            g_contexts.erase(it);
        }
    } catch (const std::exception& e) {
        std::cerr << "Exception in nativeCleanup: " << e.what() << std::endl;
        // Continue cleanup even if there's an exception
    } catch (...) {
        std::cerr << "Unknown exception in nativeCleanup" << std::endl;
        // Continue cleanup even if there's an exception
    }
}

} // extern "C"
