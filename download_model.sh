#!/bin/bash

# Qwen3-4B GGUF Model Download Script
# This script downloads the Qwen3-4B model in GGUF format from Hugging Face
# Provides integrity verification and fallback download methods

set -e  # Exit on any error

# Configuration
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
MODELS_DIR="${SCRIPT_DIR}/models"
REPO_ID="Qwen/Qwen3-0.6B-GGUF"

# Model configuration
MODEL_FILENAME="Qwen3-0.6B-Q8_0.gguf"

# Color codes for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# Download URLs for the model
DOWNLOAD_URLS=(
    "https://huggingface.co/Qwen/Qwen3-0.6B-GGUF/resolve/main/${MODEL_FILENAME}?download=true"
    "https://huggingface.co/Qwen/Qwen3-0.6B-GGUF/resolve/main/${MODEL_FILENAME}"
)

# Function to print colored output
print_info() {
    echo -e "${BLUE}[INFO]${NC} $1"
}

print_success() {
    echo -e "${GREEN}[SUCCESS]${NC} $1"
}

print_warning() {
    echo -e "${YELLOW}[WARNING]${NC} $1"
}

print_error() {
    echo -e "${RED}[ERROR]${NC} $1"
}

# Function to show usage
show_usage() {
    echo "Usage: $0 [OPTIONS]"
    echo ""
    echo "Options:"
    echo "  -d, --directory DIR         Download directory (default: ./models)"
    echo "  -f, --force                 Force re-download even if file exists"
    echo "  --fallback                  Use fallback download method (wget/curl)"
    echo "  -h, --help                  Show this help message"
    echo ""
    echo "Model:"
    echo "  $MODEL_FILENAME"
    echo ""
    echo "Examples:"
    echo "  $0                          # Download the model"
    echo "  $0 -d /path/to/models       # Download to specific directory"
    echo "  $0 --fallback               # Use fallback download method"
}

# Function to format file size
format_size() {
    local bytes=$1
    if command -v numfmt &> /dev/null; then
        numfmt --to=iec "$bytes"
    else
        # Fallback formatting
        if [ "$bytes" -gt 1073741824 ]; then
            echo "$(($bytes / 1073741824))GB"
        elif [ "$bytes" -gt 1048576 ]; then
            echo "$(($bytes / 1048576))MB"
        elif [ "$bytes" -gt 1024 ]; then
            echo "$(($bytes / 1024))KB"
        else
            echo "${bytes}B"
        fi
    fi
}

# Function to check dependencies for HuggingFace CLI
check_hf_dependencies() {
    print_info "Checking HuggingFace CLI dependencies..."
    
    # Check if Python is installed
    if ! command -v python3 &> /dev/null && ! command -v python &> /dev/null; then
        print_warning "Python not found. Will try fallback download method."
        return 1
    fi
    
    # Check if pip is installed
    if ! command -v pip3 &> /dev/null && ! command -v pip &> /dev/null; then
        print_warning "pip not found. Will try fallback download method."
        return 1
    fi
    
    # Check if huggingface-hub is installed
    if ! python3 -c "import huggingface_hub" 2>/dev/null && ! python -c "import huggingface_hub" 2>/dev/null; then
        print_info "Installing huggingface_hub..."
        if command -v pip3 &> /dev/null; then
            pip3 install -U "huggingface_hub[cli]" || {
                print_warning "Failed to install huggingface_hub. Will try fallback download method."
                return 1
            }
        else
            pip install -U "huggingface_hub[cli]" || {
                print_warning "Failed to install huggingface_hub. Will try fallback download method."
                return 1
            }
        fi
    fi
    
    # Check if huggingface-cli is available
    if ! command -v huggingface-cli &> /dev/null; then
        print_warning "huggingface-cli not found. Will try fallback download method."
        return 1
    fi
    
    print_success "HuggingFace CLI dependencies satisfied"
    return 0
}

# Function to create models directory
create_models_directory() {
    if [ ! -d "$MODELS_DIR" ]; then
        print_info "Creating models directory: $MODELS_DIR"
        mkdir -p "$MODELS_DIR" || {
            print_error "Failed to create models directory: $MODELS_DIR"
            exit 1
        }
    fi
}

# Function to download using HuggingFace CLI
download_with_hf_cli() {
    local filename="$1"
    local filepath="$MODELS_DIR/$filename"
    
    print_info "Downloading with HuggingFace CLI: $filename"
    print_info "Repository: $REPO_ID"
    print_info "Target directory: $MODELS_DIR"
    
    # Download with progress and error handling
    if huggingface-cli download \
        "$REPO_ID" \
        --include "$filename" \
        --local-dir "$MODELS_DIR" \
        --local-dir-use-symlinks False; then
        
        # Verify file was downloaded and has reasonable size
        if [ -f "$filepath" ]; then
            local filesize=$(stat -c%s "$filepath" 2>/dev/null || stat -f%z "$filepath" 2>/dev/null || echo "0")
            if [ "$filesize" -gt 100000000 ]; then  # At least 100MB for model files
                print_success "Successfully downloaded: $filename ($(format_size $filesize))"
                return 0
            else
                print_error "Downloaded file appears to be corrupted (size: $filesize bytes)"
                rm -f "$filepath"
                return 1
            fi
        else
            print_error "Download completed but file not found: $filepath"
            return 1
        fi
    else
        print_error "Failed to download $filename with HuggingFace CLI"
        return 1
    fi
}

# Function to download using wget fallback
download_with_wget() {
    local url="$1"
    local filename="$2"
    local filepath="$MODELS_DIR/$filename"
    
    print_info "Downloading with wget: $filename"
    print_info "URL: $url"
    
    if command -v wget &> /dev/null; then
        # Add progress bar and timeout
        if wget --progress=bar --timeout=30 --tries=3 -O "$filepath" "$url"; then
            # Verify file was downloaded and has reasonable size
            if [ -f "$filepath" ]; then
                local filesize=$(stat -c%s "$filepath" 2>/dev/null || stat -f%z "$filepath" 2>/dev/null || echo "0")
                if [ "$filesize" -gt 100000000 ]; then  # At least 100MB for model files
                    print_success "Successfully downloaded: $filename ($(format_size $filesize))"
                    return 0
                else
                    print_error "Downloaded file appears to be corrupted (size: $filesize bytes)"
                    rm -f "$filepath"
                    return 1
                fi
            else
                print_error "Download completed but file not found: $filepath"
                return 1
            fi
        else
            print_error "Failed to download with wget from: $url"
            return 1
        fi
    elif command -v curl &> /dev/null; then
        print_info "wget not found, trying curl..."
        # Add progress bar and follow redirects
        if curl -L --progress-bar --max-time 1800 --retry 3 -o "$filepath" "$url"; then
            # Verify file was downloaded and has reasonable size
            if [ -f "$filepath" ]; then
                local filesize=$(stat -c%s "$filepath" 2>/dev/null || stat -f%z "$filepath" 2>/dev/null || echo "0")
                if [ "$filesize" -gt 100000000 ]; then  # At least 100MB for model files
                    print_success "Successfully downloaded: $filename ($(format_size $filesize))"
                    return 0
                else
                    print_error "Downloaded file appears to be corrupted (size: $filesize bytes)"
                    rm -f "$filepath"
                    return 1
                fi
            else
                print_error "Download completed but file not found: $filepath"
                return 1
            fi
        else
            print_error "Failed to download with curl from: $url"
            return 1
        fi
    else
        print_error "Neither wget nor curl is available for fallback download"
        return 1
    fi
}

# Function to download the model
download_model() {
    local filepath="$MODELS_DIR/$MODEL_FILENAME"
    
    # Check if file already exists and force flag is not set
    if [ -f "$filepath" ] && [ "$FORCE_DOWNLOAD" != "true" ]; then
        print_warning "Model already exists: $filepath"
        print_info "Use -f/--force to re-download"
        return 0
    fi
    
    # Try HuggingFace CLI first if not forced to use fallback
    if [ "$USE_FALLBACK" != "true" ] && check_hf_dependencies; then
        if download_with_hf_cli "$MODEL_FILENAME"; then
            return 0
        fi
        print_warning "HuggingFace CLI download failed, trying direct URLs..."
    fi
    
    # Fallback to wget/curl download
    print_info "Using direct download method..."
    for url in "${DOWNLOAD_URLS[@]}"; do
        print_info "Trying: $url"
        if download_with_wget "$url" "$MODEL_FILENAME"; then
            return 0
        fi
    done
    
    print_error "All download methods failed"
    return 1
}

# Parse command line arguments
FORCE_DOWNLOAD=false
USE_FALLBACK=false

while [[ $# -gt 0 ]]; do
    case $1 in
        -d|--directory)
            MODELS_DIR="$2"
            shift 2
            ;;
        -f|--force)
            FORCE_DOWNLOAD=true
            shift
            ;;
        --fallback)
            USE_FALLBACK=true
            shift
            ;;
        -h|--help)
            show_usage
            exit 0
            ;;
        *)
            print_error "Unknown option: $1"
            show_usage
            exit 1
            ;;
    esac
done

# Main execution
main() {
    print_info "Qwen3 GGUF Model Download Script"
    echo ""
    
    # Create models directory
    create_models_directory
    echo ""
    
    # Download model
    download_model
    
    echo ""
    print_success "Download script completed!"
    print_info "Model is available in: $MODELS_DIR"
    
    # Show downloaded files
    echo ""
    print_info "Downloaded files:"
    if [ -f "$MODELS_DIR/$MODEL_FILENAME" ]; then
        ls -lh "$MODELS_DIR/$MODEL_FILENAME"
    fi
    
    # Show any existing model files
    if ls "$MODELS_DIR"/*.gguf 1> /dev/null 2>&1; then
        echo ""
        print_info "All GGUF model files in $MODELS_DIR:"
        ls -lh "$MODELS_DIR"/*.gguf
    fi
}

# Run main function
main "$@"