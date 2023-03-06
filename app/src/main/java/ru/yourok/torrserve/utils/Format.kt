package ru.yourok.torrserve.utils

import android.util.TypedValue
import ru.yourok.torrserve.R
import ru.yourok.torrserve.app.App
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.ln
import kotlin.math.pow
import kotlin.time.DurationUnit
import kotlin.time.toDuration

/**
 * Created by yourok on 23.02.18.
 */
object Format {

    fun speedFmt(bytes: Double): String {
        val bits = bytes * 8
        if (bits < 1000)
            return "%.1f".format(bits) + " " + App.context.getString(R.string.fmt_bps)
        val exp = (ln(bits) / ln(1000.0)).toInt()
        val pre = App.context.getString(R.string.fmt_p)[exp - 1].toString()
        return "%.1f %s".format(bits / 1000.0.pow(exp.toDouble()), pre) + App.context.getString(R.string.fmt_bps)
    }

    fun byteFmt(bytes: Double): String {
        if (bytes < 1024)
            return "%.1f".format(bytes) + " " + App.context.getString(R.string.fmt_b)
        val exp = (ln(bytes) / ln(1024.0)).toInt()
        val pre = App.context.getString(R.string.fmt_p)[exp - 1].toString()
        return "%.1f %s".format(bytes / 1024.0.pow(exp.toDouble()), pre) + App.context.getString(R.string.fmt_b)
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

    fun sdateFmt(timestamp: Long): String {
        val sdf = SimpleDateFormat("dd.MM.yyyy", Locale("US"))
        sdf.timeZone = TimeZone.getDefault()
        return sdf.format(Date(timestamp * 1000))
    }

    fun durFmt(data: Double): String {
        val duration = data.toDuration(DurationUnit.SECONDS)
        return duration.toComponents { hours, minutes, seconds, _ ->
            String.format("%02d:%02d:%02d", hours, minutes, seconds)
        }
    }

    fun dp2px(dip: Float): Int {
        val dm = App.context.resources.displayMetrics
        return TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, dip, dm).toInt()
    }
}