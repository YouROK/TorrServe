package ru.yourok.torrserve.ui.fragments.rutor

import android.annotation.SuppressLint
import android.graphics.drawable.Drawable
import android.text.SpannableString
import android.text.Spanned
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.appcompat.content.res.AppCompatResources
import androidx.recyclerview.widget.RecyclerView
import ru.yourok.torrserve.R
import ru.yourok.torrserve.ext.normalize
import ru.yourok.torrserve.server.models.torrent.TorrentDetails
import ru.yourok.torrserve.utils.CImageSpan
import ru.yourok.torrserve.utils.Format.sDateFmt
import ru.yourok.torrserve.utils.SpanFormat

class TorrentsAdapter : RecyclerView.Adapter<TorrentsAdapter.ViewHolder>() {
    val list = mutableListOf<TorrentDetails>()

    var onClick: ((TorrentDetails) -> Unit)? = null
    var onLongClick: ((TorrentDetails) -> Unit)? = null

    @SuppressLint("NotifyDataSetChanged")
    fun set(tds: List<TorrentDetails>) {
        try {
            this.list.clear()
            this.list.addAll(tds)
            notifyDataSetChanged()
        } catch (_: Exception) {
        }
    }

    class ViewHolder(val view: View, val adapter: TorrentsAdapter) : RecyclerView.ViewHolder(view) {
        init {
            view.setOnClickListener {
                adapter.onClick?.invoke(adapter.list[adapterPosition])
            }
            view.setOnLongClickListener {
                adapter.onLongClick?.invoke(adapter.list[adapterPosition])
                true
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val vi = LayoutInflater.from(parent.context).inflate(R.layout.torrent_details_item, parent, false)
        return ViewHolder(vi, this)
    }

    @SuppressLint("SetTextI18n")
    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val torr = list[position]
        val sDate = sDateFmt(torr.CreateDate)
        val category = torr.Categories.normalize()
        if (category.isNotBlank()) {
            val sIcon = SpannableString(" ")
            val cDrawable: Drawable? = when {
                category.contains("movie", true) -> AppCompatResources.getDrawable(holder.view.context, R.drawable.round_movie_24)
                category.contains("tv", true) -> AppCompatResources.getDrawable(holder.view.context, R.drawable.round_live_tv_24)
                category.contains("music", true) -> AppCompatResources.getDrawable(holder.view.context, R.drawable.round_music_note_24)
                category.contains("other", true) -> AppCompatResources.getDrawable(holder.view.context, R.drawable.round_more_horiz_24)
                else -> null
            }
            if (cDrawable == null)
                holder.view.findViewById<TextView>(R.id.tvName).text = "${category.replaceFirstChar{ if (it.isLowerCase()) it.titlecase() else it.toString() }} ● ${torr.Title}"
            else {
                cDrawable.setBounds(0, 0, cDrawable.intrinsicWidth, cDrawable.intrinsicHeight)
                val span = CImageSpan(cDrawable)
                sIcon.setSpan(span, 0, 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                holder.view.findViewById<TextView>(R.id.tvName).text = SpanFormat.format("%s ${torr.Title}", sIcon)
            }
        } else
            holder.view.findViewById<TextView>(R.id.tvName).text = torr.Title
        holder.view.findViewById<TextView>(R.id.tvInfo1).text = "${torr.Size} ▲${torr.Seed} ▼${torr.Peer}"
        holder.view.findViewById<TextView>(R.id.tvInfo2).text = sDate // Hash.uppercase()
    }

    override fun getItemCount() = list.size

}