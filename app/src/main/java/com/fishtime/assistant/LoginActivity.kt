package com.fishtime.assistant

import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

class LoginActivity : AppCompatActivity() {

    private lateinit var prefs: SharedPreferences

    override fun onCreate(savedInstanceState: Bundle?) {

        super.onCreate(savedInstanceState)

        prefs = getSharedPreferences("FishingAssistant", MODE_PRIVATE)

        val loggedIn = prefs.getBoolean("logged_in", false)

        if (loggedIn) {

            startActivity(Intent(this, MainActivity::class.java))

            finish()

            return
        }

        setContentView(R.layout.activity_login)

        val passwordEdit = findViewById<EditText>(R.id.passwordEdit)

        val loginButton = findViewById<Button>(R.id.loginButton)

        loginButton.setOnClickListener {

            val password = passwordEdit.text.toString()

            if (password == "@024DiaoYuQu") {

                prefs.edit().putBoolean("logged_in", true).apply()

                startActivity(Intent(this, MainActivity::class.java))

                finish()

            } else {

                Toast.makeText(this, "密码错误", Toast.LENGTH_SHORT).show()
            }
        }
    }
}
