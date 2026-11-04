package com.aidlss.mobile

import android.app.Activity
import android.os.Bundle
import android.graphics.Color
import android.view.Gravity
import android.widget.TextView

class MainActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val screen = TextView(this).apply {
            text = "AI DLSS MOBILE\n\nAPP RUNNING"
            textSize = 25f
            setTextColor(Color.CYAN)
            gravity = Gravity.CENTER
            setBackgroundColor(Color.rgb(15, 18, 28))
        }

        setContentView(screen)
    }
}
