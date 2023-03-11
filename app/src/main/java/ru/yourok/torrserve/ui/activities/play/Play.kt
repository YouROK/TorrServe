package ru.yourok.torrserve.ui.activities.play

import android.content.Intent
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import ru.yourok.torrserve.R
import ru.yourok.torrserve.app.App
import ru.yourok.torrserve.server.api.Api
import ru.yourok.torrserve.server.api.Viewed
import ru.yourok.torrserve.server.models.torrent.Torrent
import ru.yourok.torrserve.ui.activities.play.players.Players
import ru.yourok.torrserve.ui.fragments.play.TorrentFilesFragment
import ru.yourok.torrserve.utils.TorrentHelper

object Play {

    fun PlayActivity.play(save: Boolean) {
        infoFragment.show(this, R.id.info_container)
        lifecycleScope.launch(Dispatchers.IO) {
            showProgress(-1)
            torrentSave = save

            val torrent: Torrent

            try {
                val torr = addTorrent(torrentHash, torrentLink, torrentTitle, torrentPoster, torrentData, torrentSave)
                    ?: let {
                        App.toast(getString(R.string.error_retrieve_data))
                        finish()
                        return@launch
                    }
                infoFragment.startInfo(torr.hash)
                if (torrentHash.isEmpty() && torr.hash.isNotBlank()) // store hash for Api.dropTorrent on close
                    torrentHash = torr.hash
                torrent = TorrentHelper.waitFiles(torr.hash) ?: let {
                    App.toast(getString(R.string.error_retrieve_torrent_info))
                    finish()
                    return@launch
                }
            } catch (e: Exception) {
                e.printStackTrace()
                App.toast(e.message ?: getString(R.string.error_retrieve_data))
                finish()
                return@launch
            }

            val viewed = try {
                Api.listViewed(torrent.hash)
            } catch (_: Exception) {
                emptyList()
            }
            val files = TorrentHelper.getPlayableFiles(torrent)

            if (intent.hasExtra("FileTemplate") && torrentFileIndex == -1) // For lostfilm app
                torrentFileIndex = SerialFilter.filter(intent, files)

            lifecycleScope.launch {
                when {
                    files.isEmpty() -> {
                        App.toast(getString(R.string.error_retrieve_torrent_file))
                        error(ErrLoadTorrentInfo)
                    }
                    files.size == 1 -> {
                        torrentFileIndex = 0
                        streamTorrent(torrent, files.first().id)
                        successful(Intent())
                    }
                    torrentFileIndex > 0 -> {
                        streamTorrent(torrent, torrentFileIndex)
                        successful(Intent())
                    }
                    else -> {
                        hideProgress()
                        TorrentFilesFragment().showTorrent(this@play, torrent, viewed) { file ->
                            torrentFileIndex = TorrentHelper.findIndex(torrent, file)
                            lifecycleScope.launch {
                                streamTorrent(torrent, file.id)
                            }
                        }
                    }
                }
            }
        }
    }

    private suspend fun PlayActivity.streamTorrent(torrent: Torrent, index: Int) {
        var torr = torrent
        TorrentHelper.preloadTorrent(torr, index)
        delay(200)
        withContext(Dispatchers.IO) {
            try {
                torr = Api.getTorrent(torr.hash)
            } catch (e: Exception) {
            }
            while (torr.stat == TorrentHelper.TorrentSTPreload) {
                delay(1000)
                try {
                    torr = Api.getTorrent(torr.hash)
                } catch (e: Exception) {
                }
            }
        }
//        ad?.waitAd()
        var intent: Intent? = null
        try {
            intent = Players.getIntent(torr, index)
        } catch (e: Exception) {
            e.message?.let { App.toast(it) }
        }
        intent?.let {
            it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            App.context.startActivity(it)
        }
    }
}