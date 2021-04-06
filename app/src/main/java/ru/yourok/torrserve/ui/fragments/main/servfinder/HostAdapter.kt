package ru.yourok.torrserve.ui.fragments.main.servfinder

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import ru.yourok.torrserve.R
import ru.yourok.torrserve.settings.Settings

class HostAdapter : RecyclerView.Adapter<HostAdapter.ViewHolder>() {
    val hosts = mutableListOf<ServerIp>()

    var onClick: ((String) -> Unit)? = null

    fun insert(servIp: ServerIp) {
        try {
            if (hosts.find { it.host == servIp.host } == null) {
                hosts.add(0, servIp)
                notifyItemInserted(0)
            }
        } catch (e: Exception) {
        }
    }

    fun add(servIp: ServerIp) {
        try {
            if (hosts.find { it.host == servIp.host } == null) {
                hosts.add(servIp)
                notifyItemInserted(hosts.size - 1)
            }
        } catch (e: Exception) {
        }
    }

    fun clear() {
        try {
            hosts.clear()
            notifyDataSetChanged()
        } catch (e: Exception) {
        }
    }


    class ViewHolder(val view: View, val adapter: HostAdapter) : RecyclerView.ViewHolder(view) {
        init {
            view.setOnClickListener {
                adapter.onClick?.invoke(adapter.hosts[adapterPosition].host)
            }
            view.setOnLongClickListener {
                val lst = Settings.getHosts().toMutableList()
                if (lst.remove(adapter.hosts[adapterPosition].host)) {
                    Settings.setHosts(lst)
                    rem(adapter, adapter.hosts[adapterPosition])
                }
                true
            }
        }
    }


    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val vi = LayoutInflater.from(parent.context).inflate(R.layout.host_item, parent, false)
        return ViewHolder(vi, this)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.view.findViewById<TextView>(R.id.tvHost).text = hosts[position].host

        val version = hosts[position].version
        // set online badge by added version
        if (version.contains("·", true) || version.startsWith("1.2.") || version.startsWith("MatriX"))
            holder.view.findViewById<ImageView>(R.id.ivOnline)?.visibility = View.VISIBLE
        else
            holder.view.findViewById<ImageView>(R.id.ivOnline)?.visibility = View.INVISIBLE

        holder.view.findViewById<TextView>(R.id.tvVersion).text = version
    }

    override fun getItemCount() = hosts.size

    companion object {
        fun rem(hostAdapter: HostAdapter, servIp: ServerIp) {
            try {
                val pos = hostAdapter.hosts.indexOf(servIp)
                if (pos != -1) {
                    hostAdapter.hosts.removeAt(pos)
                    hostAdapter.notifyItemRemoved(pos)
                }
            } catch (e: Exception) {
            }
        }
    }

}