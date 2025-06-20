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
import androidx.core.content.res.ResourcesCompat
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
    private val logfile = File(Settings.logPath(), "torrserver.log")
    private lateinit var logView: TextView

    // Color cache
    private var warningColor: Int = 0
    private var timestampColor: Int = 0
    private var hostColor: Int = 0
    private var accentColor: Int = 0
    private var startMarkerColor: Int = 0

    // Regex patterns (compiled once)
    private val startMarkerRegex = "=+ START =+".toRegex()
    private val timestampRegex = "\\d{4}/\\d{2}/\\d{2} \\d{2}:\\d{2}:\\d{2} UTC\\d+".toRegex()
    private val errorRegex = "(?i)(error|warn|fail|exception)".toRegex()
    private val ipRegex = "\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}".toRegex()
    private val configBlockRegex = "\\{.*?\\}".toRegex()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val vi = inflater.inflate(R.layout.log_fragment, container, false)
        val title = vi.findViewById<TextView>(R.id.tvTitle)
        val btnClear = vi.findViewById<Button>(R.id.btnClear)

        // Initialize color cache
        warningColor = ResourcesCompat.getColor(resources, R.color.design_default_color_error, null)
        startMarkerColor = ResourcesCompat.getColor(resources, R.color.orange_dark, null)
        timestampColor = ThemeUtil.getColorFromAttr(requireContext(), R.attr.colorOnSurface).withAlpha(150)
        hostColor = ThemeUtil.getColorFromAttr(requireContext(), R.attr.colorHost)
        accentColor = ThemeUtil.getColorFromAttr(requireContext(), R.attr.colorAccent)

        title.text = logfile.path
        logView = vi.findViewById(R.id.tvLog)
        updateFileSizeInfo(btnClear)

        btnClear?.setOnClickListener {
            clearLog(logfile)
            btnClear.text = getString(R.string.delete)
        }

        return vi
    }

    private fun updateFileSizeInfo(btnClear: Button?) {
        val (sizeText, exactBytes) = getFileSize(logfile)
        if (exactBytes > 0L) {
            btnClear?.text = "${getString(R.string.delete)} $sizeText"
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        if (logfile.canRead()) {
            loadLogFile()
        } else {
            App.toast("${getString(R.string.error_retrieve_data)} ${logfile.path}")
        }
    }

    private fun loadLogFile() {
        lifecycleScope.launch {
            try {
                val logContent = withContext(Dispatchers.IO) {
                    readLogFileWithChunks(logfile)
                }
                withContext(Dispatchers.Main) {
                    logView.text = highlightLog(logContent)
                }
            } catch (e: Exception) {
                logView.text = e.localizedMessage
            }
        }
    }

    private fun readLogFileWithChunks(file: File, chunkSize: Int = 8192): String {
        val stringBuilder = StringBuilder()
        BufferedReader(FileReader(file), chunkSize).use { reader ->
            val buffer = CharArray(chunkSize)
            var charsRead: Int
            while (reader.read(buffer).also { charsRead = it } != -1) {
                stringBuilder.append(buffer, 0, charsRead)
            }
        }
        return stringBuilder.toString()
    }

    private fun highlightLog(content: String): SpannableString {
        val spannable = SpannableString(content)
        // Process highlights in order of most specific to least specific
        highlightPattern(spannable, startMarkerRegex, startMarkerColor, bold = true)
        highlightPattern(spannable, errorRegex, warningColor)
        highlightPattern(spannable, ipRegex, hostColor)
        highlightPattern(spannable, configBlockRegex, accentColor)
        highlightPattern(spannable, timestampRegex, timestampColor)

        return spannable
    }

    private fun highlightPattern(
        spannable: SpannableString,
        pattern: Regex,
        color: Int,
        bold: Boolean = false
    ) {
        pattern.findAll(spannable).forEach { match ->
            spannable.setSpan(
                ForegroundColorSpan(color),
                match.range.first,
                match.range.last + 1,
                Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
            )
            if (bold) {
                spannable.setSpan(
                    StyleSpan(Typeface.BOLD),
                    match.range.first,
                    match.range.last + 1,
                    Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
                )
            }
        }
    }

    private fun Int.withAlpha(alpha: Int): Int {
        return (alpha shl 24) or (this and 0x00FFFFFF)
    }

    fun clearLog(file: File) {
        lifecycleScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    file.writeText("")
                }
                logView.text = getString(R.string.torrserver_log_cleared)
                updateFileSizeInfo(view?.findViewById(R.id.btnClear))
            } catch (e: Exception) {
                logView.text = e.localizedMessage
            }
        }
    }

    fun getFileSize(file: File): Pair<String, Long> {
        return try {
            if (!file.exists()) return "File not found" to -1
            val bytes = file.length()
            byteFmt(bytes) to bytes
        } catch (_: SecurityException) {
            "No read permission" to -1
        } catch (e: Exception) {
            "Error: ${e.message}" to -1
        }
    }
}