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
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import org.json.JSONObject
import java.io.IOException

class FishingAccessibilityService : AccessibilityService() {

    companion object {
        private const val TAG = "FishingAssistant"
    }

    private val handler = Handler(Looper.getMainLooper())

    private val client = OkHttpClient()

    private var isRunning = false

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {

        if (event == null) return

        if (isRunning) return

        val rootNode = rootInActiveWindow ?: return

        val numbers = mutableListOf<Int>()

        try {

            val textNodes = ArrayList<AccessibilityNodeInfo>()

            findAllTextNodes(rootNode, textNodes)

            for (node in textNodes) {

                val text = node.text?.toString()?.trim() ?: continue

                if (text.matches(Regex("^\\d+$"))) {

                    val number = text.toIntOrNull()

                    if (number != null) {

                        numbers.add(number)
                    }
                }
            }

            if (numbers.size >= 2) {

                isRunning = true

                analyzeNumbers(numbers)
            }

        } catch (e: Exception) {

            Log.e(TAG, "识别失败: ${e.message}")
        }
    }

    override fun onInterrupt() {

    }

    private fun analyzeNumbers(numbers: List<Int>) {

        val prefs = getSharedPreferences("app", MODE_PRIVATE)

        val cookie = prefs.getString("cookie", "") ?: ""

        Log.d(TAG, "Cookie: $cookie")

        if (cookie.isEmpty()) {

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

            .url("https://你的接口/api/analyze")

            .addHeader("Cookie", cookie)

            .post(requestBody)

            .build()

        client.newCall(request).enqueue(object : Callback {

            override fun onFailure(call: Call, e: IOException) {

                Log.e(TAG, "请求失败: ${e.message}")

                isRunning = false
            }

            override fun onResponse(call: Call, response: Response) {

                try {

                    val body = response.body?.string() ?: ""

                    Log.d(TAG, "返回: $body")

                    if (!response.isSuccessful) {

                        Log.e(TAG, "请求错误: ${response.code}")

                        isRunning = false
                        return
                    }

                    val obj = JSONObject(body)

                    val bestNumber =
                        obj.optInt("best_number", -1)

                    if (bestNumber == -1) {

                        isRunning = false
                        return
                    }

                    handler.post {

                        clickNumber(bestNumber)
                    }

                } catch (e: Exception) {

                    Log.e(TAG, "解析失败: ${e.message}")

                    isRunning = false
                }
            }
        })
    }

    private fun clickNumber(number: Int) {

        val rootNode = rootInActiveWindow ?: return

        val nodes =
            rootNode.findAccessibilityNodeInfosByText(number.toString())

        for (node in nodes) {

            var parent = node

            while (parent.parent != null) {

                if (parent.isClickable) {

                    parent.performAction(
                        AccessibilityNodeInfo.ACTION_CLICK
                    )

                    Log.d(TAG, "已点击: $number")

                    isRunning = false

                    return
                }

                parent = parent.parent
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
}
