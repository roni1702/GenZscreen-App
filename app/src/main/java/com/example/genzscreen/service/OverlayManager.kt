package com.example.genzscreen.service

import android.content.Context
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.graphics.Typeface
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.TextView

class OverlayManager(private val context: Context) {
    private val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private var trackingView: View? = null
    private var blockerView: View? = null

    // 1. Top-Center Tracking Window (Transparent, Dark Green border)
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
                y = 80 // Margin from top
            }

            val layout = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                setPadding(40, 15, 40, 15)
                
                // গোল কোনা, স্বচ্ছ ব্যাকগ্রাউন্ড এবং গাঢ় সবুজ বর্ডার
                val shape = GradientDrawable().apply {
                    shape = GradientDrawable.RECTANGLE
                    cornerRadius = 50f
                    setColor(Color.TRANSPARENT)
                    setStroke(6, Color.parseColor("#006400")) // Dark Green
                }
                background = shape
            }

            // শুধু রিলস সংখ্যা (Reels লেখা বাদ)
            val tvCount = TextView(context).apply {
                text = "$reelCount  |  " 
                setTextColor(Color.parseColor("#1A73E8")) // Blue
                textSize = 15f
                setTypeface(null, Typeface.BOLD)
            }

            // সময়
            val tvTime = TextView(context).apply {
                text = timeFormatted
                setTextColor(Color.parseColor("#D93025")) // Red
                textSize = 15f
                setTypeface(null, Typeface.BOLD)
            }

            layout.addView(tvCount)
            layout.addView(tvTime)

            trackingView = layout
            windowManager.addView(trackingView, params)
        } else {
            val layout = trackingView as LinearLayout
            (layout.getChildAt(0) as TextView).text = "$reelCount  |  "
            (layout.getChildAt(1) as TextView).text = timeFormatted
        }
    }

    fun hideTrackingOverlay() {
        trackingView?.let {
            windowManager.removeView(it)
            trackingView = null
        }
    }

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
            setBackgroundColor(Color.parseColor("#F5F5F5"))
            
            val tvMsg = TextView(context).apply {
                text = "Your Daily Limit Reached"
                setTextColor(Color.parseColor("#1F1F1F"))
                textSize = 22f
                setTypeface(null, Typeface.BOLD)
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
