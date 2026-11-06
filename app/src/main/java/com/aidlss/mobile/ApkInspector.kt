package com.aidlss.mobile

import java.io.File
import java.util.zip.ZipFile

data class ApkInspection(
    val engine: String,
    val abis: List<String>,
    val nativeLibraries: Int,
    val hasUnityAssets: Boolean,
    val hasUnrealAssets: Boolean,
    val graphicsLibraries: List<String>
)

object ApkInspector {
    fun inspect(apks: List<File>): ApkInspection {
        val entries = apks.filter { it.isFile }.flatMap { apk ->
            try { ZipFile(apk).use { zip -> zip.entries().asSequence().map { it.name }.toList() } }
            catch (_: Exception) { emptyList() }
        }
        val lower = entries.map { it.lowercase() }
        val engine = when {
            lower.any { it.endsWith("/libunity.so") } -> "Unity"
            lower.any { it.endsWith("/libue4.so") || it.endsWith("/libunreal.so") } -> "Unreal Engine"
            lower.any { it.contains("godot") && it.endsWith(".so") } -> "Godot"
            lower.any { it.contains("libgdx") && it.endsWith(".so") } -> "libGDX"
            else -> "غير معروف من APK"
        }
        val abis = entries.asSequence()
            .filter { it.startsWith("lib/") && it.endsWith(".so") }
            .mapNotNull { it.split("/").getOrNull(1) }
            .distinct().sorted().toList()
        val graphics = buildList {
            if (lower.any { it.contains("vulkan") || it.contains("vk_") }) add("Vulkan (مؤشرات داخل APK)")
            if (lower.any { it.contains("gles") || it.contains("opengles") || it.endsWith("/libegl.so") }) add("OpenGL ES (مؤشرات داخل APK)")
            if (lower.any { it.contains("angle") }) add("ANGLE")
            if (lower.any { it.contains("turnip") || it.contains("freedreno") }) add("Turnip/Freedreno")
        }
        return ApkInspection(
            engine = engine,
            abis = abis,
            nativeLibraries = entries.count { it.startsWith("lib/") && it.endsWith(".so") },
            hasUnityAssets = entries.any { it.startsWith("assets/bin/Data/") },
            hasUnrealAssets = entries.any { it.startsWith("assets/") && it.endsWith(".pak") },
            graphicsLibraries = graphics.distinct()
        )
    }

    fun inspect(apk: File): ApkInspection = inspect(listOf(apk))
}
