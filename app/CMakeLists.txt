cmake_minimum_required(VERSION 3.22.1)

project("whisper-jni")

# Set C++ standard
set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)
set(CMAKE_C_STANDARD 11)

# Enable NEON optimizations for ARM
if(ANDROID_ABI STREQUAL "armeabi-v7a")
    set(CMAKE_C_FLAGS "${CMAKE_C_FLAGS} -mfpu=neon -mfloat-abi=softfp")
    set(CMAKE_CXX_FLAGS "${CMAKE_CXX_FLAGS} -mfpu=neon -mfloat-abi=softfp")
elseif(ANDROID_ABI STREQUAL "arm64-v8a")
    # ARM64 has NEON by default
    set(CMAKE_C_FLAGS "${CMAKE_C_FLAGS}")
    set(CMAKE_CXX_FLAGS "${CMAKE_CXX_FLAGS}")
endif()

# Whisper.cpp v1.5.4 - files are in the root directory
set(WHISPER_ROOT ${CMAKE_CURRENT_SOURCE_DIR}/../whisper.cpp-1.5.4)

# GGML library - v1.5.4 has files in root
add_library(
    ggml
    STATIC
    ${WHISPER_ROOT}/ggml.c
    ${WHISPER_ROOT}/ggml-alloc.c
    ${WHISPER_ROOT}/ggml-backend.c
    ${WHISPER_ROOT}/ggml-quants.c
)

target_include_directories(ggml PUBLIC ${WHISPER_ROOT})

target_compile_options(ggml PRIVATE -O3)

# Whisper library - v1.5.4
add_library(
    whisper
    STATIC
    ${WHISPER_ROOT}/whisper.cpp
)

target_include_directories(whisper PUBLIC ${WHISPER_ROOT})

target_link_libraries(whisper PUBLIC ggml)

target_compile_options(whisper PRIVATE -O3)

# JNI bridge library
add_library(
    whisper-jni
    SHARED
    src/main/cpp/whisper/jni.c
)

target_include_directories(whisper-jni PRIVATE ${WHISPER_ROOT})

# Find Android libraries
find_library(log-lib log)
find_library(android-lib android)

# Link libraries
target_link_libraries(
    whisper-jni
    whisper
    ggml
    ${log-lib}
    ${android-lib}
)

# Compiler flags for JNI
target_compile_options(whisper-jni PRIVATE
    -O3
    -fvisibility=hidden
)
