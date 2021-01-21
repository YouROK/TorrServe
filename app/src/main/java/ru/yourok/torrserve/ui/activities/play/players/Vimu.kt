package ru.yourok.torrserve.ui.activities.play.players

import android.content.Intent
import android.net.Uri
import ru.yourok.torrserve.server.models.torrent.Torrent
import ru.yourok.torrserve.utils.TorrentHelper
import java.io.File

object Vimu {
    fun getIntent(torrent: Torrent, index: Int): Intent {
        val link = TorrentHelper.getTorrentPlayLink(torrent, index)

        val intent = Intent(Intent.ACTION_VIEW)
        intent.setPackage("net.gtvbox.videoplayer")
        intent.putExtra("forceresume", true)
        intent.putExtra("forcename", torrent.title)
        intent.setDataAndType(Uri.parse(link), "application/vnd.gtvbox.filelist")

        val names = ArrayList<String>()
        val files = ArrayList<String>()

        val torrfiles = TorrentHelper.getPlayableFiles(torrent)
        torrfiles.forEach {
            if (it.id >= index) {
                names.add(File(it.path).name)
                files.add(TorrentHelper.getFileLink(torrent, it))
            }
        }

        intent.putStringArrayListExtra("asusfilelist", files)
        intent.putStringArrayListExtra("asusnamelist", names)
        return intent
    }
}