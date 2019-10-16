package ru.yourok.torrserve.activitys.play

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.support.v4.content.ContextCompat
import android.support.v7.app.AppCompatActivity
import android.support.v7.widget.DividerItemDecoration
import android.support.v7.widget.LinearLayoutManager
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import com.google.firebase.analytics.FirebaseAnalytics
import com.google.gson.Gson
import kotlinx.android.synthetic.main.activity_play.*
import org.json.JSONObject
import ru.yourok.torrserve.R
import ru.yourok.torrserve.ad.Ad
import ru.yourok.torrserve.adapters.TorrentFilesAdapter
import ru.yourok.torrserve.app.App
import ru.yourok.torrserve.atv.channels.UpdaterCards
import ru.yourok.torrserve.num.entity.Entity
import ru.yourok.torrserve.player.PlayerActivity
import ru.yourok.torrserve.preferences.Preferences
import ru.yourok.torrserve.server.api.Api
import ru.yourok.torrserve.server.api.JSObject
import ru.yourok.torrserve.server.net.Net
import ru.yourok.torrserve.server.torrent.Torrent
import ru.yourok.torrserve.serverloader.ServerFile
import ru.yourok.torrserve.services.ServerService
import ru.yourok.torrserve.utils.ByteFmt
import ru.yourok.torrserve.utils.Mime
import kotlin.concurrent.thread

class PlayActivity : AppCompatActivity() {

    private var title = ""
    private var poster = ""
    private var info = ""
    private var torrLink = ""
    private var torrent: JSObject? = null
    private var isClosed = false
    private var save = true
    private var play = true
    private var ad: Ad? = null

    private var firebaseAnalytics: FirebaseAnalytics? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_play)
        setFinishOnTouchOutside(false)

        findViewById<ImageView>(R.id.ivAd)?.let {
            ad = Ad(it, this)
            ad?.get()
        }

        firebaseAnalytics = FirebaseAnalytics.getInstance(this)

        val attr = window.attributes
        attr.width = (resources.displayMetrics.widthPixels * 0.90).toInt()
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

        if (intent.hasExtra("DontPlay"))
            play = false

        if (intent.hasExtra("Poster"))
            poster = intent.getStringExtra("Poster")
        if (intent.hasExtra("Title"))
            title = intent.getStringExtra("Title")
        if (intent.hasExtra("Info"))
            info = intent.getStringExtra("Info")

        thread {
            try {
                startServer()
                torrent = addTorrent()
                if (isClosed || torrent == null || !play) {
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
                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(Net.getHostUrl(pl)))
                    intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
                    App.getContext().startActivity(intent)
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

        if (info.isEmpty() && (poster.isNotEmpty() || title.isNotEmpty())) {
            val js = JSONObject()
            if (poster.isNotEmpty())
                js.put("poster_path", poster)
            if (title.isNotEmpty())
                js.put("title", title)
            info = js.toString(0)
        } else {
            try {
                val gson = Gson()
                val ent = gson.fromJson<Entity>(info, Entity::class.java)
                info = gson.toJson(ent)
            } catch (e: Exception) {
            }
        }

        val hash = Api.torrentAdd(torrLink, title, info, save)
        ServerService.notificationSetHash(hash)
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
        UpdaterCards.updateCards()
        return Api.torrentGet(hash)
    }

    private fun showList(torr: JSObject, files: List<JSObject>) {
        var lastPlayed = -1
        for (i in files.size - 1 downTo 0) {
            if (files[i].getBoolean("Viewed", false)) {
                lastPlayed = i
                break
            }
        }

        hideProgress()
        Handler(Looper.getMainLooper()).post {
            rvFileList.apply {
                setHasFixedSize(true)
                layoutManager = LinearLayoutManager(this@PlayActivity)
                if (lastPlayed > 0)
                    layoutManager.scrollToPosition(lastPlayed)
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
            msg += getString(R.string.download_speed) + ": " + ByteFmt.byteFmt(downloadSpeed) + "/s"
            showProgress(msg, prc)
        }, {
            App.Toast(it)
            isClosed = true
            finish()
        })

        if (!isClosed) {

            ad?.waitAd()

            val link = file.getString("Link", "")
            val name = file.getString("Name", "")

            val addr = Preferences.getCurrentHost() + link
            val pkg = Preferences.getPlayer()

            if (pkg.equals("2")) {
                intent = Intent(this, PlayerActivity::class.java)
                intent.data = Uri.parse(addr)
                startActivity(intent)
                finish()
                return
            }

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
                // color Progress: https://stackoverflow.com/questions/2020882/how-to-change-progress-bars-progress-color-in-android
                var progressDrawable = progressBar.getProgressDrawable().mutate()
                progressDrawable.setColorFilter(ContextCompat.getColor(this, R.color.colorAccent), android.graphics.PorterDuff.Mode.SRC_IN)
                progressBar.setProgressDrawable(progressDrawable)
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
                    ServerService.notificationSetHash("")
                    if (hash.isNotEmpty())
                        try {
                            Api.torrentDrop(hash)
                        } catch (e: Exception) {
                        }
                }
            }
        }
    }

    override fun finish() {
        ad?.waitAd()
        super.finish()
    }

    override fun onBackPressed() {
        isClosed = true
        super.onBackPressed()
    }
}
