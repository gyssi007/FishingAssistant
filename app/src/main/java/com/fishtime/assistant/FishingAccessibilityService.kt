package com.fishtime.assistant

import android.accessibilityservice.AccessibilityService
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import org.json.JSONObject
import java.io.IOException

class FishingAccessibilityService : AccessibilityService() {

    companion object {
        private const val TAG = "FishingAssistant"
    }

    private val client = OkHttpClient()
    private var isRunning = false

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {

        if (event == null || isRunning) return

        val rootNode = rootInActiveWindow ?: return
        val numbers = extractNumbers(rootNode)

        if (numbers.size < 2) return

        isRunning = true
        analyzeNumbers(numbers)
    }

    override fun onInterrupt() {}

    private fun analyzeNumbers(numbers: List<String>) {

        val prefs = getSharedPreferences("app", MODE_PRIVATE)
        val cookie = prefs.getString("cookie", "") ?: ""

        if (cookie.isEmpty()) {
            Log.e(TAG, "Cookie为空")
            isRunning = false
            return
        }

        val json = JSONObject()
        json.put("numbers", numbers)

        val requestBody = RequestBody.create(
            "application/json".toMediaTypeOrNull(),
            json.toString()
        )

        val request = Request.Builder()
            .url("https://fishing.gysssi.com/api/analyze") // AI分析接口
            .addHeader("Cookie", cookie)
            .post(requestBody)
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                Log.e(TAG, "AI分析请求失败: ${e.message}")
                isRunning = false
            }

            override fun onResponse(call: Call, response: Response) {
                try {
                    val body = response.body?.string() ?: ""
                    Log.d(TAG, "AI分析返回: $body")

                    if (!response.isSuccessful) {
                        Log.e(TAG, "AI接口错误: ${response.code}")
                        isRunning = false
                        return
                    }

                    val bestNumber = JSONObject(body).optInt("best_number", -1)
                    if (bestNumber == -1) {
                        Log.e(TAG, "未获取到推荐号码")
                        isRunning = false
                        return
                    }

                    clickNumber(bestNumber.toString())

                } catch (e: Exception) {
                    Log.e(TAG, "解析失败: ${e.message}")
                    isRunning = false
                }
            }
        })
    }

    private fun clickNumber(number: String) {
        val rootNode = rootInActiveWindow ?: run { isRunning = false; return }
        val nodes = rootNode.findAccessibilityNodeInfosByText(number)

        for (node in nodes) {
            var parent = node
            while (parent.parent != null) {
                if (parent.isClickable) {
                    parent.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                    Log.d(TAG, "已点击: $number")
                    isRunning = false
                    return
                }
                parent = parent.parent
            }
        }
        isRunning = false
    }

    private fun extractNumbers(node: AccessibilityNodeInfo): List<String> {
        val result = mutableListOf<String>()
        recursiveExtract(node, result)
        return result.distinct().take(2)
    }

    private fun recursiveExtract(node: AccessibilityNodeInfo?, result: MutableList<String>) {
        if (node == null) return
        val text = node.text?.toString() ?: ""
        if (text.matches(Regex("^\\d{1,2}\$"))) result.add(text)
        for (i in 0 until node.childCount) {
            recursiveExtract(node.getChild(i), result)
        }
    }
}
