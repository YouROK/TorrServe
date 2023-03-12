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
import ru.yourok.torrserve.R
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

    fun show(title: String, format: String, video: String, audio: String, subtitles: String, size: String, runtime: String, bitrate: String) {
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

        val dialog = builder.setView(view).create()

        dialog.show()
    }
}