package ru.yourok.torrserve.app

import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.content.ContextCompat
import com.google.android.material.snackbar.BaseTransientBottomBar
import ru.yourok.torrserve.R
import ru.yourok.torrserve.utils.Format
import ru.yourok.torrserve.utils.ThemeUtil

class AppToast(
    parent: ViewGroup,
    content: AppToastView
) : BaseTransientBottomBar<AppToast>(parent, content, content) {

    init {
        var bg = R.drawable.snackbar
        var tc = R.color.tv_white
        if (ThemeUtil.selectedTheme == R.style.Theme_TorrServe_Light) {
            bg = R.drawable.snackbar_dark
            tc = R.color.tv_white
        }
        getView().setBackgroundResource(bg)
        getView().alpha = 0.5f
        getView().findViewById<TextView>(R.id.tvMessage).setTextColor(ContextCompat.getColor(view.context, tc))

        val padding = Format.dp2px(10f)
        getView().setPadding(padding, padding, padding, padding)

        val width = App.currentActivity()?.window?.decorView?.rootView?.width ?: 0
        val height = App.currentActivity()?.window?.decorView?.rootView?.height ?: 0
        var hmargin = Format.dp2px(12f)
        if (width > height) // landscape
            hmargin = width / 6
        val vmargin = Format.dp2px(64f)
        val layoutParams = getView().layoutParams as ViewGroup.MarginLayoutParams
        layoutParams.setMargins(hmargin, vmargin, hmargin, vmargin)
        getView().layoutParams = layoutParams

    }

    companion object {

        fun make(viewGroup: ViewGroup, txt: String): AppToast {
            val snackView = LayoutInflater.from(viewGroup.context).inflate(
                R.layout.layout_toast,
                viewGroup,
                false
            ) as AppToastView

            val tvMessage = snackView.findViewById<TextView>(R.id.tvMessage)
            if (txt.isNotEmpty()) tvMessage.text = txt

            return AppToast(viewGroup, snackView)
        }

    }

}