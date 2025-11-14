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
