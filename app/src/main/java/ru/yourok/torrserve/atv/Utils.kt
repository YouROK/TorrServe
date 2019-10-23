package ru.yourok.torrserve.atv

import android.content.Intent
import android.net.Uri
import ru.yourok.torrserve.activitys.play.PlayActivity
import ru.yourok.torrserve.app.App
import ru.yourok.torrserve.atv.channels.providers.Torrent

object Utils {

    fun isGoogleTV(): Boolean {
        return App.getContext().packageManager.hasSystemFeature("android.software.leanback")
    }

    fun buildPendingIntent(torr: Torrent): Intent {
        val vintent = Intent(App.getContext(), PlayActivity::class.java)
        vintent.setData(Uri.parse(torr.magnet))
        vintent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
        vintent.action = Intent.ACTION_VIEW
        vintent.putExtra("DontSave", true)
        vintent.putExtra("Title", torr.name)
        vintent.putExtra("Poster", torr.poster)
        return vintent
    }
}
