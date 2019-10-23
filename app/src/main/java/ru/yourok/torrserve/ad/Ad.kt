package ru.yourok.torrserve.ad

import android.app.Activity
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.view.View
import android.widget.ImageView
import com.google.firebase.analytics.FirebaseAnalytics
import com.google.gson.Gson
import com.squareup.picasso.Callback
import com.squareup.picasso.Picasso
import ru.yourok.torrserve.BuildConfig
import ru.yourok.torrserve.ad.model.AdJson
import ru.yourok.torrserve.preferences.Preferences
import ru.yourok.torrserve.utils.Http
import java.text.SimpleDateFormat
import java.util.*
import kotlin.concurrent.thread


class Ad(private val iv: ImageView, private val activity: Activity) {
    companion object {
        val base_hosts = listOf(
                "https://yourok.github.io/TorrServePage",
                "http://tor-serve.surge.sh",
                "http://torr-serve.surge.sh"
        )
    }

    private var ad_base = ""
    private val lock = Any()

    init {
        iv.visibility = View.GONE
    }

    fun get() {
        synchronized(lock) {}
        thread {
            synchronized(lock) {
                val jsAd = getJson()
                jsAd?.let { js ->
                    try {
                        if (js.ad_expired != "0") {
                            val formatter = SimpleDateFormat("dd.MM.yyyy")
                            val date = formatter.parse(js.ad_expired) as Date
                            if (date.time < System.currentTimeMillis()) {
                                Preferences.disableAD(false)
                                return@thread
                            }
                        }

                        if (Preferences.isDisableAD()) {
                            FirebaseAnalytics.getInstance(activity).logEvent("view_ad_disable", null)
                            return@thread
                        }

                        if (js.ad_link.isNotEmpty()) {
                            loadImages(js.ad_link)
                            FirebaseAnalytics.getInstance(activity).logEvent("view_ad", null)
                            Thread.sleep(5000)
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
            }
        }
    }

    fun waitAd() {
        synchronized(lock) {}
    }

    private fun loadImages(lst: List<String>) {
        thread {
            Handler(Looper.getMainLooper()).post {
                iv.visibility = View.VISIBLE
            }

            if (lst.size == 1) {
                loadImg(lst[0])
                return@thread
            }

            try {
                var currImg = 0
                while (!activity.isFinishing) {
                    val img = lst[currImg]
                    loadImg(img)
                    currImg++
                    if (currImg >= lst.size)
                        currImg = 0

                    for (i in 0..100) {
                        Thread.sleep(100)
                        if (activity.isFinishing)
                            break
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private fun loadImg(linkImg: String) {
        var link = linkImg
        if (!link.startsWith("http"))
            link = ad_base + link

        val pcs = Picasso.get()
                .load(link)
                .placeholder(iv.drawable)
                .noFade()

        Handler(Looper.getMainLooper()).post {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) {
                iv.animate().alpha(0f).setDuration(200)
                        .withEndAction {
                            pcs.into(iv, object : Callback {
                                override fun onSuccess() {
                                    iv.animate().setDuration(200).alpha(1f).start()
                                }

                                override fun onError(e: java.lang.Exception?) {
                                }
                            })
                        }
                        .start()
            } else {
                pcs.into(iv)
            }
        }

    }

    private fun getJson(): AdJson? {
        base_hosts.forEach { host ->
            try {
                var link = "$host/ad.json"
                if (BuildConfig.DEBUG)
                    link = "$host/ad_test.json"
                val body = getBody(link)
                ad_base = host
                return Gson().fromJson(body, AdJson::class.java)
            } catch (e: Exception) {
                e.printStackTrace()

            }
        }
        return null
    }

    private fun getBody(link: String): String {
        val http = Http(link)
        return http.readTimeout(2)
    }
}