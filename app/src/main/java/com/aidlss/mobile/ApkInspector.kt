package com.aidlss.mobile

import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.zip.ZipFile

data class NativeLibraryAnalysis(
    val name: String,
    val sizeBytes: Long,
    val vulkanMarkers: List<String>,
    val openGlMarkers: List<String>,
    val dependencies: List<String>,
    val elf64: Boolean
)

data class ApkInspection(
    val engine: String,
    val abis: List<String>,
    val nativeLibraries: Int,
    val nativeLibraryNames: List<String>,
    val nativeGraphicsHints: List<String>,
    val libraryAnalyses: List<NativeLibraryAnalysis>,
    val assetCount: Int,
    val assetSamples: List<String>,
    val hasUnityAssets: Boolean,
    val hasUnrealAssets: Boolean,
    val graphicsLibraries: List<String>
)

object ApkInspector {
    private val vulkanMarkers = listOf(
        "vkCreateInstance", "vkGetInstanceProcAddr", "vkCreateDevice",
        "vkQueueSubmit", "vkCmdDraw", "libvulkan.so"
    )
    private val openGlMarkers = listOf(
        "eglGetDisplay", "eglSwapBuffers", "glDrawElements",
        "glCreateShader", "libEGL.so", "libGLESv2.so", "libGLESv3.so"
    )

    fun inspect(apks: List<File>): ApkInspection {
        val allEntries = mutableListOf<String>()
        val allHints = linkedSetOf<String>()
        val analyses = linkedMapOf<String, NativeLibraryAnalysis>()

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
                                    val limit = 64 * 1024 * 1024
                                    while (total < limit) {
                                        val count = input.read(buffer, 0, minOf(buffer.size, limit - total))
                                        if (count <= 0) break
                                        output.write(buffer, 0, count)
                                        total += count
                                    }
                                    output.toByteArray()
                                }
                                val binaryText = String(bytes, Charsets.ISO_8859_1)
                                val vk = vulkanMarkers.filter { binaryText.contains(it, ignoreCase = true) }
                                val gl = openGlMarkers.filter { binaryText.contains(it, ignoreCase = true) }
                                val deps = readElfNeeded(bytes)
                                vk.forEach { allHints.add(it) }
                                gl.forEach { allHints.add(it) }
                                val key = entry.name.substringAfterLast('/')
                                val old = analyses[key]
                                val current = NativeLibraryAnalysis(
                                    name = key,
                                    sizeBytes = entry.size,
                                    vulkanMarkers = vk,
                                    openGlMarkers = gl,
                                    dependencies = deps,
                                    elf64 = isElf64(bytes)
                                )
                                if (old == null || current.vulkanMarkers.size + current.openGlMarkers.size + current.dependencies.size >
                                    old.vulkanMarkers.size + old.openGlMarkers.size + old.dependencies.size) {
                                    analyses[key] = current
                                }
                            } catch (_: Exception) { }
                        }
                    }
                }
            } catch (_: Exception) { }
        }

        val entries = allEntries.distinct()
        val lower = entries.map { it.lowercase() }
        val libraries = entries.filter { it.startsWith("lib/") && it.endsWith(".so") }.distinct().sorted()
        val engine = when {
            lower.any { it.endsWith("/libunity.so") } -> "Unity"
            lower.any { it.endsWith("/libue4.so") || it.endsWith("/libunreal.so") } -> "Unreal Engine"
            lower.any { it.contains("godot") && it.endsWith(".so") } -> "Godot"
            lower.any { it.contains("libgdx") && it.endsWith(".so") } -> "libGDX"
            lower.any { it.endsWith("/libtombraider.so") } -> "Foundation Engine (تأكيد المستخدم؛ غير مثبت آلياً)"
            lower.any { it.contains("feral") } -> "مؤشرات Feral Interactive (محرك غير مصنف)"
            else -> "غير معروف من APK"
        }
        val abis = libraries.mapNotNull { it.split("/").getOrNull(1) }.distinct().sorted()
        val graphics = buildList {
            if (lower.any { it.contains("vulkan") || it.contains("vk_") }) add("Vulkan (مؤشرات أسماء)")
            if (lower.any { it.contains("gles") || it.contains("opengles") || it.endsWith("/libegl.so") }) add("OpenGL ES (مؤشرات أسماء)")
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
            nativeGraphicsHints = allHints.toList(),
            libraryAnalyses = analyses.values.sortedBy { it.name.lowercase() },
            assetCount = assetEntries.size,
            assetSamples = prioritizedAssets.take(50),
            hasUnityAssets = entries.any { it.startsWith("assets/bin/Data/") },
            hasUnrealAssets = entries.any { it.startsWith("assets/") && it.endsWith(".pak") },
            graphicsLibraries = graphics.distinct()
        )
    }

    private fun isElf64(bytes: ByteArray): Boolean =
        bytes.size > 5 && bytes[0] == 0x7f.toByte() &&
            bytes[1] == 'E'.code.toByte() && bytes[2] == 'L'.code.toByte() &&
            bytes[3] == 'F'.code.toByte() && bytes[4] == 2.toByte()

    // Reads DT_NEEDED entries from 64-bit little-endian ELF shared objects (Android arm64).
    private fun readElfNeeded(bytes: ByteArray): List<String> {
        try {
            if (!isElf64(bytes) || bytes.size < 64 || bytes[5] != 1.toByte()) return emptyList()
            val b = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
            val phoff = b.getLong(32).toInt()
            val phentsize = b.getShort(54).toInt() and 0xffff
            val phnum = b.getShort(56).toInt() and 0xffff
            if (phoff < 0 || phentsize < 56 || phnum > 4096 || phoff.toLong() + phentsize.toLong() * phnum > bytes.size) return emptyList()

            data class Segment(val type: Int, val offset: Long, val vaddr: Long, val filesz: Long)
            val segments = mutableListOf<Segment>()
            var dynamicOffset = -1L
            var dynamicSize = 0L
            for (i in 0 until phnum) {
                val p = phoff + i * phentsize
                val type = b.getInt(p)
                val offset = b.getLong(p + 8)
                val vaddr = b.getLong(p + 16)
                val filesz = b.getLong(p + 32)
                if (offset >= 0 && filesz >= 0 && offset + filesz <= bytes.size) {
                    segments.add(Segment(type, offset, vaddr, filesz))
                    if (type == 2) {
                        dynamicOffset = offset
                        dynamicSize = filesz
                    }
                }
            }
            if (dynamicOffset < 0 || dynamicSize < 16) return emptyList()

            val neededOffsets = mutableListOf<Long>()
            var strtabAddress = -1L
            var strtabSize = 0L
            val count = minOf((dynamicSize / 16).toInt(), 100000)
            for (i in 0 until count) {
                val p = dynamicOffset.toInt() + i * 16
                val tag = b.getLong(p)
                val value = b.getLong(p + 8)
                if (tag == 0L) break
                when (tag) {
                    1L -> neededOffsets.add(value)
                    5L -> strtabAddress = value
                    10L -> strtabSize = value
                }
            }
            if (strtabAddress < 0) return emptyList()
            val load = segments.firstOrNull {
                it.type == 1 && strtabAddress >= it.vaddr && strtabAddress - it.vaddr < it.filesz
            } ?: return emptyList()
            val strOffset = load.offset + (strtabAddress - load.vaddr)
            if (strOffset < 0 || strOffset >= bytes.size) return emptyList()
            val maxStringBytes = if (strtabSize > 0) minOf(strtabSize, bytes.size - strOffset).toInt()
                else (bytes.size - strOffset).toInt()
            val result = linkedSetOf<String>()
            neededOffsets.forEach { nameOffset ->
                if (nameOffset >= 0 && nameOffset < maxStringBytes) {
                    val start = strOffset.toInt() + nameOffset.toInt()
                    var end = start
                    while (end < strOffset.toInt() + maxStringBytes && bytes[end] != 0.toByte()) end++
                    if (end > start) result.add(String(bytes, start, end - start, Charsets.UTF_8))
                }
            }
            return result.toList()
        } catch (_: Exception) {
            return emptyList()
        }
    }

    fun inspect(apk: File): ApkInspection = inspect(listOf(apk))
}
