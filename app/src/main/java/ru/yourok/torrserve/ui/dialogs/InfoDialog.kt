package ru.yourok.torrserve.ui.dialogs

import android.content.Context
import android.content.DialogInterface
import android.content.res.ColorStateList
import android.text.SpannableString
import android.text.Spanned
import android.text.style.DynamicDrawableSpan
import android.text.style.ImageSpan
import android.view.LayoutInflater
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.content.res.AppCompatResources
import com.google.android.material.shape.CornerFamily
import com.google.android.material.shape.MaterialShapeDrawable
import com.google.android.material.shape.ShapeAppearanceModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import ru.yourok.torrserve.R
import ru.yourok.torrserve.app.App
import ru.yourok.torrserve.ui.activities.play.addTorrent
import ru.yourok.torrserve.utils.Format.dp2px
import ru.yourok.torrserve.utils.SpanFormat
import ru.yourok.torrserve.utils.ThemeUtil.Companion.getColorFromAttr

class InfoDialog(private val context: Context) {
    // round labels model
    private val radius = dp2px(5.0f).toFloat()
    private val shapeAppearanceModel = ShapeAppearanceModel()
        .toBuilder()
        .setAllCorners(CornerFamily.ROUNDED, radius)
        .build()

    private val labelsColor = ColorStateList.valueOf(getColorFromAttr(this.context, R.attr.colorPrimary))
    private val labelsTextColor = getColorFromAttr(this.context, R.attr.colorSurface)

    fun show(torrLink: String, title: String, format: String, video: String, audio: String, subtitles: String, size: String, runtime: String, bitrate: String) {
        val view = (context.getSystemService(Context.LAYOUT_INFLATER_SERVICE) as LayoutInflater)
            .inflate(R.layout.dialog_info, null) as LinearLayout? ?: return

        view.findViewById<TextView>(R.id.format)?.apply {
            if (format.isNotBlank()) {
                val sIcon = SpannableString(" ")
                val cDrawable = AppCompatResources.getDrawable(context, R.drawable.outline_format_24)
                cDrawable?.let {
                    it.setBounds(0, 0, it.intrinsicWidth, it.intrinsicHeight)
                    val span = ImageSpan(it, DynamicDrawableSpan.ALIGN_CENTER)
                    sIcon.setSpan(span, 0, 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                }
                text = SpanFormat.format("%s $format", sIcon)
            } else
                visibility = View.GONE
        }
        view.findViewById<TextView>(R.id.video)?.apply {
            if (video.isNotBlank()) {
                val sIcon = SpannableString(" ")
                val cDrawable = AppCompatResources.getDrawable(context, R.drawable.outline_video_24)
                cDrawable?.let {
                    it.setBounds(0, 0, it.intrinsicWidth, it.intrinsicHeight)
                    val span = ImageSpan(it, DynamicDrawableSpan.ALIGN_CENTER)
                    sIcon.setSpan(span, 0, 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                }
                text = SpanFormat.format("%s $video", sIcon)
            } else
                visibility = View.GONE
        }
        view.findViewById<TextView>(R.id.audio)?.apply {
            if (audio.isNotBlank()) {
                val sIcon = SpannableString(" ")
                val cDrawable = AppCompatResources.getDrawable(context, R.drawable.outline_audio_24)
                cDrawable?.let {
                    it.setBounds(0, 0, it.intrinsicWidth, it.intrinsicHeight)
                    val span = ImageSpan(it, DynamicDrawableSpan.ALIGN_CENTER)
                    sIcon.setSpan(span, 0, 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                }
                text = SpanFormat.format("%s $audio", sIcon)
            } else
                visibility = View.GONE
        }
        view.findViewById<TextView>(R.id.subtitles)?.apply {
            if (subtitles.isNotBlank()) {
                val sIcon = SpannableString(" ")
                val cDrawable = AppCompatResources.getDrawable(context, R.drawable.outline_subtitles_24)
                cDrawable?.let {
                    it.setBounds(0, 0, it.intrinsicWidth, it.intrinsicHeight)
                    val span = ImageSpan(it, DynamicDrawableSpan.ALIGN_CENTER)
                    sIcon.setSpan(span, 0, 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                }
                text = SpanFormat.format("%s $subtitles", sIcon)
            } else
                visibility = View.GONE
        }
        view.findViewById<TextView>(R.id.infoline)?.apply {
            visibility = View.GONE
        }
        view.findViewById<TextView>(R.id.tvSize)?.apply {
            if (size.isNotBlank()) {
                text = size
                val shapeDrawable = MaterialShapeDrawable(shapeAppearanceModel)
                shapeDrawable.fillColor = labelsColor.withAlpha(220)
                shapeDrawable.setStroke(2.0f, labelsColor.withAlpha(128))
                background = shapeDrawable
                setTextColor(labelsTextColor)
            } else
                visibility = View.GONE
        }
        view.findViewById<TextView>(R.id.tvRuntime)?.apply {
            if (runtime.isNotBlank()) {
                text = runtime
                val shapeDrawable = MaterialShapeDrawable(shapeAppearanceModel)
                shapeDrawable.fillColor = labelsColor.withAlpha(220)
                shapeDrawable.setStroke(2.0f, labelsColor.withAlpha(128))
                background = shapeDrawable
                setTextColor(labelsTextColor)
            } else
                visibility = View.GONE
        }
        view.findViewById<TextView>(R.id.tvBitrate)?.apply {
            if (bitrate.isNotBlank()) {
                text = bitrate
                val shapeDrawable = MaterialShapeDrawable(shapeAppearanceModel)
                shapeDrawable.fillColor = labelsColor.withAlpha(220)
                shapeDrawable.setStroke(2.0f, labelsColor.withAlpha(128))
                background = shapeDrawable
                setTextColor(labelsTextColor)
            } else
                visibility = View.GONE
        }

        val builder = AlertDialog.Builder(context)
        if (title.isNotEmpty())
            builder.setTitle(title)

        if (torrLink.isNotBlank()) {
            builder.setPositiveButton(R.string.add) { dlg, _ ->
                CoroutineScope(Dispatchers.IO).launch {
                    try {
                        val torrent = addTorrent("", torrLink, title, "", "", true)
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
        } else {
            builder.setNeutralButton(android.R.string.ok) { dlg, _ ->
                dlg.dismiss()
            }
        }

        val dialog = builder.create()
        dialog.setView(view)
        dialog.show()
        dialog.getButton(DialogInterface.BUTTON_POSITIVE)?.requestFocus()
    }
}