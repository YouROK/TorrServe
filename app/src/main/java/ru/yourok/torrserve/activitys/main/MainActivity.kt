package ru.yourok.torrserve.activitys.main

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.support.v7.app.AppCompatActivity
import android.view.KeyEvent
import android.widget.FrameLayout
import android.widget.ListView
import android.widget.TextView
import android.widget.Toast
import com.mxn.soul.flowingdrawer_core.ElasticDrawer
import com.mxn.soul.flowingdrawer_core.ElasticDrawer.STATE_CLOSED
import com.mxn.soul.flowingdrawer_core.FlowingDrawer
import kotlinx.android.synthetic.main.activity_main.*
import kotlinx.android.synthetic.main.main_navigation_menu.*
import ru.yourok.torrserve.R
import ru.yourok.torrserve.activitys.add.AddActivity
import ru.yourok.torrserve.activitys.play.PlayActivity
import ru.yourok.torrserve.activitys.settings.AppSettingsActivity
import ru.yourok.torrserve.activitys.settings.ServerSettingsActivity
import ru.yourok.torrserve.activitys.splash.SplashActivity
import ru.yourok.torrserve.activitys.updater.UpdaterActivity
import ru.yourok.torrserve.adapters.TorrentAdapter
import ru.yourok.torrserve.app.App
import ru.yourok.torrserve.dialog.DialogInputList
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
    private var mDrawer: FlowingDrawer? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        if (!isSplash) {
            startActivityForResult(Intent(this, SplashActivity::class.java), 22)
            isSplash = true
        }

        setupNavigator()

        mDrawer = findViewById(R.id.drawerlayout)
        mDrawer?.setTouchMode(ElasticDrawer.TOUCH_MODE_FULLSCREEN)
        mDrawer?.setOnDrawerStateChangeListener(object : ElasticDrawer.OnDrawerStateChangeListener {
            override fun onDrawerStateChange(oldState: Int, newState: Int) {
                if (newState == STATE_CLOSED)
                    rvTorrents.requestFocus()
            }

            override fun onDrawerSlide(openRatio: Float, offsetPixels: Int) {}
        })

        rvTorrents.apply {
            adapter = torrAdapter
            requestFocus()
            setOnItemClickListener { adapterView, view, i, l ->
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

            setOnItemLongClickListener { adapterView, view, i, l ->
                choiceMode = ListView.CHOICE_MODE_MULTIPLE_MODAL
                setItemChecked(i, true)
                true
            }
            setMultiChoiceModeListener(TorrentMainMenu(this@MainActivity, torrAdapter))
        }
        Updater.show(this)
        DialogPerm.requestPermissionWithRationale(this)
    }

    override fun onKeyUp(keyCode: Int, event: KeyEvent?): Boolean {
        mDrawer?.let {
            if (!it.isMenuVisible && keyCode == KeyEvent.KEYCODE_DPAD_RIGHT) {
                it.openMenu(true)
                header.requestFocus()
                return true
            }
        }

        return super.onKeyUp(keyCode, event)
    }

    override fun onBackPressed() {
        mDrawer?.let {
            if (it.isMenuVisible()) {
                it.closeMenu()
                return
            }
        }
        super.onBackPressed()
    }

    override fun onResume() {
        super.onResume()
        autoUpdateList()
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
                        mDrawer?.openMenu(true)
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
                if (Api.serverEcho() == "") {
                    if (Api.serverIsLocal() && !ServerFile.serverExists())
                        setStatus(getString(R.string.server_not_exists))
                    else
                        setStatus(getString(R.string.server_not_responding))
                } else
                    setStatus("")

                torrAdapter.checkList()
                Thread.sleep(1000)
            }
        }
    }

    private fun setupNavigator() {
        val tvCurrHost = findViewById<TextView>(R.id.tvCurrentHost)
        tvCurrHost.text = Preferences.getCurrentHost()

        findViewById<FrameLayout>(R.id.header).setOnClickListener { _ ->
            DialogInputList.show(this, getString(R.string.host) + ":", Preferences.getHosts()) {
                if (it.isEmpty())
                    return@show

                var host = it
                if (!host.startsWith("http://", true))
                    host = "http://" + host

                if (Uri.parse(host).port == -1)
                    host += ":8090"

                if (Api.serverCheck(host).isEmpty()) {
                    App.Toast(getString(R.string.server_not_responding))
                    return@show
                }
                Preferences.setCurrentHost(host)
                Handler(Looper.getMainLooper()).post {
                    tvCurrHost.text = host
                }
                thread {
                    val hosts = mutableListOf<String>()
                    for (h in Preferences.getHosts()) {
                        if (Api.serverCheck(h).isNotEmpty())
                            hosts.add(h)
                    }
                    hosts.add(host)
                    Preferences.setHosts(hosts)
                }
            }
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
                } catch (e: Exception) {
                }
            }
        }

        findViewById<FrameLayout>(R.id.btnPlaylist).setOnClickListener {
            thread {
                if (Api.torrentList().isNotEmpty()) {
                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(Net.getHostUrl("/torrent/playlist.m3u")))
                    App.getContext().startActivity(intent)
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

