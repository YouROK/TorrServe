package ru.yourok.torrserve.ui.activities.play

import android.content.Intent
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import ru.yourok.torrserve.R
import ru.yourok.torrserve.app.App
import ru.yourok.torrserve.atv.channels.UpdaterCards
import ru.yourok.torrserve.server.api.Api
import ru.yourok.torrserve.server.models.torrent.Torrent
import ru.yourok.torrserve.ui.activities.play.players.Players
import ru.yourok.torrserve.ui.fragments.play.TorrentFilesFragment
import ru.yourok.torrserve.ui.fragments.play.viewmodels.TorrentViewModel
import ru.yourok.torrserve.utils.TorrentHelper

object Play {

    fun PlayActivity.play(save: Boolean) {
        UpdaterCards.updateCards()
        infoFragment.show(this, R.id.info_container)
        lifecycleScope.launch(Dispatchers.IO) {
            torrentSave = save

            val dataTorr = TorrentViewModel().loadTorrent(torrentLink, torrentHash, torrentTitle, torrentPoster, torrentSave)
            withContext(Dispatchers.Main) {
                dataTorr?.observe(this@play) { tr ->
                    lifecycleScope.launch(Dispatchers.IO) {
                        infoFragment.startInfo(tr.hash)

                        val viewed = Api.listViewed(tr.hash)
                        val torr = TorrentHelper.waitFiles(tr.hash) ?: let {
                            //TODO msg
                            return@launch
                        }

                        val files = TorrentHelper.getPlayableFiles(torr)
                        lifecycleScope.launch {
                            if (files.isEmpty())
                                error(ErrLoadTorrentInfo)
                            else if (files.size == 1) {
                                torrentFileIndex = 0
                                streamTorrent(torr, files.first().id)
                                successful(Intent())
                            } else if (torrentFileIndex > 0) {
                                streamTorrent(torr, torrentFileIndex)
                                successful(Intent())
                            } else {
                                TorrentFilesFragment().showTorrent(this@play, torr, viewed) { file ->
                                    torrentFileIndex = TorrentHelper.findIndex(torr, file)
                                    lifecycleScope.launch {
                                        streamTorrent(torr, file.id)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    suspend fun PlayActivity.streamTorrent(torrent: Torrent, index: Int) {
        var torr = torrent
        TorrentHelper.preloadTorrent(torr, index)
        delay(200)
        withContext(Dispatchers.IO) {
            torr = Api.getTorrent(torr.hash)
            while (torr.stat == TorrentHelper.TorrentSTPreload) {
                delay(200)
            }
        }

        ad?.waitAd()
        val intent = Players.getIntent(torr, index)
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        App.context.startActivity(intent)
    }
}