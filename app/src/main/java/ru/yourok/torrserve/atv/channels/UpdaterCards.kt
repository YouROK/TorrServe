package ru.yourok.torrserve.atv.channels

import android.os.Build
import ru.yourok.torrserve.atv.Utils
import kotlin.concurrent.thread

object UpdaterCards {

    private val lock = Any()
    private var isUpdate = false

    fun updateCards() {
        thread {
            if (!Utils.isGoogleTV())
                return@thread

            synchronized(lock) {
                if (isUpdate)
                    return@thread
                isUpdate = true
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                ChannelService.updateChannels()
            isUpdate = false
        }
    }
}