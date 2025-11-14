package com.example.isekiparcom.ui

import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import com.google.accompanist.swiperefresh.SwipeRefresh
import com.google.accompanist.swiperefresh.rememberSwipeRefreshState

data class RecordItem(
    val id: Int,
    val noTractor: String,
    val nameTractor: String,
    val comparison: String,
    val codePart: String,
    val result: String,
    val timeRecord: String
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecordListRingSynchronizerScreen(navController: NavHostController) {
    val client = remember { OkHttpClient() }
    var records by remember { mutableStateOf<List<RecordItem>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var isRefreshing by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    fun fetchRecords() {
        scope.launch(Dispatchers.IO) {
            try {
                val url = "http://192.168.173.207/iseki_parcom/public/api/ring-synchronizer/index"
                val response = client.newCall(Request.Builder().url(url).build()).execute()
                val body = response.body?.string() ?: "[]"
                val jsonArr = JSONArray(body)
                val list = mutableListOf<RecordItem>()

                for (i in 0 until jsonArr.length()) {
                    val obj = jsonArr.getJSONObject(i)
                    val comparison = obj.getJSONObject("comparison").getString("Name_Comparison")
                    val tractor = obj.getJSONObject("tractor").getString("Type_Tractor")
                    val part = obj.getJSONObject("part").getString("Code_Part")

                    list.add(
                        RecordItem(
                            id = obj.getInt("Id_Record"),
                            noTractor = obj.getString("No_Tractor_Record"),
                            nameTractor = tractor,
                            comparison = comparison,
                            codePart = part,
                            result = obj.getString("Result_Record"),
                            timeRecord = obj.getString("Time_Record")
                        )
                    )
                }
                records = list
            } catch (e: Exception) {
                Log.e("RecordList", "Error: ${e.message}")
            } finally {
                isLoading = false
                isRefreshing = false
            }
        }
    }

    LaunchedEffect(Unit) {
        fetchRecords()
        // 🔥 Pastikan tombol back dari list selalu ke dashboard
        navController.popBackStack("dashboard", inclusive = false)
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("Ring Synchronizer", color = Color.White) },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary
                )
            )
        }
    ) { padding ->
        if (isLoading) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
            }
        } else {
            val horizontalScroll = rememberScrollState()

            SwipeRefresh(
                state = rememberSwipeRefreshState(isRefreshing),
                onRefresh = {
                    isRefreshing = true
                    fetchRecords()
                }
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding)
                        .padding(12.dp)
                        .horizontalScroll(horizontalScroll)
                ) {
                    // 🔹 Tombol Scan di atas tabel
                    Button(
                        onClick = { navController.navigate("ring_synchronizer") },
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
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .background(MaterialTheme.colorScheme.primaryContainer, RoundedCornerShape(4.dp))
                            .padding(vertical = 8.dp)
                    ) {
                        HeaderText("No", 30.dp)
                        HeaderText("No Tractor", 60.dp)
                        HeaderText("Name Tractor", 80.dp)
                        HeaderText("Comparison", 90.dp)
                        HeaderText("Part Detection", 90.dp)
                        HeaderText("Result", 60.dp)
                        HeaderText("Time Record", 100.dp)
                    }

                    Divider(color = MaterialTheme.colorScheme.secondary.copy(alpha = 0.4f))

                    // 🔹 Tabel scroll vertikal
                    LazyColumn {
                        itemsIndexed(records) { index, record ->
                            val rowBg = if (index % 2 == 0)
                                MaterialTheme.colorScheme.surface
                            else
                                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f)

                            Row(
                                modifier = Modifier
                                    .background(rowBg)
                                    .padding(vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                CellText("${index + 1}", 30.dp)
                                CellText(record.noTractor, 60.dp)
                                CellText(record.nameTractor, 80.dp)
                                CellText(record.comparison, 90.dp)
                                CellText(record.codePart, 90.dp)
                                ResultBadge(record.result, 60.dp)
                                CellText(record.timeRecord, 100.dp)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun HeaderText(text: String, width: Dp) {
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
fun CellText(text: String, width: Dp) {
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
fun ResultBadge(result: String, width: Dp) {
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
