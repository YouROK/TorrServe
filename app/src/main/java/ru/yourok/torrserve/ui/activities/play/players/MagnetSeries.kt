package ru.yourok.torrserve.ui.activities.play.players

import android.app.PendingIntent
import android.content.Intent
import android.os.Bundle
import androidx.core.net.toUri
import androidx.core.os.BundleCompat
import ru.yourok.torrserve.app.App
import ru.yourok.torrserve.server.api.Viewed
import ru.yourok.torrserve.server.local.services.PlaybackPositionReceiver
import ru.yourok.torrserve.server.local.services.PlaybackPositionReceiver.PlaybackState
import ru.yourok.torrserve.server.models.torrent.FileStat
import ru.yourok.torrserve.server.models.torrent.Torrent
import ru.yourok.torrserve.utils.SubtitlesMatcher
import ru.yourok.torrserve.utils.TorrentHelper
import java.io.File

object MagnetSeries {
    const val PACKAGE = "com.github.magnetseries.atv"

    /** Extra with the [PendingIntent] the player fires with its playback session on exit. */
    private const val KEY_PLAYBACK_CALLBACK = "extra_playback_callback"
    private const val KEY_PLAYBACK_SESSION = "playback_session"
    private const val KEY_PLAYBACK_ITEMS = "playback_items"
    private const val KEY_TIMESTAMP = "timestamp"
    private const val KEY_INDEX = "index"
    private const val KEY_POSITION = "position"
    private const val KEY_DURATION = "duration"

    /** Request type telling the player its items are the files of a Blu-ray disc rip. */
    private const val BLURAY_MIME_TYPE = "application/vnd.magnetseries.bluray"

    /**
     * [callback] is the one provided by the app that called TorrServe, so the player reports to it;
     * without it the player reports to TorrServe itself.
     * The starting file resumes from its position in [viewed]; the player asks whether to resume.
     */
    fun getIntent(
        pkg: String,
        torrent: Torrent,
        index: Int,
        callback: PendingIntent? = null,
        viewed: List<Viewed> = emptyList()
    ): Intent {
        TorrentHelper.findDisc(torrent, index)?.let { disc ->
            return getDiscIntent(pkg, torrent, index, disc, callback, viewed)
        }
        val playable = TorrentHelper.getPlayableFiles(torrent)
        if (playable.isEmpty()) {
            return Intent()
        }

        val startIndex = playable.indexOfFirst { it.id == index }.takeIf { it >= 0 } ?: 0
        val startPosition = viewed.find { it.file_index == playable[startIndex].id }?.timecode ?: 0.0
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
            setPackage(pkg)
            putPlaylist(torrent, playable, fileItems, startIndex, startPosition, callback)
        }
    }

    /**
     * A disc rip plays as a disc: the player gets the disc files with their paths and opens the title
     * menu, or resumes the title (`.mpls`) left part-way. History is reported under the title's index.
     */
    private fun getDiscIntent(
        pkg: String,
        torrent: Torrent,
        index: Int,
        disc: List<FileStat>,
        callback: PendingIntent?,
        viewed: List<Viewed>
    ): Intent {
        val fileItems = disc.map { f ->
            Bundle().apply {
                putString("uri", TorrentHelper.getFileLink(torrent, f))
                putString("name", File(f.path).name)
                putString("path", f.path)
            }
        }
        // several titles may have positions, the longest one is most likely the feature
        val resume = disc.withIndex()
            .filter { TorrentHelper.isDiscPlaylist(it.value.path) }
            .mapNotNull { (idx, f) -> viewed.find { it.file_index == f.id }?.timecode?.takeIf { it > 0 }?.let { idx to it } }
            .maxByOrNull { it.second }
        // any other disc item opens the title menu; index.bdmv is not one, so fall back to the largest stream
        val startIndex = resume?.first
            ?: disc.indexOfFirst { it.id == index }.takeIf { it >= 0 }
            ?: disc.indices.maxBy { disc[it].length }
        return Intent(Intent.ACTION_VIEW).apply {
            type = BLURAY_MIME_TYPE
            setPackage(pkg)
            putPlaylist(torrent, disc, fileItems, startIndex, resume?.second ?: 0.0, callback)
        }
    }

    private fun Intent.putPlaylist(
        torrent: Torrent,
        files: List<FileStat>,
        fileItems: List<Bundle>,
        startIndex: Int,
        startPosition: Double,
        callback: PendingIntent?
    ) {
        addFlags(Intent.FLAG_ACTIVITY_MULTIPLE_TASK)
        putExtra("extra_playlist", Bundle().apply {
            putInt("start_index", startIndex)
            putLong("start_position", (startPosition * 1000).toLong()) // ms
            putParcelableArrayList("playlist_items", ArrayList(fileItems))
        })
        putExtra(
            KEY_PLAYBACK_CALLBACK,
            callback ?: PlaybackPositionReceiver.pendingIntent(App.context, PACKAGE, torrent.hash, files.map { it.id })
        )
    }

    /** Last states of the playback session the player filled into the callback intent. */
    fun parseResult(intent: Intent): List<PlaybackState> {
        val session = intent.getBundleExtra(KEY_PLAYBACK_SESSION) ?: return emptyList()
        val history = BundleCompat.getParcelableArrayList(session, KEY_PLAYBACK_ITEMS, Bundle::class.java)
            ?: return emptyList()
        return history.map {
            PlaybackState(
                index = it.getInt(KEY_INDEX, -1),
                position = it.getLong(KEY_POSITION),
                duration = it.getLong(KEY_DURATION),
                timestamp = it.getLong(KEY_TIMESTAMP)
            )
        }
    }
}
