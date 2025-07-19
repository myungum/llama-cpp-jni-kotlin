#!/bin/bash

# System initialization script for llama.cpp + JNI + Kotlin integration
# This script installs system-level dependencies that require sudo privileges
# Run this once before using build.sh

set -e  # Exit on any error

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

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

# Install system dependencies
install_dependencies() {
    log_info "Installing system dependencies..."
    
    # Update package list
    sudo apt update
    
    # Install build essentials
    sudo apt install -y \
        build-essential \
        cmake \
        git \
        wget \
        curl \
        pkg-config \
        python3 \
        python3-pip
    
    # Install Java JDK if not present
    if ! command -v javac &> /dev/null; then
        log_info "Installing OpenJDK 17..."
        sudo apt install -y openjdk-17-jdk
    fi
    
    # Set JAVA_HOME if not set
    if [[ -z "$JAVA_HOME" ]]; then
        export JAVA_HOME="/usr/lib/jvm/java-17-openjdk-amd64"
        echo "export JAVA_HOME=\"$JAVA_HOME\"" >> ~/.bashrc
        log_info "Set JAVA_HOME to $JAVA_HOME"
    fi
    
    # Install Hugging Face CLI for model download
    pip3 install --user huggingface_hub
    
    log_success "Dependencies installed successfully"
}

# Main execution
main() {
    log_info "Starting system initialization..."
    
    check_system
    install_dependencies
    
    log_success "System initialization completed successfully!"
    log_info "You can now run ./build.sh to build the project"
}

# Handle command line arguments
case "${1:-}" in
    --help|-h)
        echo "Usage: $0 [options]"
        echo "This script installs system-level dependencies for the llama.cpp + JNI + Kotlin project"
        echo ""
        echo "Options:"
        echo "  --help, -h       Show this help message"
        echo ""
        echo "Dependencies installed:"
        echo "  - build-essential (gcc, g++, make)"
        echo "  - cmake"
        echo "  - git, wget, curl"
        echo "  - pkg-config"
        echo "  - python3, python3-pip"
        echo "  - openjdk-17-jdk (if not present)"
        echo "  - huggingface_hub (Python package)"
        exit 0
        ;;
esac

# Run main function
main "$@" 