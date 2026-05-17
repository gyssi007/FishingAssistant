package com.fishtime.assistant

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import okhttp3.*
import org.json.JSONObject
import java.io.IOException

class LoginActivity : AppCompatActivity() {

    companion object {
        private const val TAG = "FishingAssistant"
    }

    private lateinit var etUsername: EditText
    private lateinit var etPassword: EditText
    private lateinit var btnLogin: Button

    private val client = OkHttpClient.Builder()
        .retryOnConnectionFailure(true)
        .build()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContentView(R.layout.activity_login)

        etUsername = findViewById(R.id.etUsername)
        etPassword = findViewById(R.id.etPassword)
        btnLogin = findViewById(R.id.btnLogin)

        btnLogin.setOnClickListener {

            val username = etUsername.text.toString().trim()
            val password = etPassword.text.toString().trim()

            if (username.isEmpty() || password.isEmpty()) {

                Toast.makeText(
                    this,
                    "请输入账号密码",
                    Toast.LENGTH_SHORT
                ).show()

                return@setOnClickListener
            }

            login(username, password)
        }
    }

    private fun login(username: String, password: String) {

        val json = JSONObject().apply {

            put("username", username)
            put("password", password)
        }

        val mediaType = MediaType.parse("application/json; charset=utf-8")

        val requestBody = RequestBody.create(
            mediaType,
            json.toString()
        )

        val request = Request.Builder()

            // 修改成你的真实登录接口
            .url("https://你的接口地址/api/login")

            .addHeader("User-Agent", "Mozilla/5.0")

            .post(requestBody)

            .build()

        client.newCall(request).enqueue(object : Callback {

            override fun onFailure(call: Call, e: IOException) {

                runOnUiThread {

                    Toast.makeText(
                        this@LoginActivity,
                        "登录失败: ${e.message}",
                        Toast.LENGTH_LONG
                    ).show()
                }

                Log.e(TAG, "登录失败: ${e.message}")
            }

            override fun onResponse(call: Call, response: Response) {

                try {

                    val body = response.body()?.string() ?: ""

                    Log.d(TAG, "登录返回: $body")

                    if (!response.isSuccessful) {

                        runOnUiThread {

                            Toast.makeText(
                                this@LoginActivity,
                                "账号或密码错误",
                                Toast.LENGTH_LONG
                            ).show()
                        }

                        return
                    }

                    val cookies = response.headers("Set-Cookie")

                    Log.d(TAG, "原始Cookies: $cookies")

                    val cookie = cookies.mapNotNull {

                        it.substringBefore(";")
                            .takeIf { part ->
                                part.contains("=")
                            }

                    }.joinToString("; ")

                    Log.d(TAG, "最终Cookie: $cookie")

                    if (cookie.isEmpty()) {

                        runOnUiThread {

                            Toast.makeText(
                                this@LoginActivity,
                                "获取Cookie失败",
                                Toast.LENGTH_LONG
                            ).show()
                        }

                        return
                    }

                    getSharedPreferences("app", MODE_PRIVATE)
                        .edit()
                        .putString("cookie", cookie)
                        .apply()

                    runOnUiThread {

                        Toast.makeText(
                            this@LoginActivity,
                            "登录成功",
                            Toast.LENGTH_SHORT
                        ).show()

                        startActivity(
                            Intent(
                                this@LoginActivity,
                                MainActivity::class.java
                            )
                        )

                        finish()
                    }

                } catch (e: Exception) {

                    runOnUiThread {

                        Toast.makeText(
                            this@LoginActivity,
                            "解析失败: ${e.message}",
                            Toast.LENGTH_LONG
                        ).show()
                    }

                    Log.e(TAG, "解析失败: ${e.message}")
                }
            }
        })
    }
}
