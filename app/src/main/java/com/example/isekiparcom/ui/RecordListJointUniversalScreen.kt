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
import org.json.JSONObject

data class RecordJointItem(
    val id: Int,
    val noTractor: String,
    val modelNamePlan: String,
    val textRecord: String,
    val predictRecord: String,
    val result: String,
    val timeRecord: String
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecordListJointUniversalScreen(navController: NavHostController) {
    val client = remember { OkHttpClient() }
    var records by remember { mutableStateOf<List<RecordJointItem>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var isRefreshing by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    fun fetchRecords() {
        scope.launch(Dispatchers.IO) {
            try {
                val url = "http://192.168.173.201/iseki_parcom/public/api/joint-universal/index"
                val request = Request.Builder().url(url).build()
                val response = client.newCall(request).execute()

                if (!response.isSuccessful) {
                    Log.e("RecordList", "API request failed: ${response.code}")
                    return@launch
                }

                val body = response.body?.string() ?: "[]"
                Log.d("RecordList", "Raw API response: $body")

                val jsonArr = JSONArray(body)
                val list = mutableListOf<RecordJointItem>()

                for (i in 0 until jsonArr.length()) {
                    val obj = jsonArr.getJSONObject(i)

                    list.add(
                        RecordJointItem(
                            id = obj.optInt("Id_Record"),
                            noTractor = obj.optString("No_Tractor_Record"),
                            modelNamePlan = obj.optString("tractor_name"),
                            textRecord = obj.optString("Text_Record", "-"),
                            predictRecord = obj.optString("Predict_Record", "-"),
                            result = obj.optString("Result_Record", "-"),
                            timeRecord = obj.optString("Time_Record", "-")
                        )
                    )
                }
                
                withContext(Dispatchers.Main) {
                    records = list
                }
            } catch (e: org.json.JSONException) {
                Log.e("RecordList", "JSON parsing error: ${e.message}", e)
                withContext(Dispatchers.Main) {
                    records = emptyList()
                }
            } catch (e: Exception) {
                Log.e("RecordList", "General error: ${e.message}", e)
                withContext(Dispatchers.Main) {
                    records = emptyList()
                }
            } finally {
                withContext(Dispatchers.Main) {
                    isLoading = false
                    isRefreshing = false
                }
            }
        }
    }

    LaunchedEffect(Unit) {
        fetchRecords()
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("Joint Universal", color = MaterialTheme.colorScheme.onPrimary) },
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
                    Button(
                        onClick = { navController.navigate("joint_universal") },
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

                    Row(
                        Modifier
                            .fillMaxWidth()
                            .background(
                                MaterialTheme.colorScheme.primaryContainer,
                                RoundedCornerShape(4.dp)
                            )
                            .padding(vertical = 8.dp)
                    ) {
                        HeaderText("No", 30.dp)
                        HeaderText("Sequence No", 80.dp)
                        HeaderText("Name Tractor", 90.dp)
                        HeaderText("Text", 90.dp)
                        HeaderText("Predict", 90.dp)
                        HeaderText("Result", 60.dp)
                        HeaderText("Time", 100.dp)
                    }

                    HorizontalDivider(color = MaterialTheme.colorScheme.secondary.copy(alpha = 0.4f))

                    if (records.isEmpty()) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
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
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            itemsIndexed(records) { index, record ->
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
                                    CellText("${index + 1}", 30.dp)
                                    CellText(record.noTractor, 80.dp)
                                    CellText(record.modelNamePlan, 90.dp)
                                    CellText(record.textRecord, 90.dp)
                                    CellText(record.predictRecord, 90.dp)
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
}
