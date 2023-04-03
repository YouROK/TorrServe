package ru.yourok.torrserve.app

import android.animation.ValueAnimator
import android.content.Context
import android.util.AttributeSet
import android.view.View
import android.widget.ImageView
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
        val logo = content.findViewById<ImageView>(R.id.ivLogo)
        breathFadeAnimation(logo, App.longDuration.toLong())
    }

    override fun animateContentOut(delay: Int, duration: Int) {
        content.animate().apply {
            alpha(0f)
            setDuration(animateOutDuration)
            startDelay = delay.toLong()
        }

    }

    private fun breathFadeAnimation(view: View, period: Long) {
        val minAlpha = 0.25f // Minimum alpha value
        val maxAlpha = 1.0f // Maximum alpha value

        // Create alpha animator for fading in
        val fadeInAnimator = ValueAnimator.ofFloat(minAlpha, maxAlpha).apply {
            duration = period / 2
            repeatCount = ValueAnimator.INFINITE
            repeatMode = ValueAnimator.REVERSE
            addUpdateListener { valueAnimator ->
                val alphaValue = valueAnimator.animatedValue as Float
                view.alpha = alphaValue
            }
        }

        // Create alpha animator for fading out
        val fadeOutAnimator = ValueAnimator.ofFloat(maxAlpha, minAlpha).apply {
            duration = period / 2
            repeatCount = ValueAnimator.INFINITE
            repeatMode = ValueAnimator.REVERSE
            addUpdateListener { valueAnimator ->
                val alphaValue = valueAnimator.animatedValue as Float
                view.alpha = alphaValue
            }
        }

        // Start both animators simultaneously
        fadeInAnimator.start()
        fadeOutAnimator.start()
    }


    companion object {
        private const val animateInDuration = 500L
        private const val animateOutDuration = 150L
    }
}