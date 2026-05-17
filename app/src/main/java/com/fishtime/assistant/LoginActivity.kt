package com.fishtime.assistant

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject

class LoginActivity : AppCompatActivity() {

    private lateinit var passwordInput: EditText

    private lateinit var loginButton: Button

    private val client =
        OkHttpClient()

    override fun onCreate(
        savedInstanceState: Bundle?
    ) {

        super.onCreate(savedInstanceState)

        val prefs =
            getSharedPreferences(
                "app",
                MODE_PRIVATE
            )

        val loggedIn =
            prefs.getBoolean(
                "logged_in",
                false
            )

        if (loggedIn) {

            startActivity(
                Intent(
                    this,
                    MainActivity::class.java
                )
            )

            finish()

            return
        }

        setContentView(
            R.layout.activity_login
        )

        passwordInput =
            findViewById(
                R.id.passwordEdit
            )

        loginButton =
            findViewById(
                R.id.loginButton
            )

        loginButton.setOnClickListener {

            doLogin()
        }
    }

    private fun doLogin() {

        val password =
            passwordInput.text
                .toString()
                .trim()

        if (
            password.isEmpty()
        ) {

            Toast.makeText(

                this,

                "请输入密码",

                Toast.LENGTH_SHORT

            ).show()

            return
        }

        loginButton.isEnabled =
            false

        loginButton.text =
            "登录中..."

        Thread {

            try {

                val json =
                    JSONObject()

                json.put(
                    "password",
                    password
                )

                val body =
                    json.toString()
                        .toRequestBody(

                            "application/json"
                                .toMediaType()
                        )

                val request =
                    Request.Builder()

                        .url(
                            "https://fishing.gysssi.com/api/auth/login"
                        )

                        .post(body)

                        .build()

                val response =
                    client.newCall(request)
                        .execute()

                val responseBody =
                    response.body?.string()
                        ?: ""

                val responseJson =
                    JSONObject(responseBody)

                runOnUiThread {

                    loginButton.isEnabled =
                        true

                    loginButton.text =
                        "登 录"
                }

                if (
                    responseJson.optBoolean(
                        "success",
                        false
                    )
                ) {

                    val cookie =
                        response.header(
                            "Set-Cookie"
                        ) ?: ""

                    getSharedPreferences(
                        "app",
                        MODE_PRIVATE
                    ).edit()

                        .putBoolean(
                            "logged_in",
                            true
                        )

                        .putString(
                            "cookie",
                            cookie
                        )

                        .apply()

                    runOnUiThread {

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
                    }

                } else {

                    runOnUiThread {

                        Toast.makeText(

                            this,

                            "密码错误",

                            Toast.LENGTH_SHORT

                        ).show()
                    }
                }

            } catch (e: Exception) {

                e.printStackTrace()

                runOnUiThread {

                    loginButton.isEnabled =
                        true

                    loginButton.text =
                        "登 录"

                    Toast.makeText(

                        this,

                        "网络异常",

                        Toast.LENGTH_SHORT

                    ).show()
                }
            }

        }.start()
    }
}
