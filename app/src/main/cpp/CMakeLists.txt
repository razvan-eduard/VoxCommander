cmake_minimum_required(VERSION 3.22)
project(whisper_android)

set(CMAKE_CXX_STANDARD 17)

# --- CONFIGURARE VULKAN AUTOMATĂ (Pentru v1.9.1) ---
# Îi spunem lui CMake unde găsește headerele de pe Mac-ul tău
set(Vulkan_INCLUDE_DIR "/opt/homebrew/include" CACHE PATH "" FORCE)
set(SPIRV-Headers_DIR "/opt/homebrew/share/cmake/SPIRV-Headers" CACHE PATH "" FORCE)

# Forțăm includerea lor globală
include_directories("${Vulkan_INCLUDE_DIR}")

# Configurații Whisper oficiale
set(GGML_VULKAN ON CACHE BOOL "" FORCE)
set(WHISPER_BUILD_EXAMPLES OFF CACHE BOOL "" FORCE)
set(WHISPER_BUILD_TESTS OFF CACHE BOOL "" FORCE)
set(BUILD_SHARED_LIBS OFF CACHE BOOL "" FORCE)

# Adăugăm whisper.cpp oficial
add_subdirectory(whisper.cpp)

# Biblioteca noastră JNI
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
