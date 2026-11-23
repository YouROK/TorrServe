package ru.yourok.torrserve.ui

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.widget.ProgressBar
import android.widget.TextView
import ru.yourok.torrserve.R

/**
 * Панель предзагрузки внутри окна (не отдельное окно-диалог).
 * Используется и в окне раздачи из другого приложения, и в главном окне.
 */
class PreloadPanel(
    context: Context,
    onCancel: () -> Unit,
    onOpenNow: () -> Unit,
) : Preloader.Listener {

    val view: View = LayoutInflater.from(context).inflate(R.layout.view_preload, null)

    private val title = view.findViewById<TextView>(R.id.prep_title)
    private val status = view.findViewById<TextView>(R.id.prep_status)
    private val progress = view.findViewById<ProgressBar>(R.id.prep_progress)
    private val percent = view.findViewById<TextView>(R.id.prep_percent)

    init {
        // «Открыть сейчас» и «Отмена» - внутри той же панели, без всплывающих окон
        view.findViewById<View>(R.id.prep_cancel).setOnClickListener { onCancel() }
        view.findViewById<View>(R.id.prep_open).setOnClickListener { onOpenNow() }
    }

    fun bind(fileName: String) {
        title.text = fileName
        status.setText(R.string.prep_preloading)
        progress.isIndeterminate = true
        percent.text = ""
    }

    override fun onStage(text: String) {
        status.text = text
        progress.isIndeterminate = true
        percent.text = ""
    }

    override fun onProgress(percentValue: Int, statusText: String, detail: String) {
        progress.isIndeterminate = false
        progress.progress = percentValue
        status.text = statusText
        percent.text = if (detail.isBlank()) "$percentValue%" else "$percentValue% · $detail"
    }

    /** Финальное состояние перед запуском плеера. */
    fun done() {
        progress.isIndeterminate = false
        progress.progress = 100
        percent.text = "100%"
        status.setText(R.string.prep_done)
    }
}
