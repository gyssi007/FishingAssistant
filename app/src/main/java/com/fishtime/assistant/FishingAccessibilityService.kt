package com.fishtime.assistant

import android.accessibilityservice.AccessibilityService
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.util.Log

class FishingAccessibilityService : AccessibilityService() {

    companion object {
        private const val TAG = "FishingAssistant"
        private const val KEY_WORD = "我的钓位"
    }

    // ────────────────────────────────────────
    // 入口
    // ────────────────────────────────────────

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {

        if (!AppState.isRunning) return

        if (
            event?.eventType == AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED ||
            event?.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED
        ) {

            // ① 优先用 event 自带文本（兼容微信 X5 内核）
            val eventTexts = event.text
                ?.map { it.toString() }
                ?: emptyList()

            val handled = tryParseFromTexts(eventTexts)

            // ② 事件文本没拿到，走节点树（普通浏览器 / 原生 APP）
            if (!handled) {
                scanPageViaNodeTree()
            }
        }
    }

    override fun onInterrupt() {}

    // ────────────────────────────────────────
    // 方案B：从 event.getText() 解析（微信专用）
    // ────────────────────────────────────────

    private fun tryParseFromTexts(texts: List<String>): Boolean {

        if (texts.isEmpty()) return false

        val allText = texts.joinToString(" ")

        Log.d(TAG, "Event文本: $allText")

        // 必须含关键词
        if (!allText.contains(KEY_WORD)) return false

        val numbers = extractNumbersFromString(allText)

        if (numbers.size >= 2) {
            val seatA = numbers[0]
            val seatB = numbers[1]
            Log.d(TAG, "【Event方式】识别到: $seatA / $seatB")
            FloatingWindowManager.updateText("🎯 识别到 $seatA / $seatB")
            return true
        }

        Log.d(TAG, "【Event方式】含关键词但数字不足，数字列表: $numbers")
        return false
    }

    // ────────────────────────────────────────
    // 方案A：遍历节点树（普通浏览器 / 原生 APP）
    // ────────────────────────────────────────

    private fun scanPageViaNodeTree() {

        val root = rootInActiveWindow ?: return

        val found = findText(root, KEY_WORD)

        if (found) {
            val numbers = extractNumbersFromNode(root)
            if (numbers.size >= 2) {
                val seatA = numbers[0]
                val seatB = numbers[1]
                Log.d(TAG, "【节点树方式】识别到: $seatA / $seatB")
                FloatingWindowManager.updateText("🎯 识别到 $seatA / $seatB")
            } else {
                Log.d(TAG, "【节点树方式】含关键词但数字不足，数字列表: $numbers")
            }
        }
    }

    // ────────────────────────────────────────
    // 工具：节点树遍历
    // ────────────────────────────────────────

    private fun findText(
        node: AccessibilityNodeInfo,
        target: String
    ): Boolean {

        val text = node.text?.toString() ?: ""

        if (text.contains(target)) return true

        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            if (findText(child, target)) return true
        }

        return false
    }

    private fun extractNumbersFromNode(
        node: AccessibilityNodeInfo
    ): List<String> {

        val result = mutableListOf<String>()
        recursiveExtract(node, result)

        return result
            .distinct()
            .filter {
                try {
                    it.toInt() in 1..99
                } catch (e: Exception) {
                    false
                }
            }
    }

    private fun recursiveExtract(
        node: AccessibilityNodeInfo,
        result: MutableList<String>
    ) {
        val text = node.text?.toString() ?: ""

        if (text.matches(Regex("^\\d{1,2}$"))) {
            result.add(text)
        }

        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            recursiveExtract(child, result)
        }
    }

    // ────────────────────────────────────────
    // 工具：从字符串提取 1~99 的数字
    // ────────────────────────────────────────

    private fun extractNumbersFromString(text: String): List<String> {

        return Regex("\\b([1-9]\\d?)\\b")
            .findAll(text)
            .map { it.value }
            .distinct()
            .filter {
                try {
                    it.toInt() in 1..99
                } catch (e: Exception) {
                    false
                }
            }
            .toList()
    }
}
