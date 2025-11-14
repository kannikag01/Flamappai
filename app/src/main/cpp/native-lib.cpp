#include <jni.h>
#include <string>
#include <opencv2/opencv.hpp>

extern "C"
JNIEXPORT jstring JNICALL
Java_com_flamapp_MainActivity_stringFromJNI(JNIEnv* env, jobject /* this */) {

    // Simple check if OpenCV is loaded
    std::string message = "OpenCV Loaded Successfully!";

    // Small OpenCV test: create a matrix
    cv::Mat test = cv::Mat::eye(3, 3, CV_8UC1);

    // Append test result
    message += "\nMatrix: " + std::to_string(test.rows) + "x" + std::to_string(test.cols);

    return env->NewStringUTF(message.c_str());
}

// ---------------------------------------------------
// JNI function that processes a grayscale frame (Y plane)
// Java signature (static native): public static native byte[] processGrayFrame(byte[] input, int width, int height);
// Therefore second parameter in native is jclass
// ---------------------------------------------------
extern "C"
JNIEXPORT jbyteArray JNICALL
Java_com_flamapp_FrameProcessor_processGrayFrame(JNIEnv* env, jclass /* cls */,
                                                 jbyteArray input, jint width, jint height) {
    if (input == nullptr) return nullptr;
    const int w = static_cast<int>(width);
    const int h = static_cast<int>(height);
    const int size = w * h;

    // Acquire input bytes
    jbyte* inBytes = env->GetByteArrayElements(input, nullptr);
    if (inBytes == nullptr) {
        return nullptr;
    }

    // Wrap the input bytes as a single-channel cv::Mat (no copy if possible)
    cv::Mat gray(h, w, CV_8UC1, reinterpret_cast<unsigned char*>(inBytes));

    // Output container
    cv::Mat edges;
    try {
        // Apply a small Gaussian blur to reduce noise, then Canny
        cv::Mat blurred;
        cv::GaussianBlur(gray, blurred, cv::Size(3, 3), 0);
        cv::Canny(blurred, edges, 80, 150);
    } catch (const cv::Exception& e) {
        // On error, release and return null
        env->ReleaseByteArrayElements(input, inBytes, JNI_ABORT);
        return nullptr;
    }

    // Prepare the jbyteArray to return
    jbyteArray outArray = env->NewByteArray(size);
    if (outArray == nullptr) {
        env->ReleaseByteArrayElements(input, inBytes, JNI_ABORT);
        return nullptr;
    }

    // Ensure edges is continuous and has expected size
    if (!edges.isContinuous() || edges.total() != (size_t)size) {
        // make a contiguous copy
        cv::Mat cont = edges.clone();
        env->SetByteArrayRegion(outArray, 0, size, reinterpret_cast<jbyte*>(cont.data));
    } else {
        env->SetByteArrayRegion(outArray, 0, size, reinterpret_cast<jbyte*>(edges.data));
    }

    // Release input (no copy back)
    env->ReleaseByteArrayElements(input, inBytes, JNI_ABORT);

    return outArray;
}
