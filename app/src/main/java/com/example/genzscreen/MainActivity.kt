package com.example.genzscreen

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.genzscreen.ui.theme.GenZscreenTheme
import com.example.genzscreen.ui.screens.*

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            GenZscreenTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    GenZAppNavigation()
                }
            }
        }
    }
}

@Composable
fun GenZAppNavigation() {
    val navController = rememberNavController()

    NavHost(navController = navController, startDestination = "home") {
        composable("home") { HomeScreen(navController) }
        composable("about") { AboutScreen(navController) }
        composable("details") { 
            // Page 2: Overall Details (Will be in Part 4)
            OverallDetailsScreen(navController) 
        }
        composable("app_details/{packageName}") { backStackEntry ->
            // Page 3: Specific App Details (Will be in Part 4)
            val pkgName = backStackEntry.arguments?.getString("packageName") ?: ""
            AppSpecificScreen(navController, pkgName)
        }
    }
}
