cmake_minimum_required(VERSION 3.22.1)
project("offlinebot-native")

set(LLAMA_CPP_DIR "${CMAKE_CURRENT_SOURCE_DIR}/../../../../../llama.cpp"
    CACHE PATH "Path to llama.cpp repository")
set(WHISPER_CPP_DIR "${CMAKE_CURRENT_SOURCE_DIR}/../../../../../whisper.cpp"
    CACHE PATH "Path to whisper.cpp repository")

get_filename_component(LLAMA_CPP_DIR "${LLAMA_CPP_DIR}" ABSOLUTE)
get_filename_component(WHISPER_CPP_DIR "${WHISPER_CPP_DIR}" ABSOLUTE)

# Build llama.cpp (includes ggml) and common (Jinja chat templates for Gemma 4)
set(BUILD_SHARED_LIBS OFF)
set(LLAMA_BUILD_TESTS OFF)
set(LLAMA_BUILD_EXAMPLES OFF)
set(LLAMA_BUILD_SERVER OFF)
set(LLAMA_BUILD_COMMON ON CACHE BOOL "" FORCE)
set(LLAMA_LLGUIDANCE OFF CACHE BOOL "" FORCE)
set(GGML_CPU_ARM_ARCH "" CACHE STRING "" FORCE)
add_subdirectory("${LLAMA_CPP_DIR}" "${CMAKE_CURRENT_BINARY_DIR}/llama.cpp")

# Build whisper JNI library (compile whisper.cpp directly to avoid ggml target conflict)
add_library(offlinebot-whisper SHARED
    whisper-jni.cpp
    "${WHISPER_CPP_DIR}/src/whisper.cpp"
)

target_compile_definitions(offlinebot-whisper PRIVATE
    WHISPER_USE_CPU
    WHISPER_VERSION="1.0.0"
)

target_include_directories(offlinebot-whisper PRIVATE
    "${WHISPER_CPP_DIR}/include"
    "${WHISPER_CPP_DIR}/src"
    "${WHISPER_CPP_DIR}/ggml/include"
    "${LLAMA_CPP_DIR}/ggml/include"
    "${LLAMA_CPP_DIR}/include"
)

find_package(Threads REQUIRED)
target_link_libraries(offlinebot-whisper ggml Threads::Threads log)

# Build llama JNI library
add_library(offlinebot-llama SHARED llama-jni.cpp)

target_include_directories(offlinebot-llama PRIVATE
    "${LLAMA_CPP_DIR}/include"
    "${LLAMA_CPP_DIR}/ggml/include"
    "${LLAMA_CPP_DIR}/common"
)

target_link_libraries(offlinebot-llama llama-common llama ggml log)
