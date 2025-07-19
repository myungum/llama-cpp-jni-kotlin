#!/bin/bash

# Comprehensive build script for llama.cpp + JNI + Kotlin integration
# This script handles the complete build process from source to executable

set -e  # Exit on any error

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# Model configuration
MODEL_FILENAME="Qwen3-0.6B-Q8_0.gguf"

# Logging functions
log_info() {
    echo -e "${BLUE}[INFO]${NC} $1"
}

log_success() {
    echo -e "${GREEN}[SUCCESS]${NC} $1"
}

log_warning() {
    echo -e "${YELLOW}[WARNING]${NC} $1"
}

log_error() {
    echo -e "${RED}[ERROR]${NC} $1"
}

# Configuration
PROJECT_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
LLAMA_CPP_DIR="${PROJECT_ROOT}/llama.cpp"
NATIVE_DIR="${PROJECT_ROOT}/native"
KOTLIN_APP_DIR="${PROJECT_ROOT}/kotlin-app"
MODELS_DIR="${PROJECT_ROOT}/models"
BUILD_DIR="${PROJECT_ROOT}/build"
DEPLOY_DIR="${PROJECT_ROOT}/deploy"
TEMP_TEST_DIR=""

# Check if running on Ubuntu x86_64
check_system() {
    log_info "Checking system requirements..."
    
    if [[ "$(uname -s)" != "Linux" ]]; then
        log_error "This script is designed for Linux systems"
        exit 1
    fi
    
    if [[ "$(uname -m)" != "x86_64" ]]; then
        log_error "This script requires x86_64 architecture"
        exit 1
    fi
    
    # Check if we're on Ubuntu/Debian
    if ! command -v apt &> /dev/null; then
        log_warning "This script is optimized for Ubuntu/Debian systems"
    fi
    
    log_success "System check passed"
}

# Check system dependencies
check_dependencies() {
    log_info "Checking system dependencies..."
    
    local missing_deps=()
    
    # Check build essentials
    if ! command -v gcc &> /dev/null; then
        missing_deps+=("build-essential")
    fi
    
    if ! command -v cmake &> /dev/null; then
        missing_deps+=("cmake")
    fi
    
    if ! command -v git &> /dev/null; then
        missing_deps+=("git")
    fi
    
    if ! command -v wget &> /dev/null; then
        missing_deps+=("wget")
    fi
    
    if ! command -v curl &> /dev/null; then
        missing_deps+=("curl")
    fi
    
    if ! command -v pkg-config &> /dev/null; then
        missing_deps+=("pkg-config")
    fi
    
    if ! command -v python3 &> /dev/null; then
        missing_deps+=("python3")
    fi
    
    if ! command -v pip3 &> /dev/null; then
        missing_deps+=("python3-pip")
    fi
    
    # Check Java JDK
    if ! command -v javac &> /dev/null; then
        missing_deps+=("openjdk-17-jdk")
    fi
    
    # Set JAVA_HOME if not set
    if [[ -z "$JAVA_HOME" ]]; then
        if [[ -d "/usr/lib/jvm/java-21-openjdk-amd64" ]]; then
            export JAVA_HOME="/usr/lib/jvm/java-21-openjdk-amd64"
            log_info "Set JAVA_HOME to $JAVA_HOME"
        elif [[ -d "/usr/lib/jvm/java-17-openjdk-amd64" ]]; then
            export JAVA_HOME="/usr/lib/jvm/java-17-openjdk-amd64"
            log_info "Set JAVA_HOME to $JAVA_HOME"
        elif [[ -d "/usr/lib/jvm/default-java" ]]; then
            export JAVA_HOME="/usr/lib/jvm/default-java"
            log_info "Set JAVA_HOME to $JAVA_HOME"
        else
            log_warning "JAVA_HOME not set and default paths not found"
        fi
    fi
    
    # Check if any dependencies are missing
    if [[ ${#missing_deps[@]} -gt 0 ]]; then
        log_error "Missing dependencies: ${missing_deps[*]}"
        log_error "Please run './init.sh' to install system dependencies"
        log_info "Or install manually: sudo apt install ${missing_deps[*]}"
        exit 1
    fi
    
    log_success "All dependencies are available"
}

# Clone llama.cpp repository
clone_llama_cpp() {
    log_info "Cloning llama.cpp repository..."
    
    if [[ -d "$LLAMA_CPP_DIR" ]]; then
        log_warning "llama.cpp directory already exists, pulling latest changes..."
        cd "$LLAMA_CPP_DIR"
        git pull origin master
    else
        git clone https://github.com/ggerganov/llama.cpp.git "$LLAMA_CPP_DIR"
        cd "$LLAMA_CPP_DIR"
    fi
    
    log_success "llama.cpp repository ready"
}

# Build llama.cpp as shared library
build_llama_cpp() {
    log_info "Building llama.cpp as shared library..."
    
    cd "$LLAMA_CPP_DIR"
    
    # Create build directory
    mkdir -p build
    cd build
    
    # Configure with CMake for shared library
    cmake .. \
        -DBUILD_SHARED_LIBS=ON \
        -DLLAMA_STATIC=OFF \
        -DCMAKE_BUILD_TYPE=Release \
        -DCMAKE_POSITION_INDEPENDENT_CODE=ON
    
    # Build with all available cores
    make -j$(nproc)
    
    # Verify the shared library was created
    if [[ ! -f "bin/libllama.so" ]]; then
        log_error "Failed to build libllama.so"
        exit 1
    fi
    
    log_success "llama.cpp shared library built successfully"
}

# Build JNI wrapper
build_jni_wrapper() {
    log_info "Building JNI wrapper..."
    
    cd "$NATIVE_DIR"
    
    # Create build directory
    mkdir -p build
    cd build
    
    # Configure with CMake
    cmake .. \
        -DCMAKE_BUILD_TYPE=Release \
        -DJAVA_HOME="$JAVA_HOME" \
        -DLLAMA_CPP_DIR="$LLAMA_CPP_DIR" \
        -DCMAKE_POSITION_INDEPENDENT_CODE=ON
    
    # Build the JNI library
    make -j$(nproc)
    
    # Verify the JNI library was created
    if [[ ! -f "libllama_jni.so" ]]; then
        log_error "Failed to build libllama_jni.so"
        exit 1
    fi
    
    log_success "JNI wrapper built successfully"
}

# Download Qwen3-0.6B GGUF model using dedicated download script
download_model() {
    log_info "Downloading GGUF model: $MODEL_FILENAME"
    
    # Use the globally defined model filename
    MODEL_FILE="$MODEL_FILENAME"
    
    # Check if model already exists
    if [[ -f "$MODELS_DIR/$MODEL_FILE" ]]; then
        log_warning "Model file already exists, skipping download"
        return 0
    fi
    
    # Use the dedicated download script
    if [[ -f "$PROJECT_ROOT/download_model.sh" ]]; then
        log_info "Using download_model.sh script..."
        cd "$PROJECT_ROOT"
        
        # Run the download script
        if ./download_model.sh; then
            log_success "Model downloaded successfully using download_model.sh"
        else
            log_error "Failed to download model using download_model.sh"
            exit 1
        fi
    else
        log_error "download_model.sh script not found"
        exit 1
    fi
    
    # Verify download
    if [[ -f "$MODELS_DIR/$MODEL_FILE" ]]; then
        log_success "Model verification passed"
    else
        log_error "Model file not found after download"
        exit 1
    fi
}

# Build Kotlin application
build_kotlin_app() {
    log_info "Building Kotlin application..."
    
    cd "$KOTLIN_APP_DIR"
    
    # Make gradlew executable
    chmod +x ./gradlew
    
    # Build the application
    ./gradlew build
    
    log_success "Kotlin application built successfully"
}

# Package deployment artifacts
package_deployment() {
    log_info "Creating deployment package..."
    
    # Create deployment directory structure
    mkdir -p "$DEPLOY_DIR"
    
    # Copy the fat JAR
    if [[ -f "$KOTLIN_APP_DIR/build/libs/llama-app-all.jar" ]]; then
        cp "$KOTLIN_APP_DIR/build/libs/llama-app-all.jar" "$DEPLOY_DIR/"
    else
        log_error "Fat JAR not found. Build may have failed."
        exit 1
    fi
    
    # Copy native libraries
    cp "$BUILD_DIR/libllama.so" "$DEPLOY_DIR/"
    cp "$BUILD_DIR/libllama_jni.so" "$DEPLOY_DIR/"
    
    # Copy or symlink model file
    if [[ -f "$MODELS_DIR/$MODEL_FILENAME" ]]; then
        ln -sf "$MODELS_DIR/$MODEL_FILENAME" "$DEPLOY_DIR/model.gguf"
    else
        log_error "Model file not found for deployment"
        exit 1
    fi
    
    # Generate version manifest
    cat > "$DEPLOY_DIR/VERSION" << EOF
Build Date: $(date -Iseconds)
Git Commit: $(git rev-parse --short HEAD 2>/dev/null || echo "unknown")
System: $(uname -s) $(uname -m)
Java Home: $JAVA_HOME
EOF
    
    log_success "Deployment package created in $DEPLOY_DIR"
}

# Create deployment scripts
create_deployment_scripts() {
    log_info "Creating deployment scripts..."
    
    # Scripts will be created as separate files
    # This function will set proper permissions
    
    # Set executable permissions for deployment scripts
    find "$DEPLOY_DIR" -name "*.sh" -exec chmod +x {} \; 2>/dev/null || true
    
    log_success "Deployment scripts configured"
}

# Smoke test deployment
smoke_test_deployment() {
    log_info "Running deployment smoke test..."
    
    # Create temporary directory
    TEMP_TEST_DIR=$(mktemp -d)
    
    # Set trap for cleanup
    trap 'rm -rf "$TEMP_TEST_DIR"; log_info "Cleaned up temporary test directory"' EXIT
    
    # Copy deployment artifacts to temp directory
    cp -r "$DEPLOY_DIR"/* "$TEMP_TEST_DIR/"
    
    # Run smoke test in temporary location
    cd "$TEMP_TEST_DIR"
    
    # Execute quick smoke test
    if [[ -f "smoke_test.sh" ]]; then
        ./smoke_test.sh
        local test_result=$?
        
        if [[ $test_result -eq 0 ]]; then
            log_success "Deployment smoke test passed"
        else
            log_error "Deployment smoke test failed with exit code $test_result"
            return 1
        fi
    else
        log_warning "Smoke test script not found, skipping automated test"
    fi
    
    cd "$PROJECT_ROOT"
}

# Setup library paths and create run script
setup_runtime() {
    log_info "Setting up runtime environment..."
    
    # Create build output directory
    mkdir -p "$BUILD_DIR"
    
    # Copy shared libraries to build directory
    cp "$LLAMA_CPP_DIR/build/bin/libllama.so" "$BUILD_DIR/"
    cp "$NATIVE_DIR/build/libllama_jni.so" "$BUILD_DIR/"
    
    # Create run script
    cat > "$BUILD_DIR/run.sh" << 'EOF'
#!/bin/bash

# Runtime script for llama.cpp + JNI + Kotlin application

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(dirname "$SCRIPT_DIR")"

# Set library path
export LD_LIBRARY_PATH="$SCRIPT_DIR:$LD_LIBRARY_PATH"

# Set Java library path
export JAVA_OPTS="-Djava.library.path=$SCRIPT_DIR"

# Run the Kotlin application
cd "$PROJECT_ROOT/kotlin-app"
./gradlew run
EOF
    
    chmod +x "$BUILD_DIR/run.sh"
    
    # Create prompt suite run script
    cat > "$BUILD_DIR/run_prompt_suite.sh" << 'EOF'
#!/bin/bash

# Runtime script for running the LLM prompt test suite

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(dirname "$SCRIPT_DIR")"

# Set library path
export LD_LIBRARY_PATH="$SCRIPT_DIR:$LD_LIBRARY_PATH"

# Set Java library path
export JAVA_OPTS="-Djava.library.path=$SCRIPT_DIR"

echo "🧪 Starting LLM Prompt Test Suite..."
echo "📁 Results will be saved to the results/ directory"
echo "⏳ This may take several minutes depending on your system..."
echo

# Run the prompt suite
cd "$PROJECT_ROOT/kotlin-app"
./gradlew runPromptSuite

echo
echo "✅ Prompt suite completed! Check the results/ directory for output."
EOF
    
    chmod +x "$BUILD_DIR/run_prompt_suite.sh"
    
    # Create environment setup script
    cat > "$BUILD_DIR/setup_env.sh" << EOF
#!/bin/bash

# Environment setup for llama.cpp + JNI + Kotlin

export JAVA_HOME="$JAVA_HOME"
export LD_LIBRARY_PATH="$BUILD_DIR:\$LD_LIBRARY_PATH"
export JAVA_OPTS="-Djava.library.path=$BUILD_DIR"

echo "Environment configured for llama.cpp + JNI + Kotlin"
echo "JAVA_HOME: \$JAVA_HOME"
echo "LD_LIBRARY_PATH: \$LD_LIBRARY_PATH"
echo "JAVA_OPTS: \$JAVA_OPTS"
echo
echo "Available run scripts:"
echo "  - $BUILD_DIR/run.sh (interactive demo)"
echo "  - $BUILD_DIR/run_prompt_suite.sh (automated test suite)"
EOF
    
    chmod +x "$BUILD_DIR/setup_env.sh"
    
    log_success "Runtime environment configured"
}

# Verify build
verify_build() {
    log_info "Verifying build..."
    
    # Check if all required files exist
    local required_files=(
        "$BUILD_DIR/libllama.so"
        "$BUILD_DIR/libllama_jni.so"
        "$BUILD_DIR/run.sh"
        "$BUILD_DIR/run_prompt_suite.sh"
        "$MODELS_DIR/$MODEL_FILENAME"
        "$KOTLIN_APP_DIR/build/libs"
    )
    
    for file in "${required_files[@]}"; do
        if [[ ! -e "$file" ]]; then
            log_error "Missing required file/directory: $file"
            exit 1
        fi
    done
    
    # Test library loading
    if ldd "$BUILD_DIR/libllama_jni.so" | grep -q "not found"; then
        log_error "JNI library has unresolved dependencies"
        ldd "$BUILD_DIR/libllama_jni.so"
        exit 1
    fi
    
    # Verify PromptSuite was compiled
    if [[ ! -f "$KOTLIN_APP_DIR/build/classes/kotlin/main/com/traycer/llama/PromptSuiteKt.class" ]]; then
        log_warning "PromptSuite.kt may not have been compiled successfully"
    fi
    
    # Check for fat JAR if deployment was requested
    if [[ "$PACKAGE_DEPLOYMENT" == "true" ]]; then
        if [[ ! -f "$KOTLIN_APP_DIR/build/libs/llama-app-all.jar" ]]; then
            log_error "Fat JAR not found"
            exit 1
        fi
        
        # Test JAR executability
        if ! java -jar "$KOTLIN_APP_DIR/build/libs/llama-app-all.jar" --help &>/dev/null; then
            log_warning "Fat JAR may not be executable (this might be expected if --help is not implemented)"
        fi
        
        # Verify deployment artifacts
        local deploy_files=(
            "$DEPLOY_DIR/llama-app-all.jar"
            "$DEPLOY_DIR/libllama.so"
            "$DEPLOY_DIR/libllama_jni.so"
            "$DEPLOY_DIR/run_llama.sh"
            "$DEPLOY_DIR/run_prompt_suite.sh"
            "$DEPLOY_DIR/README.md"
        )
        
        for file in "${deploy_files[@]}"; do
            if [[ ! -f "$file" ]]; then
                log_error "Missing deployment file: $file"
                exit 1
            fi
        done
        
        # Validate deployment script permissions
        if [[ ! -x "$DEPLOY_DIR/run_llama.sh" ]] || [[ ! -x "$DEPLOY_DIR/run_prompt_suite.sh" ]]; then
            log_error "Deployment scripts are not executable"
            exit 1
        fi
    fi
    
    log_success "Build verification passed"
}

# Print usage instructions
print_usage() {
    log_info "Build completed successfully!"
    echo
    echo "To run the application:"
    echo "  1. Source the environment: source $BUILD_DIR/setup_env.sh"
    echo "  2. Run the interactive demo: $BUILD_DIR/run.sh"
    echo "  3. Run the automated test suite: $BUILD_DIR/run_prompt_suite.sh"
    echo
    echo "Or simply run either script directly:"
    echo "  - Interactive demo: $BUILD_DIR/run.sh"
    echo "  - Automated test suite: $BUILD_DIR/run_prompt_suite.sh"
    echo
    
    if [[ "$PACKAGE_DEPLOYMENT" == "true" ]]; then
        echo "Deployment package created:"
        echo "  - Location: $DEPLOY_DIR/"
        echo "  - Run application: $DEPLOY_DIR/run_llama.sh"
        echo "  - Run test suite: $DEPLOY_DIR/run_prompt_suite.sh"
        echo "  - Smoke test: $DEPLOY_DIR/smoke_test.sh"
        echo "  - Documentation: $DEPLOY_DIR/README.md"
        echo
    fi
    
    echo "Files created:"
    echo "  - Shared libraries: $BUILD_DIR/"
    echo "  - Model file: $MODELS_DIR/$MODEL_FILENAME"
    echo "  - Kotlin application: $KOTLIN_APP_DIR/build/"
    echo "  - Run scripts: $BUILD_DIR/run*.sh"
    echo
    echo "Test suite results will be saved to: results/"
    echo
    echo "For troubleshooting, check the logs above and ensure all dependencies are installed."
}

# Cleanup function
cleanup() {
    log_info "Cleaning up temporary files..."
    # Add any cleanup tasks here if needed
}

# Main execution
main() {
    log_info "Starting llama.cpp + JNI + Kotlin build process..."
    
    # Set trap for cleanup on exit
    trap cleanup EXIT
    
    # Execute build steps
    check_system
    check_dependencies
    clone_llama_cpp
    build_llama_cpp
    build_jni_wrapper
    download_model
    build_kotlin_app
    setup_runtime
    
    # Add deployment steps if requested
    if [[ "$PACKAGE_DEPLOYMENT" == "true" ]]; then
        log_info "Creating deployment package..."
        cd "$KOTLIN_APP_DIR"
        ./gradlew packageDeployment
        cd "$PROJECT_ROOT"
        package_deployment
        create_deployment_scripts
    fi
    
    verify_build
    
    # Run smoke test if requested
    if [[ "$RUN_SMOKE_TEST" == "true" ]]; then
        smoke_test_deployment
    fi
    
    print_usage
    
    log_success "Build process completed successfully!"
    
    # Run prompt suite if requested
    if [[ "$RUN_PROMPT_SUITE" == "true" ]]; then
        log_info "Running automated prompt test suite..."
        "$BUILD_DIR/run_prompt_suite.sh"
    fi
}

# Handle command line arguments
case "${1:-}" in
    --help|-h)
        echo "Usage: $0 [options]"
        echo "Options:"
        echo "  --help, -h       Show this help message"
        echo "  --clean          Clean build directories before building"
        echo "  --no-model       Skip model download"
        echo "  --test-prompts   Run automated prompt test suite after build"
        echo "  --package        Create deployment package"
        echo "  --smoke-test     Run deployment verification"
        echo "  --deploy-dir DIR Specify custom deployment location"
        exit 0
        ;;
    --clean)
        log_info "Cleaning build directories..."
        rm -rf "$LLAMA_CPP_DIR/build" "$NATIVE_DIR/build" "$KOTLIN_APP_DIR/build" "$BUILD_DIR" "$DEPLOY_DIR"
        log_success "Clean completed"
        ;;
    --no-model)
        # Override download_model function to skip
        download_model() {
            log_info "Skipping model download as requested"
        }
        ;;
    --test-prompts)
        # Set flag to run prompt suite after build
        RUN_PROMPT_SUITE=true
        ;;
    --package)
        # Set flag to create deployment package
        PACKAGE_DEPLOYMENT=true
        ;;
    --smoke-test)
        # Set flag to run deployment smoke test
        RUN_SMOKE_TEST=true
        PACKAGE_DEPLOYMENT=true
        ;;
    --deploy-dir)
        if [[ -n "${2:-}" ]]; then
            DEPLOY_DIR="$2"
            shift
        else
            log_error "--deploy-dir requires a directory argument"
            exit 1
        fi
        ;;
esac

# Run main function
main "$@"
