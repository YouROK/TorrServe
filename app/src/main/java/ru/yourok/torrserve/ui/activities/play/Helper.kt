package ru.yourok.torrserve.ui.activities.play

import android.content.Intent
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import ru.yourok.torrserve.R
import ru.yourok.torrserve.server.api.Api
import ru.yourok.torrserve.server.models.torrent.Torrent
import ru.yourok.torrserve.settings.Settings
import ru.yourok.torrserve.ui.fragments.play.ChooserFragment

fun PlayActivity.readArgs() {
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
                "save" -> torrentSave = this.getBoolean(key) ?: false
            }
        }
    }
}

fun PlayActivity.successful(intent: Intent) {
    setResult(AppCompatActivity.RESULT_OK, intent)
    finish()
}

fun PlayActivity.error(err: ReturnError) {
    val ret = Intent()
    ret.putExtra("errCode", err.errCode)
    ret.putExtra("errMessage", err.errMessage)
    setResult(AppCompatActivity.RESULT_CANCELED, ret)
    finish()
}

suspend fun PlayActivity.processTorrent(onTorrent: suspend (Torrent?) -> Unit) {
    if (intent.hasExtra("action") && intent.getStringExtra("action") == "play") {
        onTorrent(getTorrent(false))
        return
    }

    when (Settings.getChooserAction()) {
        1 -> {//play
            onTorrent(getTorrent(false))
            return
        }
        2 -> {//add & play
            onTorrent(getTorrent(true))
            return
        }
        3 -> {//add
            getTorrent(true)
            finish()
            return
        }
    }

    val chFrag = ChooserFragment()
    chFrag.setOnResult {
        val action = (it as Int)
        if (action < 1 || action > 3) {
            onTorrent(null)
            return@setOnResult
        }

        when (action) {
            1 -> {//play
                onTorrent(getTorrent(false))
            }
            2 -> {//add & play
                onTorrent(getTorrent(true))
            }
            3 -> {//add
                getTorrent(true)
                finish()
            }
        }
    }

    chFrag.show(this, R.id.top_container)
}

suspend fun PlayActivity.getTorrent(save: Boolean): Torrent? {
    var torr: Torrent? = null
    lifecycleScope.launch(Dispatchers.IO) {
        if (torrentHash.isNotEmpty()) {
            torr = Api.getTorrent(torrentHash)
            return@launch
        }
        if (torrentLink.isNotEmpty()) {
            torr = Api.addTorrent(torrentLink, torrentTitle, torrentPoster, save)
            torrentHash = torr?.hash ?: return@launch
            return@launch
        }
    }.join()
    return torr
}