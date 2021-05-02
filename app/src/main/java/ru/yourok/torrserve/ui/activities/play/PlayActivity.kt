package ru.yourok.torrserve.ui.activities.play

import android.graphics.PorterDuff
import android.os.Build
import android.os.Bundle
import android.view.View
import android.widget.ProgressBar
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.google.firebase.analytics.FirebaseAnalytics
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import ru.yourok.torrserve.R
import ru.yourok.torrserve.ad.AD
import ru.yourok.torrserve.app.App
import ru.yourok.torrserve.server.api.Api
import ru.yourok.torrserve.services.TorrService
import ru.yourok.torrserve.settings.Settings
import ru.yourok.torrserve.ui.activities.play.Play.play
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

    var userClose = false

    var ad: AD? = null
    val infoFragment = InfoFragment()

    private var firebaseAnalytics: FirebaseAnalytics? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        //// DayNight Theme // AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM)
        val apptheme = Settings.getTheme()
        when (apptheme) {
            "dark" -> setTheme(R.style.PlayDialog_Dark)
            "light" -> setTheme(R.style.PlayDialog_Light)
            "black" -> setTheme(R.style.PlayDialog_Black)
            else -> setTheme(R.style.PlayDialog_DayNight)
        }
        setContentView(R.layout.play_activity)
        setWindow()

        findViewById<ProgressBar>(R.id.progressBar)?.apply {
            progressDrawable?.setColorFilter(ThemeUtil.getColorFromAttr(this@PlayActivity, R.attr.colorAccent), PorterDuff.Mode.SRC_IN)
            indeterminateDrawable?.setColorFilter(ThemeUtil.getColorFromAttr(this@PlayActivity, R.attr.colorAccent), PorterDuff.Mode.SRC_IN)
        }

        lifecycleScope.launch { showProgress() }

        firebaseAnalytics = FirebaseAnalytics.getInstance(this)

        if (intent == null) {
            error(ErrIntentNull)
            return
        }

        TorrService.start()

        readArgs()
        lifecycleScope.launch(Dispatchers.IO) {
            if (!TorrService.wait(5)) {
                App.Toast(R.string.server_not_responding)
                error(ErrTorrServerNotResponding)
            }
            withContext(Dispatchers.Main) {
                processIntent()
            }
        }
    }

    override fun onDestroy() {
        if (userClose) {
            if (torrentHash.isNotEmpty())
                thread {
                    try {
                        Api.dropTorrent(torrentHash)
                    } catch (e: Exception) {
                        // TODO: notify user
                    }
                }
        }

        super.onDestroy()
    }

    override fun onBackPressed() {
        super.onBackPressed()
        userClose = true
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
            ChooserFragment().show(this) {
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

    suspend fun showProgress(prog: Int = -1) = withContext(Dispatchers.Main) {
        if (isActive) {
            val progress = findViewById<ProgressBar>(R.id.progressBar)
            progress?.progressDrawable?.setColorFilter(
                ThemeUtil.getColorFromAttr(this@PlayActivity, R.attr.colorAccent), PorterDuff.Mode.SRC_IN
            )
            progress?.indeterminateDrawable?.setColorFilter(
                ThemeUtil.getColorFromAttr(this@PlayActivity, R.attr.colorAccent), PorterDuff.Mode.SRC_IN
            )
            progress?.apply {
                visibility = View.VISIBLE
                isIndeterminate = prog < 0
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N)
                    setProgress(prog, true)
                else
                    setProgress(prog)
            }
        }
    }

    suspend fun hideProgress() = withContext(Dispatchers.Main) {
        if (isActive)
            findViewById<ProgressBar>(R.id.progressBar)?.visibility = View.INVISIBLE
    }

}