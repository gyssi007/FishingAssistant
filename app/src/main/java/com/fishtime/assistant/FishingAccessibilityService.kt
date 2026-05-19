package com.fishtime.assistant

import android.accessibilityservice.AccessibilityService
import android.os.Handler
import android.os.Looper
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo

class FishingAccessibilityService : AccessibilityService() {

    private val handler = Handler(Looper.getMainLooper())

    private var lastResult = ""

    private var isAnalyzing = false

    private val scanRunnable = object : Runnable {

        override fun run() {

            try {

                if (AppState.isRunning) {
                    scanPage()
                }

            } catch (e: Exception) {
                e.printStackTrace()
            }

            handler.postDelayed(this, 1000)
        }
    }

    override fun onServiceConnected() {
        super.onServiceConnected()

        handler.post(scanRunnable)

        FloatingWindowManager.updateText(
            "🎣 已启动实时监听页面"
        )
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // 不再依赖事件触发
    }

    override fun onInterrupt() {}

    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacks(scanRunnable)
    }

    private fun scanPage() {

        if (isAnalyzing) return

        val root = rootInActiveWindow ?: return

        val found = findKeyword(root)

        if (!found) {
            return
        }

        val numbers = extractNumbers(root)

        if (numbers.size < 2) {
            FloatingWindowManager.updateText(
                "⌛ 已进入页面，等待号码出现..."
            )
            return
        }

        val seatA = numbers[0]
        val seatB = numbers[1]

        val currentKey = "$seatA-$seatB"

        // 防止重复分析
        if (currentKey == lastResult) {
            return
        }

        lastResult = currentKey

        isAnalyzing = true

        FloatingWindowManager.updateText(
            "🎯 识别到钓位: $seatA / $seatB"
        )

        Thread {

            try {

                val prefs = getSharedPreferences(
                    "app",
                    MODE_PRIVATE
                )

                var cookie = prefs.getString(
                    "cookie",
                    ""
                ) ?: ""

                val merId = prefs.getString(
                    "merchant_id",
                    ""
                ) ?: ""

                // 自动尝试获取微信 Cookie
                if (cookie.isEmpty()) {

                    val autoCookie = extractCookie(root)

                    if (autoCookie.isNotEmpty()) {

                        cookie = autoCookie

                        prefs.edit()
                            .putString("cookie", cookie)
                            .apply()
                    }
                }

                if (cookie.isEmpty()) {

                    FloatingWindowManager.updateText(
                        "❌ Cookie为空"
                    )

                    isAnalyzing = false
                    return@Thread
                }

                if (merId.isEmpty()) {

                    FloatingWindowManager.updateText(
                        "❌ 未选择钓场"
                    )

                    isAnalyzing = false
                    return@Thread
                }

                FloatingWindowManager.updateText(
                    "🤖 AI分析中..."
                )

                val summaryA = ApiClient.getSeatSummary(
                    cookie,
                    merId,
                    seatA
                )

                val summaryB = ApiClient.getSeatSummary(
                    cookie,
                    merId,
                    seatB
                )

                if (summaryA == null || summaryB == null) {

                    FloatingWindowManager.updateText(
                        "❌ 接口分析失败"
                    )

                    isAnalyzing = false
                    return@Thread
                }

                val recommend = SeatAnalyzer.analyze(
                    seatA,
                    summaryA,
                    seatB,
                    summaryB
                )

                FloatingWindowManager.updateText(
                    "✅ 推荐 ${recommend}号"
                )

                Thread.sleep(500)

                autoClick(root, recommend)

            } catch (e: Exception) {

                e.printStackTrace()

                FloatingWindowManager.updateText(
                    "❌ ${e.message}"
                )

            } finally {

                isAnalyzing = false
            }

        }.start()
    }

    // 关键字检测
    private fun findKeyword(node: AccessibilityNodeInfo): Boolean {

        val text = node.text?.toString() ?: ""

        val keywords = listOf(
            "我的钓位",
            "请选择钓位",
            "选择钓位",
            "二选一"
        )

        for (k in keywords) {
            if (text.contains(k)) {
                return true
            }
        }

        for (i in 0 until node.childCount) {

            val child = node.getChild(i)

            if (child != null) {
                if (findKeyword(child)) {
                    return true
                }
            }
        }

        return false
    }

    // 提取号码
    private fun extractNumbers(node: AccessibilityNodeInfo): List<String> {

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
            .take(2)
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

            val child = node.getChild(i)

            if (child != null) {
                recursiveExtract(child, result)
            }
        }
    }

    // 自动点击
    private fun autoClick(
        root: AccessibilityNodeInfo,
        seatNumber: String
    ) {

        try {

            val seatNodes =
                root.findAccessibilityNodeInfosByText(
                    seatNumber
                )

            if (seatNodes.isNotEmpty()) {

                seatNodes[0].performAction(
                    AccessibilityNodeInfo.ACTION_CLICK
                )

                FloatingWindowManager.updateText(
                    "✅ 已选择 $seatNumber 号"
                )

                Thread.sleep(500)

                val confirmNodes =
                    root.findAccessibilityNodeInfosByText(
                        "确定"
                    )

                if (confirmNodes.isNotEmpty()) {

                    confirmNodes[0].performAction(
                        AccessibilityNodeInfo.ACTION_CLICK
                    )

                    FloatingWindowManager.updateText(
                        "✅ 已自动确认"
                    )
                }
            }

        } catch (e: Exception) {

            e.printStackTrace()

            FloatingWindowManager.updateText(
                "❌ 自动点击失败"
            )
        }
    }

    // 自动提取 Cookie
    // 这里先预留接口
    // 后面可接 Root WebView Cookie
    private fun extractCookie(root: AccessibilityNodeInfo): String {

        try {

            val prefs = getSharedPreferences(
                "app",
                MODE_PRIVATE
            )

            val saved = prefs.getString(
                "cookie",
                ""
            ) ?: ""

            if (saved.isNotEmpty()) {
                return saved
            }

        } catch (e: Exception) {
            e.printStackTrace()
        }

        return ""
    }
}
