package ru.yourok.torrserve.ui

import android.app.Activity
import android.app.AlertDialog
import android.content.ClipboardManager
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.widget.EditText
import android.widget.Toast
import ru.yourok.torrserve.R
import ru.yourok.torrserve.server.api.SiloApi
import ru.yourok.torrserve.settings.Prefs
import ru.yourok.torrserve.settings.Session
import kotlin.concurrent.thread

/**
 * Диалог добавления торрента по magnet-ссылке или info-hash.
 * Если на устройстве несколько пользователей - сначала выбор, от чьего имени добавить.
 */
object AddTorrentDialog {

    fun show(activity: Activity, onDone: () -> Unit) {
        val prefs = Prefs(activity)
        val sessions = prefs.getSessions()
        when {
            // Сервер без пароля (одиночный режим): добавляем без входа
            sessions.isEmpty() -> input(activity, prefs, Session("", "", ""), onDone)
            sessions.size == 1 -> input(activity, prefs, sessions.first(), onDone)
            else -> pickUser(activity, prefs, sessions, onDone)
        }
    }

    private fun pickUser(activity: Activity, prefs: Prefs, sessions: List<Session>, onDone: () -> Unit) {
        val names = sessions.map { it.username }.toTypedArray()
        val active = prefs.activeSession()
        val preselect = sessions.indexOfFirst { it.id == active?.id }.coerceAtLeast(0)
        AlertDialog.Builder(activity)
            .setTitle(R.string.magnet_pick_user)
            .setSingleChoiceItems(names, preselect) { dialog, which ->
                val chosen = sessions[which]
                prefs.activeUserId = chosen.id
                dialog.dismiss()
                input(activity, prefs, chosen, onDone)
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun input(activity: Activity, prefs: Prefs, session: Session, onDone: () -> Unit) {
        val view = LayoutInflater.from(activity).inflate(R.layout.dialog_add_torrent, null)
        val link = view.findViewById<EditText>(R.id.add_link)
        view.findViewById<android.view.View>(R.id.add_paste).setOnClickListener {
            val clipboard = activity.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            val text = clipboard.primaryClip?.getItemAt(0)?.text?.toString().orEmpty()
            if (text.isNotBlank()) link.setText(text)
        }

        val dialog = AlertDialog.Builder(activity)
            .setTitle(R.string.add_title)
            .setView(view)
            .setPositiveButton(R.string.add_button) { _, _ -> submit(activity, prefs, session, link.text.toString(), onDone) }
            .setNegativeButton(android.R.string.cancel, null)
            .create()
        dialog.show()
        link.requestFocus()
    }

    private fun submit(activity: Activity, prefs: Prefs, session: Session, raw: String, onDone: () -> Unit) {
        val value = raw.trim()
        if (value.isEmpty()) return
        val handler = Handler(Looper.getMainLooper())
        thread {
            val err = SiloApi(prefs.serverUrl).addTorrent(session.token, value)
            handler.post {
                val text = err?.let { activity.getString(R.string.magnet_error, it) }
                    ?: activity.getString(R.string.magnet_added)
                Toast.makeText(activity, text, Toast.LENGTH_LONG).show()
                if (err == null) onDone()
            }
        }
    }
}
