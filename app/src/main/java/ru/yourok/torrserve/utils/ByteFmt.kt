package ru.yourok.torrserve.utils

/**
 * Created by yourok on 23.02.18.
 */
object ByteFmt {
    fun byteFmt(bytes: Double): String {
        if (bytes < 1024)
            return bytes.toString() + " B"
        val exp = (Math.log(bytes) / Math.log(1024.0)).toInt()
        val pre = "KMGTPE"[exp - 1].toString()
        return "%.1f %sB".format(bytes / Math.pow(1024.0, exp.toDouble()), pre)
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