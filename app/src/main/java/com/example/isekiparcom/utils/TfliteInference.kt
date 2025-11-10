package com.example.isekiparcom.utils

import android.content.Context
import android.graphics.Bitmap
import org.tensorflow.lite.Interpreter
import org.tensorflow.lite.support.common.FileUtil
import org.tensorflow.lite.support.common.ops.NormalizeOp
import org.tensorflow.lite.support.image.ImageProcessor
import org.tensorflow.lite.support.image.TensorImage
import org.tensorflow.lite.support.image.ops.ResizeOp
import java.nio.MappedByteBuffer

class TfliteInference(context: Context) {
    private var interpreter: Interpreter? = null
    private var labels = listOf<String>()

    init {
        try {
            val model = FileUtil.loadMappedFile(context, "ring_synchronizer/model.tflite")
            interpreter = Interpreter(model)
            labels = context.assets.open("ring_synchronizer/labels.txt")
                .bufferedReader().readLines()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun run(bitmap: Bitmap): String {
        val inputShape = interpreter?.getInputTensor(0)?.shape() ?: intArrayOf(1, 224, 224, 3)
        val height = inputShape[1]
        val width = inputShape[2]

        val tensorImage = TensorImage.fromBitmap(bitmap)
        val processor = ImageProcessor.Builder()
            .add(ResizeOp(height, width, ResizeOp.ResizeMethod.BILINEAR))
            .add(NormalizeOp(0f, 255f))
            .build()
        val resized = processor.process(tensorImage)

        val outputBuffer = org.tensorflow.lite.support.tensorbuffer.TensorBuffer
            .createFixedSize(intArrayOf(1, labels.size), org.tensorflow.lite.DataType.FLOAT32)
        interpreter?.run(resized.buffer, outputBuffer.buffer)

        val probabilities = outputBuffer.floatArray
        val maxIndex = probabilities.indices.maxByOrNull { probabilities[it] } ?: 0
        return labels.getOrNull(maxIndex) ?: "unknown"
    }

    fun close() {
        interpreter?.close()
    }
}