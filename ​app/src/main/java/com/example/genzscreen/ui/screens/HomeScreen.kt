package com.example.genzscreen.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.example.genzscreen.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(navController: NavController) {
    var showBottomSheet by remember { mutableStateOf(false) }

    // In a real app, this data comes from Room Database via ViewModel (Starts at 0)
    val totalReelsToday = 0
    val appUsageMap = emptyMap<String, Int>() // e.g. mapOf("Facebook" to 0)

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("GenZscreen", fontWeight = FontWeight.Bold, fontSize = 22.sp)
                        Text(
                            "Control your reels & video watching",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                actions = {
                    IconButton(onClick = { navController.navigate("about") }) {
                        Icon(Icons.Default.Info, contentDescription = "About")
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(24.dp))

            // Clean Responsive Donut Chart
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(200.dp)
                    .clickable { navController.navigate("details") } // Tapping center goes to Page 2
            ) {
                DonutChart(usageData = appUsageMap)
                
                // Center Text
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "$totalReelsToday",
                        fontSize = 36.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "Today",
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.height(48.dp))

            // Set Limit Button
            Button(
                onClick = { showBottomSheet = true },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("Set limit for Reels & Videos", fontSize = 16.sp)
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Active Limits Summary (Clean UI)
            Text(
                text = "Active Limits",
                fontWeight = FontWeight.Bold,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(8.dp))
            
            // Empty State (Because no limits are set initially)
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                elevation = CardDefaults.cardElevation(0.dp)
            ) {
                PaddingValues(16.dp)
                Text(
                    text = "No limits set. Tap the button above to add a limit.",
                    modifier = Modifier.padding(16.dp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 14.sp
                )
            }
        }

        // Bottom Sheet for Apps List
        if (showBottomSheet) {
            ModalBottomSheet(
                onDismissRequest = { showBottomSheet = false },
                containerColor = MaterialTheme.colorScheme.surface
            ) {
                Column(modifier = Modifier.padding(bottom = 32.dp)) {
                    Text(
                        text = "Select App to set limit",
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(16.dp),
                        fontSize = 18.sp
                    )
                    
                    val apps = listOf("Facebook", "YouTube", "Instagram", "TikTok")
                    apps.forEach { appName ->
                        ListItem(
                            headlineContent = { Text(appName) },
                            modifier = Modifier.clickable {
                                showBottomSheet = false
                                navController.navigate("app_details/$appName") // Navigates to Page 3
                            }
                        )
                        Divider(color = MaterialTheme.colorScheme.surfaceVariant)
                    }
                }
            }
        }
    }
}

@Composable
fun DonutChart(usageData: Map<String, Int>) {
    val total = usageData.values.sum().coerceAtLeast(1) // Prevent division by zero
    
    val colors = mapOf(
        "Facebook" to FacebookColor,
        "YouTube" to YouTubeColor,
        "Instagram" to InstagramColor,
        "TikTok" to TikTokColor
    )

    Canvas(modifier = Modifier.fillMaxSize()) {
        val strokeWidth = 40f
        
        if (usageData.isEmpty() || usageData.values.all { it == 0 }) {
            // Empty State: Draw a simple grey circle
            drawArc(
                color = BorderLight,
                startAngle = 0f,
                sweepAngle = 360f,
                useCenter = false,
                style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
            )
        } else {
            var startAngle = -90f
            usageData.forEach { (app, count) ->
                if (count > 0) {
                    val sweepAngle = (count.toFloat() / total) * 360f
                    drawArc(
                        color = colors[app] ?: Color.Gray,
                        startAngle = startAngle,
                        sweepAngle = sweepAngle,
                        useCenter = false,
                        style = Stroke(width = strokeWidth, cap = StrokeCap.Butt)
                    )
                    startAngle += sweepAngle
                }
            }
        }
    }
}
