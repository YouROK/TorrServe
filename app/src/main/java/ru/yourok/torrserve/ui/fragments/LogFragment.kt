package ru.yourok.torrserve.ui.fragments

import android.graphics.Typeface
import android.os.Bundle
import android.text.Spannable
import android.text.SpannableString
import android.text.style.ForegroundColorSpan
import android.text.style.StyleSpan
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import androidx.core.graphics.toColorInt
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import ru.yourok.torrserve.R
import ru.yourok.torrserve.app.App
import ru.yourok.torrserve.settings.Settings
import ru.yourok.torrserve.utils.Format.byteFmt
import ru.yourok.torrserve.utils.ThemeUtil
import java.io.BufferedReader
import java.io.File
import java.io.FileReader

class LogFragment : TSFragment() {
    val logfile = File(Settings.logPath(), "torrserver.log")
    private lateinit var logView: TextView
    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val vi = inflater.inflate(R.layout.log_fragment, container, false)
        val title = vi.findViewById<TextView?>(R.id.tvTitle)
        val btnClear = vi.findViewById<Button?>(R.id.btnClear)

        title.text = logfile.path
        logView = vi.findViewById<TextView?>(R.id.tvLog)
        val (sizeText, exactBytes) = getFileSize(logfile)
        btnClear?.apply {
            if (exactBytes > 0L) {
                text = "${getString(R.string.delete)} $sizeText"
            }
            setOnClickListener {
                clearLog(logfile)
                text = getString(R.string.delete)
            }
        }
        return vi
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        if (logfile.canRead())
            displayLargeFileContents(logfile)
        else
            App.toast("${getString(R.string.error_retrieve_data)} ${logfile.path}")
    }

    fun displayLargeFileContents(file: File) {
        lifecycleScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    val stringBuilder = StringBuilder()
                    BufferedReader(FileReader(file)).use { reader ->
                        var line: String?
                        while (reader.readLine().also { line = it } != null) {
                            stringBuilder.append(line).append("\n")
                        }
                    }
                    withContext(Dispatchers.Main) {
                        logView.text = stringBuilder.toString().highlightLog()
                    }
                }
            } catch (e: Exception) {
                logView.text = e.localizedMessage
            }
        }
    }

    fun clearLog(file: File) {
        lifecycleScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    file.writeText("")
                }
                logView.text = getString(R.string.torrserver_log_cleared)
            } catch (e: Exception) {
                logView.text = e.localizedMessage
            }
        }
    }

    fun getFileSize(file: File): Pair<String, Long> {
        return try {
            if (!file.exists()) return "File not found" to -1

            val bytes = file.length()
            val formatted = byteFmt(bytes)

            formatted to bytes
        } catch (_: SecurityException) {
            "No read permission" to -1
        } catch (e: Exception) {
            "Error: ${e.message}" to -1
        }
    }

    // Add this extension function to your LogFragment
    private fun String.highlightLog(): SpannableString {
        val spannable = SpannableString(this)

        // Highlight START/END markers
        "=+ START =+".toRegex().findAll(this).forEach { match ->
            spannable.setSpan(
                ForegroundColorSpan("#FF5722".toColorInt()), // Orange
                match.range.first,
                match.range.last + 1,
                Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
            )
            spannable.setSpan(
                StyleSpan(Typeface.BOLD),
                match.range.first,
                match.range.last + 1,
                Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
            )
        }

        // Highlight timestamps (UTC0)
        val baseColor = ThemeUtil.getColorFromAttr(requireContext(), R.attr.colorOnSurface)
        val alpha = 128 // 0-255 (50% in this case)
        val colorWithAlpha = (alpha shl 24) or (baseColor and 0x00FFFFFF)
        "\\d{4}/\\d{2}/\\d{2} \\d{2}:\\d{2}:\\d{2} UTC\\d+".toRegex().findAll(this).forEach { match ->
            spannable.setSpan(
                ForegroundColorSpan(colorWithAlpha),
                match.range.first,
                match.range.last + 1,
                Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
            )
        }

        // Highlight error/warning messages
        "(?i)(error|warn|fail|exception)".toRegex().findAll(this).forEach { match ->
            spannable.setSpan(
                ForegroundColorSpan("#F44336".toColorInt()), // Red
                match.range.first,
                match.range.last + 1,
                Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
            )
        }

        // Highlight IP addresses
        "\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}".toRegex().findAll(this).forEach { match ->
            spannable.setSpan(
                ForegroundColorSpan(ThemeUtil.getColorFromAttr(requireContext(), R.attr.colorHost)),
                match.range.first,
                match.range.last + 1,
                Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
            )
        }

        // Highlight configuration blocks
        "\\{.*?\\}".toRegex().findAll(this).forEach { match ->
            spannable.setSpan(
                ForegroundColorSpan(ThemeUtil.getColorFromAttr(requireContext(), R.attr.colorAccent)),
                match.range.first,
                match.range.last + 1,
                Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
            )
        }

        return spannable
    }

}