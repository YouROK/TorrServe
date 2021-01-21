package ru.yourok.torrserve.ui.activities.play.players

import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import ru.yourok.torrserve.R
import ru.yourok.torrserve.app.App
import ru.yourok.torrserve.server.models.torrent.Torrent
import ru.yourok.torrserve.settings.Settings
import ru.yourok.torrserve.utils.Mime
import ru.yourok.torrserve.utils.TorrentHelper
import java.io.File

object Players {

    fun getIntent(torrent: Torrent, index: Int): Intent {
        val file = TorrentHelper.findFile(torrent, index) ?: throw Exception("file in torrent not found")
        val link = TorrentHelper.getTorrentPlayLink(torrent, index)
        val pkg = Settings.getPlayer()
        val mime = Mime.getMimeType(file.path)

        val intent = Intent(Intent.ACTION_VIEW)
        intent.setDataAndType(Uri.parse(link), mime)
        intent.putExtra("title", torrent.title)
        intent.putExtra("poster", torrent.poster)
        intent.putExtra("forcename", torrent.title) // ViMu
        intent.putExtra("forceresume", true) // ViMu
        // default player
        if (pkg == "0" && intent.resolveActivity(App.context.packageManager) != null)
            return intent
        // vimu player
        if (pkg == "net.gtvbox.videoplayer") {
            val vimuIntent = Vimu.getIntent(torrent, index)
            if (vimuIntent.resolveActivity(App.context.packageManager) != null)
                return vimuIntent
        }
        // user defined player
        if (pkg.isNotEmpty()) {
            intent.`package` = pkg
            if (intent.resolveActivity(App.context.packageManager) != null)
                return intent
            intent.`package` = null
        }
        // always ask / wrong package set
        val cIntent = Intent.createChooser(intent, "")
        return cIntent
    }

    fun getList(): List<Pair<String, String>> {
        val intent = Intent(Intent.ACTION_VIEW)
        intent.setDataAndType(Uri.fromFile(File("/sdcard/Download/file.mp4")), "video/*")
        var apps = App.context.packageManager.queryIntentActivities(intent, PackageManager.MATCH_DEFAULT_ONLY)
        val list = mutableListOf<Pair<String, String>>()
        list.add("" to App.context.getString(R.string.choose_player))
        list.add("0" to App.context.getString(R.string.default_player))

        for (a in apps) {
            val name = a.loadLabel(App.context.packageManager)?.toString() ?: a.activityInfo.packageName
            list.add(a.activityInfo.packageName to name)
        }

        intent.setDataAndType(Uri.fromFile(File("/sdcard/Download/file.mp3")), "audio/*")
        apps = App.context.packageManager.queryIntentActivities(intent, PackageManager.MATCH_DEFAULT_ONLY)
        for (a in apps) {
            val name = a.loadLabel(App.context.packageManager)?.toString() ?: a.activityInfo.packageName
            list.add(a.activityInfo.packageName to name)
        }

        return list.distinctBy { it.first }
    }
}