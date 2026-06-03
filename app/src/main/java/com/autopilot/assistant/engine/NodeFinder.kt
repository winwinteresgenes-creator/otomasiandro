package com.autopilot.assistant.engine

import android.graphics.Rect
import android.view.accessibility.AccessibilityNodeInfo
import com.autopilot.assistant.model.MatchMode

/**
 * Helpers for locating [AccessibilityNodeInfo]s inside the current window tree.
 *
 * Target apps (Shopee, TikTok, Instagram, Facebook, ...) obfuscate their view
 * ids and change layouts often, so matching by visible [text] or
 * [contentDescription] is usually more robust than matching by id.
 */
object NodeFinder {

    fun findByText(root: AccessibilityNodeInfo?, text: String, match: MatchMode): AccessibilityNodeInfo? =
        firstOrNull(root) { node -> textMatches(node.text?.toString(), text, match) }

    fun findByViewId(root: AccessibilityNodeInfo?, viewId: String): AccessibilityNodeInfo? {
        if (root == null) return null
        val matches = root.findAccessibilityNodeInfosByViewId(viewId)
        return matches?.firstOrNull()
    }

    fun findByDescription(root: AccessibilityNodeInfo?, description: String, match: MatchMode): AccessibilityNodeInfo? =
        firstOrNull(root) { node -> textMatches(node.contentDescription?.toString(), description, match) }

    /** Walks up from [node] until it finds an ancestor that is clickable. */
    fun clickableAncestor(node: AccessibilityNodeInfo?): AccessibilityNodeInfo? {
        var current = node
        while (current != null) {
            if (current.isClickable) return current
            current = current.parent
        }
        return null
    }

    fun boundsInScreen(node: AccessibilityNodeInfo): Rect {
        val rect = Rect()
        node.getBoundsInScreen(rect)
        return rect
    }

    private inline fun firstOrNull(
        root: AccessibilityNodeInfo?,
        predicate: (AccessibilityNodeInfo) -> Boolean,
    ): AccessibilityNodeInfo? {
        if (root == null) return null
        val queue = ArrayDeque<AccessibilityNodeInfo>()
        queue.add(root)
        while (queue.isNotEmpty()) {
            val node = queue.removeFirst()
            if (predicate(node)) return node
            for (i in 0 until node.childCount) {
                node.getChild(i)?.let { queue.add(it) }
            }
        }
        return null
    }

    private fun textMatches(candidate: String?, target: String, match: MatchMode): Boolean {
        if (candidate == null) return false
        return when (match) {
            MatchMode.EXACT -> candidate == target
            MatchMode.IGNORE_CASE -> candidate.equals(target, ignoreCase = true)
            MatchMode.CONTAINS -> candidate.contains(target, ignoreCase = true)
        }
    }
}
