package com.example.isekiparcom

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.Composable
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.isekiparcom.ui.BearingKbcScreen
import com.example.isekiparcom.ui.BearingKoyoScreen
import com.example.isekiparcom.ui.DashboardScreen
import com.example.isekiparcom.ui.RecordListBearingKbcScreen
import com.example.isekiparcom.ui.RecordListBearingKoyoScreen
import com.example.isekiparcom.ui.RecordListRingSynchronizerScreen
import com.example.isekiparcom.ui.RingSynchronizerScreen
import com.example.isekiparcom.ui.theme.IsekiParcomTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            IsekiParcomTheme {
                IsekiParcomApp()
            }
        }
    }
}

@Composable
fun IsekiParcomApp() {
    val navController = rememberNavController()
    NavHost(navController = navController, startDestination = "dashboard") {
        composable("dashboard") { DashboardScreen(navController) }
        composable("ring_synchronizer") { RingSynchronizerScreen(navController) }
        composable("record_list_ring") { RecordListRingSynchronizerScreen(navController) }
        composable("bearing_kbc") { BearingKbcScreen(navController) }
        composable("record_list_bearing_kbc") { RecordListBearingKbcScreen(navController) }
        composable("bearing_koyo") { BearingKoyoScreen(navController) }
        composable("record_list_bearing_koyo") { RecordListBearingKoyoScreen(navController) }
    }

}