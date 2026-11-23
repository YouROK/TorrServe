package ru.yourok.torrserve.ui

import android.content.Context
import android.graphics.drawable.Drawable
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import ru.yourok.torrserve.R
import ru.yourok.torrserve.settings.Prefs

/**
 * Список плееров внутри окна: используется и диалогом, и страницей в окне раздачи,
 * и оверлеем в главном окне - чтобы везде был один и тот же вид.
 */
object PlayerPicker {

    data class Entry(val pkg: String, val label: String, val icon: Drawable?)

    /** Установленные плееры плюс пункт «Спрашивать каждый раз» (pkg = ""). */
    fun entries(context: Context, url: String, mime: String): List<Entry> {
        val items = mutableListOf(Entry("", context.getString(R.string.player_ask), null))
        Players.installed(context, url, mime).forEach { items += Entry(it.pkg, it.label, it.icon) }
        return items
    }

    /**
     * Заполняет уже готовые список и галочку «запомнить».
     * [onChosen] вызывается с выбранным пакетом ("" - спрашивать каждый раз).
     */
    fun bind(
        context: Context,
        list: RecyclerView,
        remember: CheckBox,
        url: String,
        mime: String,
        onChosen: (String) -> Unit,
    ) {
        val prefs = Prefs(context)
        val items = entries(context, url, mime)
        list.layoutManager = LinearLayoutManager(context)
        list.adapter = Adapter(items, prefs.playerPkg) { entry ->
            if (remember.isChecked) prefs.playerPkg = entry.pkg
            onChosen(entry.pkg)
        }
        list.requestFocus()
    }

    private class Adapter(
        private val items: List<Entry>,
        private val selected: String,
        private val onClick: (Entry) -> Unit,
    ) : RecyclerView.Adapter<Adapter.VH>() {

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH =
            VH(LayoutInflater.from(parent.context).inflate(R.layout.item_player, parent, false))

        override fun getItemCount() = items.size

        override fun onBindViewHolder(holder: VH, position: Int) {
            val entry = items[position]
            holder.name.text = entry.label
            holder.icon.setImageDrawable(entry.icon)
            holder.check.visibility = if (entry.pkg == selected) View.VISIBLE else View.GONE
            holder.itemView.setOnClickListener { onClick(entry) }
        }

        inner class VH(v: View) : RecyclerView.ViewHolder(v) {
            val icon: ImageView = v.findViewById(R.id.player_icon)
            val name: TextView = v.findViewById(R.id.player_name)
            val check: TextView = v.findViewById(R.id.player_selected)
        }
    }
}
