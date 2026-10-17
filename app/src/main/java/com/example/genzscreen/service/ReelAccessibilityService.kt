package com.example.genzscreen.service

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.view.accessibility.AccessibilityEvent
import kotlinx.coroutines.*
import java.text.SimpleDateFormat
import java.util.*

class ReelAccessibilityService : AccessibilityService() {

    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private lateinit var overlayManager: OverlayManager
    
    // Usage Tracking Variables
    private var currentPackage = ""
    private var sessionStartTime = 0L
    private var isWatching = false
    
    private var videoStartTime = 0L
    private var currentReelCount = 0
    private var accumulatedTimeMillis = 0L

    private var trackingJob: Job? = null

    override fun onServiceConnected() {
        overlayManager = OverlayManager(this)
        val info = AccessibilityServiceInfo().apply {
            eventTypes = AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED or AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED
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
            resetTracking()
        }

        // Logic to detect if it's a video/reel screen (Heuristic logic)
        // For production, we track specific class names like ViewPager2 or RecyclerViews
        val isVideoScreen = detectVideoScreen(event)

        if (isVideoScreen && !isWatching) {
            startWatchingSession()
        } else if (!isVideoScreen && isWatching) {
            stopWatchingSession()
        }
    }

    private fun detectVideoScreen(event: AccessibilityEvent): Boolean {
        // Simplified detection. If it's one of the target apps and actively scrolling/changing content.
        val className = event.className?.toString() ?: ""
        return className.contains("ViewPager") || className.contains("RecyclerView")
    }

    private fun startWatchingSession() {
        isWatching = true
        videoStartTime = System.currentTimeMillis()
        if (sessionStartTime == 0L) sessionStartTime = System.currentTimeMillis()

        trackingJob?.cancel()
        trackingJob = serviceScope.launch {
            while (isActive) {
                delay(1000) // 1 second tick
                accumulatedTimeMillis += 1000
                
                // Check 5-second rule
                val timeOnCurrentVideo = System.currentTimeMillis() - videoStartTime
                if (timeOnCurrentVideo == 5000L) {
                    currentReelCount++ // Increment count ONLY after 5 seconds of play
                }

                // Check Limits & Breaks
                checkLimitsAndBreaks()

                // Update UI Overlay
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
        
        // Save to Database (Room)
        saveUsageToDatabase()

        // Hide overlay after 5 seconds of exit as per requirements
        serviceScope.launch {
            delay(5000)
            withContext(Dispatchers.Main) {
                overlayManager.hideTrackingOverlay()
                overlayManager.hideFacebookBlocker()
            }
        }
    }

    private suspend fun checkLimitsAndBreaks() {
        // Here we query the GenZDatabase (Room) for configured limits
        // val limit = db.usageDao().getLimitForApp(currentPackage)
        
        // Pseudo logic for limit enforcement
        val limitReached = false // Replace with actual DB check

        if (limitReached) {
            withContext(Dispatchers.Main) {
                if (currentPackage == "com.facebook.katana") {
                    overlayManager.showFacebookBlocker()
                } else {
                    // Force kill / prevent entry for YT, TikTok, Insta
                    performGlobalAction(GLOBAL_ACTION_HOME)
                }
            }
        }

        // 30 Min Break Alerts
        val sessionTime = System.currentTimeMillis() - sessionStartTime
        val thirtyMins = 30 * 60 * 1000L
        if (sessionTime > 0 && sessionTime % thirtyMins in 0..1500L) {
            // Trigger 30-min dialog logic here
            // performGlobalAction(GLOBAL_ACTION_HOME) to force break
        }
    }

    private fun saveUsageToDatabase() {
        // val today = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
        // db.usageDao().updateUsage(today, currentPackage, currentReelCount, accumulatedTimeMillis)
    }

    private fun resetTracking() {
        videoStartTime = 0L
        isWatching = false
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
