package ru.yourok.torrserve.utils

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.drawable.Drawable
import android.text.style.ImageSpan
import java.lang.ref.WeakReference

class CImageSpan(dr: Drawable) : ImageSpan(dr) {
    private var mDrawableRef: WeakReference<Drawable>? = null
    override fun getSize(paint: Paint, text: CharSequence?, start: Int, end: Int, fm: Paint.FontMetricsInt?): Int {
        val d = cachedDrawable
        val rect: Rect = d!!.bounds
        val pfm = paint.fontMetricsInt
        if (fm != null) {
            fm.ascent = -rect.height() / 2 + pfm.ascent / 2
            fm.descent = 0.coerceAtLeast(rect.height() / 2 + pfm.ascent / 2)
            fm.top = fm.ascent
            fm.bottom = fm.descent
        }
        return rect.right
    }

    override fun draw(canvas: Canvas, text: CharSequence?, start: Int, end: Int, x: Float, top: Int, y: Int, bottom: Int, paint: Paint) {
        val b = cachedDrawable!!
        canvas.save()
        val transY = (bottom + top) / 2 - b.bounds.height() / 2
        canvas.translate(x, transY.toFloat())
        b.draw(canvas)
        canvas.restore()
    }

    // Redefined locally because it is a private member from DynamicDrawableSpan
    private val cachedDrawable: Drawable?
        get() {
            val wr: WeakReference<Drawable>? = mDrawableRef
            var d: Drawable? = null
            if (wr != null) d = wr.get()
            if (d == null) {
                d = drawable
                mDrawableRef = WeakReference(d)
            }
            return d
        }
}