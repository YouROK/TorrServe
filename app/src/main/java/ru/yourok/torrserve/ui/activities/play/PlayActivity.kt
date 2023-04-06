package ru.yourok.torrserve.ui.activities.play

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.TextView
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isVisible
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.lifecycleScope
import com.google.android.material.progressindicator.LinearProgressIndicator
import com.google.firebase.analytics.FirebaseAnalytics
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import ru.yourok.torrserve.BuildConfig
import ru.yourok.torrserve.R
import ru.yourok.torrserve.ad.AD
import ru.yourok.torrserve.app.App
import ru.yourok.torrserve.server.api.Api
import ru.yourok.torrserve.server.local.TorrService
import ru.yourok.torrserve.settings.Settings
import ru.yourok.torrserve.ui.activities.main.MainActivity
import ru.yourok.torrserve.ui.activities.play.Play.play
import ru.yourok.torrserve.ui.fragments.donate.DonateFragment
import ru.yourok.torrserve.ui.fragments.main.servfinder.ServerFinderFragment
import ru.yourok.torrserve.ui.fragments.play.ChooserFragment
import ru.yourok.torrserve.ui.fragments.play.InfoFragment
import ru.yourok.torrserve.utils.ThemeUtil
import kotlin.concurrent.thread


class PlayActivity : AppCompatActivity() {
    var torrentLink: String = ""
    var torrentHash: String = ""
    var torrentTitle: String = ""
    var torrentPoster: String = ""
    var torrentData: String = ""
    var torrentSave: Boolean = false
    var torrentFileIndex: Int = -1

    private var userClose = false

    var ad: AD? = null
    val infoFragment = InfoFragment()

    private var firebaseAnalytics: FirebaseAnalytics? = null

    private val onBackPressedCallback: OnBackPressedCallback = object : OnBackPressedCallback(true) {
        override fun handleOnBackPressed() {
            if (BuildConfig.DEBUG) Log.d("PlayActivity", "handleOnBackPressed() set userClose = true")
            userClose = true
            finish()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        //// DayNight Theme // AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM)
        when (Settings.getTheme()) {
            "dark" -> setTheme(R.style.PlayDialog_Dark)
            "light" -> setTheme(R.style.PlayDialog_Light)
            "black" -> setTheme(R.style.PlayDialog_Black)
            else -> setTheme(R.style.PlayDialog_DayNight)
        }
        setContentView(R.layout.play_activity)
        setWindow()

        onBackPressedDispatcher.addCallback(this, onBackPressedCallback)

        lifecycleScope.launch { showProgress() }

        firebaseAnalytics = FirebaseAnalytics.getInstance(this)

        if (intent == null) {
            error(ErrIntentNull)
            return
        }

        TorrService.start()

    }

    override fun onResume() {
        super.onResume()
        readArgs()
        lifecycleScope.launch(Dispatchers.IO) {
            if (!TorrService.wait(5)) {
                error(ErrTorrServerNotResponding)
                delay(App.longToastDuration.toLong())
                // TODO: implement proper reload on Back/Apply
                ServerFinderFragment().show(App.currentActivity() as? FragmentActivity?, R.id.container, true)
            } else {
                withContext(Dispatchers.Main) {
                    processIntent()
                }
            }
        }
    }

    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        if (BuildConfig.DEBUG) Log.d("PlayActivity", "onUserLeaveHint()")
        lifecycleScope.cancel()
        finish() // back from player to files list
    }

    override fun onDestroy() {
        if (userClose) {
            if (torrentHash.isNotEmpty())
                thread {
                    try {
                        if (BuildConfig.DEBUG) Log.d("PlayActivity", "onDestroy() drop torrent $torrentHash")
                        Api.dropTorrent(torrentHash)
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
        }
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
        ad = AD(findViewById(R.id.ivAd), this)
        ad?.get()
        //// Play torrent
        if (intent.hasExtra("action") && intent.getStringExtra("action") == "play")
            play(false)
        else {
            lifecycleScope.launch { hideProgress() }
            if (App.inForeground) {
                val isForceChoose = (intent.hasExtra("action") && intent.getStringExtra("action") == "choose")
                ChooserFragment().show(this, isForceChoose) {
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
    }

    suspend fun showProgress(prc: Int = -1) = withContext(Dispatchers.Main) {
        if (isActive) {
            val progress = findViewById<LinearProgressIndicator>(R.id.progressBar)
            val color = ThemeUtil.getColorFromAttr(this@PlayActivity, R.attr.colorAccent)
            val indeterminate = progress.isIndeterminate
            val visible = progress.isVisible
            progress?.apply {
                setIndicatorColor(color)
                // https://material.io/components/progress-indicators/android
                if (prc < 0 && !indeterminate) {
                    visibility = View.INVISIBLE
                    isIndeterminate = true
                } else if (!indeterminate) {
                    isIndeterminate = false
                }
                if (!visible)
                    visibility = View.VISIBLE
                setProgressCompat(prc, true)
            }
        }
    }

    suspend fun hideTitle() = withContext(Dispatchers.Main) {
        if (isActive)
            findViewById<TextView>(R.id.info_title)?.visibility = View.GONE
    }

    suspend fun hideProgress() = withContext(Dispatchers.Main) {
        if (isActive)
            findViewById<LinearProgressIndicator>(R.id.progressBar)?.visibility = View.INVISIBLE
    }

}