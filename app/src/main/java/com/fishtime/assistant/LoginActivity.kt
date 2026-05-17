package com.fishtime.assistant

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import org.json.JSONObject
import java.io.IOException

class LoginActivity : AppCompatActivity() {

    companion object {
        private const val TAG = "FishingAssistant"
    }

    private lateinit var passwordEdit: EditText
    private lateinit var loginButton: Button

    private val client = OkHttpClient()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContentView(R.layout.activity_login)

        passwordEdit = findViewById(R.id.passwordEdit)
        loginButton = findViewById(R.id.loginButton)

        loginButton.setOnClickListener {

            val password =
                passwordEdit.text.toString().trim()

            if (password.isEmpty()) {

                Toast.makeText(
                    this,
                    "请输入密码",
                    Toast.LENGTH_SHORT
                ).show()

                return@setOnClickListener
            }

            login(password)
        }
    }

    private fun login(password: String) {

        val json = JSONObject()

        json.put("password", password)

        val requestBody = RequestBody.create(
            "application/json".toMediaTypeOrNull(),
            json.toString()
        )

        val request = Request.Builder()

            // 改成你的真实接口
            .url("https://你的接口地址/api/login")

            .post(requestBody)

            .addHeader(
                "User-Agent",
                "Mozilla/5.0"
            )

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

            override fun onResponse(
                call: Call,
                response: Response
            ) {

                try {

                    val body =
                        response.body?.string() ?: ""

                    Log.d(TAG, "返回: $body")

                    if (!response.isSuccessful) {

                        runOnUiThread {

                            Toast.makeText(
                                this@LoginActivity,
                                "密码错误",
                                Toast.LENGTH_LONG
                            ).show()
                        }

                        return
                    }

                    val cookies =
                        response.headers("Set-Cookie")

                    val cookie = cookies.mapNotNull {

                        it.substringBefore(";")
                            .takeIf { part ->
                                part.contains("=")
                            }

                    }.joinToString("; ")

                    Log.d(TAG, "Cookie: $cookie")

                    if (cookie.isEmpty()) {

                        runOnUiThread {

                            Toast.makeText(
                                this@LoginActivity,
                                "Cookie获取失败",
                                Toast.LENGTH_LONG
                            ).show()
                        }

                        return
                    }

                    getSharedPreferences(
                        "app",
                        MODE_PRIVATE
                    ).edit()

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
