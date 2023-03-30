package ru.yourok.torrserve.utils

import android.util.TypedValue
import ru.yourok.torrserve.R
import ru.yourok.torrserve.app.App
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
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

    fun sDateFmt(timestamp: Long): String {
        val sdf = SimpleDateFormat("dd.MM.yyyy", Locale("US"))
        sdf.timeZone = TimeZone.getDefault()
        return sdf.format(Date(timestamp * 1000))
    }

    fun sDateFmt(dateTimeString: String): String { //2021-06-21T00:00:00+03:00
        val idf = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssZ", Locale("US"))
        val date = idf.parse(dateTimeString)
        val sdf = SimpleDateFormat("dd.MM.yyyy", Locale("US"))
        sdf.timeZone = TimeZone.getDefault()
        return sdf.format(date)
    }

    fun durFmtS(data: Double): String {
        val duration = data.toDuration(DurationUnit.SECONDS)
//        return duration.toComponents { hours, minutes, seconds, _ ->
//            String.format("%02d:%02d:%02d", hours, minutes, seconds)
//        }
        val strDur = mutableListOf<String>()
        duration.toComponents { hours, minutes, seconds, _ ->
            if (hours > 0)
                strDur.add(String.format("%d %s", hours, App.context.getString(R.string.fmt_h)))
            if (minutes > 0)
                strDur.add(String.format("%d %s", minutes, App.context.getString(R.string.fmt_m)))
            if (hours == 0L && seconds > 0)
                strDur.add(String.format("%d %s", seconds, App.context.getString(R.string.fmt_s)))
        }
        return strDur.joinToString(" ")
    }

    fun dp2px(dip: Float): Int {
        val dm = App.context.resources.displayMetrics
        return TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, dip, dm).toInt()
    }
}