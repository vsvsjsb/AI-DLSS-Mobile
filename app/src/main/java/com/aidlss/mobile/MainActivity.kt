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
import kotlin.math.roundToInt

class MainActivity : ComponentActivity() {
    private lateinit var status: TextView
    private lateinit var details: TextView
    private lateinit var profileStatus: TextView
    private var selectedPackage: String? = null
    private var selectedScale = 1.5f
    private var selectedBackend = "Vulkan (المفضل)"
    private val prefs by lazy { getSharedPreferences("dlss_test_profile", MODE_PRIVATE) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        selectedScale = prefs.getFloat("scale", 1.5f)
        selectedBackend = prefs.getString("backend", "Vulkan (المفضل)") ?: "Vulkan (المفضل)"

        val page = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(24), dp(18), dp(30))
            setBackgroundColor(Color.rgb(12, 16, 27))
        }
        val scroll = ScrollView(this).apply { addView(page) }
        page.addView(text("AI DLSS MOBILE • VULKAN TEST", 23f, Color.rgb(0, 220, 255), true))
        page.addView(text("ملف تجربة رفع الدقة – Adreno / Android", 15f, Color.WHITE, true))
        page.addView(text("إعدادات اختبار محفوظة + فحص دعم Vulkan ومعلومات اللعبة", 12f, Color.LTGRAY, false))

        page.addView(section("1) محرك الرسوميات"))
        page.addView(button("Vulkan (المفضل)") {
            selectedBackend = "Vulkan (المفضل)"
            updateProfileStatus()
        })
        page.addView(button("OpenGL ES (احتياطي)") {
            selectedBackend = "OpenGL ES (احتياطي)"
            updateProfileStatus()
        })
        page.addView(section("2) مستوى رفع الدقة"))
        listOf(1.25f, 1.5f, 2.0f).forEach { scale ->
            page.addView(button(scale.toString() + "x • " + scaleLabel(scale)) {
                selectedScale = scale
                updateProfileStatus()
            })
        }
        page.addView(button("حفظ إعداد التجربة") {
            prefs.edit().putFloat("scale", selectedScale).putString("backend", selectedBackend).apply()
            status.text = "تم حفظ إعداد التجربة محلياً."
            updateProfileStatus()
        })
        profileStatus = text("", 13f, Color.rgb(0, 220, 255), false)
        page.addView(profileStatus)
        updateProfileStatus()

        page.addView(section("3) فحص الجهاز وVulkan"))
        page.addView(button("فحص دعم Vulkan ودقة الشاشة") { details.text = deviceReport() })
        details = text(deviceReport(), 12f, Color.WHITE, false).apply { gravity = Gravity.START }
        page.addView(details)

        page.addView(section("4) فحص اللعبة"))
        page.addView(button("اختيار Tomb Raider أو مشغّلها وفحص APK") { showInstalledApps() })
        page.addView(button("تشغيل التطبيق المحدد") { launchSelectedApp() })
        status = text("لم يتم فحص اللعبة بعد. اختر اللعبة أو مشغّلها المثبت.", 13f, Color.rgb(0, 220, 255), false)
        page.addView(status)

        page.addView(section("5) حدود التجربة – اقرأها"))
        page.addView(text(
            "هذا الإصدار يحفظ إعدادات نسبة رفع الدقة ويكشف دعم Vulkan على الهاتف ويحلل مكتبات اللعبة. لا يفرض Vulkan على اللعبة ولا يغيّر دقتها الفعلية ولا يعترض إطاراتها حتى الآن. رفع الدقة المعروض هنا إعداد اختبار فقط؛ لن يظهر تحسن داخل اللعبة قبل تنفيذ مسار التقاط/معالجة/عرض الإطارات أو ربط Vulkan داخل اللعبة.",
            12f, Color.rgb(255, 195, 110), false
        ))
        setContentView(scroll)
    }

    private fun updateProfileStatus() {
        if (!::profileStatus.isInitialized) return
        val dm = resources.displayMetrics
        val w = (dm.widthPixels * selectedScale).roundToInt()
        val h = (dm.heightPixels * selectedScale).roundToInt()
        profileStatus.text = "المحرك المحدد: " + selectedBackend +
            "\nنسبة الرفع: " + selectedScale + "x" +
            "\nهدف المعالجة النظري: " + w + " × " + h +
            "\nالحالة: إعداد محفوظ عند الضغط على زر الحفظ."
    }

    private fun scaleLabel(scale: Float) = when (scale) {
        1.25f -> "رفع خفيف"
        1.5f -> "رفع متوسط"
        else -> "رفع قوي"
    }

    private fun deviceReport(): String {
        val features = packageManager.systemAvailableFeatures
        val vkLevel = features.firstOrNull { it.name == "android.hardware.vulkan.level" }?.version
        val vkVersion = features.firstOrNull { it.name == "android.hardware.vulkan.version" }?.version
        val dm = resources.displayMetrics
        return buildString {
            appendLine("الجهاز: " + android.os.Build.MANUFACTURER + " " + android.os.Build.MODEL)
            appendLine("Android: " + android.os.Build.VERSION.RELEASE + " (API " + android.os.Build.VERSION.SDK_INT + ")")
            appendLine("دقة الشاشة المنطقية: " + dm.widthPixels + " × " + dm.heightPixels)
            appendLine("كثافة الشاشة: " + dm.densityDpi + " dpi")
            appendLine("Vulkan hardware level: " + (vkLevel ?: "غير معلن من النظام"))
            appendLine("Vulkan version feature: " + (vkVersion ?: "غير معلن من النظام"))
            appendLine("Vulkan في الجهاز: " + if (vkVersion != null) "ميزة Vulkan معلنة" else "لم يتم تأكيدها عبر ميزات Android")
            appendLine("ملاحظة: إعلان الميزة لا يثبت أن اللعبة تستخدم Vulkan أثناء التشغيل.")
        }
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
                            appendLine("مؤشرات الرسوميات: " + result.graphicsLibraries.joinToString().ifBlank { "غير محسومة" })
                            appendLine("الرموز المجمعة: " + result.nativeGraphicsHints.joinToString().ifBlank { "لا توجد" })
                            appendLine()
                            appendLine("تحليل المكتبات الأصلية:")
                            result.libraryAnalyses.forEach { lib ->
                                appendLine("[" + lib.name + "]")
                                appendLine("الحجم: " + if (lib.sizeBytes >= 0) (lib.sizeBytes / 1024).toString() + " KB" else "غير معروف")
                                appendLine("ELF64: " + if (lib.elf64) "نعم" else "لا/غير معروف")
                                appendLine("Vulkan: " + lib.vulkanMarkers.joinToString().ifBlank { "لا توجد مؤشرات" })
                                appendLine("OpenGL ES/EGL: " + lib.openGlMarkers.joinToString().ifBlank { "لا توجد مؤشرات" })
                                appendLine("DT_NEEDED: " + lib.dependencies.joinToString().ifBlank { "غير مقروءة" })
                                appendLine()
                            }
                            appendLine("عدد ملفات assets: " + result.assetCount)
                            appendLine("Unity: " + if (result.hasUnityAssets) "موجود" else "غير مكتشف")
                            appendLine("Unreal PAK: " + if (result.hasUnrealAssets) "موجود" else "غير مكتشف")
                            result.assetSamples.forEach { appendLine(" • " + it) }
                            appendLine()
                            appendLine(if (isTombRaider) "التعرف على اللعبة: Tomb Raider." else "الاسم لا يؤكد أنها Tomb Raider.")
                            appendLine()
                            appendLine("=== ملف تعريف التوافق ===")
                            append(GameProfiles.report(app.packageName))
                            appendLine()
                            appendLine("=== إعداد رفع الدقة المختار (غير مطبق داخل اللعبة) ===")
                            appendLine("المحرك المطلوب: " + selectedBackend)
                            appendLine("نسبة الرفع: " + selectedScale + "x")
                        }
                        runOnUiThread {
                            details.text = report
                            status.text = if (isTombRaider) "اكتمل فحص Tomb Raider بنجاح." else "اكتمل فحص التطبيق المحدد."
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

    private fun section(value: String) = text(value, 17f, Color.rgb(0, 220, 255), true).apply {
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
