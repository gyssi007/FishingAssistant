package com.fishtime.assistant

import android.accessibilityservice.AccessibilityService
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.util.Log

class FishingAccessibilityService : AccessibilityService() {

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (!AppState.isRunning) return

        if (event?.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED ||
            event?.eventType == AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED) {
            scanPage()
        }
    }

    override fun onInterrupt() {}

    private fun scanPage() {
        val root = rootInActiveWindow ?: return

        FloatingWindowManager.updateText("🔍 正在扫描页面...")

        val found = findPageTitle(root)
        if (!found) {
            FloatingWindowManager.updateText("❌ 未找到钓位页面")
            return
        }

        val numbers = extractNumbers(root)
        if (numbers.size < 2) {
            FloatingWindowManager.updateText("❌ 未识别到二选一钓位")
            return
        }

        val seatA = numbers[0]
        val seatB = numbers[1]
        FloatingWindowManager.updateText("🎯 识别到钓位: $seatA / $seatB")

        Thread {
            try {
                FloatingWindowManager.updateText("🤖 AI分析中...")

                val prefs = getSharedPreferences("FishingAssistant", MODE_PRIVATE)
                val cookie = prefs.getString("cookie", "") ?: ""
                val merId = prefs.getString("merchant_id", "") ?: ""

                if (cookie.isEmpty()) {
                    FloatingWindowManager.updateText("❌ Cookie为空")
                    return@Thread
                }

                if (merId.isEmpty()) {
                    FloatingWindowManager.updateText("❌ 未选择钓场")
                    return@Thread
                }

                val summaryA = ApiClient.getSeatSummary(cookie, merId, seatA)
                val summaryB = ApiClient.getSeatSummary(cookie, merId, seatB)

                if (summaryA == null || summaryB == null) {
                    FloatingWindowManager.updateText("❌ 接口分析失败")
                    return@Thread
                }

                FloatingWindowManager.updateText("✅ 钓位数据已获取")

                val recommend = SeatAnalyzer.analyze(seatA, summaryA, seatB, summaryB)
                FloatingWindowManager.updateText("✅ 推荐 ${recommend}号")

                Thread.sleep(800)

                autoClick(root, recommend)

            } catch (e: Exception) {
                e.printStackTrace()
                FloatingWindowManager.updateText("❌ 网络或处理异常: ${e.message}")
            }
        }.start()
    }

    private fun findPageTitle(node: AccessibilityNodeInfo): Boolean {
        val keywords = listOf("我的钓位", "选择钓位", "请选择钓位")
        val text = node.text?.toString() ?: ""
        for (keyword in keywords) if (text.contains(keyword)) return true
        for (i in 0 until node.childCount) {
            val child = node.getChild(i)
            if (child != null) {
                if (findPageTitle(child)) return true
            }
        }
        return false
    }

    private fun extractNumbers(node: AccessibilityNodeInfo): List<String> {
        val result = mutableListOf<String>()
        recursiveExtract(node, result)
        return result
            .distinct()
            .filter { try { it.toInt() in 1..99 } catch (e: Exception) { false } }
            .take(2)
    }

    private fun recursiveExtract(node: AccessibilityNodeInfo, result: MutableList<String>) {
        val text = node.text?.toString() ?: ""
        if (text.matches(Regex("^\\d{1,2}$"))) result.add(text)
        for (i in 0 until node.childCount) {
            val child = node.getChild(i)
            if (child != null) recursiveExtract(child, result)
        }
    }

    private fun autoClick(root: AccessibilityNodeInfo, seatNumber: String) {
        try {
            val seatNodes = root.findAccessibilityNodeInfosByText(seatNumber)
            if (seatNodes.isNotEmpty()) {
                seatNodes[0].performAction(AccessibilityNodeInfo.ACTION_CLICK)
                FloatingWindowManager.updateText("✅ 已选择 $seatNumber 号")
                Thread.sleep(500)

                val confirmNodes = root.findAccessibilityNodeInfosByText("确定")
                if (confirmNodes.isNotEmpty()) {
                    confirmNodes[0].performAction(AccessibilityNodeInfo.ACTION_CLICK)
                    FloatingWindowManager.updateText("✅ 已自动确认")
                } else {
                    FloatingWindowManager.updateText("❌ 未找到确认按钮")
                }
            } else {
                FloatingWindowManager.updateText("❌ 未找到钓位按钮")
            }
        } catch (e: Exception) {
            e.printStackTrace()
            FloatingWindowManager.updateText("❌ 自动点击失败: ${e.message}")
        }
    }
}
