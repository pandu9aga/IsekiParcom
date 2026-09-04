// app/src/main/java/com/example/isekiparcom/viewmodel/BearingKoyoViewModel.kt

package com.example.isekiparcom.viewmodel

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Log
import android.widget.Toast
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.isekiparcom.utils.TfliteInference
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.CoroutineScope
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

data class ScanResultKoyo(
    val sequenceNo: String,
    val tractorType: String,
    val productionDate: String,
    val idComparison: Int = 3
)

class BearingKoyoViewModel(private val context: Context) : ViewModel() {
    private val apiUrl = "http://192.168.173.201/iseki_parcom/public/api"
    private val client = OkHttpClient()
    // 🔥 Gunakan path model yang benar untuk Bearing KOYO
    private val tflitePart = TfliteInference(context, "bearing_koyo/model_unquant.tflite", "bearing_koyo/labels.txt")

    // State untuk scan QR
    val scanResult = mutableStateOf<ScanResultKoyo?>(null) // Pastikan ScanResult didefinisikan di file lain atau impor
    // Hapus state untuk foundPart
    // val foundPart = mutableStateOf<PartData?>(null)
    val validationMessage = mutableStateOf<String?>(null)
    val showCaptureButton = mutableStateOf(false) // Tombol untuk ambil foto part langsung
    val capturedPartPhotoFile = mutableStateOf<File?>(null)
    val partDetectionResult = mutableStateOf<String?>(null) // "metal bearing" atau "shaft" atau apapun
    val showSecondCaptureButton = mutableStateOf(false) // Tombol untuk ambil foto OCR (muncul jika part = metal bearing)
    val showRetakePartButton = mutableStateOf(false) // 🔥 Tombol ambil ulang foto part (muncul jika part != metal bearing)
    val capturedOcrPhotoFile = mutableStateOf<File?>(null)
    val ocrResult = mutableStateOf<String?>(null) // Hasil OCR (setelah foto OCR diambil)
    val finalResult = mutableStateOf<String?>(null) // "OK" atau "NG" (final setelah OCR selesai)
    val showUploadButton = mutableStateOf(false) // Tombol simpan (muncul setelah OCR selesai)
    val saveSuccess = mutableStateOf<Boolean?>(null)
    val showResultPopup = mutableStateOf(false) // Popup OK/NG muncul setelah OCR selesai
    val popupFinished = mutableStateOf(false) // Popup selesai ditampilkan

    // State baru untuk menyimpan hasil final
    val textRecordForUpload = mutableStateOf<String?>(null) // Selalu "KOYO"
    val predictRecordForUpload = mutableStateOf<String?>(null) // Hasil OCR yang diproses

    val isUploading = mutableStateOf(false) // Status upload

    // Fungsi untuk reset state terkait scan sebelumnya
    private fun resetScanStates() {
        validationMessage.value = null
        showCaptureButton.value = false
        partDetectionResult.value = null
        showSecondCaptureButton.value = false
        showRetakePartButton.value = false
        ocrResult.value = null
        finalResult.value = null
        showUploadButton.value = false
        showResultPopup.value = false
        popupFinished.value = false
        textRecordForUpload.value = null
        predictRecordForUpload.value = null
        capturedPartPhotoFile.value = null
        capturedOcrPhotoFile.value = null
        isUploading.value = false
        // Jangan reset scanResult di sini, karena kita ingin bisa scan ulang dengan data baru
    }

    fun handleQrScanned(rawQr: String) {
        try {
            val parts = rawQr.split(";")
            // 🔥 QR sekarang harus punya 3 bagian: sequence, production_date, tractor_type
            if (parts.size < 3) throw Exception("Format QR salah, harus memiliki sequence, production date, dan tractor type")
            val sequenceNo = parts[0].trim()
            // 🔥 Ambil production date dari bagian kedua
            val productionDate = parts[1].trim()
            val tractorType = parts[2].trim()
            // 🔥 Gunakan production date di constructor
            val newScanResult = ScanResultKoyo(sequenceNo, tractorType, productionDate)

            resetScanStates()

            // Set scan result baru
            scanResult.value = newScanResult
            // Tampilkan tombol validasi
            showCaptureButton.value = true

            // 🔥 Panggil validateRule langsung setelah scan
            validateRule()

        } catch (e: Exception) {
            Log.e("QR", "Parse error", e)
            validationMessage.value = "Format QR salah: ${e.message}"
        }
    }

    // 🔥 HAPUS FUNGSI fetchPartByTractorType
    // private fun fetchPartByTractorType(tractorType: String) { ... }

    fun validateRule() {
        val result = scanResult.value ?: return
        // Gunakan viewModelScope
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val json = JSONObject().apply {
                    put("sequence_no", result.sequenceNo)
                    put("id_comparison", 3)
                    // 🔥 Tambahkan production date ke request body
                    put("production_date", result.productionDate)
                }

                val mediaType = "application/json; charset=utf-8".toMediaType()
                val body = RequestBody.create(mediaType, json.toString())

                val request = Request.Builder()
                    .url("$apiUrl/bearing-koyo/validate") // Gunakan endpoint yang sesuai
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

    fun compressBitmap(bitmap: Bitmap, maxSizeKB: Int = 500): File {
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

    // 🔥 Fungsi baru untuk memproses foto part dengan TFLite
    fun processPartImage(bitmap: Bitmap) {
        val predictedClass = tflitePart.run(bitmap) // Harus mengembalikan "metal bearing" atau "shaft" atau apapun
        partDetectionResult.value = predictedClass

        if (predictedClass.lowercase() == "metal bearing") {
            showSecondCaptureButton.value = true // Munculkan tombol ambil foto OCR
            showRetakePartButton.value = false // Jangan tampilkan tombol retake part
        } else { // Jika bukan metal bearing (shaft, dll)
            // Jangan tampilkan tombol ambil foto OCR
            showSecondCaptureButton.value = false
            // Jangan tampilkan tombol simpan hasil
            showUploadButton.value = false
            // Set finalResult ke NG karena part salah
            finalResult.value = "NG"
            // Tampilkan tombol ambil ulang foto part
            showRetakePartButton.value = true
        }
    }

    // 🔥 Fungsi untuk memproses foto OCR dengan ML Kit
    fun processOcrImage(bitmap: Bitmap) {
        val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
        val image = InputImage.fromBitmap(bitmap, 0)

        recognizer.process(image)
            .addOnSuccessListener { visionText ->
                val rawText = visionText.text
                val cleanedText = rawText.replace("\\s".toRegex(), "") // Hapus semua spasi
                ocrResult.value = cleanedText

                val containsKoyo = cleanedText.contains("KOYO", ignoreCase = true)
                val finalResult = if (containsKoyo) "OK" else "NG"
                val finalPredictRecord = if (containsKoyo) "KOYO" else cleanedText

                this.finalResult.value = finalResult
                textRecordForUpload.value = "KOYO" // Konstan
                predictRecordForUpload.value = finalPredictRecord // Sesuaikan
                showUploadButton.value = true // Munculkan tombol simpan

                // 🔥 Tampilkan popup OK/NG selama 2 detik
                showResultPopup.value = true
                popupFinished.value = false // Reset status popup

                // Gunakan coroutine untuk delay
                viewModelScope.launch {
                    delay(2000) // 2 detik
                    showResultPopup.value = false // Sembunyikan popup
                    popupFinished.value = true // Tandai bahwa popup selesai
                }
            }
            .addOnFailureListener { e ->
                Log.e("OCR", "Gagal", e)
                ocrResult.value = "OCR_FAILED"
                finalResult.value = "NG"
                textRecordForUpload.value = "KOYO"
                predictRecordForUpload.value = "OCR_FAILED"
                showUploadButton.value = true

                // 🔥 Tampilkan popup NG jika gagal
                showResultPopup.value = true
                popupFinished.value = false
                viewModelScope.launch {
                    delay(2000)
                    showResultPopup.value = false
                    popupFinished.value = true
                }
            }
    }

    fun uploadResult() {
        if (isUploading.value) {
            Log.d("BearingKoyoVM", "Upload sedang berlangsung, mencegah duplikasi.")
            return
        }

        val scan = scanResult.value ?: run {
            Log.e("Upload", "scanResult.value is null")
            return
        }
        val result = finalResult.value ?: run {
            Log.e("Upload", "finalResult.value is null")
            return
        }
        val photoPartFile = capturedPartPhotoFile.value ?: run {
            Log.e("Upload", "capturedPartPhotoFile.value is null")
            return
        }
        val photoOcrFile = capturedOcrPhotoFile.value

        val textRecord = textRecordForUpload.value ?: ""
        val predictRecord = predictRecordForUpload.value ?: ""

        isUploading.value = true

        viewModelScope.launch(Dispatchers.IO) {
            try {
                val multipart = MultipartBody.Builder().setType(MultipartBody.FORM)
                    .addFormDataPart("Id_Comparison", scan.idComparison.toString())
                    .addFormDataPart("No_Tractor_Record", scan.sequenceNo)
                    .addFormDataPart("Result_Record", result)
                    .addFormDataPart("Text_Record", textRecord)
                    .addFormDataPart("Predict_Record", predictRecord)
                    // 🔥 Tambahkan production date ke multipart
                    .addFormDataPart("Production_Date_Record", scan.productionDate)

                // KOMPRES DAN TAMBAHKAN FOTO PART
                val bitmapPart = fileToBitmap(photoPartFile)
                if (bitmapPart != null) {
                    val compressedFilePart = compressBitmap(bitmapPart, 500)
                    multipart.addFormDataPart(
                        "Photo_Ng_Path",
                        compressedFilePart.name,
                        compressedFilePart.asRequestBody("image/jpeg".toMediaType())
                    )
                } else {
                    Log.e("Upload", "Gagal membaca bitmap dari Photo_Ng_Path")
                    withContext(Dispatchers.Main) {
                        Toast.makeText(context, "Gagal membaca gambar part.", Toast.LENGTH_SHORT).show()
                        isUploading.value = false
                    }
                    return@launch
                }

                // KOMPRES DAN TAMBAHKAN FOTO OCR (jika ada)
                photoOcrFile?.let { ocrFile ->
                    val bitmapOcr = fileToBitmap(ocrFile)
                    if (bitmapOcr != null) {
                        val compressedFileOcr = compressBitmap(bitmapOcr, 500)
                        multipart.addFormDataPart(
                            "Photo_Ng_Path_Two",
                            compressedFileOcr.name,
                            compressedFileOcr.asRequestBody("image/jpeg".toMediaType())
                        )
                    } else {
                        Log.e("Upload", "Gagal membaca bitmap dari Photo_Ng_Path_Two")
                        withContext(Dispatchers.Main) {
                            Toast.makeText(context, "Gagal membaca gambar OCR.", Toast.LENGTH_SHORT).show()
                            isUploading.value = false
                        }
                        return@launch
                    }
                }

                val requestBody = multipart.build()

                Log.d("Upload", "URL: $apiUrl/bearing-koyo/save")
                Log.d("Upload", "Multipart size: ${requestBody.contentLength()} bytes")
                Log.d("Upload", "Multipart type: ${requestBody.contentType()}")

                val request = Request.Builder()
                    .url("$apiUrl/bearing-koyo/save") // Gunakan endpoint yang sesuai
                    .post(requestBody)
                    .build()
                val response = client.newCall(request).execute()

                Log.d("Upload", "Response Code: ${response.code}")
                val responseStr = response.body?.string()
                Log.d("Upload", "Response Body: $responseStr")

                val json = JSONObject(responseStr)
                if (json.getBoolean("success")) {
                    withContext(Dispatchers.Main) {
                        saveSuccess.value = true
                        isUploading.value = false
                    }
                } else {
                    val errorMsg = json.optString("message", "Unknown error from server")
                    Log.e("Upload", "Upload gagal: $errorMsg")
                    withContext(Dispatchers.Main) {
                        Toast.makeText(context, "Upload gagal: $errorMsg", Toast.LENGTH_LONG).show()
                        saveSuccess.value = false
                        isUploading.value = false
                    }
                }
            } catch (e: java.net.ConnectException) {
                Log.e("Upload", "Koneksi gagal: ${e.message}", e)
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "Koneksi gagal: Periksa jaringan atau URL API.", Toast.LENGTH_LONG).show()
                    saveSuccess.value = false
                    isUploading.value = false
                }
            } catch (e: java.net.UnknownHostException) {
                Log.e("Upload", "Host tidak ditemukan: ${e.message}", e)
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "Host tidak ditemukan: ${e.message}", Toast.LENGTH_LONG).show()
                    saveSuccess.value = false
                    isUploading.value = false
                }
            } catch (e: Exception) {
                Log.e("Upload", "Gagal mengunggah: ${e.message}", e)
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "Gagal mengunggah: ${e.message ?: e.javaClass.simpleName}", Toast.LENGTH_LONG).show()
                    saveSuccess.value = false
                    isUploading.value = false
                }
            }
        }
    }

    fun setResult(partResult: String?, ocrResult: String?, finalRes: String) {
        partDetectionResult.value = partResult
        this.ocrResult.value = ocrResult
        finalResult.value = finalRes
        showUploadButton.value = true
    }

    override fun onCleared() {
        tflitePart.close()
        super.onCleared()
    }

    fun resetForRetakePart() {
        partDetectionResult.value = null
        capturedPartPhotoFile.value = null
        showSecondCaptureButton.value = false
        showRetakePartButton.value = false
        // Tidak perlu reset ocrResult, finalResult, dll.
    }

    private fun uriToFile(uri: Uri): File {
        val inputStream = context.contentResolver.openInputStream(uri)
        val file = File(context.cacheDir, "temp_image_${System.currentTimeMillis()}.jpg")
        inputStream?.use { input ->
            FileOutputStream(file).use { output ->
                input.copyTo(output)
            }
        }
        return file
    }

    // Fungsi untuk mengonversi file ke bitmap
    private fun fileToBitmap(file: File): Bitmap? {
        return BitmapFactory.decodeFile(file.path)
    }
}