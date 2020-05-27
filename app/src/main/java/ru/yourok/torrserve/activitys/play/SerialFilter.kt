package ru.yourok.torrserve.activitys.play

import android.content.Intent
import ru.yourok.torrserve.server.api.JSObject

object SerialFilter {
    fun filter(intent: Intent, files: List<JSObject>): List<JSObject> {
        var season = -1
        var episode = -1
        intent.extras?.apply {
            try {
                keySet().forEach {
                    when (it.toLowerCase()) {
                        "season" -> season = this.getInt(it)
                        "episode" -> episode = this.getInt(it)
                    }
                }
            } catch (e: Exception) {
            }
        }

        if (season == -1 && episode == -1)
            return files

        val retList = mutableListOf<JSObject>()

        files.forEach {
            val name = it.get("Name", "").toLowerCase()
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
                retList.add(it)
        }
        if (retList.isEmpty())
            return files
        return retList
    }
}