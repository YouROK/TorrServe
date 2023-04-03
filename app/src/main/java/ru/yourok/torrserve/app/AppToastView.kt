package ru.yourok.torrserve.app

import android.content.Context
import android.util.AttributeSet
import android.view.View
import androidx.constraintlayout.widget.ConstraintLayout
import com.google.android.material.snackbar.ContentViewCallback
import ru.yourok.torrserve.R

class AppToastView @JvmOverloads constructor(
    context: Context,
    attributeSet: AttributeSet? = null,
    defaultStyle: Int = 0
) : ConstraintLayout(context, attributeSet, defaultStyle), ContentViewCallback {
    private val content: View

    init {
        content = View.inflate(context, R.layout.item_toast, this)
    }

    override fun animateContentIn(delay: Int, duration: Int) {
        content.alpha = 0f
        content.animate().apply {
            alpha(1.0f)
            setDuration(animateInDuration)
            startDelay = delay.toLong()
        }
    }

    override fun animateContentOut(delay: Int, duration: Int) {
        content.animate().apply {
            alpha(0f)
            setDuration(animateOutDuration)
            startDelay = delay.toLong()
        }

    }

    companion object {
        private const val animateInDuration = 500L
        private const val animateOutDuration = 150L
    }
}