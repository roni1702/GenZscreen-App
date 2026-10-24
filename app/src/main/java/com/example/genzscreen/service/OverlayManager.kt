package com.example.genzscreen.service

import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.view.animation.DecelerateInterpolator
import android.widget.LinearLayout
import android.widget.TextView

class OverlayManager(private val context: Context) {
    private val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private var trackingView: View? = null
    private var blockerView: View? = null
    private var breakAlertView: View? = null

    private var isExpanded = false

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
                y = 20 // স্ক্রিনের একদম ওপরে পরিপাটি অবস্থান
            }

            // ফ্রস্টেড গ্লাস কন্টেইনার (হালকা স্বচ্ছ ডার্ক ব্যাকগ্রাউন্ড + চিকন হালকা সবুজ বর্ডার)
            val layout = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER
                setPadding(dpToPx(16), dpToPx(8), dpToPx(16), dpToPx(8))
                
                background = GradientDrawable().apply {
                    shape = GradientDrawable.RECTANGLE
                    cornerRadius = dpToPx(25).toFloat()
                    // ফ্রস্টেড কাচের মতো স্বচ্ছ ডার্ক ব্যাকগ্রাউন্ড (৮৫% অপাসিটি)
                    setColor(Color.parseColor("#D90F172A"))
                    // হালকা চিকন সবুজ বর্ডার
                    setStroke(dpToPx(1.5f), Color.parseColor("#4ADE80"))
                }
            }

            // রিলস সংখ্যা (উজ্জ্বল স্কাই ব্লু - কাচের ওপর স্পষ্ট)
            val tvCount = TextView(context).apply {
                text = "$reelCount"
                setTextColor(Color.parseColor("#38BDF8"))
                textSize = 14f
                setTypeface(null, Typeface.BOLD)
                alpha = 0f // শুরুতে হাইড থাকবে
            }

            // ডিভাইডার
            val tvDivider = TextView(context).apply {
                text = "  |  "
                setTextColor(Color.parseColor("#64748B"))
                textSize = 14f
                alpha = 0f
            }

            // সময় (উজ্জ্বল কোরাল রেড)
            val tvTime = TextView(context).apply {
                text = timeFormatted
                setTextColor(Color.parseColor("#F87171"))
                textSize = 14f
                setTypeface(null, Typeface.BOLD)
                alpha = 0f
            }

            layout.addView(tvCount)
            layout.addView(tvDivider)
            layout.addView(tvTime)

            trackingView = layout
            windowManager.addView(trackingView, params)
            isExpanded = false

            // আইফোনের মতো এক্সপ্যানশন অ্যানিমেশন চালানো
            animateIslandExpansion(layout, tvCount, tvDivider, tvTime)
        } else {
            val layout = trackingView as LinearLayout
            val tvCount = layout.getChildAt(0) as TextView
            val tvTime = layout.getChildAt(2) as TextView

            tvCount.text = "$reelCount"
            tvTime.text = timeFormatted
        }
    }

    // আইফোনের মিউজিক পিলের মতো ছোট বৃত্ত থেকে দুই পাশে বড় হওয়ার অ্যানিমেশন
    private fun animateIslandExpansion(
        layout: LinearLayout,
        tvCount: TextView,
        tvDivider: TextView,
        tvTime: TextView
    ) {
        layout.measure(View.MeasureSpec.UNSPECIFIED, View.MeasureSpec.UNSPECIFIED)
        val targetWidth = layout.measuredWidth
        val initialCircleWidth = dpToPx(38)

        val widthAnimator = ValueAnimator.ofInt(initialCircleWidth, targetWidth).apply {
            duration = 380
            interpolator = DecelerateInterpolator()
            addUpdateListener { anim ->
                val width = anim.animatedValue as Int
                val lp = layout.layoutParams
                lp.width = width
                layout.layoutParams = lp
            }
        }

        // লেখাগুলো আস্তে আস্তে ফেড-ইন হয়ে ভেসে উঠবে
        val textFadeIn = AnimatorSet().apply {
            playTogether(
                ObjectAnimator.ofFloat(tvCount, "alpha", 0f, 1f),
                ObjectAnimator.ofFloat(tvDivider, "alpha", 0f, 1f),
                ObjectAnimator.ofFloat(tvTime, "alpha", 0f, 1f)
            )
            duration = 200
        }

        AnimatorSet().apply {
            playSequentially(widthAnimator, textFadeIn)
            start()
        }
    }

    fun hideTrackingOverlay() {
        trackingView?.let { view ->
            try {
                windowManager.removeView(view)
            } catch (e: Exception) { }
            trackingView = null
            isExpanded = false
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
            setBackgroundColor(Color.parseColor("#F8F9FA"))

            addView(TextView(context).apply {
                text = "Your Daily Limit Reached"
                setTextColor(Color.parseColor("#1F1F1F"))
                textSize = 22f
                setTypeface(null, Typeface.BOLD)
            })
        }
        blockerView = layout
        windowManager.addView(blockerView, params)
    }

    fun hideFacebookBlocker() {
        blockerView?.let {
            try { windowManager.removeView(it) } catch (e: Exception) { }
            blockerView = null
        }
    }

    fun showBreakAlert(minutes: Int, onTakeRest: () -> Unit, onNoThanks: () -> Unit) {
        if (breakAlertView != null) return
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        )

        val rootLayout = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setBackgroundColor(Color.parseColor("#99000000"))
        }

        val card = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(dpToPx(24), dpToPx(24), dpToPx(24), dpToPx(24))
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dpToPx(16).toFloat()
                setColor(Color.WHITE)
            }
        }

        val tvMsg = TextView(context).apply {
            text = "You are watching reels/video for $minutes minutes."
            setTextColor(Color.parseColor("#1F1F1F"))
            textSize = 17f
            setTypeface(null, Typeface.BOLD)
            setPadding(0, 0, 0, dpToPx(20))
            gravity = Gravity.CENTER
        }

        val btnRest = TextView(context).apply {
            text = "Take rest"
            setTextColor(Color.WHITE)
            textSize = 15f
            gravity = Gravity.CENTER
            setPadding(0, dpToPx(12), 0, dpToPx(12))
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dpToPx(10).toFloat()
                setColor(Color.parseColor("#1A73E8"))
            }
            setOnClickListener { onTakeRest() }
        }

        val btnNo = TextView(context).apply {
            text = "No thanks"
            setTextColor(Color.parseColor("#5F6368"))
            textSize = 15f
            gravity = Gravity.CENTER
            setPadding(0, dpToPx(12), 0, dpToPx(12))
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dpToPx(10).toFloat()
                setColor(Color.parseColor("#F1F5F9"))
            }
            setOnClickListener { onNoThanks() }
        }

        val btnLayout = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(dpToPx(220), ViewGroup.LayoutParams.WRAP_CONTENT)
            addView(btnRest)
            addView(View(context).apply { layoutParams = LinearLayout.LayoutParams(1, dpToPx(10)) })
            addView(btnNo)
        }

        card.addView(tvMsg)
        card.addView(btnLayout)
        rootLayout.addView(card)

        breakAlertView = rootLayout
        windowManager.addView(breakAlertView, params)
    }

    fun hideBreakAlert() {
        breakAlertView?.let {
            try { windowManager.removeView(it) } catch (e: Exception) { }
            breakAlertView = null
        }
    }

    private fun dpToPx(dp: Float): Int = (dp * context.resources.displayMetrics.density).toInt()
    private fun dpToPx(dp: Int): Int = (dp * context.resources.displayMetrics.density).toInt()
}
