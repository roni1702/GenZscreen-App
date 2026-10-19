package com.example.genzscreen.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppSpecificScreen(navController: NavController, appName: String) {
    var isReelsActive by remember { mutableStateOf(true) }
    var showLimitDialog by remember { mutableStateOf(false) }
    
    var activeCountLimit by remember { mutableStateOf<Int?>(null) }
    var activeTimeLimit by remember { mutableStateOf<Int?>(null) }
    
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    
    val packageName = when(appName) {
        "Facebook" -> "com.facebook.katana"
        "YouTube" -> "com.google.android.youtube"
        "Instagram" -> "com.instagram.android"
        "TikTok" -> "com.zhiliaoapp.musically"
        else -> ""
    }

    // Load limits from Database
    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            val db = GenZDatabase.getDatabase(context)
            val limit = db.usageDao().getLimitForApp(packageName)
            withContext(Dispatchers.Main) {
                activeCountLimit = limit?.maxReels
                activeTimeLimit = limit?.maxTimeMillis?.let { (it / 60000).toInt() }
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(appName, fontSize = 20.sp, fontWeight = FontWeight.Bold) },
                navigationIcon = { IconButton(onClick = { navController.popBackStack() }) { Icon(Icons.Default.ArrowBack, contentDescription = "Back") } }
            )
        }
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                SegmentedToggleButton(isActive = isReelsActive, leftText = "Reels & Video", rightText = "Time Watch", onToggle = { isReelsActive = it })
            }

            Spacer(modifier = Modifier.height(16.dp))

            Card(modifier = Modifier.fillMaxWidth().height(200.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))) {
                WeeklyBarChart(average = 0f, offset = 0)
            }

            Spacer(modifier = Modifier.height(32.dp))
            Text("Limit Management", fontWeight = FontWeight.Bold, fontSize = 16.sp)
            Spacer(modifier = Modifier.height(12.dp))

            Button(onClick = { showLimitDialog = true }, modifier = Modifier.fillMaxWidth().height(56.dp)) {
                Text("Set limit for Reels & Videos")
            }

            Spacer(modifier = Modifier.height(16.dp))

            Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = if (activeCountLimit != null || activeTimeLimit != null) Color(0xFFE8F0FE) else MaterialTheme.colorScheme.surfaceVariant)) {
                Row(modifier = Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Column {
                        Text("Active Limit Status", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                        val status = when {
                            activeCountLimit != null && activeTimeLimit != null -> "Count: $activeCountLimit | Time: $activeTimeLimit min"
                            activeCountLimit != null -> "Count Limit: $activeCountLimit reels"
                            activeTimeLimit != null -> "Time Limit: $activeTimeLimit mins"
                            else -> "No limit"
                        }
                        Text(status, fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }

                    if (activeCountLimit != null || activeTimeLimit != null) {
                        IconButton(onClick = {
                            activeCountLimit = null
                            activeTimeLimit = null
                            coroutineScope.launch(Dispatchers.IO) {
                                GenZDatabase.getDatabase(context).usageDao().deleteLimit(packageName)
                            }
                        }) { Icon(Icons.Default.Delete, contentDescription = "Delete", tint = Color(0xFFD93025)) }
                    }
                }
            }
        }

        if (showLimitDialog) {
            var countInput by remember { mutableStateOf(activeCountLimit?.toString() ?: "") }
            var timeInput by remember { mutableStateOf(activeTimeLimit?.toString() ?: "") }

            AlertDialog(
                onDismissRequest = { showLimitDialog = false },
                title = { Text("Set Limit") },
                text = {
                    Column {
                        OutlinedTextField(value = countInput, onValueChange = { countInput = it }, label = { Text("Reels Count Limit") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), singleLine = true)
                        Spacer(modifier = Modifier.height(8.dp))
                        OutlinedTextField(value = timeInput, onValueChange = { timeInput = it }, label = { Text("Time Limit (Minutes)") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), singleLine = true)
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
                            GenZDatabase.getDatabase(context).usageDao().insertLimit(AppLimit(packageName, count, timeMillis))
                        }
                    }) { Text("Save Limit") }
                },
                dismissButton = { TextButton(onClick = { showLimitDialog = false }) { Text("Cancel") } }
            )
        }
    }
}
