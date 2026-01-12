package com.example.isekiparcom.utils

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.util.Log
import org.tensorflow.lite.Interpreter
import org.tensorflow.lite.support.common.ops.NormalizeOp
import org.tensorflow.lite.support.image.ImageProcessor
import org.tensorflow.lite.support.image.TensorImage
import org.tensorflow.lite.support.image.ops.ResizeOp
import java.io.FileInputStream
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel

data class Detection(
    val box: RectF,
    val confidence: Float,
    val classIndex: Int,
    val sourceWidth: Int = 0,  // Original image width
    val sourceHeight: Int = 0  // Original image height
)

class YoloV8Inference(
    context: Context,
    modelAssetPath: String
) {
    private var interpreter: Interpreter? = null
    private val inputSize = 640

    // Threshold berbeda untuk live preview vs foto capture
    var confidenceThreshold = 0.95f // Threshold SANGAT TINGGI untuk foto capture (hanya deteksi very confident)
    var confidenceThresholdLive = 0.7f // Threshold rendah untuk live preview

    private val iouThreshold = 0.4f
    private var isFirstRun = true
    private var debugFrameCount = 0
    private val maxDebugFrames = 3

    companion object {
        private const val TAG = "YoloV8Inference"
    }

    init {
        try {
            val modelBuffer = loadModelFile(context, modelAssetPath)
            val options = Interpreter.Options().apply {
                setNumThreads(4)
            }
            interpreter = Interpreter(modelBuffer, options)

            val inputTensor = interpreter?.getInputTensor(0)
            val outputTensor = interpreter?.getOutputTensor(0)
            Log.d(TAG, "Input shape: ${inputTensor?.shape()?.contentToString()}")
            Log.d(TAG, "Output shape: ${outputTensor?.shape()?.contentToString()}")
            Log.d(TAG, "Model loaded successfully")
        } catch (e: Exception) {
            Log.e(TAG, "Error loading model", e)
        }
    }

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

    fun detect(bitmap: Bitmap, isLivePreview: Boolean = false): List<Detection> {
        val interpreter = this.interpreter ?: return emptyList()

        // Gunakan threshold berbeda untuk live vs capture
        val threshold = if (isLivePreview) confidenceThresholdLive else confidenceThreshold

        try {
            // Preprocess
            val tensorImage = TensorImage.fromBitmap(bitmap)
            val processor = ImageProcessor.Builder()
                .add(ResizeOp(inputSize, inputSize, ResizeOp.ResizeMethod.BILINEAR))
                .add(NormalizeOp(0f, 255f))
                .build()
            val processedImage = processor.process(tensorImage)

            // Get output shape
            val outputShape = interpreter.getOutputTensor(0).shape()

            // Debug output format pada run pertama
            if (isFirstRun) {
                Log.d(TAG, "=== OUTPUT FORMAT ===")
                Log.d(TAG, "Shape: ${outputShape.contentToString()}")
                isFirstRun = false
            }

            // Prepare output - support kedua format
            val outputBuffer = Array(outputShape[0]) {
                Array(outputShape[1]) {
                    FloatArray(outputShape[2])
                }
            }

            // Run inference
            interpreter.run(processedImage.buffer, outputBuffer)

            // Parse berdasarkan format output
            val detections = if (outputShape[1] == 5 && outputShape[2] == 8400) {
                // Format: [1, 5, 8400] - YOLOv8 Single Class (x, y, w, h, conf)
                Log.d(TAG, "Using Single Class format parser")
                parseYoloOutput_SingleClass_5x8400(outputBuffer[0], bitmap.width, bitmap.height, threshold)
            } else if (outputShape[1] == 84 && outputShape[2] == 8400) {
                // Format: [1, 84, 8400] - Transposed
                parseYoloOutput_Transposed(outputBuffer[0], bitmap.width, bitmap.height, threshold)
            } else if (outputShape[1] == 8400 && outputShape[2] == 84) {
                // Format: [1, 8400, 84] - Standard
                parseYoloOutput_Standard(outputBuffer[0], bitmap.width, bitmap.height, threshold)
            } else if (outputShape[1] == 8400 && outputShape[2] == 5) {
                // Format: [1, 8400, 5] - Single Class Standard
                parseYoloOutput_SingleClass(outputBuffer[0], bitmap.width, bitmap.height, threshold)
            } else {
                Log.e(TAG, "Unknown output format: ${outputShape.contentToString()}")
                emptyList()
            }

            // NMS
            val finalDetections = applyNMS(detections)

            if (debugFrameCount < maxDebugFrames) {
                Log.d(TAG, "Mode: ${if (isLivePreview) "LIVE" else "CAPTURE"}, Threshold: $threshold")
                Log.d(TAG, "Raw: ${detections.size}, After NMS: ${finalDetections.size}")
                debugFrameCount++
            }

            return finalDetections

        } catch (e: Exception) {
            Log.e(TAG, "Detection error", e)
            return emptyList()
        }
    }

    // Format: [5, 8400] -> YOLOv8 Single Class (UTAMA untuk model Anda)
    private fun parseYoloOutput_SingleClass_5x8400(output: Array<FloatArray>, imgW: Int, imgH: Int, threshold: Float): List<Detection> {
        val detections = mutableListOf<Detection>()
        val numPredictions = output[0].size // 8400

        if (debugFrameCount < maxDebugFrames) {
            Log.d(TAG, "Parsing [5, 8400] format - threshold=$threshold, imgSize=${imgW}x${imgH}")
        }

        // Debug: Print beberapa sample predictions
        var samplesShown = 0
        var highConfCount = 0

        for (i in 0 until numPredictions) {
            // YOLOv8 Single Class: [x_center, y_center, width, height, confidence]
            val x = output[0][i]  // x_center
            val y = output[1][i]  // y_center
            val w = output[2][i]  // width
            val h = output[3][i]  // height
            val confidence = output[4][i]  // confidence score

            // Debug: Show first 3 high-confidence predictions
            if (confidence > 0.1f && samplesShown < 3 && debugFrameCount < maxDebugFrames) {
                Log.d(TAG, "Sample pred $i: x=%.2f, y=%.2f, w=%.2f, h=%.2f, conf=%.4f".format(x, y, w, h, confidence))
                samplesShown++
            }

            if (confidence > 0.1f) highConfCount++

            if (confidence >= threshold) {
                val detection = createDetection(x, y, w, h, confidence, imgW, imgH)
                if (detection != null) {
                    detections.add(detection)
                }
            }
        }

        if (debugFrameCount < maxDebugFrames) {
            Log.d(TAG, "Predictions with conf > 0.1: $highConfCount / $numPredictions")
            Log.d(TAG, "Valid detections (threshold=$threshold): ${detections.size}")
        }
        return detections
    }

    // Format: [84, 8400] -> transposed
    private fun parseYoloOutput_Transposed(output: Array<FloatArray>, imgW: Int, imgH: Int, threshold: Float): List<Detection> {
        val detections = mutableListOf<Detection>()
        val numPredictions = output[0].size // 8400

        for (i in 0 until numPredictions) {
            // YOLOv8: [x_center, y_center, width, height, class_conf, ...]
            val x = output[0][i]
            val y = output[1][i]
            val w = output[2][i]
            val h = output[3][i]

            // Confidence adalah nilai maksimal dari class scores
            val maxConf = (4 until output.size).maxOfOrNull { output[it][i] } ?: 0f

            if (maxConf >= threshold) {
                val detection = createDetection(x, y, w, h, maxConf, imgW, imgH)
                if (detection != null) {
                    detections.add(detection)
                }
            }
        }

        return detections
    }

    // Format: [8400, 84] -> standard
    private fun parseYoloOutput_Standard(output: Array<FloatArray>, imgW: Int, imgH: Int, threshold: Float): List<Detection> {
        val detections = mutableListOf<Detection>()

        for (i in output.indices) {
            val pred = output[i]

            val x = pred[0]
            val y = pred[1]
            val w = pred[2]
            val h = pred[3]

            // Max confidence dari class scores (index 4 onwards)
            val maxConf = pred.slice(4 until pred.size).maxOrNull() ?: 0f

            if (maxConf >= threshold) {
                val detection = createDetection(x, y, w, h, maxConf, imgW, imgH)
                if (detection != null) {
                    detections.add(detection)
                }
            }
        }

        return detections
    }

    // Format alternatif untuk single class model
    private fun parseYoloOutput_SingleClass(output: Array<FloatArray>, imgW: Int, imgH: Int, threshold: Float): List<Detection> {
        val detections = mutableListOf<Detection>()

        // Coba interpretasi sebagai [num_boxes, 5] dimana 5 = [x, y, w, h, conf]
        for (pred in output) {
            if (pred.size >= 5) {
                val x = pred[0]
                val y = pred[1]
                val w = pred[2]
                val h = pred[3]
                val conf = pred[4]

                if (conf >= confidenceThreshold) {
                    val detection = createDetection(x, y, w, h, conf, imgW, imgH)
                    if (detection != null) {
                        detections.add(detection)
                    }
                }
            }
        }

        return detections
    }

    private fun createDetection(
        cx: Float, cy: Float, w: Float, h: Float,
        confidence: Float,
        imgWidth: Int, imgHeight: Int
    ): Detection? {
        // Koordinat sudah dalam format NORMALIZED (0-1), bukan pixel (0-640)
        // Konversi langsung ke image coordinates
        val x1 = ((cx - w / 2) * imgWidth).coerceIn(0f, imgWidth.toFloat())
        val y1 = ((cy - h / 2) * imgHeight).coerceIn(0f, imgHeight.toFloat())
        val x2 = ((cx + w / 2) * imgWidth).coerceIn(0f, imgWidth.toFloat())
        val y2 = ((cy + h / 2) * imgHeight).coerceIn(0f, imgHeight.toFloat())

        // Validasi: box harus valid
        if (x2 <= x1 || y2 <= y1) {
            if (debugFrameCount < maxDebugFrames) {
                Log.d(TAG, "Invalid box: x1=$x1, y1=$y1, x2=$x2, y2=$y2")
            }
            return null
        }

        // Filter box yang terlalu kecil atau terlalu besar
        val boxArea = (x2 - x1) * (y2 - y1)
        val imageArea = imgWidth * imgHeight
        val areaRatio = boxArea / imageArea

        if (areaRatio < 0.003) {
            if (debugFrameCount < maxDebugFrames) {
                Log.d(TAG, "Box too small: area ratio = $areaRatio")
            }
            return null
        }
        if (areaRatio > 0.8) {
            if (debugFrameCount < maxDebugFrames) {
                Log.d(TAG, "Box too large: area ratio = $areaRatio")
            }
            return null
        }

        if (debugFrameCount < maxDebugFrames) {
            Log.d(TAG, "Valid detection: conf=$confidence, box=[$x1, $y1, $x2, $y2], area_ratio=$areaRatio")
        }

        return Detection(
            box = RectF(x1, y1, x2, y2),
            confidence = confidence,
            classIndex = 0,
            sourceWidth = imgWidth,
            sourceHeight = imgHeight
        )
    }

    private fun applyNMS(detections: List<Detection>): List<Detection> {
        if (detections.isEmpty()) return emptyList()

        val sorted = detections.sortedByDescending { it.confidence }
        val selected = mutableListOf<Detection>()

        for (detection in sorted) {
            var suppress = false
            for (selected in selected) {
                if (calculateIoU(detection.box, selected.box) > iouThreshold) {
                    suppress = true
                    break
                }
            }
            if (!suppress) {
                selected.add(detection)
            }
        }

        return selected
    }

    private fun calculateIoU(box1: RectF, box2: RectF): Float {
        val x1 = maxOf(box1.left, box2.left)
        val y1 = maxOf(box1.top, box2.top)
        val x2 = minOf(box1.right, box2.right)
        val y2 = minOf(box1.bottom, box2.bottom)

        if (x2 < x1 || y2 < y1) return 0f

        val intersection = (x2 - x1) * (y2 - y1)
        val area1 = (box1.right - box1.left) * (box1.bottom - box1.top)
        val area2 = (box2.right - box2.left) * (box2.bottom - box2.top)
        val union = area1 + area2 - intersection

        return if (union > 0) intersection / union else 0f
    }

    fun drawDetections(bitmap: Bitmap, detections: List<Detection>): Bitmap {
        val mutable = bitmap.copy(Bitmap.Config.ARGB_8888, true)
        val canvas = Canvas(mutable)

        val boxPaint = Paint().apply {
            color = Color.RED
            strokeWidth = 8f
            style = Paint.Style.STROKE
        }

        detections.forEach { det ->
            canvas.drawRect(det.box, boxPaint)
        }

        return mutable
    }


    fun close() {
        interpreter?.close()
        interpreter = null
    }
}