package com.fishtime.assistant

import android.app.Service
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.TextView

class FloatingWindowService : Service() {

    private lateinit var windowManager: WindowManager

    private var floatingView: View? = null

    private lateinit var statusText: TextView

    override fun onBind(intent: Intent?): IBinder? {

        return null
    }

    override fun onCreate() {

        super.onCreate()

        createFloatingWindow()
    }

    override fun onDestroy() {

        super.onDestroy()

        if (floatingView != null) {

            windowManager.removeView(
                floatingView
            )
        }
    }

    private fun createFloatingWindow() {

        windowManager =
            getSystemService(
                WINDOW_SERVICE
            ) as WindowManager

        floatingView =
            LayoutInflater.from(this)
                .inflate(
                    R.layout.layout_floating_window,
                    null
                )

        statusText =
            floatingView!!.findViewById(
                R.id.floatStatus
            )

        FloatingWindowManager.textView =
            statusText

        statusText.text =
            "🟢 监听中"

        val params =
            WindowManager.LayoutParams(

                WindowManager.LayoutParams.WRAP_CONTENT,

                WindowManager.LayoutParams.WRAP_CONTENT,

                if (
                    Build.VERSION.SDK_INT >=
                    Build.VERSION_CODES.O
                )

                    WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY

                else

                    WindowManager.LayoutParams.TYPE_PHONE,

                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,

                PixelFormat.TRANSLUCENT
            )

        params.gravity =
            Gravity.TOP or Gravity.END

        params.x = 30

        params.y = 200

        floatingView!!.setOnTouchListener(

            object : View.OnTouchListener {

                private var initialX = 0

                private var initialY = 0

                private var initialTouchX = 0f

                private var initialTouchY = 0f

                override fun onTouch(
                    v: View?,
                    event: MotionEvent
                ): Boolean {

                    when (event.action) {

                        MotionEvent.ACTION_DOWN -> {

                            initialX = params.x

                            initialY = params.y

                            initialTouchX =
                                event.rawX

                            initialTouchY =
                                event.rawY

                            return true
                        }

                        MotionEvent.ACTION_MOVE -> {

                            params.x =
                                initialX - (
                                    event.rawX -
                                    initialTouchX
                                ).toInt()

                            params.y =
                                initialY + (
                                    event.rawY -
                                    initialTouchY
                                ).toInt()

                            windowManager.updateViewLayout(
                                floatingView,
                                params
                            )

                            return true
                        }
                    }

                    return false
                }
            }
        )

        windowManager.addView(
            floatingView,
            params
        )
    }
}
