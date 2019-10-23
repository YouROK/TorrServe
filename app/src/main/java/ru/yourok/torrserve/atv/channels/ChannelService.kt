package ru.yourok.torrserve.atv.channels

import android.util.Log
import ru.yourok.torrserve.BuildConfig
import ru.yourok.torrserve.R
import ru.yourok.torrserve.app.App
import ru.yourok.torrserve.atv.channels.providers.Provider
import ru.yourok.torrserve.atv.channels.providers.Torrent
import kotlin.concurrent.thread

object ChannelService {
    fun updateChannels() {
        if (BuildConfig.DEBUG)
            Log.i("*****", "ChannelService: updateChannels")

        var torrents = emptyList<Torrent>()

        val thTorrent = thread {
            torrents = Provider.get(Provider.Torrents)
        }

        with(App.getContext()) {
            thTorrent.join()
            ChannelProvider(getString(R.string.torrents)).update(torrents)
        }
    }
}