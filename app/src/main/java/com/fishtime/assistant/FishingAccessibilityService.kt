package com.fishtime.assistant

import android.accessibilityservice.AccessibilityService
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.util.Log
import java.util.Timer
import java.util.TimerTask

class FishingAccessibilityService : AccessibilityService() {

    companion object {
        private const val TAG = "FishingAssistant"
        private const val KEY_WORD = "我的钓位"

        // 轮询间隔：1秒
        private const val POLL_INTERVAL = 1000L
    }

    // 防止重复处理
    @Volatile
    private var isProcessing = false

    // 定时器
    private var pollTimer: Timer? = null

    // ────────────────────────────────────────
    // 服务启动：开始定时轮询
    // ────────────────────────────────────────

    override fun onServiceConnected() {
        super.onServiceConnected()
        Log.d(TAG, "Accessibility服务已连接，启动轮询")
        startPolling()
    }

    // ────────────────────────────────────────
    // 事件监听（页面切换时立即触发，比轮询更快）
    // ────────────────────────────────────────

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {

        if (!AppState.isRunning) return

        if (
            event?.eventType ==
            AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED ||
            event?.eventType ==
            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED
        ) {
            // 事件触发时立即扫描一次（响应更快）
            handleScan()
        }
    }

    override fun onInterrupt() {}

    override fun onDestroy() {
        super.onDestroy()
        stopPolling()
    }

    // ────────────────────────────────────────
    // 定时轮询：每1秒主动扫描一次
    // 解决「已经在页面上但没有事件触发」的问题
    // ────────────────────────────────────────

    private fun startPolling() {
        stopPolling()
        pollTimer = Timer()
        pollTimer?.scheduleAtFixedRate(object : TimerTask() {
            override fun run() {
                if (AppState.isRunning) {
                    handleScan()
                }
            }
        }, 0, POLL_INTERVAL)
    }

    private fun stopPolling() {
        pollTimer?.cancel()
        pollTimer = null
    }

    // ────────────────────────────────────────
    // 核心扫描逻辑
    // ────────────────────────────────────────

    private fun handleScan() {

        if (isProcessing) return

        val root = rootInActiveWindow ?: return

        // 检测当前页面是否包含「我的钓位」
        if (!findText(root, KEY_WORD)) return

        Log.d(TAG, "检测到「我的钓位」页面")

        isProcessing = true

        val numbers = extractNumbers(root)

        if (numbers.size >= 2) {
            val seatA = numbers[0]
            val seatB = numbers[1]
            Log.d(TAG, "识别到: $seatA / $seatB")
            FloatingWindowManager.updateText("🎯 识别到 $seatA / $seatB")
        } else {
            Log.d(TAG, "找到关键词但数字不足: $numbers")
        }

        isProcessing = false
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
