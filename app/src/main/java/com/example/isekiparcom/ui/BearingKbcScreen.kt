// app/src/main/java/com/example/isekiparcom/ui/BearingKbcScreen.kt

package com.example.isekiparcom.ui

import android.app.Activity
import android.content.Intent
import android.graphics.BitmapFactory
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import com.example.isekiparcom.viewmodel.BearingKbcViewModel
import com.example.isekiparcom.viewmodel.BearingKbcViewModelFactory

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BearingKbcScreen(navController: NavHostController) {
    val context = LocalContext.current
    val viewModel: BearingKbcViewModel = viewModel(
        factory = BearingKbcViewModelFactory(context)
    )

    var showCameraPart by remember { mutableStateOf(false) }
    var showCameraOcr by remember { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }

    // Auto validasi setelah part ditemukan
    LaunchedEffect(viewModel.foundPart.value) {
        if (viewModel.foundPart.value != null && viewModel.scanResult.value != null) {
            viewModel.validateRule()
        }
    }

    // Kembali ke dashboard setelah simpan
    LaunchedEffect(viewModel.saveSuccess.value) {
        if (viewModel.saveSuccess.value == true) {
            Toast.makeText(context, "Berhasil disimpan!", Toast.LENGTH_SHORT).show()
            navController.popBackStack()
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Scaffold(
            topBar = {
                CenterAlignedTopAppBar(
                    title = { Text("Bearing KBC") },
                    navigationIcon = {
                        IconButton(onClick = { navController.popBackStack() }) {
                            Icon(
                                imageVector = Icons.Default.ArrowBack,
                                contentDescription = "Kembali"
                            )
                        }
                    }
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

                viewModel.foundPart.value?.let {
                    Spacer(Modifier.height(10.dp))
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer
                        ),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text("Code Part: ${it.codePart}")
                            Text("Name Part: ${it.namePart}")
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

                if (viewModel.showCaptureButton.value) {
                    Button(onClick = { showCameraPart = true }) {
                        Text("Ambil Foto Part")
                    }
                }

                // Hasil deteksi part
                viewModel.partDetectionResult.value?.let { result ->
                    Spacer(Modifier.height(16.dp))
                    val color = if (result.lowercase() == "shaft") Color(0xFF4CAF50) else Color(0xFFE53935)
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
                    if (result.lowercase() == "shaft") {
                        Text("Lanjutkan ke OCR...")
                    } else {
                        Text("Part bukan Shaft. Proses selesai.")
                    }
                }

                // Tombol ambil foto OCR (muncul jika part = shaft)
                if (viewModel.showSecondCaptureButton.value) {
                    Spacer(Modifier.height(16.dp))
                    Button(onClick = { showCameraOcr = true }) {
                        Text("Ambil Foto OCR")
                    }
                }

                // Hasil OCR
                viewModel.ocrResult.value?.let { ocr ->
                    Spacer(Modifier.height(16.dp))
                    Card {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text("OCR Result (cleaned): $ocr")
                            if (ocr.contains("KBC", ignoreCase = true)) {
                                Text("Status: OK (Mengandung KBC)", color = Color.Green)
                            } else {
                                Text("Status: NG (Tidak mengandung KBC)", color = Color.Red)
                            }
                        }
                    }
                }

                // Hasil akhir
                viewModel.finalResult.value?.let { final ->
                    Spacer(Modifier.height(24.dp))
                    val color = if (final == "OK") Color(0xFF43A047) else Color(0xFFE53935)
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .height(160.dp)
                            .background(color, MaterialTheme.shapes.medium)
                            .padding(24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            final,
                            color = Color.White,
                            style = MaterialTheme.typography.headlineLarge,
                            fontWeight = FontWeight.ExtraBold
                        )
                    }

                    Spacer(Modifier.height(24.dp))

                    if (viewModel.showUploadButton.value) {
                        Button(
                            onClick = { viewModel.uploadResult() },
                            modifier = Modifier.fillMaxWidth()
                        ) {
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
                    expectedCodePart = "IGNORED_FOR_BEARING", // Tidak digunakan
                    onPredictionResult = { _, file ->
                        viewModel.capturedPartPhotoFile.value = file
                        // Proses TFLite
                        val bitmap = BitmapFactory.decodeFile(file.absolutePath)
                        viewModel.runTfliteForPart(bitmap)
                        showCameraPart = false
                    },
                    onBack = { showCameraPart = false }
                )
            }
        }

        // Overlay Kamera OCR
        if (showCameraOcr) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.95f))
            ) {
                CameraCaptureScreen( // Gunakan screen yang sama, atau buat yang baru
                    expectedCodePart = "IGNORED_FOR_OCR", // Tidak digunakan
                    onPredictionResult = { _, file ->
                        viewModel.capturedOcrPhotoFile.value = file
                        // Proses OCR
                        val bitmap = BitmapFactory.decodeFile(file.absolutePath)
                        viewModel.runOcr(bitmap)
                        showCameraOcr = false
                    },
                    onBack = { showCameraOcr = false }
                )
            }
        }
    }
}