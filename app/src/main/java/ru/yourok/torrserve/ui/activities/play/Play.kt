package ru.yourok.torrserve.ui.activities.play

import android.content.ContentResolver
import android.content.Intent
import android.net.Uri
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import ru.yourok.torrserve.R
import ru.yourok.torrserve.app.App
import ru.yourok.torrserve.server.api.Api
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
                val torr = if (torrentHash.isNotEmpty())
                    Api.getTorrent(torrentHash)
                else if (torrentLink.isNotEmpty())
                    loadLink(torrentLink, torrentTitle, torrentPoster, torrentData, torrentSave)
                else {
                    App.Toast(getString(R.string.error_retrieve_data))
                    finish()
                    return@launch
                }
                infoFragment.startInfo(torr.hash)
                torrent = TorrentHelper.waitFiles(torr.hash) ?: let {
                    App.Toast(getString(R.string.error_retrieve_torrent_info))
                    finish()
                    return@launch
                }
            } catch (e: Exception) {
                e.printStackTrace()
                App.Toast(e.message ?: getString(R.string.error_retrieve_data))
                finish()
                return@launch
            }

            val viewed = Api.listViewed(torrent.hash)
            val files = TorrentHelper.getPlayableFiles(torrent)

            if (intent.hasExtra("FileTemplate") && torrentFileIndex == -1) // For lostfilm app
                torrentFileIndex = SerialFilter.filter(intent, files)

            lifecycleScope.launch {
                if (files.isEmpty()) {
                    App.Toast(getString(R.string.error_retrieve_torrent_file))
                    error(ErrLoadTorrentInfo)
                } else if (files.size == 1) {
                    torrentFileIndex = 0
                    streamTorrent(torrent, files.first().id)
                    successful(Intent())
                } else if (torrentFileIndex > 0) {
                    streamTorrent(torrent, torrentFileIndex)
                    successful(Intent())
                } else {
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

    suspend fun PlayActivity.streamTorrent(torrent: Torrent, index: Int) {
        var torr = torrent
        TorrentHelper.preloadTorrent(torr, index)
        delay(200)
        withContext(Dispatchers.IO) {
            torr = Api.getTorrent(torr.hash)
            while (torr.stat == TorrentHelper.TorrentSTPreload) {
                delay(1000)
                torr = Api.getTorrent(torr.hash)
            }
        }

        ad?.waitAd()
        val intent = Players.getIntent(torr, index)
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        App.context.startActivity(intent)
    }

    private fun loadLink(link: String, title: String, poster: String, data: String, save: Boolean): Torrent {
        val scheme = Uri.parse(link).scheme
        if (ContentResolver.SCHEME_ANDROID_RESOURCE == scheme || ContentResolver.SCHEME_FILE == scheme) {
            return uploadFile(link, title, poster, data, save)
        } else
            return Api.addTorrent(link, title, poster, data, save)
    }

    private fun uploadFile(link: String, title: String, poster: String, data: String, save: Boolean): Torrent {
        val fis = App.context.contentResolver.openInputStream(Uri.parse(link))
        return Api.uploadTorrent(fis, title, poster, data, save)
    }
}