package ru.yourok.torrserve.ui.fragments.play.adapters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import ru.yourok.torrserve.R
import ru.yourok.torrserve.server.api.Api
import ru.yourok.torrserve.server.api.Viewed
import ru.yourok.torrserve.server.models.torrent.Torrent
import ru.yourok.torrserve.utils.ByteFmt
import java.io.File

class TorrentFilesAdapter : BaseAdapter() {
    private var torrent: Torrent? = null
    private var viewed = listOf<Viewed>()
    private val lock = Any()

    fun update(activity: AppCompatActivity, torrent: Torrent) {
        this.torrent = torrent
        notifyDataSetChanged()
        activity.lifecycleScope.launch {
            synchronized(lock) {
                viewed = Api.listViewed(torrent.hash)
            }
        }
    }

    override fun getView(position: Int, view: View?, parent: ViewGroup?): View {
        val vi = view ?: LayoutInflater.from(parent?.context).inflate(R.layout.torrent_files_item, parent, false)
        val file = torrent?.file_stats?.get(position) ?: return vi
        val title = File(file.path).name
        val size = ByteFmt.byteFmt(file.length)

        vi.findViewById<TextView>(R.id.tvFileName)?.text = title
        vi.findViewById<TextView>(R.id.tvFileSize)?.text = size

        vi.findViewById<ImageView>(R.id.ivViewed)?.apply {
            visibility = View.GONE
            GlobalScope.launch(Dispatchers.IO) {
                synchronized(lock) {
                    viewed.forEach {
                        if (it.FileIndex == file.id)
                            launch(Dispatchers.Main) {
                                visibility = View.VISIBLE
                            }
                    }
                }
            }
        }

        return vi
    }

    override fun getItem(p0: Int): Any? {
        if (p0 < 0 || p0 >= torrent?.file_stats?.size ?: 0)
            return null
        return torrent?.file_stats?.get(p0)
    }

    override fun getItemId(p0: Int): Long {
        return p0.toLong()
    }

    override fun getCount(): Int = torrent?.file_stats?.size ?: 0
}