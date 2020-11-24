package ru.yourok.torrserve.ui.activities.play

import android.content.Intent
import androidx.lifecycle.lifecycleScope
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import ru.yourok.torrserve.server.api.Api
import ru.yourok.torrserve.server.models.torrent.Torrent

object Commands {
    fun PlayActivity.processViewed() {
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val viewed = Api.listViewed(torrentHash)
                val intent = Intent()
                intent.putExtra("result", Gson().toJson(viewed))
                successful(intent)
            } catch (e: Exception) {
                e.printStackTrace()
                error(ErrProcessCmd)
            }
        }
    }

    fun PlayActivity.processTorrentInfo() {
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                var torrent: Torrent? = null
                if (torrentHash.isNotEmpty())
                    torrent = Api.getTorrent(torrentHash)
                if (torrent == null && torrentLink.isNotEmpty())
                    torrent = Api.addTorrent(torrentLink, torrentTitle, torrentPoster, false)
                if (torrent != null) {
                    val intent = Intent()
                    intent.putExtra("result", Gson().toJson(torrent))
                    successful(intent)
                }
            } catch (e: Exception) {
                e.printStackTrace()
                error(ErrProcessCmd)
            }
        }
    }

    fun PlayActivity.processTorrentList() {
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val list = Api.listTorrent()
                val intent = Intent()
                intent.putExtra("result", Gson().toJson(list))
                successful(intent)
            } catch (e: Exception) {
                e.printStackTrace()
                error(ErrProcessCmd)
            }
        }
    }
}