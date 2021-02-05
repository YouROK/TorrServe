package ru.yourok.torrserve.ui.activities.main

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.FrameLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.GravityCompat
import androidx.drawerlayout.widget.DrawerLayout
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import com.google.firebase.analytics.FirebaseAnalytics
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import ru.yourok.torrserve.R
import ru.yourok.torrserve.app.App
import ru.yourok.torrserve.atv.channels.UpdaterCards
import ru.yourok.torrserve.ext.clearStackFragmnet
import ru.yourok.torrserve.server.api.Api
import ru.yourok.torrserve.services.TorrService
import ru.yourok.torrserve.settings.Settings
import ru.yourok.torrserve.ui.fragments.add.AddFragment
import ru.yourok.torrserve.ui.fragments.donate.DonateFragment
import ru.yourok.torrserve.ui.fragments.donate.DonateMessage
import ru.yourok.torrserve.ui.fragments.main.servfinder.ServerFinderFragment
import ru.yourok.torrserve.ui.fragments.main.servsets.ServerSettingsFragment
import ru.yourok.torrserve.ui.fragments.main.settings.SettingsFragment
import ru.yourok.torrserve.ui.fragments.main.torrents.TorrentsFragment
import ru.yourok.torrserve.ui.fragments.main.update.apk.ApkUpdateFragment
import ru.yourok.torrserve.ui.fragments.main.update.apk.UpdaterApk
import ru.yourok.torrserve.ui.fragments.main.update.server.ServerUpdateFragment
import ru.yourok.torrserve.ui.fragments.main.update.server.UpdaterServer
import ru.yourok.torrserve.utils.Net
import ru.yourok.torrserve.utils.Premissions
import ru.yourok.torrserve.utils.ThemeUtil


class MainActivity : AppCompatActivity() {

    private lateinit var viewModel: StatusViewModel
    private val themeUtil = ThemeUtil()
    private var firebaseAnalytics: FirebaseAnalytics? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Premissions.requestPermissionWithRationale(this)
        themeUtil.onCreate(this)
        setContentView(R.layout.main_activity)
        firebaseAnalytics = FirebaseAnalytics.getInstance(this)

        viewModel = ViewModelProvider(this).get(StatusViewModel::class.java)

        setupNavigator()
        lifecycleScope.launch(Dispatchers.IO) {
            TorrService.start()
            if (TorrService.wait(5)) {
                if (TorrService.isLocal()) {
                    val ver = Api.echo()
                    if (ver.startsWith("1.1.")) {
                        ServerUpdateFragment().show(this@MainActivity, R.id.container, true)
                        lifecycleScope.launch(Dispatchers.Main) {
                            App.Toast(R.string.need_update_server, true)
                        }
                    }
                } else {
                    val ver = Api.echo()
                    if (ver.startsWith("1.1.")) {
                        lifecycleScope.launch(Dispatchers.Main) {
                            App.Toast(R.string.need_update_server_remote, true)
                        }
                    }
                }
                UpdaterCards.updateCards()
            }
        }

        if (savedInstanceState == null) {
            clearStackFragmnet()
            TorrentsFragment().apply {
                show(this@MainActivity, R.id.container)
            }
            DonateMessage.showDonate(this)
            checkUpdate()
        }
    }

    override fun onResume() {
        super.onResume()
        themeUtil.onResume(this)
        updateStatus()
    }

    private fun updateStatus() {
        findViewById<TextView>(R.id.tvCurrentHost)?.text = Settings.getHost()
        val data = viewModel.get()
        data.observe(this) {
            findViewById<TextView>(R.id.tvStatus)?.text = it
            findViewById<TextView>(R.id.tvCurrentHost)?.text = Settings.getHost()
        }
    }

    private fun closeMenu() {
        findViewById<DrawerLayout>(R.id.drawerLayout)?.closeDrawers()
    }

    override fun onBackPressed() {
        if (findViewById<DrawerLayout>(R.id.drawerLayout)?.isDrawerOpen(GravityCompat.START) == true)
            closeMenu()
        else
            super.onBackPressed()
    }

    private fun checkUpdate() {
        lifecycleScope.launch(Dispatchers.IO) {
            if (UpdaterApk.check())
                withContext(Dispatchers.Main) {
                    App.Toast(R.string.found_new_app_update, true)
                }
        }
        lifecycleScope.launch(Dispatchers.IO) {
            UpdaterServer.check()
        }
    }

    private fun setupNavigator() {
        findViewById<FrameLayout>(R.id.header).setOnClickListener { _ ->
            val currFragment = supportFragmentManager?.findFragmentById(R.id.container)
            if (currFragment is TorrentsFragment)
                ServerFinderFragment().show(this, R.id.container, true)
            else {
                clearStackFragmnet()
                TorrentsFragment().show(this, R.id.container)
            }
            closeMenu()
        }
        findViewById<FrameLayout>(R.id.header).setOnLongClickListener {
            ServerSettingsFragment().show(this, R.id.container, true)
            closeMenu()
            true
        }

        findViewById<FrameLayout>(R.id.btnAdd).setOnClickListener {
            AddFragment().show(this@MainActivity, R.id.container, true)
            closeMenu()
        }

        findViewById<FrameLayout>(R.id.btnRemoveAll).setOnClickListener { _ ->
            lifecycleScope.launch(Dispatchers.IO) {
                try {
                    val list = Api.listTorrent()
                    list.forEach {
                        Api.remTorrent(it.hash)
                    }
                    UpdaterCards.updateCards()
                } catch (e: Exception) {
                    // TODO: notify user
                }
            }
            closeMenu()
        }

        findViewById<FrameLayout>(R.id.btnPlaylist).setOnClickListener {
            lifecycleScope.launch(Dispatchers.IO) {
                try {
                    if (Api.listTorrent().isNotEmpty()) {
                        val intent = Intent(Intent.ACTION_VIEW)
                        intent.setDataAndType(Uri.parse(Net.getHostUrl("/playlistall/all.m3u")), "video/*")
                        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
                        App.context.startActivity(intent)
                    }
                } catch (e: Exception) {
                    e.message?.let {
                        App.Toast(it)
                    }
                }
            }
            closeMenu()
        }

        findViewById<FrameLayout>(R.id.btnDonate).setOnClickListener {
            DonateFragment().show(this, R.id.container, true)
            closeMenu()
        }

        findViewById<FrameLayout>(R.id.btnUpdate).setOnClickListener {

            lifecycleScope.launch(Dispatchers.IO) {
                if (UpdaterApk.check())
                    withContext(Dispatchers.Main) {
                        ApkUpdateFragment().show(this@MainActivity, R.id.container, true)
                    }
                else
                    withContext(Dispatchers.Main) {
                        ServerUpdateFragment().show(this@MainActivity, R.id.container, true)
                    }
            }
            closeMenu()
        }

        findViewById<FrameLayout>(R.id.btnSettings).setOnClickListener {
            SettingsFragment().show(this@MainActivity, R.id.container)
            closeMenu()
        }

        findViewById<FrameLayout>(R.id.btnExit).setOnClickListener {
            closeMenu()
            TorrService.stop()
            finishAffinity()
        }
    }
}