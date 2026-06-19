cmake_minimum_required(VERSION 3.22)
project(whisper_android)

set(CMAKE_CXX_STANDARD 17)

# --- CONFIGURARE VULKAN DINAMICĂ ---
# Aceste variabile sunt acum trimise automat de scripts/check_whisper.sh
if (VULKAN_HEADERS_DIR)
    include_directories("${VULKAN_HEADERS_DIR}")
endif()

if (SPIRV_HEADERS_CMAKE_DIR)
    set(SPIRV-Headers_DIR "${SPIRV_HEADERS_CMAKE_DIR}" CACHE PATH "" FORCE)
endif()

if (VULKAN_GLSLC_EXECUTABLE)
    set(Vulkan_GLSLC_EXECUTABLE "${VULKAN_GLSLC_EXECUTABLE}" CACHE FILEPATH "" FORCE)
endif()

# Configurații Whisper
set(GGML_VULKAN ON CACHE BOOL "" FORCE)
set(WHISPER_BUILD_EXAMPLES OFF CACHE BOOL "" FORCE)
set(WHISPER_BUILD_TESTS OFF CACHE BOOL "" FORCE)
set(BUILD_SHARED_LIBS OFF CACHE BOOL "" FORCE)

# Adăugăm folderul oficial whisper.cpp
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

target_link_libraries(
    whisper_final
    PRIVATE
    whisper
    ${log-lib}
    ${android-lib}
)
