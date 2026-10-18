package com.example.genzscreen

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.text.TextUtils
import android.widget.Toast
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
import com.example.genzscreen.service.ReelAccessibilityService
import com.example.genzscreen.ui.screens.*
import com.example.genzscreen.ui.theme.GenZscreenTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // অ্যাপ চালু হওয়ার সাথে সাথে পারমিশন চেক করবে
        checkAndRequestPermissions()

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

    override fun onResume() {
        super.onResume()
        // ব্যবহারকারী সেটিং থেকে ফিরে আসলে আবার চেক করবে
        checkAndRequestPermissions()
    }

    private fun checkAndRequestPermissions() {
        // ১. ওভারলে (Display over other apps) পারমিশন চেক
        if (!Settings.canDrawOverlays(this)) {
            Toast.makeText(this, "Please allow 'Display over other apps' permission", Toast.LENGTH_LONG).show()
            val intent = Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:$packageName")
            )
            startActivity(intent)
            return
        }

        // ২. অ্যাক্সেসিবিলিটি (Accessibility) পারমিশন চেক
        if (!isAccessibilityServiceEnabled(this, ReelAccessibilityService::class.java)) {
            Toast.makeText(this, "Please enable 'GenZscreen' Accessibility Service", Toast.LENGTH_LONG).show()
            val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
            startActivity(intent)
            return
        }
    }

    // অ্যাক্সেসিবিলিটি চালু আছে কিনা চেক করার লজিক
    private fun isAccessibilityServiceEnabled(context: Context, service: Class<*>): Boolean {
        val expectedComponentName = ComponentName(context, service)
        val enabledServicesSetting = Settings.Secure.getString(
            context.contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        )
        if (enabledServicesSetting == null) return false
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
