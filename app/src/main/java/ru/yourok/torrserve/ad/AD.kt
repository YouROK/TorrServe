package ru.yourok.torrserve.ad

import android.graphics.drawable.ColorDrawable
import android.view.View
import android.widget.ImageView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.bumptech.glide.Glide
import com.bumptech.glide.load.resource.bitmap.BitmapTransitionOptions
//import com.google.firebase.analytics.FirebaseAnalytics
import kotlinx.coroutines.*
import ru.yourok.torrserve.ad.model.Image
import ru.yourok.torrserve.app.Consts
import ru.yourok.torrserve.settings.Settings

class AD(private val iv: ImageView, private val activity: AppCompatActivity) {
    private val lock = Any()

    fun get() {
        activity.lifecycleScope.launch(Dispatchers.IO) {
            synchronized(lock) {
                ADManager.get()?.let { js ->
                    try {
                        if (ADManager.expired()) {
                            Settings.setShowBanner(true)
                            return@launch
                        }

                        if (!Settings.showBanner()) {
//                            FirebaseAnalytics.getInstance(activity).logEvent("view_ad_disable", null)
                            return@launch
                        }

                        if (js.images.isNotEmpty()) {
                            loadImages(js.images)
//                            FirebaseAnalytics.getInstance(activity).logEvent("view_ad", null)
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

    suspend fun hideAd() = withContext(Dispatchers.Main) {
        iv.visibility = View.GONE
    }

    suspend fun showAd() = withContext(Dispatchers.Main) {
        iv.visibility = View.VISIBLE
    }

    private fun loadImages(lst: List<Image>) {
        activity.lifecycleScope.launch {
            withContext(Dispatchers.Main) { iv.visibility = View.VISIBLE }

            if (lst.size == 1) {
                loadImg(lst[0].url)
                return@launch
            }

            try {
                var currImg = 0
                while (this.isActive) {
                    val img = lst[currImg]
                    loadImg(img.url)
                    currImg++
                    if (currImg >= lst.size)
                        currImg = 0

                    delay(img.wait * 1000)
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private fun loadImg(linkImg: String) {
        var link = linkImg
        if (!link.startsWith("http"))
            link = Consts.AD_LINK + link

        Glide.with(activity)
            .asBitmap()
            .load(link)
            .fitCenter()
            .placeholder(ColorDrawable(0x3c000000))
            .transition(BitmapTransitionOptions.withCrossFade())
            .into(iv)
    }
}