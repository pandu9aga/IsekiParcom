// app/src/main/java/com/example/isekiparcom/viewmodel/BearingKoyoViewModel.kt

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
import com.example.isekiparcom.viewmodel.ScanResult
import com.example.isekiparcom.viewmodel.PartData

class BearingKoyoViewModel(private val context: Context) : ViewModel() {
    private val apiUrl = "http://192.168.173.207/iseki_parcom/public/api" // Ganti jika perlu
    private val client = OkHttpClient()
    private val tflite = TfliteInference(context) // Pastikan model shaft/metal ada

    val scanResult = mutableStateOf<ScanResult?>(null)
    val foundPart = mutableStateOf<PartData?>(null)
    val validationMessage = mutableStateOf<String?>(null)
    val showCaptureButton = mutableStateOf(false) // Untuk foto pertama
    val capturedPartPhotoFile = mutableStateOf<File?>(null)
    val partDetectionResult = mutableStateOf<String?>(null) // "shaft" atau "metal"
    val showSecondCaptureButton = mutableStateOf(false) // Untuk foto kedua (OCR)
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
            scanResult.value = ScanResult(sequenceNo, tractorType, idComparison = 3)
            fetchPartByTractorType(tractorType)
        } catch (e: Exception) {
            Log.e("QR", "Parse error", e)
        }
    }

    private fun fetchPartByTractorType(tractorType: String) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val url = "$apiUrl/bearing-koyo/part-by-tractor/${tractorType}" // 🔥 Ganti endpoint
                val request = Request.Builder().url(url).build()
                val response = client.newCall(request).execute()
                if (response.isSuccessful) {
                    val jsonStr = response.body?.string()
                    if (jsonStr != "null") {
                        val json = JSONObject(jsonStr)
                        val part = PartData(
                            idPart = json.getInt("id_part"),
                            codePart = json.getString("code_part"),
                            namePart = json.getString("name_part"),
                            idTractor = json.getInt("id_tractor"),
                            idComparison = json.getInt("id_comparison")
                        )
                        withContext(Dispatchers.Main) {
                            foundPart.value = part
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e("API", "Fetch part error", e)
            }
        }
    }

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
                    .url("$apiUrl/bearing-koyo/validate") // 🔥 Ganti endpoint
                    .post(body)
                    .build()
                val response = client.newCall(request).execute()
                val respJson = JSONObject(response.body?.string())
                val success = respJson.getBoolean("success")
                val message = respJson.getString("message")
                withContext(Dispatchers.Main) {
                    validationMessage.value = message
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
        if (predictedClass.lowercase() == "metal") {
            showSecondCaptureButton.value = true // Munculkan tombol ambil foto OCR
        } else { // Jika shaft atau lainnya
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

                val containsKoyo = cleanedText.contains("KOYO", ignoreCase = true)
                if (containsKoyo) {
                    finalResult.value = "OK"
                    // Gunakan "KOYO" untuk Text dan Predict Record
                    // Simpan di variabel lokal atau state jika perlu untuk upload
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
        val part = foundPart.value ?: return
        val result = finalResult.value ?: return
        val photoPart = capturedPartPhotoFile.value
        val photoOcr = capturedOcrPhotoFile.value // Bisa null jika hasilnya NG di TFLite

        // Tentukan Text_Record dan Predict_Record berdasarkan hasil
        val (textRecord, predictRecord) = if (partDetectionResult.value?.lowercase() == "shaft") {
            val ocrText = ocrResult.value ?: ""
            if (ocrText.contains("KOYO", ignoreCase = true)) {
                Pair("KOYO", "KOYO")
            } else {
                Pair(ocrText, ocrText) // Atau bisa gunakan ocrText jika NG
            }
        } else {
            // Jika bukan shaft, tidak ada OCR, gunakan hasil TFLite atau kosong
            Pair("", "") // Atau nilai default lain
        }


        val multipart = MultipartBody.Builder().setType(MultipartBody.FORM)
            .addFormDataPart("Id_Comparison", part.idComparison.toString())
            .addFormDataPart("Id_Tractor", part.idTractor.toString())
            .addFormDataPart("Id_Part", part.idPart.toString())
            .addFormDataPart("No_Tractor_Record", scan.sequenceNo)
            .addFormDataPart("Result_Record", result)
            .addFormDataPart("Text_Record", textRecord)
            .addFormDataPart("Predict_Record", predictRecord)


        // Tambahkan foto part (wajib)
        photoPart?.let {
            multipart.addFormDataPart(
                "Photo_Ng_Path",
                it.name,
                it.asRequestBody("image/jpeg".toMediaType())
            )
        }

        // Tambahkan foto OCR (opsional, hanya jika diambil)
        photoOcr?.let {
            multipart.addFormDataPart(
                "Photo_Ng_Path_Two", // Nama field sesuai database
                it.name,
                it.asRequestBody("image/jpeg".toMediaType())
            )
        }

        multipart.build()

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val request = Request.Builder()
                    .url("$apiUrl/bearing-koyo/save") // 🔥 Ganti endpoint
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