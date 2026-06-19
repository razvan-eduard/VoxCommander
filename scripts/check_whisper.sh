#!/bin/bash
set -e

RED='\033[0;31m'
GREEN='\033[0;32m'
BLUE='\033[0;34m'
NC='\033[0m'

log_info() { printf "${GREEN}%s${NC}\n" "$1"; }
log_error() { printf "${RED}%s${NC}\n" "$1"; }

PROJECT_ROOT="$(cd "$(dirname "$0")/.." && pwd)"
WHISPER_DIR="$PROJECT_ROOT/app/src/main/cpp/whisper.cpp"
PROJECT_JNI_DIR="$PROJECT_ROOT/app/src/main/jniLibs/arm64-v8a"
BUILD_DIR="build-android-hybrid"

FORCE_REBUILD=false
for arg in "$@"; do
    if [[ "$arg" == "--force-rebuild" ]]; then FORCE_REBUILD=true; fi
done

# Dynamic paths
VULKAN_HEADERS_BASE=$(brew --prefix vulkan-headers 2>/dev/null || echo "/usr/local")
SPIRV_HEADERS_BASE=$(brew --prefix spirv-headers 2>/dev/null || echo "/usr/local")
SHADERC_BASE=$(brew --prefix shaderc 2>/dev/null || echo "/usr/local")

VULKAN_INC="$VULKAN_HEADERS_BASE/include"
SPIRV_INC="$SPIRV_HEADERS_BASE/include"
SPIRV_CMAKE="$SPIRV_HEADERS_BASE/share/cmake/SPIRV-Headers"
GLSLC_PATH="$SHADERC_BASE/bin/glslc"

USER_HOME=$(eval echo "~$USER")
NDK_BASE="$USER_HOME/Library/Android/sdk/ndk"
LATEST_NDK=$(ls -1 "$NDK_BASE" | sort -V | tail -1)
NDK_PATH="$NDK_BASE/$LATEST_NDK"

if [ "$FORCE_REBUILD" = true ]; then
    log_error "🔥 Force rebuilding..."
    rm -rf "$WHISPER_DIR/$BUILD_DIR"
fi

mkdir -p "$WHISPER_DIR/$BUILD_DIR"
cd "$WHISPER_DIR/$BUILD_DIR"

if [ ! -f "CMakeCache.txt" ]; then
    log_info "⚙️ Configuring..."
    cmake ../.. \
      -DCMAKE_TOOLCHAIN_FILE="$NDK_PATH/build/cmake/android.toolchain.cmake" \
      -DANDROID_ABI=arm64-v8a \
      -DANDROID_PLATFORM=android-33 \
      -DCMAKE_BUILD_TYPE=Release \
      -DVULKAN_HEADERS_DIR="$VULKAN_INC" \
      -DSPIRV_HEADERS_INC_DIR="$SPIRV_INC" \
      -DSPIRV_HEADERS_CMAKE_DIR="$SPIRV_CMAKE" \
      -DVulkan_GLSLC_EXECUTABLE="$GLSLC_PATH"
fi

log_info "🚀 Compiling..."
cmake --build . --config Release -j 8

log_info "📦 Syncing..."
mkdir -p "$PROJECT_JNI_DIR"
find . -name "libwhisper.so" -exec cp {} "$PROJECT_JNI_DIR/" \;
find . -name "libggml*.so" -exec cp {} "$PROJECT_JNI_DIR/" \;

log_info "✅ Done."
