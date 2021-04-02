package ru.yourok.torrserve.ui.fragments.main.servfinder

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import ru.yourok.torrserve.R

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
        }
    }


    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val vi = LayoutInflater.from(parent.context).inflate(R.layout.host_item, parent, false)
        return ViewHolder(vi, this)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.view.findViewById<TextView>(R.id.tvHost).text = hosts[position].host

        val version = hosts[position].version
//        if (hosts[position].host.indexOf("127.0.0.1") != -1) {
//            version += " · ${App.context.getString(R.string.on_device)}"
//        }
        // set online badge by version name
        if (version.contains("MatriX", true))
            holder.view.findViewById<ImageView>(R.id.ivOnline)?.visibility = View.VISIBLE
        else
            holder.view.findViewById<ImageView>(R.id.ivOnline)?.visibility = View.INVISIBLE

        holder.view.findViewById<TextView>(R.id.tvVersion).text = version
    }

    override fun getItemCount() = hosts.size
}