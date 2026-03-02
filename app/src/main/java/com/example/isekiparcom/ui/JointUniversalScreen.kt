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
import com.example.isekiparcom.QrScannerActivity
import com.example.isekiparcom.viewmodel.JointUniversalViewModel
import com.example.isekiparcom.viewmodel.JointUniversalViewModelFactory
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun JointUniversalScreen(navController: NavHostController) {
    val context = LocalContext.current
    val viewModel: JointUniversalViewModel = viewModel(
        factory = JointUniversalViewModelFactory(context)
    )

    var showCamera by remember { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    // AutoSnackbar dihilangkan sesuai permintaan (cukup di card)
    /*
    LaunchedEffect(viewModel.validationMessage.value) {
        viewModel.validationMessage.value?.let { msg ->
            snackbarHostState.showSnackbar(
                message = msg, withDismissAction = true, duration = SnackbarDuration.Short
            )
        }
    }
    */

    // Kembali ke list joint universal setelah save
    LaunchedEffect(viewModel.saveSuccess.value) {
        if (viewModel.saveSuccess.value == true) {
            Toast.makeText(context, "Berhasil disimpan!", Toast.LENGTH_SHORT).show()
            navController.navigate("record_list_joint") {
                popUpTo("joint_universal") { inclusive = true }
            }
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Scaffold(
            topBar = {
                CenterAlignedTopAppBar(
                    title = {
                        Text(
                            "Joint Universal",
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

                // DATA KOTAK SCAN RESULT
                viewModel.scanResult.value?.let { qr ->
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer
                        ),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text("Sequence No: ${qr.sequenceNo}", color = MaterialTheme.colorScheme.onPrimaryContainer)
                            Text("Production Date: ${qr.productionDate}", color = MaterialTheme.colorScheme.onPrimaryContainer)
                        }
                    }
                }

                // DATA KOTAK API RESULT (Model_Name_Plan & Text_Record)
                viewModel.modelNamePlan.value?.let { modelName ->
                    Spacer(Modifier.height(10.dp))
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.secondaryContainer
                        ),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text("Model Name: $modelName", color = MaterialTheme.colorScheme.onSecondaryContainer)
                            viewModel.textRecord.value?.let { textRecord ->
                                Text("Expected Text: $textRecord", color = MaterialTheme.colorScheme.onSecondaryContainer, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }

                // VALIDATION MESSAGE BADGE
                Spacer(Modifier.height(16.dp))

                viewModel.validationMessage.value?.let { msg ->
                    val success = msg == "Semua proses sebelumnya sudah selesai. Siap melanjutkan."
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

                // AI PREDICTION RESULT BADGE
                viewModel.predictRecord.value?.let { predictRec ->
                    Spacer(Modifier.height(10.dp))
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.tertiaryContainer
                        ),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = "Correct Part: ${viewModel.textRecord.value ?: "-"}",
                                color = MaterialTheme.colorScheme.onTertiaryContainer,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(Modifier.height(4.dp))
                            Text(
                                text = "Prediction: $predictRec",
                                color = MaterialTheme.colorScheme.onTertiaryContainer,
                                fontSize = 18.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }

                Spacer(Modifier.height(16.dp))
                
                // Tombol Ambil Foto
                if (viewModel.showCaptureButton.value) {
                    if (!viewModel.showUploadButton.value) {
                        Button(
                            onClick = { showCamera = true },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Ambil Foto Part")
                        }
                    } else if (viewModel.resultStatus.value == "NG") {
                        // Jika NG, izinkan ambil ulang
                        OutlinedButton(
                            onClick = { 
                                viewModel.clearPhoto()
                                showCamera = true 
                            },
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.Red)
                        ) {
                            Text("Ambil Ulang Foto (NG)")
                        }
                    }
                }

                // POPUP RESULT
                if (viewModel.showResultPopup.value && viewModel.resultStatus.value != null) {
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

                // BADGE BESAR OK / NG
                if (viewModel.popupFinished.value && viewModel.resultStatus.value != null) {
                    val status = viewModel.resultStatus.value!!
                    
                    val infiniteTransition = rememberInfiniteTransition(label = "")
                    val blinkColor by infiniteTransition.animateColor(
                        initialValue = if (status == "OK") Color(0xFF43A047) else Color(0xFFE53935),
                        targetValue = if (status == "OK") Color(0xFF8BD58E) else Color(0xFFFA7E75),
                        animationSpec = infiniteRepeatable(
                            animation = tween(durationMillis = 600),
                            repeatMode = RepeatMode.Reverse
                        ),
                        label = "blinkColor"
                    )

                    Spacer(Modifier.height(20.dp))

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

        // CAMERA OVERLAY
        if (showCamera) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.95f))
            ) {
                CameraCaptureScreen(
                    onPhotoCaptured = { file -> 
                        viewModel.processImage(file)
                        showCamera = false
                    },
                    onBack = { showCamera = false }
                )
            }
        }
    }
}
