package com.aidlss.mobile

import java.io.File
import java.util.zip.ZipFile

data class ApkInspection(
    val engine: String,
    val abis: List<String>,
    val nativeLibraries: Int,
    val hasUnityAssets: Boolean,
    val hasUnrealAssets: Boolean
)

object ApkInspector {
    fun inspect(apks: List<File>): ApkInspection {
        val entries = apks.filter { it.isFile }.flatMap { apk ->
            try { ZipFile(apk).use { zip -> zip.entries().asSequence().map { it.name }.toList() } }
            catch (_: Exception) { emptyList() }
        }
        val engine = when {
            entries.any { it.endsWith("/libunity.so") } -> "Unity"
            entries.any { it.endsWith("/libUE4.so") || it.endsWith("/libUnreal.so") } -> "Unreal Engine"
            entries.any { it.contains("godot", ignoreCase = true) && it.endsWith(".so") } -> "Godot"
            entries.any { it.contains("libgdx", ignoreCase = true) && it.endsWith(".so") } -> "libGDX"
            else -> "غير معروف من APK"
        }
        val abis = entries.asSequence()
            .filter { it.startsWith("lib/") && it.endsWith(".so") }
            .mapNotNull { it.split("/").getOrNull(1) }
            .distinct().sorted().toList()
        return ApkInspection(
            engine = engine,
            abis = abis,
            nativeLibraries = entries.count { it.startsWith("lib/") && it.endsWith(".so") },
            hasUnityAssets = entries.any { it.startsWith("assets/bin/Data/") },
            hasUnrealAssets = entries.any { it.startsWith("assets/") && it.endsWith(".pak") }
        )
    }

    fun inspect(apk: File): ApkInspection = inspect(listOf(apk))
}
