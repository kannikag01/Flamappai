package com.flamapp;

public class FrameProcessor {
    static {
        // make sure this name matches your native library name in CMakeLists / build.gradle
        System.loadLibrary("native-lib");
    }

    // Native method signature — matches the JNI function name Java_com_flamapp_FrameProcessor_processGrayFrame
    // input: grayscale bytes (width*height), returns grayscale bytes
    public static native byte[] processGrayFrame(byte[] input, int width, int height);
}
