package com.autopilot.assistant.model

import org.json.JSONObject

/**
 * How a text/description selector should be matched against on-screen nodes.
 */
enum class MatchMode {
    EXACT,
    CONTAINS,
    IGNORE_CASE;

    companion object {
        fun from(value: String?): MatchMode = when (value?.lowercase()) {
            "exact" -> EXACT
            "ignore_case", "ignorecase" -> IGNORE_CASE
            else -> CONTAINS
        }
    }
}

enum class ScrollDirection {
    UP, DOWN, LEFT, RIGHT;

    companion object {
        fun from(value: String?): ScrollDirection = when (value?.lowercase()) {
            "up" -> UP
            "left" -> LEFT
            "right" -> RIGHT
            else -> DOWN
        }
    }
}

enum class GlobalAction {
    BACK, HOME, RECENTS, NOTIFICATIONS;

    companion object {
        fun from(value: String?): GlobalAction = when (value?.lowercase()) {
            "home" -> HOME
            "recents" -> RECENTS
            "notifications" -> NOTIFICATIONS
            else -> BACK
        }
    }
}

/**
 * A single instruction executed by [com.autopilot.assistant.engine.MacroEngine].
 *
 * The set of fields used depends on [type]. Steps are intentionally described as
 * plain data so that flows for new apps can be added by editing JSON only — no
 * recompilation required.
 */
data class Step(
    val type: String,
    val text: String? = null,
    val viewId: String? = null,
    val description: String? = null,
    val match: MatchMode = MatchMode.CONTAINS,
    val packageName: String? = null,
    val x: Float = 0f,
    val y: Float = 0f,
    val x2: Float = 0f,
    val y2: Float = 0f,
    val durationMs: Long = 300L,
    val timeoutMs: Long = 5000L,
    val direction: ScrollDirection = ScrollDirection.DOWN,
    val globalAction: GlobalAction = GlobalAction.BACK,
    val optional: Boolean = false,
) {
    companion object {
        fun fromJson(obj: JSONObject): Step = Step(
            type = obj.getString("type").trim().lowercase(),
            text = obj.optStringOrNull("text"),
            viewId = obj.optStringOrNull("viewId") ?: obj.optStringOrNull("id"),
            description = obj.optStringOrNull("description") ?: obj.optStringOrNull("desc"),
            match = MatchMode.from(obj.optStringOrNull("match")),
            packageName = obj.optStringOrNull("packageName") ?: obj.optStringOrNull("package"),
            x = obj.optDouble("x", 0.0).toFloat(),
            y = obj.optDouble("y", 0.0).toFloat(),
            x2 = obj.optDouble("x2", 0.0).toFloat(),
            y2 = obj.optDouble("y2", 0.0).toFloat(),
            durationMs = obj.optLong("durationMs", 300L),
            timeoutMs = obj.optLong("timeoutMs", 5000L),
            direction = ScrollDirection.from(obj.optStringOrNull("direction")),
            globalAction = GlobalAction.from(obj.optStringOrNull("globalAction")),
            optional = obj.optBoolean("optional", false),
        )
    }
}

data class Macro(
    val id: String,
    val name: String,
    val description: String = "",
    val targetPackage: String? = null,
    val steps: List<Step>,
) {
    companion object {
        fun fromJson(obj: JSONObject): Macro {
            val stepsArray = obj.optJSONArray("steps")
            val steps = buildList {
                if (stepsArray != null) {
                    for (i in 0 until stepsArray.length()) {
                        add(Step.fromJson(stepsArray.getJSONObject(i)))
                    }
                }
            }
            return Macro(
                id = obj.optStringOrNull("id") ?: obj.getString("name"),
                name = obj.getString("name"),
                description = obj.optString("description", ""),
                targetPackage = obj.optStringOrNull("targetPackage") ?: obj.optStringOrNull("package"),
                steps = steps,
            )
        }
    }
}

private fun JSONObject.optStringOrNull(key: String): String? {
    if (!has(key) || isNull(key)) return null
    val value = optString(key, "")
    return value.ifBlank { null }
}
