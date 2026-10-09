package ru.yourok.torrserve.ui.activities.play.players

import android.content.Intent
import android.os.Bundle
import androidx.core.net.toUri
import ru.yourok.torrserve.server.models.torrent.Torrent
import ru.yourok.torrserve.utils.SubtitlesMatcher
import ru.yourok.torrserve.utils.TorrentHelper
import java.io.File

object MagnetSeries {
    fun getIntent(pkg: String, torrent: Torrent, index: Int): Intent {
        val playable = TorrentHelper.getPlayableFiles(torrent)
        if (playable.isEmpty()) {
            return Intent()
        }

        val startIndex = playable.indexOfFirst { it.id == index }.takeIf { it >= 0 } ?: 0
        var remainingSubtitles = torrent.file_stats?.filter { TorrentHelper.isSubtitles(it.path) } ?: emptyList()
        val playLinkResolver: (Int) -> String = { idx ->
            TorrentHelper.getTorrentPlayLink(torrent, idx)
        }
        val fileItems = playable.map { f ->
            val file = File(f.path).name
            val base = file.substringBeforeLast('.')
            val subtitles = SubtitlesMatcher.findSubtitles(base, remainingSubtitles, playLinkResolver)
                .let { (matched, rest) ->
                    remainingSubtitles = rest
                    matched.map { subs ->
                        Bundle().apply {
                            putString("uri", subs.uri)
                            putString("mime", subs.mimeType)
                            putString("language", subs.language)
                            putString("label", subs.label)
                        }
                    }
                }
            Bundle().apply {
                putString("uri", TorrentHelper.getFileLink(torrent, f))
                putString("name", file)
                if (subtitles.isNotEmpty()) {
                    putParcelableArrayList("subtitles", ArrayList(subtitles))
                }
            }
        }

        return Intent(Intent.ACTION_VIEW).apply {
            setDataAndType("http://localhost/file.mp4".toUri(), "video/*")
            addFlags(Intent.FLAG_ACTIVITY_MULTIPLE_TASK)
            setPackage(pkg)
            putExtra("extra_playlist", Bundle().apply {
                putInt("start_index", startIndex)
                putParcelableArrayList("playlist_items", ArrayList(fileItems))
            })
        }
    }
}
