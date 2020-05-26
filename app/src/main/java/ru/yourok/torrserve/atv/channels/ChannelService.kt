package ru.yourok.torrserve.atv.channels

import android.util.Log
import ru.yourok.torrserve.BuildConfig
import ru.yourok.torrserve.R
import ru.yourok.torrserve.app.App
import ru.yourok.torrserve.atv.channels.providers.Provider

object ChannelService {
    fun updateChannels() {
        if (BuildConfig.DEBUG)
            Log.i("*****", "ChannelService: updateChannels")

        with(App.getContext()) {
            ChannelProvider(getString(R.string.torrents)).update(Provider.get(Provider.Torrents))
        }
    }
}