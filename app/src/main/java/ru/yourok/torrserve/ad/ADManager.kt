package ru.yourok.torrserve.ad

import com.google.gson.Gson
import ru.yourok.torrserve.ad.model.ADData
import ru.yourok.torrserve.app.Consts
import ru.yourok.torrserve.utils.Net
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object ADManager {
    private var addata: ADData? = null

    fun expired(): Boolean {
        get()?.let {
            if (it.expired != "0") {
                val formatter = SimpleDateFormat("dd.MM.yyyy", Locale.US)
                val date = formatter.parse(it.expired) as Date
                return System.currentTimeMillis() > date.time
            }
        }
        return true
    }

    fun get(): ADData? {
        addata?.let {
            return it
        }
        try {
            val link = Consts.AD_LINK + "/ad.json"
            val buf = Net.get(link)
            val data = Gson().fromJson(buf, ADData::class.java)
            addata = data
            return data
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return null
    }
}