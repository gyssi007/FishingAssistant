package com.fishtime.assistant

import android.content.Context
import android.graphics.PixelFormat
import android.os.Build
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.TextView

object FloatingWindowManager {

    private var windowManager:
            WindowManager? = null

    private var floatingView:
            View? = null

    private var textView:
            TextView? = null

    private var params:
            WindowManager.LayoutParams? = null

    fun show(
        context: Context
    ) {

        if (floatingView != null) {
            return
        }

        windowManager =

            context.getSystemService(
                Context.WINDOW_SERVICE
            ) as WindowManager

        floatingView =

            LayoutInflater.from(context)
                .inflate(
                    R.layout.layout_floating,
                    null
                )

        textView =
            floatingView?.findViewById(
                R.id.floatText
            )

        params =

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

        params?.gravity =
            Gravity.TOP or Gravity.END

        params?.x = 30

        params?.y = 200

        // 拖动

        floatingView?.setOnTouchListener(

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

                            initialX =
                                params?.x ?: 0

                            initialY =
                                params?.y ?: 0

                            initialTouchX =
                                event.rawX

                            initialTouchY =
                                event.rawY

                            return true
                        }

                        MotionEvent.ACTION_MOVE -> {

                            params?.x =
                                initialX -
                                        (event.rawX - initialTouchX).toInt()

                            params?.y =
                                initialY +
                                        (event.rawY - initialTouchY).toInt()

                            windowManager?.updateViewLayout(
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

        windowManager?.addView(
            floatingView,
            params
        )

        updateText(
            "🟢 监听中"
        )
    }

    fun hide(
        context: Context
    ) {

        if (floatingView != null) {

            windowManager?.removeView(
                floatingView
            )

            floatingView = null
        }
    }

    fun updateText(
        text: String
    ) {

        textView?.text = text
    }
}
