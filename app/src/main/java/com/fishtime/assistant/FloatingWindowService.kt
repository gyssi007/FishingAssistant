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

        FloatingWindowManager.show(
            this
        )

        FloatingWindowManager.updateText(
            """
🎣 渔榜助手已启动

等待进入钓位页面...
"""
        )
    }

    override fun onDestroy() {

        super.onDestroy()

        FloatingWindowManager.hide()
    }
}
