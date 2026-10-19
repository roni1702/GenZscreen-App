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
    private var breakAlertView: View? = null

    fun showTrackingOverlay(reelCount: Int, timeFormatted: String) {
        if (trackingView == null) {
            val params = WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT, WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
                PixelFormat.TRANSLUCENT
            ).apply { gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL; y = 25 }

            val layout = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                setPadding(35, 12, 35, 12)
                background = GradientDrawable().apply {
                    shape = GradientDrawable.RECTANGLE
                    cornerRadius = 50f
                    setColor(Color.TRANSPARENT)
                    setStroke(3, Color.parseColor("#4CAF50"))
                }
            }

            val tvCount = TextView(context).apply {
                text = "$reelCount  |  " 
                setTextColor(Color.parseColor("#1A73E8"))
                textSize = 15f; setTypeface(null, Typeface.BOLD)
            }
            val tvTime = TextView(context).apply {
                text = timeFormatted
                setTextColor(Color.parseColor("#D93025"))
                textSize = 15f; setTypeface(null, Typeface.BOLD)
            }

            layout.addView(tvCount); layout.addView(tvTime)
            trackingView = layout
            windowManager.addView(trackingView, params)
        } else {
            val layout = trackingView as LinearLayout
            (layout.getChildAt(0) as TextView).text = "$reelCount  "
            (layout.getChildAt(1) as TextView).text = timeFormatted
        }
    }

    fun hideTrackingOverlay() {
        trackingView?.let { windowManager.removeView(it); trackingView = null }
    }

    fun showFacebookBlocker() {
        if (blockerView != null) return
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL, PixelFormat.TRANSLUCENT
        )
        val layout = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER
            setBackgroundColor(Color.parseColor("#F5F5F5"))
            addView(TextView(context).apply {
                text = "Your Daily Limit Reached"
                setTextColor(Color.parseColor("#1F1F1F")); textSize = 22f; setTypeface(null, Typeface.BOLD)
            })
        }
        blockerView = layout
        windowManager.addView(blockerView, params)
    }

    fun hideFacebookBlocker() {
        blockerView?.let { windowManager.removeView(it); blockerView = null }
    }

    // ৩০ মিনিটের ব্রেক অ্যালার্টের ডিজাইন
    fun showBreakAlert(minutes: Int, onTakeRest: () -> Unit, onNoThanks: () -> Unit) {
        if (breakAlertView != null) return
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE, PixelFormat.TRANSLUCENT
        )

        val rootLayout = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER
            setBackgroundColor(Color.parseColor("#99000000")) // স্বচ্ছ কালো ব্যাকগ্রাউন্ড
        }

        val card = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER
            setPadding(60, 60, 60, 60)
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE; cornerRadius = 30f; setColor(Color.WHITE)
            }
        }

        val tvMsg = TextView(context).apply {
            text = "You are watching reels/video for $minutes minutes."
            setTextColor(Color.parseColor("#1F1F1F")); textSize = 18f
            setPadding(0, 0, 0, 40); gravity = Gravity.CENTER
        }

        val btnRest = TextView(context).apply {
            text = "Take rest"
            setTextColor(Color.WHITE); textSize = 16f; gravity = Gravity.CENTER
            setPadding(0, 20, 0, 20)
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE; cornerRadius = 20f; setColor(Color.parseColor("#1A73E8"))
            }
            setOnClickListener { onTakeRest() }
        }

        val btnNo = TextView(context).apply {
            text = "No thanks"
            setTextColor(Color.parseColor("#5F6368")); textSize = 16f; gravity = Gravity.CENTER
            setPadding(0, 20, 0, 20)
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE; cornerRadius = 20f; setColor(Color.parseColor("#F1F5F9"))
            }
            setOnClickListener { onNoThanks() }
        }

        val btnLayout = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL; layoutParams = LinearLayout.LayoutParams(500, LinearLayout.LayoutParams.WRAP_CONTENT)
            addView(btnRest); addView(View(context).apply { layoutParams = LinearLayout.LayoutParams(1, 20) }); addView(btnNo)
        }

        card.addView(tvMsg); card.addView(btnLayout)
        rootLayout.addView(card)

        breakAlertView = rootLayout
        windowManager.addView(breakAlertView, params)
    }

    fun hideBreakAlert() {
        breakAlertView?.let { windowManager.removeView(it); breakAlertView = null }
    }
}
