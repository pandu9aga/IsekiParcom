// app/src/main/java/com/example/isekiparcom/ui/BearingKbcScreen.kt

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
    var showResultPopup by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    // ❌ HAPUS LAUNCHED EFFECT INI KARENA TIDAK ADA foundPart LAGI
    /*
    LaunchedEffect(viewModel.foundPart.value) {
        if (viewModel.foundPart.value != null && viewModel.scanResult.value != null) {
            viewModel.validateRule()
        }
    }
    */

    // ✅ GANTI DENGAN INI: Validasi setelah QR discan jika belum divalidasi
    LaunchedEffect(viewModel.scanResult.value) {
        if (viewModel.scanResult.value != null && viewModel.validationMessage.value == null) {
            viewModel.validateRule()
        }
    }

    // Kembali ke list bearing kbc setelah simpan
    LaunchedEffect(viewModel.saveSuccess.value) {
        if (viewModel.saveSuccess.value == true) {
            Toast.makeText(context, "Berhasil disimpan!", Toast.LENGTH_SHORT).show()
            // 🔥 NAVIGASI KE LIST
            navController.navigate("record_list_bearing_kbc") {
                popUpTo("bearing_kbc") { inclusive = true } // Hapus stack bearing_kbc
            }
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Scaffold(
            topBar = {
                CenterAlignedTopAppBar(
                    title = {
                        Text(
                            "Bearing KBC",
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

                // ❌ HAPUS BAGIAN INI KARENA TIDAK ADA PART LAGI
                /*
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
                */

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

                // 🔥 TOMBOL AMBIL FOTO PART SEKARANG LANGSUNG MUNCUL SETELAH VALIDASI
                if (viewModel.showCaptureButton.value && viewModel.partDetectionResult.value == null) {
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
                        Text("Part bukan Shaft. Silakan ambil ulang.")
                    }
                }

                // Tombol ambil foto OCR (muncul jika part = shaft)
                if (viewModel.showSecondCaptureButton.value) {
                    Spacer(Modifier.height(16.dp))
                    Button(onClick = { showCameraOcr = true }) {
                        Text("Ambil Foto OCR")
                    }
                }

                // Tombol ambil ulang foto part (muncul jika part != shaft)
                if (viewModel.showRetakePartButton.value) {
                    Spacer(Modifier.height(16.dp))
                    Button(onClick = {
                        viewModel.resetForRetakePart()
                        showCameraPart = true // Buka kamera lagi untuk ambil foto part
                    }) {
                        Text("Ambil Ulang Foto Part")
                    }
                }

                // ===============================
                //     POPUP OK/NG (Tampil sementara)
                // ===============================

                if (viewModel.showResultPopup.value && viewModel.ocrResult.value != null) {
                    val result = viewModel.ocrResult.value!! // Ambil hasil OCR untuk menentukan OK/NG
                    val containsKbc = result.contains("KBC", ignoreCase = true)
                    val finalResult = if (containsKbc) "OK" else "NG"
                    val popupColor = if (finalResult == "OK") Color(0xFF4CAF50) else Color(0xFFE53935)

                    AlertDialog(
                        onDismissRequest = { /* Tidak bisa di-dismiss otomatis */ },
                        confirmButton = { /* Biarkan kosong */ },
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

                // ===============================
                //     BADGE BESAR PERMANEN (Tampil setelah popup hilang)
                // ===============================

                // Tampilkan badge besar dan tombol simpan hanya jika popup tidak aktif
                if (!viewModel.showResultPopup.value && viewModel.popupFinished.value) {
                    val result = viewModel.ocrResult.value!!
                    val containsKbc = result.contains("KBC", ignoreCase = true)
                    val finalResult = if (containsKbc) "OK" else "NG"
                    val color = if (finalResult == "OK") Color(0xFF43A047) else Color(0xFFE53935)

                    val infiniteTransition = rememberInfiniteTransition(label = "Blink Transition")
                    val blinkColor by infiniteTransition.animateColor(
                        initialValue = color,
                        targetValue = if (finalResult == "OK") Color(0xFF8BD58E) else Color(0xFFFA7E75),
                        animationSpec = infiniteRepeatable(
                            animation = tween(durationMillis = 600),
                            repeatMode = RepeatMode.Reverse
                        ),
                        label = "Blink Color"
                    )

                    Spacer(Modifier.height(16.dp))

                    Card {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text("OCR Result (cleaned): $result")
                            if (result.contains("KBC", ignoreCase = true)) {
                                Text("Status: OK (Mengandung teks KBC)", color = Color(0xFF43A047))
                            } else {
                                Text("Status: NG (Tidak mengandung teks KBC)", color = Color(0xFFE53935))
                            }
                        }
                    }

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
                            finalResult,
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
                CameraCaptureScreen(
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