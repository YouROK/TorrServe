package ru.yourok.torrserve.ad

import android.net.Uri
import com.google.gson.Gson
import org.jsoup.Jsoup
import ru.yourok.torrserve.ad.model.ADData
import ru.yourok.torrserve.app.Consts
import ru.yourok.torrserve.utils.Net
import java.text.SimpleDateFormat
import java.util.*
import javax.net.ssl.HostnameVerifier
import javax.net.ssl.HttpsURLConnection

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
            val buf = getNet(link)
            val data = Gson().fromJson(buf, ADData::class.java)
            addata = data
            return data
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return null
    }

    private fun getNet(url: String): String {
        val link = Uri.parse(url)
        if (link.scheme.equals("https", true)) {
            val trustAllHostnames = HostnameVerifier { _, _ ->
                true // Just allow them all
            }
            HttpsURLConnection.setDefaultHostnameVerifier(trustAllHostnames)
        }
        val response = Jsoup.connect(url)
            .ignoreHttpErrors(true)
            .ignoreContentType(true)
            .sslSocketFactory(Net.insecureTlsSocketFactory())
            .timeout(3000)
            .execute()

        return when (response.statusCode()) {
            200 -> {
                response.body()
            }
            302 -> {
                ""
            }
            else -> {
                throw Exception(response.statusMessage())
            }
        }
    }
}