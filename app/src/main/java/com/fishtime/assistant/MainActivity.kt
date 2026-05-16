package com.fishtime.assistant

import android.app.Activity
import android.os.Bundle
import android.widget.TextView

class MainActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {

        super.onCreate(savedInstanceState)

        val text = TextView(this)

        text.text = "渔榜助手 APK 构建成功"

        text.textSize = 28f

        setContentView(text)
    }
}
