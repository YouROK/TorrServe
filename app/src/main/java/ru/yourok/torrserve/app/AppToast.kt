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
    }

    companion object {
        private const val TEXT_SIZE = 18f
        private val logoSizePx = dp2px(25f)

        fun make(viewGroup: ViewGroup, txt: String): AppToast {
            val screenWidth = App.currentActivity?.window?.decorView?.rootView?.width ?: 0
            val screenHeight = App.currentActivity?.window?.decorView?.rootView?.height ?: 0
            val isInLandscape = screenWidth > screenHeight

            val paddingPx = if (isInLandscape) dp2px(16f) else dp2px(12f)

            val snackView = LayoutInflater.from(viewGroup.context).inflate(
                R.layout.layout_toast,
                viewGroup,
                false
            ) as AppToastView

            val ivLogo = snackView.findViewById<ImageView>(R.id.ivLogo)
            ivLogo.updateLayoutParams {
                width = logoSizePx
                height = logoSizePx
            }

            val tvMessage = snackView.findViewById<TextView>(R.id.tvMessage)
            tvMessage.textSize = TEXT_SIZE
            if (txt.isNotEmpty()) tvMessage.apply {
                text = txt
                val offset = if (isInLandscape) dp2px(4f) else dp2px(2f)
                if (Locale.getDefault().layoutDirection == LayoutDirection.RTL)
                    this.setPadding(paddingPx, 0, paddingPx - offset, 0)
                else
                    this.setPadding(paddingPx - offset, 0, paddingPx, 0)
            }

            val appToast = AppToast(viewGroup, snackView)

            appToast.setGestureInsetBottomIgnored(true) // don't add toolbar margin

            appToast.getView().setPadding(paddingPx, paddingPx, paddingPx, paddingPx)

            var hMargin = dp2px(12f)
            if (isInLandscape)
                hMargin = screenWidth / 6
            val vMargin = dp2px(64f)

            val layoutParams = appToast.getView().layoutParams as MarginLayoutParams
            layoutParams.setMargins(hMargin, vMargin, hMargin, vMargin)
            appToast.getView().layoutParams = layoutParams // apply margins

            if (Build.VERSION.SDK_INT > Build.VERSION_CODES.KITKAT)
                tvMessage.viewTreeObserver.addOnPreDrawListener(object : ViewTreeObserver.OnPreDrawListener {
                    override fun onPreDraw(): Boolean {
                        tvMessage.viewTreeObserver.removeOnPreDrawListener(this)
                        // adjust tvMessage height to fit view
                        val compoundPadding = tvMessage.getCompoundPaddingTop() + tvMessage.getCompoundPaddingBottom()
                        val noOfLinesVisible: Int = (tvMessage.height - compoundPadding) / tvMessage.lineHeight
                        tvMessage.setMaxLines(noOfLinesVisible)
                        tvMessage.ellipsize = TextUtils.TruncateAt.END
                        return true
                    }
                })

            return appToast
        }
    }
}