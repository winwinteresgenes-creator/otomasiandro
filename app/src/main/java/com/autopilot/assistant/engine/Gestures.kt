package com.autopilot.assistant.engine

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import kotlin.coroutines.resume
import kotlinx.coroutines.suspendCancellableCoroutine

/**
 * Coordinate-based input (tap / swipe / scroll) via
 * [AccessibilityService.dispatchGesture]. This is the fallback for content that
 * exposes no clickable accessibility node — e.g. game canvases, video feeds, or
 * custom-drawn UI.
 *
 * Coordinates are absolute screen pixels.
 */
object Gestures {

    suspend fun tap(service: AccessibilityService, x: Float, y: Float, durationMs: Long = 50L): Boolean {
        val path = Path().apply { moveTo(x, y) }
        return dispatch(service, path, 0L, durationMs.coerceAtLeast(1L))
    }

    suspend fun swipe(
        service: AccessibilityService,
        x1: Float,
        y1: Float,
        x2: Float,
        y2: Float,
        durationMs: Long = 300L,
    ): Boolean {
        val path = Path().apply {
            moveTo(x1, y1)
            lineTo(x2, y2)
        }
        return dispatch(service, path, 0L, durationMs.coerceAtLeast(1L))
    }

    private suspend fun dispatch(
        service: AccessibilityService,
        path: Path,
        startTime: Long,
        duration: Long,
    ): Boolean = suspendCancellableCoroutine { cont ->
        val stroke = GestureDescription.StrokeDescription(path, startTime, duration)
        val gesture = GestureDescription.Builder().addStroke(stroke).build()
        val dispatched = service.dispatchGesture(
            gesture,
            object : AccessibilityService.GestureResultCallback() {
                override fun onCompleted(gestureDescription: GestureDescription?) {
                    if (cont.isActive) cont.resume(true)
                }

                override fun onCancelled(gestureDescription: GestureDescription?) {
                    if (cont.isActive) cont.resume(false)
                }
            },
            null,
        )
        if (!dispatched && cont.isActive) cont.resume(false)
    }
}
