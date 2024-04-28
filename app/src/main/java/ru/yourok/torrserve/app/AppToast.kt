package ru.yourok.torrserve.app

import android.os.Build
import android.text.TextUtils
import android.util.LayoutDirection
import android.view.LayoutInflater
import android.view.ViewGroup
import android.view.ViewGroup.MarginLayoutParams
import android.view.ViewTreeObserver
import android.widget.ImageView
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.core.text.layoutDirection
import androidx.core.view.updateLayoutParams
import com.google.android.material.snackbar.BaseTransientBottomBar
import ru.yourok.torrserve.R
import ru.yourok.torrserve.utils.Format.dp2px
import ru.yourok.torrserve.utils.ThemeUtil
import java.util.Locale

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
        getView().findViewById<TextView>(R.id.tvMessage).setTextColor(ContextCompat.getColor(view.context, tc))

        getView().setPadding(paddingPx, paddingPx, paddingPx, paddingPx)

        val screenWidth = App.currentActivity?.window?.decorView?.rootView?.width ?: 0
        val screenHeight = App.currentActivity?.window?.decorView?.rootView?.height ?: 0
        var hmargin = dp2px(12f)
        if (screenWidth > screenHeight) // landscape
            hmargin = screenWidth / 6
        val vmargin = dp2px(64f)

        val layoutParams = getView().layoutParams as MarginLayoutParams
        layoutParams.setMargins(hmargin, vmargin, hmargin, vmargin)
        getView().layoutParams = layoutParams // apply margins
    }

    companion object {
        private const val TEXT_SIZE = 18f
        val paddingPx = dp2px(12f)
        private val logoSizePx = dp2px(22f)
        fun make(viewGroup: ViewGroup, txt: String): AppToast {
            val snackView = LayoutInflater.from(viewGroup.context).inflate(
                R.layout.layout_toast,
                viewGroup,
                false
            ) as AppToastView

            val ivLogo = snackView.findViewById<ImageView>(R.id.ivLogo)
            val tvMessage = snackView.findViewById<TextView>(R.id.tvMessage)

            tvMessage.textSize = TEXT_SIZE
            if (txt.isNotEmpty()) tvMessage.apply {
                text = txt
                if (Locale.getDefault().layoutDirection == LayoutDirection.RTL)
                    this.setPadding(paddingPx, 0, paddingPx - dp2px(2f), 0)
                else
                    this.setPadding(paddingPx - dp2px(2f), 0, paddingPx, 0)
            }

            ivLogo.updateLayoutParams {
                width = logoSizePx
                height = logoSizePx
            }

            val appToast = AppToast(viewGroup, snackView)
            appToast.setGestureInsetBottomIgnored(true) // don't add toolbar margin

            if (Build.VERSION.SDK_INT > Build.VERSION_CODES.KITKAT)
                tvMessage.viewTreeObserver.addOnPreDrawListener(object : ViewTreeObserver.OnPreDrawListener {
                    override fun onPreDraw(): Boolean {
                        tvMessage.viewTreeObserver.removeOnPreDrawListener(this)
                        // adjust tvMessage max lines to fit view
                        val fullLineHeight = tvMessage.lineHeight + tvMessage.getCompoundPaddingTop() + tvMessage.getCompoundPaddingBottom()
                        val noOfLinesVisible: Int = tvMessage.height / fullLineHeight
                        tvMessage.setMaxLines(noOfLinesVisible)
                        tvMessage.ellipsize = TextUtils.TruncateAt.END
                        // adjust appToast view height
                        val lineCount = tvMessage.lineCount
                        // Drawing happens after layout so we can assume getLineCount() returns the correct value
                        if (lineCount > 0) {
                            appToast.getView().updateLayoutParams {
                                height = 2 * paddingPx + fullLineHeight * lineCount
                            }
                        }
                        return true
                    }
                })

            return appToast
        }
    }
}