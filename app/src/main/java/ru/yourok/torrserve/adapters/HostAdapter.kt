package ru.yourok.torrserve.adapters

import android.support.v7.widget.RecyclerView
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import ru.yourok.torrserve.R
import ru.yourok.torrserve.app.App
import ru.yourok.torrserve.server.finder.FinderServer
import ru.yourok.torrserve.server.finder.ServerIp
import ru.yourok.torrserve.serverloader.ServerFile
import kotlin.concurrent.thread

class HostAdapter(val onClick: (host: String) -> Unit) : RecyclerView.Adapter<HostAdapter.ViewHolder>() {

    val finder = FinderServer()
    val hosts = mutableListOf<ServerIp>()

    fun update(onFinish: () -> Unit): List<String> {
        val ipLst = finder.getLocalIPs()
        if (hosts.isNotEmpty()) {
            notifyItemRangeRemoved(0, hosts.size)
            hosts.clear()
        }
        thread {
            for (ip in ipLst) {
                finder.findServers(ip) {
                    hosts.add(it)
                    if (hosts.isEmpty())
                        notifyItemInserted(0)
                    else
                        notifyItemInserted(hosts.size - 1)
                }
            }

            if (ServerFile.serverExists()) {
                var isLocal = false
                for (ip in hosts)
                    if (ip.host.contains("127.0.0.1"))
                        isLocal = true
                if (!isLocal) {
                    hosts.add(0, ServerIp("http://127.0.0.1:8090", App.getContext().getString(R.string.local_server)))
                    notifyItemInserted(0)
                }
            }

            onFinish()
        }
        return ipLst.map { it.address.hostAddress }
    }

    class ViewHolder(val view: View, val adapter: HostAdapter) : RecyclerView.ViewHolder(view) {
        init {
            view.setOnClickListener {
                adapter.onClick(adapter.hosts[adapterPosition].host)
            }
        }
    }


    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): HostAdapter.ViewHolder {
        val vi = LayoutInflater.from(parent.context).inflate(R.layout.host_item_view, parent, false)
        return ViewHolder(vi, this)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.view.findViewById<TextView>(R.id.tvHost).text = hosts[position].host

        var version = hosts[position].version

        if (hosts[position].host.indexOf("127.0.0.1") != -1) {
            version += "  *"
        }

        holder.view.findViewById<TextView>(R.id.tvVersion).text = version
    }

    override fun getItemCount() = hosts.size
}