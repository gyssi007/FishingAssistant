package com.fishtime.assistant

import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import android.provider.Settings
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.fishtime.assistant.network.ApiClient
import com.fishtime.assistant.network.Merchant
import com.fishtime.assistant.network.MerchantResponse
import com.google.gson.Gson
import okhttp3.Request
import kotlin.concurrent.thread

class MainActivity : AppCompatActivity() {

    private lateinit var prefs:
        SharedPreferences

    private lateinit var serviceStatus:
        TextView

    private lateinit var enableButton:
        Button

    private lateinit var startButton:
        Button

    private lateinit var logText:
        TextView

    private lateinit var merchantSpinner:
        Spinner

    private val merchantList =
        mutableListOf<Merchant>()

    override fun onCreate(
        savedInstanceState: Bundle?
    ) {

        super.onCreate(
            savedInstanceState
        )

        setContentView(
            R.layout.activity_main
        )

        prefs =
            getSharedPreferences(
                "app",
                MODE_PRIVATE
            )

        initViews()

        initEvents()

        updateAccessibilityStatus()

        addLog(
            "✅ 渔榜助手启动成功"
        )

        loadMerchants()
    }

    override fun onResume() {

        super.onResume()

        updateAccessibilityStatus()
    }

    // ─────────────────────────────
    // 初始化View
    // ─────────────────────────────

    private fun initViews() {

        serviceStatus =
            findViewById(
                R.id.serviceStatus
            )

        enableButton =
            findViewById(
                R.id.enableButton
            )

        startButton =
            findViewById(
                R.id.startButton
            )

        logText =
            findViewById(
                R.id.logText
            )

        merchantSpinner =
            findViewById(
                R.id.merchantSpinner
            )
    }

    // ─────────────────────────────
    // 初始化事件
    // ─────────────────────────────

    private fun initEvents() {

        enableButton.setOnClickListener {

            addLog(
                "⚙️ 打开无障碍设置"
            )

            startActivity(
                Intent(
                    Settings.ACTION_ACCESSIBILITY_SETTINGS
                )
            )
        }

        startButton.setOnClickListener {

            if (
                !isAccessibilityEnabled()
            ) {

                Toast.makeText(
                    this,
                    "请先开启无障碍服务",
                    Toast.LENGTH_SHORT
                ).show()

                addLog(
                    "❌ 无障碍未开启"
                )

                return@setOnClickListener
            }

            if (
                !Settings.canDrawOverlays(
                    this
                )
            ) {

                Toast.makeText(
                    this,
                    "请开启悬浮窗权限",
                    Toast.LENGTH_LONG
                ).show()

                startActivity(
                    Intent(
                        Settings.ACTION_MANAGE_OVERLAY_PERMISSION
                    )
                )

                return@setOnClickListener
            }

            if (
                !AppState.isRunning
            ) {

                val position =
                    merchantSpinner.selectedItemPosition

                if (
                    position >= 0 &&
                    position < merchantList.size
                ) {

                    val merchant =
                        merchantList[position]

                    prefs.edit()

                        .putString(
                            "merchant_id",
                            merchant.mer_id
                        )

                        .putString(
                            "merchant_name",
                            merchant.mer_name
                        )

                        .apply()

                    addLog(
                        "🎣 当前钓场: ${merchant.mer_name}"
                    )
                }

                AppState.isRunning = true

                startService(
                    Intent(
                        this,
                        FloatingWindowService::class.java
                    )
                )

                startButton.text =
                    "停止监听"

                addLog(
                    "🟢 开始监听"
                )

                FloatingWindowManager.show(
                    this
                )

                FloatingWindowManager.updateText(
                    """
🎣 渔榜助手运行中

等待进入:
二选一页面...
"""
                )

            } else {

                AppState.isRunning = false

                stopService(
                    Intent(
                        this,
                        FloatingWindowService::class.java
                    )
                )

                startButton.text =
                    "开始监听"

                addLog(
                    "🔴 停止监听"
                )

                FloatingWindowManager.hide()
            }
        }
    }

    // ─────────────────────────────
    // 加载钓场
    // ─────────────────────────────

    private fun loadMerchants() {

        addLog(
            "🌐 正在加载钓场列表"
        )

        thread {

            try {

                val request =
                    Request.Builder()

                        .url(
                            "https://api.cdtx.top/v2/userApi/merchant/index" +
                                    "?order_type=20&page=1&limit=50&city=沈阳"
                        )

                        .get()

                        .build()

                val response =
                    ApiClient.client
                        .newCall(request)
                        .execute()

                val json =
                    response.body?.string()
                        ?: ""

                val result =
                    Gson().fromJson(
                        json,
                        MerchantResponse::class.java
                    )

                runOnUiThread {

                    merchantList.clear()

                    merchantList.addAll(
                        result.data.list
                    )

                    val names =
                        merchantList.map {
                            it.mer_name
                        }

                    val adapter =
                        ArrayAdapter(

                            this,

                            android.R.layout.simple_spinner_item,

                            names
                        )

                    adapter.setDropDownViewResource(
                        android.R.layout.simple_spinner_dropdown_item
                    )

                    merchantSpinner.adapter =
                        adapter

                    addLog(
                        "✅ 已加载 ${merchantList.size} 个钓场"
                    )
                }

            } catch (e: Exception) {

                runOnUiThread {

                    addLog(
                        "❌ 钓场加载失败: ${e.message}"
                    )
                }
            }
        }
    }

    // ─────────────────────────────
    // 更新无障碍状态
    // ─────────────────────────────

    private fun updateAccessibilityStatus() {

        serviceStatus.text =

            if (
                isAccessibilityEnabled()
            )

                "✅ 无障碍服务已开启"

            else

                "❌ 无障碍服务未开启"
    }

    // ─────────────────────────────
    // 检测无障碍
    // ─────────────────────────────

    private fun isAccessibilityEnabled():
            Boolean {

        val expectedService =

            packageName +
                    "/" +
                    FishingAccessibilityService::class.java.name

        val enabledServices =
            Settings.Secure.getString(

                contentResolver,

                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES

            ) ?: return false

        return enabledServices.contains(
            expectedService
        )
    }

    // ─────────────────────────────
    // 日志输出
    // ─────────────────────────────

    private fun addLog(
        message: String
    ) {

        val time =
            java.text.SimpleDateFormat(

                "HH:mm:ss",

                java.util.Locale.getDefault()

            ).format(
                java.util.Date()
            )

        val current =
            logText.text.toString()

        logText.text =
            "[$time] $message\n$current"
    }
}
