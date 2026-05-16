package com.fishtime.assistant

import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.fishtime.assistant.network.ApiClient
import com.fishtime.assistant.network.LoginRequest
import com.fishtime.assistant.network.LoginResponse
import com.google.gson.Gson
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import kotlin.concurrent.thread

class LoginActivity : AppCompatActivity() {

    private lateinit var prefs: SharedPreferences

    override fun onCreate(savedInstanceState: Bundle?) {

        super.onCreate(savedInstanceState)

        prefs = getSharedPreferences(
            "FishingAssistant",
            MODE_PRIVATE
        )

        val loggedIn = prefs.getBoolean(
            "logged_in",
            false
        )

        if (loggedIn) {

            startActivity(
                Intent(this, MainActivity::class.java)
            )

            finish()

            return
        }

        setContentView(R.layout.activity_login)

        val passwordEdit =
            findViewById<EditText>(R.id.passwordEdit)

        val loginButton =
            findViewById<Button>(R.id.loginButton)

        loginButton.setOnClickListener {

            val password =
                passwordEdit.text.toString()

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

        thread {

            try {

                val gson = Gson()

                val json = gson.toJson(
                    LoginRequest(password)
                )

                val body = json.toRequestBody(
                    "application/json".toMediaType()
                )

                val request = Request.Builder()
                    .url("https://fishing.gysssi.com/api/auth/login")
                    .post(body)
                    .build()

                val response = ApiClient.client
                    .newCall(request)
                    .execute()

                val responseText =
                    response.body?.string() ?: ""

                val result = gson.fromJson(
                    responseText,
                    LoginResponse::class.java
                )

                runOnUiThread {

                    if (result.success) {

                        prefs.edit()
                            .putBoolean("logged_in", true)
                            .apply()

                        Toast.makeText(
                            this,
                            "登录成功",
                            Toast.LENGTH_SHORT
                        ).show()

                        startActivity(
                            Intent(
                                this,
                                MainActivity::class.java
                            )
                        )

                        finish()

                    } else {

                        Toast.makeText(
                            this,
                            result.error ?: "登录失败",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }

            } catch (e: Exception) {

                runOnUiThread {

                    Toast.makeText(
                        this,
                        "网络错误: ${e.message}",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        }
    }
}
