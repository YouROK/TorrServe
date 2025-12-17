package ru.yourok.torrserve.ui.fragments

import android.graphics.Typeface
import android.os.Bundle
import android.text.Spannable
import android.text.SpannableString
import android.text.SpannableStringBuilder
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
import kotlinx.coroutines.NonCancellable
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
import java.io.RandomAccessFile
import java.util.regex.Pattern

class LogFragment : TSFragment() {
    // Chunk size for reading file
    private companion object {
        const val DEFAULT_BUFFER_SIZE = 8192
        const val MAX_LOG_SIZE = 512 * 1024 // 512 KB limit
    }

    // File handling
    private val logfile = File(Settings.logPath(), "torrserver.log")
    private lateinit var logView: TextView

    // Color cache
    private val warningColor by lazy { ResourcesCompat.getColor(resources, R.color.design_default_color_error, null) }
    private val startMarkerColor by lazy { ResourcesCompat.getColor(resources, R.color.orange_dark, null) }
    private val timestampColor by lazy { ThemeUtil.getColorFromAttr(requireContext(), R.attr.colorOnSurface).withAlpha(150) }
    private val hostColor by lazy { ThemeUtil.getColorFromAttr(requireContext(), R.attr.colorHost) }
    private val accentColor by lazy { ThemeUtil.getColorFromAttr(requireContext(), R.attr.colorAccent) }

    // Precompiled patterns (more efficient than Regex)
    private val startMarkerPattern by lazy { Pattern.compile("=+ START =+") }
    private val timestampPattern by lazy { Pattern.compile("\\d{4}/\\d{2}/\\d{2} \\d{2}:\\d{2}:\\d{2} UTC\\d+") }
    private val errorPattern by lazy { Pattern.compile("(?i)(error|warn|fail|exception)") }
    private val ipPattern by lazy { Pattern.compile("\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}") }
    private val simpleIpv6Pattern by lazy { Pattern.compile("[0-9a-fA-F]{1,4}(?::[0-9a-fA-F]{1,4}){3,7}") }
    private val configBlockPattern by lazy { Pattern.compile("\\{.*?\\}") }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val vi = inflater.inflate(R.layout.log_fragment, container, false)
        val title = vi.findViewById<TextView>(R.id.tvTitle)
        val btnClear = vi.findViewById<Button>(R.id.btnClear)

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
                showProgress()
                val (content, wasTrimmed) = withContext(Dispatchers.IO) {
                    val content = readLogFileWithChunks(logfile)
                    val trimmed = logfile.length() > MAX_LOG_SIZE
                    content to trimmed
                }

                // Process in chunks to avoid blocking UI thread
                val highlightedContent = withContext(Dispatchers.Default) {
                    if (wasTrimmed) {
                        val trimmedMessage = SpannableString("⚠️ " + getString(R.string.torrserver_log_trimmed, byteFmt(MAX_LOG_SIZE)) + "\n\n")
                        // Style the trimmed message
                        trimmedMessage.setSpan(
                            ForegroundColorSpan(warningColor),
                            0,
                            trimmedMessage.length,
                            Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
                        )
                        trimmedMessage.setSpan(
                            StyleSpan(Typeface.BOLD),
                            0,
                            trimmedMessage.length,
                            Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
                        )

                        val logSpannable = highlightLog(content)
                        // Combine both messages
                        val combined = SpannableStringBuilder()
                            .append(trimmedMessage)
                            .append(logSpannable)
                        combined
                    } else {
                        highlightLog(content)
                    }
                }

                logView.text = highlightedContent
            } catch (e: Exception) {
                logView.text = e.localizedMessage ?: "Error loading log"
            } finally {
                withContext(NonCancellable) { // Ensure this runs even if coroutine is cancelled
                    hideProgress()
                }
            }
        }
    }

    private fun readLogFileWithChunks(file: File, chunkSize: Int = DEFAULT_BUFFER_SIZE): String {
        return when {
            !file.exists() -> ""
            file.length() > MAX_LOG_SIZE -> {
                // For large files, read only the end portion
                RandomAccessFile(file, "r").use { raf ->
                    val startPos = file.length() - MAX_LOG_SIZE
                    var pos = maxOf(0, startPos)
                    // Find the next newline character
                    if (startPos > 0) {
                        raf.seek(pos)
                        while (pos < file.length()) {
                            if (raf.readByte().toInt().toChar() == '\n') {
                                pos = raf.filePointer
                                break
                            }
                            pos++
                        }
                    }
                    // Read from the found position
                    raf.seek(pos)
                    val buffer = ByteArray(minOf(MAX_LOG_SIZE, (file.length() - pos).toInt()))
                    raf.readFully(buffer)
                    String(buffer, Charsets.UTF_8)
                }
            }

            else -> {
                // Normal file reading
                BufferedReader(FileReader(file), chunkSize).use { it.readText() }
            }
        }
    }

    private fun highlightLog(content: String): SpannableString {
        val spannable = SpannableString(content)

        // Process highlights in order of most specific to least specific
        highlightPattern(spannable, startMarkerPattern, startMarkerColor, bold = true)
        highlightPattern(spannable, errorPattern, warningColor)
        highlightPattern(spannable, ipPattern, hostColor) // IPv4
        highlightPattern(spannable, simpleIpv6Pattern, hostColor) // IPv6
        highlightPattern(spannable, configBlockPattern, accentColor)
        highlightPattern(spannable, timestampPattern, timestampColor)

        return spannable
    }

    private fun highlightPattern(
        spannable: SpannableString,
        pattern: Pattern,
        color: Int,
        bold: Boolean = false
    ) {
        val matcher = pattern.matcher(spannable)
        while (matcher.find()) {
            val start = matcher.start()
            val end = matcher.end()

            spannable.setSpan(
                ForegroundColorSpan(color),
                start,
                end,
                Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
            )
            if (bold) {
                spannable.setSpan(
                    StyleSpan(Typeface.BOLD),
                    start,
                    end,
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