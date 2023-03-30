package ru.yourok.torrserve.ui.fragments.main.torrents


import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.ImageView
import android.widget.TextView
import androidx.fragment.app.FragmentActivity
import com.bumptech.glide.Glide
import com.bumptech.glide.load.resource.bitmap.BitmapTransitionOptions
import com.bumptech.glide.load.resource.bitmap.RoundedCorners
import com.bumptech.glide.request.RequestOptions
import ru.yourok.torrserve.R
import ru.yourok.torrserve.server.models.torrent.Torrent
import ru.yourok.torrserve.settings.Settings
import ru.yourok.torrserve.utils.Format
import java.util.Locale


class TorrentsAdapter(private val activity: FragmentActivity) : BaseAdapter() {
    private val list = mutableListOf<Torrent>()

    fun update(list: List<Torrent>) {
        if (this.list.size != list.size) {
            this.list.clear()
            this.list.addAll(list)
            notifyDataSetChanged()
        } else {
            var changed = false
            for (i in list.indices) {
                if (this.list[i] != list[i]) {
                    this.list[i] = list[i]
                    changed = true
                }
            }
            if (changed)
                notifyDataSetChanged()
        }
    }

    override fun getView(position: Int, view: View?, parent: ViewGroup?): View {
        val vi = view ?: LayoutInflater.from(parent?.context).inflate(R.layout.torrent_item, parent, false)

        var title = list[position].title
        val poster = list[position].poster
        val hash = list[position].hash
        val size = list[position].torrent_size
        val addTime = list[position].timestamp
        var addStr = ""

        if (addTime > 0)
            addStr = Format.sDateFmt(addTime)

        if (title.isBlank())
            title = list[position].name

        vi.findViewById<ImageView>(R.id.ivPoster)?.visibility = View.GONE
        if (poster.isNotEmpty() && Settings.showCover())
            vi.findViewById<ImageView>(R.id.ivPoster)?.let {
                it.visibility = View.VISIBLE
                Glide.with(activity)
                    .asBitmap()
                    .load(poster)
                    .centerCrop()
                    //.placeholder(ColorDrawable(0x3c000000))
                    .apply(RequestOptions.bitmapTransform(RoundedCorners(5)))
                    .transition(BitmapTransitionOptions.withCrossFade())
                    .into(it)
            }

        vi.findViewById<TextView>(R.id.tvTorrName)?.text = title
        vi.findViewById<TextView>(R.id.tvTorrHash)?.text = hash.uppercase(Locale.getDefault())
        val td = vi.findViewById<TextView>(R.id.tvTorrDate)
        val ts = vi.findViewById<TextView>(R.id.tvTorrSize)
        if (addStr.isNotEmpty()) {
            td?.text = addStr
            td?.visibility = View.VISIBLE
        } else
            td?.visibility = View.GONE

        if (size > 0.0) {
            ts?.text = Format.byteFmt(size)
            ts?.visibility = View.VISIBLE
        } else
            ts?.visibility = View.GONE

        return vi
    }

    override fun getItem(p0: Int): Any? {
        if (p0 < 0 || p0 >= list.size)
            return null
        return list[p0]
    }

    override fun getItemId(p0: Int): Long {
        return p0.toLong()
    }

    override fun getCount(): Int = list.size
}