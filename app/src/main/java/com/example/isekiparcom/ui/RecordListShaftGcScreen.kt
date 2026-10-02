package com.example.isekiparcom.ui

import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
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

data class ShaftGcRecordItem(
    val id: Int,
    val noTractor: String,
    val nameTractor: String,
    val comparison: String,
    val codePart: String,
    val result: String,
    val timeRecord: String,
    val textRecord: String?,
    val predictRecord: String?
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecordListShaftGcScreen(navController: NavHostController) {
    val client = remember { OkHttpClient() }
    var records by remember { mutableStateOf<List<ShaftGcRecordItem>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var isRefreshing by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    fun fetchRecords() {
        scope.launch(Dispatchers.IO) {
            try {
                val url = "http://192.168.173.201/iseki_parcom/public/api/shaft-gc/index"
                val request = Request.Builder().url(url).build()
                val response = client.newCall(request).execute()

                if (!response.isSuccessful) {
                    Log.e("RecordList", "API request failed: ${response.code}")
                    return@launch
                }

                val body = response.body?.string() ?: "[]"
                Log.d("RecordList", "Raw API response: $body")

                val jsonArr = JSONArray(body)
                val list = mutableListOf<ShaftGcRecordItem>()

                for (i in 0 until jsonArr.length()) {
                    val obj = jsonArr.getJSONObject(i)

                    val comparisonObj = obj.optJSONObject("comparison")
                    val tractorObj = obj.optJSONObject("tractor")
                    val partObj = obj.optJSONObject("part")

                    val comparisonName = comparisonObj?.optString("Name_Comparison") ?: ""
                    val tractorType = tractorObj?.optString("Type_Tractor") ?: ""
                    val partCode = partObj?.optString("Code_Part") ?: ""

                    val textRec = obj.optString("Text_Record")
                    val predictRec = obj.optString("Predict_Record")

                    list.add(
                        ShaftGcRecordItem(
                            id = obj.optInt("Id_Record"),
                            noTractor = obj.optString("No_Tractor_Record"),
                            nameTractor = obj.optString("tractor_name"),
                            comparison = comparisonName,
                            codePart = partCode,
                            result = obj.optString("Result_Record"),
                            timeRecord = obj.optString("Time_Record"),
                            textRecord = if(textRec == "null") null else textRec,
                            predictRecord = if(predictRec == "null") null else predictRec
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
                title = { Text("Shaft GC", color = MaterialTheme.colorScheme.onPrimary) },
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
        }
    ) { padding ->
        if (isLoading) {
            Box(
                Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        } else {
            SwipeRefresh(
                state = rememberSwipeRefreshState(isRefreshing = isRefreshing),
                onRefresh = {
                    isRefreshing = true
                    fetchRecords()
                },
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
            ) {
                if (records.isEmpty()) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("Tidak ada data record.", fontSize = 16.sp)
                    }
                } else {
                    Column(Modifier.fillMaxSize()) {
                        val scrollState = rememberScrollState()
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .background(MaterialTheme.colorScheme.surfaceVariant)
                                .horizontalScroll(scrollState)
                                .padding(vertical = 12.dp)
                        ) {
                            Spacer(Modifier.width(16.dp))
                            HeaderCell("No", 40.dp)
                            HeaderCell("Sequence No", 120.dp)
                            HeaderCell("Expected Text", 120.dp)
                            HeaderCell("Prediction", 120.dp)
                            HeaderCell("Result", 100.dp)
                            HeaderCell("Time Record", 160.dp)
                            Spacer(Modifier.width(16.dp))
                        }
                        Divider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f))

                        LazyColumn(
                            modifier = Modifier
                                .fillMaxSize()
                                .horizontalScroll(scrollState)
                        ) {
                            itemsIndexed(records) { index, item ->
                                val bgColor = if (index % 2 == 0) Color.Transparent else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(bgColor)
                                        .padding(vertical = 12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Spacer(Modifier.width(16.dp))
                                    DataCell((index + 1).toString(), 40.dp)
                                    DataCell(item.noTractor, 120.dp)
                                    DataCell(item.textRecord ?: "-", 120.dp)
                                    DataCell(item.predictRecord ?: "-", 120.dp)
                                    ResultCell(item.result, 100.dp)
                                    DataCell(item.timeRecord, 160.dp)
                                    Spacer(Modifier.width(16.dp))
                                }
                                Divider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f))
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun HeaderCell(text: String, width: Dp) {
    Text(
        text = text,
        modifier = Modifier.width(width).padding(end = 8.dp),
        fontWeight = FontWeight.Bold,
        fontSize = 14.sp,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
}

@Composable
private fun DataCell(text: String, width: Dp) {
    Text(
        text = text,
        modifier = Modifier.width(width).padding(end = 8.dp),
        fontSize = 14.sp,
        color = MaterialTheme.colorScheme.onSurface
    )
}

@Composable
private fun ResultCell(result: String, width: Dp) {
    val (bgColor, textColor) = when (result.uppercase()) {
        "OK" -> Color(0xFFE8F5E9) to Color(0xFF2E7D32)
        "NG" -> Color(0xFFFFEBEE) to Color(0xFFC62828)
        "NG-OK" -> Color(0xFFFFF3E0) to Color(0xFFEF6C00)
        else -> MaterialTheme.colorScheme.surfaceVariant to MaterialTheme.colorScheme.onSurfaceVariant
    }

    Box(
        modifier = Modifier
            .width(width - 8.dp)
            .background(bgColor, RoundedCornerShape(4.dp))
            .padding(vertical = 4.dp, horizontal = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = result,
            color = textColor,
            fontWeight = FontWeight.Bold,
            fontSize = 12.sp,
            textAlign = TextAlign.Center
        )
    }
    Spacer(modifier = Modifier.width(8.dp))
}
