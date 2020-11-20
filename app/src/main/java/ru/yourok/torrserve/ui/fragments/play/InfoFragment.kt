package ru.yourok.torrserve.ui.fragments.play

import android.os.Bundle
import android.text.Html
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ProgressBar
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import ru.yourok.torrserve.R
import ru.yourok.torrserve.server.models.torrent.FileStat
import ru.yourok.torrserve.services.TorrService
import ru.yourok.torrserve.ui.activities.play.PlayActivity
import ru.yourok.torrserve.utils.ByteFmt
import java.io.File

class InfoFragment(val cmd: String) : Fragment() {

    companion object {
        fun newInstance(cmd: String) = InfoFragment(cmd)
    }

    private lateinit var viewModel: InfoViewModel

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val vi = inflater.inflate(R.layout.info_fragment, container, false)
        TorrService.start()
        return vi
    }

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        super.onActivityCreated(savedInstanceState)
        viewModel = ViewModelProvider(this).get(InfoViewModel::class.java)
        when (cmd) {
            "add" -> {
                val link = (requireActivity() as PlayActivity).torrentLink
                val title = (requireActivity() as PlayActivity).torrentTitle
                val poster = (requireActivity() as PlayActivity).torrentPoster
                val data = viewModel.addTorrent(link, title, poster)
                data.observe(this) { updateUI(it, -1) }
            }
            "play" -> {
                val index = (requireActivity() as PlayActivity).torrentFileIndex
                val link = (requireActivity() as PlayActivity).torrentLink
                val title = (requireActivity() as PlayActivity).torrentTitle
                val poster = (requireActivity() as PlayActivity).torrentPoster
                val data = viewModel.addTorrent(link, title, poster)
                viewModel.preloadTorrent(index)
                data.observe(this) { updateUI(it, index) }
            }
        }
    }

    private var poster = ""

    private fun updateUI(info: InfoTorrent, index: Int) {
        lifecycleScope.launch {
            if (info.torrent == null && info.error.isNotEmpty()) {
                view?.findViewById<TextView>(R.id.tvConnections)?.text = info.error
                return@launch
            }
            info.torrent?.let { torr ->
                view?.apply {
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
                    var prc = 0
                    if (torr.preload_size > 0) {
                        prc = (torr.preload_size * 100 / torr.preloaded_bytes).toInt()
                        buffer = (prc).toString() + "% " + ByteFmt.byteFmt(torr.preloaded_bytes) + "/" + ByteFmt.byteFmt(torr.preload_size)
                    }

                    if (buffer.isNotEmpty()) {
                        val txt = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.N)
                            Html.fromHtml("<b>${getString(R.string.buffer)}:</b> ${buffer}", Html.FROM_HTML_MODE_COMPACT)
                        else
                            Html.fromHtml("<b>${getString(R.string.buffer)}:</b> ${buffer}")
                        findViewById<TextView>(R.id.tvBuffer).text = txt
                    }

                    if (prc > 0 && prc < 100) {
                        findViewById<ProgressBar>(R.id.progressBar).isIndeterminate = false
                        findViewById<ProgressBar>(R.id.progressBar).progress = prc
                    } else
                        findViewById<ProgressBar>(R.id.progressBar).isIndeterminate = false

                    val peers = "[${torr.connected_seeders}] ${torr.active_peers}/${torr.total_peers}"
                    if (peers.isNotEmpty()) {
                        val txt = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.N)
                            Html.fromHtml("<b>${getString(R.string.peers)}:</b> ${peers}", Html.FROM_HTML_MODE_COMPACT)
                        else
                            Html.fromHtml("<b>${getString(R.string.peers)}:</b> ${peers}")

                        findViewById<TextView>(R.id.tvPeers).text = txt
                    }

                    val speed = ByteFmt.byteFmt(torr.download_speed) + "/s"

                    if (speed.isNotEmpty()) {
                        val txt = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.N)
                            Html.fromHtml("<b>${getString(R.string.download_speed)}:</b> ${speed}", Html.FROM_HTML_MODE_COMPACT)
                        else
                            Html.fromHtml("<b>${getString(R.string.download_speed)}:</b> ${speed}")
                        findViewById<TextView>(R.id.tvSpeed).text = txt
                    }

                }
            }
        }
    }
}