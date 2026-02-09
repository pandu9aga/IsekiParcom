package com.example.isekiparcom.ui

import android.app.Activity
import android.content.Intent
import android.graphics.BitmapFactory
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateColor
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import com.example.isekiparcom.QrScannerActivity
import com.example.isekiparcom.viewmodel.BearingMetalViewModel
import com.example.isekiparcom.viewmodel.BearingMetalViewModelFactory

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BearingMetalScreen(navController: NavHostController) {
    val context = LocalContext.current
    val viewModel: BearingMetalViewModel = viewModel(
        factory = BearingMetalViewModelFactory(context)
    )

    var showCameraPart by remember { mutableStateOf(false) }
    var showCameraBearing by remember { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }

    // Validasi setelah QR discan
    LaunchedEffect(viewModel.scanResult.value) {
        if (viewModel.scanResult.value != null && viewModel.validationMessage.value == null) {
            viewModel.validateRule()
        }
    }

    // Navigasi setelah simpan berhasil
    LaunchedEffect(viewModel.saveSuccess.value) {
        if (viewModel.saveSuccess.value == true) {
            Toast.makeText(context, "Berhasil disimpan!", Toast.LENGTH_SHORT).show()
            navController.navigate("record_list_bearing_koyo") {
                popUpTo("bearing_metal") { inclusive = true }
            }
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Scaffold(
            topBar = {
                CenterAlignedTopAppBar(
                    title = {
                        Text(
                            "Bearing Metal",
                            color = MaterialTheme.colorScheme.onPrimary
                        )
                    },
                    navigationIcon = {
                        IconButton(onClick = { navController.popBackStack() }) {
                            Icon(
                                imageVector = Icons.Default.ArrowBack,
                                contentDescription = "Kembali"
                            )
                        }
                    },
                    colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                        containerColor = MaterialTheme.colorScheme.primary
                    )
                )
            },
            snackbarHost = { SnackbarHost(snackbarHostState) }
        ) { padding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(16.dp)
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.Start
            ) {
                val launcher = rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.StartActivityForResult()
                ) { result ->
                    if (result.resultCode == Activity.RESULT_OK) {
                        val qrText = result.data?.getStringExtra("qr_result") ?: return@rememberLauncherForActivityResult
                        viewModel.handleQrScanned(qrText)
                    }
                }

                Button(onClick = { launcher.launch(Intent(context, QrScannerActivity::class.java)) }) {
                    Text("Scan QR")
                }

                Spacer(Modifier.height(12.dp))

                viewModel.scanResult.value?.let {
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer
                        ),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text("Sequence No: ${it.sequenceNo}")
                            Text("Tractor Type: ${it.tractorType}")
                        }
                    }
                }

                Spacer(Modifier.height(16.dp))

                viewModel.validationMessage.value?.let { msg ->
                    val success = msg.contains("Siap melanjutkan", ignoreCase = true)
                    val badgeColor = if (success) Color(0xFF4CAF50) else Color(0xFFE53935)
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(badgeColor, MaterialTheme.shapes.medium)
                            .padding(16.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(msg, color = Color.White, fontWeight = FontWeight.Medium)
                    }
                    Spacer(Modifier.height(20.dp))
                }

                // Tombol ambil foto part
                if (viewModel.showCaptureButton.value && viewModel.partDetectionResult.value == null) {
                    Button(
                        onClick = { showCameraPart = true },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Ambil Foto Part Metal")
                    }
                }

                // Hasil deteksi part
                viewModel.partDetectionResult.value?.let { result ->
                    Spacer(Modifier.height(16.dp))
                    val color = if (result.lowercase() == "metal bearing") Color(0xFF4CAF50) else Color(0xFFE53935)
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(100.dp)
                            .background(color, MaterialTheme.shapes.medium)
                            .padding(16.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            "Part: $result",
                            color = Color.White,
                            fontSize = 24.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    if (result.lowercase() == "metal bearing") {
                        Text("Lanjutkan ke deteksi bearing...")
                    } else {
                        Text("Part bukan Metal Bearing. Silakan ambil ulang.")
                    }
                }

                // Tombol ambil foto bearing
                if (viewModel.showSecondCaptureButton.value) {
                    Spacer(Modifier.height(16.dp))
                    Button(
                        onClick = {
                            viewModel.resetLiveBearingCameraState()
                            showCameraBearing = true
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Ambil Foto Bearing")
                    }
                }

                // Tombol ambil ulang foto part
                if (viewModel.showRetakePartButton.value) {
                    Spacer(Modifier.height(16.dp))
                    Button(
                        onClick = {
                            viewModel.resetForRetakePart()
                            showCameraPart = true
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Ambil Ulang Foto Part")
                    }
                }

                // Hasil deteksi ball
                viewModel.ballCount.value?.let { count ->
                    Spacer(Modifier.height(16.dp))
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.secondaryContainer
                        ),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            horizontalAlignment = Alignment.Start
                        ) {
                            Text(
                                text = "Jumlah Ball Terdeteksi:",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSecondaryContainer
                            )
                            Spacer(Modifier.height(8.dp))
                            Text(
                                text = "$count ball(s)",
                                style = MaterialTheme.typography.headlineMedium,
                                color = MaterialTheme.colorScheme.onSecondaryContainer,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    // Preview foto dengan boxes
                    viewModel.bearingPhotoWithBoxes.value?.let { bitmap ->
                        Spacer(Modifier.height(16.dp))
                        Card(
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Image(
                                bitmap = bitmap.asImageBitmap(),
                                contentDescription = "Bearing dengan deteksi",
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(300.dp)
                            )
                        }
                    }
                }

                // Popup OK/NG
                if (viewModel.showResultPopup.value && viewModel.ballCount.value != null) {
                    val finalResult = viewModel.finalResult.value ?: "NG"
                    val popupColor = if (finalResult == "OK") Color(0xFF4CAF50) else Color(0xFFE53935)

                    AlertDialog(
                        onDismissRequest = { },
                        confirmButton = { },
                        containerColor = popupColor,
                        text = {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(140.dp)
                                    .padding(16.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    finalResult,
                                    color = Color.White,
                                    fontSize = 69.sp,
                                    fontWeight = FontWeight.ExtraBold
                                )
                            }
                        }
                    )
                }

                // Badge besar permanen
                if (!viewModel.showResultPopup.value && viewModel.popupFinished.value && viewModel.finalResult.value != null) {
                    val final = viewModel.finalResult.value!!
                    val color = if (final == "OK") Color(0xFF43A047) else Color(0xFFE53935)

                    val infiniteTransition = rememberInfiniteTransition(label = "Blink")
                    val blinkColor by infiniteTransition.animateColor(
                        initialValue = color,
                        targetValue = if (final == "OK") Color(0xFF8BD58E) else Color(0xFFFA7E75),
                        animationSpec = infiniteRepeatable(
                            animation = tween(durationMillis = 600),
                            repeatMode = RepeatMode.Reverse
                        ),
                        label = "Blink"
                    )

                    Spacer(Modifier.height(20.dp))

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(140.dp)
                            .background(blinkColor, MaterialTheme.shapes.medium)
                            .padding(16.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            final,
                            color = Color.White,
                            fontSize = 69.sp,
                            fontWeight = FontWeight.ExtraBold
                        )
                    }

                    Spacer(Modifier.height(24.dp))

                    Button(
                        onClick = { viewModel.uploadResult() },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !viewModel.isUploading.value
                    ) {
                        if (viewModel.isUploading.value) {
                            Text("Mengunggah...")
                        } else {
                            Text("Simpan Hasil", color = Color.White)
                        }
                    }
                }
            }
        }

        // Overlay Kamera Part
        if (showCameraPart) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.95f))
            ) {
                CameraCaptureScreen(
                    onPhotoCaptured = { file ->
                        viewModel.capturedPartPhotoFile.value = file
                        val bitmap = BitmapFactory.decodeFile(file.absolutePath)
                        viewModel.processPartImage(bitmap)
                        showCameraPart = false
                    },
                    onBack = { showCameraPart = false }
                )
            }
        }

        // Overlay Kamera Bearing (YOLO)
        if (showCameraBearing) {
            LaunchedEffect(Unit) {
                viewModel.resetLiveBearingCameraState()
            }
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.95f))
            ) {
                CameraCaptureScreenYolo(
                    viewModel = viewModel,
                    onPhotoCaptured = { file ->
                        viewModel.capturedBearingPhotoFile.value = file
                        val bitmap = BitmapFactory.decodeFile(file.absolutePath)
                        viewModel.processBearingImage(bitmap)
                        showCameraBearing = false
                    },
                    onBack = { showCameraBearing = false }
                )
            }
        }
    }
}