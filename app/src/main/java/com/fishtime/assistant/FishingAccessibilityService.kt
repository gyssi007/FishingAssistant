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
            rootInActiveWindow

        if (root == null) {

            FloatingWindowManager.updateText(
                "❌ root为空"
            )

            return
        }

        FloatingWindowManager.updateText(
            "🔍 扫描页面"
        )

        val found =
            findPageTitle(root)

        if (!found) {

            FloatingWindowManager.updateText(
                "⚠️ 非钓位页面"
            )

            return
        }

        FloatingWindowManager.updateText(
            "✅ 已进入钓位页面"
        )

        val numbers =
            extractNumbers(root)

        Log.d(
            TAG,
            "识别号码: $numbers"
        )

        FloatingWindowManager.updateText(
            "🎯 识别号码: $numbers"
        )

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

        if (
            current == lastSeats
        ) {

            FloatingWindowManager.updateText(
                "⚠️ 本轮已分析过"
            )

            return
        }

        lastSeats = current

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

                FloatingWindowManager.updateText(
                    """
🎣 二选一识别成功

A号:
$seatA

B号:
$seatB

Cookie长度:
${cookie.length}

钓场ID:
$merId
"""
                )

                Log.d(
                    TAG,
                    "Cookie长度: ${cookie.length}"
                )

                Log.d(
                    TAG,
                    "钓场ID: $merId"
                )

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
                    "🌐 请求A号数据"
                )

                val summaryA =
                    ApiClient.getSeatSummary(
                        cookie,
                        merId,
                        seatA
                    )

                FloatingWindowManager.updateText(
                    "🌐 请求B号数据"
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
                        "❌ 接口返回为空"
                    )

                    isAnalyzing.set(false)

                    return@Thread
                }

                FloatingWindowManager.updateText(
                    """
📊 AI分析完成

A命中率:
${summaryA.hitRate}

B命中率:
${summaryB.hitRate}

开始生成推荐...
"""
                )

                val recommend =
                    SeatAnalyzer.analyze(

                        seatA,
                        summaryA,

                        seatB,
                        summaryB
                    )

                Log.d(
                    TAG,
                    "推荐结果: $recommend"
                )

                FloatingWindowManager.updateText(
                    """
🏆 AI推荐完成

推荐:
$recommend号

开始自动点击...
"""
                )

                Thread.sleep(1000)

                autoClick(
                    root,
                    recommend
                )

            } catch (e: Exception) {

                e.printStackTrace()

                FloatingWindowManager.updateText(
                    """
❌ AI分析异常

${e.message}
"""
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
                "⚡ 查找钓位按钮"
            )

            val seatNodes =
                root.findAccessibilityNodeInfosByText(
                    seatNumber
                )

            FloatingWindowManager.updateText(
                "🔍 找到节点数量: ${seatNodes.size}"
            )

            if (seatNodes.isEmpty()) {

                FloatingWindowManager.updateText(
                    "❌ 未找到钓位"
                )

                return
            }

            var clicked = false

            for (node in seatNodes) {

                val target =
                    findClickableParent(node)

                if (target != null) {

                    FloatingWindowManager.updateText(
                        "🖱️ 尝试点击"
                    )

                    clicked =
                        target.performAction(
                            AccessibilityNodeInfo.ACTION_CLICK
                        )

                    if (clicked) {
                        break
                    }
                }
            }

            if (!clicked) {

                FloatingWindowManager.updateText(
                    "❌ 钓位无法点击"
                )

                return
            }

            FloatingWindowManager.updateText(
                "✅ 已点击 $seatNumber"
            )

            Thread.sleep(1200)

            val confirmTexts =
                listOf(

                    "确定",

                    "确认",

                    "提交"
                )

            var confirmClicked =
                false

            for (text in confirmTexts) {

                FloatingWindowManager.updateText(
                    "🔍 查找确认按钮: $text"
                )

                val confirmNodes =
                    root.findAccessibilityNodeInfosByText(
                        text
                    )

                for (node in confirmNodes) {

                    val target =
                        findClickableParent(node)

                    if (target != null) {

                        confirmClicked =
                            target.performAction(
                                AccessibilityNodeInfo.ACTION_CLICK
                            )

                        if (confirmClicked) {
                            break
                        }
                    }
                }

                if (confirmClicked) {
                    break
                }
            }

            if (confirmClicked) {

                FloatingWindowManager.updateText(
                    "🎉 自动确认成功"
                )

            } else {

                FloatingWindowManager.updateText(
                    "⚠️ 未找到确认按钮"
                )
            }

        } catch (e: Exception) {

            e.printStackTrace()

            FloatingWindowManager.updateText(
                """
❌ 自动点击异常

${e.message}
"""
            )
        }
    }

    // ─────────────────────────────
    // 查找可点击父节点
    // ─────────────────────────────

    private fun findClickableParent(

        node: AccessibilityNodeInfo?

    ): AccessibilityNodeInfo? {

        var current = node

        while (current != null) {

            if (current.isClickable) {

                return current
            }

            current = current.parent
        }

        return null
    }
}
