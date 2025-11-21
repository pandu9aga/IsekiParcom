package com.example.isekiparcom.ui

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.example.isekiparcom.utils.TfliteInference
import kotlinx.coroutines.*
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CameraCaptureScreen(
    onPredictionResult: (String, File) -> Unit,
    expectedCodePart: String,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val activity = context as ComponentActivity

    val coroutineScope = rememberCoroutineScope()
    val imageCapture = remember { ImageCapture.Builder().build() }
    val previewView = remember { PreviewView(context) }

    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA)
                    == PackageManager.PERMISSION_GRANTED
        )
    }

    var isLoading by remember { mutableStateOf(false) }

    // 🔹 Minta izin kamera
    LaunchedEffect(Unit) {
        if (!hasCameraPermission) {
            activity.requestPermissions(arrayOf(Manifest.permission.CAMERA), 1001)
            delay(300)
            hasCameraPermission =
                ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                        PackageManager.PERMISSION_GRANTED
        }
    }

    // 🔹 Inisialisasi CameraX (stable binding)
    LaunchedEffect(hasCameraPermission) {
        if (!hasCameraPermission) return@LaunchedEffect
        try {
            val cameraProvider = ProcessCameraProvider.getInstance(context).get()
            val preview = Preview.Builder().build().also {
                it.setSurfaceProvider(previewView.surfaceProvider)
            }

            cameraProvider.unbindAll()
            cameraProvider.bindToLifecycle(
                lifecycleOwner,
                CameraSelector.DEFAULT_BACK_CAMERA,
                preview,
                imageCapture
            )
            Log.d("CameraX", "✅ Kamera berhasil dibind ke lifecycle")
        } catch (e: Exception) {
            Log.e("CameraX", "❌ Gagal bind kamera", e)
        }
    }

    if (!hasCameraPermission) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Text("Izin kamera belum diberikan.")
        }
        return
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Ambil Foto Part") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Kembali")
                    }
                }
            )
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // 🔹 Preview Kamera (selalu paling bawah)
            AndroidView(
                factory = { previewView },
                modifier = Modifier.fillMaxSize()
            )

            // 🔹 Overlay (lapisan di atas preview)
            Box(
                modifier = Modifier
                    .matchParentSize(), // supaya seluruh overlay transparan
                contentAlignment = Alignment.BottomCenter
            ) {
                if (isLoading) {
                    Box(
                        modifier = Modifier
                            .matchParentSize()
                            .background(Color(0x88000000)),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            CircularProgressIndicator(color = Color.White)
                            Text("Memproses foto...", color = Color.White)
                        }
                    }
                }

                // Tombol ambil foto
                Button(
                    onClick = {
                        val file = File(context.cacheDir, "photo_${System.currentTimeMillis()}.jpg")
                        val options = ImageCapture.OutputFileOptions.Builder(file).build()

                        imageCapture.takePicture(
                            options,
                            ContextCompat.getMainExecutor(context),
                            object : ImageCapture.OnImageSavedCallback {
                                override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                                    Log.d("CameraCapture", "✅ Foto tersimpan di: ${file.absolutePath}")
                                    val bmp = BitmapFactory.decodeFile(file.absolutePath)

                                    coroutineScope.launch {
                                        isLoading = true
                                        try {
                                            val tflite = TfliteInference(context, "ring_synchronizer/model_unquant.tflite", "ring_synchronizer/labels.txt")
                                            val result = processImageWithTflite(bmp, tflite, expectedCodePart)
                                            val compressed = compressBitmap(context, bmp, 500)

                                            withContext(Dispatchers.Main) {
                                                onPredictionResult(result, compressed)
                                                onBack()
                                            }
                                        } catch (e: Exception) {
                                            Log.e("CameraCapture", "❌ Error memproses foto", e)
                                            withContext(Dispatchers.Main) { onBack() }
                                        } finally {
                                            isLoading = false
                                        }
                                    }
                                }

                                override fun onError(exception: ImageCaptureException) {
                                    Log.e("CameraX", "❌ Gagal ambil foto", exception)
                                    isLoading = false
                                    onBack()
                                }
                            }
                        )
                    },
                    modifier = Modifier
                        .padding(24.dp)
                        .height(60.dp)
                        .fillMaxWidth()
                ) {
                    Text("AMBIL FOTO", color = Color.White)
                }
            }
        }
    }

    // 🔹 Cleanup kamera saat keluar composable
    DisposableEffect(Unit) {
        onDispose {
            try {
                val provider = ProcessCameraProvider.getInstance(context).get()
                provider.unbindAll()
                Log.d("CameraX", "🧹 Kamera dibersihkan di onDispose")
            } catch (e: Exception) {
                Log.e("CameraX", "❌ Gagal unbind saat dispose", e)
            }
        }
    }
}

/* Utilitas */
private suspend fun processImageWithTflite(
    bitmap: Bitmap,
    tflite: TfliteInference,
    expected: String
): String = withContext(Dispatchers.IO) {
    val predicted = tflite.run(bitmap)
    Log.d("CameraCapture", "Expected: $expected, Predicted: $predicted")
    if (predicted.trim() == expected.trim()) "OK" else "NG"
}

private fun compressBitmap(context: android.content.Context, bitmap: Bitmap, maxSizeKB: Int): File {
    val file = File(context.cacheDir, "compressed_${System.currentTimeMillis()}.jpg")
    var quality = 90
    do {
        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, quality, it) }
        quality -= 10
    } while (file.length() > maxSizeKB * 1024L && quality > 10)
    Log.d("CameraCapture", "Compressed file size: ${file.length() / 1024} KB")
    return file
}
