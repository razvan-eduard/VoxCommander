cmake_minimum_required(VERSION 3.22)

project(whisper_android)

set(CMAKE_CXX_STANDARD 17)

# ENABLE Vulkan for Hybrid Engine
set(GGML_VULKAN ON CACHE BOOL "" FORCE)

# Disable examples and tests
set(WHISPER_BUILD_EXAMPLES OFF CACHE BOOL "" FORCE)
set(WHISPER_BUILD_TESTS OFF CACHE BOOL "" FORCE)

# Add whisper.cpp source
add_subdirectory(whisper.cpp)

# Define our JNI Bridge Library
add_library(
    whisper_jni
    SHARED
    whisper.cpp/examples/whisper.android/lib/src/main/jni/whisper/jni.c
)

find_library(log-lib log)
find_library(android-lib android)

# Link JNI bridge with Whisper core and Android libs
target_link_libraries(
    whisper_jni
    whisper
    ${log-lib}
    ${android-lib}
)
