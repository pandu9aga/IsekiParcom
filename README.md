# IsekiParcom - AI-Powered Parts Verification

## Overview

**IsekiParcom** is a high-tech Android application designed for automated parts identification and quality control verification. By leveraging state-of-the-art Computer Vision (CV) and Artificial Intelligence, the app enables production line personnel to instantaneously verify critical components using real-time object detection and classification.

The application is built with a modular architecture, supporting specialized verification workflows for bearings, shafts, and synchronizer rings.

## Key Features

### 1. Real-Time AI Detection
*   **YOLOv8 & MobileNetV2**: Dual-model architecture utilizing **YOLOv8** for object detection and **MobileNetV2 CNN** for high-precision part classification, both optimized for mobile via TFLite.
*   **Live Inference Pipeline**: Powered by **CameraX**, the app performs continuous inference on the live camera stream, detecting and highlighting parts in real-time.
*   **Confidence Filtering**: Intelligent thresholding to ensure only high-confidence detections are recorded.

### 2. Specialized Verification Modules
The app includes dedicated interfaces and AI logic for specific components:
*   **Bearing KBC & Koyo**: Specialized detection and tracking for different bearing manufacturers.
*   **Bearing Shaft**: Verification of shaft-bearing assemblies.
*   **Ring Synchronizer**: High-precision identification of synchronizer components.

### 3. Record Management & Analytics
*   **Modular History**: Browse verification records categorized by component type ("Record List" views).
*   **Detailed Analytics**: View detection timestamps, confidence scores, and success/failure statuses.
*   **Swipe-to-Refresh**: Modern UI interactions for fetching the latest data from synchronizing services.

### 4. High-Performance UI
*   **Jetpack Compose**: A fully modern reactive UI built with Kotlin.
*   **Themed Experiences**: Dynamic material colors and typography tailored for industrial environments.
*   **Multi-Media Support**: Integration of **ExoPlayer** for potential video-based training or validation workflows.

## Technology Stack

### AI & Machine Learning
*   **Framework**: [TensorFlow Lite (TFLite)](https://www.tensorflow.org/lite)
*   **Model Architecture**: YOLOv8 (Object Detection) & MobileNetV2 (CNN Classification)
*   **Biometrics/Utilities**: Google ML Kit (Barcode & Text Recognition)

### Mobile Core
*   **Platform**: Android (Target SDK 35)
*   **Language**: [Kotlin](https://kotlinlang.org/)
*   **UI Framework**: [Jetpack Compose](https://developer.android.com/jetpack/compose)
*   **Camera API**: [CameraX](https://developer.android.com/jetpack/androidx/releases/camera)

### Internal Libraries
*   **Video Playback**: ExoPlayer
*   **Networking**: OkHttp 3
*   **UI Components**: Accompanist (SwipeRefresh), Material Icons Extended.

## Project Structure

```text
IsekiParcom/
├── app/
│   ├── src/main/java/com/example/isekiparcom/
│   │   ├── ui/               # Compose Screens (BearingKbcScreen, Dashboard, etc.)
│   │   ├── viewmodel/        # Business logic & AI orchestration
│   │   ├── utils/            # Inference utility (TfliteInference, YoloV8Inference)
│   │   └── MainActivity.kt   # App Entry & Navigation
│   └── build.gradle.kts      # AI Dependencies & Build Configuration
```

## Installation & Setup

1.  **Android Studio**
    *   Requires Android Studio (Koala or newer).
    *   Use Android SDK 35 for compilation.

2.  **AI Models**
    *   Ensure the `.tflite` model files are placed in the `app/src/main/assets/` directory (or as configured in the `utils` package).

3.  **Permissions**
    The app requires:
    *   `CAMERA`: For real-time inference and capturing.
    *   `ACCESS_WIFI_STATE`: For connectivity checks.

4.  **Gradle Sync**
    Perform a Gradle sync to download the specialized TFLite and CameraX dependencies. Note: Some ML Kit dependencies are configured with specific exclusions to avoid binary size bloat.

## Usage

1.  **Dashboard**: Launch the app to access the central hub.
2.  **Select Module**: Choose a component type (e.g., "Bearing KBC").
3.  **Scan**: Point the camera at the part; the YOLOv8 model will highlight and identify the part in real-time.
4.  **Review**: Check the "Record List" for each module to see past verification results.

## License

This project is proprietary.
