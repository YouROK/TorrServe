package ru.yourok.torrserve.ad

import com.google.gson.Gson
import org.jsoup.Connection
import org.jsoup.Jsoup
import ru.yourok.torrserve.ad.model.ADData
import ru.yourok.torrserve.app.Consts
import java.text.SimpleDateFormat
import java.util.*

object ADManager {
    private var addata: ADData? = null

    fun expired(): Boolean {
        get()?.let {
            if (it.expired != "0") {
                val formatter = SimpleDateFormat("dd.MM.yyyy")
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
            val link = Consts.ad_link + "/ad.json"
            val buf = Jsoup.connect(link)
                .method(Connection.Method.GET)
                .ignoreContentType(true)
                .execute()
                .body()

            val data = Gson().fromJson(buf, ADData::class.java)
            addata = data
            return data
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return null
    }
}