package ru.yourok.torrserve.ui.activities.play

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import ru.yourok.torrserve.R
import ru.yourok.torrserve.server.api.Api
import ru.yourok.torrserve.server.models.torrent.Torrent
import ru.yourok.torrserve.services.TorrService


class PlayActivity : AppCompatActivity() {
    var command: String = ""
    var torrentLink: String = ""
    var torrentHash: String = ""
    var torrentTitle: String = ""
    var torrentPoster: String = ""
    var torrentFileIndex: Int = 0 // from 1 to end


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.main_activity)
        setWindow()

        if (intent == null) {
            error(ErrIntentNull)
            return
        }

        TorrService.start()

        readArgs()
        processIntent()
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

    private fun processIntent() {
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
                    torrent = Api.addTorrent(torrentLink, torrentTitle, torrentPoster)
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

    private fun processPlay() {


    }

    private fun readArgs() {
        intent.data?.let {
            torrentLink = it.toString()
        }
        if (intent.action?.equals(Intent.ACTION_SEND) == true) {
            if (intent.getStringExtra(Intent.EXTRA_TEXT) != null)
                torrentLink = intent.getStringExtra(Intent.EXTRA_TEXT)
            if (intent.extras?.get(Intent.EXTRA_STREAM) != null)
                torrentLink = intent.extras?.get(Intent.EXTRA_STREAM)?.toString() ?: ""
        }

        intent?.extras?.apply {
            keySet().forEach { key ->
                when (key.toLowerCase()) {
                    "cmd" -> torrentTitle = this.getString(key) ?: ""
                    "hash" -> torrentHash = this.getString(key) ?: ""
                    "title" -> torrentTitle = this.getString(key) ?: ""
                    "poster" -> torrentPoster = this.getString(key) ?: ""
                    "fileindex" -> torrentFileIndex = this.getInt(key) ?: 0
                }
            }
        }
    }

    private fun successful(intent: Intent) {
        setResult(RESULT_OK, intent)
        finish()
    }

    private fun error(err: ReturnError) {
        val ret = Intent()
        ret.putExtra("errCode", err.errCode)
        ret.putExtra("errMessage", err.errMessage)
        setResult(RESULT_CANCELED, ret)
        finish()
    }

}