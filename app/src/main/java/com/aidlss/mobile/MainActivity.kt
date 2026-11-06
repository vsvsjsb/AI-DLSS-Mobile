package com.aidlss.mobile

import android.app.AlertDialog
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.net.Uri
import android.os.Bundle
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
import androidx.documentfile.provider.DocumentFile
import kotlin.math.roundToInt

class MainActivity : ComponentActivity() {
    private lateinit var preview: ImageView
    private lateinit var status: TextView
    private lateinit var gameScanStatus: TextView
    private var originalBitmap: Bitmap? = null
    private val prefs by lazy { getSharedPreferences("graphics_settings", MODE_PRIVATE) }
    private val sliders = linkedMapOf<String, TextView>()
    private val values = linkedMapOf(
        "الحدة" to 35, "الوضوح" to 50, "التباين" to 50, "التشبع" to 50,
        "الأحمر" to 50, "الأخضر" to 50, "الأزرق" to 50,
        "MSAA" to 0, "الترشيح متباين الخواص" to 0, "إزاحة Mipmap" to 50,
        "جودة ترشيح الإكساءات" to 50
    )

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
        values.keys.toList().forEach { values[it] = prefs.getInt(it, values[it] ?: 50) }

        val page = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(22), dp(18), dp(30))
            setBackgroundColor(Color.rgb(13, 17, 29))
        }
        val scroll = ScrollView(this).apply { addView(page) }

        page.addView(text("AI DLSS MOBILE", 27f, Color.rgb(0, 220, 255), true))
        page.addView(text("مركز التحكم الرسومي للألعاب", 17f, Color.WHITE, true))
        page.addView(text("هاتفك: OnePlus 10T • Adreno 730", 13f, Color.LTGRAY, false))

        page.addView(section("الألعاب والتطبيقات"))
        page.addView(button("اختيار لعبة أو تطبيق مثبت") { showInstalledGames() })
        page.addView(button("فحص مجلد لعبة أو محاكي") { gameFolderPicker.launch(null) })
        gameScanStatus = text("اختر لعبة أو افحص مجلدها للتعرّف على الملفات.", 13f, Color.rgb(0, 220, 255), false)
        page.addView(gameScanStatus)

        page.addView(section("تحسين الصورة والفلاتر"))
        page.addView(button("اختيار صورة للتجربة") { imagePicker.launch("image/*") })
        preview = ImageView(this).apply {
            scaleType = ImageView.ScaleType.FIT_CENTER
            adjustViewBounds = true
            setBackgroundColor(Color.rgb(25, 31, 46))
            minimumHeight = dp(190)
            contentDescription = "معاينة الصورة"
        }
        page.addView(preview, matchWrap().apply { topMargin = dp(10); bottomMargin = dp(10) })

        addManualSlider(page, "الحدة", "زيادة حدة الحواف والتفاصيل")
        addManualSlider(page, "الوضوح", "تحسين الإحساس بالتفاصيل")
        addManualSlider(page, "التباين", "التحكم في الفرق بين المناطق الفاتحة والداكنة")
        addManualSlider(page, "التشبع", "التحكم في قوة الألوان")
        addManualSlider(page, "الأحمر", "تعديل قناة اللون الأحمر")
        addManualSlider(page, "الأخضر", "تعديل قناة اللون الأخضر")
        addManualSlider(page, "الأزرق", "تعديل قناة اللون الأزرق")

        page.addView(section("فلاتر لونية جاهزة"))
        page.addView(button("طبيعي") { setFilter(50, 50, 50, 50, 50, 50, 50) })
        page.addView(button("ألوان زاهية") { setFilter(55, 58, 68, 78, 55, 52, 58) })
        page.addView(button("سينمائي") { setFilter(62, 60, 58, 55, 55, 50, 48) })
        page.addView(button("بارد") { setFilter(48, 52, 50, 48, 46, 52, 62) })
        page.addView(button("دافئ") { setFilter(48, 52, 52, 55, 62, 53, 45) })

        page.addView(section("إعدادات الرسوميات المتقدمة"))
        page.addView(text("هذه القيم ملفات إعدادات قابلة للحفظ. تأثيرها داخل اللعبة يحتاج دعماً من المحاكي أو اللعبة.", 12f, Color.LTGRAY, false))
        addManualSlider(page, "MSAA", "مستوى مانع التشويش متعدد العينات")
        addManualSlider(page, "الترشيح متباين الخواص", "أقصى مستوى لترشيح الإكساءات")
        addManualSlider(page, "إزاحة Mipmap", "إزاحة مستوى تفاصيل Mipmap")
        addManualSlider(page, "جودة ترشيح الإكساءات", "منخفضة إلى عالية")

        page.addView(button("تقنية رفع الدقة FSR") {
            chooseOption("FSR") { prefs.edit().putString("fsr", it).apply(); status.text = "تم حفظ وضع FSR: $it" }
        })
        page.addView(button("دقة العرض: ${prefs.getString("resolution", "1080p")}") {
            chooseOption("الدقة") { prefs.edit().putString("resolution", it).apply(); status.text = "تم حفظ الدقة: $it" }
        })
        page.addView(button("مقياس الدقة: ${prefs.getString("scale", "X1")}") {
            chooseOption("مقياس الدقة") { prefs.edit().putString("scale", it).apply(); status.text = "تم حفظ المقياس: $it" }
        })
        page.addView(button(if (prefs.getBoolean("ultra", false)) "الوضع الفائق: مفعّل" else "الوضع الفائق: متوقف") {
            val enabled = !prefs.getBoolean("ultra", false)
            prefs.edit().putBoolean("ultra", enabled).apply()
            status.text = if (enabled) "تم تفعيل ملف الوضع الفائق" else "تم إيقاف الوضع الفائق"
            recreate()
        })
        page.addView(button(if (prefs.getBoolean("framegen", false)) "توليد الإطارات: مفعّل" else "توليد الإطارات: متوقف") {
            val enabled = !prefs.getBoolean("framegen", false)
            prefs.edit().putBoolean("framegen", enabled).apply()
            status.text = if (enabled) "تم حفظ طلب توليد الإطارات؛ يحتاج محركاً داعماً" else "تم إيقاف توليد الإطارات"
            recreate()
        })

        page.addView(section("تطبيق الإعدادات"))
        page.addView(button("تحديث معاينة الصورة") { applyEnhancement() })
        page.addView(button("إعادة ضبط الكل") {
            values.keys.forEach { values[it] = if (it == "الحدة") 35 else 50 }
            values["MSAA"] = 0
            values["الترشيح متباين الخواص"] = 0
            values.forEach { (key, value) -> prefs.edit().putInt(key, value).apply() }
            sliders.forEach { (key, label) -> label.text = "$key: ${values[key]}%" }
            applyEnhancement()
        })
        status = text("الإعدادات تحفظ على الهاتف. اختر صورة لتجربة الفلاتر.", 13f, Color.rgb(0, 220, 255), false)
        page.addView(status)
        page.addView(text("تنبيه: تعديل صورة المعاينة يعمل داخل التطبيق فقط. لا يوجد حتى الآن حقن رسومي عام في ألعاب أندرويد.", 12f, Color.LTGRAY, false))
        setContentView(scroll)
    }

    private fun showInstalledGames() {
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val apps = packageManager.queryIntentActivities(intent, 0)
            .filter { it.activityInfo.packageName != packageName }
            .sortedBy { it.loadLabel(packageManager).toString().lowercase() }
        if (apps.isEmpty()) {
            status.text = "لم يتم العثور على تطبيقات قابلة للتشغيل."
            return
        }
        val labels = apps.map { it.loadLabel(packageManager).toString() }.toTypedArray()
        AlertDialog.Builder(this)
            .setTitle("اختر لعبة أو تطبيقاً")
            .setItems(labels) { _, which ->
                val info = apps[which].activityInfo
                val launch = packageManager.getLaunchIntentForPackage(info.packageName)
                if (launch != null) {
                    status.text = "تم اختيار: ${labels[which]} — تشغيل التطبيق"
                    startActivity(launch)
                } else status.text = "تعذر تشغيل هذا التطبيق."
            }
            .setNegativeButton("إلغاء", null)
            .show()
    }

    private fun chooseOption(title: String, onChoose: (String) -> Unit) {
        val options = when (title) {
            "FSR" -> arrayOf("إيقاف", "FSR 1.0 (يتطلب دمجاً)", "FSR 2 (يتطلب دمجاً)", "FSR 3 (يتطلب دمجاً)")
            "الدقة" -> arrayOf("1080p", "1440p", "2K", "4K")
            else -> arrayOf("X1", "X4", "X8", "X16")
        }
        AlertDialog.Builder(this).setTitle(title).setItems(options) { _, which -> onChoose(options[which]) }
            .setNegativeButton("إلغاء", null).show()
    }

    private fun addManualSlider(parent: LinearLayout, key: String, hint: String) {
        val initial = values[key] ?: 50
        val label = text("$key: $initial%", 14f, Color.WHITE, true)
        sliders[key] = label
        parent.addView(label)
        parent.addView(text(hint, 11f, Color.LTGRAY, false))
        val bar = SeekBar(this).apply { max = 100; progress = initial }
        bar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                values[key] = progress
                prefs.edit().putInt(key, progress).apply()
                label.text = "$key: $progress%"
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) { applyEnhancement() }
        })
        parent.addView(bar, matchWrap())
    }

    private fun setFilter(sharp: Int, clear: Int, contrast: Int, saturation: Int, red: Int, green: Int, blue: Int) {
        val presets = mapOf("الحدة" to sharp, "الوضوح" to clear, "التباين" to contrast, "التشبع" to saturation,
            "الأحمر" to red, "الأخضر" to green, "الأزرق" to blue)
        presets.forEach { (key, value) ->
            values[key] = value
            prefs.edit().putInt(key, value).apply()
            sliders[key]?.text = "$key: $value%"
        }
        applyEnhancement()
    }

    private fun scanGameFolder(uri: Uri) {
        gameScanStatus.text = "جارٍ فحص المجلد…"
        Thread {
            try {
                val root = DocumentFile.fromTreeUri(this, uri) ?: throw IllegalStateException("تعذر فتح المجلد")
                val queue = ArrayDeque<Pair<DocumentFile, Int>>()
                queue.add(root to 0)
                val extensions = linkedSetOf<String>()
                val indicators = linkedSetOf<String>()
                var files = 0
                var folders = 0
                while (queue.isNotEmpty() && files + folders < 2500) {
                    val (dir, depth) = queue.removeFirst()
                    if (depth > 6) continue
                    for (child in try { dir.listFiles() } catch (_: Exception) { emptyArray() }) {
                        val name = child.name ?: continue
                        if (child.isDirectory) { folders++; if (depth < 6) queue.add(child to depth + 1) }
                        else {
                            files++
                            val lower = name.lowercase()
                            val ext = lower.substringAfterLast('.', "")
                            if (ext.isNotBlank()) extensions.add(ext)
                            if (lower.contains("unityplayer") || lower == "global-metadata.dat" || lower == "libil2cpp.so") indicators.add("Unity / IL2CPP")
                            if (lower.contains("libue4") || lower.contains("unreal")) indicators.add("Unreal Engine")
                            if (lower.contains("godot")) indicators.add("Godot")
                            if (lower.endsWith(".pak") || lower.endsWith(".obb")) indicators.add("أرشيفات أصول اللعبة")
                            if (listOf(".iso", ".chd", ".cso", ".rvz", ".xex", ".cue").any { lower.endsWith(it) }) indicators.add("ملفات ألعاب المحاكيات")
                        }
                        if (files + folders >= 2500) break
                    }
                }
                val report = "اكتمل الفحص\nالملفات: $files • المجلدات: $folders\nالامتدادات: ${extensions.take(18).joinToString(", ")}\nمؤشرات: ${indicators.joinToString(", ").ifBlank { "لم يتم التحديد" }}\n\nالفحص للقراءة فقط."
                runOnUiThread { gameScanStatus.text = report }
            } catch (e: Exception) {
                runOnUiThread { gameScanStatus.text = "فشل الفحص: ${e.message ?: "خطأ غير معروف"}" }
            }
        }.start()
    }

    private fun loadImage(uri: Uri) {
        try {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
            var sample = 1
            while (bounds.outWidth / sample > 1280 || bounds.outHeight / sample > 1280) sample *= 2
            val options = BitmapFactory.Options().apply { inSampleSize = sample.coerceAtLeast(1) }
            val bitmap = contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, options) }
            if (bitmap == null) { status.text = "تعذر فتح الصورة."; return }
            originalBitmap = bitmap.copy(Bitmap.Config.ARGB_8888, false)
            applyEnhancement()
        } catch (e: Exception) { status.text = "خطأ في الصورة: ${e.message}" }
    }

    private fun applyEnhancement() {
        val source = originalBitmap ?: run { status.text = "اختر صورة أولاً لتجربة الإعدادات."; return }
        try {
            val sharpAmount = ((values["الحدة"] ?: 35) / 100f) * 0.9f
            val detail = sharpen(source, sharpAmount)
            val contrast = (values["التباين"] ?: 50) / 50f
            val saturation = (values["التشبع"] ?: 50) / 50f
            val red = (values["الأحمر"] ?: 50) / 50f
            val green = (values["الأخضر"] ?: 50) / 50f
            val blue = (values["الأزرق"] ?: 50) / 50f
            val brightness = 128f * (1f - contrast)
            val matrix = ColorMatrix(floatArrayOf(
                contrast * red, 0f, 0f, 0f, brightness,
                0f, contrast * green, 0f, 0f, brightness,
                0f, 0f, contrast * blue, 0f, brightness,
                0f, 0f, 0f, 1f, 0f
            ))
            matrix.postConcat(ColorMatrix().apply { setSaturation(saturation) })
            preview.setImageBitmap(detail)
            preview.colorFilter = ColorMatrixColorFilter(matrix)
            status.text = "تم تحديث المعاينة بالإعدادات اليدوية."
        } catch (e: Exception) { status.text = "فشل التعديل: ${e.message}" }
    }

    private fun sharpen(input: Bitmap, amount: Float): Bitmap {
        if (amount <= 0.001f) return input
        val width = input.width
        val height = input.height
        val src = IntArray(width * height)
        val dst = IntArray(width * height)
        input.getPixels(src, 0, width, 0, 0, width, height)
        val center = 1f + 4f * amount
        for (y in 0 until height) for (x in 0 until width) {
            val i = y * width + x
            if (x == 0 || y == 0 || x == width - 1 || y == height - 1) dst[i] = src[i]
            else {
                val p = src[i]; val n = src[i - width]; val s = src[i + width]; val w = src[i - 1]; val e = src[i + 1]
                fun ch(shift: Int): Int = (((p shr shift) and 255) * center -
                    (((n shr shift) and 255) + ((s shr shift) and 255) + ((w shr shift) and 255) + ((e shr shift) and 255)) * amount).roundToInt().coerceIn(0, 255)
                dst[i] = Color.argb(Color.alpha(p), ch(16), ch(8), ch(0))
            }
        }
        return Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).also { it.setPixels(dst, 0, width, 0, 0, width, height) }
    }

    private fun section(value: String) = text(value, 18f, Color.rgb(0, 220, 255), true).apply {
        gravity = Gravity.START
        setPadding(0, dp(22), 0, dp(8))
    }

    private fun button(label: String, action: () -> Unit) = Button(this).apply {
        text = label
        setOnClickListener { action() }
    }

    private fun text(value: String, size: Float, color: Int, bold: Boolean) = TextView(this).apply {
        text = value; textSize = size; setTextColor(color); gravity = Gravity.CENTER
        if (bold) setTypeface(typeface, android.graphics.Typeface.BOLD)
        setPadding(0, dp(7), 0, dp(7))
    }

    private fun matchWrap() = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).roundToInt()
}
