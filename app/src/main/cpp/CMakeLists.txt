cmake_minimum_required(VERSION 3.22)
project(whisper_android)

set(CMAKE_CXX_STANDARD 17)

# --- CONFIGURARE VULKAN REALĂ (PENTRU ANDROID PE MAC) ---
set(GGML_VULKAN ON CACHE BOOL "" FORCE)

# Forțăm căile către headers
include_directories("/opt/homebrew/opt/spirv-headers/include")
include_directories("/opt/homebrew/opt/vulkan-headers/include")

# Whisper 1.9.1 are nevoie de acest flag pentru a știi de SPIRV
set(SPIRV-Headers_DIR "/opt/homebrew/opt/spirv-headers/share/cmake/SPIRV-Headers" CACHE PATH "" FORCE)

# Configurații Whisper
set(WHISPER_BUILD_EXAMPLES OFF CACHE BOOL "" FORCE)
set(WHISPER_BUILD_TESTS OFF CACHE BOOL "" FORCE)
set(BUILD_SHARED_LIBS OFF CACHE BOOL "" FORCE)

add_subdirectory(whisper.cpp)

add_library(
    whisper_final 
    SHARED
    native-lib.cpp
)

set_target_properties(whisper_final PROPERTIES OUTPUT_NAME "whisper")

target_include_directories(
    whisper_final
    PRIVATE
    whisper.cpp/include
    whisper.cpp/ggml/include
)

find_library(log-lib log)
find_library(android-lib android)
find_library(vulkan-lib vulkan)

target_link_libraries(
    whisper_final
    PRIVATE
    whisper
    ${log-lib}
    ${android-lib}
    ${vulkan-lib}
)
