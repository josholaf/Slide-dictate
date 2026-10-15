cmake_minimum_required(VERSION 3.22.1)
project(whisper_jni)

set(CMAKE_CXX_STANDARD 11)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

add_library(whisper_jni SHARED whisper_jni.cpp)

# whisper.cpp checkout expected at app/src/main/cpp/whisper.cpp
# (git submodule, tag matching the ggml models in ModelStore).
# If absent the JNI stub still builds so assembleDebug stays green.
if(EXISTS "${CMAKE_CURRENT_SOURCE_DIR}/whisper.cpp/whisper.cpp")
    target_compile_definitions(whisper_jni PRIVATE HAVE_WHISPER_CPP)
    target_include_directories(whisper_jni PRIVATE whisper.cpp whisper.cpp/ggml/include)
    target_sources(whisper_jni PRIVATE
        whisper.cpp/whisper.cpp
        whisper.cpp/ggml/src/ggml.c
        whisper.cpp/ggml/src/ggml-alloc.c
        whisper.cpp/ggml/src/ggml-backend.c
        whisper.cpp/ggml/src/ggml-quants.c
    )
    if(ANDROID_ABI STREQUAL "arm64-v8a")
        target_compile_options(whisper_jni PRIVATE -O3)
    endif()
endif()

find_library(log-lib log)
target_link_libraries(whisper_jni ${log-lib})
