package ru.yourok.torrserve.atv.channels.providers

import ru.yourok.torrserve.server.api.Api
import ru.yourok.torrserve.server.models.torrent.Torrent

class Torrents : VideoProvider() {
    override fun get(): List<Torrent> {
        if (Api.echo().isNotEmpty())
            return Api.listTorrent()
        return emptyList()
    }
}