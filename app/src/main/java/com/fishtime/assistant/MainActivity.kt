package com.fishtime.assistant

import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {

        super.onCreate(savedInstanceState)

        setContentView(R.layout.activity_main)

        val serviceStatus = findViewById<TextView>(R.id.serviceStatus)

        val enableButton = findViewById<Button>(R.id.enableButton)

        val startButton = findViewById<Button>(R.id.startButton)

        if (isAccessibilityEnabled()) {

            serviceStatus.text = "✅ 无障碍服务已开启"

        } else {

            serviceStatus.text = "❌ 无障碍服务未开启"
        }

        enableButton.setOnClickListener {

            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        }

        startButton.setOnClickListener {

            Logger.addLog("✅ 开始监听")
        }
    }

    private fun isAccessibilityEnabled(): Boolean {

        return false
    }
}
