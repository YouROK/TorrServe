package ru.yourok.torrserve.ui.fragments.rutor

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import ru.yourok.torrserve.R
import ru.yourok.torrserve.server.models.torrent.TorrentDetails

class TorrentsAdapter : RecyclerView.Adapter<TorrentsAdapter.ViewHolder>() {
    val list = mutableListOf<TorrentDetails>()

    var onClick: ((TorrentDetails) -> Unit)? = null

    fun set(torrs: List<TorrentDetails>) {
        try {
            this.list.clear()
            this.list.addAll(torrs)
            notifyDataSetChanged()
        } catch (e: Exception) {
        }
    }

    class ViewHolder(val view: View, val adapter: TorrentsAdapter) : RecyclerView.ViewHolder(view) {
        init {
            view.setOnClickListener {
                adapter.onClick?.invoke(adapter.list[adapterPosition])
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val vi = LayoutInflater.from(parent.context).inflate(R.layout.torrent_details_item, parent, false)
        return ViewHolder(vi, this)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val torr = list[position]
        holder.view.findViewById<TextView>(R.id.tvName).text = torr.Title
        holder.view.findViewById<TextView>(R.id.tvInfo1).text = "${torr.Size} ▲${torr.Seed} ▼${torr.Peer}"
        holder.view.findViewById<TextView>(R.id.tvInfo2).text = torr.Hash.uppercase()
    }

    override fun getItemCount() = list.size

}