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
    private val apiUrl = "http://192.168.173.207/iseki_parcom/public/api"
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

    fun handleQrScanned(rawQr: String) {
        Log.d("QRDEBUG", "QR Scanned: $rawQr")
        try {
            val parts = rawQr.split(";")
            if (parts.size < 3) throw Exception("Format QR salah")
            val sequenceNo = parts[0].trim()
            val tractorType = parts[2].trim()
            scanResult.value = ScanResult(sequenceNo, tractorType)
            fetchPartByTractorType(tractorType)
        } catch (e: Exception) {
            Log.e("QR", "Parse error", e)
        }
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
                }

                val mediaType = "application/json; charset=utf-8".toMediaType()
                val body = RequestBody.create(mediaType, json.toString())

                val request = Request.Builder()
                    .url("$apiUrl/ring-synchronizer/validate")
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

    fun runTensorFlowLite(bitmap: Bitmap) {
        val predictedCode = tflite.run(bitmap)
        val actualCode = foundPart.value?.codePart ?: ""
        val isMatch = predictedCode == actualCode
        resultStatus.value = if (isMatch) "OK" else "NG"
        showUploadButton.value = true
    }

    // Di dalam class RingSynchronizerViewModel
    val isUploading = mutableStateOf(false) // 🔥 Tambahkan ini

    fun uploadResult() {
        // Cegah upload jika sedang berlangsung
        if (isUploading.value) return // 🔥 Tambahkan pengecekan ini

        isUploading.value = true // 🔥 Set ke true saat mulai upload

        val scan = scanResult.value ?: return
        val part = foundPart.value ?: return
        val result = resultStatus.value ?: return
        val photoFile = capturedPhotoFile.value ?: return

        val multipart = MultipartBody.Builder().setType(MultipartBody.FORM)
            .addFormDataPart("Id_Comparison", part.idComparison.toString())
            .addFormDataPart("Id_Tractor", part.idTractor.toString())
            .addFormDataPart("Id_Part", part.idPart.toString())
            .addFormDataPart("No_Tractor_Record", scan.sequenceNo)
            .addFormDataPart("Result_Record", result)

        photoFile.let {
            val fileBody = RequestBody.create("image/jpeg".toMediaType(), it)
            multipart.addFormDataPart("Photo_Ng_Path", it.name, fileBody)
        }

        val requestBody = multipart.build()

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val request = Request.Builder()
                    .url("$apiUrl/ring-synchronizer/save") // Ganti dengan endpoint API kamu
                    .post(requestBody)
                    .build()
                val response = client.newCall(request).execute()
                val json = JSONObject(response.body?.string())
                if (json.getBoolean("success")) {
                    withContext(Dispatchers.Main) {
                        saveSuccess.value = true
                        isUploading.value = false // 🔥 Reset status upload saat berhasil
                    }
                } else {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(context, "Upload gagal: ${json.optString("message", "Unknown error")}", Toast.LENGTH_LONG).show()
                        isUploading.value = false // 🔥 Reset status upload saat gagal
                    }
                }
            } catch (e: Exception) {
                Log.e("Upload", "Gagal", e)
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "Gagal mengunggah: ${e.message}", Toast.LENGTH_LONG).show()
                    isUploading.value = false // 🔥 Reset status upload saat error
                }
            }
        }
    }

    override fun onCleared() {
        tflite.close()
        super.onCleared()
    }

    fun setResult(result: String, photoFile: File) {
        Log.d("VM_DEBUG", "setResult called with result: $result, file: ${photoFile.name}") // 🔍 Log pemanggilan fungsi
        resultStatus.value = result
        capturedPhotoFile.value = photoFile
        showUploadButton.value = true
        Log.d("VM_DEBUG", "showUploadButton set to: ${showUploadButton.value}") // 🔍 Log perubahan state
    }

    // 🔥 Fungsi untuk mereset setelah validasi gagal (opsional)
    fun resetAfterValidationError() {
        showCaptureButton.value = false
        resultStatus.value = null
        capturedPhotoFile.value = null
        showUploadButton.value = false
        saveSuccess.value = null
    }
}