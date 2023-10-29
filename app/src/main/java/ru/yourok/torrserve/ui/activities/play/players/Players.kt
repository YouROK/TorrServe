package ru.yourok.torrserve.ui.activities.play.players

import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Environment
import ru.yourok.torrserve.R
import ru.yourok.torrserve.app.App
import ru.yourok.torrserve.server.models.torrent.Torrent
import ru.yourok.torrserve.settings.Settings
import ru.yourok.torrserve.utils.Mime
import ru.yourok.torrserve.utils.TorrentHelper
import java.io.File
import java.util.Locale

object Players {

    fun getIntent(torrent: Torrent, index: Int, selectedPlayer: String?): Intent? {
        val file = TorrentHelper.findFile(torrent, index) ?: throw Exception("file in torrent not found")
        val link = TorrentHelper.getTorrentPlayLink(torrent, index)
        var player = Settings.getPlayer()
        if (!selectedPlayer.isNullOrEmpty()) {
            player = selectedPlayer
        }
        val mime = Mime.getMimeType(file.path)
        val intent = Intent(Intent.ACTION_VIEW)
        intent.setDataAndType(Uri.parse(link), mime)
        intent.putExtra("title", torrent.title)
        intent.putExtra("poster", torrent.poster)
        // default os player
        if (player == "0" && intent.resolveActivity(App.context.packageManager) != null)
            return intent
        // ViMu player
        if (player == "net.gtvbox.videoplayer" || player == "net.gtvbox.vimuhd") {
            val vimuIntent = Vimu.getIntent(player, torrent, index)
            if (vimuIntent.resolveActivity(App.context.packageManager) != null)
                return vimuIntent
        }
        // MX player
        if (player.contains("com.mxtech.videoplayer", true)) {
            val mxIntent = MX.getIntent(player, torrent, index)
            if (mxIntent.resolveActivity(App.context.packageManager) != null)
                return mxIntent
        }
        // UPlayer
        if (player.contains("com.uapplication.uplayer", true)) {
            val uIntent = UPlayer.getIntent(player, torrent, index)
            if (uIntent.resolveActivity(App.context.packageManager) != null)
                return uIntent
        }
        // user defined player
        if (player.isNotEmpty()) {
            intent.`package` = player
            if (intent.resolveActivity(App.context.packageManager) != null)
                return intent
            intent.`package` = null
        }
        // always ask / wrong package set
        return Intent.createChooser(intent, "")
    }

    fun getList(): List<Pair<String, String>> {
        val intent = Intent(Intent.ACTION_VIEW)
        intent.setDataAndType(Uri.fromFile(File(Environment.getExternalStorageDirectory().path, "file.mp4")), "video/*")
        var apps = App.context.packageManager.queryIntentActivities(intent, PackageManager.MATCH_DEFAULT_ONLY)
        val excludedApps = hashSetOf(
            "com.android.gallery3d",
            "com.android.tv.frameworkpackagestubs",
            "com.estrongs.android.pop",
            "com.ghisler.android.totalcommander",
            "com.google.android.apps.photos",
            "com.google.android.tv.frameworkpackagestubs",
            "com.instantbits.cast.webvideo",
            "com.lonelycatgames.xplore",
            "com.mixplorer.silver",
            "pl.solidexplorer2"
        )
        val list = mutableListOf<Pair<String, String>>()
        list.add("" to App.context.getString(R.string.choose_player))
        list.add("0" to App.context.getString(R.string.default_player))
        for (a in apps) {
            val name = a.loadLabel(App.context.packageManager)?.toString() ?: a.activityInfo.packageName
            if (excludedApps.contains(a.activityInfo.packageName.lowercase(Locale.getDefault()))) {
                continue
            }
            list.add(a.activityInfo.packageName to name)
        }

        intent.setDataAndType(Uri.fromFile(File(Environment.getExternalStorageDirectory().path, "file.mp3")), "audio/*")
        apps = App.context.packageManager.queryIntentActivities(intent, PackageManager.MATCH_DEFAULT_ONLY)
        for (a in apps) {
            val name = a.loadLabel(App.context.packageManager)?.toString() ?: a.activityInfo.packageName
            list.add(a.activityInfo.packageName to name)
        }

        return list.distinctBy { it.first }
    }

}