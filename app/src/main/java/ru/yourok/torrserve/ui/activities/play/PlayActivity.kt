package ru.yourok.torrserve.ui.activities.play

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import ru.yourok.torrserve.R
import ru.yourok.torrserve.server.api.Api
import ru.yourok.torrserve.server.models.torrent.Torrent
import ru.yourok.torrserve.services.TorrService
import ru.yourok.torrserve.ui.fragments.play.InfoFragment


class PlayActivity : AppCompatActivity() {
    var command: String = ""
    var torrentLink: String = ""
    var torrentHash: String = ""
    var torrentTitle: String = ""
    var torrentPoster: String = ""
    var torrentSave: Boolean = false
    var torrentFileIndex: Int = 0


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.play_activity)
        setWindow()

        if (intent == null) {
            error(ErrIntentNull)
            return
        }

        TorrService.start()

        readArgs()
        runBlocking {
            processIntent()
        }
    }

    private fun setWindow() {
        setFinishOnTouchOutside(false)
        val attr = window.attributes
        if (resources.displayMetrics.widthPixels <= resources.displayMetrics.heightPixels)
            attr.width = (resources.displayMetrics.widthPixels * 0.90).toInt()
        else if (resources.displayMetrics.widthPixels > resources.displayMetrics.heightPixels)
            attr.width = (resources.displayMetrics.widthPixels * 0.50).toInt()
        window.attributes = attr
    }

    private suspend fun processIntent() {
        if (command.isNotEmpty()) {
            //// Commands
            when (command.toLowerCase()) {
                "viewed" -> processViewed()
                "torrentinfo" -> processTorrentInfo()
                "torrentlist" -> processTorrentList()
                else -> error(ErrUnknownCmd)
            }
            return
        } else {
            //// Play torrent
            processPlay()
        }
    }

    private fun processViewed() {
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

    private fun processTorrentInfo() {
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

    private fun processTorrentList() {
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

    private suspend fun processPlay() {
        processTorrent {
            it?.let { torr ->
                InfoFragment(torr.hash).show(this, R.id.top_container)
            }
        }
    }
}