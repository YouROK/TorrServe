package ru.yourok.torrserve.ui

import android.app.AlertDialog
import android.content.Context
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
 * Диалог выбора плеера (как в старой версии): список установленных плееров с иконками,
 * галочка «запомнить выбор» и пункт «спрашивать каждый раз».
 */
object PlayersDialog {

    data class Entry(val pkg: String, val label: String, val icon: android.graphics.drawable.Drawable?)

    fun show(
        context: Context,
        url: String,
        mime: String,
        title: String,
        poster: String,
        launchSelected: Boolean = true,
        onChosen: (String) -> Unit = {},
        playlist: List<Pair<String, String>>? = null,
        startIndex: Int = 0,
    ) {
        val prefs = Prefs(context)
        val entries = mutableListOf(Entry("", context.getString(R.string.player_ask), null))
        Players.installed(context, url, mime).forEach { entries += Entry(it.pkg, it.label, it.icon) }

        val view = LayoutInflater.from(context).inflate(R.layout.dialog_players, null)
        val listView = view.findViewById<RecyclerView>(R.id.players_list)
        val remember = view.findViewById<CheckBox>(R.id.players_remember)
        remember.isChecked = true

        val dialog = AlertDialog.Builder(context)
            .setTitle(R.string.player_choose)
            .setView(view)
            .setNegativeButton(android.R.string.cancel, null)
            .create()

        listView.layoutManager = LinearLayoutManager(context)
        listView.adapter = Adapter(entries, prefs.playerPkg) { entry ->
            if (remember.isChecked) prefs.playerPkg = entry.pkg
            dialog.dismiss()
            onChosen(entry.pkg)
            if (launchSelected && entry.pkg.isNotBlank()) {
                Players.launch(context, url, mime, title, poster, entry.pkg, playlist, startIndex)
            }
        }
        dialog.show()
        listView.requestFocus()
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
