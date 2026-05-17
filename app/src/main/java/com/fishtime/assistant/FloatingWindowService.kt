package com.fishtime.assistant

import android.app.Service
import android.content.Intent
import android.os.IBinder

class FloatingWindowService : Service() {

    override fun onBind(
        intent: Intent?
    ): IBinder? {

        return null
    }

    override fun onCreate() {

        super.onCreate()

        // 显示悬浮窗

        FloatingWindowManager.show(
            this
        )

        // 初始文字

        FloatingWindowManager.updateText(
            "🟢 监听中"
        )
    }

    override fun onDestroy() {

        super.onDestroy()

        // 隐藏悬浮窗

        FloatingWindowManager.hide(
            this
        )
    }
}
