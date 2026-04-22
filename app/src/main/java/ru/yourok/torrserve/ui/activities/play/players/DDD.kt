package ru.yourok.torrserve.ui.activities.play.players

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.core.net.toUri
import ru.yourok.torrserve.server.models.torrent.Torrent
import ru.yourok.torrserve.utils.Mime
import ru.yourok.torrserve.utils.TorrentHelper
import java.io.File

object DDD {
    fun getIntent(pkg: String, torrent: Torrent, index: Int): Intent {
        val link = TorrentHelper.getTorrentPlayLink(torrent, index)
        val file = TorrentHelper.findFile(torrent, index) ?: throw Exception("file in torrent not found")
        val mime = Mime.getMimeType(file.path)

        val intent = Intent(Intent.ACTION_VIEW)
        intent.setPackage(pkg)

        intent.setDataAndType(link.toUri(), mime)
        intent.putExtra("title", torrent.title)
        intent.putExtra("poster", torrent.poster)

        val torrfiles = TorrentHelper.getPlayableFiles(torrent)
        if (torrfiles.size > 1) {
            val videoUris = ArrayList<Uri>()
            val titles = ArrayList<String>()
            val subsList = ArrayList<Bundle>()

            torrfiles.forEachIndexed { i, torrFile ->
                val fileLink = TorrentHelper.getFileLink(torrent, torrFile)
                videoUris.add(fileLink.toUri())
                titles.add(File(torrFile.path).name)

                val subsBundle = Bundle()
                subsList.add(subsBundle)
            }

            intent.putExtra("video_list", videoUris.toTypedArray())
            intent.putExtra("video_list.name", titles.toTypedArray())
            intent.putParcelableArrayListExtra("video_list.subtitles", subsList)
        }

        return intent
    }
}