package com.example.genzscreen

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.text.TextUtils
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.genzscreen.service.ReelAccessibilityService
import com.example.genzscreen.ui.screens.*
import com.example.genzscreen.ui.theme.GenZscreenTheme

class MainActivity : ComponentActivity() {

    private var hasOverlayPermission by mutableStateOf(false)
    private var hasAccessibilityPermission by mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        updatePermissionStates()

        setContent {
            GenZscreenTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    // যদি কোনো পারমিশন বাকি থাকে, তবে প্রথমে সুন্দর এক্সপ্লেনেশন ডায়ালগ দেখাবে
                    if (!hasOverlayPermission || !hasAccessibilityPermission) {
                        PermissionExplanationDialog(
                            missingOverlay = !hasOverlayPermission,
                            missingAccessibility = !hasAccessibilityPermission,
                            onGrantClick = { requestNextPermission() }
                        )
                    }

                    GenZAppNavigation()
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        updatePermissionStates()
    }

    private fun updatePermissionStates() {
        hasOverlayPermission = Settings.canDrawOverlays(this)
        hasAccessibilityPermission = isAccessibilityServiceEnabled(this, ReelAccessibilityService::class.java)
    }

    private fun requestNextPermission() {
        if (!hasOverlayPermission) {
            val intent = Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:$packageName")
            )
            startActivity(intent)
        } else if (!hasAccessibilityPermission) {
            val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
            startActivity(intent)
        }
    }

    private fun isAccessibilityServiceEnabled(context: Context, service: Class<*>): Boolean {
        val expectedComponentName = ComponentName(context, service)
        val enabledServicesSetting = Settings.Secure.getString(
            context.contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ) ?: return false

        val colonSplitter = TextUtils.SimpleStringSplitter(':')
        colonSplitter.setString(enabledServicesSetting)
        while (colonSplitter.hasNext()) {
            val componentNameString = colonSplitter.next()
            val enabledService = ComponentName.unflattenFromString(componentNameString)
            if (enabledService != null && enabledService == expectedComponentName) {
                return true
            }
        }
        return false
    }
}

// ডিজিটাল ওয়েলবিং স্টাইলের পারমিশন এক্সপ্লেনেশন ডায়ালগ
@Composable
fun PermissionExplanationDialog(
    missingOverlay: Boolean,
    missingAccessibility: Boolean,
    onGrantClick: () -> Unit
) {
    AlertDialog(
        onDismissRequest = { /* বাধ্যতামূলক পারমিশন */ },
        title = {
            Text(
                text = "Permissions Required",
                fontWeight = FontWeight.Bold,
                fontSize = 20.sp
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = "GenZscreen needs the following permissions to help you control your reels and video addiction:",
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                if (missingOverlay) {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text(
                                text = "1. Display Over Other Apps",
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp
                            )
                            Text(
                                text = "Required to show the floating counter box on top of reels so you can track your time.",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                if (missingAccessibility) {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text(
                                text = "2. Accessibility Service",
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp
                            )
                            Text(
                                text = "Required to detect when a reel starts playing in Facebook, YouTube, TikTok, or Instagram and enforce your limits.",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = onGrantClick) {
                Text("Grant Permission")
            }
        }
    )
}

@Composable
fun GenZAppNavigation() {
    val navController = rememberNavController()

    NavHost(navController = navController, startDestination = "home") {
        composable("home") { HomeScreen(navController) }
        composable("about") { AboutScreen(navController) }
        composable("details") { OverallDetailsScreen(navController) }
        composable("app_details/{packageName}") { backStackEntry ->
            val pkgName = backStackEntry.arguments?.getString("packageName") ?: ""
            AppSpecificScreen(navController, pkgName)
        }
    }
}
