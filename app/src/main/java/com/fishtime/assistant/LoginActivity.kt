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
            val password = passwordEdit.text.toString().trim()

            if (password.isEmpty()) {
                Toast.makeText(this, "请输入访问密码", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            doLogin(password)
        }
    }

    private fun doLogin(password: String) {
        val json = JSONObject()
        json.put("password", password)

        val requestBody = RequestBody.create(
            "application/json".toMediaTypeOrNull(),
            json.toString()
        )

        val request = Request.Builder()
            .url("https://fishing.gysssi.com/api/login")
            .addHeader("User-Agent", "Mozilla/5.0")
            .post(requestBody)
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                runOnUiThread {
                    Toast.makeText(this@LoginActivity, "登录失败: ${e.message}", Toast.LENGTH_LONG).show()
                }
                Log.e(TAG, "登录失败: ${e.message}")
            }

            override fun onResponse(call: Call, response: Response) {
                try {
                    val body = response.body?.string() ?: ""
                    Log.d(TAG, "登录返回: $body")

                    if (!response.isSuccessful) {
                        runOnUiThread {
                            Toast.makeText(this@LoginActivity, "密码错误", Toast.LENGTH_LONG).show()
                        }
                        return
                    }

                    // 解析 cookie
                    val cookies = response.headers("Set-Cookie")
                    val cookie = cookies.mapNotNull {
                        it.substringBefore(";").takeIf { it.contains("=") }
                    }.joinToString("; ")

                    if (cookie.isEmpty()) {
                        runOnUiThread {
                            Toast.makeText(this@LoginActivity, "获取Cookie失败", Toast.LENGTH_LONG).show()
                        }
                        return
                    }

                    // 保存 cookie 和登录状态
                    getSharedPreferences("app", MODE_PRIVATE).edit()
                        .putString("cookie", cookie)
                        .putBoolean("logged_in", true)
                        .apply()

                    runOnUiThread {
                        Toast.makeText(this@LoginActivity, "登录成功", Toast.LENGTH_SHORT).show()
                        startActivity(Intent(this@LoginActivity, MainActivity::class.java))
                        finish()
                    }

                } catch (e: Exception) {
                    runOnUiThread {
                        Toast.makeText(this@LoginActivity, "解析失败: ${e.message}", Toast.LENGTH_LONG).show()
                    }
                    Log.e(TAG, "解析失败: ${e.message}")
                }
            }
        })
    }
}
