package ru.yourok.torrserve.ui.fragments.main.servsets

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import ru.yourok.torrserve.R
import java.io.File

class DirectoryAdapter : RecyclerView.Adapter<DirectoryAdapter.ViewHolder>() {
    private var path = ""
    private var list = emptyList<String>()

    var onClick: ((String) -> Unit)? = null

    fun setPath(path: String) {
        this.path = path
        list = File(path).listFiles { dir, name ->
            dir.isDirectory
        }.toList().map { it.name }
        notifyDataSetChanged()
    }

    fun getPath(): String {
        return path
    }

    class ViewHolder(val view: View, val adapter: DirectoryAdapter) : RecyclerView.ViewHolder(view) {
        init {
            view.setOnClickListener {
                adapter.onClick?.invoke(File(adapter.path, adapter.list[adapterPosition]).name)
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val vi = LayoutInflater.from(parent.context).inflate(R.layout.directory_item, parent, false)
        return ViewHolder(vi, this)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        (holder.view as TextView).setText(list[position])
    }

    override fun getItemCount() = list.size
}
