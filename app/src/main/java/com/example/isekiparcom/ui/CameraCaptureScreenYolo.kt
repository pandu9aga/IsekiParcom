package com.example.isekiparcom.ui

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Matrix
import android.graphics.RectF
import android.util.Log
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Canvas
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onSizeChanged
import androidx.core.content.ContextCompat
import com.example.isekiparcom.viewmodel.BearingShaftViewModel
import kotlinx.coroutines.delay
import java.io.File
import java.io.FileOutputStream

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CameraCaptureScreenYolo(
    viewModel: BearingShaftViewModel,
    onPhotoCaptured: (File) -> Unit,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val activity = context as androidx.activity.ComponentActivity

    val imageCapture = remember { ImageCapture.Builder().build() }
    val imageAnalysis = remember {
        ImageAnalysis.Builder()
            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
            .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888)
            .build()
    }
    val previewView = remember { PreviewView(context) }

    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA)
                    == PackageManager.PERMISSION_GRANTED
        )
    }
    var isFlashlightOn by remember { mutableStateOf(false) }
    var camera by remember { mutableStateOf<androidx.camera.core.Camera?>(null) }
    var zoomScale by remember { mutableStateOf(1f) }
    var isProcessing by remember { mutableStateOf(false) }

    var previewWidth by remember { mutableStateOf(1f) }
    var previewHeight by remember { mutableStateOf(1f) }

    // 🔥 Reset state saat komponen muncul
    LaunchedEffect(Unit) {
        viewModel.resetBearingDetectionState()
    }

    // Minta izin kamera
    LaunchedEffect(Unit) {
        if (!hasCameraPermission) {
            activity.requestPermissions(arrayOf(Manifest.permission.CAMERA), 1001)
            delay(300)
            hasCameraPermission =
                ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                        PackageManager.PERMISSION_GRANTED
        }
    }

    // Inisialisasi CameraX dengan ImageAnalysis untuk deteksi real-time
    LaunchedEffect(hasCameraPermission) {
        if (!hasCameraPermission) return@LaunchedEffect
        try {
            val cameraProvider = ProcessCameraProvider.getInstance(context).get()
            val preview = Preview.Builder().build().also {
                it.setSurfaceProvider(previewView.surfaceProvider)
            }

            // Setup ImageAnalysis untuk deteksi
            imageAnalysis.setAnalyzer(ContextCompat.getMainExecutor(context)) { imageProxy ->
                if (!isProcessing) {
                    isProcessing = true
                    try {
                        val bitmap = imageProxyToBitmap(imageProxy)
                        if (bitmap != null) {
                            viewModel.processFrameForPreview(bitmap)
                        }
                    } catch (e: Exception) {
                        Log.e("CameraYolo", "Error processing frame", e)
                    } finally {
                        isProcessing = false
                    }
                }
                imageProxy.close()
            }

            cameraProvider.unbindAll()
            camera = cameraProvider.bindToLifecycle(
                lifecycleOwner,
                CameraSelector.DEFAULT_BACK_CAMERA,
                preview,
                imageCapture,
                imageAnalysis
            )

            val cameraInfo = camera?.cameraInfo
            if (cameraInfo?.hasFlashUnit() == true) {
                camera?.cameraControl?.enableTorch(false)
                isFlashlightOn = false
            }

            val zoomState = camera?.cameraInfo?.zoomState?.value
            if (zoomState != null) {
                zoomScale = zoomState.linearZoom
            }
        } catch (e: Exception) {
            Log.e("CameraX", "Gagal bind kamera", e)
        }
    }

    fun toggleFlashlight() {
        val cameraInfo = camera?.cameraInfo
        if (cameraInfo?.hasFlashUnit() == true) {
            isFlashlightOn = !isFlashlightOn
            camera?.cameraControl?.enableTorch(isFlashlightOn)
        }
    }

    fun autoFocus(tapX: Float, tapY: Float) {
        camera?.cameraControl?.let { control ->
            val factory = previewView.meteringPointFactory
            val point = factory.createPoint(tapX, tapY)
            val action = FocusMeteringAction.Builder(point, FocusMeteringAction.FLAG_AF).build()
            control.startFocusAndMetering(action)
        }
    }

    fun mapRectToPreview(
        rect: RectF,
        srcWidth: Int,
        srcHeight: Int,
        dstWidth: Float,
        dstHeight: Float
    ): RectF {
        val scale = maxOf(
            dstWidth / srcWidth,
            dstHeight / srcHeight
        )

        val dx = (dstWidth - srcWidth * scale) / 2f
        val dy = (dstHeight - srcHeight * scale) / 2f

        return RectF(
            rect.left * scale + dx,
            rect.top * scale + dy,
            rect.right * scale + dx,
            rect.bottom * scale + dy
        )
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
                        "Deteksi Ball Bearing",
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
                            contentDescription = "Flash",
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
            // Preview Kamera
            AndroidView(
                factory = { previewView },
                modifier = Modifier
                    .fillMaxSize()
                    .onSizeChanged {
                        previewWidth = it.width.toFloat()
                        previewHeight = it.height.toFloat()
                    }
            )

            // Overlay untuk menggambar bounding boxes
            val detections by viewModel.lastLiveDetections

            if (detections.isNotEmpty()) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    detections.forEachIndexed { index, detection ->
                        // Draw bounding box dengan warna berbeda per deteksi
                        val boxColor = when (index % 3) {
                            0 -> Color.Red
                            1 -> Color.Red
                            else -> Color.Red
                        }

                        val mapped = mapRectToPreview(
                            detection.box,
                            detection.sourceWidth,
                            detection.sourceHeight,
                            previewWidth,
                            previewHeight
                        )

                        drawRect(
                            color = boxColor,
                            topLeft = Offset(mapped.left, mapped.top),
                            size = Size(mapped.width(), mapped.height()),
                            style = Stroke(width = 8f)
                        )
                    }
                }
            }

            // Counter ball di pojok kanan atas
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(16.dp)
                    .background(
                        if (detections.isNotEmpty()) Color(0xFF4CAF50).copy(alpha = 0.9f)
                        else Color.Black.copy(alpha = 0.7f),
                        MaterialTheme.shapes.medium
                    )
                    .padding(16.dp)
            ) {
                Text(
                    text = "🔴 Ball: ${detections.size}",
                    color = Color.White,
                    style = MaterialTheme.typography.titleLarge
                )
            }

            // Slider Zoom
            val cameraInfo = camera?.cameraInfo
            val zoomState = cameraInfo?.zoomState?.value

            if (zoomState != null) {
                LaunchedEffect(zoomState.linearZoom) {
                    zoomScale = zoomState.linearZoom
                }

                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .width(100.dp)
                        .align(Alignment.CenterEnd)
                        .padding(end = 24.dp, top = 80.dp, bottom = 100.dp)
                ) {
                    Column(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Surface(
                            color = Color.Black.copy(alpha = 0.7f),
                            shape = MaterialTheme.shapes.medium
                        ) {
                            Text(
                                text = "%.1fx".format(zoomState.zoomRatio),
                                color = Color.White,
                                style = MaterialTheme.typography.titleLarge,
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                            )
                        }

                        Spacer(modifier = Modifier.height(24.dp))

                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .wrapContentWidth(),
                            contentAlignment = Alignment.Center
                        ) {
                            Slider(
                                value = zoomScale,
                                onValueChange = { newLinearZoom ->
                                    camera?.cameraControl?.setLinearZoom(newLinearZoom)
                                },
                                valueRange = 0f..1f,
                                modifier = Modifier
                                    .graphicsLayer {
                                        rotationZ = 270f
                                        transformOrigin = androidx.compose.ui.graphics.TransformOrigin.Center
                                    }
                                    .width(500.dp)
                                    .height(56.dp),
                                colors = SliderDefaults.colors(
                                    thumbColor = Color.White,
                                    activeTrackColor = Color.White,
                                    inactiveTrackColor = Color.White.copy(alpha = 0.3f)
                                )
                            )
                        }
                    }
                }
            }

            // Tombol ambil foto
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .padding(24.dp)
            ) {
                Button(
                    onClick = {
                        // 🔥 RESET STATE SEBELUM PROSES FOTO BARU DIMULAI
                        viewModel.resetBearingDetectionState()

                        viewModel.frameGeneration++
                        viewModel.isCaptureLocked.value = true

                        val frozenBitmap = viewModel.lastLiveBitmap.value?.copy(Bitmap.Config.ARGB_8888, false)
                        val frozenDetections = viewModel.lastLiveDetections.value.toList()

                        if (frozenBitmap == null || frozenDetections.isEmpty()) {
                            Log.w("CAPTURE", "Bitmap or detections empty")
                            viewModel.isCaptureLocked.value = false
                            return@Button
                        }

                        val resultBitmap = viewModel.yoloDetector.drawDetections(frozenBitmap, frozenDetections)
                        val count = frozenDetections.size

                        val file = File(context.cacheDir, "bearing_${System.currentTimeMillis()}.jpg")
                        val outputStream = file.outputStream()
                        resultBitmap.compress(Bitmap.CompressFormat.JPEG, 95, outputStream)
                        outputStream.flush()
                        outputStream.close()

                        viewModel.capturedBearingPhotoFile.value = file
                        viewModel.bearingPhotoWithBoxes.value = resultBitmap
                        viewModel.ballCount.value = count

                        viewModel.processFinalBearingResult(count)

                        // 🔥 TIDAK ADA RESET DI SINI, KARENA KITA INGIN POPUP DLL TETAP ADA SETELAH AMBIL FOTO

                        onBack() // Tutup kamera
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(64.dp),
                    enabled = !viewModel.isCaptureLocked.value,
                    shape = MaterialTheme.shapes.large,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary
                    )
                ) {
                    Text(
                        text = "📸 AMBIL FOTO (${detections.size} Ball)",
                        color = Color.White,
                        style = MaterialTheme.typography.titleLarge
                    )
                }
            }
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            viewModel.resetBearingDetectionState()
            Log.d("CameraCaptureYolo", "State reset saat komponen dilepas.")

            try {
                val cameraInfo = camera?.cameraInfo
                if (cameraInfo?.hasFlashUnit() == true && isFlashlightOn) {
                    camera?.cameraControl?.enableTorch(false)
                }
                val provider = ProcessCameraProvider.getInstance(context).get()
                provider.unbindAll()
            } catch (e: Exception) {
                Log.e("CameraX", "Error cleanup", e)
            }
        }
    }
}

// Helper function untuk convert ImageProxy ke Bitmap (FIXED)
private fun imageProxyToBitmap(image: ImageProxy): Bitmap? {
    return try {
        // Untuk format RGBA_8888
        val plane = image.planes[0]
        val buffer = plane.buffer

        val pixelStride = plane.pixelStride
        val rowStride = plane.rowStride
        val rowPadding = rowStride - pixelStride * image.width

        val bitmap = Bitmap.createBitmap(
            image.width + rowPadding / pixelStride,
            image.height,
            Bitmap.Config.ARGB_8888
        )

        bitmap.copyPixelsFromBuffer(buffer)

        // Crop jika ada padding
        val croppedBitmap = if (rowPadding != 0) {
            Bitmap.createBitmap(bitmap, 0, 0, image.width, image.height)
        } else {
            bitmap
        }

        // Rotate sesuai orientasi kamera
        val matrix = Matrix()
        matrix.postRotate(image.imageInfo.rotationDegrees.toFloat())

        val rotated = Bitmap.createBitmap(
            croppedBitmap, 0, 0,
            croppedBitmap.width, croppedBitmap.height,
            matrix, true
        )

        if (croppedBitmap != bitmap) bitmap.recycle()
        if (rotated != croppedBitmap) croppedBitmap.recycle()

        rotated
    } catch (e: Exception) {
        Log.e("CameraYolo", "Error converting ImageProxy", e)
        null
    }
}