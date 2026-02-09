package com.example.isekiparcom.viewmodel

import android.graphics.Bitmap
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.State
import com.example.isekiparcom.utils.Detection
import com.example.isekiparcom.utils.YoloV8Inference
import java.io.File

/**
 * Interface untuk ViewModel yang menggunakan CameraCaptureScreenYolo
 * Diimplementasikan oleh BearingMetalViewModel dan BearingShaftViewModel
 */
interface YoloCameraViewModel {
    // Properties untuk deteksi real-time
    val lastLiveBitmap: MutableState<Bitmap?>
    val lastLiveDetections: State<List<Detection>>
    val isCaptureLocked: MutableState<Boolean>
    var frameGeneration: Int

    // Properties untuk hasil capture
    val capturedBearingPhotoFile: MutableState<File?>
    val bearingPhotoWithBoxes: MutableState<Bitmap?>
    val ballCount: MutableState<Int?>

    // YOLO detector instance
    val yoloDetector: YoloV8Inference

    // Methods yang dibutuhkan oleh CameraCaptureScreenYolo
    fun resetBearingDetectionState()
    fun processFrameForPreview(bitmap: Bitmap)
    fun processFinalBearingResult(count: Int)
}