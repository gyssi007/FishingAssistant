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
        private const val POLL_INTERVAL = 500L
    }

    @Volatile
    private var isProcessing = false

    private var pollTimer: Timer? = null

    override fun onServiceConnected() {
        super.onServiceConnected()
        Log.d(TAG, "无障碍服务已启动，开始持续监听屏幕")
        startPolling()
    }

    override fun onInterrupt() {}

    override fun onDestroy() {
        super.onDestroy()
        stopPolling()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        val type = event?.eventType ?: return
        if (
            type == AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED ||
            type == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED ||
            type == AccessibilityEvent.TYPE_VIEW_CLICKED ||
            type == AccessibilityEvent.TYPE_ANNOUNCEMENT
        ) {
            handleScan()
        }
    }

    private fun startPolling() {
        stopPolling()
        pollTimer = Timer()
        pollTimer?.scheduleAtFixedRate(object : TimerTask() {
            override fun run() {
                handleScan()
            }
        }, 0, POLL_INTERVAL)
    }

    private fun stopPolling() {
        pollTimer?.cancel()
        pollTimer = null
    }

    // ────────────────────────────────────────
    // 核心：扫描所有窗口，不只是活跃窗口
    // ────────────────────────────────────────

    private fun handleScan() {

        if (isProcessing) return
        isProcessing = true

        try {

            // ★ 获取所有窗口列表
            val allWindows = windows

            if (allWindows.isNullOrEmpty()) {
                // 降级：只用 rootInActiveWindow
                val root = rootInActiveWindow
                if (root != null) scanNode(root)
            } else {
                // 遍历所有窗口，找「我的钓位」
                for (window in allWindows) {
                    val root = window.root ?: continue
                    if (scanNode(root)) break
                }
            }

        } catch (e: Exception) {
            Log.e(TAG, "扫描异常: ${e.message}")
        } finally {
            isProcessing = false
        }
    }

    // 扫描单个节点树，找到返回true
    private fun scanNode(root: AccessibilityNodeInfo): Boolean {

        if (!findText(root, KEY_WORD)) return false

        Log.d(TAG, "检测到「我的钓位」页面")

        val numbers = extractNumbers(root)

        if (numbers.size >= 2) {
            val seatA = numbers[0]
            val seatB = numbers[1]
            Log.d(TAG, "识别到: $seatA / $seatB")
            FloatingWindowManager.updateText("🎯 识别到 $seatA / $seatB")
            return true
        }

        Log.d(TAG, "找到关键词但数字不足: $numbers")
        return false
    }

    // ────────────────────────────────────────
    // 查找包含目标文字的节点
    // ────────────────────────────────────────

    private fun findText(
        node: AccessibilityNodeInfo,
        target: String
    ): Boolean {

        val text = node.text?.toString() ?: ""
        val desc = node.contentDescription?.toString() ?: ""

        if (text.contains(target) || desc.contains(target)) return true

        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            if (findText(child, target)) return true
        }

        return false
    }

    // ────────────────────────────────────────
    // 提取 1~99 的数字
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
