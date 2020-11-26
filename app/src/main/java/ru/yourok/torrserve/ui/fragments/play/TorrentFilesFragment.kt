package ru.yourok.torrserve.ui.fragments.play

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ListView
import androidx.fragment.app.FragmentActivity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import ru.yourok.torrserve.R
import ru.yourok.torrserve.server.api.Viewed
import ru.yourok.torrserve.server.models.torrent.FileStat
import ru.yourok.torrserve.server.models.torrent.Torrent
import ru.yourok.torrserve.services.TorrService
import ru.yourok.torrserve.ui.fragments.TSFragment
import ru.yourok.torrserve.ui.fragments.play.adapters.TorrentFilesAdapter
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
    private lateinit var torrent: Torrent
    private var viewed: List<Viewed>? = null
    private var onClickItem: ((file: FileStat) -> Unit)? = null

    suspend fun showTorrent(activity: FragmentActivity, torr: Torrent, viewed: List<Viewed>?, onClickItem: (file: FileStat) -> Unit) = withContext(Dispatchers.Main) {
        torrent = torr
        this@TorrentFilesFragment.viewed = viewed
        this@TorrentFilesFragment.onClickItem = onClickItem
        torrFilesAdapter.update(torrent, viewed)
        show(activity, R.id.bottom_container)
    }

    suspend fun disableList() = withContext(Dispatchers.Main) {
        view?.findViewById<ListView>(R.id.lvTorrentFiles)?.isEnabled = false
    }

    suspend fun enableList() = withContext(Dispatchers.Main) {
        view?.findViewById<ListView>(R.id.lvTorrentFiles)?.isEnabled = true
    }

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        super.onActivityCreated(savedInstanceState)
        view?.apply {
            var last = 0
            viewed?.forEach { last = max(last, it.file_index) }
            val file = TorrentHelper.findFile(torrent, last + 1)
            val next = file?.id ?: last

            findViewById<Button>(R.id.btnPlaylist).setOnClickListener { }
            findViewById<Button>(R.id.btnPlaylistContinue).setOnClickListener { }
            findViewById<ListView>(R.id.lvTorrentFiles).apply {
                adapter = torrFilesAdapter
                setOnItemClickListener { parent, view, position, id ->
                    val file = torrent.file_stats?.get(position) ?: return@setOnItemClickListener
                    onClickItem?.invoke(file)
                }
                postDelayed({
                    setSelection(next)
                }, 500)
            }
        }
    }
}