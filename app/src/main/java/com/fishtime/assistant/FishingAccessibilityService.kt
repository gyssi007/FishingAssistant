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

    // 防止重复触发
    @Volatile
    private var isProcessing = false

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {

        if (!AppState.isRunning) return

        if (
            event?.eventType ==
            AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED ||
            event?.eventType ==
            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED
        ) {
            handleEvent()
        }
    }

    override fun onInterrupt() {}

    private fun handleEvent() {

        if (isProcessing) return

        val root = rootInActiveWindow ?: return

        // 检测当前页面是否包含「我的钓位」
        val isTargetPage = findText(root, KEY_WORD)

        if (!isTargetPage) return

        Log.d(TAG, "检测到「我的钓位」页面")

        isProcessing = true

        // 先尝试节点树（普通浏览器有效）
        val numbers = extractNumbers(root)

        if (numbers.size >= 2) {
            // 节点树直接拿到了（普通浏览器场景）
            val seatA = numbers[0]
            val seatB = numbers[1]
            Log.d(TAG, "【节点树】识别到: $seatA / $seatB")
            FloatingWindowManager.updateText("🎯 识别到 $seatA / $seatB")
            isProcessing = false
        } else {
            // 节点树拿不到 → 用 JS 注入（微信场景）
            Log.d(TAG, "节点树无数据，启动JS注入")
            WeChatJsInjector.readSeatNumbers { seatA, seatB ->
                Log.d(TAG, "【JS注入】识别到: $seatA / $seatB")
                FloatingWindowManager.updateText("🎯 识别到 $seatA / $seatB")
                isProcessing = false
            }
        }
    }

    // ────────────────────────────────────────
    // 节点树：查找包含目标文字的节点
    // ────────────────────────────────────────

    private fun findText(
        node: AccessibilityNodeInfo,
        target: String
    ): Boolean {

        val text = node.text?.toString() ?: ""
        val desc = node.contentDescription?.toString() ?: ""

        if (text.contains(target) || desc.contains(target)) {
            return true
        }

        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            if (findText(child, target)) return true
        }

        return false
    }

    // ────────────────────────────────────────
    // 节点树：提取 1~99 的数字
    // ────────────────────────────────────────

    private fun extractNumbers(
        node: AccessibilityNodeInfo
    ): List<String> {

        val result = mutableListOf<String>()
        recursiveExtract(node, result)

        return result.distinct().filter {
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
        val desc = node.contentDescription?.toString() ?: ""

        if (text.matches(Regex("^\\d{1,2}$"))) result.add(text)
        if (desc.matches(Regex("^\\d{1,2}$"))) result.add(desc)

        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            recursiveExtract(child, result)
        }
    }
}
