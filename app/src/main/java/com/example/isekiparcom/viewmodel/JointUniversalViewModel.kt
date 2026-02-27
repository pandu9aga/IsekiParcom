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

data class JointUniversalScanResult(
    val sequenceNo: String,
    val productionDate: String
)

class JointUniversalViewModel(private val context: Context) : ViewModel() {
    private val apiUrl = "http://192.168.173.207/iseki_parcom/public/api/testing/joint-universal"
    private val client = OkHttpClient()
    private val tflite = TfliteInference(context, "joint_universal/model_unquant.tflite", "joint_universal/labels.txt")

    val scanResult = mutableStateOf<JointUniversalScanResult?>(null)
    
    // Properties to be received from validation API
    val modelNamePlan = mutableStateOf<String?>(null)
    val textRecord = mutableStateOf<String?>(null)

    val validationMessage = mutableStateOf<String?>(null)
    val showCaptureButton = mutableStateOf(false)
    val capturedPhotoFile = mutableStateOf<File?>(null)
    
    val predictRecord = mutableStateOf<String?>(null)
    val resultStatus = mutableStateOf<String?>(null)
    
    val showUploadButton = mutableStateOf(false)
    val saveSuccess = mutableStateOf<Boolean?>(null)

    val showResultPopup = mutableStateOf(false)
    val popupFinished = mutableStateOf(false)

    val isUploading = mutableStateOf(false)

    fun processImage(bitmap: Bitmap) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                // Get prediction and uppercase it as requested
                val predictedCodeRaw = tflite.run(bitmap).trim()
                val predictedCodeUpper = predictedCodeRaw.uppercase()
                
                val expectedTextRecord = textRecord.value ?: ""
                
                // Compare Predict_Record (UPPERCASE) with Text_Record
                val result = if (predictedCodeUpper == expectedTextRecord.trim()) "OK" else "NG"

                val compressedFile = compressBitmap(bitmap, 500)

                withContext(Dispatchers.Main) {
                    predictRecord.value = predictedCodeUpper
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
            // Joint Universal QR expects 2 or more parts: sequence;production_date (from current web implementation logic)
            if (parts.size < 2) throw Exception("Format QR salah, harus memiliki Minimal Sequence No dan Production Date dipisah ';'")
            
            val sequenceNo = parts[0].trim()
            val productionDate = parts[1].trim()
            
            val newScanResult = JointUniversalScanResult(sequenceNo, productionDate)

            resetScanStates()
            scanResult.value = newScanResult
            
            // Auto validate immediately
            validateRule {}

        } catch (e: Exception) {
            Log.e("QR", "Parse error", e)
            validationMessage.value = "Format QR salah: ${e.message}"
        }
    }

    private fun resetScanStates() {
        modelNamePlan.value = null
        textRecord.value = null
        validationMessage.value = null
        showCaptureButton.value = false
        resultStatus.value = null
        predictRecord.value = null
        capturedPhotoFile.value = null
        showUploadButton.value = false
        saveSuccess.value = null
        isUploading.value = false
    }

    fun validateRule(onComplete: (Boolean) -> Unit) {
        val result = scanResult.value ?: run { onComplete(false); return }

        CoroutineScope(Dispatchers.IO).launch {
            try {
                // Create Request JSON matching Web API implementation for Joint Universal
                val json = JSONObject().apply {
                    put("sequence_no", result.sequenceNo)
                    put("production_date", result.productionDate)
                }

                val mediaType = "application/json; charset=utf-8".toMediaType()
                val body = RequestBody.create(mediaType, json.toString())

                val request = Request.Builder()
                    .url("$apiUrl/validate")
                    .post(body)
                    .build()
                    
                val response = client.newCall(request).execute()
                val respBodyText = response.body?.string()
                
                Log.d("API_VAL", "Response: $respBodyText")
                
                val respJson = JSONObject(respBodyText ?: "{}")
                val success = respJson.optBoolean("success", false)
                val message = respJson.optString("message", "Terjadi kesalahan")

                withContext(Dispatchers.Main) {
                    validationMessage.value = message
                    if (success) {
                        // Web API returns model_name and text_record on success
                        val dataObj = respJson.optJSONObject("data")
                        if (dataObj != null) {
                            modelNamePlan.value = dataObj.optString("model_name")
                            textRecord.value = dataObj.optString("text_record")
                        }
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
        val modelName = modelNamePlan.value ?: ""
        val txtRecord = textRecord.value ?: ""
        val predRecord = predictRecord.value ?: ""
        val result = resultStatus.value ?: run { isUploading.value = false; return }
        val photoFile = capturedPhotoFile.value ?: run { isUploading.value = false; return }

        // Match the web insert() logic fields
        val multipart = MultipartBody.Builder().setType(MultipartBody.FORM)
            .addFormDataPart("No_Tractor_Record", scan.sequenceNo)
            .addFormDataPart("Production_Date_Record", scan.productionDate)
            .addFormDataPart("Model_Name_Plan", modelName)
            .addFormDataPart("Text_Record", txtRecord)
            .addFormDataPart("Predict_Record", predRecord)
            .addFormDataPart("Result_Record", result)
            .addFormDataPart("Id_Comparison", "4") // Hardcoded for Joint Universal

        photoFile.let {
            val fileBody = RequestBody.create("image/jpeg".toMediaType(), it)
            multipart.addFormDataPart("Photo_Ng_Path", it.name, fileBody)
        }

        val requestBody = multipart.build()

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val request = Request.Builder()
                    .url("$apiUrl/save")
                    .post(requestBody)
                    .build()
                val response = client.newCall(request).execute()
                val respString = response.body?.string()
                
                Log.d("API_SAVE", "Response: $respString")
                val json = JSONObject(respString ?: "{}")
                
                if (json.optBoolean("success")) {
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
        resultStatus.value = result
        capturedPhotoFile.value = photoFile
        showUploadButton.value = true
    }

    fun resetAfterValidationError() {
        showCaptureButton.value = false
        resultStatus.value = null
        predictRecord.value = null
        capturedPhotoFile.value = null
        showUploadButton.value = false
        saveSuccess.value = null
    }
}
