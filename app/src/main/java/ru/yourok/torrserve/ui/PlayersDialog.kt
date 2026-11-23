package ru.yourok.torrserve.ui

import android.app.AlertDialog
import android.content.Context
import android.view.LayoutInflater
import android.widget.CheckBox
import androidx.recyclerview.widget.RecyclerView
import ru.yourok.torrserve.R

/**
 * Диалог выбора плеера (как в старой версии): список установленных плееров с иконками,
 * галочка «запомнить выбор» и пункт «спрашивать каждый раз».
 *
 * Само окно осталось для сценариев, где открыть выбор страницей нельзя; в окне раздачи
 * и в главном окне используется та же начинка через [PlayerPicker] без отдельного окна.
 */
object PlayersDialog {

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
        val view = LayoutInflater.from(context).inflate(R.layout.dialog_players, null)
        val listView = view.findViewById<RecyclerView>(R.id.players_list)
        val remember = view.findViewById<CheckBox>(R.id.players_remember)
        remember.isChecked = true

        val dialog = AlertDialog.Builder(context)
            .setTitle(R.string.player_choose)
            .setView(view)
            .setNegativeButton(android.R.string.cancel, null)
            .create()

        PlayerPicker.bind(context, listView, remember, url, mime) { pkg ->
            dialog.dismiss()
            onChosen(pkg)
            if (launchSelected && pkg.isNotBlank()) {
                Players.launch(context, url, mime, title, poster, pkg, playlist, startIndex)
            }
        }
        dialog.show()
        listView.requestFocus()
    }
}
