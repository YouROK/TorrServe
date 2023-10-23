package ru.yourok.torrserve.ui.activities.play.players

import android.content.ComponentName
import android.content.Intent
import android.net.Uri
import android.os.Parcelable
import ru.yourok.torrserve.server.models.torrent.Torrent
import ru.yourok.torrserve.utils.Mime
import ru.yourok.torrserve.utils.TorrentHelper
import java.io.File

object UPlayer {
    fun getIntent(pkg: String, torrent: Torrent, index: Int): Intent {
        val link = TorrentHelper.getTorrentPlayLink(torrent, index)

        val file = TorrentHelper.findFile(torrent, index) ?: throw Exception("file in torrent not found")

        val intent = Intent(Intent.ACTION_VIEW)
        intent.setPackage(pkg)
//        intent.component = ComponentName(pkg, "$pkg.Comp")

        val mime = Mime.getMimeType(file.path)
        intent.setDataAndType(Uri.parse(link), mime)

        val torrfiles = TorrentHelper.getPlayableFiles(torrent)
        if (torrfiles.size > 1) {
            intent.putExtra("playlistTitle", torrent.title)
            val names = ArrayList<String>()
            val files = ArrayList<String>()
            var idx = 0
            for (i in torrfiles.indices) {
                names.add(File(torrfiles[i].path).name)
                files.add(TorrentHelper.getFileLink(torrent, torrfiles[i]))
                if (torrfiles[i].id == index) idx = i
            }
            intent.putExtra("titleList", names) // ArrayList<String>
            intent.putExtra("videoList", files) // ArrayList<String>
            intent.putExtra("playlistPosition", idx) // Int
        }
        return intent
    }
}