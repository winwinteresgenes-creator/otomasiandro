package com.autopilot.assistant.service

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.TextView
import com.autopilot.assistant.R
import com.autopilot.assistant.ui.MainActivity
import kotlin.math.abs

/**
 * A draggable floating control rendered as a `TYPE_ACCESSIBILITY_OVERLAY`
 * window. This window type is available to accessibility services without the
 * `SYSTEM_ALERT_WINDOW` ("draw over other apps") runtime permission.
 *
 * Tap behaviour: stop the running macro, or open the app when idle.
 */
class OverlayController(private val service: AutomationAccessibilityService) {

    private val windowManager =
        service.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private var root: View? = null
    private var label: TextView? = null

    private val params = WindowManager.LayoutParams(
        WindowManager.LayoutParams.WRAP_CONTENT,
        WindowManager.LayoutParams.WRAP_CONTENT,
        WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
        PixelFormat.TRANSLUCENT,
    ).apply {
        gravity = Gravity.TOP or Gravity.START
        x = 24
        y = 240
    }

    @SuppressLint("ClickableViewAccessibility", "InflateParams")
    fun show() {
        if (root != null) return
        val view = LayoutInflater.from(service).inflate(R.layout.overlay_controls, null)
        label = view.findViewById(R.id.overlayLabel)
        view.setOnTouchListener(DragTapListener())
        root = view
        windowManager.addView(view, params)
        setRunning(service.isRunning)
    }

    fun hide() {
        root?.let { runCatching { windowManager.removeView(it) } }
        root = null
        label = null
    }

    fun setRunning(running: Boolean) {
        label?.post {
            label?.text = if (running) {
                service.getString(R.string.overlay_stop)
            } else {
                service.getString(R.string.overlay_idle)
            }
        }
    }

    private fun onTap() {
        if (service.isRunning) {
            service.stopMacro()
        } else {
            val intent = Intent(service, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            service.startActivity(intent)
        }
    }

    private inner class DragTapListener : View.OnTouchListener {
        private var initialX = 0
        private var initialY = 0
        private var touchX = 0f
        private var touchY = 0f
        private var moved = false

        override fun onTouch(v: View, event: MotionEvent): Boolean {
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    initialX = params.x
                    initialY = params.y
                    touchX = event.rawX
                    touchY = event.rawY
                    moved = false
                    return true
                }

                MotionEvent.ACTION_MOVE -> {
                    val dx = (event.rawX - touchX).toInt()
                    val dy = (event.rawY - touchY).toInt()
                    if (abs(dx) > TOUCH_SLOP || abs(dy) > TOUCH_SLOP) moved = true
                    params.x = initialX + dx
                    params.y = initialY + dy
                    root?.let { windowManager.updateViewLayout(it, params) }
                    return true
                }

                MotionEvent.ACTION_UP -> {
                    if (!moved) onTap()
                    return true
                }
            }
            return false
        }
    }

    companion object {
        private const val TOUCH_SLOP = 12
    }
}
