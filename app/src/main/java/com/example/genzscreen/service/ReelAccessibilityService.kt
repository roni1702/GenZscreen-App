package com.example.genzscreen.service

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.example.genzscreen.data.GenZDatabase
import kotlinx.coroutines.*
import java.text.SimpleDateFormat
import java.util.*

class ReelAccessibilityService : AccessibilityService() {

    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private lateinit var overlayManager: OverlayManager

    private var currentPackage = ""
    private var isWatching = false
    private var reelStayStartTime = 0L // নির্দিষ্ট রিলসে কতক্ষণ আছে
    private var currentReelCount = 0
    private var accumulatedTimeMillis = 0L
    private var continuousSessionMillis = 0L // একটানা দেখার সময় (ব্রেক অ্যালার্টের জন্য)

    // লিমিট ভ্যারিয়েবল
    private var dailyReelLimit: Int? = null
    private var dailyTimeLimitMillis: Long? = null

    private var trackingJob: Job? = null
    private var hideBoxJob: Job? = null
    private var isBreakAlertShowing = false

    // তারিখ ট্র্যাকিং (রাত ১২টায় অটোমেটিক ডেইলি রিসেটের জন্য)
    private var currentTrackingDate = ""

    private val targetPackages = listOf(
        "com.facebook.katana",
        "com.google.android.youtube",
        "com.zhiliaoapp.musically",
        "com.instagram.android"
    )

    override fun onServiceConnected() {
        overlayManager = OverlayManager(this)
        val info = AccessibilityServiceInfo().apply {
            eventTypes = AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED or 
                         AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED or 
                         AccessibilityEvent.TYPE_VIEW_SCROLLED
            feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC
            flags = AccessibilityServiceInfo.DEFAULT
            packageNames = targetPackages.toTypedArray()
        }
        this.serviceInfo = info
        currentTrackingDate = getTodayDateString()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        val packageName = event?.packageName?.toString() ?: return

        // ১. অন্য অ্যাপে চলে গেলে সাথে সাথে টাইমার অফ এবং ৫ সেকেন্ডে বক্স বিদায়
        if (!targetPackages.contains(packageName)) {
            if (isWatching) {
                stopTimerImmediately()
                hideBoxAfterDelay()
            }
            currentPackage = ""
            return
        }

        // ২. রাত ১২:০০ টায় অটোমেটিক কাউন্টার ০ করার ডেইলি রিসেট লজিক
        checkAndPerformMidnightReset()

        // ৩. টার্গেট অ্যাপ পরিবর্তন হলে ডেটাবেজ থেকে ডেটা ও লিমিট লোড
        if (packageName != currentPackage) {
            currentPackage = packageName
            fetchTodayDataAndLimitsFromDB()
            serviceScope.launch(Dispatchers.Main) { overlayManager.hideFacebookBlocker() }
        }

        // ৪. রিলস/শর্টস কি আসলেই স্ক্রিনে চলছে? (Precision Reels Detector)
        val isReelActive = isSpecificReelScreen(rootInActiveWindow, packageName, event)
        val isScroll = event.eventType == AccessibilityEvent.TYPE_VIEW_SCROLLED

        if (isReelActive) {
            hideBoxJob?.cancel() // বক্স লুকানোর টাইমার বাতিল

            if (!isWatching) {
                startWatchingSession()
            }

            // ৫ সেকেন্ডের নিয়ম: স্ক্রল করার সময় চেক করবে ইউজার আগের রিলসটিতে ৫ সেকেন্ড ছিল কিনা
            if (isScroll) {
                val timeOnReel = System.currentTimeMillis() - reelStayStartTime
                if (timeOnReel >= 5000L) {
                    currentReelCount++
                    saveUsageToDatabaseIncremental(currentPackage, 1, 0L)
                    checkLimits()
                }
                reelStayStartTime = System.currentTimeMillis() // নতুন রিলসে কাউন্ট শুরু
            }
        } else {
            // রিলস স্ক্রিনে না থাকলে ১ সেকেন্ডের মধ্যেই সময় গণনা বন্ধ
            if (isWatching) {
                stopTimerImmediately()
            }
            hideBoxAfterDelay()
        }
    }

    // নির্ভুল রিলস ডিটেক্টর (সাধারণ নিউজফিড পোস্টকে রিলস ধরবে না)
    private fun isSpecificReelScreen(
        rootNode: AccessibilityNodeInfo?, 
        pkg: String, 
        event: AccessibilityEvent?
    ): Boolean {
        if (rootNode == null) return false

        return when (pkg) {
            // TikTok সম্পূর্ণটাই ফুলস্ক্রিন শর্ট ভিডিও ফিড
            "com.zhiliaoapp.musically" -> true

            // YouTube Shorts ডিটেকশন
            "com.google.android.youtube" -> {
                hasNodeMatching(rootNode, maxDepth = 12) { id, cls, desc ->
                    id.contains("reel_recycler") || id.contains("shorts_container") || 
                    id.contains("reel_player") || desc.contains("shorts")
                }
            }

            // Instagram Reels ডিটেকশন
            "com.instagram.android" -> {
                hasNodeMatching(rootNode, maxDepth = 12) { id, cls, desc ->
                    id.contains("clips_viewer") || id.contains("reel_viewer") || 
                    desc.contains("reel by") || desc.contains("audio by")
                }
            }

            // Facebook Reels ডিটেকশন (সাধারণ নিউজফিড ফিল্টার করে শুধু রিলস প্লেয়ার ধরবে)
            "com.facebook.katana" -> {
                val eventClass = event?.className?.toString() ?: ""
                if (eventClass.contains("FbShorts") || eventClass.contains("ReelViewer")) return true

                hasNodeMatching(rootNode, maxDepth = 15) { id, cls, desc ->
                    id.contains("fb_shorts") || id.contains("reel_viewer") || 
                    id.contains("video_player_view") || desc.contains("reels") || 
                    desc.contains("original audio")
                }
            }

            else -> false
        }
    }

    // হালকা ও দ্রুত সার্চিং (যাতে মোবাইল হ্যাং বা মেমোরি জ্যাম না হয়)
    private fun hasNodeMatching(
        node: AccessibilityNodeInfo?, 
        depth: Int = 0, 
        maxDepth: Int = 12, 
        matcher: (String, String, String) -> Boolean
    ): Boolean {
        if (node == null || depth > maxDepth) return false
        val id = node.viewIdResourceName?.lowercase() ?: ""
        val cls = node.className?.toString() ?: ""
        val desc = node.contentDescription?.toString()?.lowercase() ?: ""

        if (matcher(id, cls, desc)) return true

        for (i in 0 until node.childCount) {
            val child = node.getChild(i)
            if (hasNodeMatching(child, depth + 1, maxDepth, matcher)) return true
        }
        return false
    }

    private fun startWatchingSession() {
        isWatching = true
        reelStayStartTime = System.currentTimeMillis()

        trackingJob?.cancel()
        trackingJob = serviceScope.launch {
            while (isActive) {
                delay(1000) // প্রতি সেকেন্ডে কাউন্ট বাড়বে
                accumulatedTimeMillis += 1000
                continuousSessionMillis += 1000

                // প্রতি ৫ সেকেন্ডে নিশ্চিত ডাটাবেজ ব্যাকআপ
                if (accumulatedTimeMillis % 5000L == 0L) {
                    saveUsageToDatabaseIncremental(currentPackage, 0, 5000L)
                    checkLimits()
                }

                // ৩০ মিনিট, ১ ঘণ্টা, ১.৫ ঘণ্টা, ২ ঘণ্টার পিরিওডিক ব্রেক অ্যালার্ট
                val thirtyMins = 30 * 60 * 1000L
                if (continuousSessionMillis > 0 && continuousSessionMillis % thirtyMins == 0L && !isBreakAlertShowing) {
                    isBreakAlertShowing = true
                    withContext(Dispatchers.Main) {
                        overlayManager.showBreakAlert(
                            minutes = (continuousSessionMillis / 60000).toInt(),
                            onTakeRest = {
                                isBreakAlertShowing = false
                                overlayManager.hideBreakAlert()
                                performGlobalAction(GLOBAL_ACTION_HOME)
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

    private fun stopTimerImmediately() {
        isWatching = false
        trackingJob?.cancel()
    }

    private fun hideBoxAfterDelay() {
        if (hideBoxJob?.isActive == true) return
        hideBoxJob = serviceScope.launch {
            delay(5000) // ৫ সেকেন্ড পর বক্স হারাবে
            continuousSessionMillis = 0L // ভিডিও ছেড়ে দিলে একটানা দেখার সেশন রিসেট
            withContext(Dispatchers.Main) {
                overlayManager.hideTrackingOverlay()
            }
        }
    }

    // লিমিট এনফোর্সমেন্ট
    private fun checkLimits() {
        var isLimitReached = false
        dailyReelLimit?.let { if (currentReelCount >= it) isLimitReached = true }
        dailyTimeLimitMillis?.let { if (accumulatedTimeMillis >= it) isLimitReached = true }

        if (isLimitReached) {
            if (currentPackage == "com.facebook.katana") {
                serviceScope.launch(Dispatchers.Main) { overlayManager.showFacebookBlocker() }
            } else {
                // YouTube, TikTok, Instagram ফোর্স ক্লোজ করে হোমে পাঠিয়ে দেবে
                performGlobalAction(GLOBAL_ACTION_HOME)
            }
        }
    }

    // রাত ১২:০০ টায় কাউন্টার অটোমেটিক ০ করার ব্যবস্থা
    private fun checkAndPerformMidnightReset() {
        val today = getTodayDateString()
        if (today != currentTrackingDate) {
            currentTrackingDate = today
            currentReelCount = 0
            accumulatedTimeMillis = 0L
            continuousSessionMillis = 0L
            fetchTodayDataAndLimitsFromDB()
        }
    }

    private fun saveUsageToDatabaseIncremental(pkg: String, countAdd: Int, timeAdd: Long) {
        if (countAdd == 0 && timeAdd == 0L) return
        val date = getTodayDateString()
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

    private fun fetchTodayDataAndLimitsFromDB() {
        val date = getTodayDateString()
        serviceScope.launch(Dispatchers.IO) {
            try {
                val db = GenZDatabase.getDatabase(applicationContext).openHelper.readableDatabase
                val cursor = db.query("SELECT reelCount, watchTimeMillis FROM daily_usage WHERE date = ? AND packageName = ?", arrayOf(date, currentPackage))
                if (cursor.moveToFirst()) {
                    currentReelCount = cursor.getInt(0)
                    accumulatedTimeMillis = cursor.getLong(1)
                } else {
                    currentReelCount = 0
                    accumulatedTimeMillis = 0L
                }
                cursor.close()

                val limitCursor = db.query("SELECT maxReels, maxTimeMillis FROM app_limits WHERE packageName = ?", arrayOf(currentPackage))
                if (limitCursor.moveToFirst()) {
                    dailyReelLimit = if (limitCursor.isNull(0)) null else limitCursor.getInt(0)
                    dailyTimeLimitMillis = if (limitCursor.isNull(1)) null else limitCursor.getLong(1)
                } else {
                    dailyReelLimit = null
                    dailyTimeLimitMillis = null
                }
                limitCursor.close()
            } catch (e: Exception) { e.printStackTrace() }
        }
    }

    private fun getTodayDateString(): String = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())

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
