package com.example.isekiparcom.ui

import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import com.google.accompanist.swiperefresh.SwipeRefresh
import com.google.accompanist.swiperefresh.rememberSwipeRefreshState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject // Tambahkan import ini

// ... data class RecordItem (sama seperti sebelumnya) ...
data class BearingKoyoRecordItem(
    val id: Int,
    val noTractor: String,
    val nameTractor: String,
    val comparison: String,
    val codePart: String,
    val result: String,
    val timeRecord: String,
    val textRecord: String?, // Bisa null
    val predictRecord: String? // Bisa null
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecordListBearingKoyoScreen(navController: NavHostController) {
    val client = remember { OkHttpClient() }
    var records by remember { mutableStateOf<List<BearingKoyoRecordItem>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var isRefreshing by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    fun fetchRecords() {
        scope.launch(Dispatchers.IO) {
            try {
                val url = "http://192.168.173.207/iseki_parcom/public/api/bearing-koyo/index"
                val request = Request.Builder().url(url).build()
                val response = client.newCall(request).execute()

                if (!response.isSuccessful) {
                    Log.e("RecordList", "API request failed: ${response.code}")
                    return@launch
                }

                val body = response.body?.string() ?: "[]"
                Log.d("RecordList", "Raw API response: $body") // Log raw response

                val jsonArr = JSONArray(body)
                val list = mutableListOf<BearingKoyoRecordItem>()

                // Di dalam fetchRecords()
                for (i in 0 until jsonArr.length()) {
                    val obj = jsonArr.getJSONObject(i)

                    // Ambil objek nested
                    val comparisonObj = obj.optJSONObject("comparison")
                    val tractorObj = obj.optJSONObject("tractor")
                    val partObj = obj.optJSONObject("part")

                    // Ambil string, fallback ke "" jika null
                    val comparisonName = comparisonObj?.optString("Name_Comparison") ?: ""
                    val tractorType = tractorObj?.optString("Type_Tractor") ?: ""
                    val partCode = partObj?.optString("Code_Part") ?: ""

                    // 🔥 Ambil field baru
                    val textRec = obj.optString("Text_Record") // Bisa null, jadi gunakan optString
                    val predictRec = obj.optString("Predict_Record") // Bisa null

                    list.add(
                        BearingKoyoRecordItem( // Gunakan class baru
                            id = obj.optInt("Id_Record"),
                            noTractor = obj.optString("No_Tractor_Record"),
                            nameTractor = tractorType,
                            comparison = comparisonName,
                            codePart = partCode,
                            result = obj.optString("Result_Record"),
                            timeRecord = obj.optString("Time_Record"),
                            textRecord = textRec, // Tambahkan field baru
                            predictRecord = predictRec // Tambahkan field baru
                        )
                    )
                }
                // Publish hasil ke state di Main thread
                withContext(Dispatchers.Main) {
                    records = list // records sekarang bertipe List<BearingKoyoRecordItem>
                }
            } catch (e: org.json.JSONException) {
                Log.e("RecordList", "JSON parsing error: ${e.message}", e)
                withContext(Dispatchers.Main) {
                    // Bisa kosongkan list atau tampilkan error lain
                    records = emptyList()
                }
            } catch (e: Exception) {
                Log.e("RecordList", "General error: ${e.message}", e)
                withContext(Dispatchers.Main) {
                    records = emptyList()
                }
            } finally {
                // Hanya set isLoading ke false di Main thread
                withContext(Dispatchers.Main) {
                    isLoading = false
                    isRefreshing = false
                }
            }
        }
    }

    // Panggil fetchRecords saat komposable pertama kali dikompos
    LaunchedEffect(Unit) {
        fetchRecords()
        // 🔥 HAPUS BARIS INI:
        // navController.popBackStack("dashboard", inclusive = false)
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("Bearing Metal", color = MaterialTheme.colorScheme.onPrimary) },
                navigationIcon = { // Tambahkan tombol back
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
        if (isLoading) {
            Box(
                Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
            }
        } else {
            val horizontalScroll = rememberScrollState()

            SwipeRefresh(
                state = rememberSwipeRefreshState(isRefreshing),
                onRefresh = {
                    isRefreshing = true
                    fetchRecords() // Panggil ulang data
                }
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding)
                        .padding(12.dp)
                        .horizontalScroll(horizontalScroll) // Izinkan scroll horizontal jika lebar tabel melebihi layar
                ) {
                    // 🔹 Tombol Scan di atas tabel
                    Button(
                        onClick = { navController.navigate("bearing_metal") },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.secondary,
                            contentColor = Color.White
                        ),
                        modifier = Modifier
                            .align(Alignment.Start)
                            .padding(bottom = 12.dp)
                    ) {
                        Text("Scan")
                    }

                    // 🔹 Header Table
                    // 🔹 Header Table
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .background(
                                MaterialTheme.colorScheme.primaryContainer,
                                RoundedCornerShape(4.dp)
                            )
                            .padding(vertical = 8.dp)
                    ) {
                        BearingKoyoHeaderText("No", 30.dp)
                        BearingKoyoHeaderText("No Tractor", 60.dp)
                        BearingKoyoHeaderText("Name Tractor", 80.dp)
                        BearingKoyoHeaderText("Comparison", 90.dp)
                        BearingKoyoHeaderText("Part Detection", 90.dp)
                        BearingKoyoHeaderText("Result", 60.dp)
                        BearingKoyoHeaderText("Text Record", 100.dp) // 🔥 Kolom baru
                        BearingKoyoHeaderText("Predict Record", 100.dp) // 🔥 Kolom baru
                        BearingKoyoHeaderText("Time Record", 100.dp) // Pindahkan ke akhir jika diinginkan
                    }

                    Divider(color = MaterialTheme.colorScheme.secondary.copy(alpha = 0.4f))

                    // 🔹 Tabel scroll vertikal atau pesan kosong
                    if (records.isEmpty()) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize() // Gunakan fillMaxSize agar pesan muncul di tengah area yang tersedia setelah header
                                .padding(16.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "Tidak ada data hari ini.",
                                color = MaterialTheme.colorScheme.onSurface,
                                fontSize = 16.sp,
                                textAlign = TextAlign.Center
                            )
                        }
                    } else {
                        LazyColumn(
                            modifier = Modifier.fillMaxWidth() // Pastikan LazyColumn mengisi lebar penuh
                        ) {
                            itemsIndexed(records) { index, record -> // record sekarang bertipe BearingKoyoRecordItem
                                val rowBg = if (index % 2 == 0)
                                    MaterialTheme.colorScheme.surface
                                else
                                    MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f)

                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(rowBg)
                                        .padding(vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    BearingKoyoCellText("${index + 1}", 30.dp)
                                    BearingKoyoCellText(record.noTractor, 60.dp)
                                    BearingKoyoCellText(record.nameTractor, 80.dp)
                                    BearingKoyoCellText(record.comparison, 90.dp)
                                    BearingKoyoCellText(record.codePart, 90.dp)
                                    BearingKoyoResultBadge(record.result, 60.dp)
                                    // 🔥 Tambahkan CellText baru untuk Text Record
                                    BearingKoyoCellText(record.textRecord ?: "", 100.dp) // Gunakan ?: "" untuk tampilkan kosong jika null
                                    // 🔥 Tambahkan CellText baru untuk Predict Record
                                    BearingKoyoCellText(record.predictRecord ?: "", 100.dp) // Gunakan ?: "" untuk tampilkan kosong jika null
                                    BearingKoyoCellText(record.timeRecord, 100.dp) // Pindahkan ke akhir jika diinginkan
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

// ... fungsi HeaderText, CellText, ResultBadge (sama seperti sebelumnya) ...
@Composable
fun BearingKoyoHeaderText(text: String, width: Dp) { // Ganti nama
    Text(
        text = text,
        color = MaterialTheme.colorScheme.onPrimaryContainer,
        fontWeight = FontWeight.SemiBold,
        textAlign = TextAlign.Center,
        fontSize = 12.sp,
        lineHeight = 12.sp,
        modifier = Modifier.width(width)
    )
}

@Composable
fun BearingKoyoCellText(text: String, width: Dp) { // Ganti nama
    Text(
        text = text,
        textAlign = TextAlign.Center,
        fontSize = 12.sp,
        lineHeight = 12.sp,
        modifier = Modifier.width(width),
        color = MaterialTheme.colorScheme.onSurface
    )
}

@Composable
fun BearingKoyoResultBadge(result: String, width: Dp) { // Ganti nama
    val (bg, textColor) = when (result.uppercase()) {
        "OK" -> Color(0xFF4CAF50) to Color.White
        "NG" -> Color(0xFFF44336) to Color.White
        "NG-OK" -> Color(0xFFFFC107) to Color.Black
        else -> MaterialTheme.colorScheme.secondary to Color.White
    }

    Box(
        modifier = Modifier
            .width(width)
            .background(bg, RoundedCornerShape(12.dp))
            .padding(vertical = 2.dp, horizontal = 4.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            result,
            color = textColor,
            fontWeight = FontWeight.Bold,
            fontSize = 12.sp
        )
    }
}