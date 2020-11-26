package ru.yourok.torrserve.ui.activities.play

import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import ru.yourok.torrserve.app.App
import ru.yourok.torrserve.server.api.Api
import ru.yourok.torrserve.server.api.Viewed
import ru.yourok.torrserve.server.models.torrent.Torrent
import ru.yourok.torrserve.ui.activities.play.players.Players
import ru.yourok.torrserve.ui.fragments.play.TorrentFilesFragment
import ru.yourok.torrserve.utils.TorrentHelper

object Play {

    fun PlayActivity.play(save: Boolean) {
        lifecycleScope.launch(Dispatchers.IO) {
            torrentSave = save

            //TODO сделать череz модель
            val result = load(this@play)
            if (result == null) {
                error(ErrLoadTorrent)
                return@launch
            }

            val torr = result.first
            val viewed = result.second
            val files = TorrentHelper.getPlayableFiles(torr)

            if (files.isEmpty()) {
                error(ErrLoadTorrentInfo)
                return@launch
            } else if (files.size == 1) {
                streamTorrent(torr, files.first().id)
            } else {
                TorrentFilesFragment().showTorrent(this@play, torr, viewed) { file ->
                    lifecycleScope.launch {
                        streamTorrent(torr, file.id)
                    }
                }
            }
        }
    }

    suspend fun load(activity: PlayActivity): Pair<Torrent, List<Viewed>>? {
        var torr: Torrent? = null
        var viewed: List<Viewed> = emptyList()
        activity.apply {
            lifecycleScope.launch(Dispatchers.IO) {
                // get torrent
                torr = loadTorrent(torrentLink, torrentHash, torrentTitle, torrentPoster, torrentSave)
                torr?.let {
                    torrentHash = it.hash
                    // start update info
                    infoFragment.startInfo(it.hash)
                    //get viewed
                    viewed = Api.listViewed(it.hash)
                    // wait torr info and change fragment
                    lifecycleScope.launch(Dispatchers.IO) {
                        torr = TorrentHelper.waitFiles(torrentHash) ?: return@launch
                    }.join()
                }
            }.join()
        }

        torr?.let {
            return it to viewed
        }
        return null
    }

    private fun loadTorrent(link: String, hash: String, title: String, poster: String, save: Boolean): Torrent? {
        if (hash.isNotEmpty())
            return Api.getTorrent(hash)
        else if (link.isNotEmpty())
            return Api.addTorrent(link, title, poster, save)
        return null
    }

    suspend fun streamTorrent(torrent: Torrent, index: Int) {
        var torr = torrent
        TorrentHelper.preloadTorrent(torr, index)
        delay(200)
        withContext(Dispatchers.IO) {
            torr = Api.getTorrent(torr.hash)
            while (torr.stat == TorrentHelper.TorrentSTPreload) {
                delay(200)
            }
        }

        val intent = Players.getIntent(torr, index)
        App.context.startActivity(intent)
    }
}