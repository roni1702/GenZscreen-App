package com.example.genzscreen.service

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.example.genzscreen.data.DailyUsage
import com.example.genzscreen.data.GenZDatabase
import kotlinx.coroutines.*
import java.text.SimpleDateFormat
import java.util.*

class ReelAccessibilityService : AccessibilityService() {

    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private lateinit var overlayManager: OverlayManager
    
    private var currentPackage = ""
    private var isWatching = false
    private var videoStartTime = 0L
    private var currentReelCount = 0
    private var accumulatedTimeMillis = 0L

    private var trackingJob: Job? = null
    private var stopJob: Job? = null

    // Video Detection Throttle
    private var lastCheckTime = 0L
    private var lastVideoResult = false

    override fun onServiceConnected() {
        overlayManager = OverlayManager(this)
        val info = AccessibilityServiceInfo().apply {
            eventTypes = AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED or AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED or AccessibilityEvent.TYPE_VIEW_SCROLLED
            feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC
            flags = AccessibilityServiceInfo.DEFAULT
            packageNames = arrayOf(
                "com.facebook.katana",
                "com.google.android.youtube",
                "com.zhiliaoapp.musically",
                "com.instagram.android"
            )
        }
        this.serviceInfo = info
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        val packageName = event?.packageName?.toString() ?: return

        if (packageName != currentPackage) {
            currentPackage = packageName
            fetchTodayCountFromDB() // Load real data for today
        }

        // Check if video is actually on screen
        val hasVideo = checkVideoThrottled(rootInActiveWindow)

        if (hasVideo) {
            stopJob?.cancel() // Cancel hiding if video is found
            
            if (!isWatching) {
                startWatchingSession()
            }

            // Detect Reel Scroll
            if (event.eventType == AccessibilityEvent.TYPE_VIEW_SCROLLED) {
                val timeSpent = System.currentTimeMillis() - videoStartTime
                if (timeSpent >= 5000L) { // Only count if watched for 5 seconds
                    currentReelCount++
                    saveUsageToDatabaseIncremental(currentPackage, 1, 0L)
                }
                videoStartTime = System.currentTimeMillis()
            }
        } else {
            // Give 3 seconds buffer before hiding the box
            if (isWatching && stopJob?.isActive != true) {
                stopJob = serviceScope.launch {
                    delay(3000)
                    stopWatchingSession()
                }
            }
        }
    }

    // Smart Video Detector
    private fun checkVideoThrottled(node: AccessibilityNodeInfo?): Boolean {
        val now = System.currentTimeMillis()
        if (now - lastCheckTime < 1000L) return lastVideoResult // Throttle check to avoid lag
        lastCheckTime = now
        lastVideoResult = isVideoOnScreen(node)
        return lastVideoResult
    }

    private fun isVideoOnScreen(node: AccessibilityNodeInfo?): Boolean {
        if (node == null) return false
        val queue = LinkedList<AccessibilityNodeInfo>()
        queue.add(node)
        
        while (queue.isNotEmpty()) {
            val current = queue.poll() ?: continue
            val className = current.className?.toString() ?: ""
            
            // TextureView and SurfaceView are native Android video players
            if (className.contains("SurfaceView") || className.contains("TextureView") || className.contains("VideoView")) {
                return true
            }
            for (i in 0 until current.childCount) {
                current.getChild(i)?.let { queue.add(it) }
            }
        }
        return false
    }

    private fun startWatchingSession() {
        isWatching = true
        videoStartTime = System.currentTimeMillis()

        trackingJob?.cancel()
        trackingJob = serviceScope.launch {
            while (isActive) {
                delay(1000) // 1 sec tick
                accumulatedTimeMillis += 1000
                
                // Save time to DB every 5 seconds to prevent data loss
                if (accumulatedTimeMillis % 5000L == 0L) {
                    saveUsageToDatabaseIncremental(currentPackage, 0, 5000L)
                }

                val timeStr = formatTime(accumulatedTimeMillis)
                withContext(Dispatchers.Main) {
                    overlayManager.showTrackingOverlay(currentReelCount, timeStr)
                }
            }
        }
    }

    private fun stopWatchingSession() {
        isWatching = false
        trackingJob?.cancel()
        
        serviceScope.launch(Dispatchers.Main) {
            overlayManager.hideTrackingOverlay()
        }
    }

    // Save Data to Room Database without crushing old data
    private fun saveUsageToDatabaseIncremental(pkg: String, countAdd: Int, timeAdd: Long) {
        if (countAdd == 0 && timeAdd == 0L) return
        val date = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
        
        serviceScope.launch(Dispatchers.IO) {
            try {
                val db = GenZDatabase.getDatabase(applicationContext)
                val cursor = db.query("SELECT * FROM daily_usage WHERE date = '$date' AND packageName = '$pkg'", null)
                val exists = cursor.moveToFirst()
                cursor.close()
                
                if (!exists) {
                    db.usageDao().insertUsage(DailyUsage(date, pkg, 0, 0L))
                }
                db.usageDao().updateUsage(date, pkg, countAdd, timeAdd)
            } catch (e: Exception) { e.printStackTrace() }
        }
    }

    private fun fetchTodayCountFromDB() {
        val date = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
        serviceScope.launch(Dispatchers.IO) {
            try {
                val db = GenZDatabase.getDatabase(applicationContext)
                val cursor = db.query("SELECT reelCount, watchTimeMillis FROM daily_usage WHERE date = '$date' AND packageName = '$currentPackage'", null)
                if (cursor.moveToFirst()) {
                    currentReelCount = cursor.getInt(0)
                    accumulatedTimeMillis = cursor.getLong(1)
                } else {
                    currentReelCount = 0
                    accumulatedTimeMillis = 0L
                }
                cursor.close()
            } catch (e: Exception) {}
        }
    }

    private fun formatTime(millis: Long): String {
        val seconds = (millis / 1000) % 60
        val minutes = (millis / (1000 * 60)) % 60
        val hours = (millis / (1000 * 60 * 60))
        return if (hours > 0) String.format("%02d:%02d:%02d", hours, minutes, seconds)
        else String.format("%02d:%02d", minutes, seconds)
    }

    override fun onInterrupt() {
        trackingJob?.cancel()
        overlayManager.hideTrackingOverlay()
    }
}
