package com.example.isekiparcom.ui

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import android.util.Log
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.FlashOff
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import kotlinx.coroutines.*
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CameraCaptureScreen(
    onPhotoCaptured: (File) -> Unit,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val activity = context as androidx.activity.ComponentActivity

    val coroutineScope = rememberCoroutineScope()
    val imageCapture = remember { ImageCapture.Builder().build() }
    val previewView = remember { PreviewView(context) }

    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA)
                    == PackageManager.PERMISSION_GRANTED
        )
    }
    var isFlashlightOn by remember { mutableStateOf(false) }
    var camera by remember { mutableStateOf<androidx.camera.core.Camera?>(null) }
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

    // 🔹 Inisialisasi CameraX
    LaunchedEffect(hasCameraPermission) {
        if (!hasCameraPermission) return@LaunchedEffect
        try {
            val cameraProvider = ProcessCameraProvider.getInstance(context).get()
            val preview = Preview.Builder().build().also {
                it.setSurfaceProvider(previewView.surfaceProvider)
            }

            cameraProvider.unbindAll()
            // 🔥 Simpan instance camera ke state
            camera = cameraProvider.bindToLifecycle(
                lifecycleOwner,
                CameraSelector.DEFAULT_BACK_CAMERA,
                preview,
                imageCapture
            )
            Log.d("CameraX", "✅ Kamera berhasil dibind ke lifecycle")

            // 🔥 Set flashlight default OFF saat kamera dibuka
            val cameraInfo = camera?.cameraInfo
            if (cameraInfo?.hasFlashUnit() == true) {
                camera?.cameraControl?.enableTorch(false)
                isFlashlightOn = false
                Log.d("CameraX", "Flashlight dimatikan saat kamera dibuka.")
            }
        } catch (e: Exception) {
            Log.e("CameraX", "❌ Gagal bind kamera", e)
        }
    }

    // 🔥 Fungsi untuk toggle flashlight
    fun toggleFlashlight() {
        val cameraInfo = camera?.cameraInfo
        if (cameraInfo == null) {
            Log.w("CameraX", "Kamera belum siap untuk toggle flashlight.")
            return
        }

        if (!cameraInfo.hasFlashUnit()) { // Cek apakah flash tersedia dari cameraInfo
            Log.w("CameraX", "Flashlight tidak tersedia di kamera ini.")
            return
        }

        // Toggle state
        isFlashlightOn = !isFlashlightOn
        // Kirim perintah ke kamera
        camera?.cameraControl?.enableTorch(isFlashlightOn)
        Log.d("CameraX", "Flashlight toggled to: $isFlashlightOn")
    }

    // 🔥 Fungsi untuk autofocus saat tap layar
    fun autoFocus(tapX: Float, tapY: Float) {
        camera?.cameraControl?.let { control ->
            val factory = previewView.meteringPointFactory
            val point = factory.createPoint(tapX, tapY)
            val action = FocusMeteringAction.Builder(point, FocusMeteringAction.FLAG_AF).build()

            control.startFocusAndMetering(action)
                .addListener(
                    { Log.d("CameraX", "AutoFocus requested at ($tapX, $tapY)") },
                    ContextCompat.getMainExecutor(context)
                )
        } ?: run {
            Log.w("CameraX", "Kamera belum siap untuk autofocus.")
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
                title = {
                    Text(
                        "Ambil Foto",
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Kembali")
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary
                ),
                actions = {
                    IconButton(onClick = { toggleFlashlight() }) {
                        Icon(
                            imageVector = if (isFlashlightOn) Icons.Default.FlashOn else Icons.Default.FlashOff,
                            contentDescription = if (isFlashlightOn) "Matikan Flash" else "Nyalakan Flash",
                            tint = if (isFlashlightOn) Color.Yellow else Color.White
                        )
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
                modifier = Modifier
                    .fillMaxSize()
                    // 🔥 Tambahkan tap gesture detector
                    .pointerInput(Unit) {
                        detectTapGestures(
                            onTap = { offset ->
                                autoFocus(offset.x, offset.y) // Panggil fungsi autofocus
                            }
                        )
                    }
            )

            // 🔹 Overlay (lapisan di atas preview)
            Box(
                modifier = Modifier
                    .matchParentSize(),
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
                                    onPhotoCaptured(file)
                                    onBack()
                                }

                                override fun onError(exception: ImageCaptureException) {
                                    Log.e("CameraX", "❌ Gagal ambil foto", exception)
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
                val cameraInfo = camera?.cameraInfo
                // Cek apakah flash tersedia dan hidup
                if (cameraInfo?.hasFlashUnit() == true && isFlashlightOn) {
                    camera?.cameraControl?.enableTorch(false)
                    Log.d("CameraX", "Flashlight dimatikan saat kamera ditutup.")
                }
                val provider = ProcessCameraProvider.getInstance(context).get()
                provider.unbindAll()
                Log.d("CameraX", "🧹 Kamera dibersihkan di onDispose")
            } catch (e: Exception) {
                Log.e("CameraX", "❌ Gagal unbind saat dispose", e)
            }
        }
    }
}