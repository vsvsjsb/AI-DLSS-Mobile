package com.fileexplorer.pro

import android.app.AlertDialog
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.OpenableColumns
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import androidx.documentfile.provider.DocumentFile
import java.io.File
import java.security.MessageDigest
import java.util.Locale
import java.util.zip.ZipFile

class MainActivity : AppCompatActivity() {
    private lateinit var list: LinearLayout
    private lateinit var report: TextView
    private var currentFolder: DocumentFile? = null
    private val staticViews = 8

    private val folderPicker = registerForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        uri?.let {
            contentResolver.takePersistableUriPermission(it, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
            currentFolder = DocumentFile.fromTreeUri(this, it)
            showFolder(currentFolder)
        }
    }
    private val filePicker = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let { inspectUri(it) }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        buildUi()
    }

    private fun buildUi() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(20), dp(16), dp(16))
            setBackgroundColor(0xFF101820.toInt())
        }
        val scroll = ScrollView(this)
        list = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        scroll.addView(list)
        root.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))
        addText(getString(R.string.app_name), 25f, true)
        addText(getString(R.string.home_subtitle), 13f, false)
        addButton(getString(R.string.browse_folder)) { folderPicker.launch(null) }
        addButton(getString(R.string.open_file)) { filePicker.launch(arrayOf("*/*")) }
        addButton(getString(R.string.installed_apps)) { showInstalledApps() }
        addButton(getString(R.string.settings)) { showSettings() }
        addText(getString(R.string.folder_contents), 18f, true)
        report = addText(getString(R.string.choose_folder), 13f, false)
        setContentView(root)
    }

    private fun showFolder(folder: DocumentFile?) {
        if (list.childCount > staticViews) list.removeViews(staticViews, list.childCount - staticViews)
        if (folder == null || !folder.isDirectory) {
            report.text = getString(R.string.cannot_open)
            return
        }
        report.text = getString(R.string.folder_path, folder.name ?: "/")
        val items = folder.listFiles().sortedWith(compareBy<DocumentFile>({ !it.isDirectory }, { it.name?.lowercase(Locale.ROOT) ?: "" }))
        if (items.isEmpty()) addText(getString(R.string.empty_folder), 13f, false)
        items.forEach { item ->
            val label = (if (item.isDirectory) "📁 " else iconFor(item.name) + " ") + (item.name ?: getString(R.string.unknown))
            addButton(label) {
                if (item.isDirectory) {
                    currentFolder = item
                    showFolder(item)
                } else inspectDocument(item)
            }
        }
        addButton(getString(R.string.go_parent)) {
            val parent = folder.parentFile
            if (parent != null) {
                currentFolder = parent
                showFolder(parent)
            } else folderPicker.launch(null)
        }
    }

    private fun inspectDocument(doc: DocumentFile) {
        report.text = getString(R.string.reading_file)
        Thread {
            try {
                val bytes = contentResolver.openInputStream(doc.uri)?.use { it.readBytesLimited(512 * 1024) }
                    ?: throw IllegalStateException(getString(R.string.cannot_open))
                val text = describeFile(doc.name ?: "file", doc.type, doc.length(), bytes)
                runOnUiThread { report.text = text }
            } catch (e: Exception) {
                runOnUiThread { report.text = getString(R.string.read_error, e.localizedMessage ?: "") }
            }
        }.start()
    }

    private fun inspectUri(uri: Uri) {
        report.text = getString(R.string.reading_file)
        Thread {
            try {
                val name = queryName(uri)
                val size = contentResolver.query(uri, null, null, null, null)?.use { c ->
                    val i = c.getColumnIndex(OpenableColumns.SIZE)
                    if (c.moveToFirst() && i >= 0 && !c.isNull(i)) c.getLong(i) else -1L
                } ?: -1L
                val bytes = contentResolver.openInputStream(uri)?.use { it.readBytesLimited(512 * 1024) }
                    ?: throw IllegalStateException(getString(R.string.cannot_open))
                val result = if (name.endsWith(".apk", true)) analyzeApkBytes(name, bytes, size)
                    else describeFile(name, contentResolver.getType(uri), size, bytes)
                runOnUiThread { report.text = result }
            } catch (e: Exception) {
                runOnUiThread { report.text = getString(R.string.read_error, e.localizedMessage ?: "") }
            }
        }.start()
    }

    private fun showInstalledApps() {
        try {
            val apps = packageManager.getInstalledApplications(0)
                .filter { (it.flags and ApplicationInfo.FLAG_SYSTEM) == 0 }
                .sortedBy { packageManager.getApplicationLabel(it).toString().lowercase(Locale.ROOT) }
            val labels = apps.map { packageManager.getApplicationLabel(it).toString() }.toTypedArray()
            AlertDialog.Builder(this).setTitle(R.string.installed_apps)
                .setItems(labels) { _, which -> inspectInstalledApp(apps[which]) }
                .setNegativeButton(R.string.cancel, null).show()
        } catch (e: Exception) {
            report.text = getString(R.string.read_error, e.localizedMessage ?: "")
        }
    }

    @Suppress("DEPRECATION")
    private fun inspectInstalledApp(app: ApplicationInfo) {
        Thread {
            try {
                val info = if (Build.VERSION.SDK_INT >= 33)
                    packageManager.getPackageInfo(app.packageName, PackageManager.PackageInfoFlags.of(PackageManager.GET_PERMISSIONS.toLong() or PackageManager.GET_ACTIVITIES.toLong()))
                else packageManager.getPackageInfo(app.packageName, PackageManager.GET_PERMISSIONS or PackageManager.GET_ACTIVITIES)
                val apk = File(app.sourceDir)
                val result = buildString {
                    appendLine(getString(R.string.app_label, packageManager.getApplicationLabel(app)))
                    appendLine(getString(R.string.package_name, app.packageName))
                    appendLine(getString(R.string.file_size, formatSize(apk.length())))
                    appendLine(getString(R.string.version, info.versionName ?: getString(R.string.unknown)))
                    appendLine(getString(R.string.activities, info.activities?.size ?: 0))
                    appendLine(getString(R.string.permissions, info.requestedPermissions?.size ?: 0))
                    appendLine()
                    appendLine(getString(R.string.apk_contents))
                    appendLine(zipSummary(apk))
                    appendLine()
                    appendLine(getString(R.string.file_purpose_apk))
                }
                runOnUiThread { report.text = result }
            } catch (e: Exception) {
                runOnUiThread { report.text = getString(R.string.read_error, e.localizedMessage ?: "") }
            }
        }.start()
    }

    @Suppress("DEPRECATION")
    private fun analyzeApkBytes(name: String, bytes: ByteArray, size: Long): String {
        val temp = File(cacheDir, "inspect-" + System.currentTimeMillis() + ".apk")
        return try {
            temp.writeBytes(bytes)
            val info = if (Build.VERSION.SDK_INT >= 33)
                packageManager.getPackageArchiveInfo(temp.absolutePath, PackageManager.PackageInfoFlags.of(PackageManager.GET_PERMISSIONS.toLong() or PackageManager.GET_ACTIVITIES.toLong()))
            else packageManager.getPackageArchiveInfo(temp.absolutePath, PackageManager.GET_PERMISSIONS or PackageManager.GET_ACTIVITIES)
            buildString {
                appendLine(getString(R.string.file_name, name))
                appendLine(getString(R.string.file_size, if (size >= 0) formatSize(size) else getString(R.string.unknown)))
                if (info != null) {
                    appendLine(getString(R.string.package_name, info.packageName))
                    appendLine(getString(R.string.version, info.versionName ?: getString(R.string.unknown)))
                    appendLine(getString(R.string.activities, info.activities?.size ?: 0))
                    appendLine(getString(R.string.permissions, info.requestedPermissions?.size ?: 0))
                } else appendLine(getString(R.string.apk_parse_failed))
                appendLine()
                appendLine(getString(R.string.apk_contents))
                appendLine(zipSummary(temp))
                appendLine()
                appendLine(getString(R.string.file_purpose_apk))
                appendLine(getString(R.string.sha256, sha256(bytes)))
            }
        } finally { temp.delete() }
    }

    private fun zipSummary(file: File): String = try {
        ZipFile(file).use { zip ->
            val entries = zip.entries().asSequence().toList()
            val interesting = entries.filter {
                val n = it.name.lowercase(Locale.ROOT)
                n.endsWith(".so") || n.endsWith(".dex") || n.endsWith(".arsc") ||
                    n.contains("assets/") || n.contains("res/") || n.contains("lib/")
            }.take(35).joinToString("\n") { it.name }
            getString(R.string.zip_entry_summary, entries.size, interesting.ifBlank { getString(R.string.no_known_entries) })
        }
    } catch (_: Exception) { getString(R.string.zip_unreadable) }

    private fun describeFile(name: String, mime: String?, size: Long, bytes: ByteArray): String {
        val binary = bytes.any { it == 0.toByte() }
        val preview = if (binary) getString(R.string.binary_file)
            else bytes.toString(Charsets.UTF_8).take(10000).ifBlank { getString(R.string.empty_file) }
        return buildString {
            appendLine(getString(R.string.file_name, name))
            appendLine(getString(R.string.file_size, if (size >= 0) formatSize(size) else getString(R.string.unknown)))
            appendLine(getString(R.string.mime_type, mime ?: getString(R.string.unknown)))
            appendLine(getString(R.string.file_type, if (binary) getString(R.string.binary) else getString(R.string.text_file)))
            appendLine(getString(R.string.sha256, sha256(bytes)))
            appendLine()
            appendLine(getString(R.string.file_purpose))
            appendLine(purposeFor(name))
            appendLine()
            appendLine(getString(R.string.content_label))
            append(preview)
            if (size > bytes.size) appendLine("\n\n" + getString(R.string.preview_limited))
        }
    }

    private fun purposeFor(name: String): String {
        val n = name.lowercase(Locale.ROOT)
        return when {
            n.endsWith(".ini") || n.endsWith(".cfg") || n.endsWith(".conf") -> getString(R.string.purpose_config)
            n.endsWith(".xml") || n.endsWith(".json") -> getString(R.string.purpose_data)
            n.endsWith(".kt") || n.endsWith(".java") || n.endsWith(".cpp") || n.endsWith(".c") || n.endsWith(".h") -> getString(R.string.purpose_source)
            n.endsWith(".so") -> getString(R.string.purpose_library)
            n.endsWith(".pak") || n.endsWith(".obb") || n.endsWith(".dat") -> getString(R.string.purpose_game_data)
            n.endsWith(".png") || n.endsWith(".jpg") || n.endsWith(".webp") || n.endsWith(".dds") -> getString(R.string.purpose_texture)
            n.endsWith(".mp3") || n.endsWith(".ogg") || n.endsWith(".wav") -> getString(R.string.purpose_audio)
            n.endsWith(".mp4") || n.endsWith(".webm") -> getString(R.string.purpose_video)
            n.endsWith(".apk") -> getString(R.string.file_purpose_apk)
            else -> getString(R.string.purpose_unknown)
        }
    }

    private fun iconFor(name: String?): String = when (name?.substringAfterLast('.', "")?.lowercase(Locale.ROOT)) {
        "apk" -> "📦"; "png", "jpg", "jpeg", "webp", "dds" -> "🖼️"
        "mp3", "ogg", "wav" -> "🎵"; "mp4", "webm" -> "🎬"
        "ini", "cfg", "xml", "json", "txt", "kt", "java", "cpp", "c", "h" -> "📄"
        else -> "📦"
    }

    private fun showSettings() {
        val choices = arrayOf(getString(R.string.language_system), getString(R.string.language_arabic), getString(R.string.language_english))
        val current = AppCompatDelegate.getApplicationLocales().toLanguageTags()
        val selected = when { current.startsWith("ar") -> 1; current.startsWith("en") -> 2; else -> 0 }
        AlertDialog.Builder(this).setTitle(R.string.language_title)
            .setSingleChoiceItems(choices, selected) { dialog, which ->
                val tag = when (which) { 1 -> "ar"; 2 -> "en"; else -> "" }
                AppCompatDelegate.setApplicationLocales(if (tag.isEmpty()) LocaleListCompat.getEmptyLocaleList() else LocaleListCompat.forLanguageTags(tag))
                dialog.dismiss()
            }.setNegativeButton(R.string.cancel, null).show()
    }

    private fun queryName(uri: Uri): String {
        contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
            val i = c.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (c.moveToFirst() && i >= 0) return c.getString(i) ?: uri.lastPathSegment.orEmpty()
        }
        return uri.lastPathSegment ?: getString(R.string.unknown)
    }

    private fun addText(value: String, size: Float, bold: Boolean): TextView {
        val v = TextView(this).apply {
            text = value; textSize = size; setTextColor(0xFFF1F5F9.toInt())
            if (bold) setTypeface(typeface, android.graphics.Typeface.BOLD)
            setPadding(dp(4), dp(9), dp(4), dp(9))
            textDirection = View.TEXT_DIRECTION_FIRST_STRONG
        }
        list.addView(v)
        return v
    }

    private fun addButton(title: String, action: () -> Unit) {
        val b = Button(this).apply {
            text = title; isAllCaps = false; setOnClickListener { action() }
            layoutParams = LinearLayout.LayoutParams(-1, ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = dp(3); bottomMargin = dp(3) }
        }
        list.addView(b)
    }

    private fun sha256(bytes: ByteArray) = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
    private fun formatSize(bytes: Long): String = when {
        bytes < 1024 -> "$bytes B"
        bytes < 1024 * 1024 -> "%.1f KB".format(bytes / 1024.0)
        else -> "%.2f MB".format(bytes / (1024.0 * 1024.0))
    }
    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()
    private fun java.io.InputStream.readBytesLimited(limit: Int): ByteArray {
        val out = java.io.ByteArrayOutputStream(); val buffer = ByteArray(8192); var left = limit
        while (left > 0) { val n = read(buffer, 0, minOf(buffer.size, left)); if (n <= 0) break; out.write(buffer, 0, n); left -= n }
        return out.toByteArray()
    }
}