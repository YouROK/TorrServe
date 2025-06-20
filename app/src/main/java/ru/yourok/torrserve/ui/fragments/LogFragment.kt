package ru.yourok.torrserve.ui.fragments

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import ru.yourok.torrserve.R
import ru.yourok.torrserve.app.App
import ru.yourok.torrserve.settings.Settings
import ru.yourok.torrserve.utils.Format.byteFmt
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
                        logView.text = stringBuilder.toString()
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

}