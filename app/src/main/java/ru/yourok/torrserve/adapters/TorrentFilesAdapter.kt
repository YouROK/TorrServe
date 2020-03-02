package ru.yourok.torrserve.adapters

import android.support.v7.widget.RecyclerView
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import ru.yourok.torrserve.R
import ru.yourok.torrserve.server.api.JSObject
import ru.yourok.torrserve.utils.ByteFmt

class TorrentFilesAdapter(val files: List<JSObject>, val onClick: (file: JSObject) -> Unit, val onLongClick: (file: JSObject) -> Unit) : RecyclerView.Adapter<TorrentFilesAdapter.ViewHolder>() {

    class ViewHolder(val view: View, val adapter: TorrentFilesAdapter) : RecyclerView.ViewHolder(view) {
        init {
            view.setOnClickListener {
                val file = adapter.files[adapterPosition]
                adapter.onClick(file)
            }
            view.setOnLongClickListener {
                val file = adapter.files[adapterPosition]
                adapter.onLongClick(file)
                true
            }
        }
    }


    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): TorrentFilesAdapter.ViewHolder {
        val vi = LayoutInflater.from(parent.context).inflate(R.layout.torrent_files_view, parent, false)
        return ViewHolder(vi, this)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        var name = files[position].get("Name", "")
        val size = files[position].get("Size", 0L)
        val viewed = files[position].get("Viewed", false)


        val ssize = ByteFmt.byteFmt(size)
        holder.view.findViewById<TextView>(R.id.tvFileName)?.text = name
        holder.view.findViewById<TextView>(R.id.tvFileSize)?.text = ssize
        if (viewed)
            holder.view.findViewById<ImageView>(R.id.ivPlayed)?.visibility = View.VISIBLE
        else
            holder.view.findViewById<ImageView>(R.id.ivPlayed)?.visibility = View.GONE
    }

    override fun getItemCount() = files.size
}