package com.autopilot.assistant.service

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.view.accessibility.AccessibilityEvent
import android.widget.Toast
import com.autopilot.assistant.engine.MacroEngine
import com.autopilot.assistant.model.Macro
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * The single always-on AccessibilityService that drives cross-app automation.
 *
 * It exposes a draggable floating control (via [OverlayController]) so a macro
 * can be stopped from anywhere, and runs macros on a cancellable coroutine
 * scope. A process-wide [instance] lets [com.autopilot.assistant.ui.MainActivity]
 * trigger runs while the service stays bound in the background.
 */
class AutomationAccessibilityService : AccessibilityService() {

    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var currentJob: Job? = null
    private var overlay: OverlayController? = null

    @Volatile
    var isRunning: Boolean = false
        private set

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        overlay = OverlayController(this).also { it.show() }
        toast("AutoPilot connected")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // Reserved for event-driven triggers (e.g. auto-dismiss popups).
        // The current macro model is imperative, so nothing is needed here yet.
    }

    override fun onInterrupt() {}

    override fun onUnbind(intent: Intent?): Boolean {
        stopMacro()
        overlay?.hide()
        overlay = null
        scope.cancel()
        if (instance === this) instance = null
        return super.onUnbind(intent)
    }

    fun runMacro(macro: Macro) {
        stopMacro()
        isRunning = true
        overlay?.setRunning(true)
        val engine = MacroEngine(this) { line -> android.util.Log.d(TAG, line) }
        currentJob = scope.launch {
            try {
                engine.run(macro)
            } finally {
                isRunning = false
                overlay?.setRunning(false)
            }
        }
    }

    fun stopMacro() {
        currentJob?.cancel()
        currentJob = null
        isRunning = false
        overlay?.setRunning(false)
    }

    private fun toast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }

    companion object {
        private const val TAG = "AutoPilot"

        @Volatile
        var instance: AutomationAccessibilityService? = null
            private set

        fun isConnected(): Boolean = instance != null
    }
}
