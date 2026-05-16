package com.fishtime.assistant

import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {

    private lateinit var serviceStatus: TextView
    private lateinit var enableButton: Button
    private lateinit var startButton: Button
    private lateinit var logText: TextView

    override fun onCreate(savedInstanceState: Bundle?) {

        super.onCreate(savedInstanceState)

        setContentView(R.layout.activity_main)

        initViews()

        updateAccessibilityStatus()

        initEvents()

        addLog("✅ 渔榜助手启动成功")
    }

    override fun onResume() {

        super.onResume()

        updateAccessibilityStatus()
    }

    private fun initViews() {

        serviceStatus = findViewById(R.id.serviceStatus)

        enableButton = findViewById(R.id.enableButton)

        startButton = findViewById(R.id.startButton)

        logText = findViewById(R.id.logText)
    }

    private fun initEvents() {

        enableButton.setOnClickListener {

            addLog("⚙️ 跳转无障碍设置")

            startActivity(
                Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
            )
        }

        startButton.setOnClickListener {

            if (isAccessibilityEnabled()) {

                addLog("✅ 开始监听摇号页面")

            } else {

                addLog("❌ 请先开启无障碍服务")
            }
        }
    }

    private fun updateAccessibilityStatus() {

        if (isAccessibilityEnabled()) {

            serviceStatus.text = "✅ 无障碍服务已开启"

        } else {

            serviceStatus.text = "❌ 无障碍服务未开启"
        }
    }

    private fun isAccessibilityEnabled(): Boolean {

        val expectedService = packageName +
                "/" +
                FishingAccessibilityService::class.java.name

        val enabledServices = Settings.Secure.getString(
            contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ) ?: return false

        return enabledServices.contains(expectedService)
    }

    private fun addLog(message: String) {

        val time = java.text.SimpleDateFormat(
            "HH:mm:ss",
            java.util.Locale.getDefault()
        ).format(java.util.Date())

        val currentText = logText.text.toString()

        val newLog = "[$time] $message\n$currentText"

        logText.text = newLog
    }
}
