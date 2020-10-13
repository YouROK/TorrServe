package ru.yourok.torrserve.activitys.main

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.KeyEvent
import android.widget.FrameLayout
import android.widget.ListView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.GravityCompat
import androidx.drawerlayout.widget.DrawerLayout
import kotlinx.android.synthetic.main.activity_main.*
import kotlinx.android.synthetic.main.main_navigation_menu.*
import ru.yourok.torrserve.R
import ru.yourok.torrserve.activitys.add.AddActivity
import ru.yourok.torrserve.activitys.play.PlayActivity
import ru.yourok.torrserve.activitys.settings.AppSettingsActivity
import ru.yourok.torrserve.activitys.settings.ConnectionActivity
import ru.yourok.torrserve.activitys.settings.ServerSettingsActivity
import ru.yourok.torrserve.activitys.splash.SplashActivity
import ru.yourok.torrserve.activitys.updater.UpdaterActivity
import ru.yourok.torrserve.adapters.TorrentAdapter
import ru.yourok.torrserve.app.App
import ru.yourok.torrserve.atv.channels.UpdaterCards
import ru.yourok.torrserve.dialog.DialogPerm
import ru.yourok.torrserve.dialog.Donate
import ru.yourok.torrserve.menu.TorrentMainMenu
import ru.yourok.torrserve.preferences.Preferences
import ru.yourok.torrserve.server.api.Api
import ru.yourok.torrserve.server.api.JSObject
import ru.yourok.torrserve.server.net.Net
import ru.yourok.torrserve.serverloader.ServerFile
import ru.yourok.torrserve.serverloader.Updater
import ru.yourok.torrserve.services.ServerService
import kotlin.concurrent.thread


class MainActivity : AppCompatActivity() {
    companion object {
        private var isSplash = false
    }

    private var torrAdapter = TorrentAdapter(this)
    private var mDrawer: DrawerLayout? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        if (!isSplash) {
            startActivityForResult(Intent(this, SplashActivity::class.java), 22)
            isSplash = true
        } else {
            UpdaterCards.updateCards()
        }

        setupNavigator()

        mDrawer = findViewById(R.id.drawerlayout)

        rvTorrents.apply {
            adapter = torrAdapter
            requestFocus()
            setOnItemClickListener { _, _, i, _ ->
                val torr = torrAdapter.getItem(i) as JSObject
                val mag = torr.getString("Magnet", "")
                if (mag.isEmpty()) {
                    Toast.makeText(App.getContext(), "Magnet not found", Toast.LENGTH_SHORT).show()
                    return@setOnItemClickListener
                }
                val vintent = Intent(App.getContext(), PlayActivity::class.java)
                vintent.setData(Uri.parse(mag))
                vintent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
                vintent.action = Intent.ACTION_VIEW
                vintent.putExtra("DontSave", true)
                App.getContext().startActivity(vintent)
            }

            setOnItemLongClickListener { _, _, i, _ ->
                choiceMode = ListView.CHOICE_MODE_MULTIPLE_MODAL
                setItemChecked(i, true)
                true
            }
            setMultiChoiceModeListener(TorrentMainMenu(this@MainActivity, torrAdapter))
        }
        Updater.show(this)
        DialogPerm.requestPermissionWithRationale(this)
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if (keyCode == KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE)
            return true
        return super.onKeyDown(keyCode, event)
    }

    override fun onKeyUp(keyCode: Int, event: KeyEvent?): Boolean {
        if (keyCode == KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE) {
            val pos = rvTorrents.selectedItemPosition

            val torr = torrAdapter.getItem(pos) as JSObject
            val mag = torr.getString("Magnet", "")
            if (mag.isEmpty()) {
                Toast.makeText(App.getContext(), "Magnet not found", Toast.LENGTH_SHORT).show()
                return true
            }
            val vintent = Intent(App.getContext(), PlayActivity::class.java)
            vintent.setData(Uri.parse(mag))
            vintent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
            vintent.action = Intent.ACTION_VIEW
            vintent.putExtra("DontSave", true)
            vintent.putExtra("PlayLast", true)
            App.getContext().startActivity(vintent)
            return true
        }

        return super.onKeyUp(keyCode, event)
    }

    override fun onBackPressed() {
        mDrawer?.let {
            if (it.isDrawerOpen(GravityCompat.START)) {
                it.closeDrawer(GravityCompat.START)
                return
            }
        }
        super.onBackPressed()
    }

    override fun onResume() {
        super.onResume()
        autoUpdateList()
        tvCurrentHost.text = Preferences.getCurrentHost()
    }

    override fun onPause() {
        super.onPause()
        isUpdate = false
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == 22) {
            thread {
                Thread.sleep(1000)
                if (torrAdapter.count == 0)
                    Handler(Looper.getMainLooper()).post {
                        mDrawer?.openDrawer(GravityCompat.START)
                    }
            }
            Donate.showDonate(this)
        }
    }

    private fun setStatus(msg: String) {
        Handler(Looper.getMainLooper()).post {
            tvStatus.text = msg
        }
    }

    private var isUpdate = false
    private fun autoUpdateList() {
        thread {
            synchronized(isUpdate) {
                if (isUpdate)
                    return@thread
                isUpdate = true
            }

            while (isUpdate) {
                val version = Api.serverEcho()
                if (version.isNullOrEmpty()) {
                    if (Api.serverIsLocal() && !ServerFile.serverExists())
                        setStatus(getString(R.string.server_not_exists))
                    else
                        setStatus(getString(R.string.server_not_responding))
                } else
                    setStatus(version)

                torrAdapter.checkList()
                Thread.sleep(1000)
            }
        }
    }

    private fun setupNavigator() {
        val tvCurrHost = findViewById<TextView>(R.id.tvCurrentHost)
        tvCurrHost.text = Preferences.getCurrentHost()

        findViewById<FrameLayout>(R.id.header).setOnClickListener { _ ->
            startActivity(Intent(this, ConnectionActivity::class.java))
        }
        findViewById<FrameLayout>(R.id.header).setOnLongClickListener {
            startActivity(Intent(this, ServerSettingsActivity::class.java))
            true
        }

        findViewById<FrameLayout>(R.id.btnAdd).setOnClickListener {
            startActivity(Intent(this, AddActivity::class.java))
        }

        findViewById<FrameLayout>(R.id.btnRemoveAll).setOnClickListener { _ ->
            thread {
                try {
                    val torrList = Api.torrentList()
                    torrList.forEach {
                        val hash = it.getString("Hash", "")
                        if (hash.isNotEmpty())
                            Api.torrentRemove(hash)
                    }
                    torrAdapter.checkList()
                    UpdaterCards.updateCards()
                } catch (e: Exception) {
                    e.message?.let {
                        App.Toast(it)
                    }
                }
            }
        }

        findViewById<FrameLayout>(R.id.btnPlaylist).setOnClickListener {
            thread {
                try {
                    if (Api.torrentList().isNotEmpty()) {
                        val intent = Intent(Intent.ACTION_VIEW)
                        intent.setDataAndType(Uri.parse(Net.getHostUrl("/torrent/playlist.m3u")), "video/*")
                        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
                        App.getContext().startActivity(intent)
                    }
                } catch (e: Exception) {
                    e.message?.let {
                        App.Toast(it)
                    }
                }
            }
        }

        findViewById<FrameLayout>(R.id.btnDonate).setOnClickListener {
            Donate.donateDialog(this)
        }

        findViewById<FrameLayout>(R.id.btnUpdate).setOnClickListener {
            startActivity(Intent(this, UpdaterActivity::class.java))
        }

        findViewById<FrameLayout>(R.id.btnSettings).setOnClickListener {
            startActivity(Intent(this, AppSettingsActivity::class.java))
        }

        findViewById<FrameLayout>(R.id.btnExit).setOnClickListener {
            ServerService.exit()
        }
    }
}

