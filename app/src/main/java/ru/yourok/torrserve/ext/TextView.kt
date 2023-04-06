package ru.yourok.torrserve.ext

import android.graphics.Typeface
import android.text.Spannable
import android.text.SpannableString
import android.text.style.ForegroundColorSpan
import android.text.style.StyleSpan
import android.widget.TextView
import androidx.annotation.ColorInt

// textView.text = "" // Remove old text
// textView.append("Red Text", Color.RED)
// textView.append("Blue Bold Text", Color.BLUE, true)
fun TextView.append(string: String?, @ColorInt color: Int = 0, bold: Boolean = false) {
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