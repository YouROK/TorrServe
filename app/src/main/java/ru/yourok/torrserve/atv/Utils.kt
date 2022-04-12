package ru.yourok.torrserve.atv

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.util.Log
import ru.yourok.torrserve.app.App
import ru.yourok.torrserve.atv.channels.UpdaterCards
import ru.yourok.torrserve.server.api.Api
import ru.yourok.torrserve.server.local.TorrService
import ru.yourok.torrserve.server.models.torrent.Torrent
import ru.yourok.torrserve.ui.activities.play.PlayActivity
import ru.yourok.torrserve.utils.TorrentHelper
import kotlin.concurrent.thread

object Utils {

    fun isGoogleTV(): Boolean {
        return App.appContext().packageManager.hasSystemFeature("android.software.leanback") && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
    }

    fun buildPendingIntent(torr: Torrent): Intent {
        val vintent = Intent(App.appContext(), PlayActivity::class.java)
        vintent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
        vintent.action = Intent.ACTION_VIEW
        vintent.data = Uri.parse(TorrentHelper.getTorrentMagnet(torr))
        vintent.putExtra("action", "play")
        vintent.putExtra("hash", torr.hash)
        vintent.putExtra("title", torr.title)
        vintent.putExtra("poster", torr.poster)
        vintent.putExtra("data", torr.data)
        vintent.putExtra("save", false)
        return vintent
    }

    private var updateStarted = false
    fun updateAtvCards() {
        if (isGoogleTV()) {
            synchronized(updateStarted) {
                if (updateStarted)
                    return
                updateStarted = true
            }
            TorrService.wait(5)
            Log.d("*****", "updateAtvCards()")
            var lastList = emptyList<Torrent>()
            try {
                lastList = Api.listTorrent()
            } catch (e: Exception) {
            }
            UpdaterCards.updateCards()
            thread {
                while (updateStarted) {
                    var torrs = emptyList<Torrent>()
                    try {
                        torrs = Api.listTorrent()
                    } catch (e: Exception) {
                    }
                    if (!equalTorrs(lastList, torrs)) {
                        lastList = torrs
                        UpdaterCards.updateCards()
                        Thread.sleep(1000)
                    } else
                        Thread.sleep(5000)
                }
            }
        }
    }

    private fun equalTorrs(lst1: List<Torrent>, lst2: List<Torrent>): Boolean {
        if (lst1.size != lst2.size)
            return false
        lst1.forEachIndexed { index, torr ->
            if (torr.hash != lst2[index].hash ||
                torr.title != lst2[index].title ||
                torr.poster != lst2[index].poster
            )
                return false
        }
        return true
    }

}
