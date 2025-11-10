package com.example.isekiparcom.viewmodel

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import android.widget.Toast
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
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
    private val tflite = TfliteInference(context)

    val scanResult = mutableStateOf<ScanResult?>(null)
    val foundPart = mutableStateOf<PartData?>(null)
    val validationMessage = mutableStateOf<String?>(null)
    val showCaptureButton = mutableStateOf(false)
    val capturedPhotoFile = mutableStateOf<File?>(null)
    val resultStatus = mutableStateOf<String?>(null)
    val showUploadButton = mutableStateOf(false)

    fun handleQrScanned(rawQr: String) {
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

    private fun fetchPartByTractorType(tractorType: String) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val url = "$apiUrl/ring-synchronizer/part-by-tractor/${tractorType}"
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

                // ✅ Perbaikan: gunakan MediaType.get() + RequestBody.create()
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

    fun runTensorFlowLite(bitmap: Bitmap) {
        val predictedCode = tflite.run(bitmap)
        val actualCode = foundPart.value?.codePart ?: ""
        val isMatch = predictedCode == actualCode
        resultStatus.value = if (isMatch) "OK" else "NG"
        showUploadButton.value = true
    }

    fun uploadResult() {
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
            .addFormDataPart(
                "Photo_Ng_Path",
                photoFile.name,
                photoFile.asRequestBody("image/jpeg".toMediaType())
            )
            .build()

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val request = Request.Builder()
                    .url("$apiUrl/ring-synchronizer/save")
                    .post(multipart)
                    .build()
                val response = client.newCall(request).execute()
                val json = JSONObject(response.body?.string())
                if (json.getBoolean("success")) {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(context, "Berhasil disimpan!", Toast.LENGTH_SHORT).show()
                    }
                }
            } catch (e: Exception) {
                Log.e("Upload", "Gagal", e)
            }
        }
    }

    override fun onCleared() {
        tflite.close()
        super.onCleared()
    }
}