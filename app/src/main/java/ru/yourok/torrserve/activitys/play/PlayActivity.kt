package ru.yourok.torrserve.activitys.play

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.support.v7.app.AppCompatActivity
import android.support.v7.widget.DividerItemDecoration
import android.support.v7.widget.LinearLayoutManager
import android.view.View
import android.widget.LinearLayout
import kotlinx.android.synthetic.main.activity_play.*
import ru.yourok.torrserve.R
import ru.yourok.torrserve.adapters.TorrentFilesAdapter
import ru.yourok.torrserve.app.App
import ru.yourok.torrserve.preferences.Preferences
import ru.yourok.torrserve.server.api.Api
import ru.yourok.torrserve.server.api.JSObject
import ru.yourok.torrserve.server.net.Net
import ru.yourok.torrserve.server.torrent.Torrent
import ru.yourok.torrserve.serverloader.ServerFile
import ru.yourok.torrserve.services.NotificationServer
import ru.yourok.torrserve.services.ServerService
import ru.yourok.torrserve.utils.ByteFmt
import ru.yourok.torrserve.utils.Mime
import kotlin.concurrent.thread

class PlayActivity : AppCompatActivity() {

    private var title = ""
    private var torrLink = ""
    private var torrent: JSObject? = null
    private var isClosed = false
    private var save = true

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_play)
        setFinishOnTouchOutside(false)

        val attr = window.attributes
        attr.width = (resources.displayMetrics.widthPixels * 0.80).toInt()
        window.attributes = attr

        if (intent == null) {
            finish()
            return
        }

        intent.data?.let { torrLink = it.toString() }
        if (torrLink.isEmpty()) {
            finish()
            return
        }

        if (intent.hasExtra("DontSave"))
            save = false

        if (intent.hasExtra("Title"))
            title = intent.getStringExtra("Title")

        thread {
            try {
                startServer()
                torrent = addTorrent()
                if (isClosed || torrent == null) {
                    finish()
                    return@thread
                }
                val files = Torrent.getPlayableFiles(torrent!!)
                if (files.size == 1) {
                    play(torrent!!, files[0])
                } else if (files.size > 1) {
                    showList(torrent!!, files)
                } else {
                    App.Toast(getString(R.string.files_not_found))
                    finish()
                }
            } catch (e: Exception) {
                e.printStackTrace()
                App.Toast(e.message ?: getString(R.string.error_open_torrent))
                finish()
            }
        }

        buttonPlaylist.setOnClickListener { _ ->
            torrent?.let {
                val pl = it.getString("Playlist", "")
                if (pl.isNotEmpty()) {
                    val browserIntent = Intent(Intent.ACTION_VIEW, Uri.parse(Net.getHostUrl(pl)))
                    App.getContext().startActivity(browserIntent)
                    finish()
                }
            }
        }
    }

    private fun startServer() {
        showProgress(getString(R.string.connecting_to_server))
        if (Api.serverIsLocal() && ServerFile.serverExists() && Api.serverEcho().isEmpty()) {
            ServerService.start()
            ServerService.wait(60)
        }
    }

    private fun addTorrent(): JSObject {
        showProgress(getString(R.string.connects_to_torrent))
        val hash = Api.torrentAdd(torrLink, title, "", save)
        NotificationServer.Show(this, hash)
        Torrent.waitInfo(hash) {
            val activePeers = it.getInt("ActivePeers", 0)
            val totalPeers = it.getInt("TotalPeers", 0)
            val connectedSeeders = it.getInt("ConnectedSeeders", 0)
            var msg = it.getString("Name", "")
            if (msg.isNotEmpty())
                msg += "\n"

            msg += getString(R.string.peers) + ": [" + connectedSeeders.toString() + "] " + activePeers.toString() + "/" + totalPeers.toString() + "\n"
            showProgress(msg, 0)
        }
        return Api.torrentGet(hash)
    }

    private fun showList(torr: JSObject, files: List<JSObject>) {
        hideProgress()
        Handler(Looper.getMainLooper()).post {
            rvFileList.apply {
                setHasFixedSize(true)
                layoutManager = LinearLayoutManager(this@PlayActivity)
                adapter = TorrentFilesAdapter(files) {
                    showProgress(getString(R.string.buffering) + "...")
                    thread {
                        play(torr, it)
                    }
                }
                requestFocus()
                addItemDecoration(DividerItemDecoration(this@PlayActivity, LinearLayout.VERTICAL))
            }
        }
    }

    fun play(torr: JSObject, file: JSObject) {
        Torrent.preload(torr, file, {
            val preloadedBytes = it.getLong("PreloadedBytes", 0L)
            val preloadSize = it.getLong("PreloadSize", 0L)
            val activePeers = it.getInt("ActivePeers", 0)
            val totalPeers = it.getInt("TotalPeers", 0)
            val connectedSeeders = it.getInt("ConnectedSeeders", 0)
            val downloadSpeed = it.getDouble("DownloadSpeed", 0.0)

            var msg = torr.getString("Name", "")
            if (msg.isNotEmpty())
                msg += "\n"
            var prc = 0
            if (preloadSize > 0) {
                prc = (preloadedBytes * 100 / preloadSize).toInt()
                msg += getString(R.string.buffer) + ": " + (prc).toString() + "% " + ByteFmt.byteFmt(preloadedBytes) + "/" + ByteFmt.byteFmt(preloadSize) + "\n"
            }
            msg += getString(R.string.peers) + ": [" + connectedSeeders.toString() + "] " + activePeers.toString() + "/" + totalPeers.toString() + "\n"
            msg += getString(R.string.download_speed) + ": " + ByteFmt.byteFmt(downloadSpeed) + "/Sec"
            showProgress(msg, prc)
        }, {
            App.Toast(it)
            isClosed = true
            finish()
        })

        if (!isClosed) {
            val link = file.getString("Link", "")
            val name = file.getString("Name", "")

            val addr = Preferences.getCurrentHost() + link
            val pkg = Preferences.getPlayer()

            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(addr))
            val mime = Mime.getMimeType(name)
            intent.setDataAndType(Uri.parse(addr), mime)
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            intent.putExtra("title", name)

            if (pkg.isEmpty() or pkg.equals("0")) {
                if (intent.resolveActivity(packageManager) != null) {
                    startActivity(intent)
                    finish()
                    return
                }
            }
            if (pkg.isNotEmpty() and !pkg.equals("0") and !pkg.equals("1")) {
                intent.`package` = pkg
                if (intent.resolveActivity(packageManager) != null) {
                    startActivity(intent)
                    finish()
                    return
                }
                intent.`package` = ""
            }

            val intentC = Intent.createChooser(intent, "")
            startActivity(intentC)
            finish()
        }
    }

    private fun showProgress(msg: String, prog: Int = 0) {
        Handler(Looper.getMainLooper()).post {
            progress.visibility = View.VISIBLE
            list.visibility = View.GONE
            tvStatus.text = msg
            progressBar.isIndeterminate = prog == 0
            if (prog > 0) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N)
                    progressBar.setProgress(prog, true)
                else
                    progressBar.setProgress(prog)
            }
        }
    }

    private fun hideProgress() {
        Handler(Looper.getMainLooper()).post {
            progress.visibility = View.GONE
            list.visibility = View.VISIBLE
            tvStatus.text = ""
            progressBar.isIndeterminate = true
        }
    }

    override fun onPause() {
        super.onPause()
        if (isClosed) {
            thread {
                torrent?.let {
                    val hash = it.getString("Hash", "")
                    NotificationServer.Show(this, "")
                    if (hash.isNotEmpty())
                        try {
                            Api.torrentDrop(hash)
                        } catch (e: Exception) {
                        }
                }
            }
        }
    }

    override fun onBackPressed() {
        super.onBackPressed()
        isClosed = true
    }
}
