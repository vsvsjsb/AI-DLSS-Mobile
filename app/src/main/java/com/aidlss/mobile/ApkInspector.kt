package com.aidlss.mobile

import java.io.File
import java.util.zip.ZipFile

data class ApkInspection(
    val engine: String,
    val abis: List<String>,
    val nativeLibraries: Int,
    val nativeLibraryNames: List<String>,
    val assetSamples: List<String>,
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
        val libraries = entries.filter { it.startsWith("lib/") && it.endsWith(".so") }
            .distinct().sorted()
        val engine = when {
            lower.any { it.endsWith("/libunity.so") } -> "Unity"
            lower.any { it.endsWith("/libue4.so") || it.endsWith("/libunreal.so") } -> "Unreal Engine"
            lower.any { it.contains("godot") && it.endsWith(".so") } -> "Godot"
            lower.any { it.contains("libgdx") && it.endsWith(".so") } -> "libGDX"
            lower.any { it.contains("feral") } -> "مؤشرات Feral Interactive (محرك غير مصنف)"
            else -> "غير معروف من APK"
        }
        val abis = libraries.mapNotNull { it.split("/").getOrNull(1) }.distinct().sorted()
        val graphics = buildList {
            if (lower.any { it.contains("vulkan") || it.contains("vk_") }) add("Vulkan (مؤشرات أسماء فقط)")
            if (lower.any { it.contains("gles") || it.contains("opengles") || it.endsWith("/libegl.so") }) add("OpenGL ES (مؤشرات أسماء فقط)")
            if (lower.any { it.contains("angle") }) add("ANGLE")
            if (lower.any { it.contains("turnip") || it.contains("freedreno") }) add("Turnip/Freedreno")
        }
        val assetSamples = entries.filter {
            it.startsWith("assets/") && !it.endsWith("/")
        }.take(35)
        return ApkInspection(
            engine = engine,
            abis = abis,
            nativeLibraries = libraries.size,
            nativeLibraryNames = libraries.map { it.substringAfterLast('/') },
            assetSamples = assetSamples,
            hasUnityAssets = entries.any { it.startsWith("assets/bin/Data/") },
            hasUnrealAssets = entries.any { it.startsWith("assets/") && it.endsWith(".pak") },
            graphicsLibraries = graphics.distinct()
        )
    }

    fun inspect(apk: File): ApkInspection = inspect(listOf(apk))
}
