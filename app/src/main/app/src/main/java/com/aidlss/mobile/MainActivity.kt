package com.aidlss.mobile

import android.app.Activity
import android.os.Bundle
import android.graphics.Color
import android.view.Gravity
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.SeekBar

class MainActivity : Activity() {

    private val backgroundColor = Color.rgb(15, 18, 28)
    private val accentColor = Color.rgb(0, 220, 255)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(28, 35, 28, 24)
            setBackgroundColor(backgroundColor)
        }

        val title = TextView(this).apply {
            text = "AI DLSS MOBILE"
            textSize = 29f
            setTextColor(accentColor)
            gravity = Gravity.CENTER
        }

        root.addView(
            title,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        )

        val subtitle = TextView(this).apply {
            text = "AI Graphics Enhancement"
            textSize = 15f
            setTextColor(Color.LTGRAY)
            gravity = Gravity.CENTER
            setPadding(0, 8, 0, 30)
        }

        root.addView(subtitle)

        addSection(root, "DEVICE PROFILE")
        addInfo(root, "Device: OnePlus 10T")
        addInfo(root, "GPU: Adreno 730")
        addInfo(root, "Mode: Mobile Graphics")

        addSection(root, "IMAGE ENHANCEMENT")

        addSlider(root, "Sharpness", 0, 100, 35)
        addSlider(root, "Contrast", 0, 100, 50)
        addSlider(root, "Color Enhancement", 0, 100, 50)

        val status = TextView(this).apply {
            text = "Prototype ready"
            textSize = 17f
            setTextColor(accentColor)
            gravity = Gravity.CENTER
            setPadding(0, 24, 0, 10)
        }

        root.addView(status)

        val note = TextView(this).apply {
            text = "Experimental graphics project. " +
                "These controls are currently a UI prototype; " +
                "real image processing will be implemented next."
            textSize = 12f
            setTextColor(Color.GRAY)
            gravity = Gravity.CENTER
        }

        root.addView(note)

        setContentView(root)
    }

    private fun addSection(
        root: LinearLayout,
        text: String
    ) {
        val view = TextView(this).apply {
            this.text = text
            textSize = 17f
            setTextColor(Color.WHITE)
            setPadding(0, 22, 0, 10)
        }

        root.addView(view)
    }

    private fun addInfo(
        root: LinearLayout,
        text: String
    ) {
        val view = TextView(this).apply {
            this.text = text
            textSize = 14f
            setTextColor(Color.LTGRAY)
            setPadding(0, 5, 0, 5)
        }

        root.addView(view)
    }

    private fun addSlider(
        root: LinearLayout,
        label: String,
        min: Int,
        max: Int,
        initial: Int
    ) {
        val heading = TextView(this).apply {
            text = "$label: $initial%"
            textSize = 14f
            setTextColor(Color.WHITE)
        }

        root.addView(heading)

        val slider = SeekBar(this).apply {
            this.max = max - min
            progress = initial - min
        }

        slider.setOnSeekBarChangeListener(
            object : SeekBar.OnSeekBarChangeListener {

                override fun onProgressChanged(
                    seekBar: SeekBar?,
                    progress: Int,
                    fromUser: Boolean
                ) {
                    heading.text = "$label: ${progress + min}%"
                }

                override fun onStartTrackingTouch(
                    seekBar: SeekBar?
                ) {}

                override fun onStopTrackingTouch(
                    seekBar: SeekBar?
                ) {}
            }
        )

        root.addView(
            slider,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        )
    }
}
