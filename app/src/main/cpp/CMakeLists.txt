cmake_minimum_required(VERSION 3.31.6)

project("studyroast_llama" VERSION 1.0.0 LANGUAGES C CXX)

set(CMAKE_C_STANDARD 11)
set(CMAKE_C_STANDARD_REQUIRED ON)

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

if(DEFINED ANDROID_ABI)
    message(STATUS "StudyRoast detected Android ABI: ${ANDROID_ABI}")
    if(ANDROID_ABI STREQUAL "arm64-v8a")
        set(GGML_SYSTEM_ARCH "ARM")
        set(GGML_CPU_KLEIDIAI OFF CACHE BOOL "" FORCE)
        set(GGML_OPENMP OFF CACHE BOOL "" FORCE)
    elseif(ANDROID_ABI STREQUAL "x86_64")
        set(GGML_SYSTEM_ARCH "x86")
        set(GGML_CPU_KLEIDIAI OFF CACHE BOOL "" FORCE)
        set(GGML_OPENMP OFF CACHE BOOL "" FORCE)
    else()
        message(FATAL_ERROR "Unsupported Android ABI: ${ANDROID_ABI}")
    endif()
endif()

set(LLAMA_SRC "${CMAKE_CURRENT_LIST_DIR}/../../../../third_party/llama.cpp")
add_subdirectory("${LLAMA_SRC}" build-llama)

add_library(${CMAKE_PROJECT_NAME} SHARED
    studyroast_llama_jni.cpp
)

target_include_directories(${CMAKE_PROJECT_NAME} PRIVATE
    "${LLAMA_SRC}/include"
    "${LLAMA_SRC}/ggml/include"
)

target_link_libraries(${CMAKE_PROJECT_NAME}
    llama
    android
    log
)
