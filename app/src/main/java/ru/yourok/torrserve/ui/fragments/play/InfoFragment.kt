package ru.yourok.torrserve.ui.fragments.play

import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.text.Html
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import com.bumptech.glide.Glide
import com.bumptech.glide.load.resource.bitmap.BitmapTransitionOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import ru.yourok.torrserve.R
import ru.yourok.torrserve.server.models.torrent.FileStat
import ru.yourok.torrserve.services.TorrService
import ru.yourok.torrserve.ui.activities.play.PlayActivity
import ru.yourok.torrserve.ui.fragments.TSFragment
import ru.yourok.torrserve.ui.fragments.play.viewmodels.InfoTorrent
import ru.yourok.torrserve.ui.fragments.play.viewmodels.InfoViewModel
import ru.yourok.torrserve.utils.ByteFmt
import ru.yourok.torrserve.utils.TorrentHelper
import java.io.File

open class InfoFragment : TSFragment() {

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val vi = inflater.inflate(R.layout.info_fragment, container, false)
        TorrService.start()
        return vi
    }

    suspend fun startInfo(hash: String) = withContext(Dispatchers.Main) {
        viewModel = ViewModelProvider(this@InfoFragment).get(InfoViewModel::class.java)
        val data = (viewModel as InfoViewModel).setTorrent(hash)
        data.observe(this@InfoFragment) {
            updateUI(it, (requireActivity() as PlayActivity).torrentFileIndex)
        }
    }

    private var poster = " "

    private fun updateUI(info: InfoTorrent, index: Int) {
        lifecycleScope.launch {
            if (info.torrent == null && info.error.isNotEmpty()) {
                view?.findViewById<TextView>(R.id.tvInfo)?.text = info.error
                return@launch
            }
            info.torrent?.let { torr ->
                view?.apply {
                    if (poster != torr.poster) {
                        poster = torr.poster
                        if (poster.isNotEmpty())
                            findViewById<ImageView>(R.id.ivPoster)?.let {
                                it.visibility = View.VISIBLE
                                Glide.with(this)
                                    .asBitmap()
                                    .load(poster)
                                    .fitCenter()
                                    .placeholder(ColorDrawable(0x3c000000))
                                    .transition(BitmapTransitionOptions.withCrossFade())
                                    .into(it)
                            }
                        else
                            findViewById<ImageView>(R.id.ivPoster)?.visibility = View.GONE
                    }

                    val title = torr.title
                    if (title.isEmpty())
                        findViewById<TextView>(R.id.tvTitle).visibility = View.GONE
                    else {
                        findViewById<TextView>(R.id.tvTitle).visibility = View.VISIBLE
                        findViewById<TextView>(R.id.tvTitle).text = title
                    }

                    val file: FileStat? = if (index >= 0) torr.file_stats?.get(index) else null

                    file?.let {
                        var name = it.path
                        if (name.isNotEmpty())
                            name = File(name).name

                        findViewById<TextView>(R.id.tvFileName).visibility = View.VISIBLE
                        findViewById<TextView>(R.id.tvFileSize).visibility = View.VISIBLE

                        findViewById<TextView>(R.id.tvFileName).text = name

                        val size = it.length
                        if (size >= 0) {
                            val txt = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.N)
                                Html.fromHtml("<b>${getString(R.string.size)}:</b> ${ByteFmt.byteFmt(size)}", Html.FROM_HTML_MODE_COMPACT)
                            else
                                Html.fromHtml("<b>${getString(R.string.size)}:</b> ${ByteFmt.byteFmt(size)}")
                            findViewById<TextView>(R.id.tvFileSize).text = txt
                        }
                    } ?: let {
                        findViewById<TextView>(R.id.tvFileName).visibility = View.GONE
                        findViewById<TextView>(R.id.tvFileSize).visibility = View.GONE
                    }

                    var buffer = ""
                    var prc = 0.0
                    if (torr.preload_size > 0 && torr.preloaded_bytes > 0) {
                        prc = torr.preloaded_bytes.toDouble() * 100.0 / torr.preload_size.toDouble()
                        if (prc < 100.0)
                            buffer = "%.02f".format(prc) + "% "
                        buffer += ByteFmt.byteFmt(torr.preloaded_bytes)
                        if (prc < 100.0)
                            buffer += "/" + ByteFmt.byteFmt(torr.preload_size)
                    }

                    if (buffer.isNotEmpty()) {
                        val txt = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.N)
                            Html.fromHtml("<b>${getString(R.string.buffer)}:</b> ${buffer}", Html.FROM_HTML_MODE_COMPACT)
                        else
                            Html.fromHtml("<b>${getString(R.string.buffer)}:</b> ${buffer}")
                        findViewById<TextView>(R.id.tvBuffer).text = txt
                    }

                    if (torr.stat < TorrentHelper.TorrentSTWorking) {
                        if (prc > 0 && prc < 100)
                            (activity as PlayActivity?)?.showProgress(prc.toInt())
                        else
                            (activity as PlayActivity?)?.showProgress(-1)
                    } else
                        (activity as PlayActivity?)?.hideProgress()

                    val peers = "[${torr.connected_seeders}] ${torr.active_peers}/${torr.total_peers}"
                    if (peers.isNotEmpty()) {
                        val txt = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.N)
                            Html.fromHtml("<b>${getString(R.string.peers)}:</b> ${peers}", Html.FROM_HTML_MODE_COMPACT)
                        else
                            Html.fromHtml("<b>${getString(R.string.peers)}:</b> ${peers}")

                        findViewById<TextView>(R.id.tvPeers).text = txt
                    }

                    val speed = ByteFmt.byteFmt(torr.download_speed) + "/s"

                    if (speed.isNotEmpty() && !speed.equals("0.0 B/s", true)) {
                        val txt = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.N)
                            Html.fromHtml("<b>${getString(R.string.download_speed)}:</b> ${speed}", Html.FROM_HTML_MODE_COMPACT)
                        else
                            Html.fromHtml("<b>${getString(R.string.download_speed)}:</b> ${speed}")
                        findViewById<TextView>(R.id.tvSpeed).text = txt
                    }

                    view?.findViewById<TextView>(R.id.tvInfo)?.text = torr.stat_string

                }
            }
        }
    }
}