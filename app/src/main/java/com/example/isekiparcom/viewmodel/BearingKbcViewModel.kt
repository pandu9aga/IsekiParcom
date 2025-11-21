package com.example.isekiparcom.viewmodel

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import android.widget.Toast
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.isekiparcom.utils.TfliteInference
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.asRequestBody
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream

// Hapus data class PartData karena tidak digunakan lagi
// data class PartData(...)

class BearingKbcViewModel(private val context: Context) : ViewModel() {
    private val apiUrl = "http://192.168.173.207/iseki_parcom/public/api"
    private val client = OkHttpClient()
    private val tflite = TfliteInference(context, "bearing_kbc/model_unquant.tflite", "bearing_kbc/labels.txt")

    // State untuk scan QR
    val scanResult = mutableStateOf<ScanResult?>(null) // Pastikan ScanResult didefinisikan di file lain atau impor
    // Hapus state untuk foundPart
    // val foundPart = mutableStateOf<PartData?>(null)
    val validationMessage = mutableStateOf<String?>(null)
    val showCaptureButton = mutableStateOf(false) // Tombol untuk ambil foto part langsung
    val capturedPartPhotoFile = mutableStateOf<File?>(null)
    val partDetectionResult = mutableStateOf<String?>(null) // "shaft" atau "metal"
    val showSecondCaptureButton = mutableStateOf(false) // Tombol untuk ambil foto OCR
    val capturedOcrPhotoFile = mutableStateOf<File?>(null)
    val ocrResult = mutableStateOf<String?>(null)
    val finalResult = mutableStateOf<String?>(null) // "OK" atau "NG"
    val showUploadButton = mutableStateOf(false)
    val saveSuccess = mutableStateOf<Boolean?>(null)

    fun handleQrScanned(rawQr: String) {
        try {
            val parts = rawQr.split(";")
            if (parts.size < 3) throw Exception("Format QR salah")
            val sequenceNo = parts[0].trim()
            val tractorType = parts[2].trim()
            // Gunakan Id_Comparison = 2 untuk Bearing KBC
            scanResult.value = ScanResult(sequenceNo, tractorType, idComparison = 2)
            // 🔥 Langsung tampilkan tombol validasi (karena tidak ada PartData lagi)
            showCaptureButton.value = true
        } catch (e: Exception) {
            Log.e("QR", "Parse error", e)
        }
    }

    // 🔥 HAPUS FUNGSI fetchPartByTractorType
    // private fun fetchPartByTractorType(tractorType: String) { ... }

    fun validateRule() {
        val result = scanResult.value ?: return
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val json = JSONObject().apply {
                    put("sequence_no", result.sequenceNo)
                    put("id_comparison", result.idComparison)
                }

                val mediaType = "application/json; charset=utf-8".toMediaType()
                val body = RequestBody.create(mediaType, json.toString())

                val request = Request.Builder()
                    .url("$apiUrl/bearing-kbc/validate") // 🔥 Ganti endpoint sesuai API Laravel kamu
                    .post(body)
                    .build()
                val response = client.newCall(request).execute()
                val respJson = JSONObject(response.body?.string())
                val success = respJson.getBoolean("success")
                val message = respJson.getString("message")
                withContext(Dispatchers.Main) {
                    validationMessage.value = message
                    // 🔥 Tidak ada PartData, jadi langsung set showCaptureButton jika validasi sukses
                    if (success) showCaptureButton.value = true
                }
            } catch (e: Exception) {
                Log.e("API", "Validate error", e)
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

    fun runTfliteForPart(bitmap: Bitmap) {
        val predictedClass = tflite.run(bitmap) // Harus mengembalikan "shaft" atau "metal"
        partDetectionResult.value = predictedClass
        if (predictedClass.lowercase() == "shaft") {
            showSecondCaptureButton.value = true // Munculkan tombol ambil foto OCR
        } else { // Jika metal atau lainnya
            finalResult.value = "NG"
            showUploadButton.value = true // Siap upload langsung
        }
    }

    fun runOcr(bitmap: Bitmap) {
        val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
        val image = com.google.mlkit.vision.common.InputImage.fromBitmap(bitmap, 0)

        recognizer.process(image)
            .addOnSuccessListener { visionText ->
                val rawText = visionText.text
                val cleanedText = rawText.replace("\\s".toRegex(), "") // Hapus semua spasi
                ocrResult.value = cleanedText

                val containsKbc = cleanedText.contains("KBC", ignoreCase = true)
                if (containsKbc) {
                    finalResult.value = "OK"
                } else {
                    finalResult.value = "NG"
                }
                showUploadButton.value = true // Siap upload setelah OCR
            }
            .addOnFailureListener { e ->
                Log.e("OCR", "Gagal", e)
                // Anggap sebagai NG jika OCR gagal
                ocrResult.value = "OCR_FAILED"
                finalResult.value = "NG"
                showUploadButton.value = true
            }
    }

    fun uploadResult() {
        val scan = scanResult.value ?: return
        // 🔥 HAPUS: val part = foundPart.value ?: return
        val result = finalResult.value ?: return
        val photoPart = capturedPartPhotoFile.value
        val photoOcr = capturedOcrPhotoFile.value // Bisa null jika hasilnya NG di TFLite

        // 🔥 SESUAIKAN DENGAN STRUKTUR TABEL RECORD YANG BARU
        // Karena tidak ada part, kita gunakan nilai default atau null untuk field terkait part
        // Misalnya: Id_Part = null, Code_Part tidak disertakan, dll.
        // Atau kamu bisa menyimpan Id_Part = 0 atau -1 sebagai placeholder jika tidak digunakan.

        val multipart = MultipartBody.Builder().setType(MultipartBody.FORM)
            .addFormDataPart("Id_Comparison", scan.idComparison.toString()) // Gunakan dari scan result
            .addFormDataPart("No_Tractor_Record", scan.sequenceNo)
            .addFormDataPart("Result_Record", result)
            // 🔥 Tambahkan field Text_Record dan Predict_Record
            .addFormDataPart("Text_Record", ocrResult.value ?: "")
            .addFormDataPart("Predict_Record", ocrResult.value ?: "")


        // Tambahkan foto part (wajib)
        photoPart?.let {
            multipart.addFormDataPart(
                "Photo_Ng_Path", // Nama field di Laravel
                it.name,
                it.asRequestBody("image/jpeg".toMediaType())
            )
        }

        // Tambahkan foto OCR (opsional, hanya jika diambil)
        photoOcr?.let {
            multipart.addFormDataPart(
                "Photo_Ng_Path_Two", // Nama field di Laravel
                it.name,
                it.asRequestBody("image/jpeg".toMediaType())
            )
        }

        // 🔥 JIKA KAMU TIDAK MENYIMPAN Id_Tractor atau Id_Part, HAPUS BARIS INI:
        // .addFormDataPart("Id_Tractor", part.idTractor.toString())
        // .addFormDataPart("Id_Part", part.idPart.toString())

        // 🔥 JIKA KAMU TIDAK MENYIMPAN Id_User di tahap ini, HAPUS ATAU ISI DENGAN NILAI DEFAULT:
        // .addFormDataPart("Id_User", userId.toString())

        multipart.build()

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val request = Request.Builder()
                    .url("$apiUrl/bearing-kbc/save") // 🔥 Ganti endpoint sesuai API Laravel kamu
                    .post(multipart.build())
                    .build()
                val response = client.newCall(request).execute()
                val json = JSONObject(response.body?.string())
                if (json.getBoolean("success")) {
                    withContext(Dispatchers.Main) {
                        saveSuccess.value = true
                    }
                } else {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(context, "Upload gagal: ${json.optString("message", "Unknown error")}", Toast.LENGTH_LONG).show()
                        saveSuccess.value = false
                    }
                }
            } catch (e: Exception) {
                Log.e("Upload", "Gagal", e)
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "Gagal mengunggah: ${e.message}", Toast.LENGTH_LONG).show()
                    saveSuccess.value = false
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
        tflite.close()
        super.onCleared()
    }
}