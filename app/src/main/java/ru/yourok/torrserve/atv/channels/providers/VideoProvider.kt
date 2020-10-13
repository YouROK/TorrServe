package ru.yourok.torrserve.atv.channels.providers


open class VideoProvider {
    open fun get(): List<Torrent> = emptyList()
}

object Provider {
    const val Torrents = "torrents"

    private val providers = mapOf<String, VideoProvider>(
            Torrents to Torrents()
    )

    fun get(name: String): List<Torrent> {
        try {
            return providers[name]?.get() ?: emptyList()
        } catch (e: Exception) {
            return emptyList()
        }
    }
}
