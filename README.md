Vision Processing App (Android + Web)

This project implements real-time image processing using **OpenCV**, **OpenGL**, and **NDK** on Android, along with a companion **TypeScript-based web interface**.
The Android app captures frames, processes them natively using C++ (JNI), and displays results in real time.

 ✅ Features Implemented

📱 Android App

📷 Camera capture (Java Camera2 API)
🔧 Native processing using **C++ (NDK)** + **OpenCV**
🎨 Rendering using **OpenGL ES**
🔄 Real-time frame pipeline: Camera → JNI → OpenCV → OpenGL → UI
💾 Ability to save captured images
🟥 Debug overlay showing processed output
🔤 JNI bridge for sending/receiving frames

🌐 Web Interface(not done completely yet)

Designed using TypeScript
🖼️ View & inspect frames sent from the Android app
📤 Upload images for server-side processing
🔌 Simple API integration to communicate with backend

📷 Screenshots / GIFs


