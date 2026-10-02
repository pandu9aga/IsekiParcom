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

class ShaftGcViewModel(private val context: Context) : ViewModel() {
    private val apiUrl = "http://192.168.173.201/iseki_parcom/public/api"
    private val client = OkHttpClient()
    
    // Tflite model setup - ensure assets/shaft_gc exists
    private val tflite = try {
        TfliteInference(context, "shaft_gc/model_unquant.tflite", "shaft_gc/labels.txt")
    } catch (e: Exception) {
        Log.e("VM", "Failed to load tflite model", e)
        null
    }

    val currentQr = mutableStateOf<String?>(null)
    val expectedText = mutableStateOf<String?>(null)
    val validationMessage = mutableStateOf<String?>(null)
    val showCaptureButton = mutableStateOf(false)
    val capturedPhotoFile = mutableStateOf<File?>(null)
    val resultStatus = mutableStateOf<String?>(null)
    val showUploadButton = mutableStateOf(false)
    val saveSuccess = mutableStateOf<Boolean?>(null)

    val showResultPopup = mutableStateOf(false)
    val popupFinished = mutableStateOf(false)
    val isUploading = mutableStateOf(false)

    fun handleQrScanned(rawQr: String) {
        currentQr.value = rawQr
        resetScanStates()
        
        viewModelScope.launch(Dispatchers.IO) {
            val json = JSONObject().apply {
                put("qr_code", rawQr)
            }
            val mediaType = "application/json; charset=utf-8".toMediaType()
            val body = RequestBody.create(mediaType, json.toString())
            val request = Request.Builder()
                .url("$apiUrl/shaft-gc/plan")
                .post(body)
                .build()

            try {
                val response = client.newCall(request).execute()
                val respJson = JSONObject(response.body?.string())
                val status = respJson.optString("status")
                val message = respJson.optString("message")

                withContext(Dispatchers.Main) {
                    validationMessage.value = message
                    if (status == "success" && respJson.optBoolean("required")) {
                        expectedText.value = respJson.optString("expected_text")
                        showCaptureButton.value = true
                    } else {
                        showCaptureButton.value = false
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    validationMessage.value = "API Error: ${e.message}"
                    showCaptureButton.value = false
                }
            }
        }
    }

    fun processImage(bitmap: Bitmap) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val predictedCode = tflite?.run(bitmap) ?: "UNKNOWN"
                val expected = expectedText.value ?: ""
                val result = if (predictedCode.trim() == expected.trim()) "OK" else "NG"

                val compressedFile = compressBitmap(bitmap, 500)

                withContext(Dispatchers.Main) {
                    resultStatus.value = result
                    capturedPhotoFile.value = compressedFile
                    showUploadButton.value = true

                    showResultPopup.value = true
                    popupFinished.value = false

                    viewModelScope.launch {
                        delay(2000)
                        showResultPopup.value = false
                        popupFinished.value = true
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    resultStatus.value = "ERROR"
                    showUploadButton.value = false
                }
            }
        }
    }

    private fun compressBitmap(bitmap: Bitmap, maxSizeKB: Int = 500): File {
        val file = File(context.cacheDir, "shaft_gc_${System.currentTimeMillis()}.jpg")
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

        val qr = currentQr.value ?: run { isUploading.value = false; return }
        val expected = expectedText.value ?: run { isUploading.value = false; return }
        val result = resultStatus.value ?: run { isUploading.value = false; return }
        val photoFile = capturedPhotoFile.value ?: run { isUploading.value = false; return }

        val multipart = MultipartBody.Builder().setType(MultipartBody.FORM)
            .addFormDataPart("qr_code", qr)
            .addFormDataPart("result_status", result)
            .addFormDataPart("expected_text", expected)
            .addFormDataPart("prediction_text", result) // Or actual prediction

        val fileBody = RequestBody.create("image/jpeg".toMediaType(), photoFile)
        multipart.addFormDataPart("photo", photoFile.name, fileBody)

        val requestBody = multipart.build()

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val request = Request.Builder()
                    .url("$apiUrl/shaft-gc/save")
                    .post(requestBody)
                    .build()
                val response = client.newCall(request).execute()
                val json = JSONObject(response.body?.string())
                if (json.optString("status") == "success") {
                    withContext(Dispatchers.Main) {
                        saveSuccess.value = true
                        isUploading.value = false
                    }
                } else {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(context, "Upload gagal", Toast.LENGTH_LONG).show()
                        isUploading.value = false
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "Gagal mengunggah: ${e.message}", Toast.LENGTH_LONG).show()
                    isUploading.value = false
                }
            }
        }
    }

    private fun resetScanStates() {
        expectedText.value = null
        validationMessage.value = null
        showCaptureButton.value = false
        resultStatus.value = null
        capturedPhotoFile.value = null
        showUploadButton.value = false
        saveSuccess.value = null
        isUploading.value = false
    }

    override fun onCleared() {
        tflite?.close()
        super.onCleared()
    }
}
