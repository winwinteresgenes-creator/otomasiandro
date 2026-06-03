package com.autopilot.assistant.engine

import android.accessibilityservice.AccessibilityService
import android.os.Bundle
import android.view.accessibility.AccessibilityNodeInfo
import com.autopilot.assistant.model.GlobalAction
import com.autopilot.assistant.model.Macro
import com.autopilot.assistant.model.MatchMode
import com.autopilot.assistant.model.ScrollDirection
import com.autopilot.assistant.model.Step
import kotlinx.coroutines.delay

/**
 * Executes a [Macro] step by step against the live UI exposed by an
 * [AccessibilityService]. Designed to be cancellable: run it from a coroutine
 * scope and cancel the job to stop a macro mid-flight.
 */
class MacroEngine(
    private val service: AccessibilityService,
    private val log: (String) -> Unit = {},
) {

    /** Runs [macro], returning true if every (non-optional) step succeeded. */
    suspend fun run(macro: Macro): Boolean {
        log("▶ Running macro: ${macro.name} (${macro.steps.size} steps)")
        macro.steps.forEachIndexed { index, step ->
            val label = "[${index + 1}/${macro.steps.size}] ${step.type}"
            val ok = runCatching { execute(step) }.getOrElse { error ->
                log("$label threw: ${error.message}")
                false
            }
            if (ok) {
                log("$label ✓")
            } else if (step.optional) {
                log("$label skipped (optional, not satisfied)")
            } else {
                log("$label ✗ — aborting macro")
                return false
            }
        }
        log("✔ Macro finished: ${macro.name}")
        return true
    }

    private suspend fun execute(step: Step): Boolean = when (step.type) {
        "launch_app", "launch" -> launchApp(step.packageName)
        "click", "click_text" -> step.text?.let { clickNode(findByText(it, step.match)) } ?: false
        "click_id" -> step.viewId?.let { clickNode(NodeFinder.findByViewId(root(), it)) } ?: false
        "click_desc" -> step.description?.let { clickNode(NodeFinder.findByDescription(root(), it, step.match)) } ?: false
        "set_text", "type" -> setText(step)
        "tap" -> Gestures.tap(service, step.x, step.y)
        "swipe" -> Gestures.swipe(service, step.x, step.y, step.x2, step.y2, step.durationMs)
        "scroll" -> scroll(step.direction, step.durationMs)
        "wait", "delay" -> { delay(step.durationMs); true }
        "wait_for_text" -> step.text?.let { waitForText(it, step.match, step.timeoutMs) } ?: false
        "wait_for_id" -> step.viewId?.let { waitForId(it, step.timeoutMs) } ?: false
        "back", "home", "recents", "notifications", "global" -> globalAction(step.globalAction)
        else -> {
            log("Unknown step type: ${step.type}")
            false
        }
    }

    private fun root(): AccessibilityNodeInfo? = service.rootInActiveWindow

    private fun findByText(text: String, match: MatchMode) = NodeFinder.findByText(root(), text, match)

    private suspend fun clickNode(node: AccessibilityNodeInfo?): Boolean {
        if (node == null) return false
        val clickable = NodeFinder.clickableAncestor(node)
        if (clickable != null && clickable.performAction(AccessibilityNodeInfo.ACTION_CLICK)) {
            return true
        }
        // Fall back to tapping the node's on-screen center.
        val rect = NodeFinder.boundsInScreen(node)
        if (rect.width() <= 0 || rect.height() <= 0) return false
        return Gestures.tap(service, rect.exactCenterX(), rect.exactCenterY())
    }

    private fun setText(step: Step): Boolean {
        val target = when {
            step.viewId != null -> NodeFinder.findByViewId(root(), step.viewId)
            step.description != null -> NodeFinder.findByDescription(root(), step.description, step.match)
            step.text != null -> findByText(step.text, step.match)
            else -> null
        } ?: return false
        val editable = if (target.isEditable) target else NodeFinder.clickableAncestor(target) ?: target
        val args = Bundle().apply {
            putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, step.text ?: "")
        }
        return editable.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)
    }

    private suspend fun scroll(direction: ScrollDirection, durationMs: Long): Boolean {
        val metrics = service.resources.displayMetrics
        val w = metrics.widthPixels.toFloat()
        val h = metrics.heightPixels.toFloat()
        val cx = w / 2f
        val cy = h / 2f
        val dx = w * 0.35f
        val dy = h * 0.35f
        return when (direction) {
            // Swiping up moves content up → scrolls the list down.
            ScrollDirection.DOWN -> Gestures.swipe(service, cx, cy + dy, cx, cy - dy, durationMs)
            ScrollDirection.UP -> Gestures.swipe(service, cx, cy - dy, cx, cy + dy, durationMs)
            ScrollDirection.LEFT -> Gestures.swipe(service, cx + dx, cy, cx - dx, cy, durationMs)
            ScrollDirection.RIGHT -> Gestures.swipe(service, cx - dx, cy, cx + dx, cy, durationMs)
        }
    }

    private suspend fun waitForText(text: String, match: MatchMode, timeoutMs: Long): Boolean =
        pollUntil(timeoutMs) { NodeFinder.findByText(root(), text, match) != null }

    private suspend fun waitForId(viewId: String, timeoutMs: Long): Boolean =
        pollUntil(timeoutMs) { NodeFinder.findByViewId(root(), viewId) != null }

    private suspend fun pollUntil(timeoutMs: Long, condition: () -> Boolean): Boolean {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            if (condition()) return true
            delay(POLL_INTERVAL_MS)
        }
        return condition()
    }

    private fun launchApp(packageName: String?): Boolean {
        if (packageName.isNullOrBlank()) return false
        val intent = service.packageManager.getLaunchIntentForPackage(packageName) ?: return false
        intent.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
        service.startActivity(intent)
        return true
    }

    private fun globalAction(action: GlobalAction): Boolean {
        val code = when (action) {
            GlobalAction.BACK -> AccessibilityService.GLOBAL_ACTION_BACK
            GlobalAction.HOME -> AccessibilityService.GLOBAL_ACTION_HOME
            GlobalAction.RECENTS -> AccessibilityService.GLOBAL_ACTION_RECENTS
            GlobalAction.NOTIFICATIONS -> AccessibilityService.GLOBAL_ACTION_NOTIFICATIONS
        }
        return service.performGlobalAction(code)
    }

    companion object {
        private const val POLL_INTERVAL_MS = 200L
    }
}
