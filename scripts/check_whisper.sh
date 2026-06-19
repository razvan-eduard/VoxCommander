#!/bin/bash
set -e

# --- COLOR DEFINITIONS ---
RED='\033[0;31m'
GREEN='\033[0;32m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# --- PATH CONFIGURATION ---
PROJECT_ROOT="$(cd "$(dirname "$0")/.." && pwd)"
WHISPER_DIR="$PROJECT_ROOT/app/src/main/cpp/whisper.cpp"
JNI_SOURCE="$PROJECT_ROOT/app/src/main/cpp/native-lib.cpp"
PROJECT_JNI_DIR="$PROJECT_ROOT/app/src/main/jniLibs/arm64-v8a"
BUILD_DIR="build-android-hybrid"
VERSION_FILE="$PROJECT_ROOT/scripts/.whisper_version"

# --- ARGUMENT PARSING ---
FORCE_REBUILD=false
for arg in "$@"; do
    case $(echo "$arg" | tr '[:upper:]' '[:lower:]') in
        --force|--force-rebuild|-f) FORCE_REBUILD=true ;;
    esac
done

# --- SOURCE SELF-HEALING (GIT) ---
if [ ! -f "$WHISPER_DIR/CMakeLists.txt" ]; then
    echo -e "${BLUE}🔄 Whisper sources missing. Initializing submodule...${NC}"
    git submodule update --init --recursive app/src/main/cpp/whisper.cpp
fi

# --- HOMEBREW & DEPENDENCY AUTO-MANAGEMENT ---
if ! command -v brew >/dev/null 2>&1; then
    echo -e "${RED}⚠️ Homebrew not found.${NC}"
    read -p "❓ Do you want to install Homebrew? (y/n) " -n 1 -r
    echo
    if [[ $REPLY =~ ^[Yy]$ ]]; then
        echo -e "${BLUE}📦 Installing Homebrew...${NC}"
        /bin/bash -c "$(curl -fsSL https://raw.githubusercontent.com/Homebrew/install/HEAD/install.sh)"
    else
        echo -e "${RED}❌ ERROR: Homebrew is required for dependency management. Falling back to /usr/local.${NC}"
        VULKAN_HEADERS_BASE="/usr/local"
        SPIRV_HEADERS_BASE="/usr/local"
    fi
fi

if command -v brew >/dev/null 2>&1; then
    echo "🍺 Homebrew detected. Ensuring dependencies are installed..."

    # Check and install required packages
    for pkg in vulkan-headers spirv-headers shaderc; do
        if ! brew list $pkg >/dev/null 2>&1; then
            echo -e "${BLUE}📦 Installing $pkg...${NC}"
            brew install $pkg
        fi
    done

    VULKAN_HEADERS_BASE=$(brew --prefix vulkan-headers)
    SPIRV_HEADERS_BASE=$(brew --prefix spirv-headers)
    SHADERC_BASE=$(brew --prefix shaderc)
else
    VULKAN_HEADERS_BASE="/usr/local"
    SPIRV_HEADERS_BASE="/usr/local"
    SHADERC_BASE="/usr/local"
fi

VULKAN_INC="$VULKAN_HEADERS_BASE/include"
SPIRV_INC="$SPIRV_HEADERS_BASE/include"
SPIRV_CMAKE="$SPIRV_HEADERS_BASE/share/cmake/SPIRV-Headers"
GLSLC_PATH="$SHADERC_BASE/bin/glslc"

# --- DYNAMIC NDK DETECTION ---
if [ -n "$ANDROID_NDK_HOME" ] && [ -d "$ANDROID_NDK_HOME" ]; then
    NDK_PATH="$ANDROID_NDK_HOME"
elif [ -n "$ANDROID_HOME" ] && [ -d "$ANDROID_HOME/ndk" ]; then
    LATEST_NDK=$(ls -1 "$ANDROID_HOME/ndk" | sort -V | tail -1)
    NDK_PATH="$ANDROID_HOME/ndk/$LATEST_NDK"
else
    USER_HOME=$(eval echo "~$USER")
    NDK_BASE="$USER_HOME/Library/Android/sdk/ndk"
    if [ -d "$NDK_BASE" ]; then
        LATEST_NDK=$(ls -1 "$NDK_BASE" | sort -V | tail -1)
        NDK_PATH="$NDK_BASE/$LATEST_NDK"
    else
        echo -e "${RED}❌ ERROR: Android NDK not found. Please set ANDROID_NDK_HOME.${NC}"
        exit 1
    fi
fi

# --- PATH VALIDATION ---
validate_path() {
    if [ ! -e "$1" ]; then
        echo -e "${RED}❌ ERROR: Critical path missing: $1${NC}"
        if [[ "$1" == *vulkan* ]] || [[ "$1" == *spirv* ]] || [[ "$1" == *glslc* ]]; then
            echo -e "${BLUE}💡 TIP: Try running: brew install vulkan-headers spirv-headers shaderc${NC}"
        fi
        exit 1
    fi
}

echo "🔍 Validating components..."
validate_path "$WHISPER_DIR"
validate_path "$JNI_SOURCE"
validate_path "$NDK_PATH"
validate_path "$VULKAN_INC/vulkan/vulkan.hpp"
validate_path "$SPIRV_INC/spirv/unified1/spirv.hpp"
validate_path "$SPIRV_CMAKE/SPIRV-HeadersConfig.cmake"
validate_path "$GLSLC_PATH"

copy_results() {
    echo "📦 Syncing compiled libraries..."
    mkdir -p "$PROJECT_JNI_DIR"
    find . -name "libwhisper.so" -exec cp {} "$PROJECT_JNI_DIR/" \;
    find . -name "libggml*.so" -exec cp {} "$PROJECT_JNI_DIR/" \;

    OMP_PATH=$(find "$NDK_PATH" -name "libomp.so" | grep "aarch64" | head -n 1)
    if [ -f "$OMP_PATH" ]; then
        cp "$OMP_PATH" "$PROJECT_JNI_DIR/"
    fi

    echo "$CURRENT_VERSION" > "$VERSION_FILE"
}

# --- VERSION TRACKING ---
CURRENT_VERSION=$(grep "project(\"whisper.cpp\" VERSION" "$WHISPER_DIR/CMakeLists.txt" | grep -oE "[0-9]+\.[0-9]+\.[0-9]+")

if [ -f "$VERSION_FILE" ]; then
    OLD_VERSION=$(cat "$VERSION_FILE")
else
    OLD_VERSION="unknown"
fi

if [ "$CURRENT_VERSION" != "$OLD_VERSION" ] && [ "$OLD_VERSION" != "unknown" ]; then
    echo -e "🆕 UPGRADE DETECTED: Whisper.cpp ${RED}$OLD_VERSION${NC} -> ${GREEN}$CURRENT_VERSION${NC}"
    FORCE_REBUILD=true
else
    echo -e "ℹ️ Current Whisper.cpp version: ${GREEN}$CURRENT_VERSION${NC}"
fi

# --- BUILD EXECUTION ---
FULL_BUILD_PATH="$WHISPER_DIR/$BUILD_DIR"

if [ "$FORCE_REBUILD" = true ]; then
    echo -e "${RED}🔥 Force rebuild requested or upgrade detected. Cleaning build directory...${NC}"
    rm -rf "${FULL_BUILD_PATH:?}"
fi

mkdir -p "$FULL_BUILD_PATH"
cd "$FULL_BUILD_PATH"

# If CMakeCache.txt is missing, we must CONFIGURE before building
if [ ! -f "CMakeCache.txt" ]; then
    echo -e "⚙️ Configuring CMake for the first time or after clean..."
    echo -e "🔹 Building with: ${BLUE}GGML_VULKAN=ON (HIBRID)${NC}"

    cmake ../.. \
      -DCMAKE_TOOLCHAIN_FILE="$NDK_PATH/build/cmake/android.toolchain.cmake" \
      -DANDROID_ABI=arm64-v8a \
      -DANDROID_PLATFORM=android-33 \
      -DCMAKE_BUILD_TYPE=Release \
      -DVULKAN_HEADERS_DIR="$VULKAN_INC" \
      -DSPIRV_HEADERS_CMAKE_DIR="$SPIRV_CMAKE" \
      -DVULKAN_GLSLC_EXECUTABLE="$GLSLC_PATH"
fi

echo -e "🚀 Initializing Whisper build (${GREEN}$CURRENT_VERSION${NC})..."
cmake --build . --config Release -j 8

copy_results
echo -e "${GREEN}✅ Build and synchronization complete.${NC}"
