package com.aidlss.mobile

import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import android.view.Gravity
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import java.security.MessageDigest

class MainActivity : AppCompatActivity() {
    private lateinit var content: LinearLayout
    private lateinit var resultText: TextView
    private val openFile = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let { inspectFile(it) }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        buildScreen()
    }

    private fun buildScreen() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(24), dp(20), dp(20))
        }
        val scroll = ScrollView(this)
        content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutDirection = ViewGroup.LAYOUT_DIRECTION_LOCALE
        }
        scroll.addView(content)
        root.addView(scroll)
        content.addView(label(getString(R.string.app_name), 26f, true))
        content.addView(label(getString(R.string.home_subtitle), 14f, false))
        content.addView(actionButton(getString(R.string.open_file)) {
            openFile.launch(arrayOf("*/*"))
        })
        content.addView(actionButton(getString(R.string.settings)) { showSettings() })
        content.addView(label(getString(R.string.preview_title), 18f, true))
        resultText = label(getString(R.string.no_file), 14f, false).apply {
            gravity = Gravity.START
            textDirection = TextView.TEXT_DIRECTION_FIRST_STRONG
            setPadding(0, dp(12), 0, dp(12))
        }
        content.addView(resultText)
        setContentView(root)
    }

    private fun showSettings() {
        val choices = arrayOf(getString(R.string.language_system), getString(R.string.language_arabic), getString(R.string.language_english))
        val current = AppCompatDelegate.getApplicationLocales().toLanguageTags()
        val selected = when {
            current.startsWith("ar") -> 1
            current.startsWith("en") -> 2
            else -> 0
        }
        AlertDialog.Builder(this)
            .setTitle(R.string.language_title)
            .setSingleChoiceItems(choices, selected) { dialog, which ->
                val locale = when (which) { 1 -> "ar"; 2 -> "en"; else -> "" }
                AppCompatDelegate.setApplicationLocales(
                    if (locale.isEmpty()) LocaleListCompat.getEmptyLocaleList()
                    else LocaleListCompat.forLanguageTags(locale)
                )
                dialog.dismiss()
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun inspectFile(uri: Uri) {
        resultText.text = getString(R.string.reading_file)
        Thread {
            try {
                val name = queryName(uri)
                val size = contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                    val index = cursor.getColumnIndex(OpenableColumns.SIZE)
                    if (cursor.moveToFirst() && index >= 0 && !cursor.isNull(index)) cursor.getLong(index) else -1L
                } ?: -1L
                val bytes = contentResolver.openInputStream(uri)?.use { it.readNBytes(512 * 1024) }
                    ?: throw IllegalStateException(getString(R.string.cannot_open))
                val sha = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
                val binary = bytes.any { it == 0.toByte() }
                val preview = if (binary) getString(R.string.binary_file)
                    else bytes.toString(Charsets.UTF_8).take(12000).ifBlank { getString(R.string.empty_file) }
                val report = buildString {
                    appendLine(getString(R.string.file_name, name))
                    appendLine(getString(R.string.file_size, if (size >= 0) formatSize(size) else getString(R.string.unknown)))
                    appendLine(getString(R.string.file_type, if (binary) getString(R.string.binary) else getString(R.string.text_file)))
                    appendLine(getString(R.string.sha256, sha))
                    appendLine()
                    appendLine(getString(R.string.content_label))
                    append(preview)
                    if (size > bytes.size) appendLine("\n\n" + getString(R.string.preview_limited))
                }
                runOnUiThread { resultText.text = report }
            } catch (e: Exception) {
                runOnUiThread { resultText.text = getString(R.string.read_error, e.localizedMessage ?: "") }
            }
        }.start()
    }

    private fun queryName(uri: Uri): String {
        contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
            val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (cursor.moveToFirst() && index >= 0) return cursor.getString(index) ?: uri.lastPathSegment.orEmpty()
        }
        return uri.lastPathSegment ?: getString(R.string.unknown)
    }

    private fun formatSize(bytes: Long): String = when {
        bytes < 1024 -> "$bytes B"
        bytes < 1024 * 1024 -> "%.1f KB".format(bytes / 1024.0)
        else -> "%.2f MB".format(bytes / (1024.0 * 1024.0))
    }

    private fun label(value: String, size: Float, bold: Boolean) = TextView(this).apply {
        text = value
        textSize = size
        if (bold) setTypeface(typeface, android.graphics.Typeface.BOLD)
        setPadding(0, dp(8), 0, dp(8))
    }

    private fun actionButton(title: String, action: () -> Unit) = Button(this).apply {
        text = title
        isAllCaps = false
        setOnClickListener { action() }
        layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            .apply { topMargin = dp(8); bottomMargin = dp(8) }
    }

    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()
}