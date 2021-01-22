package ru.yourok.torrserve.utils

import ru.yourok.torrserve.R
import ru.yourok.torrserve.app.App

/**
 * Created by yourok on 23.02.18.
 */
object ByteFmt {
    fun byteFmt(bytes: Double): String {
        if (bytes < 1024)
            return bytes.toString() + " " + App.context.getString(R.string.fmt_b)
        val exp = (Math.log(bytes) / Math.log(1024.0)).toInt()
        val pre = App.context.getString(R.string.fmt_p)[exp - 1].toString()
        return "%.1f %s".format(bytes / Math.pow(1024.0, exp.toDouble()), pre) + App.context.getString(R.string.fmt_b)
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