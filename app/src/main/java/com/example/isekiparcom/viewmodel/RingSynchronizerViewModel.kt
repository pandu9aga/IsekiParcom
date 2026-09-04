package com.example.isekiparcom.viewmodel

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import android.widget.Toast
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.isekiparcom.utils.TfliteInference
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

data class ScanResult(
    val sequenceNo: String,
    val tractorType: String,
    val productionDate: String,
    val idComparison: Int = 1
)

data class PartData(
    val idPart: Int,
    val codePart: String,
    val namePart: String,
    val idTractor: Int,
    val idComparison: Int
)

class RingSynchronizerViewModel(private val context: Context) : ViewModel() {
    private val apiUrl = "http://192.168.173.201/iseki_parcom/public/api"
    private val client = OkHttpClient()
    private val tflite = TfliteInference(context, "ring_synchronizer/model_unquant.tflite", "ring_synchronizer/labels.txt")

    val scanResult = mutableStateOf<ScanResult?>(null)
    val foundPart = mutableStateOf<PartData?>(null)
    val validationMessage = mutableStateOf<String?>(null)
    val showCaptureButton = mutableStateOf(false)
    val capturedPhotoFile = mutableStateOf<File?>(null)
    val resultStatus = mutableStateOf<String?>(null)
    val showUploadButton = mutableStateOf(false)
    val saveSuccess = mutableStateOf<Boolean?>(null)

    val showResultPopup = mutableStateOf(false)
    val popupFinished = mutableStateOf(false)

    val isUploading = mutableStateOf(false) // 🔥 Tambahkan ini

    fun processImage(bitmap: Bitmap) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val predictedCode = tflite.run(bitmap)
                val expectedCode = foundPart.value?.codePart ?: ""
                val result = if (predictedCode.trim() == expectedCode.trim()) "OK" else "NG"

                val compressedFile = compressBitmap(bitmap, 500)

                // Update state di Main thread
                withContext(Dispatchers.Main) {
                    resultStatus.value = result
                    capturedPhotoFile.value = compressedFile
                    showUploadButton.value = true

                    // 🔥 Tampilkan popup OK/NG selama 2 detik
                    showResultPopup.value = true
                    popupFinished.value = false

                    // Gunakan viewModelScope untuk delay
                    viewModelScope.launch {
                        delay(2000)
                        showResultPopup.value = false
                        popupFinished.value = true
                    }
                }
            } catch (e: Exception) {
                Log.e("VM", "Error processing image", e)
                withContext(Dispatchers.Main) {
                    resultStatus.value = "ERROR"
                    showUploadButton.value = false
                }
            }
        }
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
            val newScanResult = ScanResult(sequenceNo, tractorType, productionDate)

            // 🔥 Reset semua state terkait scan sebelumnya
            resetScanStates()

            // Set scan result baru
            scanResult.value = newScanResult
            Log.d("VM_DEBUG", "New QR scanned: ${newScanResult.sequenceNo}, Prod Date: ${newScanResult.productionDate}, fetching part...")

            // Ambil part baru
            fetchPartByTractorType(tractorType)
        } catch (e: Exception) {
            Log.e("QR", "Parse error", e)
            validationMessage.value = "Format QR salah: ${e.message}"
        }
    }

    // 🔥 Fungsi untuk mereset state terkait scan sebelumnya
    private fun resetScanStates() {
        foundPart.value = null
        validationMessage.value = null
        showCaptureButton.value = false
        resultStatus.value = null
        capturedPhotoFile.value = null
        showUploadButton.value = false
        saveSuccess.value = null
        isUploading.value = false
    }

    fun fetchPartByTractorType(tractorType: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val url = "$apiUrl/ring-synchronizer/part-by-tractor/${tractorType}"
            Log.d("API_DEBUG", "Fetching part for tractorType: $tractorType")
            Log.d("API_DEBUG", "API URL: $url")

            val client = OkHttpClient()
            val request = Request.Builder().url(url).build()

            try {
                val response = client.newCall(request).execute()
                val code = response.code
                val rawBody = response.body?.string()
                Log.d("API_DEBUG", "HTTP $code | Body raw: $rawBody")

                if (code == 200 && !rawBody.isNullOrBlank() && rawBody.trim() != "{}") {
                    val json = JSONObject(rawBody)
                    val part = PartData(
                        idComparison = json.getInt("Id_Comparison"),
                        codePart = json.getString("Code_Part"),
                        namePart = json.getString("Name_Part"),
                        idPart = json.getInt("Id_Part"),
                        idTractor = json.getInt("Id_Tractor")
                    )

                    withContext(Dispatchers.Main) {
                        foundPart.value = part
                        Log.d("API_DEBUG", "✅ Part parsed: $part")
                    }
                } else {
                    Log.e("API_DEBUG", "⚠️ Body kosong atau format salah: $rawBody")
                }
            } catch (e: Exception) {
                Log.e("API_DEBUG", "Error fetching part: ${e.message}", e)
            }
        }
    }

    fun validateRule(onComplete: (Boolean) -> Unit) {
        val result = scanResult.value ?: run { onComplete(false); return }
        val part = foundPart.value ?: run { onComplete(false); return }

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val json = JSONObject().apply {
                    put("sequence_no", result.sequenceNo)
                    put("id_comparison", result.idComparison)
                    // 🔥 Tambahkan production date ke request body
                    put("production_date", result.productionDate)
                }

                val mediaType = "application/json; charset=utf-8".toMediaType()
                val body = RequestBody.create(mediaType, json.toString())

                val request = Request.Builder()
                    .url("$apiUrl/ring-synchronizer/validate") // Gunakan endpoint yang sesuai
                    .post(body)
                    .build()
                val response = client.newCall(request).execute()
                val respJson = JSONObject(response.body?.string())
                val success = respJson.getBoolean("success")
                val message = respJson.getString("message")

                withContext(Dispatchers.Main) {
                    validationMessage.value = message
                    if (success) {
                        showCaptureButton.value = true
                    } else {
                        showCaptureButton.value = false
                    }
                    onComplete(success)
                }
            } catch (e: Exception) {
                Log.e("API", "Validate error", e)
                withContext(Dispatchers.Main) {
                    validationMessage.value = "Gagal memvalidasi: ${e.message}"
                    showCaptureButton.value = false
                    onComplete(false)
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

    fun uploadResult() {
        if (isUploading.value) return
        isUploading.value = true

        val scan = scanResult.value ?: run { isUploading.value = false; return }
        val part = foundPart.value ?: run { isUploading.value = false; return }
        val result = resultStatus.value ?: run { isUploading.value = false; return }
        val photoFile = capturedPhotoFile.value ?: run { isUploading.value = false; return }

        val multipart = MultipartBody.Builder().setType(MultipartBody.FORM)
            .addFormDataPart("Id_Comparison", part.idComparison.toString())
            .addFormDataPart("Id_Tractor", part.idTractor.toString())
            .addFormDataPart("Id_Part", part.idPart.toString())
            .addFormDataPart("No_Tractor_Record", scan.sequenceNo)
            .addFormDataPart("Result_Record", result)
            // 🔥 Tambahkan production date ke multipart
            .addFormDataPart("Production_Date_Record", scan.productionDate)

        photoFile.let {
            val fileBody = RequestBody.create("image/jpeg".toMediaType(), it)
            multipart.addFormDataPart("Photo_Ng_Path", it.name, fileBody)
        }

        val requestBody = multipart.build()

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val request = Request.Builder()
                    .url("$apiUrl/ring-synchronizer/save") // Gunakan endpoint yang sesuai
                    .post(requestBody)
                    .build()
                val response = client.newCall(request).execute()
                val json = JSONObject(response.body?.string())
                if (json.getBoolean("success")) {
                    withContext(Dispatchers.Main) {
                        saveSuccess.value = true
                        isUploading.value = false
                    }
                } else {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(context, "Upload gagal: ${json.optString("message", "Unknown error")}", Toast.LENGTH_LONG).show()
                        isUploading.value = false
                    }
                }
            } catch (e: Exception) {
                Log.e("Upload", "Gagal", e)
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "Gagal mengunggah: ${e.message}", Toast.LENGTH_LONG).show()
                    isUploading.value = false
                }
            }
        }
    }

    override fun onCleared() {
        tflite.close()
        super.onCleared()
    }

    fun setResult(result: String, photoFile: File) {
        Log.d("VM_DEBUG", "setResult called with result: $result, file: ${photoFile.name}")
        resultStatus.value = result
        capturedPhotoFile.value = photoFile
        showUploadButton.value = true
        Log.d("VM_DEBUG", "showUploadButton set to: ${showUploadButton.value}")
    }

    fun resetAfterValidationError() {
        showCaptureButton.value = false
        resultStatus.value = null
        capturedPhotoFile.value = null
        showUploadButton.value = false
        saveSuccess.value = null
    }
}