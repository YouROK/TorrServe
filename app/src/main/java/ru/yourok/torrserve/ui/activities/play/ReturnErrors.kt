package ru.yourok.torrserve.ui.activities.play

import ru.yourok.torrserve.R
import ru.yourok.torrserve.app.App

data class ReturnError(val errCode: Int, val errMessage: String)

val ErrIntentNull = ReturnError(-1, App.context.getString(R.string.error_intent_null))
val ErrUnknownCmd = ReturnError(-2, App.context.getString(R.string.error_unknown_command))
val ErrProcessIntent = ReturnError(-3, App.context.getString(R.string.error_process_intent))
val ErrEmptyTorrent = ReturnError(-4, App.context.getString(R.string.error_empty_link))
val ErrLoadTorrent = ReturnError(-5, App.context.getString(R.string.error_add_torrent))
val ErrLoadTorrentInfo = ReturnError(-6, App.context.getString(R.string.error_retrieve_torrent_info))
val ErrUserStop = ReturnError(-7, App.context.getString(R.string.error_user_cancel))
val ErrTorrServerNotResponding = ReturnError(-8, App.context.getString(R.string.server_not_responding))
val ErrLoadTorrentFiles = ReturnError(-9, App.context.getString(R.string.error_retrieve_torrent_file))
