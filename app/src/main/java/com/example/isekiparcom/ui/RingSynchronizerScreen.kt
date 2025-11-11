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

    // auto validasi part
    LaunchedEffect(viewModel.foundPart.value) {
        if (viewModel.foundPart.value != null && viewModel.scanResult.value != null) {
            viewModel.validateRule {}
        }
    }

    // sukses simpan → kembali ke MainActivity
    LaunchedEffect(viewModel.saveSuccess.value) {
        if (viewModel.saveSuccess.value == true) {
            Toast.makeText(context, "Berhasil disimpan!", Toast.LENGTH_SHORT).show()
            val intent = Intent(context, MainActivity::class.java)
            context.startActivity(intent)
            (context as? Activity)?.finish()
        }
    }

    // tampilkan kamera popup
    if (showCamera) {
        CameraCaptureScreen(
            expectedCodePart = viewModel.foundPart.value?.codePart ?: "UNKNOWN",
            onPredictionResult = { result, file ->
                // 🔥 SETELAH CAMERA SELESAI, SIMPAN HASIL KE VIEWMODEL
                viewModel.setResult(result, file)
                showCamera = false
            },
            onBack = { showCamera = false }
        )
    } else {
        Scaffold(
            topBar = { CenterAlignedTopAppBar(title = { Text("Ring Synchronizer") }) }
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
                }) { Text("Scan QR") }

                Spacer(Modifier.height(8.dp))

                // tampilkan data hasil scan
                viewModel.scanResult.value?.let {
                    Text("Sequence No: ${it.sequenceNo}")
                    Text("Tractor Type: ${it.tractorType}")
                }

                Spacer(Modifier.height(8.dp))

                viewModel.foundPart.value?.let {
                    Text("Code Part: ${it.codePart}")
                    Text("Name Part: ${it.namePart}")
                }

                Spacer(Modifier.height(16.dp))

                if (viewModel.showCaptureButton.value) {
                    Button(onClick = { showCamera = true }) {
                        Text("Ambil Foto")
                    }
                }

                // 🔥 HAPUS BAGIAN INI: TIDAK ADA PREVIEW GAMBAR
                /*
                viewModel.capturedPhotoFile.value?.let { file ->
                    val bmp = BitmapFactory.decodeFile(file.absolutePath)
                    Image(
                        bitmap = bmp.asImageBitmap(),
                        contentDescription = "Hasil Foto",
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(250.dp)
                            .padding(8.dp)
                    )
                }
                */

                // 🔥 TAMPILKAN HASIL AI DAN TOMBOL SIMPAN SECARA LANGSUNG
                viewModel.resultStatus.value?.let { status ->
                    val color = if (status == "OK") Color.Green else Color.Red
                    val label = if (status == "OK") "BERHASIL" else "GAGAL"
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .background(color, MaterialTheme.shapes.medium)
                            .padding(16.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(label, color = Color.White, fontWeight = FontWeight.Bold)
                    }

                    // 🔥 TOMBOL SIMPAN HASIL LANGSUNG MUNCUL SETELAH AI SELESAI
                    Button(
                        onClick = { viewModel.uploadResult() },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Simpan Hasil")
                    }

                    // 🔥 HAPUS TOMBOL AMBIL ULANG
                    /*
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        Button(onClick = { viewModel.uploadResult() }) {
                            Text("Simpan Hasil")
                        }
                        Button(onClick = {
                            viewModel.resetAfterValidationError()
                            showCamera = true
                        }) {
                            Text("Ambil Ulang")
                        }
                    }
                    */
                }
            }
        }
    }
}