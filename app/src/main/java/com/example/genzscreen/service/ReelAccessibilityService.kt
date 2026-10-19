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

    private val targetPackages = listOf(
        "com.facebook.katana",
        "com.google.android.youtube",
        "com.zhiliaoapp.musically",
        "com.instagram.android"
    )

    override fun onServiceConnected() {
        overlayManager = OverlayManager(this)
        val info = AccessibilityServiceInfo().apply {
            eventTypes = AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED or AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED or AccessibilityEvent.TYPE_VIEW_SCROLLED
            feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC
            flags = AccessibilityServiceInfo.DEFAULT
            packageNames = targetPackages.toTypedArray()
        }
        this.serviceInfo = info
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        val packageName = event?.packageName?.toString() ?: return

        // অন্য কোনো অ্যাপে চলে গেলে বক্স বন্ধ করে দেওয়া
        if (!targetPackages.contains(packageName)) {
            if (isWatching) stopWatchingSession()
            return
        }

        // টার্গেট অ্যাপগুলোর মধ্যে সুইচ করলে ডেটা লোড করা
        if (packageName != currentPackage) {
            currentPackage = packageName
            fetchTodayCountFromDB()
        }

        // স্ক্রিনে ভিডিও আছে কিনা চেক করা (Smart Detection)
        val hasVideo = isVideoOnScreen(rootInActiveWindow)

        if (hasVideo) {
            stopJob?.cancel() // বক্স গায়েব হওয়ার টাইমার বাতিল করা
            
            if (!isWatching) {
                startWatchingSession()
            }

            // রিলস স্ক্রল ডিটেক্ট করা
            if (event.eventType == AccessibilityEvent.TYPE_VIEW_SCROLLED) {
                val timeSpent = System.currentTimeMillis() - videoStartTime
                if (timeSpent >= 3000L) { // কমপক্ষে ৩ সেকেন্ড দেখলে তবেই কাউন্ট হবে
                    currentReelCount++
                    saveUsageToDatabaseIncremental(currentPackage, 1, 0L)
                }
                videoStartTime = System.currentTimeMillis()
            }
        } else {
            // ভিডিও স্ক্রিন থেকে বের হলে সাথে সাথে বক্স গায়েব না করে ৫ সেকেন্ড অপেক্ষা করা (Buffer)
            if (isWatching && stopJob?.isActive != true) {
                stopJob = serviceScope.launch {
                    delay(5000)
                    stopWatchingSession()
                }
            }
        }
    }

    private fun isVideoOnScreen(node: AccessibilityNodeInfo?): Boolean {
        if (node == null) return false
        val queue = LinkedList<AccessibilityNodeInfo>()
        queue.add(node)
        
        while (queue.isNotEmpty()) {
            val current = queue.poll() ?: continue
            val className = current.className?.toString() ?: ""
            val viewId = current.viewIdResourceName?.lowercase() ?: ""
            val contentDesc = current.contentDescription?.toString()?.lowercase() ?: ""
            
            // ফেসবুক, ইউটিউব, টিকটক, ইনস্টাগ্রামের ভিডিও ডিটেকশন
            if (className.contains("SurfaceView") || 
                className.contains("TextureView") || 
                className.contains("VideoView") ||
                className.contains("LithoView") || // Facebook এর জন্য
                viewId.contains("video") ||
                viewId.contains("reel") ||
                viewId.contains("player") ||
                viewId.contains("shorts") ||
                contentDesc.contains("video") ||
                contentDesc.contains("reel")) {
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
                delay(1000) // প্রতি সেকেন্ডে কাউন্ট বাড়বে
                accumulatedTimeMillis += 1000
                
                // ডেটা লস এড়াতে প্রতি ৫ সেকেন্ড পরপর ডাটাবেজে সেভ হবে
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

    private fun saveUsageToDatabaseIncremental(pkg: String, countAdd: Int, timeAdd: Long) {
        if (countAdd == 0 && timeAdd == 0L) return
        val date = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
        
        serviceScope.launch(Dispatchers.IO) {
            try {
                val db = GenZDatabase.getDatabase(applicationContext)
                val cursor = db.query("SELECT * FROM daily_usage WHERE date = ? AND packageName = ?", arrayOf(date, pkg))
                val exists = cursor.moveToFirst()
                cursor.close()
                
                if (!exists) {
                    db.usageDao().insertUsage(DailyUsage(date, pkg, 0, 0L))
                }
                db.usageDao().updateUsage(date, pkg, countAdd, timeAdd)
            } catch (e: Exception) { 
                e.printStackTrace() 
            }
        }
    }

    private fun fetchTodayCountFromDB() {
        val date = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
        serviceScope.launch(Dispatchers.IO) {
            try {
                val db = GenZDatabase.getDatabase(applicationContext)
                val cursor = db.query("SELECT reelCount, watchTimeMillis FROM daily_usage WHERE date = ? AND packageName = ?", arrayOf(date, currentPackage))
                if (cursor.moveToFirst()) {
                    currentReelCount = cursor.getInt(0)
                    accumulatedTimeMillis = cursor.getLong(1)
                } else {
                    currentReelCount = 0
                    accumulatedTimeMillis = 0L
                }
                cursor.close()
            } catch (e: Exception) {
                e.printStackTrace()
            }
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
