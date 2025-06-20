package ru.yourok.torrserve.ui.fragments

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import ru.yourok.torrserve.R
import ru.yourok.torrserve.app.App
import ru.yourok.torrserve.settings.Settings
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

        title.text = logfile.path
        logView = vi.findViewById<TextView?>(R.id.tvLog)
        return vi
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        if (logfile.canRead())
            displayLargeFileContents(logfile)
        else
            App.toast("Error reading file: ${logfile.path}")
    }

    fun displayLargeFileContents(file: File) {
        try {
            val stringBuilder = StringBuilder()
            BufferedReader(FileReader(file)).use { reader ->
                var line: String?
                while (reader.readLine().also { line = it } != null) {
                    stringBuilder.append(line).append("\n")
                }
            }
            logView.text = stringBuilder.toString()
        } catch (e: Exception) {
            logView.text = "Error reading file: ${e.message}"
        }
    }

}