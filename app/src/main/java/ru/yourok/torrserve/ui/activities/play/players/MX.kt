package ru.yourok.torrserve.ui.activities.play.players

import android.content.ComponentName
import android.content.Intent
import android.net.Uri
import android.os.Parcelable
import ru.yourok.torrserve.server.models.torrent.Torrent
import ru.yourok.torrserve.utils.Mime
import ru.yourok.torrserve.utils.TorrentHelper
import java.io.File

object MX {
    fun getIntent(pkg: String, torrent: Torrent, index: Int): Intent {
        val link = TorrentHelper.getTorrentPlayLink(torrent, index)

        val file = TorrentHelper.findFile(torrent, index) ?: throw Exception("file in torrent not found")
        val mime = Mime.getMimeType(file.path)

        val intent = Intent(Intent.ACTION_VIEW)
//        intent.setPackage(pkg)
        intent.component = ComponentName(pkg, "$pkg.ActivityScreen")
        intent.putExtra("title", torrent.title)
        intent.putExtra("sticky", false)
        intent.setDataAndType(Uri.parse(link), mime)

        val torrfiles = TorrentHelper.getPlayableFiles(torrent)
        if (torrfiles.size > 1) {
            val names = ArrayList<String>()
            val parcelableArr = arrayOfNulls<Parcelable>(torrfiles.size)
            for (i in torrfiles.indices) {
                names.add(File(torrfiles[i].path).name)
                parcelableArr[i] = Uri.parse(TorrentHelper.getFileLink(torrent, torrfiles[i]))
            }
            val ta = names.toTypedArray()
            intent.putExtra("video_list", parcelableArr)
            intent.putExtra("video_list.name", ta)
            intent.putExtra(
                "video_list.filename",
                ta
            )
            intent.putExtra("video_list_is_explicit", true)
        }
        return intent
    }
}