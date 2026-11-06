package com.aidlss.mobile

/**
 * Compatibility metadata for the supplied Feral Interactive Android APK.
 * Diagnostic only: these fields do not force a game's renderer or process frames.
 */
object GameProfiles {
    const val TOMB_RAIDER_PACKAGE = "com.feralinteractive.tombraider_android"
    const val TOMB_RAIDER_VERSION = "1.4RC6"
    const val TOMB_RAIDER_ABI = "arm64-v8a"

    data class Profile(
        val id: String,
        val displayName: String,
        val packageName: String,
        val expectedVersion: String,
        val expectedAbi: String,
        val graphicsApis: List<String>,
        val preferredApi: String,
        val primaryLibrary: String,
        val upscaleRatios: List<Float>,
        val notes: List<String>
    )

    val tombRaider2013 = Profile(
        id = "tomb_raider_2013_feral",
        displayName = "Tomb Raider (2013) – Feral Interactive",
        packageName = TOMB_RAIDER_PACKAGE,
        expectedVersion = TOMB_RAIDER_VERSION,
        expectedAbi = TOMB_RAIDER_ABI,
        graphicsApis = listOf("Vulkan", "OpenGL ES / EGL"),
        preferredApi = "Vulkan",
        primaryLibrary = "libTombRaider.so",
        upscaleRatios = listOf(1.25f, 1.5f, 2.0f),
        notes = listOf(
            "Static APK inspection found Vulkan entry-point markers and libvulkan.so in libTombRaider.so.",
            "OpenGL ES / EGL dependencies are also present in libTombRaider.so.",
            "Vulkan is the requested preferred path; APK markers do not prove the active runtime renderer.",
            "The supplied analysis found five native libraries; libTombRaider.so is the main graphics library.",
            "Foundation Engine is user-reported and is not independently verified by this APK scan.",
            "Upscale ratios are test-profile targets only; no in-game frame scaling is implemented yet."
        )
    )

    fun isTombRaider(vararg candidates: String?): Boolean {
        val values = candidates.filterNotNull()
        if (values.any { it.equals(TOMB_RAIDER_PACKAGE, ignoreCase = true) }) return true
        val text = values.joinToString(" ").lowercase().replace('_', ' ').replace('-', ' ')
        return text.contains("tomb raider") || text.contains("tombraider") ||
            text.contains("lara croft") || text.contains("tombraider2013")
    }

    fun forPackage(packageName: String): Profile? =
        if (packageName.equals(TOMB_RAIDER_PACKAGE, ignoreCase = true)) tombRaider2013 else null

    fun report(packageName: String): String {
        val profile = forPackage(packageName) ?: return "لا يوجد ملف تعريف معروف لهذه الحزمة."
        return buildString {
            appendLine("ملف التعريف: " + profile.displayName)
            appendLine("المعرّف: " + profile.id)
            appendLine("الحزمة: " + profile.packageName)
            appendLine("الإصدار المرجعي: " + profile.expectedVersion)
            appendLine("المعمارية المتوقعة: " + profile.expectedAbi)
            appendLine("واجهات الرسوميات ذات المؤشرات: " + profile.graphicsApis.joinToString())
            appendLine("المسار المفضل للتجربة: " + profile.preferredApi)
            appendLine("المكتبة الرئيسية: " + profile.primaryLibrary)
            appendLine("نسب الرفع المقترحة: " + profile.upscaleRatios.joinToString() + "x")
            appendLine("الحالة: ملف تعريف تشخيصي؛ لم يتم اعتراض إطارات اللعبة.")
            profile.notes.forEach { appendLine("• " + it) }
        }
    }
}
