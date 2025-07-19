cmake_minimum_required(VERSION 3.16)
project(llama_jni)

# Set C++ standard
set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

# Set build type to Release if not specified
if(NOT CMAKE_BUILD_TYPE)
    set(CMAKE_BUILD_TYPE Release)
endif()

# Find required packages
find_package(JNI REQUIRED)

# Set compiler flags for x86_64 architecture
set(CMAKE_CXX_FLAGS_RELEASE "-O3 -march=x86-64 -mtune=generic -fPIC -DNDEBUG")
set(CMAKE_CXX_FLAGS_DEBUG "-g -O0 -fPIC")

# Add compile options for all targets
add_compile_options(
    -Wall
    -Wextra
    -fPIC
)

# Define paths for llama.cpp
set(LLAMA_CPP_DIR "${CMAKE_SOURCE_DIR}/../llama.cpp")
set(LLAMA_CPP_BUILD_DIR "${CMAKE_SOURCE_DIR}/../llama.cpp/build")

# Check if llama.cpp directory exists
if(NOT EXISTS ${LLAMA_CPP_DIR})
    message(FATAL_ERROR "llama.cpp directory not found at ${LLAMA_CPP_DIR}")
endif()

# Find llama.cpp shared library
find_library(LLAMA_LIBRARY
    NAMES llama libllama
    PATHS ${LLAMA_CPP_BUILD_DIR}
    PATH_SUFFIXES lib bin
    REQUIRED
)

# Create the JNI shared library
add_library(llama_jni SHARED
    llama_jni.cpp
)

# Set target properties
set_target_properties(llama_jni PROPERTIES
    VERSION 1.0
    SOVERSION 1
    POSITION_INDEPENDENT_CODE ON
)

# Include directories
target_include_directories(llama_jni PRIVATE
    ${JNI_INCLUDE_DIRS}
    ${LLAMA_CPP_DIR}
    ${LLAMA_CPP_DIR}/include
    ${LLAMA_CPP_DIR}/ggml/include
    ${LLAMA_CPP_DIR}/common
    .
)

# Link libraries
target_link_libraries(llama_jni PRIVATE
    ${JNI_LIBRARIES}
    ${LLAMA_LIBRARY}
)

# Set compiler-specific options
target_compile_options(llama_jni PRIVATE
    $<$<COMPILE_LANGUAGE:CXX>:-std=c++17>
    $<$<CONFIG:Release>:-O3 -march=x86-64 -mtune=generic>
    $<$<CONFIG:Debug>:-g -O0>
)

# Add preprocessor definitions
target_compile_definitions(llama_jni PRIVATE
    $<$<CONFIG:Release>:NDEBUG>
    $<$<CONFIG:Debug>:DEBUG>
)

# Install target (optional)
install(TARGETS llama_jni
    LIBRARY DESTINATION lib
    RUNTIME DESTINATION bin
)

# Print configuration information
message(STATUS "Build type: ${CMAKE_BUILD_TYPE}")
message(STATUS "C++ compiler: ${CMAKE_CXX_COMPILER}")
message(STATUS "C++ flags: ${CMAKE_CXX_FLAGS}")
message(STATUS "JNI include dirs: ${JNI_INCLUDE_DIRS}")
message(STATUS "JNI libraries: ${JNI_LIBRARIES}")
message(STATUS "llama.cpp library: ${LLAMA_LIBRARY}")
message(STATUS "llama.cpp directory: ${LLAMA_CPP_DIR}")