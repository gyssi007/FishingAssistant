package com.fishtime.assistant

import android.content.Context
import android.graphics.PixelFormat
import android.os.Build
import android.view.Gravity
import android.view.LayoutInflater
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

    fun show(
        context: Context
    ) {

        if (
            floatingView != null
        ) {
            return
        }

        windowManager =
            context.getSystemService(
                Context.WINDOW_SERVICE
            ) as WindowManager

        floatingView =
            LayoutInflater.from(context)
                .inflate(
                    R.layout.layout_floating_window,
                    null
                )

        textView =
            floatingView!!.findViewById(
                R.id.floatText
            )

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
            Gravity.TOP or Gravity.START

        params.x = 20

        params.y = 180

        windowManager?.addView(
            floatingView,
            params
        )
    }

    fun hide() {

        try {

            if (
                floatingView != null
            ) {

                windowManager?.removeView(
                    floatingView
                )

                floatingView = null
            }

        } catch (e: Exception) {

            e.printStackTrace()
        }
    }

    fun updateText(
        text: String
    ) {

        textView?.post {

            val oldText =
                textView?.text?.toString()
                    ?: ""

            val newText =
                """
$text

────────────────

$oldText
"""

            textView?.text =
                newText.take(2000)
        }
    }
}
