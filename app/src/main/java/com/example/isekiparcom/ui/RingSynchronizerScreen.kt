package com.example.isekiparcom.ui

import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.isekiparcom.viewmodel.RingSynchronizerViewModel
import com.example.isekiparcom.viewmodel.RingSynchronizerViewModelFactory
import okhttp3.MediaType
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.RequestBody
import org.json.JSONObject

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RingSynchronizerScreen(
    navController: androidx.navigation.NavController
) {
    val context = LocalContext.current
    val viewModel: RingSynchronizerViewModel = viewModel(factory = RingSynchronizerViewModelFactory(context))

    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val qrText = result.data?.getStringExtra("qr_result") ?: return@rememberLauncherForActivityResult
            viewModel.handleQrScanned(qrText)
        }
    }

    Scaffold(
        topBar = { CenterAlignedTopAppBar(title = { Text("Ring Synchronizer") }) }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
        ) {
            Button(onClick = {
                launcher.launch(Intent(context, QrScannerActivity::class.java))
            }) {
                Text("Scan QR")
            }

            // Tampilkan hasil QR
            viewModel.scanResult.value?.let { result ->
                Card(Modifier.padding(vertical = 8.dp)) {
                    Column(Modifier.padding(12.dp)) {
                        Text("Sequence No: ${result.sequenceNo}")
                        Text("Tractor Type: ${result.tractorType}")
                    }
                }
            }

            // Tampilkan Part
            viewModel.foundPart.value?.let { part ->
                Card(Modifier.padding(vertical = 8.dp)) {
                    Column(Modifier.padding(12.dp)) {
                        Text("Code Part: ${part.codePart}")
                        Text("Name Part: ${part.namePart}")
                    }
                }
            }

            // Validasi Button
            if (viewModel.scanResult.value != null && viewModel.foundPart.value != null) {
                Button(onClick = { viewModel.validateRule() }) {
                    Text("Validasi Rule")
                }
            }

            // Validasi Result
            viewModel.validationMessage.value?.let { msg ->
                Text(
                    text = msg,
                    color = if (msg.contains("Siap")) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                )
            }

            // Capture Button
            if (viewModel.showCaptureButton.value) {
                Button(onClick = {
                    // TODO: Integrasi kamera foto (untuk sementara mock)
                    Toast.makeText(context, "Fitur ambil foto akan diimplementasi", Toast.LENGTH_SHORT).show()
                }) {
                    Text("Ambil Foto")
                }
            }

            // Hasil AI
            viewModel.resultStatus.value?.let { status ->
                Text("Hasil AI: $status", color = if (status == "OK") Green else Red)
                if (viewModel.showUploadButton.value) {
                    Button(onClick = {
                        viewModel.uploadResult()
                        Toast.makeText(context, "Mengunggah hasil...", Toast.LENGTH_SHORT).show()
                    }) {
                        Text("Simpan Hasil")
                    }
                }
            }
        }
    }
}

@Composable
fun QrScannerDialog(onDetected: (String) -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("QR Scanner") },
        text = { Text("Klik tombol di bawah untuk simulasi scan QR:\n\n`12345;...;TRACTOR_TYPE`") },
        confirmButton = {
            Button(onClick = {
                onDetected("12345;dummy;TRACTOR_TYPE")
                onDismiss()
            }) { Text("Simulasi QR") }
        },
        dismissButton = { Button(onClick = onDismiss) { Text("Batal") } }
    )
}

// ✅ Perbaiki buildMultipart
fun buildMultipart(viewModel: RingSynchronizerViewModel, bitmap: android.graphics.Bitmap?): MultipartBody {
    val builder = MultipartBody.Builder().setType(MultipartBody.FORM)
    val result = viewModel.scanResult.value!!
    val part = viewModel.foundPart.value!!

    builder.addFormDataPart("Id_Comparison", part.idComparison.toString())
    builder.addFormDataPart("Id_Tractor", part.idTractor.toString())
    builder.addFormDataPart("Id_Part", part.idPart.toString())
    builder.addFormDataPart("No_Tractor_Record", result.sequenceNo)
    builder.addFormDataPart("Result_Record", viewModel.resultStatus.value ?: "OK")

    viewModel.capturedPhotoFile.value?.let { file ->
        // ✅ Gunakan RequestBody.create() + MediaType.get()
        val fileBody = RequestBody.create("image/jpeg".toMediaType(), file)
        builder.addFormDataPart("Photo_Ng_Path", file.name, fileBody)
    }

    return builder.build()
}