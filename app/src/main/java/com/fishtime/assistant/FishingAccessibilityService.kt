# FishingAccessibilityService.kt 修复版

你当前的核心问题有两个：

1. Cookie 没有正确获取
2. 无障碍监听逻辑错误，只在“打开 APK 时”触发一次扫描

你现在的代码属于：

```kotlin
TYPE_WINDOW_STATE_CHANGED
TYPE_WINDOW_CONTENT_CHANGED
```

触发后立即扫描。

但是微信小程序很多内容是动态刷新的。

也就是说：

* 页面已经打开
* “我的钓位”文字后加载
* Accessibility 不一定再次触发

所以你当前逻辑：

只有在页面已经存在“我的钓位”时打开 APK 才能识别。

这是正常现象。

你真正需要的是：

# 正确逻辑

APK 启动后：

* 持续轮询扫描当前页面
* 只要发现“我的钓位”
* 立即识别号码
* 自动分析
* 自动点击
* 自动确认

而不是依赖 AccessibilityEvent。

---

# 你现在直接替换整个文件

路径：

```text
app/src/main/java/com/fishtime/assistant/FishingAccessibilityService.kt
```

完整替换为下面代码。

---

```kotlin
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
```

---

# 这次修复后

现在逻辑会变成：

* APK启动
* 每秒扫描一次当前页面
* 不再依赖页面切换事件
* 微信小程序动态刷新也能识别
* “我的钓位”出现立即触发
* 自动分析
* 自动点击
* 自动确认

---

# 你 Cookie 空的问题本质

你现在实际上：

根本没有真正获取微信 Cookie。

只是：

```kotlin
prefs.getString("cookie")
```

但从来没人写入。

所以永远为空。

你现在有两个方案：

# 方案1（推荐）

登录页输入 Cookie。

用户抓包一次。

永久使用。

稳定。

---

# 方案2（Root 高级版）

通过：

```kotlin
CookieManager.getInstance()
```

或者：

```kotlin
WebView DevTools
```

读取微信 WebView Cookie。

但：

* 微信8.x非常严格
* Android 13+限制更多
* 成功率不稳定
* 很容易失效

你项目里的：

```kotlin
WeChatJsInjector.kt
```

其实已经在尝试这个。

但目前没有真正写入 cookie。

所以悬窗才会一直：

```text
Cookie为空
```

---

# 目前你最稳的方案

先把：

```text
cookie 手动保存
```

实现。

后面再做自动注入。

否则你现在核心问题根本不在 AI。

而是：

根本没有 Cookie。

接口自然失败。
