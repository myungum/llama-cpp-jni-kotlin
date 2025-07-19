# Llama.cpp + JNI + Kotlin Integration

A complete implementation for integrating llama.cpp with Kotlin applications using JNI (Java Native Interface) on Ubuntu x86_64. This project demonstrates how to build llama.cpp as a shared library, create JNI bindings, and use them from a Kotlin application with the Qwen3-0.6B GGUF model.

## Table of Contents

- [Project Overview](#project-overview)
- [System Requirements](#system-requirements)
- [Project Structure](#project-structure)
- [Quick Start](#quick-start)
- [Detailed Build Instructions](#detailed-build-instructions)
- [JNI Integration Approach](#jni-integration-approach)
- [Using Different GGUF Models](#using-different-gguf-models)
- [Extending Functionality](#extending-functionality)
- [Troubleshooting](#troubleshooting)
- [Performance Considerations](#performance-considerations)
- [Contributing](#contributing)

## Project Overview

This project provides a complete solution for running Large Language Models (LLMs) in Kotlin applications by leveraging the high-performance llama.cpp library through JNI bindings. The implementation includes:

- **Native Layer**: llama.cpp compiled as a shared library with custom JNI wrapper
- **JNI Bridge**: C++ code that exposes llama.cpp functionality to the JVM
- **Kotlin Application**: High-level Kotlin API for model loading and text generation
- **Build System**: Automated scripts for building all components
- **Model Support**: Pre-configured for Qwen3-0.6B with support for other GGUF models

## System Requirements

### Hardware Requirements
- **CPU**: x86_64 architecture (Intel/AMD 64-bit)
- **RAM**: Minimum 4GB, recommended 8GB+ for larger models
- **Storage**: At least 2GB free space for models and build artifacts
- **GPU**: Optional, CUDA support available for NVIDIA GPUs

### Software Dependencies

#### Essential Build Tools
```bash
# Ubuntu/Debian
sudo apt update
sudo apt install -y build-essential cmake git wget curl

# Development tools
sudo apt install -y pkg-config autoconf libtool

# Java Development Kit
sudo apt install -y openjdk-17-jdk

# Optional: CUDA support (for GPU acceleration)
# sudo apt install -y nvidia-cuda-toolkit
```

#### Version Requirements
- **GCC**: 7.0 or later
- **CMake**: 3.16 or later
- **JDK**: 11 or later (17 recommended)
- **Kotlin**: 1.8.0 or later
- **Gradle**: 7.0 or later

## Project Structure

```
traycer_test/
├── README.md                           # This documentation
├── build.sh                           # Main build script
├── download_model.sh                  # Model download script
├── models/                            # GGUF model files
│   └── qwen3-0.6b-q4_0.gguf          # Downloaded model
├── native/                           # Native C++ code
│   ├── CMakeLists.txt                # CMake configuration
│   ├── llama_jni.h                   # JNI header declarations
│   └── llama_jni.cpp                 # JNI implementation
├── kotlin-app/                      # Kotlin application
│   ├── build.gradle.kts              # Gradle build configuration
│   ├── settings.gradle.kts           # Gradle settings
│   ├── gradlew                       # Gradle wrapper (Unix)
│   ├── gradle/wrapper/               # Gradle wrapper files
│   └── src/main/kotlin/              # Kotlin source code
│       ├── LlamaWrapper.kt           # Kotlin JNI wrapper
│       └── Main.kt                   # Main application
└── llama.cpp/                       # Cloned llama.cpp repository
    └── build/                        # CMake build directory
```

### Component Descriptions

#### Native Layer (`native/`)
- **llama_jni.cpp**: JNI implementation that bridges Kotlin calls to llama.cpp functions
- **llama_jni.h**: Header file with JNI function declarations
- **CMakeLists.txt**: CMake configuration for building the JNI shared library

#### Kotlin Application (`kotlin-app/`)
- **LlamaWrapper.kt**: Kotlin class that loads the native library and provides high-level API
- **Main.kt**: Example application demonstrating model usage
- **build.gradle.kts**: Gradle build configuration with Kotlin setup

#### Models (`models/`)
- Storage location for GGUF model files
- Pre-configured for Qwen3-0.6B but supports any compatible GGUF model

## Quick Start

### 1. Clone and Build
```bash
# Make build script executable
chmod +x build.sh

# Run the complete build process
./build.sh
```

### 2. Run the Application
```bash
cd kotlin-app
./gradlew run
```

### 3. Expected Output
```
Loading model: ../models/qwen3-0.6b-q4_0.gguf
Model loaded successfully!
Generating text for prompt: "Hello, how are you?"
Generated: Hello, how are you? I'm doing well, thank you for asking! How can I help you today?
```

## Detailed Build Instructions

### Step 1: Environment Setup
```bash
# Verify Java installation
java -version
javac -version

# Verify build tools
gcc --version
cmake --version
git --version
```

### Step 2: Clone and Build llama.cpp
```bash
# Clone llama.cpp repository
git clone https://github.com/ggml-org/llama.cpp.git
cd llama.cpp

# Create build directory
mkdir build && cd build

# Configure with CMake
cmake .. -DCMAKE_BUILD_TYPE=Release \
         -DBUILD_SHARED_LIBS=ON \
         -DLLAMA_BUILD_EXAMPLES=OFF \
         -DLLAMA_BUILD_TESTS=OFF

# Build the shared library
make -j$(nproc)
cd ../..
```

### Step 3: Build JNI Wrapper
```bash
cd native

# Create build directory
mkdir build && cd build

# Configure CMake for JNI
cmake .. -DCMAKE_BUILD_TYPE=Release

# Build the JNI library
make -j$(nproc)
cd ../..
```

### Step 4: Download Model
```bash
# Make download script executable
chmod +x download_model.sh

# Download Qwen3-0.6B model
./download_model.sh
```

### Step 5: Build Kotlin Application
```bash
cd kotlin-app

# Make Gradle wrapper executable
chmod +x gradlew

# Build the application
./gradlew build

# Run the application
./gradlew run
```

## JNI Integration Approach

### Architecture Overview

The JNI integration follows a three-layer architecture:

1. **Kotlin Layer**: High-level API using `external` functions
2. **JNI Bridge**: C++ code implementing JNI functions
3. **Native Layer**: Direct calls to llama.cpp library

### JNI Function Mapping

| Kotlin Method | JNI Function | llama.cpp Function |
|---------------|--------------|-------------------|
| `loadModel()` | `Java_LlamaWrapper_loadModel` | `llama_load_model_from_file()` |
| `generateText()` | `Java_LlamaWrapper_generateText` | `llama_decode()` + `llama_sample()` |
| `cleanup()` | `Java_LlamaWrapper_cleanup` | `llama_free()` |

### Memory Management

The JNI implementation handles memory management carefully:

- **Model Context**: Stored as a native pointer (`jlong`) in Kotlin
- **String Conversion**: UTF-8 conversion between Java strings and C++ strings
- **Resource Cleanup**: Explicit cleanup methods to prevent memory leaks
- **Error Handling**: Proper exception throwing for native errors

### Thread Safety

The current implementation is designed for single-threaded use. For multi-threaded applications:

- Use separate model contexts per thread
- Implement proper synchronization in the JNI layer
- Consider using llama.cpp's thread-safe functions

## Using Different GGUF Models

### Supported Model Formats

The implementation supports any GGUF (GPT-Generated Unified Format) model:

- **Quantization Levels**: Q4_0, Q4_1, Q5_0, Q5_1, Q8_0, F16, F32
- **Model Sizes**: From 0.5B to 70B+ parameters (limited by available RAM)
- **Model Types**: Llama, Mistral, CodeLlama, Vicuna, and other compatible architectures

### Downloading Models

#### Using the Download Script
```bash
# Download different quantization levels
./download_model.sh q4_0    # 4-bit quantization (default)
./download_model.sh q5_0    # 5-bit quantization
./download_model.sh q8_0    # 8-bit quantization
```

#### Manual Download
```bash
# Create models directory
mkdir -p models

# Download from Hugging Face
wget -O models/model.gguf "https://huggingface.co/MODEL_REPO/resolve/main/MODEL_FILE.gguf"
```

#### Popular Model Sources

| Model | Size | Quantization | Download Command |
|-------|------|--------------|------------------|
| Qwen3-0.6B | ~400MB | Q4_0 | `./download_model.sh qwen3-0.6b-q4_0` |
| Llama-3.2-1B | ~800MB | Q4_0 | `./download_model.sh llama-3.2-1b-q4_0` |
| Mistral-7B | ~4GB | Q4_0 | `./download_model.sh mistral-7b-q4_0` |

### Model Configuration

Update the model path in your Kotlin application:

```kotlin
// In Main.kt
val modelPath = "../models/your-model.gguf"
val success = wrapper.loadModel(modelPath)
```

### Model-Specific Parameters

Different models may require different generation parameters:

```kotlin
// For code generation models
val codePrompt = "def fibonacci(n):"

// For chat models
val chatPrompt = "Human: Hello!\nAssistant:"

// For instruction models
val instructPrompt = "### Instruction:\nExplain quantum computing\n### Response:"
```

## Extending Functionality

### Adding New JNI Methods

#### 1. Declare in Kotlin
```kotlin
// In LlamaWrapper.kt
external fun getModelInfo(): String
external fun setGenerationParams(temperature: Float, topP: Float)
```

#### 2. Implement in C++
```cpp
// In llama_jni.cpp
JNIEXPORT jstring JNICALL
Java_LlamaWrapper_getModelInfo(JNIEnv *env, jobject obj) {
    // Implementation here
    return env->NewStringUTF(info.c_str());
}

JNIEXPORT void JNICALL
Java_LlamaWrapper_setGenerationParams(JNIEnv *env, jobject obj, jfloat temp, jfloat top_p) {
    // Implementation here
}
```

#### 3. Update Header
```cpp
// In llama_jni.h
JNIEXPORT jstring JNICALL Java_LlamaWrapper_getModelInfo(JNIEnv *, jobject);
JNIEXPORT void JNICALL Java_LlamaWrapper_setGenerationParams(JNIEnv *, jobject, jfloat, jfloat);
```

### Advanced Features

#### Streaming Text Generation
```kotlin
// Add streaming support
external fun generateTextStream(prompt: String, callback: (String) -> Unit)
```

#### Batch Processing
```kotlin
// Process multiple prompts
external fun generateBatch(prompts: Array<String>): Array<String>
```

#### GPU Acceleration
```cpp
// Enable CUDA in CMakeLists.txt
set(LLAMA_CUDA ON)
```

### Custom Model Loaders

Create specialized loaders for different model types:

```kotlin
class ChatModelWrapper : LlamaWrapper() {
    fun chatComplete(messages: List<ChatMessage>): String {
        val prompt = formatChatPrompt(messages)
        return generateText(prompt)
    }
}
```

## Troubleshooting

### Common Build Issues

#### CMake Configuration Errors
```bash
# Error: Could not find JNI
export JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64

# Error: llama.cpp not found
# Ensure llama.cpp is built first and libraries are in the correct path
```

#### Compilation Errors
```bash
# Error: undefined reference to llama functions
# Check that llama.cpp shared library is properly linked
ldd native/build/libllama_jni.so

# Error: JNI headers not found
# Install JDK development package
sudo apt install openjdk-17-jdk-headless
```

### Runtime Issues

#### Library Loading Errors
```kotlin
// Error: java.lang.UnsatisfiedLinkError
// Check library path and dependencies
System.setProperty("java.library.path", "/path/to/native/libs")
```

#### Model Loading Failures
```bash
# Error: Failed to load model
# Check file permissions and path
ls -la models/
file models/qwen3-0.6b-q4_0.gguf
```

#### Memory Issues
```bash
# Error: Out of memory
# Increase JVM heap size
export JAVA_OPTS="-Xmx8g"

# Or use smaller quantized model
./download_model.sh qwen3-0.6b-q4_0  # Instead of q8_0
```

### Performance Issues

#### Slow Generation
- Use GPU acceleration if available
- Try different quantization levels
- Adjust context size and batch size
- Enable CPU optimizations in llama.cpp build

#### High Memory Usage
- Use smaller models or higher quantization
- Implement proper cleanup in long-running applications
- Monitor memory usage with profiling tools

### Debugging Tips

#### Enable Debug Logging
```cpp
// In llama_jni.cpp
#define DEBUG_JNI
#ifdef DEBUG_JNI
    printf("Debug: %s\n", message);
#endif
```

#### JNI Debugging
```bash
# Run with JNI checks
java -Xcheck:jni -cp . Main

# Use GDB for native debugging
gdb --args java -cp . Main
```

#### Memory Debugging
```bash
# Use Valgrind for memory leak detection
valgrind --tool=memcheck java -cp . Main
```

## Performance Considerations

### Model Selection
- **Q4_0**: Best balance of size and quality for most use cases
- **Q8_0**: Higher quality but larger size
- **F16/F32**: Highest quality but very large

### Hardware Optimization
- **CPU**: Use models that fit in available RAM
- **GPU**: Enable CUDA for significant speedup on NVIDIA GPUs
- **Storage**: Use SSD for faster model loading

### Application Optimization
- **Context Reuse**: Keep model context loaded between generations
- **Batch Processing**: Process multiple prompts together when possible
- **Streaming**: Implement streaming for better user experience

### Benchmarking
```bash
# Test generation speed
time echo "Hello world" | ./kotlin-app/gradlew run

# Memory usage monitoring
/usr/bin/time -v ./kotlin-app/gradlew run
```

## Contributing

### Development Setup
1. Fork the repository
2. Create a feature branch
3. Make changes following the coding standards
4. Test thoroughly on Ubuntu x86_64
5. Submit a pull request

### Coding Standards
- **C++**: Follow Google C++ Style Guide
- **Kotlin**: Follow Kotlin Coding Conventions
- **CMake**: Use modern CMake practices (3.16+)

### Testing
- Test with multiple model sizes and types
- Verify memory management and cleanup
- Test error handling and edge cases
- Performance testing with different hardware configurations

### Documentation
- Update README.md for new features
- Add inline code documentation
- Include usage examples
- Update troubleshooting section as needed

---

## License

This project is provided as-is for educational and development purposes. Please refer to the individual licenses of llama.cpp and other dependencies.

## Support

For issues and questions:
- Check the troubleshooting section above
- Review llama.cpp documentation
- Create an issue with detailed error information and system specifications

---

*Built with ❤️ using llama.cpp, JNI, and Kotlin*