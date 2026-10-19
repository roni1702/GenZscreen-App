package com.example.genzscreen.service

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.content.Context
import android.media.AudioManager
import android.view.accessibility.AccessibilityEvent
import com.example.genzscreen.data.GenZDatabase
import kotlinx.coroutines.*
import java.text.SimpleDateFormat
import java.util.*

class ReelAccessibilityService : AccessibilityService() {

    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private lateinit var overlayManager: OverlayManager
    private lateinit var audioManager: AudioManager
    
    private var currentPackage = ""
    private var isWatching = false
    private var videoStartTime = 0L
    private var currentReelCount = 0
    private var accumulatedTimeMillis = 0L
    private var continuousSessionMillis = 0L // একটানা দেখার হিসাব

    // লিমিট ভেরিয়েবল
    private var dailyReelLimit: Int? = null
    private var dailyTimeLimitMillis: Long? = null

    private var trackingJob: Job? = null
    private var stopJob: Job? = null
    private var isBreakAlertShowing = false

    private val targetPackages = listOf(
        "com.facebook.katana", "com.google.android.youtube",
        "com.zhiliaoapp.musically", "com.instagram.android"
    )

    override fun onServiceConnected() {
        overlayManager = OverlayManager(this)
        audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val info = AccessibilityServiceInfo().apply {
            eventTypes = AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED or AccessibilityEvent.TYPE_VIEW_SCROLLED
            feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC
            flags = AccessibilityServiceInfo.DEFAULT
            packageNames = targetPackages.toTypedArray()
        }
        this.serviceInfo = info
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        val packageName = event?.packageName?.toString() ?: return

        if (!targetPackages.contains(packageName)) {
            if (isWatching) stopWatchingSession()
            currentPackage = ""
            return
        }

        if (packageName != currentPackage) {
            currentPackage = packageName
            fetchTodayDataAndLimitsFromDB() // ডেটার সাথে লিমিটও লোড হবে
            serviceScope.launch(Dispatchers.Main) { overlayManager.hideFacebookBlocker() }
        }

        val isAudioPlaying = audioManager.isMusicActive
        val isScroll = event.eventType == AccessibilityEvent.TYPE_VIEW_SCROLLED
        
        if (isAudioPlaying || isScroll) {
            stopJob?.cancel()
            
            if (!isWatching) {
                startWatchingSession()
            }

            if (isScroll) {
                val timeSpent = System.currentTimeMillis() - videoStartTime
                if (timeSpent >= 5000L) { // ৫ সেকেন্ড লজিক
                    currentReelCount++
                    saveUsageToDatabaseIncremental(currentPackage, 1, 0L)
                    checkLimits() // রিলস বাড়লেই লিমিট চেক হবে
                    videoStartTime = System.currentTimeMillis()
                }
            }
        } else {
            if (isWatching && stopJob?.isActive != true) {
                stopJob = serviceScope.launch {
                    delay(4000)
                    stopWatchingSession()
                }
            }
        }
    }

    private fun startWatchingSession() {
        isWatching = true
        videoStartTime = System.currentTimeMillis()

        trackingJob?.cancel()
        trackingJob = serviceScope.launch {
            while (isActive) {
                delay(1000)
                accumulatedTimeMillis += 1000
                continuousSessionMillis += 1000 // একটানা সেশন কাউন্ট
                
                if (accumulatedTimeMillis % 5000L == 0L) {
                    saveUsageToDatabaseIncremental(currentPackage, 0, 5000L)
                    checkLimits() // প্রতি ৫ সেকেন্ডে লিমিট চেক
                }

                // ৩০ মিনিটের ব্রেক অ্যালার্ট লজিক
                val thirtyMins = 30 * 60 * 1000L
                if (continuousSessionMillis > 0 && continuousSessionMillis % thirtyMins == 0L && !isBreakAlertShowing) {
                    isBreakAlertShowing = true
                    withContext(Dispatchers.Main) {
                        overlayManager.showBreakAlert(
                            minutes = (continuousSessionMillis / 60000).toInt(),
                            onTakeRest = {
                                isBreakAlertShowing = false
                                overlayManager.hideBreakAlert()
                                performGlobalAction(GLOBAL_ACTION_HOME) // হোম স্ক্রিনে নিয়ে যাবে
                            },
                            onNoThanks = {
                                isBreakAlertShowing = false
                                overlayManager.hideBreakAlert()
                            }
                        )
                    }
                }

                val timeStr = formatTime(accumulatedTimeMillis)
                withContext(Dispatchers.Main) {
                    if (!isBreakAlertShowing) {
                        overlayManager.showTrackingOverlay(currentReelCount, timeStr)
                    }
                }
            }
        }
    }

    private fun stopWatchingSession() {
        isWatching = false
        trackingJob?.cancel()
        continuousSessionMillis = 0L // অ্যাপ থেকে বের হলে একটানা সময় রিসেট
        serviceScope.launch(Dispatchers.Main) {
            overlayManager.hideTrackingOverlay()
        }
    }

    // লিমিট চেক করার ফাংশন
    private fun checkLimits() {
        var isLimitReached = false
        dailyReelLimit?.let { if (currentReelCount >= it) isLimitReached = true }
        dailyTimeLimitMillis?.let { if (accumulatedTimeMillis >= it) isLimitReached = true }

        if (isLimitReached) {
            if (currentPackage == "com.facebook.katana") {
                serviceScope.launch(Dispatchers.Main) { overlayManager.showFacebookBlocker() }
            } else {
                performGlobalAction(GLOBAL_ACTION_HOME) // YouTube, TikTok, Insta ফোর্স ক্লোজ
            }
        }
    }

    private fun saveUsageToDatabaseIncremental(pkg: String, countAdd: Int, timeAdd: Long) {
        if (countAdd == 0 && timeAdd == 0L) return
        val date = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
        serviceScope.launch(Dispatchers.IO) {
            try {
                val db = GenZDatabase.getDatabase(applicationContext).openHelper.writableDatabase
                val cursor = db.query("SELECT * FROM daily_usage WHERE date = ? AND packageName = ?", arrayOf(date, pkg))
                val exists = cursor.moveToFirst()
                cursor.close()
                if (!exists) {
                    db.execSQL("INSERT INTO daily_usage (date, packageName, reelCount, watchTimeMillis) VALUES (?, ?, 0, 0)", arrayOf(date, pkg))
                }
                db.execSQL("UPDATE daily_usage SET reelCount = reelCount + ?, watchTimeMillis = watchTimeMillis + ? WHERE date = ? AND packageName = ?", arrayOf(countAdd, timeAdd, date, pkg))
            } catch (e: Exception) { e.printStackTrace() }
        }
    }

    // ডেটাবেজ থেকে রিলস, টাইম এবং লিমিট একসাথে লোড করা
    private fun fetchTodayDataAndLimitsFromDB() {
        val date = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
        serviceScope.launch(Dispatchers.IO) {
            try {
                val db = GenZDatabase.getDatabase(applicationContext).openHelper.readableDatabase
                
                // ১. ইউসেজ ডেটা আনা
                val cursor = db.query("SELECT reelCount, watchTimeMillis FROM daily_usage WHERE date = ? AND packageName = ?", arrayOf(date, currentPackage))
                if (cursor.moveToFirst()) {
                    currentReelCount = cursor.getInt(0)
                    accumulatedTimeMillis = cursor.getLong(1)
                } else {
                    currentReelCount = 0; accumulatedTimeMillis = 0L
                }
                cursor.close()

                // ২. লিমিট আনা
                val limitCursor = db.query("SELECT maxReels, maxTimeMillis FROM app_limits WHERE packageName = ?", arrayOf(currentPackage))
                if (limitCursor.moveToFirst()) {
                    dailyReelLimit = if (limitCursor.isNull(0)) null else limitCursor.getInt(0)
                    dailyTimeLimitMillis = if (limitCursor.isNull(1)) null else limitCursor.getLong(1)
                } else {
                    dailyReelLimit = null; dailyTimeLimitMillis = null
                }
                limitCursor.close()
            } catch (e: Exception) { e.printStackTrace() }
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
