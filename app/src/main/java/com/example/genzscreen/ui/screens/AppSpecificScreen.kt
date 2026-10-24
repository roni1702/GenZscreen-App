package com.example.genzscreen.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.example.genzscreen.data.AppLimit
import com.example.genzscreen.data.GenZDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.*

data class AppSpecificDayData(
    val dateString: String,
    val displayLabel: String,
    val fullDateLabel: String,
    var totalReels: Int = 0,
    var totalTimeMillis: Long = 0L
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppSpecificScreen(navController: NavController, appName: String) {
    var isReelsActive by remember { mutableStateOf(true) }
    var weekOffset by remember { mutableStateOf(0) }
    var selectedDayIndex by remember { mutableStateOf(6) }

    var showLimitDialog by remember { mutableStateOf(false) }
    var activeCountLimit by remember { mutableStateOf<Int?>(null) }
    var activeTimeLimit by remember { mutableStateOf<Int?>(null) }

    var appWeekDaysData by remember { mutableStateOf<List<AppSpecificDayData>>(emptyList()) }

    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    val packageName = when (appName) {
        "Facebook" -> "com.facebook.katana"
        "YouTube" -> "com.google.android.youtube"
        "Instagram" -> "com.instagram.android"
        "TikTok" -> "com.zhiliaoapp.musically"
        else -> ""
    }

    LaunchedEffect(packageName) {
        withContext(Dispatchers.IO) {
            val db = GenZDatabase.getDatabase(context)
            val limit = db.usageDao().getLimitForApp(packageName)
            withContext(Dispatchers.Main) {
                activeCountLimit = limit?.maxReels
                activeTimeLimit = limit?.maxTimeMillis?.let { (it / 60000).toInt() }
            }
        }
    }

    LaunchedEffect(weekOffset, packageName) {
        withContext(Dispatchers.IO) {
            val dbFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
            val shortDayFormat = SimpleDateFormat("EEE\ndd", Locale.getDefault())
            val fullLabelFormat = SimpleDateFormat("EEE, dd MMM", Locale.getDefault())

            val cal = Calendar.getInstance()
            cal.add(Calendar.DAY_OF_YEAR, weekOffset * 7)
            cal.add(Calendar.DAY_OF_YEAR, -6)

            val daysList = mutableListOf<AppSpecificDayData>()
            for (i in 0..6) {
                val dStr = dbFormat.format(cal.time)
                val shortLbl = shortDayFormat.format(cal.time).lowercase()
                val fullLbl = fullLabelFormat.format(cal.time).lowercase()
                daysList.add(AppSpecificDayData(dStr, shortLbl, fullLbl))
                cal.add(Calendar.DAY_OF_YEAR, 1)
            }

            val db = GenZDatabase.getDatabase(context)
            val cursor = db.openHelper.readableDatabase.query(
                "SELECT date, reelCount, watchTimeMillis FROM daily_usage WHERE date >= ? AND date <= ? AND packageName = ?",
                arrayOf(daysList.first().dateString, daysList.last().dateString, packageName)
            )

            while (cursor.moveToNext()) {
                val date = cursor.getString(0)
                val count = cursor.getInt(1)
                val time = cursor.getLong(2)

                val dayObj = daysList.find { it.dateString == date }
                if (dayObj != null) {
                    dayObj.totalReels = count
                    dayObj.totalTimeMillis = time
                }
            }
            cursor.close()

            withContext(Dispatchers.Main) {
                appWeekDaysData = daysList
                selectedDayIndex = 6
            }
        }
    }

    val selectedDay = appWeekDaysData.getOrNull(selectedDayIndex)
    val selectedCount = selectedDay?.totalReels ?: 0
    val selectedTimeMillis = selectedDay?.totalTimeMillis ?: 0L
    val selectedHours = (selectedTimeMillis / 3600000)
    val selectedMinutes = (selectedTimeMillis / 60000) % 60
    val formattedTime = if (selectedHours > 0) "$selectedHours hr $selectedMinutes min" else "$selectedMinutes min"

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(appName, fontSize = 20.sp, fontWeight = FontWeight.Bold) },
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
                AppSegmentedToggle(
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
                    text = "${selectedDay?.fullDateLabel ?: ""} ($appName)",
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
                    Icon(Icons.Default.KeyboardArrowLeft, contentDescription = "Previous")
                }
                Text(
                    text = if (weekOffset == 0) "Current Week" else "Previous Week",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium
                )
                if (weekOffset < 0) {
                    IconButton(onClick = { weekOffset++ }) {
                        Icon(Icons.Default.KeyboardArrowRight, contentDescription = "Next")
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
                AppBarChartCanvas(
                    weekDays = appWeekDaysData,
                    selectedIndex = selectedDayIndex,
                    isReels = isReelsActive,
                    onBarClick = { clickedIndex -> selectedDayIndex = clickedIndex }
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            Text("Limit Setup", fontWeight = FontWeight.Bold, fontSize = 15.sp)
            Spacer(modifier = Modifier.height(10.dp))

            Button(
                onClick = { showLimitDialog = true },
                modifier = Modifier.fillMaxWidth().height(50.dp),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("Set limit for Reels & Videos", fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
            }

            Spacer(modifier = Modifier.height(14.dp))

            val hasLimit = (activeCountLimit != null || activeTimeLimit != null)
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = if (hasLimit) MaterialTheme.colorScheme.primary.copy(alpha = 0.08f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
                ),
                shape = RoundedCornerShape(12.dp),
                border = if (hasLimit) CardDefaults.outlinedCardBorder() else null
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text("Active Limit Status", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        val status = when {
                            activeCountLimit != null && activeTimeLimit != null -> {
                                "Reels: $activeCountLimit | Time: $activeTimeLimit min"
                            }
                            activeCountLimit != null -> "Count Limit: $activeCountLimit reels"
                            activeTimeLimit != null -> "Time Limit: $activeTimeLimit mins"
                            else -> "No limit set for $appName"
                        }
                        Text(
                            text = status,
                            fontSize = 13.sp,
                            color = if (hasLimit) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                            fontWeight = if (hasLimit) FontWeight.SemiBold else FontWeight.Normal
                        )
                    }

                    if (hasLimit) {
                        IconButton(onClick = {
                            activeCountLimit = null
                            activeTimeLimit = null
                            coroutineScope.launch(Dispatchers.IO) {
                                GenZDatabase.getDatabase(context).usageDao().deleteLimit(packageName)
                            }
                        }) {
                            Icon(Icons.Default.Delete, contentDescription = "Delete", tint = Color(0xFFEF4444))
                        }
                    }
                }
            }
        }

        if (showLimitDialog) {
            var countInput by remember { mutableStateOf(activeCountLimit?.toString() ?: "") }
            var timeInput by remember { mutableStateOf(activeTimeLimit?.toString() ?: "") }

            AlertDialog(
                onDismissRequest = { showLimitDialog = false },
                title = { Text("Set Limit for $appName", fontWeight = FontWeight.Bold) },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(
                            text = "Set limit for reel count, watch time, or both:",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        OutlinedTextField(
                            value = countInput,
                            onValueChange = { countInput = it },
                            label = { Text("Reels Count Limit") },
                            placeholder = { Text("e.g. 30") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        OutlinedTextField(
                            value = timeInput,
                            onValueChange = { timeInput = it },
                            label = { Text("Time Limit (Minutes)") },
                            placeholder = { Text("e.g. 45") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                },
                confirmButton = {
                    Button(onClick = {
                        val count = countInput.toIntOrNull()
                        val time = timeInput.toIntOrNull()
                        activeCountLimit = count
                        activeTimeLimit = time
                        showLimitDialog = false

                        coroutineScope.launch(Dispatchers.IO) {
                            val timeMillis = time?.let { it * 60000L }
                            GenZDatabase.getDatabase(context).usageDao().insertLimit(
                                AppLimit(packageName, count, timeMillis)
                            )
                        }
                    }) {
                        Text("Save Limit")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showLimitDialog = false }) { Text("Cancel") }
                }
            )
        }
    }
}

@Composable
fun AppBarChartCanvas(
    weekDays: List<AppSpecificDayData>,
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
fun AppSegmentedToggle(
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
