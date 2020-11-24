package ru.yourok.torrserve.ui.activities.play

import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import ru.yourok.torrserve.R
import ru.yourok.torrserve.server.api.Api
import ru.yourok.torrserve.server.models.torrent.Torrent
import ru.yourok.torrserve.ui.fragments.play.InfoFragment
import ru.yourok.torrserve.utils.TorrentHelper

object LoadTorrent {

    suspend fun load(activity: PlayActivity): Torrent? {
        var torr: Torrent? = null
        activity.apply {
            lifecycleScope.launch {
                // show info
                val info = InfoFragment()
                info.show(this@apply, R.id.top_container)
                // get torrent
                withContext(Dispatchers.IO) {
                    torr = getTorrent(torrentLink, torrentHash, torrentTitle, torrentPoster, torrentSave)
                    torr?.let {
                        torrentHash = it.hash
                        // start update info
                        info.startInfo(it.hash)
                        // wait torr info and change fragment
                        lifecycleScope.launch(Dispatchers.IO) {
                            TorrentHelper.waitInfo(torrentHash)
                        }.join()
                    }
                }
            }.join()
        }
        return torr
    }

    private fun getTorrent(link: String, hash: String, title: String, poster: String, save: Boolean): Torrent? {
        val magnet =
            if (hash.isNotEmpty())
                hash
            else if (link.isNotEmpty())
                link
            else
                return null

        val torr = Api.addTorrent(magnet, title, poster, save)
        return torr
    }

}