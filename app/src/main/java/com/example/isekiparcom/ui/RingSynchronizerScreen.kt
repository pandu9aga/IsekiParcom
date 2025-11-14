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
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import com.example.isekiparcom.MainActivity
import com.example.isekiparcom.QrScannerActivity
import com.example.isekiparcom.viewmodel.RingSynchronizerViewModel
import com.example.isekiparcom.viewmodel.RingSynchronizerViewModelFactory

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RingSynchronizerScreen(navController: NavHostController) {
    val context = LocalContext.current
    val viewModel: RingSynchronizerViewModel = viewModel(
        factory = RingSynchronizerViewModelFactory(context)
    )

    var showCamera by remember { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }

    // 🔹 Snackbar otomatis saat validasi selesai
    LaunchedEffect(viewModel.validationMessage.value) {
        viewModel.validationMessage.value?.let { msg ->
            snackbarHostState.showSnackbar(
                message = msg,
                withDismissAction = true,
                duration = SnackbarDuration.Short
            )
        }
    }

    // 🔹 Auto validasi setelah part ditemukan
    LaunchedEffect(viewModel.foundPart.value) {
        if (viewModel.foundPart.value != null && viewModel.scanResult.value != null) {
            viewModel.validateRule {}
        }
    }

    // 🔹 Kembali ke Record List setelah simpan
    LaunchedEffect(viewModel.saveSuccess.value) {
        if (viewModel.saveSuccess.value == true) {
            Toast.makeText(context, "Berhasil disimpan!", Toast.LENGTH_SHORT).show()

            // 🔥 Beri sinyal ke list agar refresh data
            navController.previousBackStackEntry
                ?.savedStateHandle
                ?.set("refreshRecords", true)

            // 🔥 Kembali ke Record List
            navController.popBackStack()

            // Reset flag agar tidak retrigger
            viewModel.saveSuccess.value = null
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {

        // ======================
        // 🔹 LAYER 1 — UI utama
        // ======================
        Scaffold(
            topBar = {
                CenterAlignedTopAppBar(
                    title = {
                        Text(
                            "Ring Synchronizer",
                            color = MaterialTheme.colorScheme.onPrimary // 🔹 putih
                        )
                    },
                    colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                        containerColor = MaterialTheme.colorScheme.primary, // 🔹 pink
                        titleContentColor = MaterialTheme.colorScheme.onPrimary // 🔹 putih
                    )
                )
            }
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

                Button(onClick = {
                    launcher.launch(Intent(context, QrScannerActivity::class.java))
                }) {
                    Text("Scan QR")
                }

                Spacer(Modifier.height(12.dp))

                viewModel.scanResult.value?.let {
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer // 🔹 PinkContainer
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp)
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp)
                        ) {
                            Text(
                                "Sequence No: ${it.sequenceNo}",
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                style = MaterialTheme.typography.bodyLarge
                            )
                            Text(
                                "Tractor Type: ${it.tractorType}",
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                style = MaterialTheme.typography.bodyLarge
                            )
                        }
                    }
                }

                viewModel.foundPart.value?.let {
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer // 🔹 PinkContainer
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp)
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp)
                        ) {
                            Text(
                                "Code Part: ${it.codePart}",
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                style = MaterialTheme.typography.bodyLarge
                            )
                            Text(
                                "Name Part: ${it.namePart}",
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                style = MaterialTheme.typography.bodyLarge
                            )
                        }
                    }
                }

                Spacer(Modifier.height(20.dp))

                // 🔹 TAMPILKAN BADGE VALIDASI RULE
                viewModel.validationMessage.value?.let { msg ->
                    val success = msg.contains("Siap melanjutkan", ignoreCase = true)
                    val badgeColor = if (success) Color(0xFF4CAF50) else Color(0xFFE53935)
                    val textColor = Color.White

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(badgeColor, shape = MaterialTheme.shapes.medium)
                            .padding(vertical = 14.dp, horizontal = 20.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = msg,
                            color = textColor,
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.Medium
                        )
                    }

                    Spacer(Modifier.height(20.dp))
                }

                if (viewModel.showCaptureButton.value) {
                    Button(
                        onClick = { showCamera = true },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Ambil Foto")
                    }
                }

                Spacer(Modifier.height(24.dp))

                // 🔹 HASIL FOTO OK / NG (BADGE BESAR)
                viewModel.resultStatus.value?.let { status ->
                    val color = if (status == "OK") Color(0xFF43A047) else Color(0xFFE53935)
                    val label = if (status == "OK") "OK" else "NG"

                    Box(
                        Modifier
                            .fillMaxWidth()
                            .height(160.dp)
                            .background(color, MaterialTheme.shapes.medium)
                            .padding(24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            label,
                            color = Color.White,
                            style = MaterialTheme.typography.headlineLarge,
                            fontWeight = FontWeight.ExtraBold
                        )
                    }

                    Spacer(Modifier.height(24.dp))

                    Button(
                        onClick = { viewModel.uploadResult() },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Simpan Hasil", color = Color.White)
                    }
                }
            }
        }

        // ======================
        // 🔹 LAYER 2 — Kamera (Overlay)
        // ======================
        if (showCamera) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.95f))
            ) {
                CameraCaptureScreen(
                    expectedCodePart = viewModel.foundPart.value?.codePart ?: "UNKNOWN",
                    onPredictionResult = { result, file ->
                        viewModel.setResult(result, file)
                        showCamera = false
                    },
                    onBack = { showCamera = false }
                )
            }
        }
    }
}