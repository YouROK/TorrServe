package ru.yourok.torrserve.ui.activities.play

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import kotlinx.coroutines.runBlocking
import ru.yourok.torrserve.R
import ru.yourok.torrserve.ad.AD
import ru.yourok.torrserve.server.api.Api
import ru.yourok.torrserve.services.TorrService
import ru.yourok.torrserve.settings.Settings
import ru.yourok.torrserve.ui.activities.play.Commands.processTorrentInfo
import ru.yourok.torrserve.ui.activities.play.Commands.processTorrentList
import ru.yourok.torrserve.ui.activities.play.Commands.processViewed
import ru.yourok.torrserve.ui.activities.play.Play.play
import ru.yourok.torrserve.ui.fragments.play.ChooserFragment
import ru.yourok.torrserve.ui.fragments.play.InfoFragment
import kotlin.concurrent.thread


class PlayActivity : AppCompatActivity() {
    var command: String = ""
    var torrentLink: String = ""
    var torrentHash: String = ""
    var torrentTitle: String = ""
    var torrentPoster: String = ""
    var torrentSave: Boolean = false
    var torrentFileIndex: Int = -1

    var ad: AD? = null
    val infoFragment = InfoFragment()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        //// DayNight Theme
        val apptheme = Settings.getTheme()
        when (apptheme) {
            "dark", "black" -> AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES)
            "light" -> AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)
            else -> AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM)
        }
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

    override fun onDestroy() {
        if (command.isNotEmpty())
            error(ErrUserStop)

        if (torrentHash.isNotEmpty())
            thread { Api.dropTorrent(torrentHash) }

        super.onDestroy()
    }

    private fun setWindow() {
        setFinishOnTouchOutside(false)
        val attr = window.attributes
        if (resources.displayMetrics.widthPixels <= resources.displayMetrics.heightPixels)
            attr.width = (resources.displayMetrics.widthPixels * 0.90).toInt()
        else if (resources.displayMetrics.widthPixels > resources.displayMetrics.heightPixels)
            attr.width = (resources.displayMetrics.widthPixels * 0.70).toInt()
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
            ad = AD(findViewById(R.id.ivAd), this)
            ad?.get()
            //// Play torrent
            processTorrent()
        }
    }

    private fun processTorrent() {
        if (intent.hasExtra("action") && intent.getStringExtra("action") == "play")
            play(false)
        else ChooserFragment().show(this) {
            when (it) {
                1, 2 -> {
                    play(it == 2)
                }
                3 -> {
                    addAndExit()
                }
            }
        }
    }
}