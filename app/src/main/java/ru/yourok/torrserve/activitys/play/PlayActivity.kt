package ru.yourok.torrserve.activitys.play

import android.app.Activity
import android.content.Intent
import android.graphics.Typeface
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.Spannable
import android.text.SpannableStringBuilder
import android.text.style.StyleSpan
import android.view.KeyEvent
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
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
import ru.yourok.torrserve.preferences.Preferences
import ru.yourok.torrserve.server.api.Api
import ru.yourok.torrserve.server.api.JSObject
import ru.yourok.torrserve.server.net.Net
import ru.yourok.torrserve.server.torrent.Torrent
import ru.yourok.torrserve.serverloader.ServerFile
import ru.yourok.torrserve.services.ServerService
import ru.yourok.torrserve.utils.ByteFmt
import ru.yourok.torrserve.utils.Mime
import java.io.File
import kotlin.concurrent.thread

class PlayActivity : AppCompatActivity() {

    private var title = ""
    private var poster = ""
    private var info = ""
    private var save = true
    private var play = true
    private var torrLink = ""
    private var fileTemplate = ""

    private var lastPlayed = -1
    private var useRemote = false
    private var selectedRemote = -1

    private var torrent: JSObject? = null
    private var files: List<JSObject> = emptyList()

    private var isClosed = false
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
        if (resources.displayMetrics.widthPixels <= resources.displayMetrics.heightPixels)
            attr.width = (resources.displayMetrics.widthPixels * 0.90).toInt()
        else if (resources.displayMetrics.widthPixels > resources.displayMetrics.heightPixels)
            attr.width = (resources.displayMetrics.widthPixels * 0.50).toInt()
        window.attributes = attr

        if (intent == null) {
            finish(false, "intent is null")
            return
        }

        intent.data?.let { torrLink = it.toString() }
        if (torrLink.isEmpty()) {
            finish(false, "empty magnet")
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

        if (intent.hasExtra("FileTemplate"))
            fileTemplate = intent.getStringExtra("FileTemplate")

        thread {
            try {
                startServer()
                torrent = addTorrent()
                if (isClosed || torrent == null || !play) {
                    finish(false)
                    return@thread
                }
                files = Torrent.getPlayableFiles(torrent!!)
                files = SerialFilter.filter(intent, files)

                if (files.size > 2) {
                    for (i in files.size - 1 downTo 0) {
                        if (files[i].getBoolean("Viewed", false)) {
                            lastPlayed = i
                            break
                        }
                    }
                    selectedRemote = lastPlayed
                }

                if (files.size == 1) {
                    play(torrent!!, files[0], false)
                } else if (files.size > 1) {
                    showList(torrent!!, files)
                } else {
                    App.Toast(getString(R.string.files_not_found))
                    finish(false, "file not found in torrent")
                }
            } catch (e: Exception) {
                e.printStackTrace()
                App.Toast(e.message ?: getString(R.string.error_open_torrent))
                finish(false, "error opening torrent")
            }
        }

        buttonPlaylist.setOnClickListener { _ ->
            torrent?.let {
                val pl = it.getString("Playlist", "")
                if (pl.isNotEmpty()) {
                    val intent = Intent(Intent.ACTION_VIEW)
                    intent.setDataAndType(Uri.parse(Net.getHostUrl(pl)), "audio/x-mpegurl")
                    intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
                    App.getContext().startActivity(intent)
                    finish(true)
                }
            }
        }
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if (!useRemote)
            return super.onKeyDown(keyCode, event)

        if (keyCode == KeyEvent.KEYCODE_MENU ||
                keyCode == KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE
        )
            return true

        if (keyCode == KeyEvent.KEYCODE_MEDIA_PREVIOUS ||
                keyCode == KeyEvent.KEYCODE_MEDIA_REWIND ||
                keyCode == KeyEvent.KEYCODE_PAGE_DOWN
        ) {
            selectedRemote--
            if (selectedRemote < 0)
                selectedRemote = files.size - 1
            setFocusItem(selectedRemote, 20)
            return true
        }

        if (keyCode == KeyEvent.KEYCODE_MEDIA_NEXT ||
                keyCode == KeyEvent.KEYCODE_MEDIA_FAST_FORWARD ||
                keyCode == KeyEvent.KEYCODE_PAGE_UP
        ) {
            selectedRemote++
            if (selectedRemote >= files.size)
                selectedRemote = 0
            setFocusItem(selectedRemote, 20)
            return true
        }

        return super.onKeyDown(keyCode, event)
    }

    override fun onKeyUp(keyCode: Int, event: KeyEvent?): Boolean {
        if (!useRemote)
            return super.onKeyUp(keyCode, event)

        if (keyCode == KeyEvent.KEYCODE_MENU) {
            buttonPlaylist.performClick()
            return true
        }
        if (keyCode == KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE) {
            torrent?.let { torr ->
                val fileInd = lastPlayed + 1
                if (fileInd < files.size)
                    thread {
                        play(torr, files[fileInd], false)
                    }
            }
            return true
        }

        if (
                keyCode == KeyEvent.KEYCODE_MEDIA_PREVIOUS ||
                keyCode == KeyEvent.KEYCODE_MEDIA_REWIND ||
                keyCode == KeyEvent.KEYCODE_PAGE_DOWN ||
                keyCode == KeyEvent.KEYCODE_MEDIA_NEXT ||
                keyCode == KeyEvent.KEYCODE_MEDIA_FAST_FORWARD ||
                keyCode == KeyEvent.KEYCODE_PAGE_UP
        )
            return true

        return super.onKeyUp(keyCode, event)
    }

    private fun startServer() {
        showProgress()
        setInfo(getString(R.string.connecting_to_server))
        if (Api.serverIsLocal() && ServerFile.serverExists() && Api.serverEcho().isEmpty()) {
            ServerService.start()
            ServerService.wait(60)
        }
    }

    private fun addTorrent(): JSObject {
        showProgress()
        setInfo(getString(R.string.connects_to_torrent))
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
            val name = it.getString("Name", "")
            showProgress()
            setInfo(name, null, "", "[" + connectedSeeders.toString() + "] " + activePeers.toString() + "/" + totalPeers.toString(), "")

        }
        UpdaterCards.updateCards()
        return Api.torrentGet(hash)
    }

    private fun showList(torr: JSObject, files: List<JSObject>) {
        useRemote = true
        hideProgress()
        Handler(Looper.getMainLooper()).post {
            rvFileList.apply {
                setHasFixedSize(true)
                layoutManager = LinearLayoutManager(this@PlayActivity)
                if (lastPlayed > 0)
                    setFocusItem(lastPlayed)
                adapter = TorrentFilesAdapter(files, {
                    thread {
                        play(torr, it, false)
                    }
                }, {
                    thread {
                        play(torr, it, true)
                    }
                })
                addItemDecoration(DividerItemDecoration(this@PlayActivity, LinearLayout.VERTICAL))
            }
        }
    }

    fun play(torr: JSObject, file: JSObject, force: Boolean) {
        useRemote = false
        showProgress()
        setInfo(getString(R.string.buffering) + "...")

        Torrent.preload(torr, file, {
            val preloadedBytes = it.getLong("PreloadedBytes", 0L)
            val preloadSize = it.getLong("PreloadSize", 0L)
            val activePeers = it.getInt("ActivePeers", 0)
            val totalPeers = it.getInt("TotalPeers", 0)
            val connectedSeeders = it.getInt("ConnectedSeeders", 0)
            val downloadSpeed = it.getDouble("DownloadSpeed", 0.0)

            var buffer = ""
            var prc = 0
            if (preloadSize > 0) {
                prc = (preloadedBytes * 100 / preloadSize).toInt()
                buffer = (prc).toString() + "% " + ByteFmt.byteFmt(preloadedBytes) + "/" + ByteFmt.byteFmt(preloadSize)
            }
            val peers = "[$connectedSeeders] $activePeers/$totalPeers"
            val speed = ByteFmt.byteFmt(downloadSpeed) + "/s"
            showProgress(prc)
            setInfo("", file, buffer, peers, speed)
        }, {
            App.Toast(it)
            isClosed = true
            finish(false, it)
        })

        if (!isClosed) {

            ad?.waitAd()

            val link = file.getString("Play", "")
            val name = file.getString("Name", "")

            val addr = Preferences.getCurrentHost() + link
            val pkg = Preferences.getPlayer()

            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(addr))
            val mime = Mime.getMimeType(name)
            intent.setDataAndType(Uri.parse(addr), mime)
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            intent.putExtra("title", name)

            finish(true, "", name)

            if (force) {
                intent.setDataAndType(Uri.parse(addr), "*/*")
                val intentC = Intent.createChooser(intent, "")
                startActivity(intentC)
                return
            }

            if (pkg.isEmpty() or pkg.equals("0")) {
                if (intent.resolveActivity(packageManager) != null) {
                    startActivity(intent)
                    return
                }
            }

            if (pkg.isNotEmpty() and !pkg.equals("0") and !pkg.equals("1")) {
                intent.`package` = pkg
                if (intent.resolveActivity(packageManager) != null) {
                    startActivity(intent)
                    return
                }
                intent.`package` = ""
            }

            val intentC = Intent.createChooser(intent, "")
            startActivity(intentC)
        }
    }

    private fun showProgress(prog: Int = 0) {
        Handler(Looper.getMainLooper()).post {
            progress.visibility = View.VISIBLE
            list.visibility = View.GONE
            progressBar.isIndeterminate = prog == 0
            if (prog > 0) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N)
                    progressBar.setProgress(prog, true)
                else
                    progressBar.setProgress(prog)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                    // color Progress: https://stackoverflow.com/questions/2020882/how-to-change-progress-bars-progress-color-in-android
                    var progressDrawable = progressBar.getProgressDrawable().mutate()
                    progressDrawable.setColorFilter(ContextCompat.getColor(this, R.color.colorAccent), android.graphics.PorterDuff.Mode.SRC_IN)
                    progressBar.setProgressDrawable(progressDrawable)
                }
            }
        }
    }

    private fun hideProgress() {
        Handler(Looper.getMainLooper()).post {
            progress.visibility = View.GONE
            list.visibility = View.VISIBLE
            setInfo()
            progressBar.isIndeterminate = true
        }
    }

    private fun setInfo(info: String = "", file: JSObject? = null, buffer: String = "", peers: String = "", speed: String = "") {
        Handler(Looper.getMainLooper()).post {
            var title = ""
            title = this.title
            torrent?.let { torr ->
                if (title.isEmpty()) {
                    title = torr.get("Name", "")
                    if (title.isEmpty())
                        title = torr.getString("Title", "")
                }
            }

            if (title.isEmpty())
                findViewById<TextView>(R.id.tvTitle).visibility = View.GONE
            else {
                findViewById<TextView>(R.id.tvTitle).visibility = View.VISIBLE
                findViewById<TextView>(R.id.tvTitle).text = title
            }

            file?.let {
                var name = file.getString("Name", "")
                if (name.isNotEmpty())
                    name = File(file.getString("Name", "")).name

                findViewById<TextView>(R.id.tvFileName).visibility = View.VISIBLE
                findViewById<TextView>(R.id.tvFileSize).visibility = View.VISIBLE

                findViewById<TextView>(R.id.tvFileName).setText(name)

                val size = file.getLong("Size", -1)
                if (size >= 0) {
                    val boldText = getString(R.string.size) + ": "
                    val s = SpannableStringBuilder(boldText + "${ByteFmt.byteFmt(size)}")
                    s.setSpan(StyleSpan(Typeface.BOLD), 0, boldText.length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
                    findViewById<TextView>(R.id.tvFileSize).setText(s)
                }
            } ?: let {
                findViewById<TextView>(R.id.tvFileName).visibility = View.GONE
                findViewById<TextView>(R.id.tvFileSize).visibility = View.GONE
            }

            if (buffer.isNotEmpty()) {
                val boldText = getString(R.string.buffer) + ": "
                val s = SpannableStringBuilder(boldText + buffer)
                s.setSpan(StyleSpan(Typeface.BOLD), 0, boldText.length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
                findViewById<TextView>(R.id.tvBuffer).visibility = View.VISIBLE
                findViewById<TextView>(R.id.tvBuffer).setText(s)
            } else
                findViewById<TextView>(R.id.tvBuffer).visibility = View.GONE

            if (peers.isNotEmpty()) {
                val boldText = getString(R.string.peers) + ": "
                val s = SpannableStringBuilder(boldText + peers)
                s.setSpan(StyleSpan(Typeface.BOLD), 0, boldText.length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
                findViewById<TextView>(R.id.tvPeers).visibility = View.VISIBLE
                findViewById<TextView>(R.id.tvPeers).setText(s)
            } else
                findViewById<TextView>(R.id.tvPeers).visibility = View.GONE

            if (speed.isNotEmpty()) {
                val boldText = getString(R.string.download_speed) + ": "
                val s = SpannableStringBuilder(boldText + speed)
                s.setSpan(StyleSpan(Typeface.BOLD), 0, boldText.length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
                findViewById<TextView>(R.id.tvSpeed).visibility = View.VISIBLE
                findViewById<TextView>(R.id.tvSpeed).setText(s)
            } else
                findViewById<TextView>(R.id.tvSpeed).visibility = View.GONE
            if (info.isNotEmpty()) {
                findViewById<TextView>(R.id.tvConnections).visibility = View.VISIBLE
                findViewById<TextView>(R.id.tvConnections).setText(info)
            } else
                findViewById<TextView>(R.id.tvConnections).visibility = View.GONE
        }
    }

    private fun setFocusItem(pos: Int, timeout: Long = 500) {
        Handler(Looper.getMainLooper()).post {
            findViewById<RecyclerView>(R.id.rvFileList)?.apply {
                postDelayed({
                    layoutManager?.scrollToPosition(pos)
                    postDelayed({
                        findViewHolderForAdapterPosition(pos)?.itemView?.requestFocus()
                    }, timeout / 2)
                }, timeout)
            }
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

    fun finish(isStartPlay: Boolean, error: String = "", fname: String = "") {
        ad?.waitAd()
        val intent = Intent()
        intent.putExtra("isStartPlay", isStartPlay)
        intent.putExtra("error", error)
        intent.putExtra("fileName", fname)
        torrent?.let {
            intent.putExtra("torrent", it.js.toString(0))
        }
        setResult(Activity.RESULT_OK, intent)
        finish()
    }

    override fun onBackPressed() {
        isClosed = true
        super.onBackPressed()
    }
}
