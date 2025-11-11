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

class TfliteInference(context: Context) {
    private var interpreter: Interpreter? = null
    private var labels = listOf<String>()

    companion object {
        private const val TAG = "TfliteInference"
    }

    init {
        try {
            val model = FileUtil.loadMappedFile(context, "ring_synchronizer/model_unquant.tflite")
            interpreter = Interpreter(model)
            labels = context.assets.open("ring_synchronizer/labels.txt")
                .bufferedReader().readLines()
            Log.d(TAG, "Model loaded successfully")
            Log.d(TAG, "Labels loaded: ${labels.size} classes")
            Log.d(TAG, "Full Labels list: $labels")
        } catch (e: Exception) {
            Log.e(TAG, "Error initializing TFLite", e)
            e.printStackTrace()
        }
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
                .add(NormalizeOp(0f, 255f))
                .build()
            val resized = processor.process(tensorImage)

            val outputBuffer = org.tensorflow.lite.support.tensorbuffer.TensorBuffer
                .createFixedSize(intArrayOf(1, labels.size), org.tensorflow.lite.DataType.FLOAT32)

            interpreter.run(resized.buffer, outputBuffer.buffer)

            val probabilities = outputBuffer.floatArray
            Log.d(TAG, "Raw output probabilities size: ${probabilities.size}")

            val maxIndex = probabilities.indices.maxByOrNull { probabilities[it] } ?: 0
            val confidence = probabilities[maxIndex]

            // 🔧 AMBIL LABEL ASLI
            val predictedLabel = labels.getOrNull(maxIndex) ?: "unknown"
            Log.d(TAG, "Raw predicted label: '$predictedLabel', Index: $maxIndex, Confidence: $confidence")

            // 🔧 AMBIL BAGIAN ANGKA SAJA
            val extractedCode = extractCode(predictedLabel)
            Log.d(TAG, "Extracted code: '$extractedCode'")

            return extractedCode
        } catch (e: Exception) {
            Log.e(TAG, "Error during inference", e)
            e.printStackTrace()
            return "unknown"
        }
    }

    /**
     * Fungsi untuk mengekstrak kode dari label.
     * Misal: "1 1800214202" -> "1800214202"
     *        "0 1650214204" -> "1650214204"
     */
    private fun extractCode(label: String): String {
        // Pisahkan berdasarkan spasi
        val parts = label.trim().split("\\s+".toRegex())
        if (parts.size >= 2) {
            // Ambil bagian kedua (indeks 1) yang berisi kode
            return parts[1]
        }
        // Jika tidak sesuai format, kembalikan label aslinya (jika hanya berisi kode)
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