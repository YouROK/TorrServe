package ru.yourok.torrserve.menu

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.view.ActionMode
import android.view.Menu
import android.view.MenuItem
import android.widget.AbsListView
import android.widget.Toast
import ru.yourok.torrserve.R
import ru.yourok.torrserve.adapters.TorrentAdapter
import ru.yourok.torrserve.app.App
import ru.yourok.torrserve.server.api.Api
import ru.yourok.torrserve.server.api.JSObject
import ru.yourok.torrserve.server.net.Net
import kotlin.concurrent.thread

/**
 * Created by yourok on 19.11.17.
 */
class TorrentMainMenu(val activity: Activity, val adapter: TorrentAdapter) : AbsListView.MultiChoiceModeListener {

    private val selected: MutableSet<Int> = mutableSetOf()

    override fun onCreateActionMode(mode: ActionMode?, menu: Menu?): Boolean {
        mode?.menuInflater?.inflate(R.menu.torrent_main_menu, menu)
        selected.clear()
        return true
    }

    override fun onPrepareActionMode(mode: ActionMode?, menu: Menu?): Boolean {
        return false
    }

    override fun onActionItemClicked(mode: ActionMode, item: MenuItem): Boolean {
        when (item.itemId) {

            R.id.itemPlaylist -> {
                selected.first().let {
                    val torrent = (adapter.getItem(it) as JSObject)
                    val pl = torrent.getString("Playlist", "")
                    if (pl.isNotEmpty()) {
                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(Net.getHostUrl(pl)))
                        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
                        App.getContext().startActivity(intent)
                    }
                }
            }

            R.id.itemShareMagnet -> {
                var msg = ""
                selected.forEach {
                    val torrent = (adapter.getItem(it) as JSObject)
                    var magnet = torrent.get("Magnet", "")
                    msg += "${magnet}\n\n"
                }
                if (msg.isNotEmpty()) {
                    val share = Intent(Intent.ACTION_SEND)
                    share.setType("text/plain")
                    share.putExtra(Intent.EXTRA_TEXT, msg)
                    val intent = Intent.createChooser(share, "")
                    activity.startActivity(intent)
                }
            }
            R.id.itemCopyMagnet -> {
                var msg = ""
                selected.forEach {
                    val torrent = (adapter.getItem(it) as JSObject)
                    var magnet = torrent.get("Magnet", "")
                    msg += "${magnet}\n\n"
                }
                if (msg.isNotEmpty()) {
                    val clipboard = activity.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    val clip = ClipData.newPlainText("magnets", msg)
                    clipboard.setPrimaryClip(clip)
                    App.Toast(activity.getString(R.string.copy_to_clipboard))
                }
            }
            R.id.itemRemove -> {
                selected.forEach {
                    val torrent = (adapter.getItem(it) as JSObject)
                    val hash = torrent.getString("Hash", "")
                    if (hash.isNotEmpty())
                        thread {
                            try {
                                Api.torrentRemove(hash)
                            } catch (e: Exception) {
                                activity.runOnUiThread {
                                    val msg = e.message.run { if (this != null) ": " + this else "" }
                                    Toast.makeText(activity, activity.getText(R.string.error_remove_torrent).toString() + msg, Toast.LENGTH_SHORT).show()
                                }
                            }
                        }
                }
                adapter.checkList()
            }
        }
        mode.finish()
        return false
    }

    override fun onDestroyActionMode(mode: ActionMode?) {
    }

    override fun onItemCheckedStateChanged(mode: ActionMode?, position: Int, id: Long, checked: Boolean) {
        if (checked)
            selected.add(position)
        else
            selected.remove(position)
    }
}