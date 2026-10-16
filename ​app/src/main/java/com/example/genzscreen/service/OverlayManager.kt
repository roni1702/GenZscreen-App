package com.example.genzscreen.service

import android.content.Context
import android.graphics.Color
import android.graphics.PixelFormat
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import com.example.genzscreen.R

class OverlayManager(private val context: Context) {
    private val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private var trackingView: View? = null
    private var blockerView: View? = null

    // 1. Top-Center Tracking Window (Green border, Blue count, Red time)
    fun showTrackingOverlay(reelCount: Int, timeFormatted: String) {
        if (trackingView == null) {
            val params = WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
                PixelFormat.TRANSLUCENT
            ).apply {
                gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
                y = 50 // Margin from top
            }

            // Create simple UI programmatically to keep it clean
            val layout = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                setPadding(30, 15, 30, 15)
                setBackgroundColor(Color.parseColor("#F8F9FA")) // Light background
                // Note: In real app, use a drawable for rounded green border
            }

            val tvCount = TextView(context).apply {
                text = "Reels: $reelCount"
                setTextColor(Color.parseColor("#1A73E8")) // Blue
                textSize = 14f
                setPadding(0, 0, 20, 0)
            }

            val tvTime = TextView(context).apply {
                text = timeFormatted
                setTextColor(Color.parseColor("#D93025")) // Red
                textSize = 14f
            }

            layout.addView(tvCount)
            layout.addView(tvTime)

            trackingView = layout
            windowManager.addView(trackingView, params)
        } else {
            // Update existing view
            val layout = trackingView as LinearLayout
            (layout.getChildAt(0) as TextView).text = "Reels: $reelCount"
            (layout.getChildAt(1) as TextView).text = timeFormatted
        }
    }

    fun hideTrackingOverlay() {
        trackingView?.let {
            windowManager.removeView(it)
            trackingView = null
        }
    }

    // 2. Limit Reached Blocker for Facebook
    fun showFacebookBlocker() {
        if (blockerView != null) return
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT
        )

        val layout = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setBackgroundColor(Color.parseColor("#F5F5F5")) // Clean Grey Background
            
            val tvMsg = TextView(context).apply {
                text = "Your Daily Limit Reached"
                setTextColor(Color.parseColor("#1F1F1F"))
                textSize = 22f
            }
            addView(tvMsg)
        }

        blockerView = layout
        windowManager.addView(blockerView, params)
    }

    fun hideFacebookBlocker() {
        blockerView?.let {
            windowManager.removeView(it)
            blockerView = null
        }
    }
}
