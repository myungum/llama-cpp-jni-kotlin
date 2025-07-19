# Llama.cpp + JNI + Kotlin Integration

A Kotlin application that integrates with llama.cpp using JNI to run GGUF models locally.

## Requirements

- Ubuntu/Debian x86_64
- JDK 17+
- CMake 3.16+
- Build essentials (gcc, make, etc.)

## Quick Start

1. **Install dependencies**
```bash
sudo apt install build-essential cmake git wget curl openjdk-17-jdk
```

2. **Clone and build**
```bash
git clone --recursive <repository-url>
cd traycer_test
chmod +x build.sh
./build.sh
```

3. **Run the application**
```bash
./build/run.sh
```

## Project Structure

```
traycer_test/
├── build.sh                    # Main build script
├── download_model.sh           # Model download script
├── native/                     # JNI wrapper code
├── kotlin-app/                 # Kotlin application
├── llama.cpp/                  # Git submodule
└── models/                     # GGUF model files
```

## Features

- **Interactive Demo**: Chat with the model directly
- **Automated Test Suite**: Run predefined prompts for testing
- **Model Management**: Easy model downloading and switching
- **Deployment Package**: Self-contained deployment artifacts

## Usage

### Interactive Mode
```bash
./build/run.sh
```

### Test Suite
```bash
./build/run_prompt_suite.sh
```

### Using in Your Kotlin Project

After building, you can use the JNI wrapper in your own Kotlin applications:

```kotlin
import com.traycer.llama.LlamaWrapper
import com.traycer.llama.ModelConfig

fun main() {
    val wrapper = LlamaWrapper()
    
    try {
        // Load model
        val success = wrapper.loadModel(
            modelPath = ModelConfig.MODEL_PATH,
            contextSize = 2048,
            threads = -1  // Auto-detect
        )
        
        if (success) {
            println("Model loaded successfully!")
            
            // Generate text
            val response = wrapper.generateText(
                prompt = "Hello, how are you?",
                maxTokens = 100,
                temperature = 0.8f,
                topP = 0.9f,
                topK = 40
            )
            
            println("Response: $response")
        }
    } finally {
        wrapper.cleanup()
    }
}
```

**Required files for your project:**
- `libllama.so` and `libllama_jni.so` (from `build/` directory)
- Model file (from `models/` directory)
- Set JVM library path: `-Djava.library.path=/path/to/native/libs`

### Custom Models
Edit `ModelConfig.kt` to use different GGUF models:
```kotlin
const val MODEL_FILENAME = "your-model.gguf"
```

## Build Options

```bash
./build.sh --help              # Show all options
./build.sh --clean             # Clean build directories
./build.sh --no-model          # Skip model download
./build.sh --test-prompts      # Run test suite after build
```

## Troubleshooting

**Build fails**: Check that all dependencies are installed
```bash
java -version && cmake --version && gcc --version
```

**Model loading fails**: Verify model file exists and is readable
```bash
ls -la models/
```

**JNI errors**: Set JAVA_HOME properly
```bash
export JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64
```

---

*This project was created with [Claude Code](https://claude.ai/code) and [Cursor](https://cursor.sh)*