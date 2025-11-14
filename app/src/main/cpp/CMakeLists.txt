cmake_minimum_required(VERSION 3.10)

project("native-lib")

# Use OpenCV headers from your local folder (e.g., app/src/main/cpp/opencv/include)
include_directories(${CMAKE_SOURCE_DIR}/opencv/include)

# Build your own native library
add_library(
    native-lib
    SHARED
    native-lib.cpp
)

# Import OpenCV shared library for the current ABI
add_library(opencv_java4 SHARED IMPORTED)
set_target_properties(opencv_java4 PROPERTIES
    IMPORTED_LOCATION ${CMAKE_SOURCE_DIR}/../jniLibs/${ANDROID_ABI}/libopencv_java4.so)

# (OPTIONAL BUT RECOMMENDED)
# If your app gives libc++_shared.so missing errors, ensure the following for Android C++ runtime:
# Set this argument in your build.gradle under externalNativeBuild.cmake
# arguments "-DANDROID_STL=c++_shared"

find_library(log-lib log)

target_link_libraries(
    native-lib
    opencv_java4
    ${log-lib}
)
