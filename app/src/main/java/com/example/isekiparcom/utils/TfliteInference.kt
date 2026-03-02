package com.example.isekiparcom.utils

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import org.tensorflow.lite.Interpreter
import org.tensorflow.lite.support.common.FileUtil
import org.tensorflow.lite.support.common.ops.NormalizeOp
import org.tensorflow.lite.support.image.ImageProcessor
import org.tensorflow.lite.support.image.TensorImage
import org.tensorflow.lite.support.image.ops.ResizeOp
import java.io.FileInputStream
import java.io.IOException
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel

class TfliteInference(
    context: Context,
    modelAssetPath: String, // Contoh: "ring_synchronizer/model_unquant.tflite"
    labelsAssetPath: String, // Contoh: "ring_synchronizer/labels.txt"
    private val mean: Float = 0f,
    private val std: Float = 255f
) {
    private var interpreter: Interpreter? = null
    private var labels = listOf<String>()

    companion object {
        private const val TAG = "TfliteInference"
    }

    init {
        try {
            // 1. Muat model dari Assets
            val modelBuffer = loadModelFile(context, modelAssetPath)
            interpreter = Interpreter(modelBuffer)

            // 2. Muat labels dari Assets
            labels = loadLabels(context, labelsAssetPath)

            Log.d(TAG, "Model loaded successfully from: $modelAssetPath")
            Log.d(TAG, "Labels loaded: ${labels.size} classes")
            // Log.d(TAG, "Full Labels list: $labels") // Comment jika terlalu panjang

        } catch (e: Exception) {
            Log.e(TAG, "Error initializing TFLite from paths: $modelAssetPath, $labelsAssetPath", e)
            e.printStackTrace()
        }
    }

    // Fungsi untuk memuat model dari assets
    private fun loadModelFile(context: Context, modelPath: String): MappedByteBuffer {
        val fileDescriptor = context.assets.openFd(modelPath)
        val inputStream = FileInputStream(fileDescriptor.fileDescriptor)
        val fileChannel = inputStream.channel
        val startOffset = fileDescriptor.startOffset
        val declaredLength = fileDescriptor.declaredLength
        return fileChannel.map(FileChannel.MapMode.READ_ONLY, startOffset, declaredLength).also {
            inputStream.close()
        }
    }

    private fun loadLabels(context: Context, labelsPath: String): List<String> {
        return context.assets.open(labelsPath)
            .bufferedReader()
            .readLines()
            .filter { it.isNotBlank() } // 🔥 Filter baris kosong
    }

    fun run(bitmap: Bitmap): String {
        val interpreter = this.interpreter ?: run {
            Log.e(TAG, "Interpreter is null")
            return "unknown"
        }

        if (labels.isEmpty()) {
            Log.e(TAG, "Labels list is empty")
            return "unknown"
        }

        try {
            val inputShape = interpreter.getInputTensor(0).shape()
            Log.d(TAG, "Model input shape: ${inputShape.contentToString()}")

            val height = inputShape[1]
            val width = inputShape[2]

            Log.d(TAG, "Resizing image to: $width x $height")

            val tensorImage = TensorImage.fromBitmap(bitmap)
            val processor = ImageProcessor.Builder()
                .add(ResizeOp(height, width, ResizeOp.ResizeMethod.BILINEAR))
                .add(NormalizeOp(mean, std)) // 🔥 Gunakan mean & std dari constructor
                .build()
            val resized = processor.process(tensorImage)

            val outputBuffer = org.tensorflow.lite.support.tensorbuffer.TensorBuffer
                .createFixedSize(intArrayOf(1, labels.size), org.tensorflow.lite.DataType.FLOAT32)

            interpreter.run(resized.buffer, outputBuffer.buffer)

            val probabilities = outputBuffer.floatArray
            
            // 🔥 Log all probabilities for debugging
            Log.d(TAG, "=== ALL PROBABILITIES ===")
            probabilities.forEachIndexed { index, prob ->
                val labelAt = labels.getOrNull(index) ?: "???"
                Log.d(TAG, "Index $index ($labelAt): ${"%.6f".format(prob)}")
            }
            Log.d(TAG, "Raw output probabilities size: ${probabilities.size}")

            val maxIndex = probabilities.indices.maxByOrNull { probabilities[it] } ?: 0
            val confidence = probabilities[maxIndex]

            val predictedLabel = labels.getOrNull(maxIndex) ?: "unknown"
            Log.d(TAG, "Raw predicted label: '$predictedLabel', Index: $maxIndex, Confidence: $confidence")

            // 🔧 Ambil bagian angka saja (jika formatnya "0 12345")
            val extractedCode = extractCode(predictedLabel)
            Log.d(TAG, "Extracted code: '$extractedCode'")

            return extractedCode
        } catch (e: Exception) {
            Log.e(TAG, "Error during inference", e)
            e.printStackTrace()
            return "unknown"
        }
    }

    private fun extractCode(label: String): String {
        val parts = label.trim().split("\\s+".toRegex())
        if (parts.size >= 2) {
            // Ambil semua bagian mulai dari indeks 1 (part ke-2) hingga akhir
            return parts.subList(1, parts.size).joinToString(" ")
        }
        return label.trim()
    }

    fun close() {
        try {
            interpreter?.close()
            interpreter = null
            Log.d(TAG, "TFLite interpreter closed")
        } catch (e: Exception) {
            Log.e(TAG, "Error closing interpreter", e)
        }
    }
}