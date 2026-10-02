package com.example.isekiparcom.ui

import android.app.Activity
import android.content.Intent
import android.graphics.BitmapFactory
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import com.example.isekiparcom.viewmodel.ShaftGcViewModel
import com.example.isekiparcom.viewmodel.ShaftGcViewModelFactory

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShaftGcScreen(navController: NavHostController) {
    val context = LocalContext.current
    val viewModel: ShaftGcViewModel = viewModel(
        factory = ShaftGcViewModelFactory(context)
    )

    var showCamera by remember { mutableStateOf(false) }

    val qrLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val qrContent = result.data?.getStringExtra("SCAN_RESULT") ?: return@rememberLauncherForActivityResult
            viewModel.handleQrScanned(qrContent)
        }
    }

    if (showCamera) {
        CameraCaptureScreen(
            onImageCaptured = { file ->
                showCamera = false
                val bitmap = BitmapFactory.decodeFile(file.absolutePath)
                viewModel.processImage(bitmap)
            },
            onCancel = { showCamera = false }
        )
        return
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Shaft GC Validation") },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.primary)
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .padding(16.dp)
                .fillMaxSize()
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Button(
                onClick = { qrLauncher.launch(Intent(context, QrScannerActivity::class.java)) },
                modifier = Modifier.fillMaxWidth().height(50.dp)
            ) {
                Text("Scan QR Kanban")
            }

            Spacer(modifier = Modifier.height(16.dp))

            viewModel.currentQr.value?.let { qr ->
                Text("QR: $qr", fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(8.dp))
            }

            viewModel.validationMessage.value?.let { msg ->
                Text(msg, color = if (viewModel.showCaptureButton.value) Color.Green else Color.Red)
                Spacer(modifier = Modifier.height(16.dp))
            }

            viewModel.expectedText.value?.let { expected ->
                Text("Model Plan Target: $expected", color = Color.Blue)
                Spacer(modifier = Modifier.height(16.dp))
            }

            if (viewModel.showCaptureButton.value) {
                Button(
                    onClick = { showCamera = true },
                    modifier = Modifier.fillMaxWidth().height(50.dp)
                ) {
                    Text("Buka Kamera & Validasi")
                }
                Spacer(modifier = Modifier.height(16.dp))
            }

            viewModel.resultStatus.value?.let { res ->
                Text("Hasil Prediksi: $res", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = if(res=="OK") Color.Green else Color.Red)
                Spacer(modifier = Modifier.height(16.dp))
            }

            if (viewModel.showUploadButton.value) {
                Button(
                    onClick = { viewModel.uploadResult() },
                    enabled = !viewModel.isUploading.value,
                    modifier = Modifier.fillMaxWidth().height(50.dp)
                ) {
                    Text(if (viewModel.isUploading.value) "Mengunggah..." else "Upload Hasil")
                }
            }

            viewModel.saveSuccess.value?.let { success ->
                if (success) {
                    Text("Data berhasil disimpan!", color = Color.Green, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}
