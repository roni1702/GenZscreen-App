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
        audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        
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

        // অন্য অ্যাপে গেলে বক্স বন্ধ করে দিবে
        if (!targetPackages.contains(packageName)) {
            if (isWatching) stopWatchingSession()
            currentPackage = ""
            return
        }

        if (packageName != currentPackage) {
            currentPackage = packageName
            fetchTodayCountFromDB()
        }

        // 🚀 স্মার্ট ডিটেকশন (বিনা ল্যাগে): অডিও বাজছে কিনা বা স্ক্রল হচ্ছে কিনা
        val isAudioPlaying = audioManager.isMusicActive
        val isScroll = event.eventType == AccessibilityEvent.TYPE_VIEW_SCROLLED
        
        // ফেসবুক/ইউটিউবে অডিও বাজলে বা স্ক্রল করলেই আমরা ধরে নিব ভিডিও/রিলস চলছে
        if (isAudioPlaying || isScroll) {
            stopJob?.cancel() // বক্স গায়েবের টাইমার বাতিল
            
            if (!isWatching) {
                startWatchingSession()
            }

            // রিলস স্ক্রল করলে কাউন্ট বাড়ানো
            if (isScroll) {
                val timeSpent = System.currentTimeMillis() - videoStartTime
                if (timeSpent >= 3000L) { // ৩ সেকেন্ড দেখলে তবেই ১টি রিলস কাউন্ট হবে
                    currentReelCount++
                    saveUsageToDatabaseIncremental(currentPackage, 1, 0L)
                    videoStartTime = System.currentTimeMillis() // সময় রিসেট
                }
            }
        } else {
            // ভিডিও বন্ধ হলে সাথে সাথে বক্স না লুকিয়ে ৪ সেকেন্ড বাফার দেওয়া
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
                delay(1000) // প্রতি সেকেন্ডে কাউন্ট বাড়বে
                accumulatedTimeMillis += 1000
                
                // ডেটা লস এড়াতে প্রতি ৫ সেকেন্ডে ডাটাবেজে সেভ
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

    // নিখুঁতভাবে ডেটা সেভ করার সেফ মেথড (কোনো ক্র্যাশ হবে না)
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
            } catch (e: Exception) { 
                e.printStackTrace() 
            }
        }
    }

    private fun fetchTodayCountFromDB() {
        val date = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
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
