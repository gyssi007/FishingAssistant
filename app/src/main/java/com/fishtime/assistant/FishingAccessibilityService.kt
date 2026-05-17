package com.fishtime.assistant

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import okhttp3.*
import org.json.JSONObject
import java.io.IOException

class FishingAccessibilityService : AccessibilityService() {

    companion object {
        private const val TAG = "FishingAssistant"
    }

    private val handler = Handler(Looper.getMainLooper())

    private val client = OkHttpClient.Builder()
        .retryOnConnectionFailure(true)
        .build()

    private var isRunning = false

    override fun onServiceConnected() {
        super.onServiceConnected()
        Log.d(TAG, "无障碍服务已连接")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {

        if (event == null) return

        if (isRunning) return

        val rootNode = rootInActiveWindow ?: return

        try {

            val textNodes = ArrayList<AccessibilityNodeInfo>()

            findAllTextNodes(rootNode, textNodes)

            val numbers = mutableListOf<Int>()

            for (node in textNodes) {

                val text = node.text?.toString()?.trim() ?: continue

                if (text.matches(Regex("^\\d+\$"))) {

                    val number = text.toIntOrNull()

                    if (number != null) {
                        numbers.add(number)
                    }
                }
            }

            if (numbers.size >= 2) {

                isRunning = true

                Log.d(TAG, "识别到号码: $numbers")

                analyzeNumbers(numbers)
            }

        } catch (e: Exception) {

            Log.e(TAG, "识别失败: ${e.message}")

        }
    }

    override fun onInterrupt() {
        Log.d(TAG, "无障碍服务中断")
    }

    private fun analyzeNumbers(numbers: List<Int>) {

        val prefs = getSharedPreferences("app", MODE_PRIVATE)

        val cookie = prefs.getString("cookie", "") ?: ""

        Log.d(TAG, "读取Cookie: $cookie")

        if (cookie.isEmpty()) {

            Log.e(TAG, "Cookie为空")

            isRunning = false
            return
        }

        val json = JSONObject().apply {

            put("numbers", numbers)
        }

        val mediaType = MediaType.parse("application/json; charset=utf-8")

        val requestBody = RequestBody.create(
            mediaType,
            json.toString()
        )

        val request = Request.Builder()
            .url("https://你的接口地址/api/analyze")
            .addHeader("Cookie", cookie)
            .addHeader("User-Agent", "Mozilla/5.0")
            .post(requestBody)
            .build()

        client.newCall(request).enqueue(object : Callback {

            override fun onFailure(call: Call, e: IOException) {

                Log.e(TAG, "请求失败: ${e.message}")

                isRunning = false
            }

            override fun onResponse(call: Call, response: Response) {

                try {

                    val body = response.body()?.string() ?: ""

                    Log.d(TAG, "接口返回: $body")

                    if (!response.isSuccessful) {

                        Log.e(TAG, "接口错误: ${response.code()}")

                        isRunning = false
                        return
                    }

                    val jsonObject = JSONObject(body)

                    val bestNumber = jsonObject.optInt("best_number", -1)

                    if (bestNumber == -1) {

                        Log.e(TAG, "未获取到推荐号码")

                        isRunning = false
                        return
                    }

                    Log.d(TAG, "AI推荐号码: $bestNumber")

                    handler.post {

                        clickBestNumber(bestNumber)
                    }

                } catch (e: Exception) {

                    Log.e(TAG, "解析失败: ${e.message}")

                    isRunning = false
                }
            }
        })
    }

    private fun clickBestNumber(bestNumber: Int) {

        val rootNode = rootInActiveWindow ?: run {

            isRunning = false
            return
        }

        val targetNodes = rootNode.findAccessibilityNodeInfosByText(bestNumber.toString())

        if (targetNodes.isEmpty()) {

            Log.e(TAG, "未找到目标号码")

            isRunning = false
            return
        }

        for (node in targetNodes) {

            try {

                var clickableNode: AccessibilityNodeInfo? = node

                while (clickableNode != null) {

                    if (clickableNode.isClickable) {

                        clickableNode.performAction(AccessibilityNodeInfo.ACTION_CLICK)

                        Log.d(TAG, "已点击号码: $bestNumber")

                        isRunning = false
                        return
                    }

                    clickableNode = clickableNode.parent
                }

            } catch (e: Exception) {

                Log.e(TAG, "点击失败: ${e.message}")
            }
        }

        isRunning = false
    }

    private fun findAllTextNodes(
        node: AccessibilityNodeInfo?,
        result: MutableList<AccessibilityNodeInfo>
    ) {

        if (node == null) return

        if (!node.text.isNullOrEmpty()) {

            result.add(node)
        }

        for (i in 0 until node.childCount) {

            findAllTextNodes(node.getChild(i), result)
        }
    }

    private fun performGestureClick(x: Float, y: Float) {

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) {
            return
        }

        val path = Path()

        path.moveTo(x, y)

        val gesture = GestureDescription.Builder()
            .addStroke(
                GestureDescription.StrokeDescription(
                    path,
                    0,
                    100
                )
            )
            .build()

        dispatchGesture(gesture, null, null)
    }
}
