package com.aidlss.mobile

import android.app.Activity
import android.os.Bundle
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.net.Uri
import android.content.Intent
import androidx.documentfile.provider.DocumentFile
import android.view.Gravity
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.SeekBar
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import kotlin.math.roundToInt

class MainActivity : ComponentActivity() {
    private lateinit var preview: ImageView
    private lateinit var status: TextView
    private lateinit var sharpnessLabel: TextView
    private lateinit var contrastLabel: TextView
    private lateinit var colorLabel: TextView
    private lateinit var gameScanStatus: TextView
    private var originalBitmap: Bitmap? = null
    private var sharpness = 20
    private var contrast = 50
    private var color = 50

    private val imagePicker = registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        if (uri != null) loadImage(uri)
    }
    private val gameFolderPicker = registerForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri: Uri? ->
        if (uri != null) {
            try { contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION) } catch (_: Exception) {}
            scanGameFolder(uri)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val page = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(24), dp(20), dp(24))
            setBackgroundColor(Color.rgb(13, 17, 29))
        }
        val scroll = ScrollView(this).apply { addView(page) }

        page.addView(text("AI DLSS MOBILE", 28f, Color.rgb(0, 220, 255), true))
        page.addView(text("Image enhancement lab • Adreno 730", 14f, Color.LTGRAY, false))
        page.addView(text("Universal game & emulator scanner", 17f, Color.WHITE, true))
        val scanButton = Button(this).apply {
            text = "Select game / emulator folder"
            setOnClickListener { gameFolderPicker.launch(null) }
        }
        page.addView(scanButton, matchWrap())
        gameScanStatus = text("Select a game folder to identify its files and possible engine.", 13f, Color.rgb(0, 220, 255), false)
        page.addView(gameScanStatus)

        val device = text("DEVICE  |  OnePlus 10T\nGPU  |  Qualcomm Adreno 730\nENGINE  |  On-device bitmap processing", 14f, Color.WHITE, false)
        device.setPadding(0, dp(22), 0, dp(18))
        page.addView(device)

        val choose = Button(this).apply {
            text = "Choose image from phone"
            setOnClickListener { imagePicker.launch("image/*") }
        }
        page.addView(choose, matchWrap())

        preview = ImageView(this).apply {
            scaleType = ImageView.ScaleType.FIT_CENTER
            adjustViewBounds = true
            setBackgroundColor(Color.rgb(25, 31, 46))
            minimumHeight = dp(220)
            contentDescription = "Image enhancement preview"
        }
        val imageParams = matchWrap().apply { topMargin = dp(14); bottomMargin = dp(12) }
        page.addView(preview, imageParams)

        page.addView(text("LIVE PREVIEW CONTROLS", 17f, Color.WHITE, true))
        sharpnessLabel = addSlider(page, "Detail / sharpness", sharpness)
        contrastLabel = addSlider(page, "Contrast", contrast)
        colorLabel = addSlider(page, "Color saturation", color)

        val reset = Button(this).apply {
            text = "Reset adjustments"
            setOnClickListener {
                sharpness = 20
                contrast = 50
                color = 50
                updateLabels()
                applyEnhancement()
            }
        }
        page.addView(reset, matchWrap())

        status = text("Choose an image to start processing.", 14f, Color.rgb(0, 220, 255), false)
        status.setPadding(0, dp(18), 0, dp(8))
        page.addView(status)
        page.addView(text("This version processes a selected image inside the app. It does not modify live game graphics or inject into games.", 12f, Color.LTGRAY, false))

        setContentView(scroll)
    }

    private fun scanGameFolder(uri: Uri) {
        gameScanStatus.text = "Scanning folder…"
        Thread {
            try {
                val root = DocumentFile.fromTreeUri(this, uri)
                if (root == null) throw IllegalStateException("Folder cannot be opened")
                val queue = ArrayDeque<Pair<DocumentFile, Int>>()
                queue.add(root to 0)
                val extensions = linkedSetOf<String>()
                val names = mutableListOf<String>()
                val indicators = linkedSetOf<String>()
                var files = 0
                var folders = 0
                while (queue.isNotEmpty() && files + folders < 2500) {
                    val (dir, depth) = queue.removeFirst()
                    if (depth > 6) continue
                    val children = try { dir.listFiles() } catch (_: Exception) { emptyArray() }
                    for (child in children) {
                        val name = child.name ?: continue
                        if (child.isDirectory) {
                            folders++
                            if (depth < 6) queue.add(child to depth + 1)
                        } else {
                            files++
                            val lower = name.lowercase()
                            val ext = lower.substringAfterLast('.', "")
                            if (ext.isNotBlank()) extensions.add(ext)
                            if (names.size < 80) names.add(name)
                            if (lower.contains("unityplayer") || lower == "global-metadata.dat" || lower == "libil2cpp.so") indicators.add("Unity / IL2CPP")
                            if (lower.contains("libue4") || lower.contains("unreal")) indicators.add("Unreal Engine")
                            if (lower.contains("godot")) indicators.add("Godot")
                            if (lower.endsWith(".pak") || lower.endsWith(".obb")) indicators.add("Game asset archives")
                            if (lower.endsWith(".iso") || lower.endsWith(".chd") || lower.endsWith(".cso") || lower.endsWith(".rvz") || lower.endsWith(".xex") || lower.endsWith(".cue")) indicators.add("Console / emulator image files")
                            if (lower.contains("dolphin") || lower.contains("pcsx2") || lower.contains("ppsspp") || lower.contains("aethersx2") || lower.contains("winlator")) indicators.add("Possible emulator data")
                        }
                        if (files + folders >= 2500) break
                    }
                }
                val result = buildString {
                    append("SCAN COMPLETE (read-only)\n")
                    append("Files: $files • Folders: $folders\n")
                    append("Detected file types: ").append(extensions.take(18).joinToString(", ").ifBlank { "none" }).append("\n")
                    append("Possible components: ").append(indicators.joinToString(", ").ifBlank { "not identified yet" }).append("\n")
                    append("Sample files:\n").append(names.take(18).joinToString("\n"))
                    if (files + folders >= 2500) append("\nScan capped at 2,500 entries for performance.")
                    append("\n\nThis scanner only reads names and folders; it does not modify game data or hook into rendering.")
                }
                runOnUiThread { gameScanStatus.text = result }
            } catch (e: Exception) {
                runOnUiThread { gameScanStatus.text = "Scan failed: " + (e.message ?: "unknown error") }
            }
        }.start()
    }

    private fun addSlider(parent: LinearLayout, title: String, initial: Int): TextView {
        val label = text("$title: $initial%", 14f, Color.WHITE, false)
        parent.addView(label)
        val slider = SeekBar(this).apply {
            max = 100
            progress = initial
        }
        slider.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(bar: SeekBar?, value: Int, fromUser: Boolean) {
                when (title) {
                    "Detail / sharpness" -> sharpness = value
                    "Contrast" -> contrast = value
                    else -> color = value
                }
                label.text = "$title: $value%"
            }
            override fun onStartTrackingTouch(bar: SeekBar?) {}
            override fun onStopTrackingTouch(bar: SeekBar?) { applyEnhancement() }
        })
        parent.addView(slider, matchWrap())
        return label
    }

    private fun updateLabels() {
        sharpnessLabel.text = "Detail / sharpness: $sharpness%"
        contrastLabel.text = "Contrast: $contrast%"
        colorLabel.text = "Color saturation: $color%"
    }

    private fun loadImage(uri: Uri) {
        try {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
            var sample = 1
            while (bounds.outWidth / sample > 1280 || bounds.outHeight / sample > 1280) sample *= 2
            val options = BitmapFactory.Options().apply { inSampleSize = sample.coerceAtLeast(1) }
            val bitmap = contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, options) }
            if (bitmap == null) {
                status.text = "Could not open this image. Try another file."
                return
            }
            originalBitmap = bitmap.copy(Bitmap.Config.ARGB_8888, false)
            status.text = "Image loaded • processing on device"
            applyEnhancement()
        } catch (e: Exception) {
            status.text = "Image error: " + (e.message ?: "unknown error")
        }
    }

    private fun applyEnhancement() {
        val source = originalBitmap ?: run {
            status.text = "Choose an image first."
            return
        }
        try {
            val detailed = sharpen(source, sharpness / 100f * 0.8f)
            val matrix = ColorMatrix()
            matrix.setSaturation(color / 50f)
            val saturationValues = matrix.array.copyOf()
            val c = contrast / 50f
            val translate = 128f * (1f - c)
            val contrastMatrix = ColorMatrix(floatArrayOf(
                c, 0f, 0f, 0f, translate,
                0f, c, 0f, 0f, translate,
                0f, 0f, c, 0f, translate,
                0f, 0f, 0f, 1f, 0f
            ))
            matrix.set(saturationValues)
            matrix.postConcat(contrastMatrix)
            preview.setImageBitmap(detailed)
            preview.colorFilter = ColorMatrixColorFilter(matrix)
            status.text = "Processed preview updated • no game injection"
        } catch (e: Exception) {
            status.text = "Processing failed: " + (e.message ?: "unknown error")
        }
    }

    private fun sharpen(input: Bitmap, amount: Float): Bitmap {
        if (amount <= 0.001f) return input
        val width = input.width
        val height = input.height
        val src = IntArray(width * height)
        val dst = IntArray(width * height)
        input.getPixels(src, 0, width, 0, 0, width, height)
        val center = 1f + 4f * amount
        for (y in 0 until height) {
            for (x in 0 until width) {
                val i = y * width + x
                if (x == 0 || y == 0 || x == width - 1 || y == height - 1) {
                    dst[i] = src[i]
                } else {
                    val p = src[i]
                    val n = src[i - width]
                    val s = src[i + width]
                    val w = src[i - 1]
                    val e = src[i + 1]
                    fun channel(shift: Int): Int {
                        val v = (((p shr shift) and 255) * center -
                            (((n shr shift) and 255) + ((s shr shift) and 255) +
                             ((w shr shift) and 255) + ((e shr shift) and 255)) * amount)
                        return v.roundToInt().coerceIn(0, 255)
                    }
                    dst[i] = Color.argb(
                        Color.alpha(p),
                        channel(16),
                        channel(8),
                        channel(0)
                    )
                }
            }
        }
        return Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).also {
            it.setPixels(dst, 0, width, 0, 0, width, height)
        }
    }

    private fun text(value: String, size: Float, color: Int, bold: Boolean): TextView =
        TextView(this).apply {
            text = value
            textSize = size
            setTextColor(color)
            gravity = Gravity.CENTER
            if (bold) setTypeface(typeface, android.graphics.Typeface.BOLD)
            setPadding(0, dp(8), 0, dp(8))
        }

    private fun matchWrap() = LinearLayout.LayoutParams(
        ViewGroup.LayoutParams.MATCH_PARENT,
        ViewGroup.LayoutParams.WRAP_CONTENT
    )

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).roundToInt()
}
