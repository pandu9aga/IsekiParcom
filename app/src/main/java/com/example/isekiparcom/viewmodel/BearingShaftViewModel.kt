package com.example.isekiparcom.viewmodel

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Log
import android.widget.Toast
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.isekiparcom.utils.Detection
import com.example.isekiparcom.utils.TfliteInference
import com.example.isekiparcom.utils.YoloV8Inference
import com.example.isekiparcom.viewmodel.YoloCameraViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.asRequestBody
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream

data class ScanResultShaft(
    val sequenceNo: String,
    val tractorType: String,
    val productionDate: String,
    val idComparison: Int = 2 // ID untuk Bearing Shaft
)

class BearingShaftViewModel(private val context: Context) : ViewModel(), YoloCameraViewModel {
    private val apiUrl = "http://192.168.173.207/iseki_parcom/public/api"
    private val client = OkHttpClient()

    // TFLite untuk deteksi part shaft
    private val tflitePart = TfliteInference(
        context,
        "bearing_kbc/model_unquant.tflite",
        "bearing_kbc/labels.txt"
    )

    // YOLOv8 untuk deteksi ball
    override val yoloDetector = YoloV8Inference(
        context,
        "bearing_shaft/model_unquant.tflite" // Model YOLO
    )

    // State untuk scan QR
    val scanResult = mutableStateOf<ScanResultShaft?>(null)
    val validationMessage = mutableStateOf<String?>(null)
    val showCaptureButton = mutableStateOf(false)

    // State untuk foto part shaft
    val capturedPartPhotoFile = mutableStateOf<File?>(null)
    val partDetectionResult = mutableStateOf<String?>(null)
    val showSecondCaptureButton = mutableStateOf(false)
    val showRetakePartButton = mutableStateOf(false)

    // State untuk foto bearing (YOLO)
    override val capturedBearingPhotoFile = mutableStateOf<File?>(null)
    override val bearingPhotoWithBoxes = mutableStateOf<Bitmap?>(null)
    override val ballCount = mutableStateOf<Int?>(null)
    val finalResult = mutableStateOf<String?>(null)
    val showUploadButton = mutableStateOf(false)

    // State untuk popup dan upload
    val showResultPopup = mutableStateOf(false)
    val popupFinished = mutableStateOf(false)
    val isUploading = mutableStateOf(false)
    val saveSuccess = mutableStateOf<Boolean?>(null)

    private var isProcessingFrame = false

    override val lastLiveBitmap = mutableStateOf<Bitmap?>(null)
    override val lastLiveDetections = mutableStateOf<List<Detection>>(emptyList())

    override val isCaptureLocked = mutableStateOf(false)

    override var frameGeneration: Int = 0

    private fun resetScanStates() {
        validationMessage.value = null
        showCaptureButton.value = false
        partDetectionResult.value = null
        showSecondCaptureButton.value = false
        showRetakePartButton.value = false
        ballCount.value = null
        finalResult.value = null
        showUploadButton.value = false
        showResultPopup.value = false
        popupFinished.value = false
        capturedPartPhotoFile.value = null
        capturedBearingPhotoFile.value = null
        bearingPhotoWithBoxes.value = null
        isUploading.value = false
        lastLiveDetections.value = emptyList()
    }

    fun handleQrScanned(rawQr: String) {
        try {
            val parts = rawQr.split(";")
            if (parts.size < 3) throw Exception("Format QR salah")

            val sequenceNo = parts[0].trim()
            val productionDate = parts[1].trim()
            val tractorType = parts[2].trim()
            val newScanResult = ScanResultShaft(sequenceNo, tractorType, productionDate)

            resetScanStates()
            scanResult.value = newScanResult
            showCaptureButton.value = true
            validateRule()

        } catch (e: Exception) {
            Log.e("QR", "Parse error", e)
            validationMessage.value = "Format QR salah: ${e.message}"
        }
    }

    fun validateRule() {
        val result = scanResult.value ?: return
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val json = JSONObject().apply {
                    put("sequence_no", result.sequenceNo)
                    put("id_comparison", 2)
                    put("production_date", result.productionDate)
                }

                val mediaType = "application/json; charset=utf-8".toMediaType()
                val body = RequestBody.create(mediaType, json.toString())

                val request = Request.Builder()
                    .url("$apiUrl/bearing-kbc/validate")
                    .post(body)
                    .build()

                val response = client.newCall(request).execute()
                val respJson = JSONObject(response.body?.string())
                val success = respJson.getBoolean("success")
                val message = respJson.getString("message")

                withContext(Dispatchers.Main) {
                    validationMessage.value = message
                    showCaptureButton.value = success
                }
            } catch (e: Exception) {
                Log.e("API", "Validate error", e)
                withContext(Dispatchers.Main) {
                    validationMessage.value = "Gagal memvalidasi: ${e.message}"
                    showCaptureButton.value = false
                }
            }
        }
    }

    // Proses foto part dengan TFLite
    fun processPartImage(bitmap: Bitmap) {
        val predictedClass = tflitePart.run(bitmap)
        partDetectionResult.value = predictedClass

        if (predictedClass.lowercase() == "shaft bearing") {
            showSecondCaptureButton.value = true
            showRetakePartButton.value = false
        } else {
            showSecondCaptureButton.value = false
            showUploadButton.value = false
            finalResult.value = "NG"
            showRetakePartButton.value = true
        }
    }

    // Proses foto bearing dengan YOLO
    fun processBearingImage(bitmap: Bitmap) {
        viewModelScope.launch(Dispatchers.IO) {
            // Detect dengan threshold SANGAT TINGGI untuk foto final (isLivePreview = false)
            val detections = yoloDetector.detect(bitmap, isLivePreview = false) // Gunakan fungsi detect dengan threshold tinggi
            Log.d("BearingShaftVM", "=== CAPTURED IMAGE DETECTION (PROCESSBEARINGIMAGE) ===")
            Log.d("BearingShaftVM", "Total detections: ${detections.size}")
            Log.d("BearingShaftVM", "Threshold used: ${yoloDetector.confidenceThreshold}")

            // Log hanya top 15 detections
            detections.take(15).forEachIndexed { idx, det ->
                Log.d("BearingShaftVM", "Ball $idx: conf=${"%.4f".format(det.confidence)}, box=${det.box}")
            }
            if (detections.size > 15) {
                Log.d("BearingShaftVM", "... and ${detections.size - 15} more detections")
            }

            // Gambar boxes pada bitmap
            val bitmapWithBoxes = yoloDetector.drawDetections(bitmap, detections)
            val count = detections.size

            withContext(Dispatchers.Main) {
                // Simpan bitmap dengan boxes untuk ditampilkan di preview (opsional)
                // Jangan atur finalResult atau showUploadButton di sini
                bearingPhotoWithBoxes.value = bitmapWithBoxes
                // Jangan atur ballCount di sini, karena ini untuk preview live, bukan hasil final
                // ballCount.value = count
                // Jangan atur finalResult di sini
                // finalResult.value = ...
                // Jangan atur showUploadButton di sini
                // showUploadButton.value = ...
                // Jangan tampilkan popup di sini
                // showResultPopup.value = ...
                // popupFinished.value = ...
            }
        }
    }

    // 🔥 FUNGSI BARU: Proses hasil akhir dari jumlah bola yang dihitung di CameraCaptureScreenYolo
    override fun processFinalBearingResult(count: Int) {
        viewModelScope.launch {
            // Tentukan OK/NG berdasarkan jumlah ball
            // Sesuaikan range ini sesuai spesifikasi bearing Anda
            val result = when {
                count == 9 -> "OK"
                else -> "NG"
            }

            Log.d("BearingShaftVM", "Final Result determined: $result (count=$count)")

            withContext(Dispatchers.Main) {
                // Set hasil final
                finalResult.value = result
                // Set jumlah bola
                ballCount.value = count
                // Siapkan tombol upload
                showUploadButton.value = true

                // Tampilkan popup OK/NG selama 2 detik
                showResultPopup.value = true
                popupFinished.value = false // Reset status popup finished

                // Gunakan delay untuk menyembunyikan popup
                delay(2000) // 2 detik
                showResultPopup.value = false // Sembunyikan popup
                popupFinished.value = true // Tandai bahwa popup selesai
            }
        }
    }

    // Proses frame untuk live preview (OPTIMIZED)
    override fun processFrameForPreview(bitmap: Bitmap) {
        // Skip jika sedang proses
        if (isProcessingFrame) return
        if (isCaptureLocked.value) return

        isProcessingFrame = true
        viewModelScope.launch(Dispatchers.Default) {
            try {
                // Langsung detect dengan flag isLivePreview = true (threshold lebih rendah)
                val detections = yoloDetector.detect(bitmap, isLivePreview = true)

                withContext(Dispatchers.Main) {
                    lastLiveBitmap.value = bitmap
                    lastLiveDetections.value = detections
                }
            } catch (e: Exception) {
                Log.e("BearingShaftVM", "Error processing frame", e)
            } finally {
                // Delay untuk throttle (jangan process setiap frame)
                delay(100) // Process max 10 fps
                isProcessingFrame = false
            }
        }
    }

    fun uploadResult() {
        if (isUploading.value) {
            Log.d("BearingShaftVM", "Upload sedang berlangsung")
            return
        }

        val scan = scanResult.value ?: run {
            Log.e("Upload", "scanResult is null")
            return
        }
        val result = finalResult.value ?: run {
            Log.e("Upload", "finalResult is null")
            return
        }
        val photoPartFile = capturedPartPhotoFile.value ?: run {
            Log.e("Upload", "capturedPartPhotoFile is null")
            return
        }
        val photoBearingFile = capturedBearingPhotoFile.value
        val count = ballCount.value ?: 0

        isUploading.value = true

        viewModelScope.launch(Dispatchers.IO) {
            try {
                val multipart = MultipartBody.Builder().setType(MultipartBody.FORM)
                    .addFormDataPart("Id_Comparison", scan.idComparison.toString())
                    .addFormDataPart("No_Tractor_Record", scan.sequenceNo)
                    .addFormDataPart("Result_Record", result)
                    .addFormDataPart("Text_Record", "9")
                    .addFormDataPart("Predict_Record", count.toString())
                    .addFormDataPart("Production_Date_Record", scan.productionDate)

                // Kompres dan tambahkan foto part
                val bitmapPart = fileToBitmap(photoPartFile)
                if (bitmapPart != null) {
                    val compressedFilePart = compressBitmap(bitmapPart, 500)
                    multipart.addFormDataPart(
                        "Photo_Ng_Path",
                        compressedFilePart.name,
                        compressedFilePart.asRequestBody("image/jpeg".toMediaType())
                    )
                } else {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(context, "Gagal membaca gambar part.", Toast.LENGTH_SHORT).show()
                        isUploading.value = false
                    }
                    return@launch
                }

                // Kompres dan tambahkan foto bearing dengan boxes
                photoBearingFile?.let { bearingFile ->
                    // Gunakan bitmap dengan boxes yang sudah digambar
                    val bitmapWithBoxes = bearingPhotoWithBoxes.value
                    if (bitmapWithBoxes != null) {
                        val compressedFileBearing = compressBitmap(bitmapWithBoxes, 500)
                        multipart.addFormDataPart(
                            "Photo_Ng_Path_Two",
                            compressedFileBearing.name,
                            compressedFileBearing.asRequestBody("image/jpeg".toMediaType())
                        )
                    }
                }

                val requestBody = multipart.build()

                val request = Request.Builder()
                    .url("$apiUrl/bearing-kbc/save") // Pastikan ini endpoint yang benar untuk bearing shaft
                    .post(requestBody)
                    .build()

                val response = client.newCall(request).execute()
                val responseCode = response.code // 🔥 Log response code
                val responseStr = response.body?.string() // 🔥 Simpan response body

                Log.d("Upload", "Response Code: $responseCode") // 🔥 Log
                Log.d("Upload", "Response Body: $responseStr") // 🔥 Log

                if (responseStr.isNullOrEmpty()) {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(context, "Upload gagal: Response kosong dari server.", Toast.LENGTH_LONG).show()
                        saveSuccess.value = false
                        isUploading.value = false
                    }
                    return@launch
                }

                try {
                    val json = JSONObject(responseStr)

                    if (json.getBoolean("success")) {
                        withContext(Dispatchers.Main) {
                            saveSuccess.value = true
                            isUploading.value = false
                        }
                    } else {
                        val errorMsg = json.optString("message", "Unknown error from server")
                        Log.e("Upload", "Upload failed: $errorMsg") // 🔥 Log error dari server
                        withContext(Dispatchers.Main) {
                            Toast.makeText(context, "Upload gagal: $errorMsg", Toast.LENGTH_LONG).show()
                            saveSuccess.value = false
                            isUploading.value = false
                        }
                    }
                } catch (e: org.json.JSONException) { // 🔥 Tangkap JSON parsing error
                    Log.e("Upload", "JSON parsing error: ${e.message}", e) // 🔥 Log error parsing
                    Log.e("Upload", "Raw response was: $responseStr") // 🔥 Log response yang menyebabkan error
                    withContext(Dispatchers.Main) {
                        Toast.makeText(context, "Upload gagal: Format respon server tidak valid (JSON Error).", Toast.LENGTH_LONG).show()
                        saveSuccess.value = false
                        isUploading.value = false
                    }
                } catch (e: Exception) { // 🔥 Tangkap error lain
                    Log.e("Upload", "General error during upload processing: ${e.message}", e)
                    withContext(Dispatchers.Main) {
                        Toast.makeText(context, "Upload gagal: ${e.message}", Toast.LENGTH_LONG).show()
                        saveSuccess.value = false
                        isUploading.value = false
                    }
                }
            } catch (e: Exception) {
                Log.e("Upload", "Error", e)
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "Gagal mengunggah: ${e.message}", Toast.LENGTH_LONG).show()
                    saveSuccess.value = false
                    isUploading.value = false
                }
            }
        }
    }

    fun resetForRetakePart() {
        partDetectionResult.value = null
        capturedPartPhotoFile.value = null
        showSecondCaptureButton.value = false
        showRetakePartButton.value = false
    }

    override fun resetBearingDetectionState() {
        // Jangan reset capturedBearingPhotoFile, bearingPhotoWithBoxes di sini kecuali memang tidak dibutuhkan lagi
        // Reset hasil deteksi
        ballCount.value = null
        finalResult.value = null
        // Reset status upload
        showUploadButton.value = false
        // Reset popup
        showResultPopup.value = false
        popupFinished.value = false
        // Jika kamu menyimpan hasil deteksi sementara lain di ViewModel, reset juga di sini
        // Contoh:
        // bearingPhotoWithBoxes.value = null // Hanya jika kamu ingin membersihkan preview
        // lastLiveDetections.value = emptyList() // Jika ini terkait dengan hasil akhir
    }

    fun resetLiveBearingCameraState() {
        Log.d("BearingShaftVM", "Reset LIVE bearing camera state")

        // Live preview
        lastLiveBitmap.value = null
        lastLiveDetections.value = emptyList()

        // Lock & processing
        isCaptureLocked.value = false
    }

    private fun compressBitmap(bitmap: Bitmap, maxSizeKB: Int = 500): File {
        val file = File(context.cacheDir, "compressed_${System.currentTimeMillis()}.jpg")
        var quality = 90
        do {
            val stream = FileOutputStream(file)
            bitmap.compress(Bitmap.CompressFormat.JPEG, quality, stream)
            stream.flush()
            stream.close()
            quality -= 10
        } while (file.length() > maxSizeKB * 1024 && quality > 10)
        return file
    }

    private fun fileToBitmap(file: File): Bitmap? {
        return BitmapFactory.decodeFile(file.path)
    }

    override fun onCleared() {
        tflitePart.close()
        yoloDetector.close()
        super.onCleared()
    }
}