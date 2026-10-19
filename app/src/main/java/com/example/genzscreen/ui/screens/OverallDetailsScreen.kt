package com.example.genzscreen.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.example.genzscreen.data.GenZDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OverallDetailsScreen(navController: NavController) {
    var isReelsActive by remember { mutableStateOf(true) }
    var weekOffset by remember { mutableStateOf(0) }

    var dailyCount by remember { mutableStateOf(0) }
    var dailyTimeStr by remember { mutableStateOf("0 hr 0 min") }
    var appRankingList by remember { mutableStateOf(emptyList<Pair<String, Int>>()) }
    val context = LocalContext.current

    LaunchedEffect(isReelsActive, weekOffset) {
        withContext(Dispatchers.IO) {
            val db = GenZDatabase.getDatabase(context)
            val date = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
            db.usageDao().getUsageForDate(date).collect { list ->
                var totalCount = 0
                var totalTime = 0L
                val rankingMap = mutableMapOf<String, Int>()

                list.forEach { usage ->
                    val appName = when(usage.packageName) {
                        "com.facebook.katana" -> "Facebook"
                        "com.google.android.youtube" -> "YouTube"
                        "com.instagram.android" -> "Instagram"
                        "com.zhiliaoapp.musically" -> "TikTok"
                        else -> "Unknown"
                    }
                    totalCount += usage.reelCount
                    totalTime += usage.watchTimeMillis
                    
                    val value = if (isReelsActive) usage.reelCount else (usage.watchTimeMillis / 60000).toInt()
                    rankingMap[appName] = value
                }

                val mins = (totalTime / 60000) % 60
                val hrs = (totalTime / 3600000)
                
                withContext(Dispatchers.Main) {
                    dailyCount = totalCount
                    dailyTimeStr = if (hrs > 0) "$hrs hr $mins min" else "$mins min"
                    appRankingList = rankingMap.toList().sortedByDescending { it.second }.filter { it.second > 0 }
                }
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Reels & Video Details", fontSize = 18.sp, fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp)) {
            Row(modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp), horizontalArrangement = Arrangement.Center) {
                SegmentedToggleButton(isActive = isReelsActive, leftText = "Reels & Video", rightText = "Time Watch", onToggle = { isReelsActive = it })
            }

            Text(
                text = if (isReelsActive) "$dailyCount reels & video watch" else dailyTimeStr,
                fontSize = 28.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(vertical = 8.dp)
            )

            Row(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { weekOffset-- }) { Icon(Icons.Default.KeyboardArrowLeft, contentDescription = "Previous") }
                Text(text = if (weekOffset == 0) "Current Week" else "Previous Week", fontWeight = FontWeight.Medium)
                if (weekOffset < 0) {
                    IconButton(onClick = { weekOffset++ }) { Icon(Icons.Default.KeyboardArrowRight, contentDescription = "Next") }
                } else { Spacer(modifier = Modifier.size(48.dp)) }
            }

            Card(modifier = Modifier.fillMaxWidth().height(200.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))) {
                WeeklyBarChart(average = 0f, offset = weekOffset)
            }

            Spacer(modifier = Modifier.height(24.dp))
            Text("App Usage Ranking", fontWeight = FontWeight.Bold, fontSize = 16.sp)
            Spacer(modifier = Modifier.height(8.dp))

            if (appRankingList.isEmpty()) {
                Text("No usage recorded yet.", color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 16.dp))
            } else {
                LazyColumn {
                    items(appRankingList.size) { index ->
                        val item = appRankingList[index]
                        Row(modifier = Modifier.fillMaxWidth().clickable { navController.navigate("app_details/${item.first}") }.padding(vertical = 12.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(text = item.first, fontWeight = FontWeight.Medium)
                            Text(text = "${item.second} ${if (isReelsActive) "reels" else "mins"}")
                        }
                        Divider(color = MaterialTheme.colorScheme.surfaceVariant)
                    }
                }
            }
        }
    }
}

@Composable
fun SegmentedToggleButton(isActive: Boolean, leftText: String, rightText: String, onToggle: (Boolean) -> Unit) {
    Row(modifier = Modifier.background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(24.dp))) {
        Button(onClick = { onToggle(true) }, colors = ButtonDefaults.buttonColors(containerColor = if (isActive) MaterialTheme.colorScheme.primary else Color.Transparent, contentColor = if (isActive) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant), elevation = ButtonDefaults.buttonElevation(0.dp), shape = RoundedCornerShape(topStart = 24.dp, bottomStart = 24.dp)) { Text(leftText) }
        Button(onClick = { onToggle(false) }, colors = ButtonDefaults.buttonColors(containerColor = if (!isActive) MaterialTheme.colorScheme.primary else Color.Transparent, contentColor = if (!isActive) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant), elevation = ButtonDefaults.buttonElevation(0.dp), shape = RoundedCornerShape(topEnd = 24.dp, bottomEnd = 24.dp)) { Text(rightText) }
    }
}

@Composable
fun WeeklyBarChart(average: Float, offset: Int) {
    val dashEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 10f), 0f)
    Canvas(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        val width = size.width
        val height = size.height
        val barWidth = width / 14f
        for (i in 0 until 7) {
            val x = (i * 2 * barWidth) + barWidth / 2f
            drawRoundRect(color = Color(0xFF1A73E8).copy(alpha = 0.2f), topLeft = Offset(x, height - 10f), size = Size(barWidth, 10f), cornerRadius = CornerRadius(4.dp.toPx()))
        }
    }
}
