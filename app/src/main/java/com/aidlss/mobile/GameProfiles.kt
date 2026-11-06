package com.aidlss.mobile

/**
 * Static compatibility metadata for the Feral Interactive Android release.
 * This profile is diagnostic metadata only; it does not hook or process game frames.
 */
object GameProfiles {
    const val TOMB_RAIDER_PACKAGE = "com.feralinteractive.tombraider_android"
    const val TOMB_RAIDER_VERSION = "1.4RC6"
    const val TOMB_RAIDER_ABI = "arm64-v8a"

    data class Profile(
        val id: String,
        val displayName: String,
        val packageName: String,
        val expectedAbi: String,
        val graphicsApis: List<String>,
        val primaryLibrary: String,
        val notes: List<String>
    )

    val tombRaider2013 = Profile(
        id = "tomb_raider_2013_feral",
        displayName = "Tomb Raider (2013) – Feral Interactive",
        packageName = TOMB_RAIDER_PACKAGE,
        expectedAbi = TOMB_RAIDER_ABI,
        graphicsApis = listOf("Vulkan", "OpenGL ES / EGL"),
        primaryLibrary = "libTombRaider.so",
        notes = listOf(
            "Static APK analysis found Vulkan entry-point markers and libvulkan.so in libTombRaider.so.",
            "OpenGL ES / EGL dependencies are also present in libTombRaider.so.",
            "APK symbols do not prove which graphics API is active during gameplay.",
            "Unity and Unreal asset layouts were not detected in the supplied analysis.",
            "Runtime frame processing and graphics interception are not implemented."
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
            appendLine("ملف التعريف: ${profile.displayName}")
            appendLine("المعرّف: ${profile.id}")
            appendLine("الحزمة المطابقة: ${profile.packageName}")
            appendLine("الإصدار المرجعي: ${TOMB_RAIDER_VERSION}")
            appendLine("المعمارية المتوقعة: ${profile.expectedAbi}")
            appendLine("واجهات الرسوميات ذات المؤشرات: ${profile.graphicsApis.joinToString()}")
            appendLine("المكتبة الرئيسية: ${profile.primaryLibrary}")
            appendLine("الحالة: ملف تعريف وتشخيص APK فقط؛ لا يوجد اعتراض لإطارات اللعبة بعد.")
            profile.notes.forEach { appendLine("• $it") }
        }
    }
}
