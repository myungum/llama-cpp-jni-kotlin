package com.traycer.llama

import java.io.File
import kotlin.system.exitProcess
import kotlin.system.measureTimeMillis

/**
 * Quick smoke test for deployment verification
 * 
 * This class provides a minimal test suite for verifying that the LLM deployment
 * is functioning correctly. It performs basic validation with reduced parameters
 * for faster execution and quick feedback.
 */
class QuickSmokeTest {
    private var verbose = false
    private val wrapper = LlamaWrapper()
    
    companion object {
        private const val DEFAULT_MODEL_PATH = "model.gguf"
        private const val MAX_TOKENS = 64
        private const val CONTEXT_SIZE = 512
        private const val TIMEOUT_SECONDS = 60
        
        @JvmStatic
        fun main(args: Array<String>) {
            val test = QuickSmokeTest()
            
            // Parse command line arguments
            for (arg in args) {
                when (arg) {
                    "--verbose" -> test.verbose = true
                    "--help", "-h" -> {
                        println("Usage: QuickSmokeTest [options]")
                        println("Options:")
                        println("  --verbose    Show detailed output")
                        println("  --help, -h   Show this help message")
                        exitProcess(0)
                    }
                }
            }
            
            try {
                val success = test.runSmokeTest()
                exitProcess(if (success) 0 else 1)
            } catch (e: Exception) {
                System.err.println("Smoke test failed with exception: ${e.message}")
                if (test.verbose) {
                    e.printStackTrace()
                }
                exitProcess(1)
            }
        }
    }
    
    fun runSmokeTest(): Boolean {
        println("🧪 Starting Quick Smoke Test for LLM Deployment")
        println("⚡ This test performs minimal validation for fast feedback")
        println()
        
        // Verify model file exists
        if (!verifyModelFile()) {
            return false
        }
        
        // Load model with minimal context
        if (!loadModel()) {
            return false
        }
        
        // Execute essential test prompts
        val testResults = runEssentialTests()
        
        // Cleanup
        cleanup()
        
        // Report results
        return reportResults(testResults)
    }
    
    private fun verifyModelFile(): Boolean {
        log("Verifying model file availability...")
        
        val modelFile = File(DEFAULT_MODEL_PATH)
        if (!modelFile.exists()) {
            // Try alternative locations
            val alternativeLocations = listOf(
                "models/model.gguf",
                "../models/Qwen3-4B-Q4_K_M.gguf",
                "../models/qwen3-0.6b-q4_0.gguf"
            )
            
            for (location in alternativeLocations) {
                val altFile = File(location)
                if (altFile.exists()) {
                    log("Found model at alternative location: $location")
                    return true
                }
            }
            
            System.err.println("❌ Model file not found: $DEFAULT_MODEL_PATH")
            System.err.println("   Checked locations: $DEFAULT_MODEL_PATH, ${alternativeLocations.joinToString(", ")}")
            return false
        }
        
        val fileSize = modelFile.length() / (1024 * 1024) // MB
        log("✅ Model file found: ${modelFile.absolutePath} (${fileSize}MB)")
        
        if (fileSize < 100) {
            System.err.println("⚠️  Warning: Model file seems small (${fileSize}MB), may be incomplete")
        }
        
        return true
    }
    
    private fun loadModel(): Boolean {
        log("Loading model with minimal context...")
        
        val loadTime = measureTimeMillis {
            try {
                // Use minimal parameters for faster loading
                wrapper.loadModel(DEFAULT_MODEL_PATH, CONTEXT_SIZE, 1) // 1 thread for simplicity
            } catch (e: Exception) {
                System.err.println("❌ Model loading failed: ${e.message}")
                if (verbose) {
                    e.printStackTrace()
                }
                return false
            }
        }
        
        log("✅ Model loaded successfully in ${loadTime}ms")
        
        if (loadTime > 30000) { // 30 seconds
            println("⚠️  Warning: Model loading took ${loadTime/1000}s, performance may be suboptimal")
        }
        
        return true
    }
    
    private fun runEssentialTests(): List<TestResult> {
        log("Running essential test prompts...")
        
        val testPrompts = listOf(
            TestPrompt("factual", "What is 2+2?", "simple arithmetic"),
            TestPrompt("generation", "Complete this sentence: The weather today is", "text completion")
        )
        
        val results = mutableListOf<TestResult>()
        
        for ((index, prompt) in testPrompts.withIndex()) {
            log("Test ${index + 1}/${testPrompts.size}: ${prompt.description}")
            
            val result = runSingleTest(prompt)
            results.add(result)
            
            if (verbose) {
                println("  Input: ${prompt.text}")
                println("  Output: ${result.response}")
                println("  Time: ${result.executionTime}ms")
                println("  Success: ${result.success}")
                println()
            } else {
                print(if (result.success) "✅" else "❌")
                if (index < testPrompts.size - 1) print(" ")
            }
        }
        
        if (!verbose) println() // New line after progress indicators
        
        return results
    }
    
    private fun runSingleTest(prompt: TestPrompt): TestResult {
        val startTime = System.currentTimeMillis()
        
        return try {
            // Execute with timeout protection
            val response = wrapper.generateText(prompt.text, MAX_TOKENS)
            val executionTime = System.currentTimeMillis() - startTime
            
            // Basic response validation
            val isValid = response.isNotBlank() && response.length > 5 && executionTime < TIMEOUT_SECONDS * 1000
            
            TestResult(
                prompt = prompt,
                response = response,
                executionTime = executionTime,
                success = isValid,
                error = null
            )
        } catch (e: Exception) {
            val executionTime = System.currentTimeMillis() - startTime
            TestResult(
                prompt = prompt,
                response = "",
                executionTime = executionTime,
                success = false,
                error = e.message
            )
        }
    }
    
    private fun cleanup() {
        try {
            wrapper.cleanup()
            log("Model cleanup completed")
        } catch (e: Exception) {
            log("Warning: Cleanup failed: ${e.message}")
        }
    }
    
    private fun reportResults(results: List<TestResult>): Boolean {
        println()
        println("📊 Smoke Test Results Summary")
        println("=" * 40)
        
        val successCount = results.count { it.success }
        val totalCount = results.size
        val averageTime = results.map { it.executionTime }.average()
        
        println("Tests passed: $successCount/$totalCount")
        println("Average execution time: ${"%.1f".format(averageTime)}ms")
        
        if (successCount == totalCount) {
            println("✅ All smoke tests passed!")
            println("🚀 Deployment verification successful")
            return true
        } else {
            println("❌ Some tests failed:")
            results.filter { !it.success }.forEach { result ->
                println("  - ${result.prompt.description}: ${result.error ?: "Invalid response"}")
            }
            println("🔧 Deployment may need troubleshooting")
            return false
        }
    }
    
    private fun log(message: String) {
        if (verbose) {
            println("[INFO] $message")
        }
    }
    
    data class TestPrompt(
        val category: String,
        val text: String,
        val description: String
    )
    
    data class TestResult(
        val prompt: TestPrompt,
        val response: String,
        val executionTime: Long,
        val success: Boolean,
        val error: String?
    )
}

private operator fun String.times(n: Int): String = this.repeat(n) 