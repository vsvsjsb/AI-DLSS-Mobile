package com.aidlss.mobile

import java.io.ByteArrayOutputStream
import java.io.File
import java.util.zip.ZipFile

data class ApkInspection(
    val engine: String,
    val abis: List<String>,
    val nativeLibraries: Int,
    val nativeLibraryNames: List<String>,
    val nativeGraphicsHints: List<String>,
    val assetCount: Int,
    val assetSamples: List<String>,
    val hasUnityAssets: Boolean,
    val hasUnrealAssets: Boolean,
    val graphicsLibraries: List<String>
)

object ApkInspector {
    private val binaryMarkers = listOf(
        "vkCreateInstance", "vkGetInstanceProcAddr", "vkCreateDevice",
        "vkQueueSubmit", "vkCmdDraw", "libvulkan.so",
        "eglGetDisplay", "eglSwapBuffers", "glDrawElements",
        "glCreateShader", "libEGL.so", "libGLESv2.so", "libGLESv3.so"
    )

    fun inspect(apks: List<File>): ApkInspection {
        val allEntries = mutableListOf<String>()
        val nativeHints = linkedSetOf<String>()
        apks.filter { it.isFile }.forEach { apk ->
            try {
                ZipFile(apk).use { zip ->
                    val entries = zip.entries()
                    while (entries.hasMoreElements()) {
                        val entry = entries.nextElement()
                        allEntries.add(entry.name)
                        if (entry.name.startsWith("lib/") && entry.name.endsWith(".so")) {
                            try {
                                val bytes = zip.getInputStream(entry).use { input ->
                                    val output = ByteArrayOutputStream()
                                    val buffer = ByteArray(8192)
                                    var total = 0
                                    while (total < 32 * 1024 * 1024) {
                                        val count = input.read(buffer, 0, minOf(buffer.size, 32 * 1024 * 1024 - total))
                                        if (count <= 0) break
                                        output.write(buffer, 0, count)
                                        total += count
                                    }
                                    output.toByteArray()
                                }
                                val binaryText = String(bytes, Charsets.ISO_8859_1)
                                binaryMarkers.forEach { marker ->
                                    if (binaryText.contains(marker, ignoreCase = true)) {
                                        nativeHints.add(marker)
                                    }
                                }
                            } catch (_: Exception) { }
                        }
                    }
                }
            } catch (_: Exception) { }
        }

        val entries = allEntries.distinct()
        val lower = entries.map { it.lowercase() }
        val libraries = entries.filter { it.startsWith("lib/") && it.endsWith(".so") }.sorted()
        val engine = when {
            lower.any { it.endsWith("/libunity.so") } -> "Unity"
            lower.any { it.endsWith("/libue4.so") || it.endsWith("/libunreal.so") } -> "Unreal Engine"
            lower.any { it.contains("godot") && it.endsWith(".so") } -> "Godot"
            lower.any { it.contains("libgdx") && it.endsWith(".so") } -> "libGDX"
            lower.any { it.endsWith("/libtombraider.so") } -> "Foundation Engine (تأكيد المستخدم؛ التحقق الآلي قيد العمل)"
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
        val assetEntries = entries.filter { it.startsWith("assets/") && !it.endsWith("/") }
        val prioritizedAssets = assetEntries
            .filterNot { it.startsWith("assets/Achievements/icons/") }
            .sorted() + assetEntries.filter { it.startsWith("assets/Achievements/icons/") }.sorted()
        return ApkInspection(
            engine = engine,
            abis = abis,
            nativeLibraries = libraries.size,
            nativeLibraryNames = libraries.map { it.substringAfterLast('/') },
            nativeGraphicsHints = nativeHints.toList(),
            assetCount = assetEntries.size,
            assetSamples = prioritizedAssets.take(50),
            hasUnityAssets = entries.any { it.startsWith("assets/bin/Data/") },
            hasUnrealAssets = entries.any { it.startsWith("assets/") && it.endsWith(".pak") },
            graphicsLibraries = graphics.distinct()
        )
    }

    fun inspect(apk: File): ApkInspection = inspect(listOf(apk))
}
