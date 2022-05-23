package ru.yourok.torrserve.ui.activities.play.players

import android.content.Intent
import android.net.Uri
import ru.yourok.torrserve.server.models.torrent.Torrent
import ru.yourok.torrserve.utils.Mime
import ru.yourok.torrserve.utils.TorrentHelper
import java.io.File

object Vimu {
    fun getIntent(pkg: String, torrent: Torrent, index: Int): Intent {
        val link = TorrentHelper.getTorrentPlayLink(torrent, index)

        val file = TorrentHelper.findFile(torrent, index) ?: throw Exception("file in torrent not found")
        val mime = Mime.getMimeType(file.path)

        val intent = Intent(Intent.ACTION_VIEW)
        intent.setPackage(pkg)
        intent.putExtra("forceresume", true)
        intent.putExtra("forcename", torrent.title)
        intent.setDataAndType(Uri.parse(link), mime)

        val torrfiles = TorrentHelper.getPlayableFiles(torrent)
        if (torrfiles.size > 1) {
            val names = ArrayList<String>()
            val files = ArrayList<String>()
            torrfiles.forEach {
                if (it.id >= index) {
                    names.add(File(it.path).name)
                    files.add(TorrentHelper.getFileLink(torrent, it))
                }
            }
            intent.setDataAndType(Uri.parse(link), "application/vnd.gtvbox.filelist")
            intent.putStringArrayListExtra("asusfilelist", files)
            intent.putStringArrayListExtra("asusnamelist", names)
        }
        return intent
    }
}