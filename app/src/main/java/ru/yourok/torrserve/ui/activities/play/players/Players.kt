package ru.yourok.torrserve.ui.activities.play.players

import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import ru.yourok.torrserve.app.App
import ru.yourok.torrserve.server.models.torrent.Torrent
import ru.yourok.torrserve.settings.Settings
import ru.yourok.torrserve.utils.Mime
import ru.yourok.torrserve.utils.TorrentHelper

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

        if (pkg == "0" && intent.resolveActivity(App.context.packageManager) != null)
            return intent

        if (pkg.isNotEmpty()) {
            intent.`package` = pkg
            if (intent.resolveActivity(App.context.packageManager) != null)
                return intent
            intent.`package` = ""
        }

        val cIntent = Intent.createChooser(intent, "")
        return cIntent
    }

    fun getList(): List<Pair<String, String>> {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse("file:///sdcard/Download/file.mp4"))
        val apps = App.context.packageManager.queryIntentActivities(intent, PackageManager.MATCH_DEFAULT_ONLY)
        val list = mutableListOf<Pair<String, String>>()
        for (a in apps) {
            val name = a.loadLabel(App.context.packageManager)?.toString() ?: a.activityInfo.packageName
            list.add(a.activityInfo.packageName to name)
        }
        return list
    }
}