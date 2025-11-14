package com.example.isekiparcom.ui

import android.app.Activity
import android.content.Intent
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateColor
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import com.example.isekiparcom.MainActivity
import com.example.isekiparcom.QrScannerActivity
import com.example.isekiparcom.viewmodel.RingSynchronizerViewModel
import com.example.isekiparcom.viewmodel.RingSynchronizerViewModelFactory
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RingSynchronizerScreen(navController: NavHostController) {
    val context = LocalContext.current
    val viewModel: RingSynchronizerViewModel = viewModel(
        factory = RingSynchronizerViewModelFactory(context)
    )

    var showCamera by remember { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }

    // Popup OK/NG 1 detik
    var showResultPopup by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    // ==========================
    //     EVENT LISTENERS
    // ==========================

    // AutoSnackbar saat validasi
    LaunchedEffect(viewModel.validationMessage.value) {
        viewModel.validationMessage.value?.let { msg ->
            snackbarHostState.showSnackbar(
                message = msg, withDismissAction = true, duration = SnackbarDuration.Short
            )
        }
    }

    // Auto-validasi setelah part ditemukan
    LaunchedEffect(viewModel.foundPart.value) {
        if (viewModel.foundPart.value != null && viewModel.scanResult.value != null) {
            viewModel.validateRule {}
        }
    }

    // Kembali ke record list setelah save
    LaunchedEffect(viewModel.saveSuccess.value) {
        if (viewModel.saveSuccess.value == true) {
            Toast.makeText(context, "Berhasil disimpan!", Toast.LENGTH_SHORT).show()

            // Beri sinyal refresh ke list
            navController.previousBackStackEntry
                ?.savedStateHandle
                ?.set("refreshRecords", true)

            navController.popBackStack()
            viewModel.saveSuccess.value = null
        }
    }

    // ==========================
    //          UI
    // ==========================

    Box(modifier = Modifier.fillMaxSize()) {

        Scaffold(
            topBar = {
                CenterAlignedTopAppBar(
                    title = {
                        Text(
                            "Ring Synchronizer",
                            color = MaterialTheme.colorScheme.onPrimary
                        )
                    },
                    navigationIcon = {
                        IconButton(onClick = { navController.popBackStack() }) {
                            Icon(
                                imageVector = androidx.compose.material.icons.Icons.Default.ArrowBack,
                                contentDescription = "Kembali"
                            )
                        }
                    },
                    colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                        containerColor = MaterialTheme.colorScheme.primary
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

                // QR Scanner Launcher
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

                // =====================================================
                //                   DATA KOTAK SCAN RESULT
                // =====================================================

                viewModel.scanResult.value?.let {
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer
                        ),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text("Sequence No: ${it.sequenceNo}", color = MaterialTheme.colorScheme.onPrimaryContainer)
                            Text("Tractor Type: ${it.tractorType}", color = MaterialTheme.colorScheme.onPrimaryContainer)
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
                            Text("Code Part: ${it.codePart}", color = MaterialTheme.colorScheme.onPrimaryContainer)
                            Text("Name Part: ${it.namePart}", color = MaterialTheme.colorScheme.onPrimaryContainer)
                        }
                    }
                }

                // =====================================================
                //                VALIDATION MESSAGE BADGE
                // =====================================================

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
                    Button(
                        onClick = { showCamera = true },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Ambil Foto")
                    }
                }

                // =====================================================
                //               POPUP RESULT
                // =====================================================

                if (showResultPopup && viewModel.resultStatus.value != null) {
                    val status = viewModel.resultStatus.value!!
                    val popupColor = if (status == "OK") Color(0xFF4CAF50) else Color(0xFFE53935)

                    AlertDialog(
                        onDismissRequest = {},
                        confirmButton = {},
                        containerColor = popupColor,
                        title = {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(140.dp)
                                    .padding(16.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    status,
                                    color = Color.White,
                                    fontSize = 69.sp,
                                    fontWeight = FontWeight.ExtraBold
                                )
                            }
                        }
                    )
                }

                // =====================================================
                //                   BADGE BESAR OK / NG
                // =====================================================

                if (!showResultPopup) {
                    viewModel.resultStatus.value?.let { status ->
                        val color = if (status == "OK") Color(0xFF43A047) else Color(0xFFE53935)

                        val infiniteTransition = rememberInfiniteTransition(label = "")
                        val blinkColor by infiniteTransition.animateColor(
                            initialValue = if (status == "OK") Color(0xFF43A047) else Color(0xFFE53935),
                            targetValue = if (status == "OK") Color(0xFF8BD58E) else Color(
                                0xFFFA7E75
                            ),
                            animationSpec = infiniteRepeatable(
                                animation = tween(durationMillis = 600),
                                repeatMode = RepeatMode.Reverse
                            ),
                            label = "blinkColor"
                        )

                        Spacer(Modifier.height(20.dp)) // 🔥 Jarak dari tombol Ambil Foto

                        Box(
                            Modifier
                                .fillMaxWidth()
                                .height(140.dp)
                                .background(blinkColor, MaterialTheme.shapes.medium)
                                .padding(16.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                status,
                                color = Color.White,
                                fontSize = 69.sp,
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
        }

        // =====================================================
        //                    CAMERA OVERLAY
        // =====================================================

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

                        // 🔥 Tampilkan popup OK/NG selama 1 detik
                        showResultPopup = true

                        scope.launch {
                            delay(2000)
                            showResultPopup = false
                        }
                    },
                    onBack = { showCamera = false }
                )
            }
        }
    }
}
