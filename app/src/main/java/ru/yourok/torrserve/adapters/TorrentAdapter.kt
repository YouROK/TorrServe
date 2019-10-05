package ru.yourok.torrserve.adapters

import android.app.Activity
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.ImageView
import android.widget.TextView
import com.google.gson.Gson
import com.squareup.picasso.Picasso
import ru.yourok.torrserve.R
import ru.yourok.torrserve.num.entity.Entity
import ru.yourok.torrserve.server.api.Api
import ru.yourok.torrserve.server.api.JSObject
import ru.yourok.torrserve.utils.ByteFmt


class TorrentAdapter(private val activity: Activity) : BaseAdapter() {
    private var torrList: List<JSObject> = listOf()

    fun checkList() {
        try {
            if (Api.serverEcho().isEmpty()) {
                if (torrList.isNotEmpty()) {
                    synchronized(torrList) {
                        torrList = listOf()
                        Handler(Looper.getMainLooper()).post {
                            notifyDataSetChanged()
                        }
                    }
                }
                return
            }

            val tmpList = Api.torrentList()
            if (tmpList.size != torrList.size) {
                synchronized(torrList) {
                    torrList = tmpList
                    Handler(Looper.getMainLooper()).post {
                        notifyDataSetChanged()
                    }
                }
            } else
                tmpList.forEachIndexed { index, js ->
                    if (js.toString() != torrList[index].toString()) {
                        synchronized(torrList) {
                            torrList = tmpList
                            Handler(Looper.getMainLooper()).post {
                                notifyDataSetChanged()
                            }
                        }
                        return@forEachIndexed
                    }
                }
        } catch (e: Exception) {
        }
    }


    override fun getView(position: Int, view: View?, parent: ViewGroup?): View {
        val vi: View = view ?: (activity.getSystemService(Context.LAYOUT_INFLATER_SERVICE) as LayoutInflater).inflate(R.layout.torrent_view, null)
        var name = torrList[position].get("Name", "")
        val magnet = torrList[position].get("Hash", "")
        val length = torrList[position].get("Length", 0L)
        val info = torrList[position].get("Info", "")

        vi.findViewById<ImageView>(R.id.ivPoster)?.visibility = View.GONE

        if (info.isNotEmpty()) {
            val gson = Gson()
            val ent = gson.fromJson<Entity>(info, Entity::class.java)

            ent?.let {
                it.title?.let { name = it }

                ent.poster_path?.let { poster ->
                    if (poster.isNotEmpty())
                        vi.findViewById<ImageView>(R.id.ivPoster)?.let {
                            val picass = Picasso.get().load(poster).placeholder(R.color.lighter_gray).fit().centerCrop()
                            picass.into(it)
                            it.visibility = View.VISIBLE
                        }
                }
            }
        }

        vi.findViewById<TextView>(R.id.tvTorrName)?.text = name
        vi.findViewById<TextView>(R.id.tvTorrMagnet)?.text = magnet.toUpperCase()
        vi.findViewById<TextView>(R.id.tvTorrSize)?.text = ByteFmt.byteFmt(length)

        return vi
    }

    override fun getItem(p0: Int): Any {
        if (p0 < 0 || p0 >= torrList.size)
            return JSObject()
        return torrList[p0]
    }

    override fun getItemId(p0: Int): Long {
        return p0.toLong()
    }

    override fun getCount(): Int = torrList.size
}