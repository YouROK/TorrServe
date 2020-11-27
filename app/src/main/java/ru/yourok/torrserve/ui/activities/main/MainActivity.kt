package ru.yourok.torrserve.ui.activities.main

import android.os.Bundle
import android.widget.FrameLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.drawerlayout.widget.DrawerLayout
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import ru.yourok.torrserve.R
import ru.yourok.torrserve.ext.clearStackFragmnet
import ru.yourok.torrserve.server.api.Api
import ru.yourok.torrserve.server.local.Updater
import ru.yourok.torrserve.services.TorrService
import ru.yourok.torrserve.settings.Settings
import ru.yourok.torrserve.ui.fragments.add.AddFragment
import ru.yourok.torrserve.ui.fragments.main.settings.SettingsFragment
import ru.yourok.torrserve.ui.fragments.main.torrents.TorrentsFragment
import ru.yourok.torrserve.utils.Premissions

class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Premissions.requestPermissionWithRationale(this)
        setContentView(R.layout.main_activity)

        setupNavigator()

        //TODO remove
        Updater.updateFromFile("/sdcard/Download/TorrServer-linux-arm64")

        TorrService.start()

        val viewModel = ViewModelProvider(this).get(StatusViewModel::class.java)
        val data = viewModel.get()
        data.observe(this) {
            findViewById<TextView>(R.id.tvStatus)?.text = it
        }

        if (savedInstanceState == null) {
            clearStackFragmnet()
            TorrentsFragment().apply {
                show(this@MainActivity, R.id.container)
            }
        }
    }

    private fun closeMenu() {
        findViewById<DrawerLayout>(R.id.drawerLayout)?.closeDrawers()
    }

    private fun setupNavigator() {
        val tvCurrHost = findViewById<TextView>(R.id.tvCurrentHost)
        tvCurrHost.text = Settings.getHost()

        findViewById<FrameLayout>(R.id.header).setOnClickListener { _ ->
//                startActivity(Intent(this, ConnectionActivity::class.java))
        }
        findViewById<FrameLayout>(R.id.header).setOnLongClickListener {
//                startActivity(Intent(this, ServerSettingsActivity::class.java))
            true
        }

        findViewById<FrameLayout>(R.id.btnAdd).setOnClickListener {
            AddFragment().show(this@MainActivity, R.id.container, true)
            closeMenu()
        }

        findViewById<FrameLayout>(R.id.btnRemoveAll).setOnClickListener { _ ->
            lifecycleScope.launch(Dispatchers.IO) {
                val list = Api.listTorrent()
                list.forEach {
                    Api.remTorrent(it.hash)
                }
            }
            closeMenu()
        }

        findViewById<FrameLayout>(R.id.btnPlaylist).setOnClickListener {
//                thread {
//                    try {
//                        if (Api.torrentList().isNotEmpty()) {
//                            val intent = Intent(Intent.ACTION_VIEW)
//                            intent.setDataAndType(Uri.parse(Net.getHostUrl("/torrent/playlist.m3u")), "video/*")
//                            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
//                            App.getContext().startActivity(intent)
//                        }
//                    } catch (e: Exception) {
//                        e.message?.let {
//                            App.Toast(it)
//                        }
//                    }
//                }
            closeMenu()
        }

        findViewById<FrameLayout>(R.id.btnDonate).setOnClickListener {
//                Donate.donateDialog(this)
            closeMenu()
        }

        findViewById<FrameLayout>(R.id.btnUpdate).setOnClickListener {
//                startActivity(Intent(this, UpdaterActivity::class.java))
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