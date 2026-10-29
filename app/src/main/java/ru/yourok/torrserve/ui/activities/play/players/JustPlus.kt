package ru.yourok.torrserve.ui.activities.play.players

import android.app.PendingIntent
import android.content.Intent
import android.os.Bundle
import androidx.core.net.toUri
import org.json.JSONObject
import ru.yourok.torrserve.app.App
import ru.yourok.torrserve.server.api.Viewed
import ru.yourok.torrserve.server.local.services.PlaybackPositionReceiver
import ru.yourok.torrserve.server.local.services.PlaybackPositionReceiver.PlaybackState
import ru.yourok.torrserve.server.models.torrent.Torrent
import ru.yourok.torrserve.utils.Net
import ru.yourok.torrserve.utils.SubtitlesMatcher
import ru.yourok.torrserve.utils.TorrentHelper
import java.io.File
import java.util.Locale

// Just+ Player nested playlist API
// https://github.com/just-plus-player/just-plus-player/blob/master/docs/PLAYLIST_API.md
object JustPlus {
    /** Player id of the results parser; beta builds add a suffix to the package. */
    const val PACKAGE = "com.justplus.player"
    private val PREFIXES = listOf(PACKAGE, "com.lampaua.player")

    private const val WATCHED_PERCENT = 96 // Lampa's VIDEO_COMPLETED_DURATION_MAX_PERCENTAGE
    private const val TMDB_IMG = "https://image.tmdb.org/t/p"
    // Strict S01E02 only, not glued to a word or a number; anything looser misreads release names
    private val SEASON_EPISODE = Regex("""(?i)(?<![a-z0-9])s(\d{1,2})e(\d{1,3})(?![0-9])""")
    // Lampa card ids are TMDB ids only for these sources (an empty one is an older Lampa)
    private val TMDB_SOURCES = setOf("", "tmdb", "cub")

    // The API asks to keep the intent well under 500 KB (Binder): a long anime or a discography
    // is cut to a window around the chosen file
    private const val MAX_ITEMS = 200

    fun isJustPlus(pkg: String) = PREFIXES.any { pkg.startsWith(it, true) }

    /**
     * [callback] is the one provided by the app that called TorrServe, so the player reports to it;
     * without it the player reports to TorrServe itself.
     * A playlist launch never uses the player's own resume store: items resume from [viewed].
     */
    fun getIntent(
        pkg: String,
        torrent: Torrent,
        index: Int,
        callback: PendingIntent? = null,
        viewed: List<Viewed> = emptyList()
    ): Intent {
        // A file outside the playable list (a short .ts, an audio track of a video torrent) plays alone
        val selected = TorrentHelper.findFile(torrent, index)
        val all = TorrentHelper.getPlayableFiles(torrent)
            .takeIf { list -> selected == null || list.any { it.id == selected.id } }
            ?: listOfNotNull(selected)
        val from = (all.indexOfFirst { it.id == index } - MAX_ITEMS / 2)
            .coerceAtMost(all.size - MAX_ITEMS).coerceAtLeast(0)
        val playable = all.drop(from).take(MAX_ITEMS)
        if (playable.isEmpty()) {
            return Intent()
        }

        val startIndex = playable.indexOfFirst { it.id == index }.takeIf { it >= 0 } ?: 0
        val position = { id: Int -> viewed.find { it.file_index == id }?.timecode?.toInt() ?: 0 }
        var remainingSubtitles = torrent.file_stats?.filter { TorrentHelper.isSubtitles(it.path) } ?: emptyList()
        val playLinkResolver: (Int) -> String = { idx ->
            TorrentHelper.getTorrentPlayLink(torrent, idx)
        }
        // Lampa saves its card as {"lampa":true,"movie":{...}}; other apps save anything or nothing
        val movie = runCatching { JSONObject(torrent.data!!).optJSONObject("movie") }.getOrNull()
        val isSeries = torrent.category == "tv" || movie?.has("first_air_date") == true
        val title = (if (isSeries) listOf("name", "title") else listOf("title", "name"))
            .firstNotNullOfOrNull { k -> movie?.optString(k)?.takeIf { it.isNotEmpty() } }
            ?: torrent.title.removePrefix("[LAMPA] ").trim().ifEmpty { torrent.name }
        val tmdbId = movie?.optString("id")?.takeIf {
            it.isNotEmpty() && it.all(Char::isDigit) && movie.optString("source").lowercase() in TMDB_SOURCES
        }
        val imdbId = (movie?.optString("imdb_id")?.takeIf { it.isNotEmpty() }
            ?: movie?.optJSONObject("external_ids")?.optString("imdb_id"))?.takeIf { it.startsWith("tt") }
        // Lampa's own pick (its language), else the widest logo in the device language, else in English
        val logos = movie?.optJSONObject("images")?.optJSONArray("logos")
            ?.let { a -> (0 until a.length()).mapNotNull { a.optJSONObject(it) } }.orEmpty()
        val logo = movie?.optString("logo_url")?.takeIf { it.startsWith("http") }
            ?.replace("//t/p/w300/", "/t/p/w500/")
            ?: listOf(Locale.getDefault().language, "en").firstNotNullOfOrNull { lang ->
                logos.filter { it.optString("iso_639_1") == lang }.maxByOrNull { it.optDouble("aspect_ratio") }
            }?.optString("file_path")?.let { "$TMDB_IMG/w500$it" }
        // The best-voted backdrop without lettering, else the card's own backdrop
        val backdrops = movie?.optJSONObject("images")?.optJSONArray("backdrops")
            ?.let { a -> (0 until a.length()).mapNotNull { a.optJSONObject(it) } }.orEmpty()
        val background = (backdrops.filter { it.optString("iso_639_1").let { l -> l.isEmpty() || l == "null" } }
            .maxByOrNull { it.optDouble("vote_average") }?.optString("file_path")
            ?: movie?.optString("backdrop_path"))?.takeIf { it.startsWith("/") }
            ?.let { "$TMDB_IMG/w1280$it" }
            ?: movie?.optString("background_image")?.takeIf { it.startsWith("http") }
        // Season/episode all or nothing: every file marked, no pair twice, and not a known film.
        // A half-marked or odd torrent keeps file names, a wrong number is worse than none
        val seasonEpisodes = playable.map { f ->
            SEASON_EPISODE.find(File(f.path).nameWithoutExtension)
                ?.let { it.groupValues[1].toInt() to it.groupValues[2].toInt() }
        }.takeIf { se ->
            (isSeries || movie == null && torrent.category != "movie") &&
                    se.all { it != null } && se.distinct().size == se.size
        }
        val items = playable.mapIndexed { i, f ->
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
                val se = seasonEpisodes?.get(i)
                if (se != null) {
                    putInt("season", se.first)
                    putInt("episode", se.second)
                } else if (playable.size > 1) putString("episode_title", base)
                // No backdrop (not from Lampa, or no TMDB images): the torrent's poster is the only picture
                if (background == null) torrent.poster?.takeIf { it.startsWith("http") }
                    ?.let { putString("thumbnail", it) }
                position(f.id).takeIf { it > 0 }?.let { putInt("position_sec", it) }
                tmdbId?.let { putString("tmdb_id", it) }
                imdbId?.let { putString("imdb_id", it) }
                if (subtitles.isNotEmpty()) {
                    putParcelableArray("subtitles", subtitles.toTypedArray())
                }
            }
        }

        return Intent(Intent.ACTION_VIEW).apply {
            // informational only: the player plays items[start_index]
            setDataAndType(TorrentHelper.getFileLink(torrent, playable[startIndex]).toUri(), "video/*")
            setPackage(pkg)
            // a Just+ older than the playlist API plays the start file alone, under this name
            putExtra("title", title)
            putExtra("playlist", Bundle().apply {
                putString("title", title)
                logo?.let { putString("logo", it) }
                background?.let { putString("background", it) }
                putInt("start_index", startIndex)
                // TorrServe has no resume setting: the player asks Resume / Start over for the start item
                if (position(playable[startIndex].id) > 0) putBoolean("ask_resume", true)
                // TorrServer with a login: the player fetches the stream itself
                Net.getAuthB64().takeIf { it.isNotEmpty() }
                    ?.let { putStringArray("headers", arrayOf("Authorization", it)) }
                putParcelableArray("items", items.toTypedArray())
                // Reports on every leave (Home included)
                putParcelable(
                    "result_callback",
                    callback ?: PlaybackPositionReceiver.pendingIntent(App.context, PACKAGE, torrent.hash, playable.map { it.id })
                )
            })
        }
    }

    /**
     * Last position of every item opened in the session (-1 = never opened). An item played past 96%
     * (one auto-next passed reports its full duration) is watched and starts over next time, as in Lampa.
     */
    fun parseResult(intent: Intent): List<PlaybackState> {
        // cancelled before start / error: nothing was watched
        if (intent.getStringExtra("end_by") !in setOf("completion", "user", "interval")) return emptyList()
        val positions = intent.getLongArrayExtra("positions_ms")
            ?: intent.getIntArrayExtra("positions_sec")?.map { if (it < 0) -1L else it * 1000L }?.toLongArray()
            ?: return emptyList()
        // Durations come per visit in history; the last known one of each item wins
        val durations = LongArray(positions.size)
        @Suppress("DEPRECATION")
        intent.getParcelableArrayExtra("history")?.forEach { visit ->
            val v = visit as? Bundle ?: return@forEach
            val i = v.getInt("index", -1)
            val d = v.getInt("duration_sec", 0) * 1000L
            if (i in durations.indices && d > 0) durations[i] = d
        }
        val index = intent.getIntExtra("index", -1)
        val duration = intent.getIntExtra("duration_sec", 0) * 1000L
        if (index in durations.indices && duration > 0) durations[index] = duration
        return positions.indices.filter { positions[it] >= 0 }.map { i ->
            val watched = durations[i] > 0 && positions[i] >= durations[i] * WATCHED_PERCENT / 100
            PlaybackState(i, if (watched) 0 else positions[i], durations[i], 0)
        }
    }
}
