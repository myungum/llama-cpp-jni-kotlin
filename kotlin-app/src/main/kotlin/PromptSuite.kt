package com.traycer.llama

import java.io.File
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import kotlin.system.exitProcess
import kotlin.system.measureTimeMillis

/**
 * Automated prompt test suite for LLM testing.
 * This suite runs a collection of diverse prompts through the Qwen3-0.6B model
 * and saves results to timestamped files for analysis.
 */
object PromptSuite {
    
    // Configuration constants
    private const val MAX_TOKENS = 128
    private const val TEMPERATURE = 0.8f
    private const val TOP_P = 0.9f
    private const val TOP_K = 40
    private const val CONTEXT_SIZE = 2048
    private const val THREADS = -1  // Auto-detect
    
    // Model configuration using centralized ModelConfig
    private val MODEL_FILENAME = ModelConfig.MODEL_FILENAME
    private val MODEL_PATH = ModelConfig.MODEL_PATH
    private val ALTERNATIVE_MODEL_PATH = ModelConfig.ALTERNATIVE_MODEL_PATH
    
    // Test prompt collection with diverse categories
    private val testPrompts = listOf(
        // Simple factual questions
        "What is the capital of France?",
        "How many continents are there on Earth?",
        "What is the chemical symbol for water?",
        
        // Creative writing tasks
        "Write a short story about a robot learning to paint:",
        "Describe a magical forest in three sentences:",
        "Create a poem about the ocean:",
        
        // Code generation
        "Write a Python function to calculate the factorial of a number:",
        "Show me a simple HTML webpage structure:",
        "Create a JavaScript function to reverse a string:",
        
        // Mathematical problems
        "If I have 15 apples and give away 7, how many do I have left?",
        "What is 25% of 80?",
        "Solve: 2x + 5 = 13",
        
        // Explanations and reasoning
        "Explain why the sky appears blue:",
        "How does photosynthesis work?",
        "What causes earthquakes?",
        
        // Reasoning and logic tasks
        "If all cats are animals and some animals are pets, can we conclude that some cats are pets?",
        "List three advantages of renewable energy:",
        "Compare the pros and cons of electric vs gasoline cars:",
        
        // Different complexity levels
        "Hello, how are you?",
        "Explain the concept of artificial intelligence and its potential impact on society:",
        "What are the main differences between machine learning and deep learning?",
        
        // Creative and open-ended
        "If you could travel anywhere in the world, where would you go and why?",
        "Describe your ideal day:",
        "What would happen if gravity suddenly stopped working?",
        
        // Practical questions
        "How do I cook pasta?",
        "What are some tips for staying healthy?",
        "How can I improve my time management skills?"
    )
    
    /**
     * Creates a URL-safe slug from a prompt text
     */
    private fun createSlug(prompt: String): String {
        return prompt
            .lowercase()
            .replace(Regex("[^a-z0-9\\s]"), "")
            .trim()
            .replace(Regex("\\s+"), "_")
            .take(40)
            .trimEnd('_')
    }
    
    /**
     * Creates timestamped output directory
     */
    private fun createOutputDirectory(): File {
        val timestamp = Instant.now()
            .atOffset(ZoneOffset.UTC)
            .format(DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH-mm-ss'Z'"))
        
        val outputDir = File("results/$timestamp")
        outputDir.mkdirs()
        return outputDir
    }
    
    /**
     * Saves prompt result to individual file with metadata
     */
    private fun saveResult(
        outputDir: File, 
        index: Int, 
        prompt: String, 
        response: String, 
        generationTimeMs: Long,
        error: String? = null
    ) {
        val slug = createSlug(prompt)
        val filename = String.format("%02d_%s.txt", index + 1, slug)
        val file = File(outputDir, filename)
        
        val content = buildString {
            appendLine("=".repeat(80))
            appendLine("PROMPT SUITE RESULT")
            appendLine("=".repeat(80))
            appendLine("Index: ${index + 1}")
            appendLine("Timestamp: ${Instant.now()}")
            appendLine("Generation time: ${generationTimeMs}ms")
            appendLine("Model parameters:")
            appendLine("  - Max tokens: $MAX_TOKENS")
            appendLine("  - Temperature: $TEMPERATURE")
            appendLine("  - Top-P: $TOP_P")
            appendLine("  - Top-K: $TOP_K")
            appendLine()
            appendLine("PROMPT:")
            appendLine("-".repeat(40))
            appendLine(prompt)
            appendLine()
            
            if (error != null) {
                appendLine("ERROR:")
                appendLine("-".repeat(40))
                appendLine(error)
            } else {
                appendLine("RESPONSE:")
                appendLine("-".repeat(40))
                appendLine(response)
            }
            appendLine()
            appendLine("=".repeat(80))
        }
        
        file.writeText(content)
    }
    
    /**
     * Loads the model with error handling
     */
    private fun loadModel(wrapper: LlamaWrapper): Boolean {
        println("🔄 Loading model...")
        
        // Try primary model path first
        val modelFile = File(MODEL_PATH)
        val alternativeFile = File(ALTERNATIVE_MODEL_PATH)
        
        val pathToUse = when {
            modelFile.exists() && modelFile.canRead() -> {
                println("📁 Found model at: $MODEL_PATH")
                MODEL_PATH
            }
            alternativeFile.exists() && alternativeFile.canRead() -> {
                println("📁 Found model at: $ALTERNATIVE_MODEL_PATH")
                ALTERNATIVE_MODEL_PATH
            }
            else -> {
                println("❌ Model file not found at either location:")
                println("   - $MODEL_PATH")
                println("   - $ALTERNATIVE_MODEL_PATH")
                println()
                println("💡 Please run the build script to download the model:")
                println("   ./build.sh")
                return false
            }
        }
        
        return try {
            val loadTime = measureTimeMillis {
                wrapper.loadModel(pathToUse, CONTEXT_SIZE, THREADS)
            }
            println("✅ Model loaded successfully in ${loadTime}ms")
            
            // Display model information
            wrapper.getModelInfo()?.let { info ->
                println("📊 Model Information:")
                info.lines().forEach { line ->
                    if (line.isNotBlank()) {
                        println("   $line")
                    }
                }
            }
            println()
            true
        } catch (e: Exception) {
            println("❌ Failed to load model: ${e.message}")
            false
        }
    }
    
    /**
     * Processes a single prompt and returns the result
     */
    private fun processPrompt(
        wrapper: LlamaWrapper,
        index: Int,
        prompt: String
    ): Pair<String?, Long> {
        return try {
            val generationTime = measureTimeMillis {
                val response = wrapper.generateText(
                    prompt = prompt,
                    maxTokens = MAX_TOKENS,
                    temperature = TEMPERATURE,
                    topP = TOP_P,
                    topK = TOP_K
                )
            }
            Pair(wrapper.generateText(prompt, MAX_TOKENS, TEMPERATURE, TOP_P, TOP_K), generationTime)
        } catch (e: Exception) {
            println("   ⚠️  Error: ${e.message}")
            Pair(null, 0L)
        }
    }
    
    /**
     * Creates summary file with overall statistics
     */
    private fun createSummary(outputDir: File, results: List<Triple<String, String?, Long>>) {
        val summaryFile = File(outputDir, "summary.txt")
        
        val successful = results.count { it.second != null }
        val failed = results.size - successful
        val totalTime = results.sumOf { it.third }
        val avgTime = if (successful > 0) totalTime / successful else 0L
        
        val content = buildString {
            appendLine("PROMPT SUITE EXECUTION SUMMARY")
            appendLine("=".repeat(50))
            appendLine("Execution time: ${Instant.now()}")
            appendLine("Total prompts: ${results.size}")
            appendLine("Successful: $successful")
            appendLine("Failed: $failed")
            appendLine("Success rate: ${if (results.isNotEmpty()) (successful * 100) / results.size else 0}%")
            appendLine("Total generation time: ${totalTime}ms")
            appendLine("Average generation time: ${avgTime}ms")
            appendLine()
            appendLine("PROMPT SUMMARY:")
            appendLine("-".repeat(30))
            results.forEachIndexed { index, (prompt, response, time) ->
                val status = if (response != null) "✅" else "❌"
                val timeStr = if (time > 0) "${time}ms" else "N/A"
                appendLine("${(index + 1).toString().padStart(2)}. $status [$timeStr] ${prompt.take(50)}${if (prompt.length > 50) "..." else ""}")
            }
            appendLine()
            appendLine("Results saved to: ${outputDir.absolutePath}")
        }
        
        summaryFile.writeText(content)
    }
    
    /**
     * Main function that runs the entire prompt suite
     */
    @JvmStatic
    fun main(args: Array<String>) {
        println("🧪 LLM Prompt Test Suite")
        println("=" .repeat(50))
        println("📋 Testing ${testPrompts.size} diverse prompts")
        println("🤖 Model: ${ModelConfig.MODEL_DISPLAY_NAME} GGUF")
        println("⚙️  Parameters: maxTokens=$MAX_TOKENS, temp=$TEMPERATURE, topP=$TOP_P, topK=$TOP_K")
        println()
        
        // Create output directory
        val outputDir = createOutputDirectory()
        println("📁 Results will be saved to: ${outputDir.absolutePath}")
        println()
        
        // Initialize wrapper
        val wrapper = LlamaWrapper()
        val results = mutableListOf<Triple<String, String?, Long>>()
        
        try {
            // Load model
            if (!loadModel(wrapper)) {
                exitProcess(1)
            }
            
            println("🚀 Starting prompt suite execution...")
            println("-".repeat(50))
            
            // Process each prompt
            testPrompts.forEachIndexed { index, prompt ->
                println("${(index + 1).toString().padStart(2)}/${testPrompts.size} Processing: ${prompt.take(60)}${if (prompt.length > 60) "..." else ""}")
                
                val (response, generationTime) = processPrompt(wrapper, index, prompt)
                
                if (response != null) {
                    // Show preview of response
                    val preview = response.trim().replace("\n", " ").take(80)
                    println("   ✅ Generated (${generationTime}ms): $preview${if (response.length > 80) "..." else ""}")
                    
                    // Save successful result
                    saveResult(outputDir, index, prompt, response, generationTime)
                    results.add(Triple(prompt, response, generationTime))
                } else {
                    // Save error result
                    saveResult(outputDir, index, prompt, "", 0L, "Generation failed")
                    results.add(Triple(prompt, null, 0L))
        }
        
                println()
    }
    
            // Create summary
            createSummary(outputDir, results)
            
            // Final statistics
            val successful = results.count { it.second != null }
            val failed = results.size - successful
            val totalTime = results.sumOf { it.third }
            
            println("=" .repeat(50))
            println("✅ Prompt suite completed!")
            println("📊 Results: $successful successful, $failed failed")
            println("⏱️  Total generation time: ${totalTime}ms")
            println("📁 All results saved to: ${outputDir.absolutePath}")
            println("📋 Summary available in: ${File(outputDir, "summary.txt").absolutePath}")
            
        } catch (e: Exception) {
            println("❌ Fatal error during execution: ${e.message}")
            e.printStackTrace()
            exitProcess(1)
        } finally {
            // Ensure proper cleanup
            try {
                wrapper.cleanup()
                println("🧹 Cleanup completed")
            } catch (e: Exception) {
                println("⚠️  Warning: Error during cleanup: ${e.message}")
            }
        }
    }
}