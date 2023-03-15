package ru.yourok.torrserve.ui.dialogs

import android.content.Context
import android.graphics.Typeface
import android.text.Spannable
import android.text.SpannableString
import android.text.style.ForegroundColorSpan
import android.text.style.StyleSpan
import android.view.LayoutInflater
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import androidx.annotation.ColorInt
import androidx.appcompat.app.AlertDialog
import androidx.core.graphics.ColorUtils
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import ru.yourok.torrserve.R
import ru.yourok.torrserve.app.App
import ru.yourok.torrserve.server.models.torrent.TorrentDetails
import ru.yourok.torrserve.ui.activities.play.addTorrent
import ru.yourok.torrserve.utils.ThemeUtil.Companion.getColorFromAttr

class InfoDialog(private val context: Context) {
    private fun TextView.append(string: String?, @ColorInt color: Int = 0, bold: Boolean = false) {
        if (string.isNullOrEmpty()) {
            return
        }
        val spannable: Spannable = SpannableString(string)
        if (color != 0)
            spannable.setSpan(
                ForegroundColorSpan(color),
                0,
                spannable.length,
                Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
            )
        if (bold)
            spannable.setSpan(
                StyleSpan(Typeface.BOLD),
                0,
                spannable.length,
                Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
            )
        append(spannable)
    }

    fun show(td: TorrentDetails, title: String, format: String, video: String, audio: String, subtitles: String, size: String, runtime: String, bitrate: String) {
        val view = (context.getSystemService(Context.LAYOUT_INFLATER_SERVICE) as LayoutInflater)
            .inflate(R.layout.dialog_info, null) as LinearLayout? ?: return
        val color1 = 0
        val color2 = ColorUtils.setAlphaComponent(getColorFromAttr(this.context, R.attr.colorOnBackground), 220)
        view.findViewById<TextView>(R.id.format)?.apply {
            if (format.isNotBlank()) {
                text = ""
                append("${context.getString(R.string.format)}: ", color1)
                append(format, color2, true)
            } else
                visibility = View.GONE
        }
        view.findViewById<TextView>(R.id.video)?.apply {
            if (video.isNotBlank()) {
                text = ""
                append("${context.getString(R.string.video)}: ", color1)
                append(video, color2, true)
            } else
                visibility = View.GONE
        }
        view.findViewById<TextView>(R.id.audio)?.apply {
            if (audio.isNotBlank()) {
                text = ""
                append("${context.getString(R.string.audio)}: ", color1)
                append(audio, color2, true)
            } else
                visibility = View.GONE
        }
        view.findViewById<TextView>(R.id.subtitles)?.apply {
            if (subtitles.isNotBlank()) {
                text = ""
                append("${context.getString(R.string.subtitles)}: ", color1)
                append(subtitles, color2, true)
            } else
                visibility = View.GONE
        }
        view.findViewById<TextView>(R.id.infoline)?.apply {
            text = ""
            append("${context.getString(R.string.size)}: ", color1)
            append(size, color2, true)
            append(", ${context.getString(R.string.runtime)}: ", color1)
            append(runtime, color2, true)
            append(", ${context.getString(R.string.bit_rate)}: ", color1)
            append(bitrate, color2, true)
        }
        val builder = AlertDialog.Builder(context)
        if (title.isNotEmpty())
            builder.setTitle(title)

        val dialog = builder.setView(view)

        builder.setPositiveButton(R.string.add) { dlg, _ ->
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    val torrent = addTorrent("", td.Magnet, td.Title, "", "", true)
                    torrent?.let { App.toast("${context.getString(R.string.stat_string_added)}: ${it.title}") } ?: App.toast(context.getString(R.string.error_add_torrent))
                } catch (e: Exception) {
                    e.printStackTrace()
                    App.toast(e.message ?: context.getString(R.string.error_add_torrent))
                }
            }
            dlg.dismiss()
        }

        builder.setNegativeButton(android.R.string.cancel) { dlg, _ ->
            dlg.dismiss()
        }

//        builder.setNeutralButton(R.string.play) { dlg, _ ->
//            createPlayIntent()
//            dlg.dismiss()
//        }

        dialog.show()
    }
}