package com.example.isekiparcom.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.navigation.NavHostController
import com.example.isekiparcom.ui.components.VideoBackground


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(navController: NavHostController) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    // 🔥 Ganti struktur utama dengan Box untuk mengatur lapisan
    Box(
        modifier = Modifier.fillMaxSize()
    ) {
        // 1️⃣ Video Background (paling bawah)
        VideoBackground(
            modifier = Modifier
                .fillMaxSize()
                .zIndex(0f)
        )

        // 2️⃣ Content UI (di atas video)
        // Bungkus seluruh Scaffold dalam Box lain agar tidak mengganggu lapisan video
        Box(
            modifier = Modifier
                .fillMaxSize()
                .zIndex(1f)
        ) {
            Scaffold(
                containerColor = Color.Transparent,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                topBar = {
                    CenterAlignedTopAppBar(
                        title = {
                            Text(
                                "Iseki Parcom",
                                color = MaterialTheme.colorScheme.onPrimary
                            )
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
                        .verticalScroll(rememberScrollState())
                        .padding(padding)
                        .padding(horizontal = 20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Top
                ) {
                    Spacer(modifier = Modifier.height(24.dp))

                    // 🔹 Subtitle di bawah TopBar
                    Box(
                        contentAlignment = Alignment.Center
                    ) {
                        // 🔻 Layer Blur Background
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(12.dp))
                                .background(Color.White.copy(alpha = 0.6f))
                                .blur(10.dp)
                                .padding(horizontal = 16.dp, vertical = 8.dp)
                                .matchParentSize()    // pastikan ukurannya sama dengan teks
                        )

                        // 🔺 Layer Teks (tidak di-blur)
                        Text(
                            text = "Iseki Part Comparator",
                            color = MaterialTheme.colorScheme.secondary,
                            style = MaterialTheme.typography.titleLarge.copy(
                                fontWeight = FontWeight.Bold
                            ),
                            modifier = Modifier
                                .padding(horizontal = 16.dp, vertical = 8.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(32.dp))

                    // 🔹 Kartu untuk Ring Synchronizer
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 12.dp),
                        shape = MaterialTheme.shapes.extraLarge,
                        colors = CardDefaults.cardColors(
                            containerColor = Color.White.copy(alpha = 0.6f),
                            contentColor = MaterialTheme.colorScheme.primary,
                            disabledContainerColor = Color.Transparent,
                            disabledContentColor = Color.Transparent
                        ),
                        elevation = CardDefaults.cardElevation(0.dp)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = "Ring Synchronizer",
                                style = MaterialTheme.typography.headlineSmall.copy(
                                    color = MaterialTheme.colorScheme.secondary,
                                    fontWeight = FontWeight.Bold
                                )
                            )

                            Spacer(modifier = Modifier.height(24.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceEvenly
                            ) {
                                // 🔸 Tombol List
                                Button(
                                    onClick = { navController.navigate("record_list_ring") },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = MaterialTheme.colorScheme.primary
                                    ),
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(50.dp)
                                ) {
                                    Text("List", color = Color.White)
                                }

                                Spacer(modifier = Modifier.width(16.dp))

                                // 🔸 Tombol Scan
                                Button(
                                    onClick = { navController.navigate("ring_synchronizer") },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = MaterialTheme.colorScheme.secondary
                                    ),
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(50.dp)
                                ) {
                                    Text("Scan", color = Color.White)
                                }
                            }
                        }
                    }

//                    // 🔹 Kartu untuk Bearing KBC (baru)
//                    Card(
//                        modifier = Modifier
//                            .fillMaxWidth()
//                            .padding(vertical = 12.dp),
//                        shape = MaterialTheme.shapes.extraLarge,
//                        colors = CardDefaults.cardColors(
//                            containerColor = Color.White.copy(alpha = 0.6f),
//                            contentColor = MaterialTheme.colorScheme.primary,
//                            disabledContainerColor = Color.Transparent,
//                            disabledContentColor = Color.Transparent
//                        ),
//                        elevation = CardDefaults.cardElevation(0.dp)
//                    ) {
//                        Column(
//                            modifier = Modifier
//                                .fillMaxWidth()
//                                .padding(24.dp),
//                            horizontalAlignment = Alignment.CenterHorizontally
//                        ) {
//                            Text(
//                                text = "Bearing KBC",
//                                style = MaterialTheme.typography.headlineSmall.copy(
//                                    color = MaterialTheme.colorScheme.secondary,
//                                    fontWeight = FontWeight.Bold
//                                )
//                            )
//
//                            Spacer(modifier = Modifier.height(24.dp))
//
//                            Row(
//                                modifier = Modifier.fillMaxWidth(),
//                                horizontalArrangement = Arrangement.SpaceEvenly
//                            ) {
//                                // 🔸 Tombol List Bearing KBC
//                                Button(
//                                    onClick = { navController.navigate("record_list_bearing_kbc") },
//                                    colors = ButtonDefaults.buttonColors(
//                                        containerColor = MaterialTheme.colorScheme.primary
//                                    ),
//                                    modifier = Modifier
//                                        .weight(1f)
//                                        .height(50.dp)
//                                ) {
//                                    Text("List", color = Color.White)
//                                }
//
//                                Spacer(modifier = Modifier.width(16.dp))
//
//                                // 🔸 Tombol Scan Bearing KBC
//                                Button(
//                                    onClick = { navController.navigate("bearing_kbc") },
//                                    colors = ButtonDefaults.buttonColors(
//                                        containerColor = MaterialTheme.colorScheme.secondary
//                                    ),
//                                    modifier = Modifier
//                                        .weight(1f)
//                                        .height(50.dp)
//                                ) {
//                                    Text("Scan", color = Color.White)
//                                }
//                            }
//                        }
//                    }
//
//                    // 🔹 Kartu untuk Bearing KOYO (baru)
//                    Card(
//                        modifier = Modifier
//                            .fillMaxWidth()
//                            .padding(vertical = 12.dp),
//                        shape = MaterialTheme.shapes.extraLarge,
//                        colors = CardDefaults.cardColors(
//                            containerColor = Color.White.copy(alpha = 0.6f),
//                            contentColor = MaterialTheme.colorScheme.primary,
//                            disabledContainerColor = Color.Transparent,
//                            disabledContentColor = Color.Transparent
//                        ),
//                        elevation = CardDefaults.cardElevation(0.dp)
//                    ) {
//                        Column(
//                            modifier = Modifier
//                                .fillMaxWidth()
//                                .padding(24.dp),
//                            horizontalAlignment = Alignment.CenterHorizontally
//                        ) {
//                            Text(
//                                text = "Bearing KOYO",
//                                style = MaterialTheme.typography.headlineSmall.copy(
//                                    color = MaterialTheme.colorScheme.secondary,
//                                    fontWeight = FontWeight.Bold
//                                )
//                            )
//
//                            Spacer(modifier = Modifier.height(24.dp))
//
//                            Row(
//                                modifier = Modifier.fillMaxWidth(),
//                                horizontalArrangement = Arrangement.SpaceEvenly
//                            ) {
//                                // 🔸 Tombol List Bearing KOYO
//                                Button(
//                                    onClick = { navController.navigate("record_list_bearing_koyo") },
//                                    colors = ButtonDefaults.buttonColors(
//                                        containerColor = MaterialTheme.colorScheme.primary
//                                    ),
//                                    modifier = Modifier
//                                        .weight(1f)
//                                        .height(50.dp)
//                                ) {
//                                    Text("List", color = Color.White)
//                                }
//
//                                Spacer(modifier = Modifier.width(16.dp))
//
//                                // 🔸 Tombol Scan Bearing KOYO
//                                Button(
//                                    onClick = { navController.navigate("bearing_koyo") },
//                                    colors = ButtonDefaults.buttonColors(
//                                        containerColor = MaterialTheme.colorScheme.secondary
//                                    ),
//                                    modifier = Modifier
//                                        .weight(1f)
//                                        .height(50.dp)
//                                ) {
//                                    Text("Scan", color = Color.White)
//                                }
//                            }
//                        }
//                    }

                    // 🔹 Kartu untuk Bearing Shaft (baru)
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 12.dp),
                        shape = MaterialTheme.shapes.extraLarge,
                        colors = CardDefaults.cardColors(
                            containerColor = Color.White.copy(alpha = 0.6f),
                            contentColor = MaterialTheme.colorScheme.primary,
                            disabledContainerColor = Color.Transparent,
                            disabledContentColor = Color.Transparent
                        ),
                        elevation = CardDefaults.cardElevation(0.dp)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = "Bearing Shaft",
                                style = MaterialTheme.typography.headlineSmall.copy(
                                    color = MaterialTheme.colorScheme.secondary,
                                    fontWeight = FontWeight.Bold
                                )
                            )

                            Spacer(modifier = Modifier.height(24.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceEvenly
                            ) {
                                // 🔸 Tombol List Bearing Shaft
                                Button(
                                    onClick = { navController.navigate("record_list_bearing_kbc") },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = MaterialTheme.colorScheme.primary
                                    ),
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(50.dp)
                                ) {
                                    Text("List", color = Color.White)
                                }

                                Spacer(modifier = Modifier.width(16.dp))

                                // 🔸 Tombol Scan Bearing Shaft
                                Button(
                                    onClick = { navController.navigate("bearing_shaft") },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = MaterialTheme.colorScheme.secondary
                                    ),
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(50.dp)
                                ) {
                                    Text("Scan", color = Color.White)
                                }
                            }
                        }
                    }

                    // 🔹 Kartu untuk Bearing Metal (baru)
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 12.dp),
                        shape = MaterialTheme.shapes.extraLarge,
                        colors = CardDefaults.cardColors(
                            containerColor = Color.White.copy(alpha = 0.6f),
                            contentColor = MaterialTheme.colorScheme.primary,
                            disabledContainerColor = Color.Transparent,
                            disabledContentColor = Color.Transparent
                        ),
                        elevation = CardDefaults.cardElevation(0.dp)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = "Bearing Metal",
                                style = MaterialTheme.typography.headlineSmall.copy(
                                    color = MaterialTheme.colorScheme.secondary,
                                    fontWeight = FontWeight.Bold
                                )
                            )

                            Spacer(modifier = Modifier.height(24.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceEvenly
                            ) {
                                // 🔸 Tombol List Bearing Shaft
                                Button(
                                    onClick = { navController.navigate("record_list_bearing_koyo") },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = MaterialTheme.colorScheme.primary
                                    ),
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(50.dp)
                                ) {
                                    Text("List", color = Color.White)
                                }

                                Spacer(modifier = Modifier.width(16.dp))

                                // 🔸 Tombol Scan Bearing Shaft
                                Button(
                                    onClick = { navController.navigate("bearing_metal") },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = MaterialTheme.colorScheme.secondary
                                    ),
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(50.dp)
                                ) {
                                    Text("Scan", color = Color.White)
                                }
                            }
                        }
                    }

                    // 🔹 Kartu untuk Joint Universal (baru)
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 12.dp),
                        shape = MaterialTheme.shapes.extraLarge,
                        colors = CardDefaults.cardColors(
                            containerColor = Color.White.copy(alpha = 0.6f),
                            contentColor = MaterialTheme.colorScheme.primary,
                            disabledContainerColor = Color.Transparent,
                            disabledContentColor = Color.Transparent
                        ),
                        elevation = CardDefaults.cardElevation(0.dp)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = "Joint Universal",
                                style = MaterialTheme.typography.headlineSmall.copy(
                                    color = MaterialTheme.colorScheme.secondary,
                                    fontWeight = FontWeight.Bold
                                )
                            )

                            Spacer(modifier = Modifier.height(24.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceEvenly
                            ) {
                                // 🔸 Tombol List Joint Universal
                                Button(
                                    onClick = { navController.navigate("record_list_joint") },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = MaterialTheme.colorScheme.primary
                                    ),
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(50.dp)
                                ) {
                                    Text("List", color = Color.White)
                                }

                                Spacer(modifier = Modifier.width(16.dp))

                                // 🔸 Tombol Scan Joint Universal
                                Button(
                                    onClick = { navController.navigate("joint_universal") },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = MaterialTheme.colorScheme.secondary
                                    ),
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(50.dp)
                                ) {
                                    Text("Scan", color = Color.White)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}