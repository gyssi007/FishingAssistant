package com.fishtime.assistant

import android.accessibilityservice.AccessibilityService
import android.util.Log
import android.view.accessibility.AccessibilityEvent

class FishingAccessibilityService : AccessibilityService() {

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {

        if (event == null) return

        val packageName = event.packageName?.toString() ?: ""

        val className = event.className?.toString() ?: ""

        Log.d(
            "FishingAssistant",
            "检测到页面变化: $packageName / $className"
        )
    }

    override fun onInterrupt() {

        Log.d("FishingAssistant", "无障碍服务中断")
    }

    override fun onServiceConnected() {

        super.onServiceConnected()

        Log.d("FishingAssistant", "无障碍服务已连接")
    }
}
