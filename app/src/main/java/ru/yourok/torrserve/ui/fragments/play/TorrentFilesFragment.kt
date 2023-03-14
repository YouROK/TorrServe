package ru.yourok.torrserve.ui.fragments.play

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ListView
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import ru.yourok.torrserve.R
import ru.yourok.torrserve.app.App
import ru.yourok.torrserve.ext.urlEncode
import ru.yourok.torrserve.server.api.Api
import ru.yourok.torrserve.server.api.Viewed
import ru.yourok.torrserve.server.local.TorrService
import ru.yourok.torrserve.server.models.torrent.FileStat
import ru.yourok.torrserve.server.models.torrent.Torrent
import ru.yourok.torrserve.ui.fragments.TSFragment
import ru.yourok.torrserve.ui.fragments.play.adapters.TorrentFilesAdapter
import ru.yourok.torrserve.utils.Net
import ru.yourok.torrserve.utils.TorrentHelper
import kotlin.math.max

class TorrentFilesFragment : TSFragment() {
    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val vi = inflater.inflate(R.layout.torrent_file_fragment, container, false)
        TorrService.start()
        return vi
    }

    private val torrFilesAdapter = TorrentFilesAdapter()
    private var torrent: Torrent? = null
    private var viewed: List<Viewed>? = null
    private var onClickItem: ((file: FileStat) -> Unit)? = null

    suspend fun showTorrent(activity: FragmentActivity, torr: Torrent, viewed: List<Viewed>?, onClickItem: (file: FileStat) -> Unit) = withContext(Dispatchers.Main) {
        torrent = torr
        this@TorrentFilesFragment.viewed = viewed
        this@TorrentFilesFragment.onClickItem = onClickItem
        torrFilesAdapter.update(torr, viewed)
        show(activity, R.id.bottom_container)
    }

    override fun onResume() {
        super.onResume()
        lifecycleScope.launch(Dispatchers.IO) {
            val viewed = try {
                Api.listViewed(torrent?.hash ?: return@launch)
            } catch (_: Exception) {
                return@launch
            }
            if (viewed.size != torrFilesAdapter.viewed.size)
                withContext(Dispatchers.Main) {
                    torrFilesAdapter.update(torrent ?: return@withContext, viewed)
                    torrFilesAdapter.notifyDataSetChanged()
                }
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        view.apply {
            var last = 0
            viewed?.forEach { last = max(last, it.file_index) }
            val file = TorrentHelper.findFile(torrent ?: return, last)
            val next = if (file != null)
                TorrentHelper.findIndex(torrent ?: return, file)
            else
                last

            findViewById<Button>(R.id.btnPlaylist).setOnClickListener {
                lifecycleScope.launch(Dispatchers.IO) {
                    torrent?.let { torr ->
                        try {
                            if (Api.listTorrent().isNotEmpty()) {
                                val intent = Intent(Intent.ACTION_VIEW)
                                intent.setDataAndType(Uri.parse(Net.getHostUrl("/playlist/${torr.name.urlEncode()}.m3u?hash=${torr.hash}")), "video/*")
                                intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
                                App.context.startActivity(intent)
                            }
                        } catch (e: Exception) {
                            e.message?.let {
                                App.toast(it)
                            }
                        }
                    }
                }
            }
            findViewById<Button>(R.id.btnPlaylistContinue).setOnClickListener {
                lifecycleScope.launch(Dispatchers.IO) {
                    torrent?.let { torr ->
                        try {
                            if (Api.listTorrent().isNotEmpty()) {
                                val intent = Intent(Intent.ACTION_VIEW)
                                intent.setDataAndType(Uri.parse(Net.getHostUrl("/playlist/${torr.name.urlEncode()}.m3u?hash=${torr.hash}&fromlast")), "video/*")
                                intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
                                App.context.startActivity(intent)
                            }
                        } catch (e: Exception) {
                            e.message?.let {
                                App.toast(it)
                            }
                        }
                    }
                }
            }
            findViewById<ListView>(R.id.lvTorrentFiles).apply {
                adapter = torrFilesAdapter
                setOnItemClickListener { _, _, position, _ ->
                    val f = torrFilesAdapter.getItem(position) as FileStat? ?: return@setOnItemClickListener
                    // clear viewed
                    if (torrFilesAdapter.count > 1 && position == count - 1)
                        lifecycleScope.launch(Dispatchers.IO) {
                            torrent?.hash?.let {
                                try {
                                    Api.remViewed(it)
                                } catch (_: Exception) {
                                }
                            }
                        }
                    onClickItem?.invoke(f)
                }
                postDelayed({
                    setSelection(next)
                    requestFocus()
                }, 500)
            }
        }
    }
}