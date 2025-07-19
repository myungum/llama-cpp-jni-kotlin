package com.traycer.llama

import java.io.File
import java.util.*
import kotlin.system.exitProcess

/**
 * Main application demonstrating llama.cpp integration with Kotlin via JNI.
 * This application showcases loading the GGUF model and performing text generation.
 */
object Main {
    
    @JvmStatic
    fun main(args: Array<String>) {
        println("=".repeat(80))
        println("🦙 Llama.cpp + Kotlin JNI Integration Demo")
        println("🤖 Using ${ModelConfig.MODEL_DISPLAY_NAME} GGUF Model")
        println("=".repeat(80))
        println()
        
        val wrapper = LlamaWrapper()
        
        try {
            // Load the model
            loadModel(wrapper)
            
            // Display model information
            displayModelInfo(wrapper)
            
            // Run predefined examples
            runPredefinedExamples(wrapper)
            
            // Interactive mode
            runInteractiveMode(wrapper)
            
        } catch (e: Exception) {
            System.err.println("❌ Error: ${e.message}")
            e.printStackTrace()
            exitProcess(1)
        } finally {
            // Ensure cleanup
            println("\n🧹 Cleaning up resources...")
            wrapper.cleanup()
            println("✅ Cleanup completed.")
        }
    }
    
    /**
     * Load the GGUF model with proper error handling and fallback paths.
     */
    private fun loadModel(wrapper: LlamaWrapper) {
        println("📂 Loading model...")
        
        val modelPaths = listOf(ModelConfig.MODEL_PATH, ModelConfig.ALTERNATIVE_MODEL_PATH)
        var modelLoaded = false
        
        for (path in modelPaths) {
            val file = File(path)
            if (file.exists()) {
                try {
                    println("🔍 Found model at: $path")
                    println("📊 Model size: ${formatFileSize(file.length())}")
                    
                    print("⏳ Loading model (this may take a moment)... ")
                    val startTime = System.currentTimeMillis()
                    
                    wrapper.loadModel(
                        modelPath = path,
                        contextSize = ModelConfig.DefaultParams.CONTEXT_SIZE,
                        threads = ModelConfig.DefaultParams.THREADS
                    )
                    
                    val loadTime = System.currentTimeMillis() - startTime
                    println("✅ Done! (${loadTime}ms)")
                    modelLoaded = true
                    break
                    
                } catch (e: Exception) {
                    println("❌ Failed to load model from $path: ${e.message}")
                }
            } else {
                println("⚠️  Model not found at: $path")
            }
        }
        
        if (!modelLoaded) {
            throw RuntimeException("""
                |No model found! Please ensure the Qwen3-0.6B GGUF model is available at one of:
                |${modelPaths.joinToString("\n")}
                |
                |You can download the model using the download_model.sh script.
            """.trimMargin())
        }
    }
    
    /**
     * Display information about the loaded model.
     */
    private fun displayModelInfo(wrapper: LlamaWrapper) {
        println("\n" + "=".repeat(50))
        println("📋 Model Information")
        println("=".repeat(50))
        
        val modelInfo = wrapper.getModelInfo()
        if (modelInfo != null) {
            println(modelInfo)
        } else {
            println("ℹ️  Model information not available")
        }
        
        println("✅ Model loaded successfully: ${wrapper.isModelLoaded()}")
        println()
    }
    
    /**
     * Run predefined examples to demonstrate various capabilities.
     */
    private fun runPredefinedExamples(wrapper: LlamaWrapper) {
        println("=".repeat(50))
        println("🎯 Predefined Examples")
        println("=".repeat(50))
        
        val examples = listOf(
            Example(
                title = "Simple Question",
                prompt = "What is the capital of France?",
                maxTokens = 50
            ),
            Example(
                title = "Creative Writing",
                prompt = "Write a short story about a robot learning to paint:",
                maxTokens = 150,
                temperature = 0.9f
            ),
            Example(
                title = "Code Generation",
                prompt = "Write a Python function to calculate the factorial of a number:",
                maxTokens = 100,
                temperature = 0.3f
            ),
            Example(
                title = "Explanation",
                prompt = "Explain quantum computing in simple terms:",
                maxTokens = 120,
                temperature = 0.7f
            ),
            Example(
                title = "Math Problem",
                prompt = "If I have 15 apples and give away 7, then buy 12 more, how many apples do I have?",
                maxTokens = 80,
                temperature = 0.1f
            )
        )
        
        examples.forEachIndexed { index, example ->
            runExample(wrapper, index + 1, example)
            if (index < examples.size - 1) {
                println("\n" + "-".repeat(30))
                Thread.sleep(1000) // Brief pause between examples
            }
        }
    }
    
    /**
     * Run a single example with proper formatting and error handling.
     */
    private fun runExample(wrapper: LlamaWrapper, number: Int, example: Example) {
        println("\n🔸 Example $number: ${example.title}")
        println("💭 Prompt: \"${example.prompt}\"")
        println("⚙️  Settings: maxTokens=${example.maxTokens}, temperature=${example.temperature}")
        println()
        
        try {
            print("🤔 Thinking... ")
            val startTime = System.currentTimeMillis()
            
            val response = wrapper.generateText(
                prompt = example.prompt,
                maxTokens = example.maxTokens,
                temperature = example.temperature,
                topP = 0.9f,
                topK = 40
            )
            
            val generateTime = System.currentTimeMillis() - startTime
            println("✅ Generated! (${generateTime}ms)")
            println()
            println("🤖 Response:")
            println("┌" + "─".repeat(78) + "┐")
            
            // Format response with proper line wrapping
            val lines = wrapText(response.trim(), 76)
            lines.forEach { line ->
                println("│ ${line.padEnd(76)} │")
            }
            
            println("└" + "─".repeat(78) + "┘")
            println("📊 Stats: ${response.length} characters, ~${response.split("\\s+".toRegex()).size} words")
            
        } catch (e: Exception) {
            println("❌ Error generating response: ${e.message}")
        }
    }
    
    /**
     * Interactive mode allowing user to input custom prompts.
     */
    private fun runInteractiveMode(wrapper: LlamaWrapper) {
        println("\n" + "=".repeat(50))
        println("🎮 Interactive Mode")
        println("=".repeat(50))
        println("Enter your prompts below. Type 'quit', 'exit', or 'q' to stop.")
        println("Type 'help' for available commands.")
        println()
        
        val scanner = Scanner(System.`in`)
        var maxTokens = 150
        var temperature = 0.8f
        
        while (true) {
            print("💬 You: ")
            val input = scanner.nextLine().trim()
            
            when (input.lowercase()) {
                "quit", "exit", "q" -> {
                    println("👋 Goodbye!")
                    break
                }
                "help" -> {
                    showHelp()
                    continue
                }
                "settings" -> {
                    showCurrentSettings(maxTokens, temperature)
                    continue
                }
                "" -> {
                    println("⚠️  Please enter a prompt or command.")
                    continue
                }
            }
            
            // Check for settings commands
            if (input.startsWith("set ")) {
                val result = handleSettingsCommand(input, maxTokens, temperature)
                maxTokens = result.first
                temperature = result.second
                continue
            }
            
            // Generate response
            try {
                print("🤖 Assistant: ")
                val startTime = System.currentTimeMillis()
                
                val response = wrapper.generateText(
                    prompt = input,
                    maxTokens = maxTokens,
                    temperature = temperature
                )
                
                val generateTime = System.currentTimeMillis() - startTime
                println(response.trim())
                println("⏱️  Generated in ${generateTime}ms")
                println()
                
            } catch (e: Exception) {
                println("❌ Error: ${e.message}")
                println()
            }
        }
    }
    
    /**
     * Show help information for interactive mode.
     */
    private fun showHelp() {
        println("""
            |📖 Available Commands:
            |  help                    - Show this help message
            |  settings               - Show current generation settings
            |  set tokens <number>    - Set max tokens (e.g., 'set tokens 200')
            |  set temperature <float> - Set temperature (e.g., 'set temperature 0.7')
            |  quit/exit/q           - Exit interactive mode
            |
            |💡 Tips:
            |  - Lower temperature (0.1-0.3) for more focused responses
            |  - Higher temperature (0.7-1.0) for more creative responses
            |  - Adjust max tokens based on desired response length
        """.trimMargin())
        println()
    }
    
    /**
     * Show current generation settings.
     */
    private fun showCurrentSettings(maxTokens: Int, temperature: Float) {
        println("⚙️  Current Settings:")
        println("   Max Tokens: $maxTokens")
        println("   Temperature: $temperature")
        println()
    }
    
    /**
     * Handle settings commands in interactive mode.
     */
    private fun handleSettingsCommand(input: String, currentMaxTokens: Int, currentTemperature: Float): Pair<Int, Float> {
        val parts = input.split(" ")
        if (parts.size < 3) {
            println("⚠️  Invalid settings command. Use 'set tokens <number>' or 'set temperature <float>'")
            return Pair(currentMaxTokens, currentTemperature)
        }
        
        val setting = parts[1].lowercase()
        val value = parts[2]
        
        return when (setting) {
            "tokens" -> {
                try {
                    val tokens = value.toInt()
                    if (tokens in 1..2048) {
                        println("✅ Max tokens set to $tokens")
                        Pair(tokens, currentTemperature)
                    } else {
                        println("⚠️  Max tokens must be between 1 and 2048")
                        Pair(currentMaxTokens, currentTemperature)
                    }
                } catch (e: NumberFormatException) {
                    println("⚠️  Invalid number for tokens: $value")
                    Pair(currentMaxTokens, currentTemperature)
                }
            }
            "temperature" -> {
                try {
                    val temp = value.toFloat()
                    if (temp in 0.0f..2.0f) {
                        println("✅ Temperature set to $temp")
                        Pair(currentMaxTokens, temp)
                    } else {
                        println("⚠️  Temperature must be between 0.0 and 2.0")
                        Pair(currentMaxTokens, currentTemperature)
                    }
                } catch (e: NumberFormatException) {
                    println("⚠️  Invalid number for temperature: $value")
                    Pair(currentMaxTokens, currentTemperature)
                }
            }
            else -> {
                println("⚠️  Unknown setting: $setting. Use 'tokens' or 'temperature'")
                Pair(currentMaxTokens, currentTemperature)
            }
        }
    }
    
    /**
     * Format file size in human-readable format.
     */
    private fun formatFileSize(bytes: Long): String {
        val units = arrayOf("B", "KB", "MB", "GB")
        var size = bytes.toDouble()
        var unitIndex = 0
        
        while (size >= 1024 && unitIndex < units.size - 1) {
            size /= 1024
            unitIndex++
        }
        
        return "%.1f %s".format(size, units[unitIndex])
    }
    
    /**
     * Wrap text to specified width for better display formatting.
     */
    private fun wrapText(text: String, width: Int): List<String> {
        if (text.length <= width) return listOf(text)
        
        val lines = mutableListOf<String>()
        val words = text.split(" ")
        var currentLine = ""
        
        for (word in words) {
            if (currentLine.isEmpty()) {
                currentLine = word
            } else if (currentLine.length + word.length + 1 <= width) {
                currentLine += " $word"
            } else {
                lines.add(currentLine)
                currentLine = word
            }
        }
        
        if (currentLine.isNotEmpty()) {
            lines.add(currentLine)
        }
        
        return lines
    }
    
    /**
     * Data class representing an example prompt with its configuration.
     */
    private data class Example(
        val title: String,
        val prompt: String,
        val maxTokens: Int = 150,
        val temperature: Float = 0.8f
    )
}
