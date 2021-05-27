package ru.yourok.torrserve.animations

import android.view.View

object FIO {

    fun anim(view1: View, view2: View, time: Long, onEnd: (() -> Unit)? = null) {
        view2.visibility = View.GONE
        view2.alpha = 0.0f

        view1.animate().apply {
            duration = time / 2
            alpha(0.0f)
            withEndAction {
                view1.visibility = View.GONE
                view2.visibility = View.VISIBLE
                view2.animate()?.apply {
                    duration = time / 2
                    alpha(1.0f)
                    start()
                    withEndAction {
                        onEnd?.invoke()
                    }
                }
            }
            start()
        }
    }

}