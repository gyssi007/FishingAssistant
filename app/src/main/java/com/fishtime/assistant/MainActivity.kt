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

    private lateinit var prefs: SharedPreferences

    private lateinit var serviceStatus: TextView

    private lateinit var enableButton: Button

    private lateinit var startButton: Button

    private lateinit var logText: TextView

    private lateinit var merchantSpinner: Spinner

    private val merchantList = mutableListOf<Merchant>()

    override fun onCreate(savedInstanceState: Bundle?) {

        super.onCreate(savedInstanceState)

        setContentView(R.layout.activity_main)

        prefs = getSharedPreferences(
            "FishingAssistant",
            MODE_PRIVATE
        )

        initViews()

        initEvents()

        updateAccessibilityStatus()

        addLog("✅ 渔榜助手启动成功")

        loadMerchants()
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

        merchantSpinner = findViewById(R.id.merchantSpinner)
    }

    private fun initEvents() {

        enableButton.setOnClickListener {

            addLog("⚙️ 打开无障碍设置")

            startActivity(
                Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
            )
        }

        startButton.setOnClickListener {

            if (!isAccessibilityEnabled()) {

                Toast.makeText(
                    this,
                    "请先开启无障碍服务",
                    Toast.LENGTH_SHORT
                ).show()

                addLog("❌ 无障碍服务未开启")

                return@setOnClickListener
            }

            val position =
                merchantSpinner.selectedItemPosition

            if (position < 0 ||
                position >= merchantList.size
            ) {

                Toast.makeText(
                    this,
                    "请选择钓场",
                    Toast.LENGTH_SHORT
                ).show()

                return@setOnClickListener
            }

            val merchant = merchantList[position]

            prefs.edit()
                .putString("merchant_id", merchant.mer_id)
                .putString("merchant_name", merchant.mer_name)
                .apply()

            addLog("🎣 当前钓场: ${merchant.mer_name}")

            addLog("✅ 开始监听摇号页面")
        }
    }

    private fun loadMerchants() {

        addLog("🌐 正在加载钓场列表")

        thread {

            try {

                val request = Request.Builder()
                    .url(
                        "https://api.cdtx.top/v2/userApi/merchant/index" +
                                "?order_type=20&page=1&limit=50&city=沈阳"
                    )
                    .get()
                    .build()

                val response = ApiClient.client
                    .newCall(request)
                    .execute()

                val json =
                    response.body?.string() ?: ""

                val result = Gson().fromJson(
                    json,
                    MerchantResponse::class.java
                )

                runOnUiThread {

                    merchantList.clear()

                    merchantList.addAll(
                        result.data.list
                    )

                    val names = merchantList.map {

                        it.mer_name
                    }

                    val adapter = ArrayAdapter(
                        this,
                        android.R.layout.simple_spinner_item,
                        names
                    )

                    adapter.setDropDownViewResource(
                        android.R.layout.simple_spinner_dropdown_item
                    )

                    merchantSpinner.adapter = adapter

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

    private fun updateAccessibilityStatus() {

        if (isAccessibilityEnabled()) {

            serviceStatus.text =
                "✅ 无障碍服务已开启"

        } else {

            serviceStatus.text =
                "❌ 无障碍服务未开启"
        }
    }

    private fun isAccessibilityEnabled(): Boolean {

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

    private fun addLog(message: String) {

        val time = java.text.SimpleDateFormat(
            "HH:mm:ss",
            java.util.Locale.getDefault()
        ).format(java.util.Date())

        val current =
            logText.text.toString()

        val newLog =
            "[$time] $message\n$current"

        logText.text = newLog
    }
}
