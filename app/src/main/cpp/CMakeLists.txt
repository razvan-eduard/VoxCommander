cmake_minimum_required(VERSION 3.22)

project(whisper_android)

set(CMAKE_CXX_STANDARD 17)

# Disable Vulkan (Adreno driver incompatible with ggml-vulkan shaders)
set(GGML_VULKAN OFF CACHE BOOL "" FORCE)

# Disable examples
set(WHISPER_BUILD_EXAMPLES OFF CACHE BOOL "" FORCE)
set(WHISPER_BUILD_TESTS OFF CACHE BOOL "" FORCE)

add_subdirectory(whisper.cpp)

add_library(
        native-lib
        SHARED
        native-lib.cpp
)

find_library(log-lib log)

find_library(android-lib android)

target_link_libraries(
        native-lib
        whisper
        ${log-lib}
        ${android-lib}
)
