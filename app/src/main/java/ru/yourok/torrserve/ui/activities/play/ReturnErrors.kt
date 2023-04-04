package ru.yourok.torrserve.ui.activities.play

import ru.yourok.torrserve.R
import ru.yourok.torrserve.app.App

data class ReturnError(val errCode: Int, val errMessage: String)

val ErrIntentNull = ReturnError(-1, "intent is null")
val ErrUnknownCmd = ReturnError(-2, "unknown command")
val ErrProcessCmd = ReturnError(-3, "error when processing cmd, see logcat")
val ErrEmptyTorrent = ReturnError(-4, "torrent link empty")
val ErrLoadTorrent = ReturnError(-5, App.context.getString(R.string.error_add_torrent))
val ErrLoadTorrentInfo = ReturnError(-6, App.context.getString(R.string.error_retrieve_torrent_info))
val ErrUserStop = ReturnError(-7, "user stop")
val ErrTorrServerNotResponding = ReturnError(-8, App.context.getString(R.string.server_not_responding))
val ErrLoadTorrentFiles = ReturnError(-9, App.context.getString(R.string.error_retrieve_torrent_file))
