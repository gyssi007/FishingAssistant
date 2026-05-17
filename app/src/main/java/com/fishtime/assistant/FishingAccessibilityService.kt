package com.fishtime.assistant

import android.accessibilityservice.AccessibilityService
import android.os.SystemClock
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import java.util.concurrent.atomic.AtomicBoolean

class FishingAccessibilityService :
    AccessibilityService() {

    companion object {

        private const val TAG =
            "FishingAccessibility"

        private var lastScanTime = 0L

        private val isAnalyzing =
            AtomicBoolean(false)

        private var lastSeats = ""
    }

    override fun onAccessibilityEvent(
        event: AccessibilityEvent?
    ) {

        if (!AppState.isRunning) {
            return
        }

        if (event == null) {
            return
        }

        when (event.eventType) {

            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED,

            AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED -> {

                val now =
                    SystemClock.elapsedRealtime()

                // 节流
                if (
                    now - lastScanTime < 1200
                ) {
                    return
                }

                lastScanTime = now

                scanPage()
            }
        }
    }

    override fun onInterrupt() {

    }

    // ─────────────────────────────
    // 扫描页面
    // ─────────────────────────────

    private fun scanPage() {

        if (
            isAnalyzing.get()
        ) {
            return
        }

        val root =
            rootInActiveWindow ?: return

        FloatingWindowManager.updateText(
            "🔍 AI识别页面"
        )

        val found =
            findPageTitle(root)

        if (!found) {
            return
        }

        val numbers =
            extractNumbers(root)

        if (numbers.size < 2) {

            FloatingWindowManager.updateText(
                "❌ 未识别二选一"
            )

            return
        }

        val seatA = numbers[0]

        val seatB = numbers[1]

        val current =
            "$seatA-$seatB"

        // 防重复分析

        if (
            current == lastSeats
        ) {
            return
        }

        lastSeats = current

        FloatingWindowManager.updateText(
            "🎯 二选一: $seatA / $seatB"
        )

        isAnalyzing.set(true)

        Thread {

            try {

                val prefs =
                    getSharedPreferences(
                        "app",
                        MODE_PRIVATE
                    )

                val cookie =
                    prefs.getString(
                        "cookie",
                        ""
                    ) ?: ""

                val merId =
                    prefs.getString(
                        "merchant_id",
                        ""
                    ) ?: ""

                if (
                    cookie.isEmpty()
                ) {

                    FloatingWindowManager.updateText(
                        "❌ Cookie为空"
                    )

                    isAnalyzing.set(false)

                    return@Thread
                }

                if (
                    merId.isEmpty()
                ) {

                    FloatingWindowManager.updateText(
                        "❌ 未选择钓场"
                    )

                    isAnalyzing.set(false)

                    return@Thread
                }

                FloatingWindowManager.updateText(
                    "🤖 AI分析中"
                )

                Log.d(
                    TAG,
                    "开始分析: $seatA / $seatB"
                )

                val summaryA =
                    ApiClient.getSeatSummary(
                        cookie,
                        merId,
                        seatA
                    )

                val summaryB =
                    ApiClient.getSeatSummary(
                        cookie,
                        merId,
                        seatB
                    )

                if (
                    summaryA == null ||
                    summaryB == null
                ) {

                    FloatingWindowManager.updateText(
                        "❌ 分析接口失败"
                    )

                    isAnalyzing.set(false)

                    return@Thread
                }

                val recommend =
                    SeatAnalyzer.analyze(

                        seatA,
                        summaryA,

                        seatB,
                        summaryB
                    )

                FloatingWindowManager.updateText(
                    "✅ 推荐: $recommend"
                )

                Thread.sleep(800)

                autoClick(
                    root,
                    recommend
                )

            } catch (e: Exception) {

                e.printStackTrace()

                FloatingWindowManager.updateText(
                    "❌ AI分析异常"
                )

            } finally {

                isAnalyzing.set(false)
            }

        }.start()
    }

    // ─────────────────────────────
    // 查找页面
    // ─────────────────────────────

    private fun findPageTitle(
        node: AccessibilityNodeInfo
    ): Boolean {

        val keywords =
            listOf(

                "选择钓位",

                "请选择钓位",

                "我的钓位",

                "二选一",

                "抽号"
            )

        val text =
            node.text?.toString()
                ?: ""

        for (keyword in keywords) {

            if (
                text.contains(keyword)
            ) {

                return true
            }
        }

        for (i in 0 until node.childCount) {

            val child =
                node.getChild(i)

            if (child != null) {

                val found =
                    findPageTitle(child)

                if (found) {

                    return true
                }
            }
        }

        return false
    }

    // ─────────────────────────────
    // 提取号码
    // ─────────────────────────────

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

            .take(2)
    }

    // ─────────────────────────────
    // 递归提取
    // ─────────────────────────────

    private fun recursiveExtract(

        node: AccessibilityNodeInfo,

        result: MutableList<String>

    ) {

        val text =
            node.text?.toString()
                ?: ""

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

    // ─────────────────────────────
    // 自动点击
    // ─────────────────────────────

    private fun autoClick(

        root: AccessibilityNodeInfo,

        seatNumber: String

    ) {

        try {

            FloatingWindowManager.updateText(
                "⚡ 自动点击中"
            )

            val seatNodes =
                root.findAccessibilityNodeInfosByText(
                    seatNumber
                )

            if (
                seatNodes.isEmpty()
            ) {

                FloatingWindowManager.updateText(
                    "❌ 未找到钓位"
                )

                return
            }

            val seatNode =
                seatNodes[0]

            seatNode.performAction(
                AccessibilityNodeInfo.ACTION_CLICK
            )

            FloatingWindowManager.updateText(
                "✅ 已选择 $seatNumber"
            )

            Thread.sleep(600)

            val confirmTexts =
                listOf(

                    "确定",

                    "确认",

                    "提交"
                )

            var clicked =
                false

            for (text in confirmTexts) {

                val confirmNodes =
                    root.findAccessibilityNodeInfosByText(
                        text
                    )

                if (
                    confirmNodes.isNotEmpty()
                ) {

                    confirmNodes[0].performAction(
                        AccessibilityNodeInfo.ACTION_CLICK
                    )

                    clicked = true

                    break
                }
            }

            if (clicked) {

                FloatingWindowManager.updateText(
                    "✅ 自动确认成功"
                )

            } else {

                FloatingWindowManager.updateText(
                    "⚠️ 未找到确认按钮"
                )
            }

        } catch (e: Exception) {

            e.printStackTrace()

            FloatingWindowManager.updateText(
                "❌ 自动点击失败"
            )
        }
    }
}
