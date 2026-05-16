package com.fishtime.assistant

import android.widget.TextView

object FloatingWindowManager {

    var textView: TextView? = null

    fun updateText(text: String) {

        textView?.post {

            textView?.text = text
        }
    }
}
