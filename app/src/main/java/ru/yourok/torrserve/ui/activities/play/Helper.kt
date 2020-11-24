package ru.yourok.torrserve.ui.activities.play

import android.content.Intent
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import ru.yourok.torrserve.server.api.Api

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

fun PlayActivity.addAndExit() {
    lifecycleScope.launch(Dispatchers.IO) {
        Api.addTorrent(torrentLink, torrentTitle, torrentPoster, true)
        finish()
    }
}