#!/bin/bash
set -e

# Paths
WHISPER_DIR="/Users/razvan/AndroidStudioProjects/whisper.cpp"
# Jni source for useGpu
JNI_SOURCE="$(cd "$(dirname "$0")/.." && pwd)/app/src/main/cpp/native-lib.cpp"

PROJECT_ROOT="$(cd "$(dirname "$0")/.." && pwd)"
PROJECT_JNI_DIR="$PROJECT_ROOT/app/src/main/jniLibs/arm64-v8a"
BUILD_DIR="build-android-hybrid"

# Flags
FORCE_REBUILD=false
if [[ "$1" == "--force-rebuild" ]]; then FORCE_REBUILD=true; fi

# NDK path
NDK_BASE="/Users/razvan/Library/Android/sdk/ndk"
LATEST_NDK=$(ls -1 "$NDK_BASE" | sort -n | tail -1)
NDK_PATH="$NDK_BASE/$LATEST_NDK"

copy_results() {
    echo "📦 Syncing HYBRID libraries..."
    mkdir -p "$PROJECT_JNI_DIR"
    find "$WHISPER_DIR/$BUILD_DIR" -name "*.so" -exec cp {} "$PROJECT_JNI_DIR/" \;

    # Runtime dependencies
    OMP_PATH=$(find "$NDK_PATH" -name "libomp.so" | grep "aarch64" | head -n 1)
    if [ -f "$OMP_PATH" ]; then
        cp "$OMP_PATH" "$PROJECT_JNI_DIR/"
    fi
}

# Check current build
if [ -f "$PROJECT_JNI_DIR/libwhisper.so" ] && [ "$FORCE_REBUILD" = false ]; then
    # 1. Checking companion symbol
    if nm -D "$PROJECT_JNI_DIR/libwhisper.so" | grep -q "WhisperLib_00024Companion_initContext"; then

        # 2. Checking if sources are newer
        SOURCE_NEWER=false
        # Verificăm native-lib.cpp
        if [ "$JNI_SOURCE" -nt "$PROJECT_JNI_DIR/libwhisper.so" ]; then
            SOURCE_NEWER=true
        fi

        # Checking whisper.cpp
        if [ "$(find "$WHISPER_DIR/src" -name "*.cpp" -newer "$PROJECT_JNI_DIR/libwhisper.so" | wc -l)" -gt 0 ]; then
            SOURCE_NEWER=true
        fi

        if [ "$SOURCE_NEWER" = false ]; then
            echo "🍏 Whisper is already compiled and up to date. Skipping build."
            exit 0
        fi
    fi
fi

echo "🚀 Rebuilding libwhisper.so with JNI Patch (Vulkan + NEON)..."

cd "$WHISPER_DIR"
# CMake will take care of BUILD_DIR
mkdir -p "$BUILD_DIR"

BREW_PREFIX=$(brew --prefix)
VULKAN_INC="$BREW_PREFIX/include"
SPIRV_DIR="$BREW_PREFIX/share/cmake/SPIRV-Headers"

COMMON_FLAGS="-O3 -march=armv8-a+simd+crypto -ffast-math -fno-finite-math-only -I$VULKAN_INC"

cmake -B "$BUILD_DIR" \
  -DCMAKE_TOOLCHAIN_FILE="$NDK_PATH/build/cmake/android.toolchain.cmake" \
  -DANDROID_ABI=arm64-v8a \
  -DANDROID_PLATFORM=android-33 \
  -DGGML_VULKAN=OFF \
  -DGGML_OPENMP=ON \
  -DBUILD_SHARED_LIBS=ON \
  -DWHISPER_BUILD_TESTS=OFF \
  -DWHISPER_BUILD_EXAMPLES=OFF \
  -DCMAKE_BUILD_TYPE=Release \
  -DSPIRV-Headers_DIR="$SPIRV_DIR" \
  -DCMAKE_CXX_FLAGS="$COMMON_FLAGS" \
  -DCMAKE_C_FLAGS="$COMMON_FLAGS" \
  -DWHISPER_EXTRA_SOURCES="$JNI_SOURCE"

cmake --build "$BUILD_DIR" --config Release -j 8

copy_results
echo "✅ Process complete. All pieces are synced."
