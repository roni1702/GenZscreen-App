package com.example.genzscreen.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.example.genzscreen.data.GenZDatabase
import com.example.genzscreen.ui.theme.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.*

data class OverallDayData(
    val dateString: String,
    val displayLabel: String,
    val fullDateLabel: String,
    var totalReels: Int = 0,
    var totalTimeMillis: Long = 0L,
    val appStats: MutableMap<String, Pair<Int, Long>> = mutableMapOf()
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OverallDetailsScreen(navController: NavController) {
    var isReelsActive by remember { mutableStateOf(true) }
    var weekOffset by remember { mutableStateOf(0) }
    var selectedDayIndex by remember { mutableStateOf(6) }

    var weekDaysData by remember { mutableStateOf<List<OverallDayData>>(emptyList()) }
    val context = LocalContext.current

    LaunchedEffect(weekOffset) {
        withContext(Dispatchers.IO) {
            val dbFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
            val shortDayFormat = SimpleDateFormat("EEE\ndd", Locale.getDefault())
            val fullLabelFormat = SimpleDateFormat("EEE, dd MMM", Locale.getDefault())

            val cal = Calendar.getInstance()
            cal.add(Calendar.DAY_OF_YEAR, weekOffset * 7)
            cal.add(Calendar.DAY_OF_YEAR, -6)

            val daysList = mutableListOf<OverallDayData>()
            for (i in 0..6) {
                val dStr = dbFormat.format(cal.time)
                val shortLbl = shortDayFormat.format(cal.time).lowercase()
                val fullLbl = fullLabelFormat.format(cal.time).lowercase()
                daysList.add(OverallDayData(dStr, shortLbl, fullLbl))
                cal.add(Calendar.DAY_OF_YEAR, 1)
            }

            val db = GenZDatabase.getDatabase(context)
            val cursor = db.openHelper.readableDatabase.query(
                "SELECT date, packageName, reelCount, watchTimeMillis FROM daily_usage WHERE date >= ? AND date <= ?",
                arrayOf(daysList.first().dateString, daysList.last().dateString)
            )

            while (cursor.moveToNext()) {
                val date = cursor.getString(0)
                val pkg = cursor.getString(1)
                val count = cursor.getInt(2)
                val time = cursor.getLong(3)

                val dayObj = daysList.find { it.dateString == date }
                if (dayObj != null) {
                    dayObj.totalReels += count
                    dayObj.totalTimeMillis += time
                    val appName = getAppNameInternal(pkg)
                    dayObj.appStats[appName] = Pair(count, time)
                }
            }
            cursor.close()

            withContext(Dispatchers.Main) {
                weekDaysData = daysList
                selectedDayIndex = 6
            }
        }
    }

    val selectedDay = weekDaysData.getOrNull(selectedDayIndex)
    val selectedCount = selectedDay?.totalReels ?: 0
    val selectedTimeMillis = selectedDay?.totalTimeMillis ?: 0L
    val selectedHours = (selectedTimeMillis / 3600000)
    val selectedMinutes = (selectedTimeMillis / 60000) % 60
    val formattedTime = if (selectedHours > 0) "$selectedHours hr $selectedMinutes min" else "$selectedMinutes min"

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
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp)
        ) {
            Spacer(modifier = Modifier.height(8.dp))

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                OverallSegmentedToggle(
                    isActive = isReelsActive,
                    leftText = "Reels & Video",
                    rightText = "Time Watch",
                    onToggle = { isReelsActive = it }
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            Column {
                Text(
                    text = if (isReelsActive) "$selectedCount reels & video watch" else formattedTime,
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Text(
                    text = selectedDay?.fullDateLabel ?: "",
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Medium
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = { if (weekOffset > -26) weekOffset-- }) {
                    Icon(Icons.Default.KeyboardArrowLeft, contentDescription = "Previous Week")
                }

                Text(
                    text = if (weekOffset == 0) "Current Week" else "Week (${weekDaysData.firstOrNull()?.dateString ?: ""} to ${weekDaysData.lastOrNull()?.dateString ?: ""})",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                if (weekOffset < 0) {
                    IconButton(onClick = { weekOffset++ }) {
                        Icon(Icons.Default.KeyboardArrowRight, contentDescription = "Next Week")
                    }
                } else {
                    Spacer(modifier = Modifier.size(48.dp))
                }
            }

            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(210.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)),
                shape = RoundedCornerShape(16.dp)
            ) {
                OverallBarChartCanvas(
                    weekDays = weekDaysData,
                    selectedIndex = selectedDayIndex,
                    isReels = isReelsActive,
                    onBarClick = { clickedIndex -> selectedDayIndex = clickedIndex }
                )
            }

            Spacer(modifier = Modifier.height(20.dp))

            Text(
                text = "App Usage Ranking (${selectedDay?.fullDateLabel ?: ""})",
                fontWeight = FontWeight.Bold,
                fontSize = 15.sp
            )
            Spacer(modifier = Modifier.height(8.dp))

            val currentDayAppStats = selectedDay?.appStats?.toList()?.sortedByDescending {
                if (isReelsActive) it.second.first else it.second.second
            }?.filter { it.second.first > 0 || it.second.second > 0 } ?: emptyList()

            if (currentDayAppStats.isEmpty()) {
                Text(
                    text = "No reels or video watched on this day.",
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 16.dp)
                )
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(currentDayAppStats) { (appName, stats) ->
                        val (reels, time) = stats
                        val appMins = (time / 60000)
                        val appColor = when (appName) {
                            "Facebook" -> FacebookColor
                            "YouTube" -> YouTubeColor
                            "Instagram" -> InstagramColor
                            "TikTok" -> TikTokColor
                            else -> MaterialTheme.colorScheme.primary
                        }

                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { navController.navigate("app_details/$appName") },
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
                                            .background(appColor)
                                    )
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Text(appName, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                                }

                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = "$reels reels",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 14.sp,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = "•  $appMins min",
                                        fontSize = 13.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun OverallBarChartCanvas(
    weekDays: List<OverallDayData>,
    selectedIndex: Int,
    isReels: Boolean,
    onBarClick: (Int) -> Unit
) {
    if (weekDays.isEmpty()) return

    val values = weekDays.map { if (isReels) it.totalReels.toFloat() else (it.totalTimeMillis / 60000).toFloat() }
    val maxVal = (values.maxOrNull() ?: 10f).coerceAtLeast(10f)
    val avgVal = values.average().toFloat()

    val dashEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 10f), 0f)

    Column(modifier = Modifier.fillMaxSize().padding(12.dp)) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .pointerInput(Unit) {
                    detectTapGestures { offset ->
                        val barWidth = size.width / 7f
                        val clickedIndex = (offset.x / barWidth).toInt().coerceIn(0, 6)
                        onBarClick(clickedIndex)
                    }
                }
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val width = size.width
                val height = size.height
                val barWidth = (width / 7f) * 0.45f

                if (avgVal > 0) {
                    val avgY = height - ((avgVal / maxVal) * height)
                    drawLine(
                        color = Color(0xFF64748B),
                        start = Offset(0f, avgY),
                        end = Offset(width, avgY),
                        strokeWidth = 2f,
                        pathEffect = dashEffect
                    )
                }

                for (i in 0 until 7) {
                    val slotCenter = (i * (width / 7f)) + ((width / 7f) / 2f)
                    val barHeight = ((values[i] / maxVal) * (height - 20f)).coerceAtLeast(6f)
                    val top = height - barHeight

                    val isSelected = (i == selectedIndex)
                    val barColor = if (isSelected) Color(0xFF1A73E8) else Color(0xFFCBD5E1)

                    drawRoundRect(
                        color = barColor,
                        topLeft = Offset(slotCenter - (barWidth / 2f), top),
                        size = Size(barWidth, barHeight),
                        cornerRadius = CornerRadius(6.dp.toPx())
                    )
                }
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
            horizontalArrangement = Arrangement.SpaceAround
        ) {
            weekDays.forEachIndexed { index, day ->
                val isSelected = (index == selectedIndex)
                Text(
                    text = day.displayLabel,
                    fontSize = 10.sp,
                    lineHeight = 12.sp,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                    color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.clickable { onBarClick(index) }
                )
            }
        }
    }
}

@Composable
fun OverallSegmentedToggle(
    isActive: Boolean,
    leftText: String,
    rightText: String,
    onToggle: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier.background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(24.dp))
    ) {
        Button(
            onClick = { onToggle(true) },
            colors = ButtonDefaults.buttonColors(
                containerColor = if (isActive) MaterialTheme.colorScheme.primary else Color.Transparent,
                contentColor = if (isActive) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
            ),
            elevation = ButtonDefaults.buttonElevation(0.dp),
            shape = RoundedCornerShape(topStart = 24.dp, bottomStart = 24.dp)
        ) { Text(leftText, fontSize = 13.sp) }

        Button(
            onClick = { onToggle(false) },
            colors = ButtonDefaults.buttonColors(
                containerColor = if (!isActive) MaterialTheme.colorScheme.primary else Color.Transparent,
                contentColor = if (!isActive) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
            ),
            elevation = ButtonDefaults.buttonElevation(0.dp),
            shape = RoundedCornerShape(topEnd = 24.dp, bottomEnd = 24.dp)
        ) { Text(rightText, fontSize = 13.sp) }
    }
}

private fun getAppNameInternal(pkg: String): String {
    return when (pkg) {
        "com.facebook.katana" -> "Facebook"
        "com.google.android.youtube" -> "YouTube"
        "com.instagram.android" -> "Instagram"
        "com.zhiliaoapp.musically" -> "TikTok"
        else -> "App"
    }
}
