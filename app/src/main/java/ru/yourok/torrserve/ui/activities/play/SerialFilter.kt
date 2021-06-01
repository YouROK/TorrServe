package ru.yourok.torrserve.ui.activities.play

import android.content.Intent
import ru.yourok.torrserve.server.models.torrent.FileStat
import java.util.*

object SerialFilter {
    fun filter(intent: Intent, files: List<FileStat>): Int {
        var season = -1
        var episode = -1
        intent.extras?.apply {
            try {
                keySet().forEach {
                    when (it.lowercase(Locale.getDefault())) {
                        "season" -> season = this.getInt(it)
                        "episode" -> episode = this.getInt(it)
                    }
                }
            } catch (e: Exception) {
            }
        }

        if (season == -1 && episode == -1)
            return -1

        files.forEach {
            val name = it.path.lowercase(Locale.getDefault())
            if (
                name.contains("s${season.toString().padStart(2, '0')}e${episode.toString().padStart(2, '0')}") ||
                name.contains("e${episode.toString().padStart(2, '0')}s${season.toString().padStart(2, '0')}") ||
                name.contains("s${season}e${episode}") ||
                name.contains("e${season}s${episode}") ||
                (name.contains("s${season.toString().padStart(2, '0')}") &&
                        name.contains("e${episode.toString().padStart(2, '0')}")) ||
                (name.contains("e${season}") &&
                        name.contains("s${episode}"))
            )
                return it.id
        }
        return -1
    }
}