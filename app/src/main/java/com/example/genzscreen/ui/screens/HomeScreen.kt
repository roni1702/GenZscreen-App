package com.example.genzscreen.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.example.genzscreen.data.AppLimit
import com.example.genzscreen.data.GenZDatabase
import com.example.genzscreen.ui.theme.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(navController: NavController) {
    var showBottomSheet by remember { mutableStateOf(false) }

    // লাইভ ডেটাবেজ স্টেট
    var totalReelsToday by remember { mutableStateOf(0) }
    var appUsageMap by remember { mutableStateOf(emptyMap<String, Int>()) }
    var activeLimitsList by remember { mutableStateOf(emptyList<AppLimit>()) }

    val context = LocalContext.current

    // ডেটাবেজ থেকে রিয়েলটাইমে আজকের ইউসেজ এবং সেট করা সব লিমিট আনা
    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            val db = GenZDatabase.getDatabase(context)
            val date = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())

            // ইউসেজ পর্যবেক্ষণ
            db.usageDao().getUsageForDate(date).collect { usageList ->
                var total = 0
                val map = mutableMapOf<String, Int>()
                usageList.forEach {
                    total += it.reelCount
                    val appName = when (it.packageName) {
                        "com.facebook.katana" -> "Facebook"
                        "com.google.android.youtube" -> "YouTube"
                        "com.instagram.android" -> "Instagram"
                        "com.zhiliaoapp.musically" -> "TikTok"
                        else -> "Unknown"
                    }
                    if (it.reelCount > 0) {
                        map[appName] = it.reelCount
                    }
                }
                withContext(Dispatchers.Main) {
                    totalReelsToday = total
                    appUsageMap = map
                }
            }
        }
    }

    // অ্যাক্টিভ লিমিটের রিয়েলটাইম লিস্ট পর্যবেক্ষণ
    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            val db = GenZDatabase.getDatabase(context)
            db.usageDao().getAllLimits().collect { limits ->
                withContext(Dispatchers.Main) {
                    activeLimitsList = limits
                }
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.PlayCircle,
                            contentDescription = "Logo",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(28.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text("GenZscreen", fontWeight = FontWeight.Bold, fontSize = 20.sp)
                            Text(
                                "Control your reels & video watching",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
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
                .padding(horizontal = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(20.dp))

            // ডোনাট চার্ট (মাঝখানে ট্যাপ করলে Page 2 তে নিয়ে যাবে)
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(210.dp)
                    .clickable { navController.navigate("details") }
            ) {
                DonutChart(usageData = appUsageMap)

                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "$totalReelsToday",
                        fontSize = 38.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "Today",
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "Tap for details",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.8f),
                        modifier = Modifier.padding(top = 2.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // চার্টের লেজেন্ড (Legend): কোন অ্যাপ কতটুকু ও তার কালার
            AppUsageLegend(usageData = appUsageMap)

            Spacer(modifier = Modifier.height(28.dp))

            // লিমিট সেট করার মূল বাটন
            Button(
                onClick = { showBottomSheet = true },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("Set limit for Reels & Videos", fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
            }

            Spacer(modifier = Modifier.height(24.dp))

            // অ্যাক্টিভ লিমিট সামারি কার্ড
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Active Limit Summaries",
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp
                )
                if (activeLimitsList.isNotEmpty()) {
                    Text(
                        text = "${activeLimitsList.size} active",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Medium
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // লাইভ লিমিট লিস্ট (যদি কোনো লিমিট না থাকে তবে এম্পটি কার্ড)
            if (activeLimitsList.isEmpty()) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text(
                        text = "No limits set. Tap the button above to set your first reel limit.",
                        modifier = Modifier.padding(16.dp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 13.sp
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(activeLimitsList) { limit ->
                        ActiveLimitCard(limit = limit, onClick = {
                            val appName = getAppNameFromPackage(limit.packageName)
                            navController.navigate("app_details/$appName")
                        })
                    }
                }
            }
        }

        // অ্যাপ লিস্ট বটম শিট
        if (showBottomSheet) {
            ModalBottomSheet(
                onDismissRequest = { showBottomSheet = false },
                containerColor = MaterialTheme.colorScheme.surface
            ) {
                Column(modifier = Modifier.padding(bottom = 32.dp)) {
                    Text(
                        text = "Select App to set limit",
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
                        fontSize = 18.sp
                    )

                    val apps = listOf(
                        Triple("Facebook", FacebookColor, "com.facebook.katana"),
                        Triple("YouTube", YouTubeColor, "com.google.android.youtube"),
                        Triple("Instagram", InstagramColor, "com.instagram.android"),
                        Triple("TikTok", TikTokColor, "com.zhiliaoapp.musically")
                    )

                    apps.forEach { (appName, color, _) ->
                        ListItem(
                            leadingContent = {
                                Box(
                                    modifier = Modifier
                                        .size(16.dp)
                                        .clip(CircleShape)
                                        .background(color)
                                )
                            },
                            headlineContent = { Text(appName, fontWeight = FontWeight.Medium) },
                            modifier = Modifier.clickable {
                                showBottomSheet = false
                                navController.navigate("app_details/$appName")
                            }
                        )
                        Divider(color = MaterialTheme.colorScheme.surfaceVariant)
                    }
                }
            }
        }
    }
}

// ডোনাট চার্ট
@Composable
fun DonutChart(usageData: Map<String, Int>) {
    val total = usageData.values.sum().coerceAtLeast(1)

    val colors = mapOf(
        "Facebook" to FacebookColor,
        "YouTube" to YouTubeColor,
        "Instagram" to InstagramColor,
        "TikTok" to TikTokColor
    )

    Canvas(modifier = Modifier.fillMaxSize()) {
        val strokeWidth = 36f

        if (usageData.isEmpty() || usageData.values.all { it == 0 }) {
            drawArc(
                color = BorderLight,
                startAngle = 0f, sweepAngle = 360f,
                useCenter = false, style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
            )
        } else {
            var startAngle = -90f
            usageData.forEach { (app, count) ->
                if (count > 0) {
                    val sweepAngle = (count.toFloat() / total) * 360f
                    drawArc(
                        color = colors[app] ?: Color.Gray,
                        startAngle = startAngle, sweepAngle = sweepAngle,
                        useCenter = false, style = Stroke(width = strokeWidth, cap = StrokeCap.Butt)
                    )
                    startAngle += sweepAngle
                }
            }
        }
    }
}

// অ্যাপের নাম ও কালার লেজেন্ড (যদি ১টি অ্যাপ হয় তবে শুধু ১টিই দেখাবে)
@Composable
fun AppUsageLegend(usageData: Map<String, Int>) {
    val colors = mapOf(
        "Facebook" to FacebookColor,
        "YouTube" to YouTubeColor,
        "Instagram" to InstagramColor,
        "TikTok" to TikTokColor
    )

    val activeApps = usageData.filter { it.value > 0 }

    if (activeApps.isNotEmpty()) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            activeApps.forEach { (appName, count) ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .clip(CircleShape)
                            .background(colors[appName] ?: Color.Gray)
                    )
                    Spacer(modifier = Modifier.width(5.dp))
                    Text(
                        text = "$appName ($count)",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

// অ্যাক্টিভ লিমিট কার্ড
@Composable
fun ActiveLimitCard(limit: AppLimit, onClick: () -> Unit) {
    val appName = getAppNameFromPackage(limit.packageName)
    val color = when (appName) {
        "Facebook" -> FacebookColor
        "YouTube" -> YouTubeColor
        "Instagram" -> InstagramColor
        "TikTok" -> TikTokColor
        else -> MaterialTheme.colorScheme.primary
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() },
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(12.dp),
        border = CardDefaults.outlinedCardBorder()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(12.dp)
                        .clip(CircleShape)
                        .background(color)
                )
                Spacer(modifier = Modifier.width(10.dp))
                Text(appName, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
            }

            // কী কী লিমিট সক্রিয় আছে তার সামারি
            val limitText = when {
                limit.maxReels != null && limit.maxTimeMillis != null -> {
                    "${limit.maxReels} reels | ${(limit.maxTimeMillis / 60000)} min"
                }
                limit.maxReels != null -> "${limit.maxReels} reels limit"
                limit.maxTimeMillis != null -> "${(limit.maxTimeMillis / 60000)} min limit"
                else -> "No limit"
            }

            Surface(
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f),
                shape = RoundedCornerShape(8.dp)
            ) {
                Text(
                    text = limitText,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                )
            }
        }
    }
}

fun getAppNameFromPackage(pkg: String): String {
    return when (pkg) {
        "com.facebook.katana" -> "Facebook"
        "com.google.android.youtube" -> "YouTube"
        "com.instagram.android" -> "Instagram"
        "com.zhiliaoapp.musically" -> "TikTok"
        else -> "App"
    }
}
