package ru.yourok.torrserve.utils

import ru.yourok.torrserve.R
import ru.yourok.torrserve.app.App
import kotlin.math.ln
import kotlin.math.pow

/**
 * Created by yourok on 23.02.18.
 */
object ByteFmt {

    fun speedFmt(bytes: Double): String {
        val bits = bytes * 8
        if (bits < 1000)
            return "%.1f".format(bits) + " " + App.appContext().getString(R.string.fmt_bps)
        val exp = (ln(bits) / ln(1000.0)).toInt()
        val pre = App.appContext().getString(R.string.fmt_p)[exp - 1].toString()
        return "%.1f %s".format(bits / 1000.0.pow(exp.toDouble()), pre) + App.appContext().getString(R.string.fmt_bps)
    }

    fun byteFmt(bytes: Double): String {
        if (bytes < 1024)
            return "%.1f".format(bytes) + " " + App.appContext().getString(R.string.fmt_b)
        val exp = (ln(bytes) / ln(1024.0)).toInt()
        val pre = App.appContext().getString(R.string.fmt_p)[exp - 1].toString()
        return "%.1f %s".format(bytes / 1024.0.pow(exp.toDouble()), pre) + App.appContext().getString(R.string.fmt_b)
    }

    fun byteFmt(bytes: Float): String {
        return byteFmt(bytes.toDouble())
    }

    fun byteFmt(bytes: Long): String {
        return byteFmt(bytes.toDouble())
    }

    fun byteFmt(bytes: Int): String {
        return byteFmt(bytes.toDouble())
    }
}