package ru.yourok.torrserve.ui.activities.main

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.KeyEvent
import android.widget.FrameLayout
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
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
import ru.yourok.torrserve.ext.clearStackFragmnet
import ru.yourok.torrserve.server.api.Api
import ru.yourok.torrserve.server.local.TorrService
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
import ru.yourok.torrserve.ui.fragments.rutor.RutorFragment
import ru.yourok.torrserve.utils.Net
import ru.yourok.torrserve.utils.Permission
import ru.yourok.torrserve.utils.ThemeUtil
import kotlin.system.exitProcess


class MainActivity : AppCompatActivity() {

    private lateinit var viewModel: StatusViewModel
    private val themeUtil = ThemeUtil()
    private var firebaseAnalytics: FirebaseAnalytics? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Permission.requestPermissionWithRationale(this)
        themeUtil.onCreate(this)
        setContentView(R.layout.main_activity)
        firebaseAnalytics = FirebaseAnalytics.getInstance(this)

        viewModel = ViewModelProvider(this)[StatusViewModel::class.java]

        setupNavigator()
        lifecycleScope.launch(Dispatchers.IO) {
            TorrService.start()
            if (TorrService.wait(10)) {
                if (TorrService.isLocal()) {
                    val ver = Api.echo()
                    if (ver.startsWith("1.1.")) {
                        withContext(Dispatchers.Main) {
                            ServerUpdateFragment().show(this@MainActivity, R.id.container, true)
                            App.toast(R.string.need_update_server, true)
                        }
                    }
                } else {
                    val ver = Api.echo()
                    if (ver.startsWith("1.1.")) {
                        withContext(Dispatchers.Main) {
                            App.toast(R.string.not_support_old_server, true)
                        }
                    }
                }
            } else {
                withContext(Dispatchers.Main) {
                    if (App.inForeground)
                        if (TorrService.isLocal())
                            ServerUpdateFragment().show(this@MainActivity, R.id.container, true)
                        else
                            ServerFinderFragment().show(this@MainActivity, R.id.container, true)
                    App.toast(R.string.need_install_server, true)
                }
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
        TorrService.start()
        updateStatus()
    }

    private fun updateStatus() {
        val host = viewModel.getHost()
        host.observe(this) {
            findViewById<TextView>(R.id.tvCurrentHost)?.text = it
        }
        val data = viewModel.get()
        data.observe(this) {
            findViewById<TextView>(R.id.tvStatus)?.text = it
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

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        val menu = findViewById<DrawerLayout>(R.id.drawerLayout)
        if (keyCode == KeyEvent.KEYCODE_MENU) {
            if (menu?.isDrawerOpen(GravityCompat.START) == true)
                closeMenu()
            else
                menu?.openDrawer(GravityCompat.START)
        }
        return super.onKeyDown(keyCode, event)
    }

    private fun checkUpdate() {
        lifecycleScope.launch(Dispatchers.IO) {
            if (UpdaterApk.check())
                withContext(Dispatchers.Main) {
                    App.toast(R.string.found_new_app_update, true)
                }
        }
        lifecycleScope.launch(Dispatchers.IO) {
            UpdaterServer.check()
        }
    }

    private fun setupNavigator() {
        // Logo
        findViewById<FrameLayout>(R.id.header).setOnClickListener {
            val currFragment = supportFragmentManager.findFragmentById(R.id.container)
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

        findViewById<FrameLayout>(R.id.btnRutor).setOnClickListener {
            RutorFragment().show(this@MainActivity, R.id.container, true)
            closeMenu()
        }

        findViewById<FrameLayout>(R.id.btnRemoveAll).setOnClickListener { _ ->
            AlertDialog.Builder(this)
                .setTitle(R.string.remove_all_warn)
                .setPositiveButton(android.R.string.yes) { dialog, _ ->
                    lifecycleScope.launch(Dispatchers.IO) {
                        try {
                            val list = Api.listTorrent()
                            list.forEach {
                                Api.remTorrent(it.hash)
                            }
                            withContext(Dispatchers.Main) { dialog.dismiss() }
                        } catch (e: Exception) {
                            e.message?.let {
                                App.toast(it)
                            }
                        }
                    }
                }
                .setNegativeButton(android.R.string.no) { dialog, _ ->
                    dialog.dismiss()
                }
                .show()
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
                        App.toast(it)
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
            exitProcess(0)
        }
    }
}