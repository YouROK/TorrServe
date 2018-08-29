package ru.yourok.torrserve.server.api

import android.os.Handler
import android.os.Looper
import android.support.v4.content.ContextCompat
import android.widget.ImageView
import com.squareup.picasso.Picasso
import ru.yourok.torrserve.R
import ru.yourok.torrserve.app.App

object ImgLoader {
    fun load(url: String, img: ImageView) {
        if (url.isNotEmpty()) {
            val picass = Picasso.get().load(url).placeholder(R.color.lighter_gray).fit().centerCrop()
            Handler(Looper.getMainLooper()).post {
                picass.into(img)
            }
            return
        }
        Handler(Looper.getMainLooper()).post {
            img.setImageDrawable(ContextCompat.getDrawable(App.getContext(), R.drawable.emptyposter))
            img.setBackgroundResource(R.color.darker_gray)
        }
    }
}