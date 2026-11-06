package com.aidlss.mobile

import android.app.AlertDialog
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.view.Gravity
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.activity.ComponentActivity
import java.io.File

class MainActivity : ComponentActivity() {
    private lateinit var status: TextView
    private lateinit var details: TextView
    private var selectedPackage: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val page = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(24), dp(18), dp(30))
            setBackgroundColor(Color.rgb(12, 16, 27))
        }
        val scroll = ScrollView(this).apply { addView(page) }
        page.addView(text("DLSS5 MOBILE", 28f, Color.rgb(0, 220, 255), true))
        page.addView(text("TOMB RAIDER 2013 • ANDROID", 16f, Color.WHITE, true))
        page.addView(text("نسخة التطوير الأولى مخصصة للعبة Tomb Raider فقط", 13f, Color.LTGRAY, false))
        page.addView(section("فحص اللعبة"))
        page.addView(button("اختيار Tomb Raider أو مشغّلها وفحص APK") { showInstalledApps() })
        page.addView(button("تشغيل التطبيق المحدد") { launchSelectedApp() })
        status = text("لم يتم فحص اللعبة بعد. اختر تطبيق اللعبة أو مشغّلها المثبت.", 14f, Color.rgb(0, 220, 255), false)
        page.addView(status)
        page.addView(section("نتيجة التحليل"))
        details = text("ستظهر هنا معلومات الحزمة، المحرك، المكتبات الأصلية وأسماء ملفات الأصول.", 13f, Color.WHITE, false)
        details.gravity = Gravity.START
        page.addView(details)
        page.addView(section("هدف هذا الإصدار"))
        page.addView(text(
            "1) تحديد نسخة Tomb Raider المثبتة.\n2) فحص APK الأساسي وملفات Split APK دون طلب Android/data أو OBB.\n3) استخراج أسماء المكتبات الأصلية وبصمات ملفات الأصول.\n4) استخدام النتائج لتحديد طريقة الربط الرسومي المناسبة للعبة.",
            13f, Color.LTGRAY, false
        ))
        page.addView(section("ملاحظة تقنية"))
        page.addView(text(
            "هذا إصدار تشخيص وتجهيز للتكامل، وليس محرك تحسين داخل اللعبة بعد. أسماء المكتبات تعطي مؤشرات ولا تثبت وحدها واجهة الرسم المستخدمة.",
            12f, Color.rgb(255, 195, 110), false
        ))
        setContentView(scroll)
    }

    private fun showInstalledApps() {
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val apps = packageManager.queryIntentActivities(intent, 0)
            .filter { it.activityInfo.packageName != packageName }
            .distinctBy { it.activityInfo.packageName }
            .sortedBy { it.loadLabel(packageManager).toString().lowercase() }
        if (apps.isEmpty()) {
            status.text = "لم يتم العثور على تطبيقات قابلة للتشغيل."
            return
        }
        val labels = apps.map { it.loadLabel(packageManager).toString() }.toTypedArray()
        AlertDialog.Builder(this)
            .setTitle("اختر Tomb Raider أو مشغّلها")
            .setItems(labels) { _, which ->
                val app = apps[which].activityInfo.applicationInfo
                selectedPackage = app.packageName
                status.text = "جارٍ فحص " + labels[which] + "…"
                details.text = "نقرأ APK الأساسي وأي ملفات Split APK متاحة."
                Thread {
                    try {
                        val apkFiles = mutableListOf(File(app.sourceDir))
                        app.splitSourceDirs?.forEach { apkFiles.add(File(it)) }
                        val result = ApkInspector.inspect(apkFiles)
                        val info = packageManager.getPackageInfo(app.packageName, 0)
                        val version = info.versionName ?: "غير معروف"
                        val totalSize = apkFiles.filter { it.isFile }.sumOf { it.length() }
                        val isTombRaider = GameProfiles.isTombRaider(labels[which], app.packageName)
                        val report = buildString {
                            appendLine("الاسم: " + labels[which])
                            appendLine("الحزمة: " + app.packageName)
                            appendLine("الإصدار: " + version)
                            appendLine("عدد ملفات APK: " + apkFiles.size)
                            appendLine("الحجم الإجمالي: " + (totalSize / (1024 * 1024)) + " MB")
                            appendLine("المحرك/البصمة: " + result.engine)
                            appendLine("معماريات CPU: " + result.abis.joinToString().ifBlank { "غير محددة" })
                            appendLine("عدد المكتبات الأصلية: " + result.nativeLibraries)
                            appendLine("أسماء المكتبات الأصلية:")
                            result.nativeLibraryNames.forEach { appendLine(" • " + it) }
                            appendLine("مؤشرات الرسوميات: " + result.graphicsLibraries.joinToString().ifBlank { "غير محسومة من أسماء الملفات" })
                            appendLine("ملفات Unity: " + if (result.hasUnityAssets) "موجودة" else "غير مكتشفة")
                            appendLine("ملفات Unreal PAK: " + if (result.hasUnrealAssets) "موجودة" else "غير مكتشفة")
                            appendLine("عينات من ملفات الأصول:")
                            result.assetSamples.forEach { appendLine(" • " + it) }
                            appendLine()
                            append(if (isTombRaider) "التعرف على اللعبة: الاسم يطابق Tomb Raider." else "التعرف على اللعبة: الاسم لا يؤكد أنها Tomb Raider؛ تحقق من التطبيق المحدد.")
                        }
                        runOnUiThread {
                            details.text = report
                            status.text = if (isTombRaider) "اكتمل فحص Tomb Raider بنجاح." else "اكتمل فحص التطبيق المحدد. تحقق من أنه مشغّل Tomb Raider."
                        }
                    } catch (e: Exception) {
                        runOnUiThread { status.text = "تعذر فحص التطبيق: " + (e.message ?: "خطأ غير معروف") }
                    }
                }.start()
            }
            .setNegativeButton("إلغاء", null)
            .show()
    }

    private fun launchSelectedApp() {
        val target = selectedPackage
        if (target == null) {
            status.text = "اختر التطبيق وافحصه أولاً."
            return
        }
        val launchIntent = packageManager.getLaunchIntentForPackage(target)
        if (launchIntent == null) {
            status.text = "لا يوجد مشغّل مباشر لهذا التطبيق."
            return
        }
        startActivity(launchIntent)
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
        text = value
        textSize = size
        setTextColor(color)
        gravity = Gravity.CENTER
        if (bold) setTypeface(typeface, android.graphics.Typeface.BOLD)
        setPadding(0, dp(7), 0, dp(7))
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()
}
