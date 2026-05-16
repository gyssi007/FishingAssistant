package com.fishtime.assistant

import android.accessibilityservice.AccessibilityService
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.util.Log

class FishingAccessibilityService : AccessibilityService() {

    override fun onAccessibilityEvent(
        event: AccessibilityEvent?
    ) {

        if (!AppState.isRunning) {
            return
        }

        if (
            event?.eventType ==
            AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED
            ||
            event?.eventType ==
            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED
        ) {

            scanPage()
        }
    }

    override fun onInterrupt() {

    }

    private fun scanPage() {

        val root = rootInActiveWindow ?: return

        val found =
            findText(
                root,
                "我的钓位"
            )

        if (found) {

            val numbers =
                extractNumbers(root)

            if (numbers.size >= 2) {

                val seatA = numbers[0]

                val seatB = numbers[1]

                Log.d(
                    "FishingAssistant",
                    "识别到: $seatA / $seatB"
                )

                FloatingWindowManager.updateText(
                    "🎯 识别到 $seatA / $seatB"
                )
            }
        }
    }

    private fun findText(
        node: AccessibilityNodeInfo,
        target: String
    ): Boolean {

        val text =
            node.text?.toString() ?: ""

        if (text.contains(target)) {

            return true
        }

        for (i in 0 until node.childCount) {

            val child =
                node.getChild(i)

            if (child != null) {

                if (
                    findText(
                        child,
                        target
                    )
                ) {

                    return true
                }
            }
        }

        return false
    }

    private fun extractNumbers(
        node: AccessibilityNodeInfo
    ): List<String> {

        val result =
            mutableListOf<String>()

        recursiveExtract(
            node,
            result
        )

        return result
            .distinct()
            .filter {

                try {

                    val num =
                        it.toInt()

                    num in 1..99

                } catch (e: Exception) {

                    false
                }
            }
    }

    private fun recursiveExtract(
        node: AccessibilityNodeInfo,
        result: MutableList<String>
    ) {

        val text =
            node.text?.toString() ?: ""

        if (
            text.matches(
                Regex("^\\d{1,2}$")
            )
        ) {

            result.add(text)
        }

        for (i in 0 until node.childCount) {

            val child =
                node.getChild(i)

            if (child != null) {

                recursiveExtract(
                    child,
                    result
                )
            }
        }
    }
}
